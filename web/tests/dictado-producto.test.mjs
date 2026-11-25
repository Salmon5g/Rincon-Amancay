import { test } from 'node:test';
import assert from 'node:assert/strict';
import { combinarDictado } from '../app/lib/dictado-producto.ts';

test('en texto agrega lo dictado al final con mayúscula inicial cuando corresponde', () => {
  assert.equal(combinarDictado('', 'torta de manjar', false), 'Torta de manjar');
  assert.equal(combinarDictado('Torta de manjar', 'con nueces', false), 'Torta de manjar con nueces');
  assert.equal(combinarDictado('Rica y casera.', 'hecha  en   casa', false), 'Rica y casera. Hecha en casa');
  assert.equal(combinarDictado('Algo', '   ', false), null);
});

test('en números reemplaza por los dígitos reconocidos', () => {
  assert.equal(combinarDictado('10', '$2.333', true), '2333');
  assert.equal(combinarDictado('', 'quedan 7 unidades', true), '7');
});

test('convierte dígitos hablados de cero a nueve y secuencias', () => {
  assert.equal(combinarDictado('', 'siete', true), '7');
  assert.equal(combinarDictado('', 'Uno dos tres.', true), '123');
  assert.equal(combinarDictado('', 'una', true), '1');
  assert.equal(combinarDictado('', 'cero', true), '0');
});

test('avisa con null cuando no entiende una cifra', () => {
  assert.equal(combinarDictado('5', 'muchas', true), null);
  assert.equal(combinarDictado('5', 'dos mil', true), null);
  assert.equal(combinarDictado('5', '', true), null);
});
