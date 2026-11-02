#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { applicationDefault, deleteApp, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { FieldValue, getFirestore, Timestamp } from 'firebase-admin/firestore';

const FIXTURE_URL = new URL('../../datos-prueba/catalogo-local.json', import.meta.url);
const DEMO_UIDS = {
  admin: 'u_admin_demo',
  entrepreneur: 'u_emprendedora_demo',
};
const ALLOWED_COLLECTIONS = new Set([
  'sectores', 'tiposEmprendimiento', 'categorias', 'tiposProducto', 'usuarios',
  'accesos', 'emprendedoras', 'tiendasPrivadas', 'tiendasPublicas',
]);

function requiredEnv(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Falta configurar ${name} en firebase/secrets/development.env.`);
  return value;
}

function validateProject(projectId, confirmation) {
  if (!/^rincon-amancay-dev-[a-z0-9][a-z0-9-]{2,9}[a-z0-9]$/.test(projectId)) {
    throw new Error('El ID debe ser un proyecto separado rincon-amancay-dev-<sufijo> de hasta 30 caracteres; no se admite el proyecto compartido.');
  }
  if (projectId === 'rincon-amancay' || projectId.startsWith('demo-')) {
    throw new Error('Se rechazó el proyecto de producción o el proyecto de emuladores.');
  }
  if (confirmation !== projectId) {
    throw new Error(`Confirma el destino repitiendo exactamente el ID: --confirm-dev-project ${projectId}`);
  }
  if (process.env.FIRESTORE_EMULATOR_HOST || process.env.FIREBASE_AUTH_EMULATOR_HOST
    || process.env.FIREBASE_STORAGE_EMULATOR_HOST || process.env.STORAGE_EMULATOR_HOST) {
    throw new Error('Cierra o desconfigura los emuladores antes de cargar un proyecto Firebase Cloud de desarrollo.');
  }
  for (const variable of ['GCLOUD_PROJECT', 'GOOGLE_CLOUD_PROJECT']) {
    if (process.env[variable] && process.env[variable] !== projectId) {
      throw new Error(`${variable} debe coincidir con el proyecto confirmado ${projectId}.`);
    }
  }
}

function materializar(valor) {
  if (Array.isArray(valor)) return valor.map(materializar);
  if (!valor || typeof valor !== 'object') return valor;
  if (Object.keys(valor).length === 1 && typeof valor.$timestamp === 'string') {
    const fecha = new Date(valor.$timestamp);
    if (Number.isNaN(fecha.getTime())) throw new Error('La fixture contiene una marca de tiempo inválida.');
    return Timestamp.fromDate(fecha);
  }
  if (Object.keys(valor).length === 1 && valor.$serverTimestamp === true) return FieldValue.serverTimestamp();
  return Object.fromEntries(Object.entries(valor).map(([clave, contenido]) => [clave, materializar(contenido)]));
}

async function cargarFixture() {
  const fixture = JSON.parse(await readFile(FIXTURE_URL, 'utf8'));
  if (fixture.proyecto !== 'demo-rincon-amancay' || fixture.soloEmulador !== true) {
    throw new Error('La fixture dejó de ser la fixture ficticia conocida; revisión requerida antes de importar.');
  }
  if (!Array.isArray(fixture.documentos) || fixture.documentos.length < 1 || fixture.documentos.length > 100) {
    throw new Error('La fixture tiene un número de documentos fuera del límite permitido (1–100).');
  }
  const rutas = new Set();
  for (const documento of fixture.documentos) {
    const partes = documento.ruta?.split('/');
    if (!partes || partes.length % 2 !== 0 || !partes.every(parte => /^[A-Za-z0-9_-]{1,128}$/.test(parte))) {
      throw new Error(`Ruta de documento inválida: ${documento.ruta}`);
    }
    if (!ALLOWED_COLLECTIONS.has(partes[0]) || rutas.has(documento.ruta)) {
      throw new Error(`Colección no autorizada o ruta repetida: ${documento.ruta}`);
    }
    rutas.add(documento.ruta);
  }
  for (const uid of Object.values(DEMO_UIDS)) {
    if (!rutas.has(`usuarios/${uid}`) || !rutas.has(`accesos/${uid}`)) {
      throw new Error(`La fixture no contiene el perfil y acceso de ${uid}.`);
    }
  }
  return fixture;
}

async function buscarUsuario(auth, uid, correo) {
  let porUid = null;
  try {
    porUid = await auth.getUser(uid);
  } catch (error) {
    if (error?.code !== 'auth/user-not-found') throw error;
  }
  let porCorreo = null;
  try {
    porCorreo = await auth.getUserByEmail(correo);
  } catch (error) {
    if (error?.code !== 'auth/user-not-found') throw error;
  }
  if (porCorreo && porCorreo.uid !== uid) {
    throw new Error(`El correo ${correo} ya pertenece a otra cuenta de Authentication en el proyecto de desarrollo.`);
  }
  if (porUid && porUid.email?.toLowerCase() !== correo.toLowerCase()) {
    throw new Error(`El UID de demostración ${uid} ya está asociado a otro correo.`);
  }
  if (porUid?.disabled) throw new Error(`La cuenta ficticia ${uid} está deshabilitada; revísala antes de volver a cargar la fixture.`);
  return porUid;
}

async function seed() {
  const projectId = requiredEnv('AMANCAY_DEV_PROJECT_ID');
  const confirmation = process.argv[process.argv.indexOf('--confirm-dev-project') + 1];
  validateProject(projectId, confirmation);

  const accounts = [
    {
      uid: DEMO_UIDS.admin,
      correo: requiredEnv('AMANCAY_DEV_ADMIN_EMAIL'),
      password: requiredEnv('AMANCAY_DEV_ADMIN_PASSWORD'),
      nombre: 'Administración de prueba',
    },
    {
      uid: DEMO_UIDS.entrepreneur,
      correo: requiredEnv('AMANCAY_DEV_OWNER_EMAIL'),
      password: requiredEnv('AMANCAY_DEV_OWNER_PASSWORD'),
      nombre: 'Emprendedora de prueba',
    },
  ];
  for (const account of accounts) {
    if (!/^[^\s@]+@[^\s@]+\.test$/i.test(account.correo)) {
      throw new Error(`Usa un correo ficticio terminado en .test para ${account.uid}.`);
    }
    if (account.password.length < 12) throw new Error(`La clave temporal para ${account.uid} debe tener al menos 12 caracteres.`);
  }

  const fixture = await cargarFixture();
  const app = initializeApp({ projectId, credential: applicationDefault() }, `amancay-seed-${Date.now()}`);
  const auth = getAuth(app);
  const db = getFirestore(app);
  const creadosEnEstaEjecucion = [];

  try {
    const existentes = await Promise.all(accounts.map(account => buscarUsuario(auth, account.uid, account.correo)));
    for (let i = 0; i < accounts.length; i++) {
      const account = accounts[i];
      if (!existentes[i]) {
        await auth.createUser({
          uid: account.uid,
          email: account.correo,
          password: account.password,
          displayName: account.nombre,
          emailVerified: true,
        });
        creadosEnEstaEjecucion.push(account.uid);
      }
    }

    const lote = db.batch();
    for (const documento of fixture.documentos) {
      lote.set(db.doc(documento.ruta), materializar(documento.datos));
    }
    await lote.commit();
    for (let i = 0; i < accounts.length; i++) {
      if (existentes[i]) {
        const account = accounts[i];
        await auth.updateUser(account.uid, {
          password: account.password,
          displayName: account.nombre,
          emailVerified: true,
        });
      }
    }
    console.log(`Cargados ${fixture.documentos.length} documentos ficticios y dos cuentas de prueba en ${projectId}.`);
    console.log('Las cuentas usan los UID u_admin_demo y u_emprendedora_demo; sus claves no se muestran.');
  } catch (error) {
    await Promise.all(creadosEnEstaEjecucion.map(uid => auth.deleteUser(uid).catch(() => undefined)));
    throw error;
  } finally {
    await deleteApp(app);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    await seed();
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}

export { cargarFixture, seed, validateProject };
