import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { getApps, initializeApp } from 'firebase-admin/app';
import { Timestamp } from 'firebase-admin/firestore';
import { getFirestore } from 'firebase-admin/firestore';
import { crearVentas } from '../src/modules/ventas.ts';

const projectId = 'demo-rincon-amancay';
if (process.env.NODE_ENV === 'production') throw new Error('La prueba de ventas solo admite emuladores.');
if (process.env.FIRESTORE_EMULATOR_HOST && process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') throw new Error('La prueba solo admite Firestore local.');
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
for (const key of ['GCLOUD_PROJECT', 'GOOGLE_CLOUD_PROJECT']) {
  if (process.env[key] && process.env[key] !== projectId) throw new Error('Proyecto real rechazado.');
}
const app = getApps().find(item => item.name === 'ventas-test') ?? initializeApp({ projectId }, 'ventas-test');
const db = getFirestore(app);

test('ventas manuales descuentan stock y guardan el historial configurado', async t => {
  const uid = `venta_${randomUUID()}`;
  const tiendaId = uid;
  const tienda = db.doc(`tiendasPrivadas/${tiendaId}`);
  const producto = tienda.collection('productos').doc('producto');
  const ventas = crearVentas(db);

  async function registrar(extra: Record<string, unknown> = {}) {
    return ventas('registrarVenta', { uid }, {
      tiendaId,
      operacionId: randomUUID(),
      productoId: 'producto',
      varianteId: 'azul',
      cantidad: 2,
      lugarTipo: 'otro',
      lugarTexto: 'Feria Plaza Ñuñoa',
      fecha: '2025-03-12T15:00:00.000Z',
      ...extra
    });
  }

  try {
    await db.doc(`accesos/${uid}`).set({ estado: 'activo', roles: ['emprendedora'], tiendaId });
    await tienda.set({ propietarioUid: uid, estadoPublicacion: 'borrador', cicloCatalogo: 0 });
    await tienda.collection('configuracion').doc('general').set({ historialVentasActivo: true });
    await producto.set({ nombre: 'Bufanda tejida', estadoPublicacion: 'borrador', modalidad: 'regular', manejaStock: true, tieneVariantes: true, actualizadoEn: Timestamp.now() });
    await producto.collection('variantes').doc('azul').set({ opciones: { color: 'Azul' }, precio: 9000, stock: 5, activa: true });
    await producto.collection('variantes').doc('roja').set({ opciones: { color: 'Roja' }, precio: 11000, stock: 8, activa: true });

    await t.test('registra variante, cantidad, precio, fecha y lugar; descuenta solo esa variante', async () => {
      const operacionId = randomUUID();
      const solicitud = {
        tiendaId,
        operacionId,
        productoId: 'producto',
        varianteId: 'azul',
        cantidad: 2,
        lugarTipo: 'otro',
        lugarTexto: 'Feria Plaza Ñuñoa',
        fecha: '2025-03-12T15:00:00.000Z'
      };
      const resultado = await ventas('registrarVenta', { uid }, solicitud);
      assert.deepEqual(resultado, { operacionId, precioUnitario: 9000, cantidad: 2, total: 18000, historialRegistrado: true, actualizacionCatalogo: 'sin_publicacion' });
      assert.equal((await producto.collection('variantes').doc('azul').get()).data()?.stock, 3);
      assert.equal((await producto.collection('variantes').doc('roja').get()).data()?.stock, 8);
      const venta = (await tienda.collection('ventas').doc(operacionId).get()).data()!;
      assert.equal(venta.precioUnitario, 9000);
      assert.equal(venta.total, 18000);
      assert.equal(venta.lugarTexto, 'Feria Plaza Ñuñoa');
      assert.equal(venta.fecha.toDate().toISOString(), '2025-03-12T15:00:00.000Z');
      assert.deepEqual(venta.opcionesVariante, { color: 'Azul' });
      await ventas('registrarVenta', { uid }, solicitud);
      assert.equal((await producto.collection('variantes').doc('azul').get()).data()?.stock, 3);
      assert.equal((await tienda.collection('ventas').get()).size, 1);
    });

    await t.test('rechaza una cantidad mayor al stock sin modificar el inventario', async () => {
      await assert.rejects(registrar({ cantidad: 4 }), /No hay stock suficiente/);
      assert.equal((await producto.collection('variantes').doc('azul').get()).data()?.stock, 3);
      assert.equal((await tienda.collection('ventas').get()).size, 1);
    });

    await t.test('con el historial apagado actualiza stock y no crea una venta histórica', async () => {
      await ventas('configurarHistorialVentas', { uid }, { tiendaId, historialVentasActivo: false });
      assert.equal((await tienda.collection('configuracion').doc('general').get()).data()?.historialVentasActivo, false);
      const id = randomUUID();
      const resultado = await ventas('registrarVenta', { uid }, { tiendaId, operacionId: id, productoId: 'producto', varianteId: 'roja', cantidad: 3, lugarTipo: 'feria' }) as Record<string, unknown>;
      assert.equal(resultado.historialRegistrado, false);
      assert.equal((await producto.collection('variantes').doc('roja').get()).data()?.stock, 5);
      assert.equal((await tienda.collection('ventas').doc(id).get()).exists, false);
    });

    await t.test('requiere lugar cuando el historial está activo', async () => {
      await ventas('configurarHistorialVentas', { uid }, { tiendaId, historialVentasActivo: true });
      await assert.rejects(ventas('registrarVenta', { uid }, { tiendaId, operacionId: randomUUID(), productoId: 'producto', varianteId: 'azul', cantidad: 1, lugarTipo: 'otro', lugarTexto: ' ' }), /Indica una feria o un lugar/);
      assert.equal((await producto.collection('variantes').doc('azul').get()).data()?.stock, 3);
    });

    await t.test('guarda el nombre de una feria publicada como dato histórico', async () => {
      await db.doc('ferias/feria_ventas_prueba').set({ estado: 'publicada', nombre: 'Feria Amancay' });
      const operacionId = randomUUID();
      await ventas('registrarVenta', { uid }, { tiendaId, operacionId, productoId: 'producto', varianteId: 'azul', cantidad: 1, lugarTipo: 'feria', feriaId: 'feria_ventas_prueba', fecha: '2025-03-13T15:00:00.000Z' });
      const venta = (await tienda.collection('ventas').doc(operacionId).get()).data()!;
      assert.equal(venta.feriaId, 'feria_ventas_prueba');
      assert.equal(venta.feriaNombre, 'Feria Amancay');
      await db.doc('ferias/feria_ventas_prueba').delete();
    });

    await t.test('sincroniza la web al agotarse según la política de disponibilidad', async t => {
      await tienda.update({ estadoPublicacion: 'publicado' });
      await db.doc(`tiendasPublicas/${tiendaId}`).set({ estadoPublicacion: 'publicado', habilitada: true });
      for (const [regla, estadoEsperado] of [['mostrar_agotado', 'agotado'], ['pasar_a_pedido', 'a_pedido']] as const) {
        await t.test(`${regla} mantiene el producto con su estado`, async () => {
          const id = `producto_${regla}`;
          const ficha = tienda.collection('productos').doc(id);
          const publica = db.doc(`tiendasPublicas/${tiendaId}/productos/${id}`);
          await ficha.set({ nombre: id, estadoPublicacion: 'publicado', modalidad: 'regular', manejaStock: true, alAgotarse: regla, tieneVariantes: false, precioBase: 3000, stock: 1, piezaUnicaVendida: false, actualizadoEn: Timestamp.now() });
          await ficha.collection('publicacion').doc('vigente').set({ schema: 1, cicloCatalogo: 0, politica: { modalidad: 'regular', manejaStock: true, alAgotarse: regla }, producto: { nombre: id, tieneVariantes: false } });
          await publica.set({ nombre: id, estadoDisponibilidad: 'disponible' });
          await ventas('registrarVenta', { uid }, { tiendaId, operacionId: randomUUID(), productoId: id, cantidad: 1, lugarTipo: 'otro', lugarTexto: 'Tienda' });
          assert.equal((await publica.get()).data()?.estadoDisponibilidad, estadoEsperado);
        });
      }
      await t.test('ocultar retira el producto hasta que se reponga y publique', async () => {
        const id = 'producto_ocultar';
        const ficha = tienda.collection('productos').doc(id);
        const publica = db.doc(`tiendasPublicas/${tiendaId}/productos/${id}`);
        await ficha.set({ nombre: id, estadoPublicacion: 'publicado', modalidad: 'regular', manejaStock: true, alAgotarse: 'ocultar', tieneVariantes: false, precioBase: 3000, stock: 1, piezaUnicaVendida: false, actualizadoEn: Timestamp.now() });
        await ficha.collection('publicacion').doc('vigente').set({ schema: 1, cicloCatalogo: 0, politica: { modalidad: 'regular', manejaStock: true, alAgotarse: 'ocultar' }, producto: { nombre: id, tieneVariantes: false } });
        await publica.set({ nombre: id, estadoDisponibilidad: 'disponible' });
        await ventas('registrarVenta', { uid }, { tiendaId, operacionId: randomUUID(), productoId: id, cantidad: 1, lugarTipo: 'otro', lugarTexto: 'Tienda' });
        assert.equal((await publica.get()).exists, false);
      });
    });

    await t.test('al vender una pieza única elimina su ficha pública y la marca como vendida', async () => {
      const pieza = tienda.collection('productos').doc('pieza');
      const publica = db.doc(`tiendasPublicas/${tiendaId}/productos/pieza`);
      const publicShop = db.doc(`tiendasPublicas/${tiendaId}`);
      await tienda.update({ estadoPublicacion: 'publicado' });
      await publicShop.set({ estadoPublicacion: 'publicado', habilitada: true });
      await pieza.set({ nombre: 'Jarrón único', estadoPublicacion: 'publicado', modalidad: 'pieza_unica', manejaStock: true, tieneVariantes: false, stock: 1, precioBase: 12000, piezaUnicaVendida: false, actualizadoEn: Timestamp.now() });
      await pieza.collection('publicacion').doc('vigente').set({
        schema: 1,
        cicloCatalogo: 0,
        politica: { modalidad: 'pieza_unica', manejaStock: true, alAgotarse: 'mostrar_agotado' },
        producto: { nombre: 'Jarrón único', tieneVariantes: false }
      });
      await publica.set({ nombre: 'Jarrón único', estadoDisponibilidad: 'disponible' });
      const operacionId = randomUUID();
      await ventas('registrarVenta', { uid }, { tiendaId, operacionId, productoId: 'pieza', cantidad: 1, lugarTipo: 'otro', lugarTexto: 'Tienda', fecha: '2025-03-12T15:00:00.000Z' });
      assert.equal((await publica.get()).exists, false);
      assert.equal((await pieza.get()).data()?.stock, 0);
      assert.equal((await pieza.get()).data()?.piezaUnicaVendida, true);
      assert.equal((await tienda.collection('ventas').doc(operacionId).get()).exists, true);
    });
  } finally {
    await db.recursiveDelete(tienda);
    await db.recursiveDelete(db.doc(`tiendasPublicas/${tiendaId}`));
    await db.doc(`accesos/${uid}`).delete();
    await db.doc('ferias/feria_ventas_prueba').delete();
    await db.terminate();
  }
});
