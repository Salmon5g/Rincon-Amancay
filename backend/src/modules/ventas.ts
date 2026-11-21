import { createHash } from 'node:crypto';
import { FieldPath, FieldValue } from 'firebase-admin/firestore';
import type { DocumentSnapshot, Firestore, QueryDocumentSnapshot, QuerySnapshot } from 'firebase-admin/firestore';
import { ErrorOperacion, estado } from './publicacion.ts';
import { crearProductoSinPrecios, crearVarianteSinPrecio } from './vista-sin-precios.ts';

function exigir(ok: unknown, code: string, message: string): asserts ok { if (!ok) throw new ErrorOperacion(code, message); }
function id(v: unknown): v is string { return typeof v === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(v); }
function texto(v: unknown, max: number, label: string): string { exigir(typeof v === 'string' && v.trim().length > 0 && v.trim().length <= max, 'datos-invalidos', `${label} es obligatorio.`); return v.trim(); }

export const accionesVenta = ['registrarVenta', 'configurarHistorialVentas', 'configurarValoresDefecto', 'configurarMostrarPrecios', 'configurarAvisosContacto', 'configurarCalificaciones'] as const;
export type AccionVenta = typeof accionesVenta[number];

async function prepararVistasSinPrecios(db: Firestore, tiendaId: string) {
  const tiendaPublica = db.doc(`tiendasPublicas/${tiendaId}`);
  const productosPublicos = tiendaPublica.collection('productos');
  let cursor: QueryDocumentSnapshot | undefined;
  while (true) {
    let consulta = productosPublicos.orderBy(FieldPath.documentId()).limit(25);
    if (cursor) consulta = consulta.startAfter(cursor);
    const pagina = await consulta.get();
    if (pagina.empty) return;
    for (const referencia of pagina.docs) {
      const productoConsulta = tiendaPublica.collection('productosConsulta').doc(referencia.id);
      await db.runTransaction(async tx => {
        const [tienda, producto, consultaExistente] = await tx.getAll(tiendaPublica, referencia.ref, productoConsulta);
        const datos = producto.data();
        if (tienda.data()?.estadoPublicacion !== 'publicado' || tienda.data()?.habilitada !== true) return;
        if (!datos || datos.estadoPublicacion !== 'publicado') {
          const variantesAntiguas = await tx.get(productoConsulta.collection('variantes').limit(101));
          exigir(variantesAntiguas.size <= 100, 'limite-local', 'Hay demasiadas variantes públicas para completar la proyección sin precios.');
          for (const variante of variantesAntiguas.docs) tx.delete(variante.ref);
          if (consultaExistente.exists) tx.delete(productoConsulta);
          return;
        }
        const variantesPublicas = await tx.get(referencia.ref.collection('variantes').limit(101));
        const variantesAnteriores = await tx.get(productoConsulta.collection('variantes').limit(101));
        exigir(variantesPublicas.size <= 20 && variantesAnteriores.size <= 100, 'limite-local', 'Hay demasiadas variantes públicas para completar la proyección sin precios.');
        const idsActuales = new Set(variantesPublicas.docs.map(variante => variante.id));
        for (const variante of variantesAnteriores.docs) if (!idsActuales.has(variante.id)) tx.delete(variante.ref);
        tx.set(productoConsulta, crearProductoSinPrecios(datos));
        for (const variante of variantesPublicas.docs) tx.set(productoConsulta.collection('variantes').doc(variante.id), crearVarianteSinPrecio(variante.data()));
      });
    }
    cursor = pagina.docs[pagina.docs.length - 1];
  }
}

export function crearVentas(db: Firestore) {
  return async (accion: AccionVenta, identidad: {uid: string}, input: unknown) => {
    exigir(input && typeof input === 'object' && !Array.isArray(input), 'datos-invalidos', 'Solicitud inválida.');
    const d = input as Record<string, unknown>;
    exigir(id(d.tiendaId), 'datos-invalidos', 'Tienda inválida.');
    const tienda = db.doc(`tiendasPrivadas/${d.tiendaId}`);
    const configuracion = tienda.collection('configuracion').doc('general');
    const accesoRef = db.doc(`accesos/${identidad.uid}`);
    if (accion === 'configurarAvisosContacto') {
      exigir(Object.keys(d).length === 5 && typeof d.habilitado === 'boolean' &&
        typeof d.horaInicio === 'string' && /^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(d.horaInicio) &&
        typeof d.horaFin === 'string' && /^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(d.horaFin) &&
        typeof d.zonaHoraria === 'string' && d.zonaHoraria.length <= 64, 'datos-invalidos', 'Revisa la preferencia y el horario de avisos.');
      try { new Intl.DateTimeFormat('es-CL', { timeZone: d.zonaHoraria as string }); }
      catch { throw new ErrorOperacion('datos-invalidos', 'La zona horaria no es válida.'); }
      return db.runTransaction(async tx => {
        const [access, shop, config] = await tx.getAll(accesoRef, tienda, configuracion);
        exigir(access.data()?.estado === 'activo' && access.data()?.roles?.includes('emprendedora') && access.data()?.tiendaId === d.tiendaId && shop.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
        exigir(config.exists, 'no-encontrado', 'No existe la configuración de la tienda.');
        const notificacionesContacto = { habilitado: d.habilitado, horaInicio: d.horaInicio, horaFin: d.horaFin, zonaHoraria: d.zonaHoraria };
        tx.update(configuracion, { notificacionesContacto, actualizadoEn: FieldValue.serverTimestamp() });
        return notificacionesContacto;
      });
    }
    if (accion === 'configurarCalificaciones') {
      exigir(Object.keys(d).length === 2 && typeof d.habilitado === 'boolean', 'datos-invalidos', 'Indica si las calificaciones están activas.');
      const tiendaPublica = db.doc(`tiendasPublicas/${d.tiendaId}`);
      return db.runTransaction(async tx => {
        const [access, shop, config, publicShop] = await tx.getAll(accesoRef, tienda, configuracion, tiendaPublica);
        exigir(access.data()?.estado === 'activo' && access.data()?.roles?.includes('emprendedora') && access.data()?.tiendaId === d.tiendaId && shop.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
        exigir(config.exists, 'no-encontrado', 'No existe la configuración de la tienda.');
        tx.update(configuracion, { calificacionesHabilitadas: d.habilitado, actualizadoEn: FieldValue.serverTimestamp() });
        if (publicShop.exists && publicShop.get('estadoPublicacion') === 'publicado' && publicShop.get('habilitada') === true) {
          tx.update(tiendaPublica, { calificacionesHabilitadas: d.habilitado, actualizadoEn: FieldValue.serverTimestamp() });
        }
        return { calificacionesHabilitadas: d.habilitado };
      });
    }
    if (accion === 'configurarMostrarPrecios') {
      exigir(Object.keys(d).length === 2 && typeof d.mostrarPrecios === 'boolean', 'datos-invalidos', 'Se requiere el estado de visibilidad de precios.');
      const tiendaPublica = db.doc(`tiendasPublicas/${d.tiendaId}`);
      const [accesoInicial, tiendaInicial, configuracionInicial, tiendaPublicaInicial] = await Promise.all([
        accesoRef.get(), tienda.get(), configuracion.get(), tiendaPublica.get(),
      ]);
      const validarPropiedad = (acceso: DocumentSnapshot, ficha: DocumentSnapshot) => {
        exigir(acceso.data()?.estado === 'activo' && acceso.data()?.roles?.includes('emprendedora') && acceso.data()?.tiendaId === d.tiendaId && ficha.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
      };
      validarPropiedad(accesoInicial, tiendaInicial);
      exigir(configuracionInicial.exists, 'no-encontrado', 'No existe la configuración de la tienda.');
      if (d.mostrarPrecios === false && tiendaInicial.data()?.estadoPublicacion === 'publicado'
        && tiendaPublicaInicial.data()?.estadoPublicacion === 'publicado' && tiendaPublicaInicial.data()?.habilitada === true) {
        await prepararVistasSinPrecios(db, d.tiendaId);
      }
      return db.runTransaction(async tx => {
        const [acceso, ficha, config, publica] = await tx.getAll(accesoRef, tienda, configuracion, tiendaPublica);
        validarPropiedad(acceso, ficha);
        exigir(config.exists, 'no-encontrado', 'No existe la configuración de la tienda.');
        const catalogoPublicado = ficha.data()?.estadoPublicacion === 'publicado'
          && publica.data()?.estadoPublicacion === 'publicado' && publica.data()?.habilitada === true;
        tx.update(configuracion, {mostrarPrecios: d.mostrarPrecios, actualizadoEn: FieldValue.serverTimestamp()});
        if (catalogoPublicado) tx.update(tiendaPublica, {mostrarPrecios: d.mostrarPrecios, actualizadoEn: FieldValue.serverTimestamp()});
        return {mostrarPrecios: d.mostrarPrecios, catalogoPublicado};
      });
    }
    if (accion === 'configurarHistorialVentas') {
      exigir(Object.keys(d).length === 2 && typeof d.historialVentasActivo === 'boolean', 'datos-invalidos', 'Se requiere el estado del historial.');
      return db.runTransaction(async tx => {
        const [acceso, shop, config] = await tx.getAll(accesoRef, tienda, configuracion);
        exigir(acceso.data()?.estado === 'activo' && acceso.data()?.roles?.includes('emprendedora') && acceso.data()?.tiendaId === d.tiendaId && shop.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
        exigir(config.exists, 'no-encontrado', 'No existe la configuración de la tienda.');
        tx.update(configuracion, {historialVentasActivo: d.historialVentasActivo, actualizadoEn: FieldValue.serverTimestamp()});
        return {historialVentasActivo: d.historialVentasActivo};
      });
    }
    if (accion === 'configurarValoresDefecto') {
      exigir(typeof d.modalidad === 'string' && ['regular','a_pedido','pieza_unica'].includes(d.modalidad), 'datos-invalidos', 'Modalidad inválida.');
      exigir(typeof d.manejaStock === 'boolean', 'datos-invalidos', 'Maneja stock inválido.');
      exigir(typeof d.alAgotarse === 'string' && ['mostrar_agotado','ocultar','pasar_a_pedido'].includes(d.alAgotarse), 'datos-invalidos', 'Regla al agotarse inválida.');
      return db.runTransaction(async tx => {
        const [acceso, shop, config] = await tx.getAll(accesoRef, tienda, configuracion);
        exigir(acceso.data()?.estado === 'activo' && acceso.data()?.roles?.includes('emprendedora') && acceso.data()?.tiendaId === d.tiendaId && shop.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
        exigir(config.exists, 'no-encontrado', 'No existe la configuración de la tienda.');
        tx.update(configuracion, {modalidadDefecto: d.modalidad, manejaStockDefecto: d.manejaStock, alAgotarseDefecto: d.alAgotarse, actualizadoEn: FieldValue.serverTimestamp()});
        return {modalidad: d.modalidad, manejaStock: d.manejaStock, alAgotarse: d.alAgotarse};
      });
    }

    exigir(Object.keys(d).every(k => ['tiendaId','operacionId','productoId','varianteId','cantidad','lugarTipo','feriaId','lugarTexto','fecha'].includes(k)), 'datos-invalidos', 'La solicitud contiene campos no permitidos.');
    exigir(id(d.operacionId) && id(d.productoId) && Number.isSafeInteger(d.cantidad) && Number(d.cantidad) > 0 && Number(d.cantidad) <= 10000, 'datos-invalidos', 'Producto, operación y cantidad válidos son obligatorios.');
    exigir(typeof d.varianteId === 'undefined' || id(d.varianteId), 'datos-invalidos', 'Variante inválida.');
    const fecha = typeof d.fecha === 'string' ? new Date(d.fecha) : new Date();
    exigir(Number.isFinite(fecha.getTime()), 'datos-invalidos', 'Fecha inválida.');
    const producto = tienda.collection('productos').doc(d.productoId as string);
    const variante = typeof d.varianteId === 'string' ? producto.collection('variantes').doc(d.varianteId) : undefined;
    const operacion = tienda.collection('operacionesVentas').doc(d.operacionId as string);
    const venta = tienda.collection('ventas').doc(d.operacionId as string);
    const feria = d.lugarTipo === 'feria' && id(d.feriaId) ? db.doc(`ferias/${d.feriaId}`) : undefined;
    const firma = createHash('sha256').update(JSON.stringify([identidad.uid,d.tiendaId,d.operacionId,d.productoId,d.varianteId ?? null,d.cantidad,d.lugarTipo,d.feriaId ?? null,d.lugarTexto ?? null,fecha.toISOString()])).digest('hex');
    const publicProduct = db.doc(`tiendasPublicas/${d.tiendaId}/productos/${d.productoId}`);
    const publicProductConsulta = db.doc(`tiendasPublicas/${d.tiendaId}/productosConsulta/${d.productoId}`);
    const publicShop = db.doc(`tiendasPublicas/${d.tiendaId}`);
    const aprobacion = producto.collection('publicacion').doc('vigente');
    return db.runTransaction(async tx => {
      const refs = [accesoRef, tienda, configuracion, producto, operacion, ...(variante ? [variante] : []), publicProduct, aprobacion, publicShop, publicProductConsulta, ...(feria ? [feria] : [])];
      const snapshots = await tx.getAll(...refs);
      const [acceso, shop, config, p, previous] = snapshots;
      exigir(acceso.data()?.estado === 'activo' && acceso.data()?.roles?.includes('emprendedora') && acceso.data()?.tiendaId === d.tiendaId && shop.data()?.propietarioUid === identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
      exigir(previous.exists ? previous.data()?.firma === firma : true, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
      if (previous.exists) {
        const resultadoAnterior = previous.data()!.resultado as Record<string, unknown>;
        return {...resultadoAnterior, historialRegistrado: typeof resultadoAnterior.historialRegistrado === 'boolean' ? resultadoAnterior.historialRegistrado : true};
      }
      const historialRegistrado = config.data()?.historialVentasActivo === true;
      if (historialRegistrado) {
        exigir(d.lugarTipo === 'feria' ? id(d.feriaId) : d.lugarTipo === 'otro' && typeof d.lugarTexto === 'string' && d.lugarTexto.trim().length > 0 && d.lugarTexto.length <= 200, 'datos-invalidos', 'Indica una feria o un lugar.');
        if (feria) exigir(snapshots[snapshots.length - 1].exists && snapshots[snapshots.length - 1].data()?.estado === 'publicada', 'no-encontrado', 'La feria debe estar publicada en la plataforma.');
      }
      exigir(p.exists && p.data()?.estadoPublicacion !== 'eliminado', 'no-encontrado', 'Producto inexistente.');
      const data = p.data()!;
      exigir(data.manejaStock === false || Number.isSafeInteger(d.cantidad), 'datos-invalidos', 'Cantidad inválida.');
      exigir(Boolean(variante) === (data.tieneVariantes === true), 'datos-invalidos', 'Selecciona una variante solo para productos con variantes.');
      const indiceVariante = variante ? 5 : -1;
      const v = variante ? snapshots[indiceVariante] : undefined;
      if (variante) exigir(v?.exists && v.data()?.activa !== false, 'no-encontrado', 'Variante no disponible.');
      const saldo = variante ? v!.data()!.stock : data.stock;
      const precio = variante ? v!.data()!.precio : data.precioBase;
      exigir(Number.isSafeInteger(precio) && Number(precio) >= 0, 'datos-invalidos', 'El producto no tiene un precio válido.');
      if (data.manejaStock === true) exigir(Number.isSafeInteger(saldo) && Number(saldo) >= Number(d.cantidad), 'conflicto', 'No hay stock suficiente para registrar esta venta.');
      const total = Number(precio) * Number(d.cantidad);
      exigir(Number.isSafeInteger(total), 'datos-invalidos', 'El total excede el rango permitido.');
      const resultado: Record<string, unknown> = historialRegistrado
        ? {operacionId: d.operacionId, precioUnitario: Number(precio), cantidad: Number(d.cantidad), total, historialRegistrado}
        : {operacionId: d.operacionId, historialRegistrado};
      const now = FieldValue.serverTimestamp();
      const stockNuevo = data.manejaStock === true ? Number(saldo) - Number(d.cantidad) : undefined;
      let proyeccion: Record<string, unknown> | null = null;
      let proyeccionesVariantes: {id: string; data: Record<string, unknown>}[] = [];
      let requiereRepublicar = false;
      let actualizacionCatalogo = 'sin_publicacion';
      let variantesPublicasActuales: QuerySnapshot | undefined;
      let variantesConsultaActuales: QuerySnapshot | undefined;
      if (stockNuevo !== undefined) {
        const indiceProyeccion = variante ? 6 : 5;
        const pubSnap = snapshots[indiceProyeccion];
        const aprobacionData = snapshots[indiceProyeccion + 1].data();
        const tiendaPublica = snapshots[indiceProyeccion + 2];
        variantesPublicasActuales = await tx.get(publicProduct.collection('variantes').limit(101));
        variantesConsultaActuales = await tx.get(publicProductConsulta.collection('variantes').limit(101));
        exigir(variantesPublicasActuales.size <= 100, 'limite-local', 'Demasiadas variantes públicas; no se registró la venta.');
        exigir(variantesConsultaActuales.size <= 100, 'limite-local', 'Demasiadas variantes sin precio; no se registró la venta.');
        const sincronizar = data.disponible !== false
          && aprobacionData?.schema === 1
          && aprobacionData.cicloCatalogo === (shop.data()?.cicloCatalogo ?? 0)
          && shop.data()?.estadoPublicacion === 'publicado'
          && tiendaPublica.exists
          && data.estadoPublicacion === 'publicado';
        requiereRepublicar = data.disponible !== false && !sincronizar
          && (pubSnap.exists || !variantesPublicasActuales.empty || data.estadoPublicacion === 'publicado');
        if (sincronizar) {
          const policy = aprobacionData.politica;
          exigir(policy && ['regular','a_pedido','pieza_unica'].includes(policy.modalidad) && typeof policy.manejaStock === 'boolean' && ['mostrar_agotado','ocultar','pasar_a_pedido'].includes(policy.alAgotarse), 'conflicto', 'Publicación aprobada inconsistente.');
          const base = aprobacionData.producto as Record<string, unknown>;
          base.manejaStock = policy.manejaStock;
          if (base.tieneVariantes === true) {
            const variantesAprobadas = aprobacionData.variantes as Record<string, Record<string, unknown>>;
            const ids = Object.keys(variantesAprobadas);
            exigir(ids.length > 0 && ids.length <= 100, 'conflicto', 'Variantes aprobadas inválidas.');
            const saldos = await tx.getAll(...ids.map(key => producto.collection('variantes').doc(key)));
            const estados: string[] = [];
            proyeccionesVariantes = saldos.flatMap(saldoVariante => {
              exigir(saldoVariante.exists, 'conflicto', 'Falta una variante aprobada; revisar la publicación.');
              const nuevoSaldo = saldoVariante.id === d.varianteId ? stockNuevo : saldoVariante.data()!.stock;
              const disponibilidad = estado(policy, nuevoSaldo);
              if (!disponibilidad) return [];
              estados.push(disponibilidad);
              const stockPublico = policy.manejaStock ? nuevoSaldo : null;
              return [{id: saldoVariante.id, data: {...variantesAprobadas[saldoVariante.id], stock: stockPublico, estadoDisponibilidad: disponibilidad}}];
            });
            if (estados.length) proyeccion = {...base, estadoDisponibilidad: estados.includes('disponible') ? 'disponible' : estados.includes('a_pedido') ? 'a_pedido' : 'agotado'};
          } else {
            const disponibilidad = data.modalidad === 'pieza_unica' && stockNuevo === 0
              ? null
              : estado(policy, stockNuevo);
            base.stock = policy.manejaStock ? stockNuevo : null;
            if (disponibilidad) proyeccion = {...base, estadoDisponibilidad: disponibilidad};
          }
          actualizacionCatalogo = proyeccion ? 'actualizado' : 'oculto';
        } else actualizacionCatalogo = requiereRepublicar ? 'requiere_republicar' : 'sin_publicacion';
        resultado['actualizacionCatalogo'] = actualizacionCatalogo;
      }
      if (stockNuevo !== undefined) {
        if (variante) tx.update(variante, {stock: stockNuevo, actualizadoEn: now});
        tx.update(producto, { ...(variante ? {} : {stock: stockNuevo}), ...(data.modalidad === 'pieza_unica' ? {piezaUnicaVendida: true} : {}), actualizadoEn: now, ...(requiereRepublicar ? {estadoPublicacion: 'archivado'} : {}) });
        if (variantesPublicasActuales) {
          const ids = new Set(proyeccionesVariantes.map(item => item.id));
          for (const item of variantesPublicasActuales.docs) if (!ids.has(item.id)) tx.delete(item.ref);
          for (const item of variantesConsultaActuales!.docs) if (!ids.has(item.id)) tx.delete(item.ref);
          for (const item of proyeccionesVariantes) tx.set(publicProduct.collection('variantes').doc(item.id), {...item.data, actualizadoEn: now});
          for (const item of proyeccionesVariantes) tx.set(publicProductConsulta.collection('variantes').doc(item.id), crearVarianteSinPrecio({...item.data, actualizadoEn: now}));
          if (proyeccion) {
            tx.set(publicProduct, {...proyeccion, actualizadoEn: now});
            tx.set(publicProductConsulta, crearProductoSinPrecios({...proyeccion, actualizadoEn: now}));
          } else if (data.disponible === false || actualizacionCatalogo !== 'sin_publicacion') {
            tx.delete(publicProduct);
            tx.delete(publicProductConsulta);
          }
        }
      }
      if (historialRegistrado) {
        const datosFeria = feria ? snapshots[snapshots.length - 1].data() : undefined;
        tx.create(venta, {productoId: d.productoId, ...(variante ? {varianteId: d.varianteId, opcionesVariante: v!.data()!.opciones} : {}), productoNombre: data.nombre, precioUnitario: Number(precio), cantidad: Number(d.cantidad), total, fecha: fecha, lugarTipo: d.lugarTipo, ...(d.lugarTipo === 'feria' ? {feriaId: d.feriaId, feriaNombre: typeof datosFeria?.nombre === 'string' ? datosFeria.nombre : d.feriaId} : {lugarTexto: texto(d.lugarTexto,200,'Lugar') }), creadoPor: identidad.uid, creadoEn: now});
      }
      tx.create(operacion, {firma, resultado, creadoEn: now});
      return resultado;
    });
  };
}
