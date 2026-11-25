import { test } from 'node:test';
import assert from 'node:assert/strict';
import { agruparPorDia, claveDia, horarioFeria, mapearInvitacionesFeria } from '../app/lib/ferias-emprendedora.ts';

test('traduce los estados de la API y descarta filas incompletas', () => {
  const invitaciones = mapearInvitacionesFeria({
    invitaciones: [
      { id: 'f1', nombre: 'Feria de primavera', donde: 'Plaza', organiza: 'Municipio', fechaInicio: '2026-12-05T13:00:00.000Z', fechaFin: '2026-12-05T20:00:00.000Z', estado: 'invitada' },
      { id: 'f2', nombre: '', estado: 'confirmada' },
      { id: 'f3', estado: 'desconocido' },
      null,
    ],
  });
  assert.deepEqual(invitaciones.map((i) => [i.id, i.estado, i.nombre]), [['f1', 'pendiente', 'Feria de primavera'], ['f2', 'aceptada', 'Feria']]);
  assert.equal(invitaciones[1].donde, 'Lugar por confirmar');
});

test('muestra horario y día en hora de Chile', () => {
  const inicio = new Date('2026-12-05T13:00:00.000Z');
  const fin = new Date('2026-12-05T20:30:00.000Z');
  assert.equal(horarioFeria(inicio, fin), '10:00 a 17:30');
  assert.equal(claveDia(new Date('2026-12-06T02:00:00.000Z')), '2026-12-05');
  assert.equal(horarioFeria(null, fin), 'Horario por confirmar');
});

test('agrupa por día y omite rechazadas o sin fecha', () => {
  const grupos = agruparPorDia(mapearInvitacionesFeria({
    invitaciones: [
      { id: 'a', estado: 'invitada', fechaInicio: '2026-12-05T13:00:00.000Z', fechaFin: '2026-12-05T20:00:00.000Z' },
      { id: 'b', estado: 'confirmada', fechaInicio: '2026-12-05T15:00:00.000Z', fechaFin: '2026-12-05T18:00:00.000Z' },
      { id: 'c', estado: 'rechazada', fechaInicio: '2026-12-05T15:00:00.000Z' },
      { id: 'd', estado: 'agregada_admin' },
    ],
  }));
  assert.deepEqual([...grupos.keys()], ['2026-12-05']);
  assert.deepEqual(grupos.get('2026-12-05').map((i) => i.id), ['a', 'b']);
});

test('reutiliza la lectura reciente y agrupa llamadas simultáneas', async () => {
  const { obtenerInvitacionesFeria, olvidarInvitaciones } = await import('../app/lib/ferias-emprendedora.ts');
  olvidarInvitaciones();
  let llamadas = 0;
  const llamar = async () => { llamadas++; return { invitaciones: [{ id: 'x', estado: 'invitada' }] }; };
  const [a, b] = await Promise.all([obtenerInvitacionesFeria('u1', llamar), obtenerInvitacionesFeria('u1', llamar)]);
  await obtenerInvitacionesFeria('u1', llamar);
  assert.equal(llamadas, 1);
  assert.equal(a, b);
  await obtenerInvitacionesFeria('u1', llamar, true);
  assert.equal(llamadas, 2);
});
