import { FieldValue, type Firestore } from 'firebase-admin/firestore';
import { ErrorOperacion } from './publicacion.ts';

export type SolicitudConsultaPublica = {
  tiendaId: string;
  operacionId: string;
  nombre: string;
  correo: string;
  mensaje: string;
  producto?: string;
};

function estaDentroDelHorario(ahora: Date, inicio: string, fin: string, zonaHoraria: string) {
  try {
    const partes = new Intl.DateTimeFormat('en-GB', { timeZone: zonaHoraria, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(ahora).split(':').map(Number);
    const minuto = partes[0] * 60 + partes[1];
    const [horaInicio, minutoInicio] = inicio.split(':').map(Number);
    const [horaFin, minutoFin] = fin.split(':').map(Number);
    const desde = horaInicio * 60 + minutoInicio;
    const hasta = horaFin * 60 + minutoFin;
    return desde === hasta ? true : desde < hasta ? minuto >= desde && minuto < hasta : minuto >= desde || minuto < hasta;
  } catch { return false; }
}

export function crearConsultasComprador(db: Firestore, notificar?: (tiendaId: string, nombreTienda: string) => Promise<void>) {
  const guardar = async (solicitud: SolicitudConsultaPublica) => {
    const tiendaRef = db.collection('tiendasPublicas').doc(solicitud.tiendaId);
    const tiendaPrivadaRef = db.collection('tiendasPrivadas').doc(solicitud.tiendaId);
    const consultaRef = tiendaPrivadaRef.collection('consultas').doc(solicitud.operacionId);
    const resultado = await db.runTransaction(async tx => {
      const [tienda, privada, existente, configuracion] = await Promise.all([
        tx.get(tiendaRef), tx.get(tiendaPrivadaRef), tx.get(consultaRef),
        tx.get(tiendaPrivadaRef.collection('configuracion').doc('general')),
      ]);
      const canales = tienda.data()?.canalesContacto as { formulario?: { habilitado?: unknown } } | undefined;
      const recibeFormulario = typeof canales?.formulario?.habilitado === 'boolean'
        ? canales.formulario.habilitado
        : ['formulario', 'ambos'].includes(String(tienda.data()?.formaContacto ?? 'formulario'));
      if (!tienda.exists || !privada.exists || tienda.get('estadoPublicacion') !== 'publicado' ||
          tienda.get('habilitada') !== true || !recibeFormulario) {
        throw new ErrorOperacion('no-disponible', 'La tienda no recibe consultas por formulario.');
      }
      const horario = configuracion.get('notificacionesContacto') as { habilitado?: unknown; horaInicio?: unknown; horaFin?: unknown; zonaHoraria?: unknown } | undefined;
      const notificable = horario?.habilitado === true && typeof horario.horaInicio === 'string' && typeof horario.horaFin === 'string' &&
        typeof horario.zonaHoraria === 'string' && estaDentroDelHorario(new Date(), horario.horaInicio, horario.horaFin, horario.zonaHoraria);
      if (existente.exists) return { recibida: true, nueva: false, notificable: false, nombreTienda: '' };
      tx.create(consultaRef, {
        nombre: solicitud.nombre,
        correo: solicitud.correo,
        mensaje: solicitud.mensaje,
        ...(solicitud.producto ? { producto: solicitud.producto } : {}),
        notificable,
        estado: 'nueva',
        creadaEn: FieldValue.serverTimestamp(),
      });
      return { recibida: true, nueva: true, notificable, nombreTienda: String(tienda.get('nombre') ?? 'tu tienda') };
    });
    if (resultado.nueva && resultado.notificable && notificar) await notificar(solicitud.tiendaId, resultado.nombreTienda ?? 'tu tienda').catch(() => undefined);
    return { recibida: true };
  };
  const leerRespuesta = async (tiendaId: string, operacionId: string) => {
    const referencia = db.doc(`tiendasPrivadas/${tiendaId}/consultas/${operacionId}`);
    const [tienda, consulta] = await Promise.all([
      db.doc(`tiendasPublicas/${tiendaId}`).get(),
      referencia.get(),
    ]);
    if (!tienda.exists || tienda.get('estadoPublicacion') !== 'publicado' || tienda.get('habilitada') !== true || !consulta.exists) {
      throw new ErrorOperacion('no-encontrado', 'No encontramos esa consulta.');
    }
    return {
      respondida: typeof consulta.get('respuesta') === 'string' && Boolean(consulta.get('respuesta')),
      respuesta: consulta.get('respuesta') ?? null,
      respondidaEn: consulta.get('respondidaEn')?.toDate?.().toISOString?.() ?? null,
    };
  };
  const responder = async (uid: string, solicitud: { tiendaId: string; consultaId: string; respuesta: string }) => {
    const accesoRef = db.doc(`accesos/${uid}`);
    const tiendaRef = db.doc(`tiendasPrivadas/${solicitud.tiendaId}`);
    const consultaRef = tiendaRef.collection('consultas').doc(solicitud.consultaId);
    const texto = solicitud.respuesta.trim();
    if (texto.length < 1 || texto.length > 2000) throw new ErrorOperacion('datos-invalidos', 'La respuesta debe tener entre 1 y 2000 caracteres.');
    await db.runTransaction(async tx => {
      const [acceso, tienda, consulta] = await Promise.all([tx.get(accesoRef), tx.get(tiendaRef), tx.get(consultaRef)]);
      if (!acceso.exists || acceso.get('estado') !== 'activo' || !((acceso.get('roles') as unknown[] | undefined) ?? []).includes('emprendedora') || acceso.get('tiendaId') !== solicitud.tiendaId || tienda.get('propietarioUid') !== uid) {
        throw new ErrorOperacion('sin-permiso', 'La sesión no tiene permiso para responder consultas de esta tienda.');
      }
      if (!consulta.exists) throw new ErrorOperacion('no-encontrado', 'No encontramos esta consulta.');
      tx.update(consultaRef, { respuesta: texto, respondidaEn: FieldValue.serverTimestamp(), estado: 'respondida' });
    });
    return { respondida: true };
  };
  return { guardar, leerRespuesta, responder };
}
