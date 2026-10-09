import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import type { Auth } from 'firebase-admin/auth';
import { Timestamp } from 'firebase-admin/firestore';
import { db } from '../src/config/emulador.ts';
import { inspeccionarReserva, liberarReserva } from '../src/modules/recuperacion-cuentas.ts';
import { conBloqueoPersistente } from '../src/modules/serializacion-cuentas.ts';
import { crearDesactivacion } from '../src/modules/desactivacion.ts';
import { crearCicloCuentas } from '../src/modules/ciclo-cuentas.ts';
import { argumentosRecuperacion } from '../scripts/recuperar-cuenta.ts';

test('recuperación exige argumentos explícitos antes de conectar', () => {
  assert.throws(() => argumentosRecuperacion([]));
  assert.throws(() => argumentosRecuperacion(['--entorno', 'local', '--uid', 'u', '--ejecutor', 'e']));
  assert.throws(() => argumentosRecuperacion(['--entorno', 'local', '--uid', 'u', '--confirmar-servidores-detenidos']));
  assert.equal(argumentosRecuperacion(['--entorno', 'local', '--uid', 'u']).ejecutor, undefined);
});

test('recuperación después de terminar un proceso real', { timeout: 60000 }, async t => {
  try {
    for (const accion of ['desactivarEmprendedora', 'reactivarEmprendedora'] as const) {
      await t.test(accion, async () => {
        const uid = `rec_${randomUUID()}`, admin = `admin_${randomUUID()}`, tienda = `t_${randomUUID()}`;
        const fecha = Timestamp.now(), version = `${fecha.seconds}:${fecha.nanoseconds}`;
        const acceso = db.doc(`accesos/${uid}`), lock = db.doc(`ejecucionesCuentas/${uid}`);
        const coleccion = accion === 'desactivarEmprendedora' ? 'desactivaciones' : 'reactivaciones';
        const recibo = db.doc(`${coleccion}/${admin}_interrumpida`);
        const pub = db.doc(`tiendasPublicas/${tienda}`);
        let ejecutor: string | undefined;
        let child: ReturnType<typeof spawn> | undefined;
        try {
          await db.doc(`accesos/${admin}`).set({ estado: 'activo', roles: ['administrador'] });
          await acceso.set({ estado: accion === 'desactivarEmprendedora' ? 'activo' : 'desactivado', roles: ['emprendedora'], tiendaId: tienda, actualizadoEn: fecha });
          await db.doc(`tiendasPrivadas/${tienda}`).set({ propietarioUid: uid });
          await db.doc(`emprendedoras/${uid}`).set({ tiendaId: tienda });
          await pub.set({ habilitada: true, estadoPublicacion: 'publicado' });
          child = spawn(process.execPath, ['tests/fixtures/cuenta-interrumpida.ts', uid, admin, version, accion], { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
          await new Promise<void>((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error('No alcanzó la fase Auth')), 15000);
            let salida = '', errores = '';
            child!.stderr!.on('data', chunk => { errores += chunk; });
            child!.stdout!.on('data', chunk => { salida += chunk; if (salida.includes('FASE_AUTH')) { clearTimeout(timer); resolve(); } });
            child!.once('error', error => { clearTimeout(timer); reject(error); });
            child!.once('exit', code => { clearTimeout(timer); reject(new Error(`Proceso terminó antes: ${code} ${errores}`)); });
          });
          // Dos procesos independientes no pueden reservar la misma cuenta.
          await assert.rejects(conBloqueoPersistente(db, uid, admin, async () => {}), /reservada/);
          const fin = once(child, 'exit'); child.kill(); await fin;
          const inspeccion = await inspeccionarReserva(db, uid);
          ejecutor = inspeccion.reserva!.ejecutorId;
          assert.equal(inspeccion.operacionPendiente!.accion, accion);
          assert.equal(inspeccion.operacionPendiente!.solicitud.versionEsperada, version);
          const base = { uid, ejecutorId: ejecutor!, operador: 'prueba', motivo: 'Proceso terminado', servidoresDetenidos: true };
          await assert.rejects(liberarReserva(db, { ...base, servidoresDetenidos: false }), /Detener/);
          await assert.rejects(liberarReserva(db, { ...base, ejecutorId: 'otro' }), /cambió/);
          assert.equal((await lock.get()).exists, true);
          const recuperada = await liberarReserva(db, base);
          assert.equal(recuperada.repetida, false);
          assert.equal((await lock.get()).exists, false);
          assert.equal((await acceso.get()).data()!.estado, 'desactivado');
          assert.equal((await pub.get()).data()!.habilitada, false);
          assert.equal((await recibo.get()).data()!.estado, 'pendienteAuth');
          assert.equal((await liberarReserva(db, base)).repetida, true);
          // Reanudar exactamente la intención original, sin duplicar ni publicar.
          const llamadas: string[] = [];
          const auth = { updateUser: async (_uid: string, opciones: { disabled: boolean }) => { llamadas.push(String(opciones.disabled)); }, revokeRefreshTokens: async () => {} } as unknown as Auth;
          const solicitud = recuperada.operacionPendiente!.solicitud;
          const resultado = accion === 'desactivarEmprendedora'
            ? await crearDesactivacion(db, auth)({ uid: admin }, solicitud)
            : await crearCicloCuentas(db, auth)(accion, { uid: admin }, solicitud);
          assert.equal(resultado.estado, 'completada');
          assert.deepEqual(llamadas, [String(accion === 'desactivarEmprendedora')]);
          assert.equal((await pub.get()).data()!.habilitada, false);
          assert.equal((await acceso.get()).data()!.operacionCuentaPendiente, undefined);
          assert.equal((await db.doc(`recuperacionesCuentas/${ejecutor}`).get()).data()!.uid, uid);
          // Un intento viejo no puede borrar la reserva de una ejecución nueva.
          await lock.set({ ejecutorId: 'nuevo' });
          await assert.rejects(liberarReserva(db, base), /cambió/);
          assert.equal((await lock.get()).data()!.ejecutorId, 'nuevo');
        } finally {
          if (child && child.exitCode === null && child.signalCode === null) { const fin = once(child, 'exit'); child.kill(); await fin; }
          for (const ruta of [`accesos/${uid}`, `accesos/${admin}`, `tiendasPrivadas/${tienda}`, `emprendedoras/${uid}`, pub.path, recibo.path, lock.path, ...(ejecutor ? [`recuperacionesCuentas/${ejecutor}`] : [])]) await db.doc(ruta).delete();
        }
      });
    }
    await t.test('no libera un comprobante inconsistente', async () => {
      const uid = `rec_${randomUUID()}`, ejecutorId = randomUUID();
      try {
        await db.doc(`ejecucionesCuentas/${uid}`).set({ ejecutorId });
        await db.doc(`accesos/${uid}`).set({ estado: 'desactivado', operacionCuentaPendiente: 'desactivaciones/inexistente' });
        await assert.rejects(liberarReserva(db, { uid, ejecutorId, operador: 'prueba', motivo: 'prueba', servidoresDetenidos: true }), /inconsistentes/);
        assert.equal((await db.doc(`ejecucionesCuentas/${uid}`).get()).exists, true);
      } finally { await db.doc(`ejecucionesCuentas/${uid}`).delete(); await db.doc(`accesos/${uid}`).delete(); }
    });
  } finally { await db.terminate(); }
});
