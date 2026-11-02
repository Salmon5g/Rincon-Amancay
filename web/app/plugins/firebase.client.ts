import { crearAccesoComprador } from '~/lib/acceso-comprador';
import { obtenerFirebaseLocal } from '~/lib/firebase-local';
import { obtenerFirebaseCliente } from '~/lib/firebase';
import { crearClienteLocal } from '~/lib/cliente-local';
import { crearLecturasAdmin } from '~/repositories/firebase/lecturas-admin';
import { crearClienteCompartido } from '~/lib/cliente-compartido';
import { crearCatalogoPublicoFirebase } from '~/repositories/firebase/catalogo-publico';

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig().public;
  const modo = config.firebaseModo;
  if (!['local', 'real-lectura', 'real-api'].includes(modo)) throw new Error('FIREBASE_MODO debe ser local, real-lectura o real-api.');
  if (modo === 'local' && !import.meta.dev) throw new Error('Los emuladores solo se usan en desarrollo.');
  if (modo !== 'local' && config.firebaseProjectId !== 'rincon-amancay') {
    throw new Error('El entorno real debe usar rincon-amancay.');
  }
  const firebase = modo === 'local' ? obtenerFirebaseLocal() : obtenerFirebaseCliente({
    apiKey: config.firebaseApiKey, authDomain: config.firebaseAuthDomain,
    projectId: config.firebaseProjectId, storageBucket: config.firebaseStorageBucket,
    messagingSenderId: config.firebaseMessagingSenderId, appId: config.firebaseAppId,
  });
  const catalogoPublico = crearCatalogoPublicoFirebase(firebase, config.supabaseUrl);
  const lecturas = crearLecturasAdmin(firebase);
  // En real-lectura no se construye el cliente de API ni se exponen escrituras.
  if (modo === 'real-api' && !config.apiUrl) throw new Error('NUXT_PUBLIC_API_URL es obligatoria en modo real-api.');
  const clienteApi = modo === 'local'
    ? crearClienteLocal(firebase)
    : modo === 'real-api'
      ? crearClienteCompartido(firebase, { apiUrl: config.apiUrl, permitirHttpLocal: import.meta.dev })
      : undefined;
  const amancay = clienteApi ? { ...clienteApi, ...lecturas } : lecturas;
  const accesoComprador = crearAccesoComprador(firebase, clienteApi?.llamar);
  return { provide: { amancay, catalogoPublico, accesoComprador, firebaseModo: modo } };
});
