import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { bucket } from '../src/config/emulador.ts';
import { crearVerificadorImagenes } from '../src/modules/imagenes.ts';

test('validación de objetos en Storage local', async t => {
  const verify = crearVerificadorImagenes(bucket);
  const prefix = `tiendas/test_imagenes/productos/test_producto/${randomUUID()}`;
  const paths: string[] = [];
  async function save(suffix: string, bytes: Buffer, contentType: string) {
    const path = prefix + suffix; paths.push(path);
    await bucket.file(path).save(bytes, {resumable:false,metadata:{contentType}}); return path;
  }
  const valid = (path: string) => verify(path,'test_imagenes','test_producto');
  try {
    const png = await save('.png',Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64'),'image/png');
    await t.test('admite PNG con metadatos y firma concordantes',async()=>{assert.equal(await valid(png),true);});
    await t.test('rechaza otra tienda',async()=>{assert.equal(await verify(png,'otra','test_producto'),false);});
    await t.test('rechaza otro producto',async()=>{assert.equal(await verify(png,'test_imagenes','otro'),false);});
    await t.test('rechaza URL externa y ruta arbitraria',async()=>{
      for (const p of ['https://example.com/foto.png','../foto.png',prefix+'.svg']) assert.equal(await valid(p),false);
    });
    await t.test('rechaza objeto inexistente',async()=>{assert.equal(await valid(prefix+'_missing.png'),false);});
    await t.test('rechaza texto disfrazado de PNG',async()=>{assert.equal(await valid(await save('_fake.png',Buffer.from('Esto no es una imagen'),'image/png')),false);});
    await t.test('rechaza MIME incorrecto',async()=>{assert.equal(await valid(await save('_mime.png',Buffer.from('89504e470d0a1a0a','hex'),'text/plain')),false);});
    await t.test('rechaza vacío',async()=>{assert.equal(await valid(await save('_empty.png',Buffer.alloc(0),'image/png')),false);});
    await t.test('rechaza más de 5 MiB',async()=>{assert.equal(await valid(await save('_large.png',Buffer.alloc(5*1024*1024+1),'image/png')),false);});
  } finally { for (const path of paths) await bucket.file(path).delete({ignoreNotFound:true}); }
});
