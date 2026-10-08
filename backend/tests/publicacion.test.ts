import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { Timestamp } from 'firebase-admin/firestore';
import { db } from '../src/config/emulador.ts';
import { crearPublicacion, version } from '../src/modules/publicacion.ts';
import type { Accion, Solicitud } from '../src/modules/publicacion.ts';

const store = db.doc('tiendasPrivadas/t_demo_01');
const pub = db.doc('tiendasPublicas/t_demo_01');
const product = store.collection('productos').doc('p_gorro_01');
const pubProduct = pub.collection('productos').doc('p_gorro_01');
const identidad = { uid: 'u_emprendedora_demo' };
// Sustituto de Storage solo en pruebas: admite una ruta ficticia concreta.
const imagen = 'tiendas/t_demo_01/catalogo/gorro-demo.webp';
const ejecutar = crearPublicacion(db, async (path, tienda) => tienda === 't_demo_01' && path === imagen);
const fixture = JSON.parse(await readFile(new URL('../../datos-prueba/catalogo-local.json', import.meta.url), 'utf8'));
const recibos: string[] = [];
function convert(x: any): any {
  if (x && typeof x === 'object' && '$timestamp' in x) return Timestamp.fromDate(new Date(x.$timestamp));
  if (Array.isArray(x)) return x.map(convert);
  if (x && typeof x === 'object') return Object.fromEntries(Object.entries(x).map(([k,v]) => [k,convert(v)]));
  return x;
}
async function reset() {
  const batch = db.batch();
  for (const d of fixture.documentos) batch.set(db.doc(d.ruta), convert(d.datos));
  await batch.commit();
}
async function input(accion: Accion): Promise<Solicitud> {
  const operacionId = randomUUID(); recibos.push(operacionId);
  const source = accion.endsWith('Producto') ? product : store;
  return { tiendaId: 't_demo_01', ...(accion.endsWith('Producto') ? { productoId:'p_gorro_01' } : {}), operacionId, versionEsperada: version((await source.get()).data()!) };
}
const errorCode = (code: string) => (e: any) => e.code === code;
async function publicRead(path: string) {
  return fetch(`http://127.0.0.1:8080/v1/projects/demo-rincon-amancay/databases/(default)/documents/${path}`, { signal: AbortSignal.timeout(10000) });
}
test('operaciones locales de publicación', async t => {
  try {
    await t.test('publicar tienda proyecta solo campos autorizados', async () => {
      await reset(); await store.update({ secretoInterno:'no publicar' });
      await ejecutar('publicarTienda', identidad, await input('publicarTienda'));
      assert.equal((await store.get()).data()!.estadoPublicacion,'publicado');
      const data = (await pub.get()).data()!;
      assert.equal(data.habilitada,true); assert(!('secretoInterno' in data)); assert(!('propietarioUid' in data));
    });
    await t.test('administrador y otra cuenta no publican tienda ajena', async () => {
      await reset(); const req = await input('publicarTienda');
      for (const uid of ['u_admin_demo','u_otra']) await assert.rejects(ejecutar('publicarTienda',{uid},req),errorCode('sin-permiso'));
    });
    await t.test('cuenta desactivada no publica', async () => {
      await reset(); await db.doc('accesos/u_emprendedora_demo').update({estado:'desactivado'});
      await assert.rejects(ejecutar('publicarTienda',identidad,await input('publicarTienda')),errorCode('sin-permiso'));
    });
    await t.test('publicar producto sin foto falla sin efectos parciales', async () => {
      await reset(); const before=(await pubProduct.get()).data();
      const req=await input('publicarProducto');
      await assert.rejects(ejecutar('publicarProducto',identidad,req),errorCode('imagen-requerida'));
      assert.deepEqual((await pubProduct.get()).data(),before);
      assert.equal((await store.collection('operacionesPublicacion').doc(req.operacionId).get()).exists,false);
    });
    await t.test('producto proyecta variantes sin stock ni atributos desconocidos', async () => {
      await reset(); await product.update({imagenes:[imagen], 'atributosEspecificos.notaPrivada':'secreto'});
      await ejecutar('publicarProducto',identidad,await input('publicarProducto'));
      const data=(await pubProduct.get()).data()!;
      assert(!('notaPrivada' in data.atributosEspecificos)); assert(!('stock' in data));
      const v=(await pubProduct.collection('variantes').doc('v_azul_m').get()).data()!;
      assert.equal(v.precio,8000); assert(!('stock' in v));
    });
    await t.test('republicar elimina variantes inactivas antiguas', async () => {
      await reset(); await product.update({imagenes:[imagen]});
      await product.collection('variantes').doc('v_rojo_m').update({activa:false});
      await ejecutar('publicarProducto',identidad,await input('publicarProducto'));
      assert.equal((await pubProduct.collection('variantes').doc('v_rojo_m').get()).exists,false);
    });
    await t.test('producto a pedido sin variantes publica precioBase y no stock', async () => {
      await reset(); const simple=store.collection('productos').doc('p_alfajor_01');
      await simple.update({imagenes:[imagen]});
      const req=await input('publicarProducto');
      req.productoId='p_alfajor_01'; req.versionEsperada=version((await simple.get()).data()!);
      await ejecutar('publicarProducto',identidad,req);
      const result=(await pub.collection('productos').doc('p_alfajor_01').get()).data()!;
      assert.equal(result.precioBase,4500); assert.equal(result.estadoDisponibilidad,'a_pedido'); assert(!('stock' in result));
    });
    await t.test('stock negativo rechaza publicación completa', async () => {
      await reset(); await product.update({imagenes:[imagen]});
      await product.collection('variantes').doc('v_azul_m').update({stock:-1});
      const before=(await pubProduct.get()).data();
      await assert.rejects(ejecutar('publicarProducto',identidad,await input('publicarProducto')),errorCode('datos-invalidos'));
      assert.deepEqual((await pubProduct.get()).data(),before);
    });
    await t.test('agotado configurado oculto no deja variantes antiguas', async () => {
      await reset(); await product.update({imagenes:[imagen],alAgotarse:'ocultar'});
      await product.collection('variantes').doc('v_rojo_m').update({stock:0});
      await ejecutar('publicarProducto',identidad,await input('publicarProducto'));
      assert.equal((await pubProduct.collection('variantes').doc('v_rojo_m').get()).exists,false);
      assert.equal((await pubProduct.collection('variantes').doc('v_azul_m').get()).exists,true);
    });
    await t.test('no publica productos de una tienda retirada', async () => {
      await reset(); await ejecutar('retirarTienda',identidad,await input('retirarTienda'));
      await product.update({imagenes:[imagen]});
      await assert.rejects(ejecutar('publicarProducto',identidad,await input('publicarProducto')),errorCode('tienda-no-publicada'));
    });
    await t.test('imagen no autorizada no se publica', async () => {
      await reset(); await product.update({imagenes:['tiendas/ajena/secreto.webp']});
      await assert.rejects(ejecutar('publicarProducto',identidad,await input('publicarProducto')),errorCode('imagen-no-autorizada'));
    });
    await t.test('reintento devuelve mismo resultado sin volver a escribir', async () => {
      await reset(); const req=await input('publicarTienda');
      const first=await ejecutar('publicarTienda',identidad,req);
      const time=(await pub.get()).updateTime;
      assert.deepEqual(await ejecutar('publicarTienda',identidad,req),first);
      assert.deepEqual((await pub.get()).updateTime,time);
      await assert.rejects(ejecutar('retirarTienda',identidad,req),errorCode('id-reutilizado'));
    });
    await t.test('versión antigua se rechaza', async () => {
      await reset(); const req=await input('publicarTienda');
      await store.update({nombre:'Editado',actualizadoEn:Timestamp.now()});
      await assert.rejects(ejecutar('publicarTienda',identidad,req),errorCode('conflicto'));
    });
    await t.test('dos operaciones simultáneas: solo una gana', async () => {
      await reset(); const a=await input('publicarTienda'), b=await input('retirarTienda');
      const results=await Promise.allSettled([ejecutar('publicarTienda',identidad,a),ejecutar('retirarTienda',identidad,b)]);
      assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
      assert.equal((results.find(r=>r.status==='rejected') as PromiseRejectedResult).reason.code,'conflicto');
    });
    await t.test('retirar producto elimina público/variantes y conserva privado', async () => {
      await reset(); await ejecutar('retirarProducto',identidad,await input('retirarProducto'));
      assert.equal((await product.get()).data()!.estadoPublicacion,'archivado');
      assert.equal((await pubProduct.get()).exists,false);
      assert.equal((await pubProduct.collection('variantes').get()).size,0);
      assert.equal((await product.collection('variantes').get()).size,2);
      assert.equal((await publicRead(pubProduct.path+'/variantes/v_azul_m')).status,403);
    });
    await t.test('retirar y republicar tienda no resucita productos antiguos', async () => {
      await reset(); await ejecutar('retirarTienda',identidad,await input('retirarTienda'));
      assert.equal((await pub.get()).exists,false); assert.equal((await pub.collection('productos').get()).size,0);
      assert.equal((await store.collection('productos').get()).size,3);
      assert.equal((await publicRead(pubProduct.path+'/variantes/v_azul_m')).status,403);
      await ejecutar('publicarTienda',identidad,await input('publicarTienda'));
      assert.equal((await pub.collection('productos').get()).size,0);
    });
  } finally {
    await reset();
    const batch=db.batch(); for(const id of recibos) batch.delete(store.collection('operacionesPublicacion').doc(id));
    await batch.commit(); await db.terminate();
  }
});
