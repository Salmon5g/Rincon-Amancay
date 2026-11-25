import { test } from 'node:test';
import assert from 'node:assert/strict';
import { alternarSeleccion, crearLotesDisponibilidad, filtrarOrganizables } from '../app/lib/organizar-catalogo.ts';

const producto = (id, disponible, categoriaId = 'c1', extra = {}) => ({
  id, nombre: `Producto ${id}`, descripcion: '', categoriaId, disponible, versionEsperada: '1:0', estado: 'publicado', ...extra,
});

test('agrupa en lotes de 20 y omite los que ya tienen esa disponibilidad', () => {
  const productos = Array.from({ length: 45 }, (_, i) => producto(`p${i}`, i % 9 !== 0));
  let contador = 0;
  const lotes = crearLotesDisponibilidad(productos, false, () => `op${++contador}`);
  const total = lotes.reduce((suma, lote) => suma + lote.items.length, 0);
  assert.equal(total, 40);
  assert.deepEqual(lotes.map((lote) => lote.items.length), [20, 20]);
  assert.deepEqual(lotes.map((lote) => lote.operacionId), ['op1', 'op2']);
});

test('no repite productos ni envía los que no tienen versión', () => {
  const lotes = crearLotesDisponibilidad([producto('a', true), producto('a', true), producto('b', true, 'c1', { versionEsperada: '' })], false, () => 'x');
  assert.deepEqual(lotes, [{ operacionId: 'x', items: [{ productoId: 'a', versionEsperada: '1:0' }] }]);
  assert.deepEqual(crearLotesDisponibilidad([producto('a', false)], false, () => 'x'), []);
});

test('filtra por texto, categoría y disponibilidad', () => {
  const productos = [producto('a', true, 'c1', { nombre: 'Torta' }), producto('b', false, 'c2'), producto('c', true, 'c2', { estado: 'eliminado' })];
  assert.deepEqual(filtrarOrganizables(productos, { texto: 'tor', categoriaId: '', disponibilidad: 'todas' }).map((p) => p.id), ['a']);
  assert.deepEqual(filtrarOrganizables(productos, { texto: '', categoriaId: 'c2', disponibilidad: 'todas' }).map((p) => p.id), ['b']);
  assert.deepEqual(filtrarOrganizables(productos, { texto: '', categoriaId: '', disponibilidad: 'no_disponibles' }).map((p) => p.id), ['b']);
});

test('marca y desmarca grupos sin mutar la selección original', () => {
  const inicial = new Set(['a']);
  const marcada = alternarSeleccion(inicial, ['b', 'c'], true);
  assert.deepEqual([...marcada].sort(), ['a', 'b', 'c']);
  assert.deepEqual([...inicial], ['a']);
  assert.deepEqual([...alternarSeleccion(marcada, ['a', 'c'], false)], ['b']);
});
