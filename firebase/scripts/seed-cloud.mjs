#!/usr/bin/env node
/**
 * Carga los datos ficticios en un proyecto Firebase real (nube).
 *
 * Requisitos:
 * - Archivo de cuenta de servicio: ruta indicada en GOOGLE_APPLICATION_CREDENTIALS
 * - Opcional: GOOGLE_CLOUD_PROJECT o GCLOUD_PROJECT (o usa el projectId de la cuenta de servicio)
 * - Colección de destino: Firestore del proyecto real
 *
 * Uso:
 *   set GOOGLE_APPLICATION_CREDENTIALS=C:\ruta\serviceAccount.json
 *   node scripts/seed-cloud.mjs
 */
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import admin from 'firebase-admin';

function value(input) {
  if (input === null) return admin.firestore.FieldValue.delete(); // no aplica, pero defensivo
  if (typeof input === 'boolean') return input;
  if (typeof input === 'string') return input;
  if (typeof input === 'number' && Number.isSafeInteger(input)) return input;
  if (Array.isArray(input)) return input.map((v) => value(v));
  if (typeof input === 'object') {
    if (Object.keys(input).length === 1 && '$timestamp' in input) {
      const d = new Date(input.$timestamp);
      if (Number.isNaN(d.getTime())) throw new Error('Fecha inválida');
      return admin.firestore.Timestamp.fromDate(d);
    }
    return Object.fromEntries(Object.entries(input).map(([k, v]) => [k, value(v)]));
  }
  throw new Error('Tipo no soportado en la carga ficticia');
}

function asFields(data) {
  return Object.fromEntries(Object.entries(data).map(([key, input]) => [key, value(input)]));
}

async function loadFixture() {
  const fixture = JSON.parse(
    await readFile(new URL('../../datos-prueba/catalogo-local.json', import.meta.url), 'utf8')
  );
  const paths = new Set();
  for (const doc of fixture.documentos) {
    if (!/^[a-zA-Z0-9_/-]+$/.test(doc.ruta) || doc.ruta.split('/').length % 2 !== 0 || paths.has(doc.ruta)) {
      throw new Error(`Ruta inválida o repetida: ${doc.ruta}`);
    }
    paths.add(doc.ruta);
  }
  return fixture;
}

async function seed() {
  const credential = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!credential) {
    throw new Error('Falta GOOGLE_APPLICATION_CREDENTIALS: ruta a la serviceAccount.json');
  }

  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
    });
  }

  const db = admin.firestore();
  const fixture = await loadFixture();

  const batch = db.batch();
  for (const doc of fixture.documentos) {
    const ref = db.doc(doc.ruta);
    batch.set(ref, asFields(doc.datos), { merge: true });
  }
  await batch.commit();

  const projectId = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || admin.app().options.projectId;
  console.log(`Cargados ${fixture.documentos.length} documentos ficticios en ${projectId} (nube).`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  seed().catch((err) => {
    console.error(err instanceof Error ? err.message : err);
    process.exit(1);
  });
}

export { seed };
