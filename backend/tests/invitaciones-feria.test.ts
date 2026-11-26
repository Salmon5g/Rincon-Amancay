import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { getApps, initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import { crearFerias } from '../src/modules/ferias.ts';

const projectId = 'demo-rincon-amancay';
if (process.env.NODE_ENV === 'production') throw new Error('La prueba de invitaciones solo admite emuladores.');
const host = process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080';
if (!/^127\.0\.0\.1:\d{2,5}$/.test(host)) throw new Error('La prueba solo admite Firestore local.');
process.env.FIRESTORE_EMULATOR_HOST = host;
process.env.GCLOUD_PROJECT = projectId;

const app = getApps().find(a => a.name === 'prueba-invitaciones') ?? initializeApp({ projectId }, 'prueba-invitaciones');
const db = getFirestore(app);
const listar = crearFerias(db);

async function feria(id: string, estado: string, nombre: string, dias: number) {
  await db.doc(`ferias/${id}`).set({
    nombre, estado, direccion: 'Plaza de prueba', organizador: 'Equipo de prueba', descripcion: 'Feria de prueba', imagenUrl: 'https://ejemplo.test/feria.webp', latitud: -41.47, longitud: -72.94,
    fechaInicio: Timestamp.fromMillis(Date.now() + dias * 86_400_000),
    fechaFin: Timestamp.fromMillis(Date.now() + dias * 86_400_000 + 3_600_000),
  });
}

async function participante(feriaId: string, uid: string, estado: string) {
  await db.doc(`ferias/${feriaId}/participantes/emprendedora_${uid}`).set({ tipo: 'emprendedora', entidadId: uid, estado, creadoEn: Timestamp.fromMillis(Date.UTC(2026, 10, 20)) });
}

test('lista solo las participaciones propias en ferias en borrador o publicadas', async () => {
  const uid = `u_${randomUUID().replaceAll('-', '')}`;
  const otra = `u_${randomUUID().replaceAll('-', '')}`;
  const sufijo = randomUUID().slice(0, 8);
  await db.doc(`accesos/${uid}`).set({ estado: 'activo', roles: ['emprendedora'], tiendaId: `t_${sufijo}` });
  await feria(`publicada_${sufijo}`, 'publicada', 'Feria publicada', 5);
  await feria(`borrador_${sufijo}`, 'borrador', 'Feria en borrador', 2);
  await feria(`cancelada_${sufijo}`, 'cancelada', 'Feria cancelada', 3);
  await feria(`sin_${sufijo}`, 'publicada', 'Feria sin participación', 1);
  await participante(`publicada_${sufijo}`, uid, 'invitada');
  await participante(`borrador_${sufijo}`, uid, 'agregada_admin');
  await participante(`cancelada_${sufijo}`, uid, 'confirmada');
  await participante(`sin_${sufijo}`, otra, 'invitada');

  const resultado = await listar('listarInvitacionesFeria', { uid }, {}) as { invitaciones: { id: string; nombre: string; estado: string; donde: string; organiza: string; fechaInicio: string | null; descripcion: string; imagenUrl: string; latitud: number | null; longitud: number | null; invitadaEn: string | null }[] };

  assert.deepEqual(resultado.invitaciones.map(i => [i.id, i.estado]), [
    [`borrador_${sufijo}`, 'agregada_admin'],
    [`publicada_${sufijo}`, 'invitada'],
  ]);
  assert.equal(resultado.invitaciones[1]?.nombre, 'Feria publicada');
  assert.equal(resultado.invitaciones[1]?.donde, 'Plaza de prueba');
  assert.equal(resultado.invitaciones[1]?.organiza, 'Equipo de prueba');
  assert.ok(resultado.invitaciones[1]?.fechaInicio);
  assert.equal(resultado.invitaciones[1]?.descripcion, 'Feria de prueba');
  assert.equal(resultado.invitaciones[1]?.imagenUrl, 'https://ejemplo.test/feria.webp');
  assert.equal(resultado.invitaciones[1]?.latitud, -41.47);
  assert.equal(resultado.invitaciones[1]?.longitud, -72.94);
  assert.equal(resultado.invitaciones[1]?.invitadaEn, '2026-11-20T00:00:00.000Z');
});

test('rechaza a una cuenta sin rol emprendedora activo', async () => {
  const uid = `u_${randomUUID().replaceAll('-', '')}`;
  await db.doc(`accesos/${uid}`).set({ estado: 'activo', roles: ['comprador'] });
  await assert.rejects(() => listar('listarInvitacionesFeria', { uid }, {}), (error: { code?: string; codigo?: string }) => (error.code ?? error.codigo) === 'sin-permiso');
});
