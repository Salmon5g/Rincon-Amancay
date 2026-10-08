import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, uploadBytes, getBytes, deleteObject, updateMetadata, listAll } from 'firebase/storage';
import { doc, updateDoc, serverTimestamp, setLogLevel } from 'firebase/firestore';
import { seed } from '../scripts/seed.mjs';

setLogLevel('silent');
const env = await initializeTestEnvironment({
  projectId: 'demo-rincon-amancay',
  firestore: { host: '127.0.0.1', port: 8080, rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
  storage: { host: '127.0.0.1', port: 9199, rules: await readFile(new URL('../storage.rules', import.meta.url), 'utf8') },
});
const owner = env.authenticatedContext('u_emprendedora_demo');
const stranger = env.authenticatedContext('u_otra_persona');
const visitor = env.unauthenticatedContext();
const bucket = 'gs://demo-rincon-amancay.appspot.com';
const prefix = 'tiendas/t_demo_01/productos/p_gorro_01/';
const path = prefix + randomUUID() + '.png';
const bytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');
const product = 'tiendasPrivadas/t_demo_01/productos/p_gorro_01';
const publicProduct = 'tiendasPublicas/t_demo_01/productos/p_gorro_01';
let count = 0;
const uploaded = [];
const file = (context, name = path) => ref(context.storage(bucket), name);
const upload = (context, name = path, data = bytes, metadata = { contentType: 'image/png' }) => uploadBytes(file(context, name), data, metadata);
async function check(name, fn) { await fn(); console.log(`OK ${++count}: ${name}`); }
async function serverChange(path, data) { await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(), path), data)); }
try {
  await seed();
  await check('visitante no sube', () => assertFails(upload(visitor)));
  await check('otra cuenta no sube', () => assertFails(upload(stranger)));
  await check('propietaria sube PNG', async () => { await assertSucceeds(upload(owner)); uploaded.push(path); });
  await check('propietaria lee borrador', () => assertSucceeds(getBytes(file(owner))));
  await check('visitante no lee imagen sin publicar', () => assertFails(getBytes(file(visitor))));
  await check('otra cuenta no lee borrador', () => assertFails(getBytes(file(stranger))));
  await check('no sobrescribe foto existente', () => assertFails(upload(owner)));
  await check('no modifica metadatos', () => assertFails(updateMetadata(file(owner), {contentType:'image/jpeg'})));
  await check('no elimina archivo directamente', () => assertFails(deleteObject(file(owner))));
  await check('no lista archivos', () => assertFails(listAll(ref(owner.storage(bucket), prefix))));
  await check('no sube a producto inexistente', () => assertFails(upload(owner, 'tiendas/t_demo_01/productos/inexistente/foto.png')));
  await check('no sube a otra tienda', () => assertFails(upload(owner, 'tiendas/otra/productos/p_gorro_01/foto.png')));
  await check('no acepta SVG', () => assertFails(upload(owner, prefix+'foto.svg', bytes, {contentType:'image/svg+xml'})));
  await check('no acepta MIME distinto de extensión', () => assertFails(upload(owner, prefix+'mime.png', bytes, {contentType:'image/jpeg'})));
  await check('no acepta archivo vacío', () => assertFails(upload(owner, prefix+'vacio.png', new Uint8Array())));
  await check('no acepta más de 5 MiB', () => assertFails(upload(owner, prefix+'grande.png', new Uint8Array(5*1024*1024+1))));
  await check('no acepta metadatos arbitrarios', () => assertFails(upload(owner, prefix+'meta.png', bytes, {contentType:'image/png',customMetadata:{propietario:'otro'}})));
  await check('propietaria selecciona imagen del borrador', () => assertSucceeds(updateDoc(doc(owner.firestore(), product), {imagenes:[path],actualizadoEn:serverTimestamp()})));
  await check('otra cuenta no selecciona imágenes', () => assertFails(updateDoc(doc(stranger.firestore(), product), {imagenes:[path],actualizadoEn:serverTimestamp()})));
  await check('selección no permite cambiar precio', () => assertFails(updateDoc(doc(owner.firestore(), product), {imagenes:[path],precioBase:1,actualizadoEn:serverTimestamp()})));
  await check('selección exige fecha servidor', () => assertFails(updateDoc(doc(owner.firestore(), product), {imagenes:[]})));
  await check('selección limita cinco imágenes', () => assertFails(updateDoc(doc(owner.firestore(), product), {imagenes:Array(6).fill(path),actualizadoEn:serverTimestamp()})));
  await serverChange(publicProduct, {imagenes:[path]});
  await check('visitante lee imagen vinculada a producto publicado', () => assertSucceeds(getBytes(file(visitor))));
  await serverChange('tiendasPublicas/t_demo_01', {habilitada:false});
  await check('deshabilitar tienda bloquea imagen pública', () => assertFails(getBytes(file(visitor))));
  await serverChange('tiendasPublicas/t_demo_01', {habilitada:true,mostrarPrecios:false});
  await check('catálogo oculto bloquea imagen pública', () => assertFails(getBytes(file(visitor))));
  await serverChange('tiendasPublicas/t_demo_01', {mostrarPrecios:true});
  await serverChange(publicProduct, {estadoPublicacion:'archivado'});
  await check('retirar producto bloquea imagen pública', () => assertFails(getBytes(file(visitor))));
  await serverChange('accesos/u_emprendedora_demo', {estado:'desactivado'});
  await check('acceso desactivado bloquea subida', () => assertFails(upload(owner,prefix+'inactivo.png')));
  await check('acceso desactivado bloquea borrador', () => assertFails(getBytes(file(owner))));
} finally {
  await env.withSecurityRulesDisabled(async ctx => { for (const path of uploaded) await deleteObject(file(ctx,path)); });
  await seed(); await env.cleanup();
}
console.log(`${count} comprobaciones de Storage y selección de imágenes correctas.`);
