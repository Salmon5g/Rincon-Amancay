import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { Timestamp } from 'firebase-admin/firestore';
import { crearValidadorSesion } from '../src/modules/sesiones.ts';
import { auth, db, bucket } from '../src/config/emulador.ts';
import { crearCicloCuentas } from '../src/modules/ciclo-cuentas.ts';
import { crearDesactivacion } from '../src/modules/desactivacion.ts';
import { crearApi } from '../src/http/api.ts';
import { crearPublicacion, version } from '../src/modules/publicacion.ts';

test('ciclo de cuentas local con Auth, API y Firestore',async t=>{
  const prefix='ciclo_'+randomUUID(), adminUid=prefix+'_admin', buyer=prefix+'_buyer', other=prefix+'_other';
  const uids=[adminUid,buyer,other],tokens:Record<string,string>={},claves:Record<string,string>={};
  const invites=new Set<string>(),stores=new Set<string>(),receipts=new Set<string>();
  const ciclo=crearCicloCuentas(db,auth), desactivar=crearDesactivacion(db,auth);
  const server=crearApi(auth,crearPublicacion(db,async()=>false),undefined,undefined,undefined,desactivar,ciclo,crearValidadorSesion(db));
  server.listen(0,'127.0.0.1');await once(server,'listening');
  const address=server.address();assert(address && typeof address!=='string');const port=address.port;
  async function authRest(action:string,body:unknown) {
    const r=await fetch(`http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:${action}?key=demo-key`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
    assert.equal(r.status,200,`Auth ${action}: ${r.status}`);return r.json();
  }
  async function login(uid:string) {tokens[uid]=(await authRest('signInWithPassword',{email:`${uid}@example.test`,password:claves[uid],returnSecureToken:true})).idToken;}
  async function post(action:string,body:unknown,uid=adminUid,expected=200) {
    const r=await fetch(`http://127.0.0.1:${port}/api/v1/${action}`,{method:'POST',headers:{'Content-Type':'application/json',...(tokens[uid]?{Authorization:`Bearer ${tokens[uid]}`}:{})},body:JSON.stringify(body)});
    const data=await r.json();assert.equal(r.status,expected,r.status===200 ? 'Respuesta inesperada' : JSON.stringify(data));return data.datos;
  }
  function invitation(correo=`${buyer}@example.test`) {
    return {operacionId:randomUUID(),correo,nombreMostrar:'Compradora',nombreTienda:'Tejidos de prueba',descripcion:'Tienda local',sectorId:prefix,tipoEmprendimientoId:prefix,mostrarPrecios:true,historialVentasActivo:false,formaContacto:'formulario'};
  }
  async function invite(body=invitation()) {
    const r=await post('invitarEmprendedora',body);invites.add(r.invitacionId);stores.add(r.tiendaId);receipts.add(`operacionesCuentas/${adminUid}_${body.operacionId}`);return r;
  }
  async function transition(collection:string,uid=buyer) {
    const operacionId=randomUUID();receipts.add(`${collection}/${adminUid}_${operacionId}`);
    return {uidDestino:uid,operacionId,versionEsperada:version((await db.doc(`accesos/${uid}`).get()).data()!),motivo:'Prueba administrativa'};
  }
  async function codeFor(uid:string,type:string) {
    const r=await fetch('http://127.0.0.1:9099/emulator/v1/projects/demo-rincon-amancay/oobCodes');assert.equal(r.status,200);
    const c=(await r.json()).oobCodes.filter((c:{email:string;requestType:string})=>c.email===`${uid}@example.test` && c.requestType===type).at(-1);
    assert(c);return c.oobCode;
  }
  try {
    for(const uid of uids) {claves[uid]=randomUUID();await auth.createUser({uid,email:`${uid}@example.test`,password:claves[uid],emailVerified:uid!==buyer});await login(uid);}
    await db.doc(`accesos/${adminUid}`).set({estado:'activo',roles:['administrador'],actualizadoEn:Timestamp.now()});
    await db.doc(`sectores/${prefix}`).set({activo:true});await db.doc(`tiposEmprendimiento/${prefix}`).set({activo:true});
    await t.test('rutas requieren sesión; no permiten inyectar roles',async()=>{
      await post('registrarComprador',{nombreMostrar:'Persona'},'sin_token',401);
      await post('registrarComprador',{nombreMostrar:'Persona',roles:['administrador']},buyer,400);
      await post('invitarEmprendedora',invitation(),other,403);
    });
    await t.test('sin correo verificado no se registra ni consulta invitaciones',async()=>{
      await post('registrarComprador',{nombreMostrar:'Persona'},buyer,403);
      await post('consultarInvitacion',{invitacionId:'desconocida'},buyer,403);
      assert.equal((await db.doc(`accesos/${buyer}`).get()).exists,false);
    });
    await t.test('verificación Auth mediante código local habilita registro idempotente',async()=>{
      await authRest('sendOobCode',{requestType:'VERIFY_EMAIL',idToken:tokens[buyer]});
      const code=await codeFor(buyer,'VERIFY_EMAIL');await authRest('update',{oobCode:code});await login(buyer);
      await post('registrarComprador',{nombreMostrar:'Nombre elegido'},buyer);
      await post('registrarComprador',{nombreMostrar:'No sobrescribir'},buyer);
      assert.equal((await db.doc(`usuarios/${buyer}`).get()).data()!.nombreMostrar,'Nombre elegido');
      assert.deepEqual((await db.doc(`accesos/${buyer}`).get()).data()!.roles,['comprador']);
    });
    await t.test('registro no reactiva cuentas ni cambia roles existentes',async()=>{
      await post('registrarComprador',{nombreMostrar:'Admin'},adminUid,409);
      await db.doc(`accesos/${buyer}`).update({estado:'desactivado'});
      await post('registrarComprador',{nombreMostrar:'Persona'},buyer,409);
      await db.doc(`accesos/${buyer}`).update({estado:'activo'});
    });
    const d=invitation();const accepted=await invite(d);
    await t.test('invitación reintentada no duplica tienda; ID reutilizado se rechaza',async()=>{
      assert.equal((await post('invitarEmprendedora',d)).invitacionId,accepted.invitacionId);
      await post('invitarEmprendedora',{...d,nombreTienda:'Distinta'},adminUid,409);
      assert.equal((await db.doc(`tiendasPrivadas/${accepted.tiendaId}`).get()).exists,false);
    });
    await t.test('solo el correo verificado destinatario puede consultar o aceptar',async()=>{
      await post('consultarInvitacion',{invitacionId:accepted.invitacionId},other,403);
      await post('aceptarInvitacion',{invitacionId:accepted.invitacionId},other,403);
      assert.equal((await post('consultarInvitacion',{invitacionId:accepted.invitacionId},buyer)).nombreTienda,d.nombreTienda);
    });
    await t.test('invitación cancelada no se acepta y cancelar es idempotente',async()=>{
      const r=await invite();await post('cancelarInvitacion',{invitacionId:r.invitacionId});await post('cancelarInvitacion',{invitacionId:r.invitacionId});
      await post('aceptarInvitacion',{invitacionId:r.invitacionId},buyer,409);
    });
    await t.test('invitación vencida no crea relaciones',async()=>{
      const r=await invite();await db.doc(`invitaciones/${r.invitacionId}`).update({venceEn:Timestamp.fromMillis(1)});
      await post('aceptarInvitacion',{invitacionId:r.invitacionId},buyer,409);
      assert.equal((await post('consultarInvitacion',{invitacionId:r.invitacionId},buyer)).estado,'vencida');
    });
    await t.test('administrador desactivado invalida aceptación pendiente',async()=>{
      await db.doc(`accesos/${adminUid}`).update({estado:'desactivado'});
      await post('aceptarInvitacion',{invitacionId:accepted.invitacionId},buyer,403);
      await db.doc(`accesos/${adminUid}`).update({estado:'activo'});
    });
    await t.test('dos aceptaciones concurrentes crean una sola asignación y conservan comprador',async()=>{
      const input={invitacionId:accepted.invitacionId};
      const [a,b]=await Promise.all([post('aceptarInvitacion',input,buyer),post('aceptarInvitacion',input,buyer)]);assert.deepEqual(a,b);
      assert.deepEqual((await db.doc(`accesos/${buyer}`).get()).data()!.roles,['comprador','emprendedora']);
      assert.equal((await db.doc(`invitaciones/${accepted.invitacionId}`).get()).data()!.estado,'aceptada');
      assert.equal((await db.doc(`tiendasPrivadas/${accepted.tiendaId}`).get()).data()!.estadoPublicacion,'borrador');
      assert.equal((await db.doc(`usuarios/${buyer}`).get()).data()!.nombreMostrar,'Nombre elegido');
      await post('cancelarInvitacion',input,adminUid,409);
    });
    await t.test('invitaciones solo se leen por admin y no se editan desde SDK',async()=>{
      const url=`http://127.0.0.1:8080/v1/projects/demo-rincon-amancay/databases/(default)/documents/invitaciones/${accepted.invitacionId}`;
      for(const uid of [buyer,other,adminUid]) assert.equal((await fetch(url,{headers:{Authorization:`Bearer ${tokens[uid]}`}})).status,uid===adminUid ? 200 : 403);
      assert.equal((await fetch(url,{method:'PATCH',headers:{Authorization:`Bearer ${tokens[buyer]}`,'Content-Type':'application/json'},body:JSON.stringify({fields:{estado:{stringValue:'aceptada'}}})})).status,403);
    });
    await db.doc(`tiendasPublicas/${accepted.tiendaId}`).set({estadoPublicacion:'publicado',habilitada:true});
    // Auth expresa la revocación en segundos; separar la sesión anterior.
    await new Promise(resolve=>setTimeout(resolve,1100));
    const off=await transition('desactivaciones');
    await t.test('desactivación pendiente bloquea reactivación hasta completarse',async()=>{
      const fallar=crearDesactivacion(db,{updateUser:async()=>{throw new Error('fallo simulado');},revokeRefreshTokens:auth.revokeRefreshTokens.bind(auth)});
      await assert.rejects(fallar({uid:adminUid},off),{code:'pendiente'});
      await post('reactivarEmprendedora',await transition('reactivaciones'),adminUid,409);
      await post('desactivarEmprendedora',off);
    });
    await t.test('reactivación exige administrador, versión vigente y no admite roles',async()=>{
      const d=await transition('reactivaciones');await post('reactivarEmprendedora',d,other,403);
      await post('reactivarEmprendedora',{...d,versionEsperada:'1:0'},adminUid,409);
      await post('reactivarEmprendedora',{...d,roles:['administrador']},adminUid,400);
    });
    const on=await transition('reactivaciones');
    await t.test('fallo al habilitar Auth conserva bloqueo; otra desactivación no interfiere',async()=>{
      const fake=Object.create(auth);fake.revokeRefreshTokens=auth.revokeRefreshTokens.bind(auth);fake.updateUser=async()=>{throw new Error('fallo simulado');};
      await assert.rejects(crearCicloCuentas(db,fake)('reactivarEmprendedora',{uid:adminUid},on),{code:'pendiente'});
      assert.equal((await db.doc(`accesos/${buyer}`).get()).data()!.estado,'desactivado');
      assert.equal((await auth.getUser(buyer)).disabled,true);
      await post('desactivarEmprendedora',await transition('desactivaciones'),adminUid,409);
    });
    await t.test('reintento reactiva sin publicar; sesión vieja sigue revocada',async()=>{
      await post('reactivarEmprendedora',on);assert.equal((await auth.getUser(buyer)).disabled,false);
      assert.equal((await db.doc(`accesos/${buyer}`).get()).data()!.estado,'activo');
      assert.equal((await db.doc(`tiendasPublicas/${accepted.tiendaId}`).get()).data()!.habilitada,false);
      await post('registrarComprador',{nombreMostrar:'Persona'},buyer,401);
      const access=(await db.doc(`accesos/${buyer}`).get()).data()!;
      assert.equal(await crearValidadorSesion(db)(buyer,access.sesionesRevocadasHasta),false);
      assert.equal(await crearValidadorSesion(db)(buyer,access.sesionesRevocadasHasta+1),true);
      const url=`http://127.0.0.1:8080/v1/projects/demo-rincon-amancay/databases/(default)/documents/tiendasPrivadas/${accepted.tiendaId}`;
      assert.equal((await fetch(url,{headers:{Authorization:`Bearer ${tokens[buyer]}`}})).status,403);
      const image=bucket.file(`tiendas/${accepted.tiendaId}/productos/p/test.png`);
      await image.save(Buffer.from('fixture privado de reglas'),{contentType:'image/png'});
      try {
        const imageUrl=`http://127.0.0.1:9199/v0/b/${bucket.name}/o/${encodeURIComponent(image.name)}?alt=media`;
        assert.equal((await fetch(imageUrl,{headers:{Authorization:`Bearer ${tokens[buyer]}`}})).status,403);
        // auth_time tiene resolución de segundos; una sesión debe ser posterior al corte.
        await new Promise(resolve=>setTimeout(resolve,1100));await login(buyer);
        assert.equal((await fetch(url,{headers:{Authorization:`Bearer ${tokens[buyer]}`}})).status,200);
        assert.equal((await fetch(imageUrl,{headers:{Authorization:`Bearer ${tokens[buyer]}`}})).status,200);
      } finally {await image.delete();}
      const first=(await db.doc(`accesos/${buyer}`).get()).updateTime!;await post('reactivarEmprendedora',on);
      assert(first.isEqual((await db.doc(`accesos/${buyer}`).get()).updateTime!));
      await post('reactivarEmprendedora',{...on,motivo:'Otro'},adminUid,409);
    });
    await t.test('reintento histórico de desactivación no vuelve a deshabilitar',async()=>{
      await post('desactivarEmprendedora',off);assert.equal((await auth.getUser(buyer)).disabled,false);
    });
    await t.test('operaciones opuestas concurrentes se serializan sin separar Auth y acceso',async()=>{
      await post('desactivarEmprendedora',await transition('desactivaciones'));
      const on2=await transition('reactivaciones');
      let listo!:()=>void, liberar!:()=>void;
      const iniciado=new Promise<void>(resolve=>{listo=resolve;});const puerta=new Promise<void>(resolve=>{liberar=resolve;});
      const fake=Object.create(auth);fake.revokeRefreshTokens=auth.revokeRefreshTokens.bind(auth);
      fake.updateUser=async(...args: Parameters<typeof auth.updateUser>)=>{listo();await puerta;return auth.updateUser(...args);};
      const reactivar=crearCicloCuentas(db,fake)('reactivarEmprendedora',{uid:adminUid},on2);
      await iniciado;
      const solicitud=await transition('desactivaciones');
      const apagar=assert.rejects(desactivar({uid:adminUid},solicitud),{code:'conflicto'});
      liberar();await Promise.all([reactivar,apagar]);
      assert.equal((await auth.getUser(buyer)).disabled,false);
      assert.equal((await db.doc(`accesos/${buyer}`).get()).data()!.estado,'activo');
    });
    await t.test('recuperación cambia contraseña y no concede permisos',async()=>{
      await authRest('sendOobCode',{requestType:'PASSWORD_RESET',email:`${other}@example.test`});const code=await codeFor(other,'PASSWORD_RESET');
      claves[other]=randomUUID();await authRest('resetPassword',{oobCode:code,newPassword:claves[other]});await login(other);
      assert.equal((await db.doc(`accesos/${other}`).get()).exists,false);
      await post('invitarEmprendedora',invitation(),other,403);
      const r=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:resetPassword?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({oobCode:code,newPassword:randomUUID()})});assert.equal(r.status,400);
    });
    await t.test('invitación puede emitirse antes de existir la identidad',async()=>{
      const r=await invite(invitation(`${prefix}_futura@example.test`));assert.equal(r.estado,'pendiente');
    });
    await t.test('reactivación no crea una identidad eliminada',async()=>{
      const off2=await transition('desactivaciones');await post('desactivarEmprendedora',off2);await auth.deleteUser(buyer);
      await post('reactivarEmprendedora',await transition('reactivaciones'),adminUid,503);
      assert.equal((await db.doc(`accesos/${buyer}`).get()).data()!.estado,'desactivado');
      assert.equal((await db.doc(`tiendasPublicas/${accepted.tiendaId}`).get()).data()!.habilitada,false);
    });
  } finally {
    await new Promise<void>(resolve=>{server.close(()=>resolve());server.closeAllConnections();});
    for(const store of stores) {await db.recursiveDelete(db.doc(`tiendasPrivadas/${store}`));await db.recursiveDelete(db.doc(`tiendasPublicas/${store}`));}
    const batch=db.batch();for(const uid of uids) for(const c of ['usuarios','accesos','emprendedoras']) batch.delete(db.doc(`${c}/${uid}`));
    for(const i of invites) batch.delete(db.doc(`invitaciones/${i}`));for(const r of receipts) batch.delete(db.doc(r));
    batch.delete(db.doc(`sectores/${prefix}`));batch.delete(db.doc(`tiposEmprendimiento/${prefix}`));await batch.commit();
    for(const uid of uids) await auth.deleteUser(uid).catch(e=>{if(e.code!=='auth/user-not-found') throw e;});
    await db.terminate();
  }
});
