import { FieldValue } from 'firebase-admin/firestore';
import type { Firestore, DocumentData, Transaction } from 'firebase-admin/firestore';
import { ErrorOperacion } from './publicacion.ts';

// Herramienta técnica con Admin SDK. No se expone en HTTP ni en SDK cliente.
function exigir(ok: unknown, mensaje: string): asserts ok {
  if (!ok) throw new ErrorOperacion('conflicto', mensaje);
}
function validarUid(uid: string) {
  exigir(/^[a-zA-Z0-9_-]{1,128}$/.test(uid), 'UID inválido.');
}
async function pendiente(db: Firestore, tx: Transaction, uid: string, acceso?: DocumentData) {
  const ruta = acceso?.operacionCuentaPendiente;
  if (ruta === undefined) return null;
  exigir(typeof ruta === 'string' && /^(desactivaciones|reactivaciones)\/[a-zA-Z0-9_-]{1,257}$/.test(ruta), 'Reserva lógica inválida; requiere revisión manual.');
  const documento = await tx.get(db.doc(ruta));
  const datos = documento.data();
  exigir(datos?.uidDestino === uid && datos.estado === 'pendienteAuth' && acceso?.estado === 'desactivado', 'Comprobante y cuenta inconsistentes; no liberar automáticamente.');
  return { ruta, accion: ruta.startsWith('desactivaciones/') ? 'desactivarEmprendedora' : 'reactivarEmprendedora',
    actorUid: datos.realizadaPor, operacionId: datos.operacionId,
    solicitud: datos.solicitud ?? null };
}

export async function inspeccionarReserva(db: Firestore, uid: string) {
  validarUid(uid);
  return db.runTransaction(async tx => {
    const [reserva, acceso] = await tx.getAll(db.doc(`ejecucionesCuentas/${uid}`), db.doc(`accesos/${uid}`));
    const operacionPendiente = await pendiente(db, tx, uid, acceso.data());
    return { uid, reserva: reserva.data() ?? null, estadoAcceso: acceso.data()?.estado ?? null, operacionPendiente };
  });
}

export type Liberacion = {
  uid: string; ejecutorId: string; operador: string; motivo: string; servidoresDetenidos: boolean;
};

export async function liberarReserva(db: Firestore, solicitud: Liberacion) {
  validarUid(solicitud.uid);
  exigir(solicitud.servidoresDetenidos === true, 'Detener todas las instancias y resolver peticiones Auth en vuelo antes de confirmar.');
  exigir(/^[a-zA-Z0-9_-]{1,128}$/.test(solicitud.ejecutorId), 'ejecutorId requerido, copiado de la inspección.');
  for (const valor of [solicitud.operador, solicitud.motivo]) exigir(typeof valor === 'string' && valor.trim().length > 0 && valor.length <= 500, 'Operador y motivo requeridos (hasta 500 caracteres).');
  const ref = db.doc(`ejecucionesCuentas/${solicitud.uid}`);
  const auditoria = db.doc(`recuperacionesCuentas/${solicitud.ejecutorId}`);
  return db.runTransaction(async tx => {
    const [lock, anterior, acceso] = await tx.getAll(ref, auditoria, db.doc(`accesos/${solicitud.uid}`));
    if (!lock.exists) {
      exigir(anterior.data()?.uid === solicitud.uid, 'La reserva ya no existe; volver a inspeccionar.');
      return { liberada: true, repetida: true, operacionPendiente: anterior.data()!.operacionPendiente };
    }
    exigir(lock.data()?.ejecutorId === solicitud.ejecutorId, 'La reserva cambió; no se liberó. Volver a inspeccionar.');
    exigir(!anterior.exists, 'Ya existe una intervención para este ejecutor; requiere revisión manual.');
    const operacionPendiente = await pendiente(db, tx, solicitud.uid, acceso.data());
    tx.create(auditoria, { uid: solicitud.uid, ejecutorId: solicitud.ejecutorId,
      operador: solicitud.operador.trim(), motivo: solicitud.motivo.trim(),
      reservaAnterior: lock.data(), operacionPendiente, creadoEn: FieldValue.serverTimestamp() });
    tx.delete(ref);
    // Conservar estado, corte de sesiones, catálogo y comprobante. La API retoma
    // la MISMA intención; esta intervención nunca habilita Auth ni publica tiendas.
    return { liberada: true, repetida: false, operacionPendiente };
  });
}
