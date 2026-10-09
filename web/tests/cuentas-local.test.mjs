import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { deleteApp } from 'firebase/app';
import { terminate } from 'firebase/firestore';
import { obtenerFirebaseLocal } from '../app/lib/firebase-local.ts';
import { crearClienteLocal } from '../app/lib/cliente-local.ts';
import { auth as adminAuth, db as adminDb } from '../../backend/src/config/emulador.ts';

test('registro y recuperación con adaptador web real en emuladores',async t=>{
  const suffix=randomUUID(),correo=`sdk_${suffix}@example.test`,clave=randomUUID();
  const local=obtenerFirebaseLocal(`cuentas_${suffix}`),client=crearClienteLocal(local);
  let uid;
  async function codigo(tipo) {
    const r=await fetch('http://127.0.0.1:9099/emulator/v1/projects/demo-rincon-amancay/oobCodes');assert.equal(r.status,200);
    const c=(await r.json()).oobCodes.filter(c=>c.email===correo && c.requestType===tipo).at(-1);assert(c);return c.oobCode;
  }
  try {
    await t.test('crear identidad no concede rol automáticamente',async()=>{
      uid=(await client.crearIdentidad(correo,clave)).user.uid;
      assert.equal((await adminDb.doc(`accesos/${uid}`).get()).exists,false);
      await assert.rejects(client.llamar('registrarComprador',{nombreMostrar:'Persona SDK'}),{code:'sin-permiso'});
    });
    await t.test('verificar correo y registrar usando token del cliente',async()=>{
      await client.enviarVerificacion();await client.confirmarCorreo(await codigo('VERIFY_EMAIL'));
      assert.equal(local.auth.currentUser.emailVerified,true);
      await client.llamar('registrarComprador',{nombreMostrar:'Persona SDK'});
      assert.deepEqual((await client.consultarAcceso()).roles,['comprador']);
      await client.llamar('registrarComprador',{nombreMostrar:'Reintento'});
    });
    await t.test('recuperación confirma código, cierra sesión y permite nueva clave',async()=>{
      await client.solicitarRecuperacion(correo);const code=await codigo('PASSWORD_RESET');
      assert.equal(await client.comprobarRecuperacion(code),correo);
      const nueva=randomUUID();await client.confirmarRecuperacion(code,nueva);assert.equal(local.auth.currentUser,null);
      await assert.rejects(client.iniciarSesion(correo,clave));await client.iniciarSesion(correo,nueva);
      assert.deepEqual((await client.consultarAcceso()).roles,['comprador']);
      await assert.rejects(client.confirmarRecuperacion(code,randomUUID()));
    });
    await t.test('solicitud para correo desconocido da el mismo mensaje',async()=>{
      const a=await client.solicitarRecuperacion(correo),b=await client.solicitarRecuperacion(`ausente_${suffix}@example.test`);
      assert.deepEqual(a,b);
    });
    await t.test('recuperar contraseña no reactiva el acceso desactivado',async()=>{
      await adminDb.doc(`accesos/${uid}`).update({estado:'desactivado'});
      await client.solicitarRecuperacion(correo);const nueva=randomUUID();await client.confirmarRecuperacion(await codigo('PASSWORD_RESET'),nueva);
      await client.iniciarSesion(correo,nueva);
      await assert.rejects(client.llamar('registrarComprador',{nombreMostrar:'Persona SDK'}),{code:'conflicto'});
      assert.equal((await adminDb.doc(`accesos/${uid}`).get()).data().estado,'desactivado');
    });
  } finally {
    await client.cerrarSesion();await terminate(local.db);await deleteApp(local.app);
    if(uid) {await adminDb.doc(`accesos/${uid}`).delete();await adminDb.doc(`usuarios/${uid}`).delete();await adminAuth.deleteUser(uid);}
    await adminDb.terminate();
  }
});
