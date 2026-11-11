import { applicationDefault, deleteApp, initializeApp } from 'firebase-admin/app';
import { FieldPath, FieldValue, getFirestore, type QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { randomUUID } from 'node:crypto';
import { exigirEntornoCompartido } from '../src/config/compartido.ts';

const TAMANO_PAGINA = 50;
const PROYECTO = exigirEntornoCompartido(process.env);
const [modo, proyectoConfirmado, ...argumentosExtra] = process.argv.slice(2);
if (argumentosExtra.length || !['--dry-run', '--aplicar'].includes(modo ?? '') || proyectoConfirmado !== PROYECTO) {
  throw new Error('Uso: npm run catalogo:sincronizar-stock-publico -- --dry-run|--aplicar rincon-amancay');
}

const aplicar = modo === '--aplicar';
const app = initializeApp({ projectId: PROYECTO, credential: applicationDefault() }, `sincronizar-stock-publico-${randomUUID()}`);
const db = getFirestore(app);
type Resultado = { estado: 'actualizado' | 'al-dia' | 'omitido'; variantes: number; razon?: string };

function esSaldo(valor: unknown): valor is number {
  return typeof valor === 'number' && Number.isSafeInteger(valor) && valor >= 0;
}

async function sincronizarProducto(tiendaId: string, productoId: string): Promise<Resultado> {
  const publico = db.doc(`tiendasPublicas/${tiendaId}/productos/${productoId}`);
  const privado = db.doc(`tiendasPrivadas/${tiendaId}/productos/${productoId}`);
  return db.runTransaction(async tx => {
    const [fichaPublica, fichaPrivada] = await tx.getAll(publico, privado);
    const p = fichaPublica.data();
    const d = fichaPrivada.data();
    if (!fichaPublica.exists || p?.estadoPublicacion !== 'publicado') return { estado: 'omitido', variantes: 0, razon: 'ficha pública no publicada' };
    if (!fichaPrivada.exists || d?.estadoPublicacion !== 'publicado') return { estado: 'omitido', variantes: 0, razon: 'ficha privada no publicada' };
    if (typeof d.manejaStock !== 'boolean' || typeof d.tieneVariantes !== 'boolean' || p.tieneVariantes !== d.tieneVariantes) {
      return { estado: 'omitido', variantes: 0, razon: 'estructura o política de stock inconsistente' };
    }

    const ahora = FieldValue.serverTimestamp();
    if (!d.tieneVariantes) {
      if (d.manejaStock && !esSaldo(d.stock)) return { estado: 'omitido', variantes: 0, razon: 'saldo simple inválido' };
      const stock = d.manejaStock ? d.stock : null;
      if (p.manejaStock === d.manejaStock && p.stock === stock) return { estado: 'al-dia', variantes: 0 };
      if (aplicar) tx.update(publico, { manejaStock: d.manejaStock, stock, actualizadoEn: ahora });
      return { estado: 'actualizado', variantes: 0 };
    }

    const consultaVariantes = publico.collection('variantes')
      .where('activa', '==', true)
      .orderBy(FieldPath.documentId())
      .limit(101);
    const variantesPublicas = await tx.get(consultaVariantes);
    if (variantesPublicas.size === 0 || variantesPublicas.size > 100) {
      return { estado: 'omitido', variantes: 0, razon: 'cantidad de variantes públicas fuera del límite' };
    }
    const variantesPrivadas = await tx.getAll(...variantesPublicas.docs.map(v => privado.collection('variantes').doc(v.id)));
    if (variantesPrivadas.some(v => !v.exists || (d.manejaStock && !esSaldo(v.data()?.stock)))) {
      return { estado: 'omitido', variantes: 0, razon: 'falta el saldo privado de una variante pública' };
    }
    const saldosPublicos = variantesPublicas.docs.map((v, indice) => d.manejaStock ? variantesPrivadas[indice].data()!.stock : null);
    const alDia = p.manejaStock === d.manejaStock
      && variantesPublicas.docs.every((v, indice) => v.data().stock === saldosPublicos[indice]);
    if (alDia) return { estado: 'al-dia', variantes: variantesPublicas.size };
    if (aplicar) {
      tx.update(publico, { manejaStock: d.manejaStock, actualizadoEn: ahora });
      variantesPublicas.docs.forEach((v, indice) => {
        tx.update(v.ref, { stock: saldosPublicos[indice], actualizadoEn: ahora });
      });
    }
    return { estado: 'actualizado', variantes: variantesPublicas.size };
  });
}

async function recorrerPaginas(
  crearConsulta: (cursor?: QueryDocumentSnapshot) => FirebaseFirestore.Query,
  procesar: (documento: QueryDocumentSnapshot) => Promise<void>,
) {
  let cursor: QueryDocumentSnapshot | undefined;
  while (true) {
    const pagina = await crearConsulta(cursor).get();
    for (const documento of pagina.docs) await procesar(documento);
    if (pagina.size < TAMANO_PAGINA) return;
    cursor = pagina.docs.at(-1);
  }
}

const resumen = { actualizados: 0, alDia: 0, variantes: 0, omitidos: 0, motivos: new Map<string, number>(), fallos: 0 };
try {
  const consultaTiendas = (cursor?: QueryDocumentSnapshot) => {
    let query = db.collection('tiendasPublicas')
      .where('estadoPublicacion', '==', 'publicado')
      .where('habilitada', '==', true)
      .orderBy(FieldPath.documentId())
      .limit(TAMANO_PAGINA);
    if (cursor) query = query.startAfter(cursor);
    return query;
  };

  await recorrerPaginas(consultaTiendas, async tienda => {
    const consultaProductos = (cursor?: QueryDocumentSnapshot) => {
      let query = tienda.ref.collection('productos')
        .where('estadoPublicacion', '==', 'publicado')
        .orderBy(FieldPath.documentId())
        .limit(TAMANO_PAGINA);
      if (cursor) query = query.startAfter(cursor);
      return query;
    };
    await recorrerPaginas(consultaProductos, async producto => {
      try {
        const resultado = await sincronizarProducto(tienda.id, producto.id);
        if (resultado.estado === 'actualizado') {
          resumen.actualizados++;
          resumen.variantes += resultado.variantes;
        } else if (resultado.estado === 'al-dia') {
          resumen.alDia++;
        } else {
          resumen.omitidos++;
          const razon = resultado.razon ?? 'sin detalle';
          resumen.motivos.set(razon, (resumen.motivos.get(razon) ?? 0) + 1);
        }
      } catch (error) {
        resumen.fallos++;
        console.error(`Falló ${tienda.id}/${producto.id}: ${error instanceof Error ? error.message : 'error desconocido'}`);
      }
    });
  });

  console.log(`${aplicar ? 'ESCRITURA' : 'SIMULACIÓN'} en ${PROYECTO}`);
  console.log(`Productos ${aplicar ? 'actualizados' : 'por actualizar'}: ${resumen.actualizados}; ya al día: ${resumen.alDia}; variantes afectadas: ${resumen.variantes}; omitidos: ${resumen.omitidos}; fallos: ${resumen.fallos}.`);
  for (const [razon, cantidad] of resumen.motivos) console.log(`Omitidos — ${razon}: ${cantidad}`);
  if (resumen.fallos) process.exitCode = 1;
} finally {
  await deleteApp(app);
}
