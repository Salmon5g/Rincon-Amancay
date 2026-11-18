import { createHash } from 'node:crypto';
import { FieldValue, type Firestore, type DocumentData } from 'firebase-admin/firestore';
import { ErrorOperacion, version } from './publicacion.ts';
import { sincronizarAsociacionesDeTienda } from './proyecciones-asociaciones-publicas.ts';

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

function validarPuntoRetiro(value: unknown): { latitud: number; longitud: number; descripcion: string } | null {
  if (value === null) return null;
  const punto = objeto(value);
  campos(punto, ['latitud', 'longitud', 'descripcion']);
  const latitud = punto.latitud;
  const longitud = punto.longitud;
  exigir(typeof latitud === 'number' && Number.isFinite(latitud) && latitud >= -90 && latitud <= 90, 'datos-invalidos', 'La latitud del punto de retiro está fuera de rango.');
  exigir(typeof longitud === 'number' && Number.isFinite(longitud) && longitud >= -180 && longitud <= 180, 'datos-invalidos', 'La longitud del punto de retiro está fuera de rango.');
  const descripcion = texto(punto.descripcion, 200, true);
  return { latitud, longitud, descripcion };
}

type CanalesContacto = {
  whatsapp: { habilitado: boolean; numero: string };
  formulario: { habilitado: boolean };
  correo: { habilitado: boolean; direccion: string };
};

function validarCanalesContacto(value: unknown): CanalesContacto | undefined {
  if (value === undefined) return undefined;
  const canales = objeto(value);
  exigir(Object.keys(canales).every(clave => ['whatsapp', 'formulario', 'correo'].includes(clave)) && Object.hasOwn(canales, 'whatsapp') && Object.hasOwn(canales, 'formulario'), 'datos-invalidos', 'Canales de contacto inválidos.');
  const whatsapp = objeto(canales.whatsapp);
  const formulario = objeto(canales.formulario);
  const correo = canales.correo === undefined ? { habilitado: false, direccion: '' } : objeto(canales.correo);
  campos(whatsapp, ['habilitado', 'numero']);
  exigir(Object.keys(formulario).every(clave => clave === 'habilitado') && Object.hasOwn(formulario, 'habilitado'), 'datos-invalidos', 'Preferencia del formulario inválida.');
  campos(correo, ['habilitado', 'direccion']);
  exigir(typeof whatsapp.habilitado === 'boolean' && typeof whatsapp.numero === 'string' && whatsapp.numero.length <= 32, 'datos-invalidos', 'Preferencia de WhatsApp inválida.');
  exigir(typeof formulario.habilitado === 'boolean', 'datos-invalidos', 'Preferencia del formulario inválida.');
  exigir(typeof correo.habilitado === 'boolean' && typeof correo.direccion === 'string' && correo.direccion.length <= 254, 'datos-invalidos', 'Preferencia de correo inválida.');
  const numero = whatsapp.numero.trim();
  const direccion = correo.direccion.trim();
  if (numero) exigir(/^\+?[0-9][0-9\s()-]{6,22}$/.test(numero), 'datos-invalidos', 'WhatsApp debe incluir un número válido con código de país.');
  if (direccion) exigir(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(direccion), 'datos-invalidos', 'Correo de contacto inválido.');
  exigir(!whatsapp.habilitado || Boolean(numero), 'datos-invalidos', 'Agrega un número válido antes de habilitar WhatsApp.');
  exigir(!correo.habilitado || Boolean(direccion), 'datos-invalidos', 'Agrega un correo válido antes de habilitar este canal.');
  return {
    whatsapp: { habilitado: whatsapp.habilitado, numero },
    formulario: { habilitado: formulario.habilitado },
    correo: { habilitado: correo.habilitado, direccion },
  };
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
    // 'direccion', 'canalesContacto' y 'puntoRetiro' son opcionales para conservar compatibilidad
    // con clientes que no los envían; si faltan, no modifican los valores guardados.
    const CAMPOS_EDICION = ['tiendaId', 'operacionId', 'versionEsperada', 'nombre', 'descripcion', 'tipoEmprendimientoId', 'sectorId', 'contacto', 'mostrarContacto', 'canalesContacto', 'puntoRetiro'];
    exigir(Object.keys(solicitud).every(clave => clave === 'direccion' || CAMPOS_EDICION.includes(clave)), 'datos-invalidos', 'Campos ausentes o no permitidos.');
    exigir(CAMPOS_EDICION.filter(clave => clave !== 'canalesContacto').every(clave => Object.hasOwn(solicitud, clave)), 'datos-invalidos', 'Campos ausentes o no permitidos.');
    exigir(id(solicitud.tiendaId) && id(solicitud.operacionId) && id(solicitud.tipoEmprendimientoId) && id(solicitud.sectorId), 'datos-invalidos', 'Identificadores inválidos.');
    exigir(typeof solicitud.versionEsperada === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(solicitud.versionEsperada), 'datos-invalidos', 'Versión inválida.');
    const nombre = texto(solicitud.nombre, 120);
    const descripcion = texto(solicitud.descripcion, 2000, true);
    const llevaDireccion = Object.hasOwn(solicitud, 'direccion') && solicitud.direccion !== null;
    const direccion = llevaDireccion ? texto(solicitud.direccion, 200, true) : '';
    const contacto = validarContacto(solicitud.contacto);
    const canalesContacto = Object.hasOwn(solicitud, 'canalesContacto') ? validarCanalesContacto(solicitud.canalesContacto) : undefined;
    const llevaPuntoRetiro = Object.hasOwn(solicitud, 'puntoRetiro');
    const puntoRetiro = llevaPuntoRetiro ? validarPuntoRetiro(solicitud.puntoRetiro) : undefined;
    exigir(typeof solicitud.mostrarContacto === 'boolean', 'datos-invalidos', 'Indica si el contacto se muestra públicamente.');
    exigir(!solicitud.mostrarContacto || contacto !== null, 'datos-invalidos', 'Completa un contacto antes de mostrarlo públicamente.');
    const solicitudNormalizada: Record<string, unknown> = { tiendaId: solicitud.tiendaId, operacionId: solicitud.operacionId, versionEsperada: solicitud.versionEsperada, nombre, descripcion, tipoEmprendimientoId: solicitud.tipoEmprendimientoId, sectorId: solicitud.sectorId, contacto, mostrarContacto: solicitud.mostrarContacto };
    if (canalesContacto) solicitudNormalizada.canalesContacto = canalesContacto;
    if (llevaDireccion) solicitudNormalizada.direccion = direccion;
    if (llevaPuntoRetiro) solicitudNormalizada.puntoRetiro = puntoRetiro;
    const firma = createHash('sha256').update(canon({ uid: identidad.uid, solicitud: solicitudNormalizada })).digest('hex');
    const storeRef = db.doc(`tiendasPrivadas/${solicitud.tiendaId}`);
    const receiptRef = storeRef.collection('operacionesEdicion').doc(solicitud.operacionId as string);
    const accessRef = db.doc(`accesos/${identidad.uid}`);
    const profileRef = db.doc(`emprendedoras/${identidad.uid}`);
    const publicRef = db.doc(`tiendasPublicas/${solicitud.tiendaId}`);

    const resultado = await db.runTransaction(async tx => {
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
      let canalesContactoPublicos: DocumentData | undefined;
      let formaContactoPublica: string | undefined;
      if (canalesContacto) {
        const anterior = store!.canalesContacto as { formulario?: { formularioId?: unknown } } | undefined;
        const formularioId = typeof anterior?.formulario?.formularioId === 'string' && anterior.formulario.formularioId.trim()
          ? anterior.formulario.formularioId
          : `contacto-${solicitud.tiendaId}`;
        const canalesSolicitados = solicitud.canalesContacto as Record<string, unknown>;
        const anteriorCompleto = store!.canalesContacto as {
          correo?: { habilitado?: unknown; direccion?: unknown };
        } | undefined;
        const contactoAnterior = store!.contacto as { tipo?: unknown; valor?: unknown } | undefined;
        const correoAnterior = typeof anteriorCompleto?.correo?.habilitado === 'boolean'
          ? {
              habilitado: anteriorCompleto.correo.habilitado,
              direccion: typeof anteriorCompleto.correo.direccion === 'string' ? anteriorCompleto.correo.direccion : '',
            }
          : {
              habilitado: store!.mostrarContacto === true && contactoAnterior?.tipo === 'correo' && typeof contactoAnterior.valor === 'string',
              direccion: typeof contactoAnterior?.valor === 'string' && contactoAnterior.tipo === 'correo' ? contactoAnterior.valor : '',
            };
        const correo = Object.hasOwn(canalesSolicitados, 'correo') ? canalesContacto.correo : correoAnterior;
        const canalesActualizados = { ...canalesContacto, correo };
        const canalesPrivados = { ...canalesActualizados, formulario: { ...canalesActualizados.formulario, formularioId } };
        fields.canalesContacto = canalesPrivados;
        canalesContactoPublicos = {
          whatsapp: { ...canalesActualizados.whatsapp, numero: canalesActualizados.whatsapp.habilitado ? canalesActualizados.whatsapp.numero : '' },
          formulario: { ...canalesPrivados.formulario },
          correo: { ...canalesActualizados.correo, direccion: canalesActualizados.correo.habilitado ? canalesActualizados.correo.direccion : '' },
        };
        formaContactoPublica = canalesActualizados.formulario.habilitado
          ? canalesActualizados.whatsapp.habilitado ? 'ambos' : 'formulario'
          : canalesActualizados.whatsapp.habilitado ? 'whatsapp' : 'ninguno';
      }
      if (contacto) fields.contacto = contacto;
      else fields.contacto = FieldValue.delete();
      if (llevaDireccion) {
        if (direccion) fields.direccion = direccion;
        else fields.direccion = FieldValue.delete();
      }
      if (llevaPuntoRetiro) {
        if (puntoRetiro) fields.puntoRetiro = puntoRetiro;
        else fields.puntoRetiro = FieldValue.delete();
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
        if (canalesContactoPublicos) publicFields.canalesContacto = canalesContactoPublicos;
        if (formaContactoPublica) publicFields.formaContacto = formaContactoPublica;
        if (llevaDireccion) {
          if (direccion) publicFields.direccion = direccion;
          else publicFields.direccion = FieldValue.delete();
        }
        if (llevaPuntoRetiro) {
          if (puntoRetiro) publicFields.puntoRetiro = puntoRetiro;
          else publicFields.puntoRetiro = FieldValue.delete();
        }
        tx.update(publicRef, publicFields);
      }
      const resultado = { tiendaId: solicitud.tiendaId as string, operacionId: solicitud.operacionId as string, estadoPublicacion };
      tx.create(receiptRef, { firma, resultado, realizadoPor: identidad.uid, creadoEn: now });
      return resultado;
    });
    await sincronizarAsociacionesDeTienda(db, solicitud.tiendaId as string);
    return resultado;
  };
}
