import { createHash } from 'node:crypto';
import { FieldValue, type Firestore, type DocumentData } from 'firebase-admin/firestore';
import { ErrorOperacion, version } from './publicacion.ts';

function exigir(ok: unknown, code: string, message: string): asserts ok {
  if (!ok) throw new ErrorOperacion(code, message);
}

function objeto(value: unknown): Record<string, unknown> {
  exigir(value !== null && typeof value === 'object' && !Array.isArray(value), 'datos-invalidos', 'Se requiere un objeto.');
  return value as Record<string, unknown>;
}

function id(value: unknown): value is string {
  return typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value);
}

function campos(data: Record<string, unknown>, esperados: string[]) {
  exigir(esperados.every(key => Object.hasOwn(data, key)) && Object.keys(data).every(key => esperados.includes(key)), 'datos-invalidos', 'Campos ausentes o no permitidos.');
}

function texto(value: unknown, max: number, permitirVacio = false): string {
  exigir(typeof value === 'string' && value.length <= max && (permitirVacio || value.trim().length > 0), 'datos-invalidos', 'Texto inválido.');
  return value.trim();
}

function validarContacto(value: unknown): { tipo: 'whatsapp' | 'correo' | 'web'; valor: string } | null {
  if (value === null) return null;
  const data = objeto(value);
  campos(data, ['tipo', 'valor']);
  exigir(['whatsapp', 'correo', 'web'].includes(String(data.tipo)), 'datos-invalidos', 'Tipo de contacto inválido.');
  const tipo = data.tipo as 'whatsapp' | 'correo' | 'web';
  const valor = texto(data.valor, 254);
  if (tipo === 'whatsapp') exigir(/^\+?[0-9][0-9\s()-]{6,22}$/.test(valor), 'datos-invalidos', 'WhatsApp debe incluir un número válido con código de país.');
  if (tipo === 'correo') exigir(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(valor), 'datos-invalidos', 'Correo de contacto inválido.');
  if (tipo === 'web') {
    try {
      const url = new URL(valor);
      exigir(url.protocol === 'https:' && !url.username && !url.password, 'datos-invalidos', 'El sitio de contacto debe ser HTTPS.');
    } catch (error) {
      if (error instanceof ErrorOperacion) throw error;
      throw new ErrorOperacion('datos-invalidos', 'El sitio de contacto debe ser HTTPS.');
    }
  }
  return { tipo, valor };
}

function canon(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canon).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canon((value as Record<string, unknown>)[key])}`).join(',')}}`;
  return JSON.stringify(value);
}

export function crearEdicionTiendaEmprendedora(db: Firestore) {
  return async (identidad: { uid: string }, input: unknown) => {
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Sesión requerida.');
    const solicitud = objeto(input);
    // 'direccion' es un campo opcional de la ficha (lo usa la app combinada): los clientes
    // que aún no lo envían siguen siendo válidos y no modifican la dirección guardada.
    const CAMPOS_EDICION = ['tiendaId', 'operacionId', 'versionEsperada', 'nombre', 'descripcion', 'tipoEmprendimientoId', 'sectorId', 'contacto', 'mostrarContacto'];
    exigir(Object.keys(solicitud).every(clave => clave === 'direccion' || CAMPOS_EDICION.includes(clave)), 'datos-invalidos', 'Campos ausentes o no permitidos.');
    exigir(CAMPOS_EDICION.every(clave => Object.hasOwn(solicitud, clave)), 'datos-invalidos', 'Campos ausentes o no permitidos.');
    exigir(id(solicitud.tiendaId) && id(solicitud.operacionId) && id(solicitud.tipoEmprendimientoId) && id(solicitud.sectorId), 'datos-invalidos', 'Identificadores inválidos.');
    exigir(typeof solicitud.versionEsperada === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(solicitud.versionEsperada), 'datos-invalidos', 'Versión inválida.');
    const nombre = texto(solicitud.nombre, 120);
    const descripcion = texto(solicitud.descripcion, 2000, true);
    const llevaDireccion = Object.hasOwn(solicitud, 'direccion') && solicitud.direccion !== null;
    const direccion = llevaDireccion ? texto(solicitud.direccion, 200, true) : '';
    const contacto = validarContacto(solicitud.contacto);
    exigir(typeof solicitud.mostrarContacto === 'boolean', 'datos-invalidos', 'Indica si el contacto se muestra públicamente.');
    exigir(!solicitud.mostrarContacto || contacto !== null, 'datos-invalidos', 'Completa un contacto antes de mostrarlo públicamente.');
    const solicitudNormalizada: Record<string, unknown> = { tiendaId: solicitud.tiendaId, operacionId: solicitud.operacionId, versionEsperada: solicitud.versionEsperada, nombre, descripcion, tipoEmprendimientoId: solicitud.tipoEmprendimientoId, sectorId: solicitud.sectorId, contacto, mostrarContacto: solicitud.mostrarContacto };
    if (llevaDireccion) solicitudNormalizada.direccion = direccion;
    const firma = createHash('sha256').update(canon({ uid: identidad.uid, solicitud: solicitudNormalizada })).digest('hex');
    const storeRef = db.doc(`tiendasPrivadas/${solicitud.tiendaId}`);
    const receiptRef = storeRef.collection('operacionesEdicion').doc(solicitud.operacionId as string);
    const accessRef = db.doc(`accesos/${identidad.uid}`);
    const profileRef = db.doc(`emprendedoras/${identidad.uid}`);
    const publicRef = db.doc(`tiendasPublicas/${solicitud.tiendaId}`);

    return db.runTransaction(async tx => {
      const [accessSnap, profileSnap, storeSnap, receiptSnap, sectorSnap, typeSnap, publicSnap] = await tx.getAll(
        accessRef, profileRef, storeRef, receiptRef,
        db.doc(`sectores/${solicitud.sectorId}`),
        db.doc(`tiposEmprendimiento/${solicitud.tipoEmprendimientoId}`),
        publicRef,
      );
      const access = accessSnap.data();
      const store = storeSnap.data();
      exigir(access?.estado === 'activo' && Array.isArray(access.roles) && access.roles.includes('emprendedora') && access.tiendaId === solicitud.tiendaId, 'sin-permiso', 'La cuenta no tiene una tienda emprendedora activa asignada.');
      exigir(profileSnap.data()?.tiendaId === solicitud.tiendaId && store?.propietarioUid === identidad.uid, 'sin-permiso', 'La tienda no pertenece a esta cuenta.');
      if (receiptSnap.exists) {
        exigir(receiptSnap.data()?.firma === firma, 'id-reutilizado', 'operacionId corresponde a otra edición.');
        return receiptSnap.data()?.resultado as { tiendaId: string; operacionId: string; estadoPublicacion: string };
      }
      exigir(storeSnap.exists, 'no-encontrado', 'No se encontró la ficha de la tienda.');
      exigir(version(store!) === solicitud.versionEsperada, 'conflicto', 'La ficha cambió. Vuelve a cargarla antes de guardar.');
      const conservaSector = solicitud.sectorId === store.sectorId;
      const conservaTipo = solicitud.tipoEmprendimientoId === store.tipoEmprendimientoId;
      exigir(conservaSector || sectorSnap.data()?.activo === true, 'datos-invalidos', 'El sector seleccionado no existe o está inactivo.');
      exigir(conservaTipo || typeSnap.data()?.activo === true, 'datos-invalidos', 'El tipo de emprendimiento seleccionado no existe o está inactivo.');

      const estadoPublicacion = String(store.estadoPublicacion ?? 'borrador');
      const publicStore = publicSnap.data();
      const published = estadoPublicacion === 'publicado';
      exigir(!published || (publicStore?.estadoPublicacion === 'publicado' && publicStore.habilitada === true), 'conflicto', 'La tienda publicada no tiene una proyección pública coherente.');
      const now = FieldValue.serverTimestamp();
      const fields: DocumentData = {
        nombre,
        descripcion,
        tipoEmprendimientoId: solicitud.tipoEmprendimientoId,
        sectorId: solicitud.sectorId,
        mostrarContacto: solicitud.mostrarContacto,
        actualizadoEn: now,
      };
      if (contacto) fields.contacto = contacto;
      else fields.contacto = FieldValue.delete();
      if (llevaDireccion) {
        if (direccion) fields.direccion = direccion;
        else fields.direccion = FieldValue.delete();
      }
      tx.update(storeRef, fields);

      if (published) {
        const publicFields: DocumentData = {
          nombre,
          descripcion,
          tipoEmprendimientoId: solicitud.tipoEmprendimientoId,
          sectorId: solicitud.sectorId,
          actualizadoEn: now,
          contacto: solicitud.mostrarContacto ? contacto : FieldValue.delete(),
        };
        if (llevaDireccion) {
          if (direccion) publicFields.direccion = direccion;
          else publicFields.direccion = FieldValue.delete();
        }
        tx.update(publicRef, publicFields);
      }
      const resultado = { tiendaId: solicitud.tiendaId as string, operacionId: solicitud.operacionId as string, estadoPublicacion };
      tx.create(receiptRef, { firma, resultado, realizadoPor: identidad.uid, creadoEn: now });
      return resultado;
    });
  };
}
