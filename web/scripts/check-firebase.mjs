import { initializeApp, deleteApp } from 'firebase/app';
import { doc, getDocFromServer, getFirestore, terminate } from 'firebase/firestore';

const nombres = ['API_KEY', 'AUTH_DOMAIN', 'PROJECT_ID', 'APP_ID'];
for (const nombre of nombres) {
  if (!process.env[`NUXT_PUBLIC_FIREBASE_${nombre}`]) {
    throw new Error(`Falta NUXT_PUBLIC_FIREBASE_${nombre} en .env`);
  }
}
const app = initializeApp({
  apiKey: process.env.NUXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NUXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NUXT_PUBLIC_FIREBASE_PROJECT_ID,
  appId: process.env.NUXT_PUBLIC_FIREBASE_APP_ID,
}, 'comprobacion-sin-sesion');
const db = getFirestore(app);
// Solo lectura. No crea este documento ni inicia sesión. Una denegación es
// el resultado esperado mientras las reglas iniciales permanezcan cerradas.
const limite = setTimeout(() => {
  console.error('Tiempo agotado; no se pudo comprobar Firestore.');
  process.exit(1);
}, 20000);
try {
  await getDocFromServer(doc(db, 'usuarios', 'comprobacion_sin_sesion'));
  console.error('Resultado inesperado: se permitió leer sin sesión. Revisar reglas.');
  process.exitCode = 1;
} catch (error) {
  if (error.code === 'permission-denied') {
    console.log('OK: Firestore respondió permission-denied a la lectura sin sesión.');
    console.log('Confirma comunicación y rechazo de esta lectura; no prueba todas las reglas.');
  } else {
    console.error(`No se pudo validar: ${error.code ?? error.message}`);
    process.exitCode = 1;
  }
} finally {
  clearTimeout(limite);
  await terminate(db);
  await deleteApp(app);
}
