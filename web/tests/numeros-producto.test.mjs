import { test } from 'node:test';
import assert from 'node:assert/strict';
import { enteroNoNegativoFormulario, textoNumericoFormulario } from '../app/lib/numeros-producto.ts';

test('acepta precio y stock entregados como número o texto de borrador', () => {
  assert.equal(enteroNoNegativoFormulario(6990), 6990);
  assert.equal(enteroNoNegativoFormulario('6990'), 6990);
  assert.equal(enteroNoNegativoFormulario(' 0 '), 0);
});

test('rechaza campos vacíos, fraccionarios, negativos y fuera del rango seguro', () => {
  for (const valor of ['', null, undefined, 1.5, -1, '1.5', '-1', '9007199254740992', Number.NaN]) {
    assert.equal(enteroNoNegativoFormulario(valor), null, String(valor));
  }
});

test('normaliza número y texto para comparar el stock de pieza única', () => {
  assert.equal(textoNumericoFormulario(1), '1');
  assert.equal(textoNumericoFormulario(' 1 '), '1');
  assert.equal(textoNumericoFormulario(null), '');
});
