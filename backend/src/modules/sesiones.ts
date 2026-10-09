import type { Firestore } from 'firebase-admin/firestore';

// Coincide con el corte aplicado por las reglas de Firestore y Storage.
export function crearValidadorSesion(db: Firestore) {
  return async (uid: string, authTime: number) => {
    const access=(await db.doc(`accesos/${uid}`).get()).data();
    return access?.sesionesRevocadasHasta === undefined || authTime > access.sesionesRevocadasHasta;
  };
}
