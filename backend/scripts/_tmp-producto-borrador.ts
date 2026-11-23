import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';

const app = initializeApp({ projectId: 'rincon-amancay', credential: applicationDefault() });
const db = getFirestore(app);
const referencia = db.doc('tiendasPublicas/tienda_b10bebea870c49f095f014c5e1a7a9b9/productos/tmp_borrador_w13');
if (process.argv[2] === '--borrar') {
  await referencia.delete();
  console.log('producto temporal eliminado');
} else {
  await referencia.set({
    nombre: 'Borrador temporal W13',
    estadoPublicacion: 'borrador',
    estadoDisponibilidad: 'disponible',
    actualizadoEn: FieldValue.serverTimestamp(),
  });
  console.log('producto temporal en borrador creado');
}
