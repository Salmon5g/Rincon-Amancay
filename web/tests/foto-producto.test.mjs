import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ajustarLuzSuave, areaRecorte } from '../app/lib/foto-producto.ts';

test('el recorte cuadrado toma el centro de la foto', () => {
  assert.deepEqual(areaRecorte(4000, 3000, true), { x: 500, y: 0, ancho: 3000, alto: 3000 });
  assert.deepEqual(areaRecorte(1200, 1600, true), { x: 0, y: 200, ancho: 1200, alto: 1200 });
  assert.deepEqual(areaRecorte(1200, 1600, false), { x: 0, y: 0, ancho: 1200, alto: 1600 });
});

test('el ajuste suave de luz aclara los colores sin tocar la transparencia', () => {
  const pixeles = new Uint8ClampedArray([0, 100, 250, 128]);
  ajustarLuzSuave(pixeles);
  assert.deepEqual([...pixeles], [8, 112, 255, 128]);
});
