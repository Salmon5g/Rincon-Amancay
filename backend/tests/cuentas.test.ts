import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { auth, db } from '../src/config/emulador.ts';
import { crearCuentas } from '../src/modules/cuentas.ts';
import { crearApi } from '../src/http/api.ts';
import { crearPublicacion } from '../src/modules/publicacion.ts';

test('alta administrativa mediante API y emuladores', async t => {
  const prefix = 'alta_' + randomUUID();
  const adminUid = prefix + '_admin';
  const targetUid = prefix + '_target';
  const otherUid = prefix + '_other';
  const userIds = [adminUid, targetUid, otherUid];
  const stores = new Set<string>();
  const receipts = new Set<string>();
  const tokens: Record<string,string> = {};
  const server = crearApi(auth, crearPublicacion(db, async()=>false), crearCuentas(db,auth));
  server.listen(0,'127.0.0.1'); await once(server,'listening');
  const address = server.address(); assert(address && typeof address !== 'string');
  const endpoint = `http://127.0.0.1:${address.port}/api/v1/altaEmprendedora`;
  function input(uidDestino = targetUid) {
    const tiendaId = 't_' + randomUUID(); stores.add(tiendaId);
    const operacionId = randomUUID(); receipts.add(`${adminUid}_${operacionId}`); receipts.add(`${otherUid}_${operacionId}`);
    return {uidDestino,tiendaId,operacionId,nombreMostrar:'Nombre inicial',nombreTienda:'Tienda de prueba',descripcion:'Borrador de alta',sectorId:prefix,tipoEmprendimientoId:prefix,mostrarPrecios:true,historialVentasActivo:false,formaContacto:'formulario'};
  }
  async function post(body: unknown, uid = adminUid) {
    const response = await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json',...(tokens[uid]?{Authorization:`Bearer ${tokens[uid]}`}:{})},body:JSON.stringify(body)});
    return {status:response.status,data:await response.json()};
  }
  async function expect(body: unknown, status: number, code?: string, uid = adminUid) {
    const r=await post(body,uid); assert.equal(r.status,status,JSON.stringify(r.data)); if(code) assert.equal(r.data.error.code,code); return r.data;
  }
  try {
    for(const uid of userIds) {
      const email=`${uid}@example.test`; const password=randomUUID();
      await auth.createUser({uid,email,password});
      const r=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
      assert.equal(r.status,200); tokens[uid]=(await r.json()).idToken;
    }
    await db.doc(`accesos/${adminUid}`).set({estado:'activo',roles:['administrador']});
    await db.doc(`sectores/${prefix}`).set({activo:true});
    await db.doc(`tiposEmprendimiento/${prefix}`).set({activo:true});
    await t.test('sin sesión no se da de alta',()=>expect(input(),401,'no-autenticado','sin_token'));
    await t.test('cuenta sin rol no asigna permisos',()=>expect(input(),403,'sin-permiso',otherUid));
    await t.test('administrador desactivado no asigna',async()=>{
      await db.doc(`accesos/${adminUid}`).update({estado:'desactivado'});
      await expect(input(),403,'sin-permiso');
      await db.doc(`accesos/${adminUid}`).update({estado:'activo'});
    });
    await t.test('rechaza roles o contraseña enviados',async()=>{
      await expect({...input(),roles:['administrador']},400,'datos-invalidos');
      await expect({...input(),password:'no_aceptar'},400,'datos-invalidos');
    });
    await t.test('rechaza tipos inválidos y rutas',async()=>{
      await expect({...input(),mostrarPrecios:'true'},400,'datos-invalidos');
      await expect({...input(),tiendaId:'otra/ruta'},400,'datos-invalidos');
    });
    await t.test('no permite asignarse tienda',()=>expect(input(adminUid),403,'sin-permiso'));
    await t.test('identidad inexistente no crea documentos',async()=>{
      const d=input(prefix+'_missing'); await expect(d,404,'no-encontrado'); assert.equal((await db.doc(`tiendasPrivadas/${d.tiendaId}`).get()).exists,false);
    });
    await t.test('identidad Auth deshabilitada se rechaza',async()=>{
      await auth.updateUser(targetUid,{disabled:true}); await expect(input(),409,'conflicto'); await auth.updateUser(targetUid,{disabled:false});
    });
    await t.test('catálogo inactivo no produce alta parcial',async()=>{
      await db.doc(`sectores/${prefix}`).update({activo:false});
      const d=input(); await expect(d,400,'datos-invalidos');
      assert.equal((await db.doc(`accesos/${targetUid}`).get()).exists,false);
      assert.equal((await db.doc(`tiendasPrivadas/${d.tiendaId}`).get()).exists,false);
      await db.doc(`sectores/${prefix}`).update({activo:true});
    });
    await t.test('no reactiva accesos desactivados',async()=>{
      await db.doc(`accesos/${targetUid}`).set({estado:'desactivado',roles:['comprador']}); await expect(input(),409,'conflicto'); await db.doc(`accesos/${targetUid}`).delete();
    });
    await t.test('no reutiliza ruta con productos huérfanos',async()=>{
      const d=input(); const ref=db.doc(`tiendasPrivadas/${d.tiendaId}/productos/anterior`);
      await ref.set({nombre:'Datos anteriores'});
      try {await expect(d,409,'conflicto');} finally {await ref.delete();}
    });
    const first=input();
    await t.test('alta crea relaciones coherentes y borrador privado',async()=>{
      const r=await expect(first,200); assert.equal(r.datos.uid,targetUid);
      const a=(await db.doc(`accesos/${targetUid}`).get()).data()!;
      assert.deepEqual(a.roles,['emprendedora']); assert.equal(a.tiendaId,first.tiendaId);
      const store=(await db.doc(`tiendasPrivadas/${first.tiendaId}`).get()).data()!;
      assert.equal(store.propietarioUid,targetUid); assert.equal(store.estadoPublicacion,'borrador'); assert(store.creadoEn.toMillis()>0);
      assert.equal((await db.doc(`emprendedoras/${targetUid}`).get()).data()!.creadaPor,adminUid);
      assert.equal((await db.doc(`tiendasPrivadas/${first.tiendaId}/configuracion/general`).get()).data()!.mostrarPrecios,true);
      assert.equal((await db.doc(`tiendasPublicas/${first.tiendaId}`).get()).exists,false);
    });
    await t.test('reintento conserva fecha y resultado',async()=>{
      const before=(await db.doc(`tiendasPrivadas/${first.tiendaId}`).get()).updateTime!;
      await expect(first,200); assert(before.isEqual((await db.doc(`tiendasPrivadas/${first.tiendaId}`).get()).updateTime!));
    });
    await t.test('permisos recién asignados funcionan con el token de la propietaria',async()=>{
      const url=`http://127.0.0.1:8080/v1/projects/demo-rincon-amancay/databases/(default)/documents/tiendasPrivadas/${first.tiendaId}`;
      for(const [uid,expected] of [[targetUid,200],[otherUid,403],[adminUid,403]] as const) {
        const response=await fetch(url,{headers:{Authorization:`Bearer ${tokens[uid]}`}});
        assert.equal(response.status,expected,await response.text());
      }
    });
    await t.test('mismo ID con otros datos da conflicto',()=>expect({...first,nombreTienda:'Otro'},409,'id-reutilizado'));
    await t.test('reintento exige administrador todavía activo',async()=>{
      await db.doc(`accesos/${adminUid}`).update({estado:'desactivado'}); await expect(first,403,'sin-permiso'); await db.doc(`accesos/${adminUid}`).update({estado:'activo'});
    });
    await t.test('no asigna segunda tienda a la misma cuenta',()=>expect(input(),409,'conflicto'));
    await t.test('no toma tienda existente para otra cuenta',()=>expect({...input(otherUid),tiendaId:first.tiendaId},409,'conflicto'));
    await t.test('altas concurrentes: una gana y conserva perfil comprador',async()=>{
      await db.doc(`usuarios/${otherUid}`).set({nombreMostrar:'Nombre elegido',creadoEn:new Date()});
      await db.doc(`accesos/${otherUid}`).set({estado:'activo',roles:['comprador'],creadoEn:new Date()});
      const d1=input(otherUid),d2=input(otherUid);
      const results=await Promise.all([post(d1),post(d2)]);
      assert.deepEqual(results.map(r=>r.status).sort(),[200,409]);
      assert.equal((await db.doc(`usuarios/${otherUid}`).get()).data()!.nombreMostrar,'Nombre elegido');
      assert.deepEqual((await db.doc(`accesos/${otherUid}`).get()).data()!.roles,['comprador','emprendedora']);
      assert.equal(Number((await db.doc(`tiendasPrivadas/${d1.tiendaId}`).get()).exists)+Number((await db.doc(`tiendasPrivadas/${d2.tiendaId}`).get()).exists),1);
    });
  } finally {
    await new Promise<void>(resolve=>{server.close(()=>resolve());server.closeAllConnections();});
    const batch=db.batch();
    for(const uid of userIds) for(const collection of ['usuarios','accesos','emprendedoras']) batch.delete(db.doc(`${collection}/${uid}`));
    for(const id of stores) {batch.delete(db.doc(`tiendasPrivadas/${id}`));batch.delete(db.doc(`tiendasPrivadas/${id}/configuracion/general`));}
    for(const id of receipts) batch.delete(db.doc(`operacionesCuentas/${id}`));
    batch.delete(db.doc(`sectores/${prefix}`));batch.delete(db.doc(`tiposEmprendimiento/${prefix}`));await batch.commit();
    for(const uid of userIds) await auth.deleteUser(uid).catch(e=>{if(e.code!=='auth/user-not-found') throw e;});
    await db.terminate();
  }
});
