import { test } from 'node:test';
import assert from 'node:assert/strict';
import { crearPrimerAdministrador } from '../scripts/admin-compartido.ts';

const base = {
  AMANCAY_ENTORNO: 'compartido',
  AMANCAY_PROJECT_ID: 'rincon-amancay',
};

test('admin compartido exige entorno seguro y datos válidos antes de tocar la red', async () => {
  await assert.rejects(crearPrimerAdministrador({}, 'admin@example.test', 'Administración'), /AMANCAY_ENTORNO/);
  await assert.rejects(crearPrimerAdministrador({ ...base, FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080' }, 'admin@example.test', 'Administración'), /incompatible/);
  await assert.rejects(crearPrimerAdministrador(base, 'no-es-correo', 'Administración'), /Correo inválido/);
  await assert.rejects(crearPrimerAdministrador(base, 'admin@example.test', '   '), /nombre/);
});
