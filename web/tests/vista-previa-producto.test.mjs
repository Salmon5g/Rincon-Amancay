import { test } from 'node:test';
import assert from 'node:assert/strict';
import { disponibilidadPrevia, motivoSinCompartir, nombreArchivoQr, precioPrevio, urlPublicaProducto } from '../app/lib/vista-previa-producto.ts';

const base = { estado: 'publicado', tieneVariantes: false, precioBase: 4500, stock: 3, manejaStock: true, modalidad: 'stock', disponible: true, piezaUnicaVendida: false, variantes: [] };
const variante = (id, precio, stock, activa = true) => ({ id, opciones: { color: id }, precio, stock, activa });

test('arma el enlace público del producto', () => {
  assert.equal(urlPublicaProducto('https://rinconamancay3.web.app', 't 1', 'p/2'), 'https://rinconamancay3.web.app/comprador?tienda=t+1&producto=p%2F2');
});

test('solo permite compartir con tienda y producto publicados y disponibles', () => {
  assert.equal(motivoSinCompartir(base, false), 'tienda');
  assert.equal(motivoSinCompartir({ ...base, estado: 'borrador' }, true), 'producto');
  assert.equal(motivoSinCompartir({ ...base, disponible: false }, true), 'disponible');
  assert.equal(motivoSinCompartir(base, true), null);
});

test('muestra precio desde el mínimo de variantes activas o el de la variante elegida', () => {
  const producto = { ...base, tieneVariantes: true, variantes: [variante('a', 5000, 1), variante('b', 3000, 0), variante('c', 1000, 4, false)] };
  assert.equal(precioPrevio(producto).replace(/\s/g, ' '), 'Desde $3.000');
  assert.equal(precioPrevio(producto, producto.variantes[0]).replace(/\s/g, ' '), '$5.000');
  assert.equal(precioPrevio({ ...base, precioBase: null }), 'Precio por definir');
});

test('resume la disponibilidad como la verá la compradora', () => {
  assert.deepEqual(disponibilidadPrevia(base), { texto: 'Disponible · 3 unidades', agotado: false });
  assert.deepEqual(disponibilidadPrevia({ ...base, stock: 0 }), { texto: 'Agotado', agotado: true });
  assert.deepEqual(disponibilidadPrevia({ ...base, modalidad: 'a_pedido' }), { texto: 'Disponible para encargo', agotado: false });
  assert.deepEqual(disponibilidadPrevia({ ...base, disponible: false }), { texto: 'No disponible por ahora', agotado: true });
  const conVariantes = { ...base, tieneVariantes: true, variantes: [variante('a', 1, 1), variante('b', 1, 2)] };
  assert.equal(disponibilidadPrevia(conVariantes).texto, 'Disponible · 3 unidades');
  assert.equal(disponibilidadPrevia(conVariantes, conVariantes.variantes[0]).texto, 'Disponible · 1 unidad');
});

test('genera un nombre de archivo simple para el QR', () => {
  assert.equal(nombreArchivoQr('Torta de Plátano «Especial»'), 'qr-torta-de-platano-especial.png');
  assert.equal(nombreArchivoQr('***'), 'qr-producto.png');
});
