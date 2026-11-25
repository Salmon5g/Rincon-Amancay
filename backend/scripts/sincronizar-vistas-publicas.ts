import { applicationDefault, deleteApp, initializeApp } from 'firebase-admin/app';
import { FieldPath, FieldValue, getFirestore, type QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { randomUUID } from 'node:crypto';
import { exigirEntornoCompartido } from '../src/config/compartido.ts';
import { construirProyeccionAsociacionPublica } from '../src/modules/proyecciones-asociaciones-publicas.ts';
import { crearProductoSinPrecios, crearVarianteSinPrecio } from '../src/modules/vista-sin-precios.ts';

const PROYECTO = exigirEntornoCompartido(process.env);
const [modo, proyectoConfirmado, ...extras] = process.argv.slice(2);
if (extras.length || !['--dry-run', '--aplicar'].includes(modo ?? '') || proyectoConfirmado !== PROYECTO) {
  throw new Error('Uso: npm run catalogo:sincronizar-vistas-publicas -- --dry-run|--aplicar <ID_PROYECTO_CONFIRMADO>');
}
const aplicar = modo === '--aplicar';
const app = initializeApp({ projectId: PROYECTO, credential: applicationDefault() }, `vistas-publicas-${randomUUID()}`);
const db = getFirestore(app);
const resumen = { asociaciones: 0, tiendas: 0, productos: 0, variantes: 0, fallos: 0 };

async function recorrer(queryBase: FirebaseFirestore.Query, procesar: (doc: QueryDocumentSnapshot) => Promise<void>) {
  let cursor: QueryDocumentSnapshot | undefined;
  while (true) {
    let query = queryBase.limit(50);
    if (cursor) query = query.startAfter(cursor);
    const pagina = await query.get();
    for (const doc of pagina.docs) await procesar(doc);
    if (pagina.size < 50) return;
    cursor = pagina.docs.at(-1);
  }
}

try {
  await recorrer(db.collection('asociaciones').orderBy(FieldPath.documentId()), async asociacion => {
    try {
      const proyeccion = await construirProyeccionAsociacionPublica(db, asociacion.id);
      const destino = db.doc(`asociacionesPublicas/${asociacion.id}`);
      if (aplicar) {
        if (proyeccion) await destino.set(proyeccion);
        else await destino.delete().catch(() => undefined);
      }
      resumen.asociaciones++;
    } catch (error) {
      resumen.fallos++;
      console.error(`Asociación ${asociacion.id}: ${error instanceof Error ? error.message : 'error desconocido'}`);
    }
  });

  await recorrer(db.collection('tiendasPublicas')
    .where('estadoPublicacion', '==', 'publicado')
    .where('habilitada', '==', true)
    .orderBy(FieldPath.documentId()), async tienda => {
    try {
      const configuracion = await db.doc(`tiendasPrivadas/${tienda.id}/configuracion/general`).get();
      const formaContacto = ['whatsapp', 'formulario', 'ambos'].includes(String(configuracion.get('formaContacto')))
        ? configuracion.get('formaContacto') as string
        : 'formulario';
      if (aplicar && tienda.get('formaContacto') !== formaContacto) {
        await tienda.ref.update({ formaContacto, actualizadoEn: FieldValue.serverTimestamp() });
      }
      resumen.tiendas++;
      const productos = await tienda.ref.collection('productos')
        .where('estadoPublicacion', '==', 'publicado')
        .orderBy(FieldPath.documentId()).get();
      for (const producto of productos.docs) {
        const variantes = await producto.ref.collection('variantes').where('activa', '==', true).orderBy(FieldPath.documentId()).get();
        const destino = tienda.ref.collection('productosConsulta').doc(producto.id);
        if (aplicar) {
          const existentes = await destino.collection('variantes').get();
          const activas = new Set(variantes.docs.map(variant => variant.id));
          const batch = db.batch();
          batch.set(destino, crearProductoSinPrecios(producto.data()));
          for (const variante of variantes.docs) {
            batch.set(destino.collection('variantes').doc(variante.id), crearVarianteSinPrecio(variante.data()));
          }
          for (const variante of existentes.docs) if (!activas.has(variante.id)) batch.delete(variante.ref);
          await batch.commit();
        }
        resumen.productos++;
        resumen.variantes += variantes.size;
      }
    } catch (error) {
      resumen.fallos++;
      console.error(`Tienda ${tienda.id}: ${error instanceof Error ? error.message : 'error desconocido'}`);
    }
  });

  console.log(`${aplicar ? 'ESCRITURA' : 'SIMULACIÓN'} en ${PROYECTO}: ${resumen.asociaciones} asociaciones revisadas, ${resumen.tiendas} tiendas, ${resumen.productos} productos, ${resumen.variantes} variantes; fallos: ${resumen.fallos}.`);
  if (resumen.fallos) process.exitCode = 1;
} finally {
  await deleteApp(app);
}
