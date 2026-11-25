import { test } from 'node:test';
import assert from 'node:assert/strict';
import { claveLugar, filtrarVentas, inicioDiaSantiago, opcionesFiltro, rangoHistorial, restarDias, resumirVentas } from '../app/lib/historial-ventas.ts';

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
