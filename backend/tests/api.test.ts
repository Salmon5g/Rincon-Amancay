import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { once } from 'node:events';
import { setTimeout as esperar } from 'node:timers/promises';
import { Timestamp } from 'firebase-admin/firestore';
import { auth, db, bucket } from '../src/config/emulador.ts';
import { crearApi } from '../src/http/api.ts';
import { crearPublicacion, version } from '../src/modules/publicacion.ts';
import { crearVerificadorImagenes } from '../src/modules/imagenes.ts';

const fixture = JSON.parse(await readFile(new URL('../../datos-prueba/catalogo-local.json', import.meta.url), 'utf8'));
function convert(x: any): any {
  if (x && typeof x === 'object' && '$timestamp' in x) return Timestamp.fromDate(new Date(x.$timestamp));
  if (Array.isArray(x)) return x.map(convert);
  if (x && typeof x === 'object') return Object.fromEntries(Object.entries(x).map(([k,v]) => [k,convert(v)]));
  return x;
}
async function reset() {
  const batch = db.batch(); for (const d of fixture.documentos) batch.set(db.doc(d.ruta), convert(d.datos)); await batch.commit();
}
test('API con Firebase Authentication y Firestore locales', async t => {
  const uid = `api_${randomUUID()}`;
  const email = `${uid}@example.test`;
  const password = randomUUID();
  const receiptIds: string[] = [];
  const store = db.doc('tiendasPrivadas/t_demo_01');
  const access = db.doc(`accesos/${uid}`);
  const imagePath = `tiendas/t_demo_01/productos/p_gorro_01/${randomUUID()}.png`;
  const server = crearApi(auth, crearPublicacion(db, crearVerificadorImagenes(bucket)));
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const address = server.address(); assert(address && typeof address !== 'string');
  const base = `http://127.0.0.1:${address.port}`;
  let token = '';
  async function input() {
    const id = randomUUID(); receiptIds.push(id);
    return { tiendaId:'t_demo_01', operacionId:id, versionEsperada:version((await store.get()).data()!) };
  }
  async function post(data: unknown, bearer: string | null = token, path = 'publicarTienda', headers: Record<string,string> = {}) {
    return fetch(`${base}/api/v1/${path}`, { method:'POST', headers:{'Content-Type':'application/json', ...(bearer === null ? {} : {Authorization:`Bearer ${bearer}`}), ...headers}, body:JSON.stringify(data) });
  }
  async function status(response: Response, expected: number, code?: string) {
    const data = await response.json(); assert.equal(response.status,expected,JSON.stringify(data));
    if (code) assert.equal(data.error.code,code);
    return data;
  }
  try {
    await reset();
    await auth.createUser({uid,email,password});
    const login = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({email,password,returnSecureToken:true}),
    });
    const loginData=await login.json(); assert.equal(login.status,200); assert.equal(loginData.localId,uid); token=loginData.idToken;
    await t.test('salud sin sesión',async()=>{ await status(await fetch(base+'/health'),200); });
    await t.test('sin token devuelve 401',async()=>{ await status(await post(await input(),null),401,'no-autenticado'); });
    await t.test('token inválido devuelve 401',async()=>{ await status(await post(await input(),'no-es-token'),401,'no-autenticado'); });
    await t.test('token de otro proyecto es rechazado',async()=>{
      const parts=token.split('.'); const payload=JSON.parse(Buffer.from(parts[1],'base64url').toString());
      payload.aud='otro-proyecto'; parts[1]=Buffer.from(JSON.stringify(payload)).toString('base64url');
      await status(await post(await input(),parts.join('.')),401,'no-autenticado');
    });
    await t.test('token vencido es rechazado',async()=>{
      const parts=token.split('.'); const payload=JSON.parse(Buffer.from(parts[1],'base64url').toString());
      payload.exp=1; parts[1]=Buffer.from(JSON.stringify(payload)).toString('base64url');
      await status(await post(await input(),parts.join('.')),401,'no-autenticado');
    });
    await t.test('cuenta autenticada sin acceso devuelve 403',async()=>{ await status(await post(await input()),403,'sin-permiso'); });
    await access.set({estado:'activo',roles:['emprendedora'],tiendaId:'t_demo_01'});
    await store.update({propietarioUid:uid});
    await t.test('uid enviado en cuerpo no suplanta identidad',async()=>{ await status(await post({...await input(),uid:'u_admin_demo'}),400,'datos-invalidos'); });
    await t.test('tipos inválidos rechazados sin 500',async()=>{ await status(await post({...await input(),tiendaId:123}),400,'datos-invalidos'); });
    await t.test('null rechazado',async()=>{ await status(await post(null),400,'datos-invalidos'); });
    await t.test('JSON malformado rechazado',async()=>{
      await status(await fetch(base+'/api/v1/publicarTienda',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:'{'}),400,'datos-invalidos');
    });
    await t.test('tipo de contenido inválido devuelve 415',async()=>{ await status(await post(await input(),token,'publicarTienda',{'Content-Type':'text/plain'}),415); });
    await t.test('cuerpo grande devuelve 413',async()=>{ await status(await post({relleno:'x'.repeat(17000)}),413); });
    await t.test('origen desconocido rechazado',async()=>{ await status(await post(await input(),token,'publicarTienda',{Origin:'https://ajeno.example'}),403,'origen-no-permitido'); });
    await t.test('preflight local autorizado',async()=>{
      const r=await fetch(base+'/api/v1/publicarTienda',{method:'OPTIONS',headers:{Origin:'http://localhost:3000'}});
      assert.equal(r.status,204); assert.equal(r.headers.get('Access-Control-Allow-Origin'),'http://localhost:3000');
    });
    await t.test('ruta y método incorrectos',async()=>{
      await status(await fetch(base+'/api/v1/publicarTienda'),405);
      await status(await fetch(base+'/inexistente'),404);
    });
    await t.test('propietaria publica y reintenta mediante HTTP',async()=>{
      const request=await input(); const first=await status(await post(request),200);
      assert.deepEqual(await status(await post(request),200),first);
      assert.equal((await db.doc('tiendasPublicas/t_demo_01').get()).data()!.habilitada,true);
    });
    await t.test('conflicto de versión devuelve 409',async()=>{ await status(await post({...await input(),versionEsperada:'1:0'}),409,'conflicto'); });
    await t.test('Auth deshabilitado devuelve 401',async()=>{
      await auth.updateUser(uid,{disabled:true}); await status(await post(await input()),401,'no-autenticado'); await auth.updateUser(uid,{disabled:false});
    });
    await t.test('acceso Firestore desactivado devuelve 403',async()=>{
      await access.update({estado:'desactivado'}); await status(await post(await input()),403,'sin-permiso'); await access.update({estado:'activo'});
    });
    await t.test('publicar producto rechaza imagen inexistente',async()=>{
      const p=store.collection('productos').doc('p_gorro_01'); await p.update({imagenes:[imagePath]});
      await status(await post({...await input(),productoId:'p_gorro_01',versionEsperada:version((await p.get()).data()!)},token,'publicarProducto'),422,'imagen-no-autorizada');
    });
    await t.test('publicar producto con imagen de Storage mediante HTTP',async()=>{
      await bucket.file(imagePath).save(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'), {resumable:false,metadata:{contentType:'image/png'}});
      const p=store.collection('productos').doc('p_gorro_01');
      const request={...await input(),productoId:'p_gorro_01',versionEsperada:version((await p.get()).data()!)};
      await status(await post(request,token,'publicarProducto'),200);
      await status(await post(request,token,'publicarProducto'),200);
      assert.deepEqual((await db.doc('tiendasPublicas/t_demo_01/productos/p_gorro_01').get()).data()!.imagenes,[imagePath]);
    });
    await t.test('retirar producto mediante HTTP',async()=>{
      const p=store.collection('productos').doc('p_gorro_01');
      await status(await post({...await input(),productoId:'p_gorro_01',versionEsperada:version((await p.get()).data()!)},token,'retirarProducto'),200);
      assert.equal((await db.doc('tiendasPublicas/t_demo_01/productos/p_gorro_01').get()).exists,false);
    });
    await t.test('retirar tienda mediante HTTP',async()=>{
      await status(await post(await input(),token,'retirarTienda'),200);
      assert.equal((await db.doc('tiendasPublicas/t_demo_01').get()).exists,false);
    });
    await t.test('revocar la sesión invalida su token anterior',async()=>{
      // Los tiempos de Auth tienen precisión de segundos.
      await esperar(1100); await auth.revokeRefreshTokens(uid);
      await status(await post(await input()),401,'no-autenticado');
    });
  } finally {
    await new Promise<void>(resolve=>{server.close(()=>resolve());server.closeAllConnections();});
    await reset(); await access.delete();
    const batch=db.batch(); for(const id of receiptIds) batch.delete(store.collection('operacionesPublicacion').doc(id)); await batch.commit();
    await auth.deleteUser(uid).catch(e=>{ if(e.code!=='auth/user-not-found') throw e; });
    await bucket.file(imagePath).delete({ignoreNotFound:true});
    await db.terminate();
  }
});
