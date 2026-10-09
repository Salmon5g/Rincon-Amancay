import sharp from 'sharp';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { bucket } from '../src/config/emulador.ts';
import { crearVerificadorImagenes, crearProcesadorImagenes } from '../src/modules/imagenes.ts';

test('validación de objetos en Storage local', async t => {
  const verify = crearVerificadorImagenes(bucket);
  const procesar = crearProcesadorImagenes(bucket);
  const prefix = `tiendas/test_imagenes/productos/test_producto/${randomUUID()}`;
  const paths: string[] = [];
  async function save(suffix: string, bytes: Buffer, contentType: string) {
    const path = prefix + suffix; paths.push(path);
    await bucket.file(path).save(bytes, {resumable:false,metadata:{contentType}}); return path;
  }
  const valid = (path: string) => verify(path,'test_imagenes','test_producto');
  try {
    const png = await save('.png',Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAACXBIWXMAAAPoAAAD6AG1e1JrAAAAEklEQVQImWOYFrdtWtw2BggFAC4mBqmTvDXaAAAAAElFTkSuQmCC','base64'),'image/png');
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
    await t.test('rechaza cabecera válida con contenido truncado',async()=>{
      assert.equal(await valid(await save('_truncado.png',Buffer.from('89504e470d0a1a0a','hex'),'image/png')),false);
      const jpeg=await sharp({create:{width:80,height:80,channels:3,background:'#8f8f00'}}).jpeg().toBuffer();
      assert.equal(await valid(await save('_truncado.jpg',jpeg.subarray(0,Math.floor(jpeg.length/2)),'image/jpeg')),false);
    });
    await t.test('rechaza formato real distinto aunque se cambien extensión y MIME',async()=>{
      const jpeg=await sharp({create:{width:2,height:2,channels:3,background:'red'}}).jpeg().toBuffer();
      assert.equal(await valid(await save('_renombrado.png',jpeg,'image/png')),false);
    });
    await t.test('rechaza dimensiones y píxeles excesivos',async()=>{
      const ancho=await sharp({create:{width:10001,height:1,channels:3,background:'red'}}).png().toBuffer();
      assert.equal(await valid(await save('_ancho.png',ancho,'image/png')),false);
      const grande=await sharp({create:{width:5000,height:5000,channels:3,background:'red'}}).png().toBuffer();
      assert.equal(await valid(await save('_pixeles.png',grande,'image/png')),false);
    });
    await t.test('rechaza WebP animado',async()=>{
      const pixels=Buffer.from([...Array(4).fill([255,0,0]).flat(),...Array(4).fill([0,0,255]).flat()]);
      const bytes=await sharp(pixels,{raw:{width:2,height:4,pageHeight:2,channels:3}}).webp({loop:0,delay:[100,100]}).toBuffer();
      assert.equal((await sharp(bytes).metadata()).pages,2);
      assert.equal(await valid(await save('_animado.webp',bytes,'image/webp')),false);
    });
    await t.test('orienta, reduce y elimina metadatos; genera dos copias inmutables',async()=>{
      const original=await sharp({create:{width:400,height:2000,channels:3,background:'#246875'}}).withMetadata({orientation:6}).withExifMerge({IFD0:{Artist:'Dato privado'}}).jpeg().toBuffer();
      assert((await sharp(original).metadata()).exif);
      const input=await save('_orientada.jpg',original,'image/jpeg');
      const output=await procesar(input,'test_imagenes','test_producto');assert(output);paths.push(output.imagen,output.miniatura);
      const [main]=await bucket.file(output.imagen).download(),[small]=await bucket.file(output.miniatura).download();
      const m=await sharp(main).metadata(),v=await sharp(small).metadata();
      assert.equal(m.format,'webp');assert.equal(m.width,1600);assert.equal(m.height,320);
      assert.equal(v.width,320);assert.equal(v.height,64);
      for(const data of [m,v])for(const field of ['exif','icc','xmp','iptc','orientation'] as const)assert.equal(data[field],undefined);
      const [before]=await bucket.file(output.imagen).getMetadata();
      assert.equal(before.metadata?.firebaseStorageDownloadTokens,undefined);
      assert.deepEqual(await procesar(input,'test_imagenes','test_producto'),output);
      const [after]=await bucket.file(output.imagen).getMetadata();assert.equal(after.generation,before.generation);
    });
  } finally { for (const path of paths) await bucket.file(path).delete({ignoreNotFound:true}); }
});
