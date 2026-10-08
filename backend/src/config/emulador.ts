import { initializeApp, getApps } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';

const projectId = 'demo-rincon-amancay';
if (process.env.NODE_ENV === 'production') throw new Error('El adaptador de emuladores no puede iniciarse en producción.');
if (process.env.FIRESTORE_EMULATOR_HOST && process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') {
  throw new Error('Este adaptador solo admite el emulador local en 127.0.0.1:8080.');
}
for (const key of ['GCLOUD_PROJECT', 'GOOGLE_CLOUD_PROJECT']) {
  if (process.env[key] && process.env[key] !== projectId) throw new Error('Proyecto real rechazado.');
}
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
if (process.env.FIREBASE_AUTH_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:9099') {
  throw new Error('Solo se admite Authentication local en 127.0.0.1:9099.');
}
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
const app = getApps().find(a => a.name === 'backend-local') ?? initializeApp({ projectId }, 'backend-local');
export const db = getFirestore(app);
export const auth = getAuth(app);
