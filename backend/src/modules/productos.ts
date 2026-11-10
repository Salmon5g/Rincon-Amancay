import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import type { Firestore, DocumentData, QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { ErrorOperacion, version, estado } from './publicacion.ts';

export type AccionProducto = 'crearProducto' | 'editarProducto' | 'eliminarProducto' | 'asignarImagenVariante' | 'cambiarDisponibilidadProductos';
function exigir(ok: unknown, code: string, message: string): asserts ok {
  if (!ok) throw new ErrorOperacion(code, message);
}
function objeto(value: unknown): Record<string, any> {
  exigir(value !== null && typeof value === 'object' && !Array.isArray(value), 'datos-invalidos', 'Se requiere un objeto.');
  return value as Record<string, any>;
}
function campos(d: Record<string, any>, required: string[], optional: string[] = []) {
  exigir(required.every(k => Object.hasOwn(d, k)) && Object.keys(d).every(k => [...required, ...optional].includes(k)), 'datos-invalidos', 'Campos ausentes o no permitidos.');
}
function id(v: unknown) { return typeof v === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(v); }
function texto(v: unknown, max: number) { exigir(typeof v === 'string' && v.trim().length > 0 && v.length <= max, 'datos-invalidos', 'Texto inválido.'); }
function entero(v: unknown) { exigir(Number.isSafeInteger(v) && Number(v) >= 0, 'datos-invalidos', 'Se requiere un entero no negativo.'); }
function canon(v: any): string {
  if (Array.isArray(v)) return '[' + v.map(canon).join(',') + ']';
  if (v && typeof v === 'object') return '{' + Object.keys(v).sort().map(k => JSON.stringify(k) + ':' + canon(v[k])).join(',') + '}';
  return JSON.stringify(v);
}

export function crearProductos(db: Firestore, limpiarImagenesPublicas?: (rutas: string[]) => Promise<void>, procesarImagen?: (ruta: string, tiendaId: string, productoId: string) => Promise<boolean | {imagen:string;miniatura:string}>) {
  return async (accion: AccionProducto, identidad: {uid: string}, input: unknown) => {
    exigir(['crearProducto','editarProducto','eliminarProducto','asignarImagenVariante','cambiarDisponibilidadProductos'].includes(accion), 'datos-invalidos', 'Acción inválida.');
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');

    if (accion === 'cambiarDisponibilidadProductos') {
      const d = objeto(input);
      campos(d, ['tiendaId','operacionId','disponible','items']);
      exigir(id(d.tiendaId) && id(d.operacionId) && typeof d.disponible === 'boolean' && Array.isArray(d.items) && d.items.length > 0 && d.items.length <= 20, 'datos-invalidos', 'Selecciona de 1 a 20 productos y una disponibilidad.');
      const items = d.items.map((value: unknown) => {
        const item = objeto(value);
        campos(item, ['productoId','versionEsperada']);
        exigir(id(item.productoId) && typeof item.versionEsperada === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(item.versionEsperada), 'datos-invalidos', 'Producto o versión inválida.');
        return {productoId:item.productoId as string, versionEsperada:item.versionEsperada as string};
      });
      exigir(new Set(items.map((item: {productoId:string; versionEsperada:string}) => item.productoId)).size === items.length, 'datos-invalidos', 'No repetir productos en una operación.');
      const tiendaRef = db.doc(`tiendasPrivadas/${d.tiendaId}`), accesoRef = db.doc(`accesos/${identidad.uid}`);
      const reciboRef = tiendaRef.collection('operacionesDisponibilidad').doc(d.operacionId);
      const tiendaPublicaRef = db.doc(`tiendasPublicas/${d.tiendaId}`);
      const firma = createHash('sha256').update(canon({uid:identidad.uid, tiendaId:d.tiendaId, disponible:d.disponible, items})).digest('hex');
      const referencias = items.flatMap((item: {productoId:string; versionEsperada:string}) => {
        const productoRef = tiendaRef.collection('productos').doc(item.productoId);
        return [productoRef, productoRef.collection('publicacion').doc('vigente'), db.doc(`tiendasPublicas/${d.tiendaId}/productos/${item.productoId}`)];
      });
      return db.runTransaction(async tx => {
        const iniciales = await tx.getAll(accesoRef, tiendaRef, reciboRef, tiendaPublicaRef, ...referencias);
        const [acceso, tienda, recibo, tiendaPublica] = iniciales;
        exigir(acceso.data()?.estado === 'activo' && acceso.data()?.roles?.includes('emprendedora') && acceso.data()?.tiendaId === d.tiendaId && tienda.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
        if (recibo.exists) { exigir(recibo.data()?.firma === firma, 'id-reutilizado', 'operacionId corresponde a otra solicitud.'); return recibo.data()!.resultado; }
        const consultas = await Promise.all(items.map(async (item: {productoId:string; versionEsperada:string}, index: number) => {
          const base = 4 + index * 3;
          const productoRef = referencias[index * 3] as FirebaseFirestore.DocumentReference;
          const publicRef = referencias[index * 3 + 2] as FirebaseFirestore.DocumentReference;
          const [variantes, variantesPublicas] = await Promise.all([
            tx.get(productoRef.collection('variantes').limit(21)),
            tx.get(publicRef.collection('variantes').limit(21))
          ]);
          exigir(variantes.size <= 20 && variantesPublicas.size <= 20, 'limite-local', 'Cada producto admite hasta 20 variantes en este cambio.');
          return {item, producto:iniciales[base], aprobacion:iniciales[base+1], proyeccion:iniciales[base+2], variantes, variantesPublicas, productoRef, publicRef};
        }));
        const cambios: {productoId:string; disponible:boolean; actualizacionCatalogo:string}[] = [];
        const ahora = FieldValue.serverTimestamp();
        for (const consulta of consultas) {
          const data = consulta.producto.data();
          exigir(data && data.estadoPublicacion !== 'eliminado', 'no-encontrado', 'Uno de los productos ya no está disponible.');
          exigir(version(data) === consulta.item.versionEsperada, 'conflicto', 'Uno de los productos cambió; actualiza el catálogo e inténtalo nuevamente.');
          tx.update(consulta.productoRef, {disponible:d.disponible, actualizadoEn:ahora});
          if (!d.disponible) {
            for (const variante of consulta.variantesPublicas.docs) tx.delete(variante.ref);
            if (consulta.proyeccion.exists) tx.delete(consulta.publicRef);
            cambios.push({productoId:consulta.item.productoId, disponible:false, actualizacionCatalogo:'oculto'});
            continue;
          }
          const approved = consulta.aprobacion.data();
          const sincronizar = approved?.schema === 1 && approved.cicloCatalogo === (tienda.data()?.cicloCatalogo ?? 0)
            && tienda.data()?.estadoPublicacion === 'publicado' && tiendaPublica.data()?.estadoPublicacion === 'publicado'
            && tiendaPublica.data()?.habilitada === true && data.estadoPublicacion === 'publicado' && data.piezaUnicaVendida !== true;
          if (!sincronizar) {
            cambios.push({productoId:consulta.item.productoId, disponible:true, actualizacionCatalogo:'sin_publicacion'});
            continue;
          }
          const policy = approved.politica;
          const base = {...approved.producto} as DocumentData;
          let proyeccion: DocumentData | null = null;
          const nuevasVariantes: {id:string; data:DocumentData}[] = [];
          const estados: string[] = [];
          if (base.tieneVariantes === true) {
            const aprobadas = approved.variantes as Record<string, DocumentData>;
            for (const varianteRef of consulta.variantes.docs) {
              const v = varianteRef.data(), a = aprobadas[varianteRef.id];
              if (!a || a.activa !== true) continue;
              const disponibilidad = estado(policy, v.stock);
              if (!disponibilidad) continue;
              estados.push(disponibilidad);
              nuevasVariantes.push({id:varianteRef.id, data:{...a, estadoDisponibilidad:disponibilidad, actualizadoEn:ahora}});
            }
          } else {
            const disponibilidad = estado(policy, data.stock);
            if (disponibilidad) estados.push(disponibilidad);
          }
          if (estados.length) {
            base.estadoDisponibilidad = estados.includes('disponible') ? 'disponible' : estados.includes('a_pedido') ? 'a_pedido' : 'agotado';
            base.actualizadoEn = ahora;
            proyeccion = base;
          }
          const idsPublicos = new Set(nuevasVariantes.map(v => v.id));
          for (const variante of consulta.variantesPublicas.docs) if (!idsPublicos.has(variante.id)) tx.delete(variante.ref);
          for (const variante of nuevasVariantes) tx.set(consulta.publicRef.collection('variantes').doc(variante.id), variante.data);
          if (proyeccion) tx.set(consulta.publicRef, proyeccion); else tx.delete(consulta.publicRef);
          cambios.push({productoId:consulta.item.productoId, disponible:true, actualizacionCatalogo:proyeccion ? 'restaurado' : 'agotado_oculto'});
        }
        const resultado = {operacionId:d.operacionId, disponible:d.disponible, cantidad:cambios.length, cambios};
        tx.create(reciboRef, {firma,resultado,creadoEn:ahora});
        return resultado;
      });
    }

    if (accion === 'asignarImagenVariante') {
      const d = objeto(input);
      campos(d, ['tiendaId','productoId','varianteId','operacionId','imagen']);
      exigir(id(d.tiendaId) && id(d.productoId) && id(d.varianteId) && id(d.operacionId) && typeof d.imagen === 'string', 'datos-invalidos', 'Referencias de imagen inválidas.');
      exigir(procesarImagen, 'pendiente', 'El procesamiento público de imágenes no está configurado.');
      const storeRef = db.doc(`tiendasPrivadas/${d.tiendaId}`), productRef = storeRef.collection('productos').doc(d.productoId), variantRef = productRef.collection('variantes').doc(d.varianteId);
      const publicProductRef = db.doc(`tiendasPublicas/${d.tiendaId}/productos/${d.productoId}`), publicVariantRef = publicProductRef.collection('variantes').doc(d.varianteId);
      const accessRef = db.doc(`accesos/${identidad.uid}`), receiptRef = storeRef.collection('operacionesProductos').doc(d.operacionId), approvalRef = productRef.collection('publicacion').doc('vigente');
      const publicStoreRef = db.doc(`tiendasPublicas/${d.tiendaId}`);
      const [access, store, product, variant, publicStore] = await Promise.all([accessRef.get(), storeRef.get(), productRef.get(), variantRef.get(), publicStoreRef.get()]);
      exigir(access.data()?.estado === 'activo' && access.data()?.roles?.includes('emprendedora') && access.data()?.tiendaId === d.tiendaId && store.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
      exigir(product.exists && variant.exists, 'no-encontrado', 'No se encontró el producto o variante.');
      const imagePath = d.imagen as string;
      exigir(Array.isArray(product.data()?.imagenes) && product.data()!.imagenes.includes(imagePath), 'imagen-no-autorizada', 'La imagen debe estar guardada en la ficha privada del producto.');
      const productoPublicado = product.data()?.estadoPublicacion === 'publicado' && store.data()?.estadoPublicacion === 'publicado' && publicStore.data()?.estadoPublicacion === 'publicado' && publicStore.data()?.habilitada === true;
      const prepared = productoPublicado ? await procesarImagen(imagePath, d.tiendaId as string, d.productoId as string) : undefined;
      if (productoPublicado) exigir(prepared && typeof prepared === 'object', 'imagen-no-autorizada', 'No se pudo preparar la imagen de la variante.');
      const firma = createHash('sha256').update(canon({uid:identidad.uid,tiendaId:d.tiendaId,productoId:d.productoId,varianteId:d.varianteId,imagen:imagePath})).digest('hex');
      const confirmado = await db.runTransaction(async tx => {
        const [currentAccess, currentStore, currentProduct, currentVariant, receipt, approval, publicProduct, publicVariant, currentPublicStore] = await tx.getAll(accessRef,storeRef,productRef,variantRef,receiptRef,approvalRef,publicProductRef,publicVariantRef,publicStoreRef);
        exigir(currentAccess.data()?.estado === 'activo' && currentAccess.data()?.roles?.includes('emprendedora') && currentAccess.data()?.tiendaId === d.tiendaId && currentStore.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'La cuenta perdió acceso a la tienda.');
        if (receipt.exists) { exigir(receipt.data()?.firma === firma, 'id-reutilizado', 'operacionId corresponde a otra solicitud.'); return {resultado:receipt.data()!.resultado, limpieza:receipt.data()?.limpiezaImagenes as string[]|undefined}; }
        exigir(currentProduct.exists && currentVariant.exists && currentProduct.data()?.imagenes?.includes(imagePath), 'conflicto', 'El producto cambió; vuelve a cargarlo.');
        const now = FieldValue.serverTimestamp();
        const preparadaActual = prepared && typeof prepared === 'object' ? prepared : undefined;
        const publicadaAhora = Boolean(preparadaActual && currentProduct.data()?.estadoPublicacion === 'publicado' && currentStore.data()?.estadoPublicacion === 'publicado' && currentPublicStore.data()?.estadoPublicacion === 'publicado' && currentPublicStore.data()?.habilitada === true);
        if (publicadaAhora) exigir(preparadaActual, 'imagen-no-autorizada', 'La imagen pública no pudo validarse.');
        const imagenPublicaAnterior = publicVariant.data()?.imagen;
        const miniaturaAnterior = publicVariant.data()?.miniatura;
        tx.update(variantRef, {imagen:imagePath, imagenPublica:publicadaAhora ? preparadaActual!.imagen : FieldValue.delete(), miniaturaPublica:publicadaAhora ? preparadaActual!.miniatura : FieldValue.delete(), actualizadoEn:now});
        tx.update(productRef, {actualizadoEn:now});
        if (approval.exists && publicadaAhora) {
          const approvedVariants = {...(approval.data()?.variantes ?? {})};
          approvedVariants[d.varianteId as string] = {...approvedVariants[d.varianteId as string], imagen:preparadaActual!.imagen, miniatura:preparadaActual!.miniatura};
          tx.update(approvalRef, {variantes:approvedVariants});
        }
        if (publicVariant.exists && publicadaAhora) tx.set(publicVariantRef, {...publicVariant.data(), imagen:preparadaActual!.imagen, miniatura:preparadaActual!.miniatura, actualizadoEn:now});
        const resultado = {accion, productoId:d.productoId, varianteId:d.varianteId, operacionId:d.operacionId};
        const limpieza = publicadaAhora ? [imagenPublicaAnterior,miniaturaAnterior].filter((value): value is string => typeof value === 'string' && value !== preparadaActual!.imagen && value !== preparadaActual!.miniatura) : [];
        tx.create(receiptRef, {firma,resultado,limpiezaImagenes:limpieza,creadoEn:now});
        return {resultado,limpieza};
      });
      if (confirmado.limpieza?.length && limpiarImagenesPublicas) await limpiarImagenesPublicas(confirmado.limpieza);
      return confirmado.resultado;
    }

    if (accion === 'eliminarProducto') {
      const d = objeto(input);
      campos(d, ['tiendaId', 'productoId', 'operacionId', 'versionEsperada']);
      exigir(id(d.tiendaId) && id(d.productoId) && id(d.operacionId), 'datos-invalidos', 'IDs inválidos.');
      exigir(typeof d.versionEsperada === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(d.versionEsperada), 'datos-invalidos', 'Versión inválida.');
      const firma = createHash('sha256').update(canon({accion, uid: identidad.uid, tiendaId: d.tiendaId, productoId: d.productoId, versionEsperada: d.versionEsperada})).digest('hex');
      const storeRef = db.doc(`tiendasPrivadas/${d.tiendaId}`);
      const productRef = storeRef.collection('productos').doc(d.productoId);
      const publicRef = db.doc(`tiendasPublicas/${d.tiendaId}/productos/${d.productoId}`);
      const approvalRef = productRef.collection('publicacion').doc('vigente');
      const receiptRef = storeRef.collection('operacionesProductos').doc(d.operacionId);
      const confirmado = await db.runTransaction(async tx => {
        const [access, store, product, receipt, publicProduct, approval] = await tx.getAll(
          db.doc(`accesos/${identidad.uid}`), storeRef, productRef, receiptRef, publicRef, approvalRef
        );
        const a = access.data();
        exigir(a?.estado === 'activo' && Array.isArray(a.roles) && a.roles.includes('emprendedora') && a.tiendaId === d.tiendaId && store.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere la propietaria activa de esta tienda.');
        if (receipt.exists) {
          exigir(receipt.data()?.firma === firma, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
          return {resultado: receipt.data()!.resultado as Record<string, unknown>, limpieza: receipt.data()?.limpiezaImagenes as string[] | undefined};
        }
        const data = product.data();
        exigir(data, 'no-encontrado', 'El producto ya no existe en esta tienda.');
        exigir(data.estadoPublicacion !== 'eliminado', 'conflicto', 'El producto ya fue eliminado.');
        exigir(version(data) === d.versionEsperada, 'conflicto', 'El producto cambió; vuelve a cargar la lista e inténtalo de nuevo.');

        const publicVariants = await tx.get(publicRef.collection('variantes').limit(21));
        exigir(publicVariants.size <= 20, 'limite-local', 'Este incremento admite hasta 20 variantes públicas por producto.');
        const now = FieldValue.serverTimestamp();
        const result = {accion, productoId: d.productoId, operacionId: d.operacionId, estadoPublicacion: 'eliminado'};
        const projection = publicProduct.data();
        const approved = approval.data()?.producto as DocumentData | undefined;
        const paths = new Set<string>();
        for (const value of [
          ...(Array.isArray(projection?.imagenes) ? projection.imagenes : []),
          ...(Array.isArray(projection?.miniaturas) ? projection.miniaturas : []),
          ...(Array.isArray(approved?.imagenes) ? approved.imagenes : []),
          ...(Array.isArray(approved?.miniaturas) ? approved.miniaturas : []),
        ]) if (typeof value === 'string') paths.add(value);
        for (const variant of publicVariants.docs) {
          for (const value of [variant.get('imagen'), variant.get('miniatura')]) if (typeof value === 'string') paths.add(value);
        }
        const approvedVariants = approval.data()?.variantes as Record<string, DocumentData> | undefined;
        for (const variant of Object.values(approvedVariants ?? {})) {
          for (const value of [variant.imagen, variant.miniatura]) if (typeof value === 'string') paths.add(value);
        }

        for (const variant of publicVariants.docs) tx.delete(variant.ref);
        if (approval.exists) tx.delete(approvalRef);
        if (publicProduct.exists) tx.delete(publicRef);
        // Tombstone privado: desaparece de la aplicación, pero conserva ficha,
        // variantes, imágenes originales e historial de stock para trazabilidad.
        tx.update(productRef, {estadoPublicacion: 'eliminado', disponible: false, eliminadoEn: now, actualizadoEn: now});
        const limpieza = [...paths];
        tx.create(receiptRef, {firma, resultado: result, limpiezaImagenes: limpieza, creadoEn: now});
        return {resultado: result, limpieza};
      });
      if (confirmado.limpieza?.length && limpiarImagenesPublicas) await limpiarImagenesPublicas(confirmado.limpieza);
      return confirmado.resultado;
    }

    const crear = accion === 'crearProducto';
    const d = objeto(input);
    campos(d, ['tiendaId','productoId','operacionId','producto','variantes', ...(!crear ? ['versionEsperada'] : [])]);
    exigir(id(d.tiendaId) && id(d.productoId) && id(d.operacionId), 'datos-invalidos', 'IDs inválidos.');
    if (!crear) exigir(typeof d.versionEsperada === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(d.versionEsperada), 'datos-invalidos', 'Versión inválida.');
    const p = objeto(d.producto);
    campos(p, ['nombre','descripcion','tipoProductoId','versionTipoProducto','categoriaId','atributosEspecificos','tieneVariantes','moneda','unidadVenta','pasoCantidad','modalidad','manejaStock','alAgotarse'], ['precioBase', 'stockInicial']);
    texto(p.nombre,120); texto(p.descripcion,2000);
    exigir(id(p.tipoProductoId) && id(p.categoriaId) && Number.isSafeInteger(p.versionTipoProducto) && p.versionTipoProducto > 0, 'datos-invalidos', 'Referencias inválidas.');
    exigir(p.moneda === 'CLP' && p.unidadVenta === 'unidad' && p.pasoCantidad === 1, 'datos-invalidos', 'Solo CLP y unidades enteras.');
    exigir(typeof p.tieneVariantes === 'boolean' && typeof p.manejaStock === 'boolean', 'datos-invalidos', 'Indicadores booleanos requeridos.');
    exigir(['regular','a_pedido','pieza_unica'].includes(p.modalidad) && ['mostrar_agotado','ocultar','pasar_a_pedido'].includes(p.alAgotarse), 'datos-invalidos', 'Modalidad o política inválida.');
    if (p.modalidad === 'pieza_unica') exigir(!p.tieneVariantes && p.manejaStock, 'datos-invalidos', 'Pieza única sin variantes y con stock.');
    const attrs = objeto(p.atributosEspecificos);
    exigir(Array.isArray(d.variantes) && d.variantes.length <= 20, 'datos-invalidos', 'Máximo 20 variantes por solicitud.');
    exigir(p.tieneVariantes ? d.variantes.length > 0 && !('precioBase' in p) && !('stockInicial' in p) : d.variantes.length === 0, 'datos-invalidos', 'Precio, stock o variantes incompatibles.');
    if (!p.tieneVariantes) entero(p.precioBase);
    const variants = d.variantes.map((v: unknown) => {
      const x = objeto(v); campos(x, ['varianteId','opciones','precio','activa'], ['stockInicial']);
      exigir(id(x.varianteId) && typeof x.activa === 'boolean', 'datos-invalidos', 'Variante inválida.');
      objeto(x.opciones); entero(x.precio); return x;
    });
    exigir(new Set(variants.map((v: any) => v.varianteId)).size === variants.length, 'datos-invalidos', 'IDs de variantes repetidos.');
    const firma = createHash('sha256').update(canon({accion, uid: identidad.uid, solicitud: d})).digest('hex');
    const storeRef = db.doc(`tiendasPrivadas/${d.tiendaId}`);
    const ref = storeRef.collection('productos').doc(d.productoId);
    const receiptRef = storeRef.collection('operacionesProductos').doc(d.operacionId);
    const publicStoreRef = db.doc(`tiendasPublicas/${d.tiendaId}`);
    const publicRef = publicStoreRef.collection('productos').doc(d.productoId);
    const approvalRef = ref.collection('publicacion').doc('vigente');
    return db.runTransaction(async tx => {
      const [access, store, source, receipt, type, definition, category, publicSource, publicStore] = await tx.getAll(
        db.doc(`accesos/${identidad.uid}`), storeRef, ref, receiptRef,
        db.doc(`tiposProducto/${p.tipoProductoId}`), db.doc(`tiposProducto/${p.tipoProductoId}/versiones/${p.versionTipoProducto}`),
        db.doc(`categorias/${p.categoriaId}`), publicRef, publicStoreRef,
      );
      const a = access.data();
      exigir(a?.estado === 'activo' && Array.isArray(a.roles) && a.roles.includes('emprendedora') && a.tiendaId === d.tiendaId && store.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
      if (receipt.exists) {
        exigir(receipt.data()!.firma === firma, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
        return receipt.data()!.resultado as {accion: AccionProducto; productoId: string; operacionId: string};
      }
      const oldVariants = await tx.get(ref.collection('variantes').limit(21));
      exigir(oldVariants.size <= 20, 'limite-local', 'Este editor admite hasta 20 variantes.');
      const old = source.data();
      if (crear) exigir(!source.exists && !publicSource.exists && oldVariants.empty, 'conflicto', 'El producto o sus datos ya existen.');
      else {
        exigir(old, 'no-encontrado', 'Producto inexistente.');
        exigir(version(old) === d.versionEsperada, 'conflicto', 'La ficha cambió; volver a leer.');
        for (const key of ['tipoProductoId','versionTipoProducto','tieneVariantes','manejaStock','modalidad','moneda','unidadVenta','pasoCantidad']) {
          exigir(old[key] === p[key], 'conflicto', 'Cambiar tipo, modalidad o estructura requiere una migración posterior.');
        }
        exigir(!old.piezaUnicaVendida, 'conflicto', 'No se edita una pieza única vendida.');
      }
      const referenciasVigentes = type.data()?.activo === true && category.data()?.activo === true;
      const conservaReferenciasHistoricas = !crear && old?.tipoProductoId === p.tipoProductoId
        && old?.categoriaId === p.categoriaId && old?.versionTipoProducto === p.versionTipoProducto;
      exigir(definition.exists && (referenciasVigentes || conservaReferenciasHistoricas), 'datos-invalidos', 'Tipo, versión o categoría inexistente/inactiva para una ficha nueva.');
      const def = definition.data()!;
      exigir(Array.isArray(def.campos) && Array.isArray(def.opcionesVariante), 'datos-invalidos', 'Definición de tipo inválida.');
      exigir(Object.keys(attrs).every(k => def.campos.some((f: any) => f.clave === k)), 'datos-invalidos', 'Atributo no definido para el tipo.');
      for (const f of def.campos) {
        exigir(f.tipo === 'texto', 'pendiente', 'Tipo de campo aún no soportado por el editor.');
        if (Object.hasOwn(attrs, f.clave)) texto(attrs[f.clave],500);
        else exigir(!f.obligatorio, 'datos-invalidos', `Falta atributo ${f.clave}.`);
      }
      const now = FieldValue.serverTimestamp();
      const saved: DocumentData = {...p}; delete saved.stockInicial;
      const stock = (incoming: Record<string,any>, existing?: DocumentData) => {
        if (existing || !p.manejaStock) exigir(!('stockInicial' in incoming), 'datos-invalidos', 'No se permite modificar stock ni introducirlo sin manejo de stock.');
        else {entero(incoming.stockInicial); if (p.modalidad === 'pieza_unica') exigir(incoming.stockInicial === 1, 'datos-invalidos', 'Pieza única requiere una unidad.');}
        if (!p.manejaStock) return undefined;
        const quantity = existing ? existing.stock : incoming.stockInicial;
        entero(quantity);
        return quantity;
      };
      if (!p.tieneVariantes) {const n = stock(p,old); if (n !== undefined) {entero(n); saved.stock=n;}}
      const previous = new Map(oldVariants.docs.map(v => [v.id,v.data()]));
      exigir([...previous.keys()].every(k => variants.some((v: any) => v.varianteId === k)), 'datos-invalidos', 'No omitir variantes existentes; usar activa=false.');
      const combinations = new Set<string>();
      const writes: {id: string; data: DocumentData}[] = [];
      for (const v of variants) {
        exigir(def.opcionesVariante.length > 0, 'datos-invalidos', 'Este tipo no define opciones de variante.');
        campos(v.opciones,def.opcionesVariante);
        for (const val of Object.values(v.opciones)) texto(val,100);
        const combination = canon(Object.fromEntries(Object.entries(v.opciones).map(([k,val]) => [k,(val as string).trim().toLowerCase()])));
        exigir(!combinations.has(combination), 'datos-invalidos', 'Combinación de opciones repetida.'); combinations.add(combination);
        const prev = previous.get(v.varianteId);
        if (prev) exigir(canon(prev.opciones) === canon(v.opciones), 'conflicto', 'Las opciones identifican la variante; crear otra para cambiarlas.');
        const n = stock(v,prev);
        const data: DocumentData = {opciones:v.opciones,precio:v.precio,activa:v.activa,creadoEn:prev?.creadoEn ?? now,actualizadoEn:now,
          ...(prev?.imagen ? {imagen:prev.imagen} : {}), ...(prev?.imagenPublica ? {imagenPublica:prev.imagenPublica} : {}), ...(prev?.miniaturaPublica ? {miniaturaPublica:prev.miniaturaPublica} : {})};
        if (n !== undefined) {entero(n); data.stock=n;}
        writes.push({id:v.varianteId,data});
      }
      saved.actualizadoEn=now;
      const sincronizarCatalogo = !crear
        && old?.estadoPublicacion === 'publicado'
        && store.data()?.estadoPublicacion === 'publicado'
        && publicStore.data()?.estadoPublicacion === 'publicado'
        && publicStore.data()?.habilitada === true
        && publicSource.exists;
      let proyeccion: DocumentData | null = null;
      let variantesPublicas: {id: string; data: DocumentData}[] = [];
      let snapshotAprobado: DocumentData | null = null;
      let publicVariants: QueryDocumentSnapshot[] = [];
      if (sincronizarCatalogo) {
        const publicData = publicSource.data()!;
        const imagenesPublicas = Array.isArray(publicData.imagenes)
          ? publicData.imagenes.filter((ruta: unknown): ruta is string => typeof ruta === 'string')
          : [];
        const miniaturasPublicas = Array.isArray(publicData.miniaturas)
          ? publicData.miniaturas.filter((ruta: unknown): ruta is string => typeof ruta === 'string')
          : [];
        const base: DocumentData = {
          nombre: p.nombre,
          descripcion: p.descripcion,
          tipoProductoId: p.tipoProductoId,
          versionTipoProducto: p.versionTipoProducto,
          categoriaId: p.categoriaId,
          imagenes: imagenesPublicas,
          ...(miniaturasPublicas.length ? {miniaturas: miniaturasPublicas} : {}),
          atributosEspecificos: attrs,
          tieneVariantes: p.tieneVariantes,
          moneda: 'CLP',
          unidadVenta: 'unidad',
          modalidad: p.modalidad,
          estadoPublicacion: 'publicado',
          actualizadoEn: now,
        };
        if (!p.tieneVariantes) base.precioBase = p.precioBase;
        const politica = {modalidad: p.modalidad, manejaStock: p.manejaStock, alAgotarse: p.alAgotarse};
        const estadosDisponibilidad: string[] = [];
        const aprobadas: Record<string, DocumentData> = Object.create(null);
        if (p.tieneVariantes) {
          for (const variante of writes) {
            if (!variante.data.activa) continue;
            const aprobada = {opciones: variante.data.opciones, precio: variante.data.precio, activa: true,
              ...(variante.data.imagenPublica ? {imagen:variante.data.imagenPublica} : {}), ...(variante.data.miniaturaPublica ? {miniatura:variante.data.miniaturaPublica} : {})};
            aprobadas[variante.id] = aprobada;
            const disponibilidad = estado(saved, variante.data.stock);
            if (!disponibilidad) continue;
            estadosDisponibilidad.push(disponibilidad);
            variantesPublicas.push({
              id: variante.id,
              data: {...aprobada, ...(variante.data.imagenPublica ? {imagen:variante.data.imagenPublica} : {}), ...(variante.data.miniaturaPublica ? {miniatura:variante.data.miniaturaPublica} : {}), estadoDisponibilidad: disponibilidad, actualizadoEn: now},
            });
          }
        } else {
          const disponibilidad = estado(saved, saved.stock);
          if (disponibilidad) {
            estadosDisponibilidad.push(disponibilidad);
          }
        }
        if (estadosDisponibilidad.length) {
          base.estadoDisponibilidad = estadosDisponibilidad.includes('disponible')
            ? 'disponible'
            : estadosDisponibilidad.includes('a_pedido') ? 'a_pedido' : 'agotado';
          proyeccion = base;
        }
        snapshotAprobado = {
          schema: 1,
          cicloCatalogo: store.data()?.cicloCatalogo ?? 0,
          politica,
          producto: base,
          variantes: aprobadas,
          aprobadoEn: now,
        };
        const publicVariantSnapshot = await tx.get(publicRef.collection('variantes').limit(101));
        exigir(publicVariantSnapshot.size <= 100, 'limite-local', 'Demasiadas variantes públicas; no se aplicó la edición.');
        publicVariants = publicVariantSnapshot.docs;
      }
      if (crear) tx.create(ref,{...saved,imagenes:[],disponible:true,piezaUnicaVendida:false,estadoPublicacion:'borrador',creadoEn:now});
      else tx.update(ref,saved);
      for (const v of writes) tx.set(ref.collection('variantes').doc(v.id),v.data);
      if (sincronizarCatalogo) {
        const nuevasVariantes = new Set(variantesPublicas.map(v => v.id));
        for (const variante of publicVariants) if (!nuevasVariantes.has(variante.id)) tx.delete(variante.ref);
        for (const variante of variantesPublicas) tx.set(publicRef.collection('variantes').doc(variante.id), variante.data);
        if (proyeccion) tx.set(publicRef, proyeccion);
        else tx.delete(publicRef);
        tx.set(approvalRef, snapshotAprobado!);
      }
      const resultado = {
        accion,
        productoId:d.productoId,
        operacionId:d.operacionId,
        ...(sincronizarCatalogo ? {actualizacionCatalogo: proyeccion ? 'actualizado' : 'oculto'} : {}),
      };
      tx.create(receiptRef,{firma,resultado,creadoEn:now});
      return resultado;
    });
  };
}
