import { obtenerFirebaseLocal } from '~/lib/firebase-local';
import { obtenerFirebaseCliente } from '~/lib/firebase';
import { crearClienteLocal } from '~/lib/cliente-local';
import { crearLecturasAdmin } from '~/lib/admin-lecturas';

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig().public;
  const modo = config.firebaseModo;
  if (modo !== 'local' && modo !== 'real-lectura') throw new Error('FIREBASE_MODO debe ser local o real-lectura.');
  if (modo === 'local' && !import.meta.dev) throw new Error('Los emuladores solo se usan en desarrollo.');
  if (modo === 'real-lectura' && config.firebaseProjectId !== 'rincon-amancay') {
    throw new Error('El entorno real debe usar rincon-amancay.');
  }
  const firebase = modo === 'local' ? obtenerFirebaseLocal() : obtenerFirebaseCliente({
    apiKey: config.firebaseApiKey, authDomain: config.firebaseAuthDomain,
    projectId: config.firebaseProjectId, storageBucket: config.firebaseStorageBucket,
    messagingSenderId: config.firebaseMessagingSenderId, appId: config.firebaseAppId,
  });
  const lecturas = crearLecturasAdmin(firebase);
  // En real-lectura no se construye el cliente de API ni se exponen escrituras.
  const amancay = modo === 'local'
    ? { ...crearClienteLocal(firebase), ...lecturas }
    : lecturas;
  return { provide: { amancay, firebaseModo: modo } };
});
