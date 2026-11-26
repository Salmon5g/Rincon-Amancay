import { test } from 'node:test';
import assert from 'node:assert/strict';
import { agruparPorDia, barrasPorDia, etiquetaDia, periodoHistorial, productoMasVendido, variacionTotal, ventasCsv, claveLugar, filtrarVentas, inicioDiaSantiago, opcionesFiltro, rangoHistorial, restarDias, resumirVentas } from '../app/lib/historial-ventas.ts';

const venta = (id, productoId, cantidad, total, lugarTipo, lugarNombre, feriaId = '') => ({
  id, productoId, productoNombre: `Producto ${productoId}`, varianteNombre: '', precioUnitario: total / cantidad, cantidad, total,
  fecha: new Date('2026-11-20T15:00:00.000Z'), lugarTipo, feriaId, lugarNombre,
});

test('calcula el inicio del día en Chile, con y sin horario de verano', () => {
  assert.equal(inicioDiaSantiago('2026-11-25').toISOString(), '2026-11-25T03:00:00.000Z');
  assert.equal(inicioDiaSantiago('2026-06-15').toISOString(), '2026-06-15T04:00:00.000Z');
});

test('el rango incluye todo el día final y rechaza fechas invertidas', () => {
  const rango = rangoHistorial('2026-11-01', '2026-11-25');
  assert.equal(rango.desde.toISOString(), '2026-11-01T03:00:00.000Z');
  assert.equal(rango.hasta.toISOString(), '2026-11-26T03:00:00.000Z');
  assert.throws(() => rangoHistorial('2026-11-25', '2026-11-01'));
  assert.equal(restarDias('2026-03-01', 1), '2026-02-28');
});

test('filtra por producto y lugar, y resume unidades y total', () => {
  const ventas = [
    venta('a', 'p1', 2, 10000, 'feria', 'Feria Navidad', 'f1'),
    venta('b', 'p2', 1, 3000, 'otro', 'Domicilio'),
    venta('c', 'p1', 1, 5000, 'otro', 'domicilio'),
  ];
  assert.equal(claveLugar(ventas[1]), claveLugar(ventas[2]));
  assert.deepEqual(filtrarVentas(ventas, { productoId: 'p1', lugar: '' }).map((v) => v.id), ['a', 'c']);
  assert.deepEqual(filtrarVentas(ventas, { productoId: '', lugar: 'feria:f1' }).map((v) => v.id), ['a']);
  assert.deepEqual(resumirVentas(ventas), { unidades: 4, total: 18000, ventas: 3 });
  const opciones = opcionesFiltro(ventas);
  assert.deepEqual(opciones.productos.map((p) => p.id), ['p1', 'p2']);
  assert.equal(opciones.lugares.length, 2);
});

const ventaEn = (id, iso, total, extra = {}) => ({
  id, productoId: 'p1', productoNombre: 'Torta', varianteNombre: '', precioUnitario: total, cantidad: 1, total,
  fecha: new Date(iso), lugarTipo: 'otro', feriaId: '', lugarNombre: 'Casa', ...extra,
});

test('arma los períodos rápidos y el período anterior para comparar', () => {
  assert.deepEqual(periodoHistorial('hoy', '2026-11-25').anterior, { desde: '2026-11-24', hasta: '2026-11-24' });
  const semana = periodoHistorial('semana', '2026-11-25');
  assert.equal(semana.desde, '2026-11-23');
  assert.deepEqual(semana.anterior, { desde: '2026-11-16', hasta: '2026-11-18' });
  const mes = periodoHistorial('mes', '2026-03-31');
  assert.equal(mes.desde, '2026-03-01');
  assert.deepEqual(mes.anterior, { desde: '2026-02-01', hasta: '2026-02-28' });
  assert.equal(mes.comparacion, 'que febrero a la misma fecha');
  const libre = periodoHistorial('personalizado', '2026-11-25', '2026-11-10', '2026-11-19');
  assert.deepEqual(libre.anterior, { desde: '2026-10-31', hasta: '2026-11-09' });
  assert.equal(libre.comparacion, 'que los 10 días anteriores');
});

test('agrupa por día en hora de Chile y nombra los días', () => {
  const ventas = [ventaEn('a', '2026-11-25T02:30:00Z', 1000), ventaEn('b', '2026-11-25T15:00:00Z', 2000), ventaEn('c', '2026-11-24T15:00:00Z', 500)];
  const grupos = agruparPorDia(ventas);
  assert.deepEqual(grupos.map((g) => [g.dia, g.total]), [['2026-11-25', 2000], ['2026-11-24', 1500]]);
  assert.equal(etiquetaDia('2026-11-25', '2026-11-25'), 'Hoy · miércoles 25');
  assert.equal(etiquetaDia('2026-11-24', '2026-11-25'), 'Ayer · martes 24');
  assert.equal(etiquetaDia('2026-11-02', '2026-11-25'), 'Lunes 2 de noviembre');
});

test('calcula barras por día, producto más vendido y variación', () => {
  const ventas = [ventaEn('a', '2026-11-24T15:00:00Z', 1000, { lugarTipo: 'feria', feriaId: 'f' }), ventaEn('b', '2026-11-25T15:00:00Z', 4000, { productoId: 'p2', productoNombre: 'Pie', cantidad: 3 })];
  const barras = barrasPorDia(ventas, '2026-11-23', '2026-11-25');
  assert.deepEqual(barras.map((b) => [b.dia, b.proporcion, b.conFeria]), [['2026-11-23', 0, false], ['2026-11-24', 0.25, true], ['2026-11-25', 1, false]]);
  assert.deepEqual(barrasPorDia(ventas, '2026-11-25', '2026-11-25'), []);
  assert.deepEqual(productoMasVendido(ventas), { nombre: 'Pie', unidades: 3 });
  assert.deepEqual(variacionTotal(5000, 8000), { diferencia: 3000, tendencia: 'baja' });
});

test('filtra por texto y día', () => {
  const ventas = [ventaEn('a', '2026-11-24T15:00:00Z', 1000, { productoNombre: 'Pié de limón' }), ventaEn('b', '2026-11-25T15:00:00Z', 4000)];
  assert.deepEqual(filtrarVentas(ventas, { lugar: '', texto: 'pie' }).map((v) => v.id), ['a']);
  assert.deepEqual(filtrarVentas(ventas, { lugar: '', dia: '2026-11-25' }).map((v) => v.id), ['b']);
});

test('exporta CSV con separador punto y coma y celdas seguras', () => {
  const csv = ventasCsv([ventaEn('a', '2026-11-25T15:00:00Z', 1000, { productoNombre: '=Torta; "rica"' })]);
  const lineas = csv.replace('\uFEFF', '').trim().split('\r\n');
  assert.equal(lineas[0], 'Fecha;Hora;Producto;Variante;Cantidad;Precio unitario;Total;Lugar');
  assert.equal(lineas[1], `2026-11-25;12:00;"'=Torta; ""rica""";;1;1000;1000;Casa`);
});
