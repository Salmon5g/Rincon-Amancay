import { getApp, getApps, initializeApp, type FirebaseOptions } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import { getStorage } from 'firebase/storage';

// Integrar desde un plugin Nuxt .client.ts cuando el equipo inicialice Nuxt.
// No utiliza Firebase Admin ni añade permisos a la cuenta.
export function obtenerFirebaseCliente(config: FirebaseOptions) {
  if (typeof window === 'undefined') {
    throw new Error('Firebase cliente debe inicializarse en el navegador.');
  }
  for (const campo of ['apiKey', 'authDomain', 'projectId', 'storageBucket', 'appId'] as const) {
    if (!config[campo]) throw new Error(`Falta configuración Firebase: ${campo}`);
  }
  const nombre = 'rincon-amancay-web';
  const app = getApps().some((actual) => actual.name === nombre)
    ? getApp(nombre)
    : initializeApp(config, nombre);
  if (app.options.projectId !== config.projectId || app.options.appId !== config.appId) {
    throw new Error('Firebase ya se inicializó con otra aplicación.');
  }
  return { app, auth: getAuth(app), db: getFirestore(app), storage: getStorage(app) };
}
export type FirebaseCompartido = ReturnType<typeof obtenerFirebaseCliente>;
