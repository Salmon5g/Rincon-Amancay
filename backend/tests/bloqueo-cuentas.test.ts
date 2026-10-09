import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { Timestamp } from 'firebase-admin/firestore';
import { db } from '../src/config/emulador.ts';
import { conBloqueoPersistente, porCuenta } from '../src/modules/serializacion-cuentas.ts';
import { ErrorOperacion } from '../src/modules/publicacion.ts';

const espera = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
const conCodigo = (code: string) => (error: unknown) => error instanceof ErrorOperacion && error.code === code;

test('bloqueo persistente de cuentas: exclusión, liberación y serialización', async t => {
  const prefix = 'bloqueo_' + randomUUID();
  const adminUid = prefix + '_admin', ajenoUid = prefix + '_ajeno', uid = 'empr_' + randomUUID();
  const ref = db.doc(`ejecucionesCuentas/${uid}`);
  const actor = db.doc(`accesos/${adminUid}`), ajeno = db.doc(`accesos/${ajenoUid}`);
  try {
    await actor.set({ estado: 'activo', roles: ['administrador'], actualizadoEn: Timestamp.now() });
    await ajeno.set({ estado: 'activo', roles: ['comprador'], actualizadoEn: Timestamp.now() });

    await t.test('exige un administrador activo', async () => {
      await assert.rejects(() => conBloqueoPersistente(db, uid, ajenoUid, async () => 'x'), conCodigo('sin-permiso'));
      await ajeno.update({ estado: 'desactivado' });
      await assert.rejects(() => conBloqueoPersistente(db, uid, ajenoUid, async () => 'x'), conCodigo('sin-permiso'));
      assert.equal((await ref.get()).exists, false);
    });

    await t.test('rechaza identificadores inválidos sin reservar', async () => {
      await assert.rejects(() => conBloqueoPersistente(db, 'uid inválido', adminUid, async () => 'x'), conCodigo('datos-invalidos'));
      await assert.rejects(() => conBloqueoPersistente(db, uid, 'actor inválido', async () => 'x'), conCodigo('datos-invalidos'));
      assert.equal((await ref.get()).exists, false);
    });

    await t.test('reserva durante la ejecución y libera al terminar', async () => {
      let reservado = false;
      const resultado = await conBloqueoPersistente(db, uid, adminUid, async () => { reservado = (await ref.get()).exists; return 'ok'; });
      assert.equal(resultado, 'ok');
      assert.equal(reservado, true);
      assert.equal((await ref.get()).exists, false);
    });

    await t.test('una segunda ejecución simultánea es rechazada', async () => {
      let segundo: unknown;
      await conBloqueoPersistente(db, uid, adminUid, async () => {
        await conBloqueoPersistente(db, uid, adminUid, async () => 'no debería ejecutarse').catch((error: unknown) => { segundo = error; });
        return 'primera';
      });
      assert.ok(segundo instanceof ErrorOperacion);
      assert.equal((segundo as ErrorOperacion).code, 'pendiente');
      assert.equal((await ref.get()).exists, false);
    });

    await t.test('libera la reserva aunque la ejecución falle', async () => {
      await assert.rejects(() => conBloqueoPersistente(db, uid, adminUid, async () => { throw new Error('fallo simulado'); }), /fallo simulado/);
      assert.equal((await ref.get()).exists, false);
    });

    await t.test('solo una de dos reservas concurrentes gana la cuenta', async () => {
      const resultados = await Promise.allSettled([
        conBloqueoPersistente(db, uid, adminUid, async () => { await espera(80); return 'a'; }),
        conBloqueoPersistente(db, uid, adminUid, async () => { await espera(80); return 'b'; }),
      ]);
      const ganadas = resultados.filter(r => r.status === 'fulfilled');
      const perdidas = resultados.filter((r): r is PromiseRejectedResult => r.status === 'rejected');
      assert.equal(ganadas.length, 1);
      assert.equal(perdidas.length, 1);
      assert.ok(perdidas[0].reason instanceof ErrorOperacion);
      assert.equal((perdidas[0].reason as ErrorOperacion).code, 'pendiente');
      assert.equal((await ref.get()).exists, false);
    });

    await t.test('una reserva huérfana exige revisión técnica (sin vencimiento)', async () => {
      await ref.set({ ejecutorId: 'proceso-antiguo', actorUid: adminUid, creadoEn: Timestamp.now() });
      await assert.rejects(() => conBloqueoPersistente(db, uid, adminUid, async () => 'x'), conCodigo('pendiente'));
      assert.equal((await ref.get()).data()!.ejecutorId, 'proceso-antiguo');
      await ref.delete();
    });

    await t.test('porCuenta serializa por UID dentro del proceso', async () => {
      const orden: string[] = [];
      const primera = porCuenta(db, uid, adminUid, async () => { orden.push('inicio-1'); await espera(60); orden.push('fin-1'); });
      const segunda = porCuenta(db, uid, adminUid, async () => { orden.push('inicio-2'); await espera(10); orden.push('fin-2'); });
      await Promise.all([primera, segunda]);
      assert.deepEqual(orden, ['inicio-1', 'fin-1', 'inicio-2', 'fin-2']);
      assert.equal((await ref.get()).exists, false);
    });
  } finally {
    await ref.delete().catch(() => undefined);
    await actor.delete(); await ajeno.delete();
    await db.terminate();
  }
});
