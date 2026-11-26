import { test } from 'node:test';
import assert from 'node:assert/strict';
import { bloqueFecha, cuantoFalta, enlaceComoLlegar, haceCuanto, seccionesFerias, agruparPorDia, claveDia, horarioFeria, mapearInvitacionesFeria } from '../app/lib/ferias-emprendedora.ts';

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

const feriaDe = (id, estado, inicioIso, horas = 6, extra = {}) => {
  const inicio = inicioIso ? new Date(inicioIso) : null;
  return { id, nombre: id, donde: 'Plaza', organiza: 'Municipio', fechaInicio: inicio, fechaFin: inicio ? new Date(inicio.getTime() + horas * 3_600_000) : null, estado, descripcion: '', imagenUrl: '', latitud: null, longitud: null, invitadaEn: null, ...extra };
};

test('ordena las secciones: próxima, por responder, agenda por mes y pasadas', () => {
  const ahora = new Date('2026-11-25T15:00:00.000Z');
  const secciones = seccionesFerias([
    feriaDe('dic', 'aceptada', '2026-12-05T13:00:00.000Z'),
    feriaDe('nov', 'agregada_admin', '2026-11-28T13:00:00.000Z'),
    feriaDe('enero', 'aceptada', '2027-01-10T13:00:00.000Z'),
    feriaDe('pend', 'pendiente', '2026-12-12T14:00:00.000Z'),
    feriaDe('pendVieja', 'pendiente', '2026-11-01T14:00:00.000Z'),
    feriaDe('rech', 'rechazada', '2026-12-20T14:00:00.000Z'),
    feriaDe('pasada', 'aceptada', '2026-11-20T14:00:00.000Z'),
  ], ahora);
  assert.equal(secciones.proxima?.id, 'nov');
  assert.deepEqual(secciones.pendientes.map((f) => f.id), ['pend']);
  assert.deepEqual(secciones.agenda.map((m) => [m.titulo, m.ferias.map((f) => f.id)]), [['Noviembre', ['nov']], ['Diciembre', ['dic']], ['Enero 2027', ['enero']]]);
  assert.deepEqual(secciones.pasadas.map((f) => f.id), ['rech', 'pasada', 'pendVieja']);
});

test('una feria en curso sigue en la agenda y se marca como ahora mismo', () => {
  const ahora = new Date('2026-11-25T15:00:00.000Z');
  const enCurso = feriaDe('hoy', 'aceptada', '2026-11-25T13:00:00.000Z');
  assert.equal(seccionesFerias([enCurso], ahora).proxima?.id, 'hoy');
  assert.equal(cuantoFalta(enCurso, ahora), 'Ahora mismo');
  assert.equal(cuantoFalta(feriaDe('m', 'aceptada', '2026-11-26T13:00:00.000Z'), ahora), 'Mañana');
  assert.equal(cuantoFalta(feriaDe('t', 'aceptada', '2026-11-28T13:00:00.000Z'), ahora), 'En 3 días');
  assert.equal(cuantoFalta(feriaDe('s', 'aceptada', '2026-12-20T13:00:00.000Z'), ahora), 'En 4 semanas');
});

test('arma textos de invitación, bloque de fecha y enlace para llegar', () => {
  const ahora = new Date('2026-11-25T15:00:00.000Z');
  assert.equal(haceCuanto(new Date('2026-11-23T15:00:00.000Z'), ahora), 'Te invitaron hace 2 días');
  assert.equal(haceCuanto(new Date('2026-11-25T03:30:00.000Z'), ahora), 'Te invitaron hoy');
  assert.deepEqual(bloqueFecha(new Date('2026-12-05T02:00:00.000Z')), { dia: '4', mes: 'dic' });
  assert.equal(enlaceComoLlegar({ latitud: -41.47, longitud: -72.94, donde: 'Plaza' }), 'https://www.google.com/maps/dir/?api=1&destination=-41.47%2C-72.94');
  assert.equal(enlaceComoLlegar({ latitud: null, longitud: null, donde: 'Costanera, Puerto Montt' }), 'https://www.google.com/maps/dir/?api=1&destination=Costanera%2C+Puerto+Montt');
});

test('mapea imagen, descripción, coordenadas y fecha de invitación', () => {
  const [feria] = mapearInvitacionesFeria({ invitaciones: [{ id: 'f', estado: 'invitada', imagenUrl: 'http://inseguro/x.png', latitud: -41.5, longitud: 300, descripcion: ' Feria ', invitadaEn: '2026-11-20T00:00:00.000Z' }] });
  assert.equal(feria.imagenUrl, '');
  assert.equal(feria.latitud, -41.5);
  assert.equal(feria.longitud, null);
  assert.equal(feria.descripcion, 'Feria');
  assert.equal(feria.invitadaEn?.toISOString(), '2026-11-20T00:00:00.000Z');
});
