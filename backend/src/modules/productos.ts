import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import type { Firestore, DocumentData } from 'firebase-admin/firestore';
import { ErrorOperacion, version } from './publicacion.ts';

export type AccionProducto = 'crearProducto' | 'editarProducto';
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

export function crearProductos(db: Firestore) {
  return async (accion: AccionProducto, identidad: {uid: string}, input: unknown) => {
    exigir(accion === 'crearProducto' || accion === 'editarProducto', 'datos-invalidos', 'Acción inválida.');
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');
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
    return db.runTransaction(async tx => {
      const [access, store, source, receipt, type, definition, category, publicSource] = await tx.getAll(
        db.doc(`accesos/${identidad.uid}`), storeRef, ref, receiptRef,
        db.doc(`tiposProducto/${p.tipoProductoId}`), db.doc(`tiposProducto/${p.tipoProductoId}/versiones/${p.versionTipoProducto}`),
        db.doc(`categorias/${p.categoriaId}`), db.doc(`tiendasPublicas/${d.tiendaId}/productos/${d.productoId}`),
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
      exigir(type.data()?.activo === true && definition.exists && category.data()?.activo === true, 'datos-invalidos', 'Tipo, versión o categoría inexistente/inactiva.');
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
        const data: DocumentData = {opciones:v.opciones,precio:v.precio,activa:v.activa,creadoEn:prev?.creadoEn ?? now,actualizadoEn:now};
        if (n !== undefined) {entero(n); data.stock=n;}
        writes.push({id:v.varianteId,data});
      }
      saved.actualizadoEn=now;
      if (crear) tx.create(ref,{...saved,imagenes:[],disponible:true,piezaUnicaVendida:false,estadoPublicacion:'borrador',creadoEn:now});
      else tx.update(ref,saved);
      for (const v of writes) tx.set(ref.collection('variantes').doc(v.id),v.data);
      const resultado = {accion,productoId:d.productoId,operacionId:d.operacionId};
      tx.create(receiptRef,{firma,resultado,creadoEn:now});
      return resultado;
    });
  };
}
