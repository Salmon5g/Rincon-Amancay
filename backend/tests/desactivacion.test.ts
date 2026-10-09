import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { Timestamp } from 'firebase-admin/firestore';
import { auth, db, bucket } from '../src/config/emulador.ts';
import { crearApi } from '../src/http/api.ts';
import { crearPublicacion, version } from '../src/modules/publicacion.ts';
import { crearDesactivacion } from '../src/modules/desactivacion.ts';

test('desactivación coordinada, permisos y recuperación',async t=>{
  const adminUid='admin_'+randomUUID(),uid='empr_'+randomUUID(),tiendaId='t_'+randomUUID();
  const access=db.doc(`accesos/${uid}`),actor=db.doc(`accesos/${adminUid}`);
  const store=db.doc(`tiendasPrivadas/${tiendaId}`),pub=db.doc(`tiendasPublicas/${tiendaId}`);
  const imagePath=`tiendas/${tiendaId}/productos/p/foto.png`;
  const imageUrl=`http://127.0.0.1:9199/v0/b/demo-rincon-amancay.appspot.com/o/${encodeURIComponent(imagePath)}?alt=media`;
  const receipts=new Set<string>();const tokens:Record<string,string>={};
  let failure:'none'|'update'|'revoke'='none';
  const adminAuth={
    updateUser:async(...args:Parameters<typeof auth.updateUser>)=>{if(failure==='update')throw new Error('simulación de interrupción Auth');return auth.updateUser(...args);},
    revokeRefreshTokens:async(...args:Parameters<typeof auth.revokeRefreshTokens>)=>{if(failure==='revoke')throw new Error('simulación de interrupción revocación');return auth.revokeRefreshTokens(...args);},
  };
  const publication=crearPublicacion(db,async()=>true);
  const disable=crearDesactivacion(db,adminAuth);
  const server=crearApi(auth,publication,undefined,undefined,undefined,disable);
  server.listen(0,'127.0.0.1');await once(server,'listening');const address=server.address();assert(address&&typeof address!=='string');
  const base=`http://127.0.0.1:${address.port}/api/v1/`;
  async function reset(){
    failure='none';await auth.updateUser(uid,{disabled:false});
    await access.set({roles:['emprendedora'],estado:'activo',tiendaId,actualizadoEn:Timestamp.now()});
    await pub.set({nombre:'Tienda visible',habilitada:true,mostrarPrecios:true,estadoPublicacion:'publicado'});
  }
  async function input(extra:Record<string,unknown>={}){
    const operacionId=randomUUID();receipts.add(`${adminUid}_${operacionId}`);
    return {uidDestino:uid,operacionId,versionEsperada:version((await access.get()).data()!),motivo:'Suspensión de prueba',...extra};
  }
  async function post(body:unknown,expected:number,who=adminUid){
    const r=await fetch(base+'desactivarEmprendedora',{method:'POST',headers:{'Content-Type':'application/json',...(tokens[who]?{Authorization:`Bearer ${tokens[who]}`}:{})},body:JSON.stringify(body)});
    const data=await r.json();assert.equal(r.status,expected,JSON.stringify(data));return data;
  }
  async function read(path:string,who='visitante',method='GET',body?:unknown){
    const r=await fetch(`http://127.0.0.1:8080/v1/projects/demo-rincon-amancay/databases/(default)/documents/${path}`,{method,headers:{'Content-Type':'application/json',...(tokens[who]?{Authorization:`Bearer ${tokens[who]}`}:{})},...(body?{body:JSON.stringify(body)}:{})});return r.status;
  }
  try{
    for(const who of [uid,adminUid]){
      const email=who+'@example.test',password=randomUUID();await auth.createUser({uid:who,email,password});
      const r=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});assert.equal(r.status,200);tokens[who]=(await r.json()).idToken;
    }
    await actor.set({roles:['administrador'],estado:'activo',actualizadoEn:Timestamp.now()});
    await store.set({propietarioUid:uid,nombre:'Tienda privada',descripcion:'Descripción',estadoPublicacion:'publicado',sectorId:tiendaId,tipoEmprendimientoId:tiendaId,actualizadoEn:Timestamp.now()});
    await store.collection('configuracion').doc('general').set({mostrarPrecios:true});
    await db.doc(`sectores/${tiendaId}`).set({activo:true});await db.doc(`tiposEmprendimiento/${tiendaId}`).set({activo:true});
    await db.doc(`emprendedoras/${uid}`).set({tiendaId});await store.collection('productos').doc('p').set({nombre:'Producto privado'});
    await pub.collection('productos').doc('p').set({estadoPublicacion:'publicado',imagenes:[imagePath]});
    await pub.collection('productos').doc('p').collection('variantes').doc('v').set({activa:true});
    await bucket.file(imagePath).save(Buffer.from('89504e470d0a1a0a','hex'),{resumable:false,metadata:{contentType:'image/png'}});
    await reset();
    await t.test('sin sesión y sin rol no desactivan',async()=>{await post(await input(),401,'sin-token');await post(await input(),403,uid);});
    await t.test('no permite desactivarse ni tocar administradores',async()=>{
      await post(await input({uidDestino:adminUid}),403);await access.update({roles:['emprendedora','administrador']});await post(await input(),403);await reset();
    });
    await t.test('administrador desactivado no opera',async()=>{await actor.update({estado:'desactivado'});await post(await input(),403);await actor.update({estado:'activo'});});
    await t.test('rechaza campos extra, motivo vacío y versión antigua',async()=>{await post(await input({estado:'activo'}),400);await post(await input({motivo:''}),400);await post(await input({versionEsperada:'1:0'}),409);});
    await t.test('propiedad inconsistente no bloquea otras tiendas',async()=>{await store.update({propietarioUid:'otra'});await post(await input(),409);assert.equal((await access.get()).data()!.estado,'activo');await store.update({propietarioUid:uid});});
    const pending=await input();
    await t.test('fallo de Auth mantiene bloqueo y comprobante pendiente',async()=>{
      assert.equal(await read(pub.path),200);assert.equal((await fetch(imageUrl)).status,200);
      failure='update';await post(pending,503);assert.equal((await access.get()).data()!.estado,'desactivado');assert.equal((await pub.get()).data()!.habilitada,false);
      assert.equal((await auth.getUser(uid)).disabled,false);
      assert.equal((await db.doc(`desactivaciones/${adminUid}_${pending.operacionId}`).get()).data()!.estado,'pendienteAuth');
    });
    await t.test('token anterior no lee ni escribe tienda; público e imágenes ocultos',async()=>{
      assert.equal(await read(store.path,uid),403);assert.equal(await read(`${store.path}/productos/p`,uid),403);
      assert.equal(await read(store.path,uid,'PATCH',{fields:{nombre:{stringValue:'Intento'}}}),403);
      for(const path of [pub.path,`${pub.path}/productos/p`,`${pub.path}/productos/p/variantes/v`])assert.equal(await read(path),403);
      assert.equal((await fetch(imageUrl)).status,403);
      assert.equal((await store.collection('productos').doc('p').get()).exists,true);assert.equal((await bucket.file(imagePath).exists())[0],true);
    });
    await t.test('reintento completa deshabilitación y revocación',async()=>{
      failure='none';const r=await post(pending,200);assert.equal(r.datos.estado,'completada');assert.equal(r.datos.autenticacion,'deshabilitada');assert.equal((await auth.getUser(uid)).disabled,true);
      await assert.rejects(auth.verifyIdToken(tokens[uid],true));
    });
    await t.test('completada no vuelve a ejecutar Auth ni cambia fecha',async()=>{
      const ref=db.doc(`desactivaciones/${adminUid}_${pending.operacionId}`),before=(await ref.get()).updateTime!;
      failure='update';await post(pending,200);assert(before.isEqual((await ref.get()).updateTime!));
      await post({...pending,motivo:'Distinto'},409);failure='none';
    });
    await t.test('reintento comprueba permiso administrativo vigente',async()=>{await actor.update({estado:'desactivado'});await post(pending,403);await actor.update({estado:'activo'});});
    await t.test('comprobante solo lo leen administradores y no se edita por cliente',async()=>{
      const path=`desactivaciones/${adminUid}_${pending.operacionId}`;
      assert.equal(await read(path,adminUid),200);assert.equal(await read(path,uid),403);assert.equal(await read(path),403);
      assert.equal(await read(path,adminUid,'DELETE'),403);assert.equal(await read(path,adminUid,'PATCH',{fields:{estado:{stringValue:'completada'}}}),403);
    });
    await t.test('fallo de revocación se recupera sin reabrir catálogo',async()=>{
      await reset();const d=await input();failure='revoke';await post(d,503);assert.equal((await auth.getUser(uid)).disabled,true);assert.equal((await pub.get()).data()!.habilitada,false);
      failure='none';await post(d,200);
    });
    await t.test('publicación concurrente no vuelve a habilitar cuenta desactivada',async()=>{
      await reset();const d=await input();const req={tiendaId,operacionId:randomUUID(),versionEsperada:version((await store.get()).data()!)};
      const results=await Promise.allSettled([publication('publicarTienda',{uid},req),post(d,200)]);
      assert.equal(results[1].status,'fulfilled');
      assert.equal((await access.get()).data()!.estado,'desactivado');assert.equal((await pub.get()).data()!.habilitada,false);
    });
    await t.test('tienda sin publicación no crea ficha pública',async()=>{await reset();await pub.delete();await post(await input(),200);assert.equal((await pub.get()).exists,false);});
    await t.test('identidad Auth ausente termina con datos bloqueados',async()=>{await reset();await auth.deleteUser(uid);const r=await post(await input(),200);assert.equal(r.datos.autenticacion,'ausente');assert.equal((await pub.get()).data()!.habilitada,false);});
  }finally{
    await new Promise<void>(resolve=>{server.close(()=>resolve());server.closeAllConnections();});
    await db.recursiveDelete(store);await db.recursiveDelete(pub);await db.doc(`emprendedoras/${uid}`).delete();
    await db.doc(`sectores/${tiendaId}`).delete();await db.doc(`tiposEmprendimiento/${tiendaId}`).delete();
    for(const receipt of receipts)await db.doc(`desactivaciones/${receipt}`).delete();
    for(const who of [uid,adminUid]){await db.doc(`accesos/${who}`).delete();await auth.deleteUser(who).catch(e=>{if(e.code!=='auth/user-not-found')throw e;});}
    await bucket.file(imagePath).delete({ignoreNotFound:true});await db.terminate();
  }
});
