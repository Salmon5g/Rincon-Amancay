import { test } from 'node:test';
import assert from 'node:assert/strict';
import { leerConfigCompartida } from '../src/config/compartido.ts';

const base = {
  AMANCAY_ENTORNO: 'compartido',
  AMANCAY_PROJECT_ID: 'rincon-amancay',
  AMANCAY_STORAGE_BUCKET: 'rincon-amancay.firebasestorage.app',
  AMANCAY_ORIGENES_WEB: 'https://rincon-amancay.web.app',
};

test('config compartida: proyecto, bucket, puerto y HTTP explícitos', () => {
  const config = leerConfigCompartida({ ...base, PORT: '9090' });
  assert.equal(config.proyecto, 'rincon-amancay');
  assert.equal(config.bucket, 'rincon-amancay.firebasestorage.app');
  assert.equal(config.port, 9090);
  assert.deepEqual(config.http, { entorno: 'compartido', proyecto: 'rincon-amancay', origenes: ['https://rincon-amancay.web.app'] });
  assert.equal(leerConfigCompartida({ ...base }).port, 8080);
});

test('config compartida rechaza emuladores, credenciales y proyectos ajenos', () => {
  for (const key of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST', 'FIREBASE_STORAGE_EMULATOR_HOST', 'STORAGE_EMULATOR_HOST', 'GOOGLE_APPLICATION_CREDENTIALS']) {
    assert.throws(() => leerConfigCompartida({ ...base, [key]: 'x' }));
  }
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ENTORNO: 'emulador' }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ENTORNO: undefined }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_PROJECT_ID: 'demo-rincon-amancay' }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_STORAGE_BUCKET: 'rincon-amancay.appspot.com' }));
  assert.throws(() => leerConfigCompartida({ ...base, GCLOUD_PROJECT: 'otro-proyecto' }));
  assert.throws(() => leerConfigCompartida({ ...base, GOOGLE_CLOUD_PROJECT: 'otro-proyecto' }));
  assert.throws(() => leerConfigCompartida({ ...base, PORT: '0' }));
  assert.throws(() => leerConfigCompartida({ ...base, PORT: '70000' }));
  assert.throws(() => leerConfigCompartida({ ...base, PORT: 'abc' }));
});

test('config compartida admite proyectos de entorno coherentes y valida orígenes', () => {
  assert.equal(leerConfigCompartida({ ...base, GCLOUD_PROJECT: 'rincon-amancay', GOOGLE_CLOUD_PROJECT: 'rincon-amancay' }).proyecto, 'rincon-amancay');
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ORIGENES_WEB: '' }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ORIGENES_WEB: 'http://rincon-amancay.web.app' }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ORIGENES_WEB: 'https://*.example.test' }));
  assert.deepEqual(
    leerConfigCompartida({ ...base, AMANCAY_ORIGENES_WEB: 'https://a.example.test, https://b.example.test ,https://a.example.test' }).http.origenes,
    ['https://a.example.test', 'https://b.example.test'],
  );
});
