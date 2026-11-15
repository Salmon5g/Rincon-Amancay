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
  return async (solicitud: SolicitudConsultaPublica) => {
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
}
