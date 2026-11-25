import { test } from 'node:test';
import assert from 'node:assert/strict';
import { atributosConEtiqueta, etiquetaLegibleAtributo } from '../app/lib/atributos-producto.ts';

test('convierte claves internas en etiquetas legibles', () => {
  assert.equal(etiquetaLegibleAtributo('alergenos'), 'Alérgenos');
  assert.equal(etiquetaLegibleAtributo('fechaElaboracion'), 'Fecha de elaboración');
  assert.equal(etiquetaLegibleAtributo('peso'), 'Peso');
  assert.equal(etiquetaLegibleAtributo('tipo_de_tela'), 'Tipo de tela');
});

test('prefiere la etiqueta definida por el tipo de producto y omite valores vacíos', () => {
  const resultado = atributosConEtiqueta(
    { ingredientes: ' harina ', fechaElaboracion: '2026-11-20', vacio: '  ' },
    { ingredientes: 'Ingredientes principales' },
  );
  assert.deepEqual(resultado, [
    { clave: 'ingredientes', etiqueta: 'Ingredientes principales', valor: 'harina' },
    { clave: 'fechaElaboracion', etiqueta: 'Fecha de elaboración', valor: '2026-11-20' },
  ]);
});
