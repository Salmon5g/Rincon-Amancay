import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { Timestamp, FieldValue } from 'firebase-admin/firestore';
import { auth, db } from '../src/config/emulador.ts';
import { crearApi } from '../src/http/api.ts';
import { crearStock } from '../src/modules/stock.ts';
import { crearPublicacion, version } from '../src/modules/publicacion.ts';

test('ajustes de stock e historial privados',async t=>{
  const uid='stock_'+randomUUID(),other='otro_'+randomUUID(),tiendaId='t_'+randomUUID();
  const store=db.doc(`tiendasPrivadas/${tiendaId}`),product=store.collection('productos').doc('producto');
  const pub=db.doc(`tiendasPublicas/${tiendaId}/productos/producto`);
  const tokens:Record<string,string>={};
  const server=crearApi(auth,crearPublicacion(db,async()=>true),undefined,undefined,crearStock(db));
  server.listen(0,'127.0.0.1');await once(server,'listening');const addr=server.address();assert(addr&&typeof addr!=='string');
  async function input(extra:Record<string,unknown>={}) {
    return {tiendaId,productoId:'producto',operacionId:randomUUID(),versionEsperada:version((await product.get()).data()!),cantidad:2,motivo:'reposicion',...extra};
  }
  async function request(d:unknown,who=uid){
    const r=await fetch(`http://127.0.0.1:${(addr as {port:number}).port}/api/v1/ajustarStock`,{method:'POST',headers:{'Content-Type':'application/json',...(tokens[who]?{Authorization:`Bearer ${tokens[who]}`}:{})},body:JSON.stringify(d)});
    return {status:r.status,body:await r.json()};
  }
  async function expect(d:unknown,status:number,who=uid){const r=await request(d,who);assert.equal(r.status,status,JSON.stringify(r.body));return r.body;}
  async function reset(extra:Record<string,unknown>={}) {await product.set({stock:5,manejaStock:true,tieneVariantes:false,modalidad:'regular',piezaUnicaVendida:false,estadoPublicacion:'borrador',actualizadoEn:Timestamp.now(),...extra});}
  async function client(path:string,who=uid,method='GET',body?:unknown){return fetch(`http://127.0.0.1:8080/v1/projects/demo-rincon-amancay/databases/(default)/documents/${path}`,{method,headers:{'Content-Type':'application/json',...(tokens[who]?{Authorization:`Bearer ${tokens[who]}`}:{})},...(body?{body:JSON.stringify(body)}:{})});}
  try{
    for(const id of [uid,other]) {
      const email=id+'@example.test',password=randomUUID();await auth.createUser({uid:id,email,password});
      const r=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});assert.equal(r.status,200);tokens[id]=(await r.json()).idToken;
    }
    await store.set({propietarioUid:uid});await db.doc(`accesos/${uid}`).set({estado:'activo',roles:['emprendedora'],tiendaId});
    await db.doc(`accesos/${other}`).set({estado:'activo',roles:['administrador']});await reset();
    await t.test('sin sesión no ajusta',async()=>{await expect(await input(),401,'sin-token');});
    await t.test('administrador ajeno no ajusta',async()=>{await expect(await input(),403,other);});
    await t.test('propietaria desactivada no ajusta',async()=>{await db.doc(`accesos/${uid}`).update({estado:'desactivado'});await expect(await input(),403);await db.doc(`accesos/${uid}`).update({estado:'activo'});});
    await t.test('no acepta actor, fechas o saldo enviados',async()=>{await expect(await input({realizadoPor:other}),400);await expect(await input({stockNuevo:100}),400);});
    await t.test('cantidades y motivos inválidos se rechazan',async()=>{
      for(const extra of [{cantidad:0},{cantidad:1.5},{cantidad:'2'},{cantidad:-1},{motivo:'perdida'},{motivo:'venta'},{nota:''}])await expect(await input(extra),400);
    });
    const first=await input({nota:'Nueva producción'});
    await t.test('reposicion registra antes, después, actor y fecha',async()=>{
      const r=await expect(first,200);assert.equal(r.datos.stockAnterior,5);assert.equal(r.datos.stockNuevo,7);assert.equal(r.datos.requiereRepublicar,false);
      const movement=(await store.collection('movimientosStock').doc(first.operacionId).get()).data()!;
      assert.equal(movement.realizadoPor,uid);assert.equal(movement.motivo,'reposicion');assert.equal(movement.cantidad,2);assert(movement.creadoEn instanceof Timestamp);
    });
    await t.test('reintento no vuelve a sumar ni duplica historial',async()=>{
      await expect(first,200);assert.equal((await product.get()).data()!.stock,7);assert.equal((await store.collection('movimientosStock').get()).size,1);
      await expect({...first,cantidad:3},409);
    });
    await t.test('historial solo legible por propietaria; inmutable desde cliente',async()=>{
      const path=`${store.path}/movimientosStock/${first.operacionId}`;
      for(const [who,status] of [[uid,200],[other,403],['visitante',403]] as const){const r=await client(path,who);assert.equal(r.status,status,await r.text());}
      assert.equal((await client(path,uid,'PATCH',{fields:{cantidad:{integerValue:'999'}}})).status,403);
      assert.equal((await client(path,uid,'DELETE')).status,403);
      assert.equal((await client(`${store.path}/movimientosStock/falso`,uid,'PATCH',{fields:{cantidad:{integerValue:'999'}}})).status,403);
      assert.equal((await client(`${store.path}/operacionesStock/${first.operacionId}`,uid)).status,403);
    });
    await t.test('saldo negativo falla sin crear movimiento',async()=>{
      const d=await input({cantidad:-99,motivo:'perdida'});await expect(d,409);assert.equal((await product.get()).data()!.stock,7);assert.equal((await store.collection('movimientosStock').doc(d.operacionId).get()).exists,false);
    });
    await t.test('versión antigua se rechaza',async()=>{await expect(await input({versionEsperada:'1:0'}),409);});
    await t.test('publicación antigua sin aprobación requiere republicación, sin copiar borrador',async()=>{
      await product.update({estadoPublicacion:'publicado',nombre:'Borrador nuevo'});await pub.set({estadoPublicacion:'publicado',nombre:'Texto anterior'});await pub.collection('variantes').doc('antigua').set({activa:true});
      const r=await expect(await input({cantidad:-1,motivo:'perdida'}),200);assert.equal(r.datos.requiereRepublicar,true);
      assert.equal((await pub.get()).exists,false);assert.equal((await pub.collection('variantes').get()).size,0);assert.equal((await product.get()).data()!.estadoPublicacion,'archivado');assert.equal((await product.get()).data()!.nombre,'Borrador nuevo');
    });
    await t.test('variante actualiza también versión del producto',async()=>{
      await reset({tieneVariantes:true});await product.update({stock:FieldValue.delete()});
      await product.collection('variantes').doc('azul').set({stock:4,activa:false});
      const before=version((await product.get()).data()!);
      await expect(await input({varianteId:'azul'}),200);assert.equal((await product.collection('variantes').doc('azul').get()).data()!.stock,6);
      assert.notEqual(version((await product.get()).data()!),before);assert(!('stock' in (await product.get()).data()!));
      await expect(await input(),400);await expect(await input({varianteId:'inexistente'}),404);
    });
    await t.test('producto simple no admite variante',async()=>{await reset();await expect(await input({varianteId:'azul'}),400);});
    await t.test('producto sin stock se rechaza',async()=>{await reset({manejaStock:false});await expect(await input(),400);});
    await t.test('pieza única admite 0/1 pero no se convierte en venta',async()=>{
      await reset({modalidad:'pieza_unica',stock:1});await expect(await input(),409);
      await expect(await input({cantidad:-1,motivo:'perdida'}),200);assert.equal((await product.get()).data()!.piezaUnicaVendida,false);
      await expect(await input({cantidad:1,motivo:'correccion'}),200);
      await product.update({piezaUnicaVendida:true});await expect(await input({cantidad:-1,motivo:'correccion'}),409);
    });
    await t.test('rechaza desbordamiento numérico',async()=>{await reset({stock:Number.MAX_SAFE_INTEGER});await expect(await input(),409);});
    await t.test('ajustes concurrentes no pierden movimientos',async()=>{
      await reset();const d=await input();const r=await Promise.all([request(d),request({...d,operacionId:randomUUID()})]);assert.deepEqual(r.map(x=>x.status).sort(),[200,409]);assert.equal((await product.get()).data()!.stock,7);
    });
  }finally{
    await new Promise<void>(resolve=>{server.close(()=>resolve());server.closeAllConnections();});await db.recursiveDelete(store);await db.recursiveDelete(db.doc(`tiendasPublicas/${tiendaId}`));
    for(const id of [uid,other]){await db.doc(`accesos/${id}`).delete();await auth.deleteUser(id).catch(e=>{if(e.code!=='auth/user-not-found')throw e;});}await db.terminate();
  }
});
