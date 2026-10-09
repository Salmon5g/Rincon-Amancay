import type { ImagenPreparada } from './imagenes.ts';
import { createHash } from 'node:crypto';
import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import type { Firestore, DocumentData, DocumentReference, Transaction } from 'firebase-admin/firestore';

export type Accion = 'publicarTienda' | 'retirarTienda' | 'publicarProducto' | 'retirarProducto';
export type Solicitud = { tiendaId: string; productoId?: string; operacionId: string; versionEsperada: string };
// Esta identidad debe provenir del token verificado por la API,
// nunca de un campo uid recibido en el cuerpo del cliente.
export type IdentidadVerificada = { uid: string };
export class ErrorOperacion extends Error {
  code: string;
  constructor(code: string, message: string) { super(message); this.code = code; }
}
function exigir(ok: unknown, code: string, message: string): asserts ok {
  if (!ok) throw new ErrorOperacion(code, message);
}
function id(value: unknown) { return typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value); }
export function version(data: DocumentData): string {
  exigir(data.actualizadoEn instanceof Timestamp, 'datos-invalidos', 'Falta actualizadoEn de Firestore.');
  return `${data.actualizadoEn.seconds}:${data.actualizadoEn.nanoseconds}`;
}
function texto(value: unknown, max: number) {
  exigir(typeof value === 'string' && value.trim().length > 0 && value.length <= max, 'datos-invalidos', 'Texto requerido o demasiado largo.');
  return value;
}
function precio(value: unknown) {
  exigir(Number.isSafeInteger(value) && Number(value) >= 0, 'datos-invalidos', 'Precio CLP entero no negativo requerido.');
  return value;
}
export function estado(p: DocumentData, stock: unknown): string | null {
  if (p.modalidad === 'a_pedido') return 'a_pedido';
  if (!p.manejaStock) return 'disponible';
  exigir(Number.isSafeInteger(stock) && Number(stock) >= 0, 'datos-invalidos', 'Stock entero no negativo requerido.');
  if (Number(stock) > 0) return 'disponible';
  if (p.alAgotarse === 'ocultar') return null;
  return p.alAgotarse === 'pasar_a_pedido' ? 'a_pedido' : 'agotado';
}
async function hijos(tx: Transaction, ref: DocumentReference, nombre: string) {
  const result = await tx.get(ref.collection(nombre).limit(101));
  exigir(result.size <= 100, 'limite-local', 'Máximo 100 documentos por subcolección en este incremento.');
  return result.docs;
}

export function crearPublicacion(db: Firestore, imagenAutorizada: (ruta: string, tiendaId: string, productoId: string) => Promise<boolean | ImagenPreparada>) {
  return async function ejecutar(accion: Accion, identidad: IdentidadVerificada, solicitud: Solicitud) {
    exigir(['publicarTienda', 'retirarTienda', 'publicarProducto', 'retirarProducto'].includes(accion), 'datos-invalidos', 'Acción inválida.');
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');
    exigir(id(solicitud.tiendaId) && id(solicitud.operacionId), 'datos-invalidos', 'IDs inválidos.');
    const esProducto = accion.endsWith('Producto');
    exigir(esProducto ? id(solicitud.productoId) : solicitud.productoId === undefined, 'datos-invalidos', 'Producto no corresponde a la operación.');
    exigir(typeof solicitud.versionEsperada === 'string', 'datos-invalidos', 'Versión requerida.');
    const firma = createHash('sha256').update(JSON.stringify([accion, identidad.uid, solicitud.tiendaId, solicitud.productoId ?? null, solicitud.versionEsperada])).digest('hex');
    const privada = db.doc(`tiendasPrivadas/${solicitud.tiendaId}`);
    const publica = db.doc(`tiendasPublicas/${solicitud.tiendaId}`);
    const source = esProducto ? privada.collection('productos').doc(solicitud.productoId!) : privada;
    const target = esProducto ? publica.collection('productos').doc(solicitud.productoId!) : publica;
    const aprobacion = esProducto ? source.collection('publicacion').doc('vigente') : null;
    const imagenesPreparadas = new Map<string, boolean | ImagenPreparada>();
    const recibo = privada.collection('operacionesPublicacion').doc(solicitud.operacionId);

    return db.runTransaction(async tx => {
      const access = (await tx.get(db.doc(`accesos/${identidad.uid}`))).data();
      const store = (await tx.get(privada)).data();
      exigir(access?.estado === 'activo' && access.roles?.includes('emprendedora') && access.tiendaId === solicitud.tiendaId && store?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
      const previous = await tx.get(recibo);
      if (previous.exists) {
        exigir(previous.data()!.firma === firma, 'id-reutilizado', 'operacionId ya corresponde a otra solicitud.');
        return previous.data()!.resultado as { accion: Accion; operacionId: string };
      }
      const snapshot = esProducto ? await tx.get(source) : null;
      const data = esProducto ? snapshot?.data() : store;
      exigir(data, 'no-encontrado', 'Documento privado inexistente.');
      exigir(version(data) === solicitud.versionEsperada, 'conflicto', 'La ficha cambió; volver a leer antes de operar.');
      const now = FieldValue.serverTimestamp();
      const resultado = { accion, operacionId: solicitud.operacionId };
      const borrar: DocumentReference[] = [];
      const escribir: { ref: DocumentReference; data: DocumentData }[] = [];

      if (accion === 'retirarTienda') {
        const products = await hijos(tx, publica, 'productos');
        for (const product of products) {
          const variants = await hijos(tx, product.ref, 'variantes');
          borrar.push(...variants.map(v => v.ref), product.ref);
          exigir(borrar.length <= 199, 'limite-local', 'Retirada demasiado grande para este incremento; no se aplicó ningún cambio.');
        }
        borrar.push(publica);
      } else if (accion === 'retirarProducto') {
        borrar.push(aprobacion!);
        borrar.push(...(await hijos(tx, target, 'variantes')).map(v => v.ref), target);
      } else {
        const config = (await tx.get(privada.collection('configuracion').doc('general'))).data();
        exigir(typeof config?.mostrarPrecios === 'boolean', 'datos-invalidos', 'Falta configuración de precios.');
        if (accion === 'publicarTienda') {
          const sector = await tx.get(db.doc(`sectores/${texto(store.sectorId, 128)}`));
          const tipo = await tx.get(db.doc(`tiposEmprendimiento/${texto(store.tipoEmprendimientoId, 128)}`));
          exigir(sector.data()?.activo === true && tipo.data()?.activo === true, 'datos-invalidos', 'Sector o tipo inactivo.');
          escribir.push({ ref: publica, data: {
            nombre: texto(store.nombre, 120), descripcion: texto(store.descripcion, 2000),
            sectorId: store.sectorId, tipoEmprendimientoId: store.tipoEmprendimientoId,
            estadoPublicacion: 'publicado', habilitada: true, mostrarPrecios: config.mostrarPrecios, actualizadoEn: now,
          } });
        } else {
          const publicStore = (await tx.get(publica)).data();
          exigir(store.estadoPublicacion === 'publicado' && publicStore?.estadoPublicacion === 'publicado' && publicStore.habilitada === true, 'tienda-no-publicada', 'Publicar primero la tienda.');
          exigir(config.mostrarPrecios === true && publicStore.mostrarPrecios === true, 'pendiente', 'Catálogo sin precios requiere el siguiente incremento.');
          exigir(data.disponible === true && data.piezaUnicaVendida === false, 'no-publicable', 'Producto no disponible o pieza vendida.');
          exigir(data.moneda === 'CLP' && data.unidadVenta === 'unidad' && data.pasoCantidad === 1, 'datos-invalidos', 'Este incremento admite CLP y unidades enteras.');
          exigir(['regular', 'a_pedido', 'pieza_unica'].includes(data.modalidad) && typeof data.manejaStock === 'boolean' && typeof data.tieneVariantes === 'boolean', 'datos-invalidos', 'Modalidad o indicadores inválidos.');
          if (data.modalidad === 'pieza_unica') exigir(!data.tieneVariantes && data.manejaStock && data.stock === 1, 'datos-invalidos', 'Pieza única requiere una unidad sin variantes.');
          exigir(['mostrar_agotado', 'ocultar', 'pasar_a_pedido'].includes(data.alAgotarse), 'datos-invalidos', 'Política de agotamiento inválida.');
          exigir(id(data.tipoProductoId) && id(data.categoriaId) && Number.isSafeInteger(data.versionTipoProducto), 'datos-invalidos', 'Referencias inválidas.');
          const type = await tx.get(db.doc(`tiposProducto/${data.tipoProductoId}`));
          const definition = await tx.get(db.doc(`tiposProducto/${data.tipoProductoId}/versiones/${data.versionTipoProducto}`));
          const category = await tx.get(db.doc(`categorias/${data.categoriaId}`));
          exigir(type.data()?.activo === true && definition.exists && category.data()?.activo === true, 'datos-invalidos', 'Tipo, versión o categoría inexistentes/inactivos.');
          exigir(Array.isArray(data.imagenes) && data.imagenes.length >= 1 && data.imagenes.length <= 5, 'imagen-requerida', 'Se requiere entre una y cinco imágenes autorizadas.');
          exigir(new Set(data.imagenes).size === data.imagenes.length, 'datos-invalidos', 'No repetir imágenes.');
          const imagenes:string[]=[],miniaturas:string[]=[];
          for (const image of data.imagenes) {
            exigir(typeof image==='string','imagen-no-autorizada','Ruta inválida.');
            if(!imagenesPreparadas.has(image)) imagenesPreparadas.set(image,await imagenAutorizada(image,solicitud.tiendaId,solicitud.productoId!));
            const preparada=imagenesPreparadas.get(image)!;
            exigir(preparada,'imagen-no-autorizada','Imagen no validada.');
            if(typeof preparada==='object') {imagenes.push(preparada.imagen);miniaturas.push(preparada.miniatura);}
            else imagenes.push(image); // Inyección de pruebas aisladas; servidor usa procesador.
          }
          const attrs: DocumentData = {};
          for (const field of definition.data()!.campos) {
            const input = data.atributosEspecificos?.[field.clave];
            if (input !== undefined) attrs[field.clave] = texto(input, 500);
            else exigir(!field.obligatorio, 'datos-invalidos', `Falta atributo ${field.clave}`);
          }
          const variants = await hijos(tx, source, 'variantes');
          const oldVariants = await hijos(tx, target, 'variantes');
          borrar.push(...oldVariants.map(v => v.ref));
          const projection: DocumentData = {
            nombre: texto(data.nombre, 120), descripcion: texto(data.descripcion, 2000),
            tipoProductoId: data.tipoProductoId, versionTipoProducto: data.versionTipoProducto,
            categoriaId: data.categoriaId, imagenes, ...(miniaturas.length?{miniaturas}:{}), atributosEspecificos: attrs,
            tieneVariantes: data.tieneVariantes, moneda: 'CLP', unidadVenta: 'unidad', modalidad: data.modalidad,
            estadoPublicacion: 'publicado', actualizadoEn: now,
          };
          const variantesAprobadas: Record<string,DocumentData> = Object.create(null);
          if (data.tieneVariantes) {
            exigir(!('precioBase' in data) && !('stock' in data), 'datos-invalidos', 'Stock/precio duplicados en producto con variantes.');
            const states: string[] = [];
            for (const variant of variants) {
              const v = variant.data();
              if (!v.activa) continue;
              const availability = estado(data, v.stock);
              const options: DocumentData = {};
              for (const key of definition.data()!.opcionesVariante) options[key] = texto(v.opciones?.[key], 100);
              variantesAprobadas[variant.id]={opciones:options,precio:precio(v.precio),activa:true};
              if(!availability) continue;
              states.push(availability);
              escribir.push({ ref: target.collection('variantes').doc(variant.id), data: { opciones: options, precio: precio(v.precio), activa: true, estadoDisponibilidad: availability, actualizadoEn: now } });
            }
            exigir(states.length > 0, 'no-publicable', 'No hay variantes publicables.');
            projection.estadoDisponibilidad = states.includes('disponible') ? 'disponible' : states.includes('a_pedido') ? 'a_pedido' : 'agotado';
          } else {
            exigir(variants.length === 0, 'datos-invalidos', 'Producto simple conserva variantes privadas.');
            const availability = estado(data, data.stock);
            exigir(availability, 'no-publicable', 'Producto agotado configurado para ocultarse.');
            projection.precioBase = precio(data.precioBase);
            projection.estadoDisponibilidad = availability;
          }
          escribir.push({ref:aprobacion!,data:{schema:1,cicloCatalogo:store.cicloCatalogo??0,
            politica:{modalidad:data.modalidad,manejaStock:data.manejaStock,alAgotarse:data.alAgotarse},
            producto:projection,variantes:variantesAprobadas,aprobadoEn:now}});
          escribir.push({ ref: target, data: projection });
        }
      }
      exigir(borrar.length + escribir.length <= 200, 'limite-local', 'La operación supera el límite atómico de este incremento.');
      const nuevos = new Set(escribir.map(w => w.ref.path));
      for (const ref of borrar) if (!nuevos.has(ref.path)) tx.delete(ref);
      for (const item of escribir) tx.set(item.ref, item.data);
      tx.update(source, { ...(accion==='retirarTienda'?{cicloCatalogo:(store.cicloCatalogo??0)+1}:{}), estadoPublicacion: accion.startsWith('publicar') ? 'publicado' : 'archivado', actualizadoEn: now });
      tx.create(recibo, { firma, resultado, creadoEn: now });
      return resultado;
    });
  };
}
