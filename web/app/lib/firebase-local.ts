import { getApps, initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth } from 'firebase/auth';
import { connectFirestoreEmulator, getFirestore } from 'firebase/firestore';
import { connectStorageEmulator, getStorage } from 'firebase/storage';

function inicializar(nombre: string) {
  if (getApps().some(app => app.name === nombre)) throw new Error('La app local ya existe fuera de este inicializador. Recargar antes de continuar.');
  const app = initializeApp({
    projectId: 'demo-rincon-amancay', apiKey: 'demo-key',
    authDomain: 'demo-rincon-amancay.firebaseapp.com', appId: 'demo-rincon-amancay-web',
    storageBucket: 'demo-rincon-amancay.appspot.com',
  }, nombre);
  const auth = getAuth(app), db = getFirestore(app), storage = getStorage(app);
  // Conectar antes de cualquier lectura o inicio de sesión. Nunca usa .env real.
  connectAuthEmulator(auth, 'http://127.0.0.1:9099');
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
  connectStorageEmulator(storage, '127.0.0.1', 9199);
  return { app, auth, db, storage };
}
export type FirebaseLocal = ReturnType<typeof inicializar>;
// Cache en globalThis para conservar la conexión durante HMR de Nuxt.
const runtime = globalThis as typeof globalThis & { __amancayLocal?: Map<string, FirebaseLocal> };
export function obtenerFirebaseLocal(nombre = 'rincon-amancay-web-local'): FirebaseLocal {
  const cache = runtime.__amancayLocal ??= new Map();
  const existing = cache.get(nombre);
  if (existing) return existing;
  const connection = inicializar(nombre); cache.set(nombre, connection); return connection;
}
