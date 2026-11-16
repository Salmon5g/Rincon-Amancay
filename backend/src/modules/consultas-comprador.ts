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

export function crearConsultasComprador(db: Firestore) {
  const guardar = async (solicitud: SolicitudConsultaPublica) => {
    const tiendaRef = db.collection('tiendasPublicas').doc(solicitud.tiendaId);
    const tiendaPrivadaRef = db.collection('tiendasPrivadas').doc(solicitud.tiendaId);
    const consultaRef = tiendaPrivadaRef.collection('consultas').doc(solicitud.operacionId);
    return db.runTransaction(async tx => {
      const [tienda, privada, existente] = await Promise.all([
        tx.get(tiendaRef), tx.get(tiendaPrivadaRef), tx.get(consultaRef),
      ]);
      if (!tienda.exists || !privada.exists || tienda.get('estadoPublicacion') !== 'publicado' ||
          tienda.get('habilitada') !== true || !['formulario', 'ambos'].includes(tienda.get('formaContacto') ?? 'formulario')) {
        throw new ErrorOperacion('no-disponible', 'La tienda no recibe consultas por formulario.');
      }
      if (existente.exists) return { recibida: true };
      tx.create(consultaRef, {
        nombre: solicitud.nombre,
        correo: solicitud.correo,
        mensaje: solicitud.mensaje,
        ...(solicitud.producto ? { producto: solicitud.producto } : {}),
        estado: 'nueva',
        creadaEn: FieldValue.serverTimestamp(),
      });
      return { recibida: true };
    });
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
