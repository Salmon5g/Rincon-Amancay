import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { deleteApp } from 'firebase/app';
import { terminate } from 'firebase/firestore';
import { obtenerFirebaseLocal } from '../app/lib/firebase-local.ts';
import { crearLecturasAdmin } from '../app/lib/admin-lecturas.ts';
import { auth as authAdmin, db as dbAdmin } from '../../backend/src/config/emulador.ts';

test('lecturas de administración respetan sesión y rol en Firestore', async t => {
  const uid = `lectura_${randomUUID()}`;
  const correo = `${uid}@example.test`, clave = randomUUID();
  const firebase = obtenerFirebaseLocal(uid);
  const cliente = crearLecturasAdmin(firebase);
  const acceso = dbAdmin.doc(`accesos/${uid}`);
  const categoria = dbAdmin.doc(`categorias/${uid}`);
  try {
    await authAdmin.createUser({ uid, email: correo, password: clave });
    await acceso.set({ estado: 'activo', roles: ['comprador'] });
    await categoria.set({ nombre: 'Categoría de prueba', activo: true });
    await t.test('sin sesión rechaza listados', async () => {
      assert.equal(await cliente.consultarAcceso(), null);
      await assert.rejects(cliente.listarAdministracion('administradores'));
    });
    await cliente.iniciarSesion(correo, clave);
    await t.test('comprador puede leer su acceso pero no listar administradores', async () => {
      assert.deepEqual((await cliente.consultarAcceso()).roles, ['comprador']);
      await assert.rejects(cliente.listarAdministracion('administradores'));
    });
    await acceso.update({ roles: ['administrador'] });
    await t.test('administrador lee registros desde servidor con las reglas del proyecto', async () => {
      assert((await cliente.listarAdministracion('administradores')).some(f => f.titulo === uid));
      assert((await cliente.listarAdministracion('catalogos')).some(f => f.detalle === `categorias / ${uid}`));
    });
    await acceso.update({ estado: 'inactivo' });
    await t.test('volver a consultar tras desactivar la cuenta rechaza el listado', async () => {
      await assert.rejects(cliente.listarAdministracion('administradores'));
    });
    await cliente.cerrarSesion();
    assert.equal(await cliente.consultarAcceso(), null);
    assert.equal('llamar' in cliente, false);
    assert.equal('subirImagen' in cliente, false);
  } finally {
    await cliente.cerrarSesion();
    await Promise.all([acceso.delete(), categoria.delete(), authAdmin.deleteUser(uid)]);
    await terminate(firebase.db);
    await deleteApp(firebase.app);
    await dbAdmin.terminate();
  }
});
