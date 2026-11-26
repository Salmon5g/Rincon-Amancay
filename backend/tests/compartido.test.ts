import { test } from 'node:test';
import assert from 'node:assert/strict';
import { leerConfigCompartida } from '../src/config/compartido.ts';

const base = {
  AMANCAY_ENTORNO: 'compartido',
  AMANCAY_PROJECT_ID: 'amancay-test-project',
  AMANCAY_PROJECT_ID_ESPERADO: 'amancay-test-project',
  AMANCAY_ORIGENES_WEB: 'https://amancay.test',
};

test('config compartida: proyecto, puerto y HTTP explícitos', () => {
  const config = leerConfigCompartida({ ...base, PORT: '9090' });
  assert.equal(config.proyecto, 'amancay-test-project');
  assert.equal(config.port, 9090);
  assert.deepEqual(config.http, { entorno: 'compartido', proyecto: 'amancay-test-project', origenes: ['https://amancay.test'] });
  assert.equal(leerConfigCompartida({ ...base }).port, 8080);
});

test('config compartida rechaza emuladores, credenciales y proyectos ajenos', () => {
  for (const key of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST', 'FIREBASE_STORAGE_EMULATOR_HOST', 'STORAGE_EMULATOR_HOST', 'GOOGLE_APPLICATION_CREDENTIALS']) {
    assert.throws(() => leerConfigCompartida({ ...base, [key]: 'x' }));
  }
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ENTORNO: 'emulador' }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ENTORNO: undefined }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_PROJECT_ID: 'demo-amancay-test' }));
  assert.throws(() => leerConfigCompartida({ ...base, GCLOUD_PROJECT: 'otro-proyecto' }));
  assert.throws(() => leerConfigCompartida({ ...base, GOOGLE_CLOUD_PROJECT: 'otro-proyecto' }));
  assert.throws(() => leerConfigCompartida({ ...base, PORT: '0' }));
  assert.throws(() => leerConfigCompartida({ ...base, PORT: '70000' }));
  assert.throws(() => leerConfigCompartida({ ...base, PORT: 'abc' }));
});

test('config compartida admite proyectos de entorno coherentes y valida orígenes', () => {
  assert.equal(leerConfigCompartida({ ...base, GCLOUD_PROJECT: 'amancay-test-project', GOOGLE_CLOUD_PROJECT: 'amancay-test-project' }).proyecto, 'amancay-test-project');
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ORIGENES_WEB: '' }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ORIGENES_WEB: 'http://amancay.test' }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_ORIGENES_WEB: 'https://*.example.test' }));
  assert.deepEqual(
    leerConfigCompartida({ ...base, AMANCAY_ORIGENES_WEB: 'https://a.example.test, https://b.example.test ,https://a.example.test' }).http.origenes,
    ['https://a.example.test', 'https://b.example.test'],
  );
});

const cuentaServicio = (extra = {}) => ({
  type: 'service_account',
  project_id: 'amancay-test-project',
  client_email: 'api@amancay-test-project.iam.gserviceaccount.com',
  private_key: '-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n',
  ...extra,
});

test('config compartida elige la interfaz de escucha', () => {
  assert.equal(leerConfigCompartida({ ...base }).host, '127.0.0.1');
  assert.equal(leerConfigCompartida({ ...base, K_SERVICE: 'amancay-api' }).host, '0.0.0.0');
  assert.equal(leerConfigCompartida({ ...base, AMANCAY_HOST: '0.0.0.0' }).host, '0.0.0.0');
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_HOST: '192.168.1.10' }));
});

test('config compartida acepta una cuenta de servicio del mismo proyecto', () => {
  assert.equal(leerConfigCompartida({ ...base }).credencial, null);
  const json = JSON.stringify(cuentaServicio());
  const esperada = { projectId: 'amancay-test-project', clientEmail: 'api@amancay-test-project.iam.gserviceaccount.com', privateKey: cuentaServicio().private_key };
  assert.deepEqual(leerConfigCompartida({ ...base, AMANCAY_CREDENCIAL_SERVICIO: json }).credencial, esperada);
  assert.deepEqual(leerConfigCompartida({ ...base, AMANCAY_CREDENCIAL_SERVICIO: Buffer.from(json).toString('base64') }).credencial, esperada);
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_CREDENCIAL_SERVICIO: JSON.stringify(cuentaServicio({ project_id: 'otro-proyecto' })) }), /otro proyecto/);
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_CREDENCIAL_SERVICIO: JSON.stringify(cuentaServicio({ type: 'authorized_user' })) }));
  assert.throws(() => leerConfigCompartida({ ...base, AMANCAY_CREDENCIAL_SERVICIO: '{no es json' }), (error: Error) => !error.message.includes('no es json'));
});
