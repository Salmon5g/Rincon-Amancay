import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';

const app = initializeApp({ projectId: 'rincon-amancay', credential: applicationDefault() });
const db = getFirestore(app);
const feriaId = '757oqu8CpHGtCdwmVeSB';
const referencia = db.doc(`ferias/${feriaId}/participantes/tmp_rechazada_w12`);
if (process.argv[2] === '--borrar') {
  await referencia.delete();
  console.log('participante temporal eliminado');
} else {
  await referencia.set({
    tipo: 'emprendedora',
    entidadId: 'hfnb3cPfiBX3pz4CK94hyQdaF1o1',
    nombre: 'Rechazada temporal W12',
    nombreTienda: 'Rechazada temporal W12',
    estado: 'rechazada',
    asistencia: 'pendiente',
    actualizadoEn: FieldValue.serverTimestamp(),
  });
  console.log('participante temporal rechazado creado');
}
