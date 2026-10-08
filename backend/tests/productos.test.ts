import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { auth, db } from '../src/config/emulador.ts';
import { crearApi } from '../src/http/api.ts';
import { crearPublicacion, version } from '../src/modules/publicacion.ts';
import { crearProductos } from '../src/modules/productos.ts';

test('edición de productos y variantes mediante API local', async t => {
  const uid='productos_'+randomUUID(), tiendaId='t_'+randomUUID(), tipo='tipo_'+randomUUID();
  const store=db.doc(`tiendasPrivadas/${tiendaId}`);
  const publicStore=db.doc(`tiendasPublicas/${tiendaId}`);
  const type=db.doc(`tiposProducto/${tipo}`), category=db.doc(`categorias/${tipo}`);
  const server=crearApi(auth,crearPublicacion(db,async()=>true),undefined,crearProductos(db));
  server.listen(0,'127.0.0.1');await once(server,'listening');
  const addr=server.address();assert(addr&&typeof addr!=='string');
  const base=`http://127.0.0.1:${addr.port}/api/v1/`;
  let token='';
  function input(): any {
    return {tiendaId,productoId:'p_'+randomUUID(),operacionId:randomUUID(),producto:{nombre:'Gorro',descripcion:'Tejido de prueba',tipoProductoId:tipo,versionTipoProducto:1,categoriaId:tipo,atributosEspecificos:{material:'Lana'},tieneVariantes:false,moneda:'CLP',unidadVenta:'unidad',pasoCantidad:1,modalidad:'regular',manejaStock:true,alAgotarse:'mostrar_agotado',precioBase:5000,stockInicial:3},variantes:[]};
  }
  async function post(d: any, accion='crearProducto', expected=200, bearer=token) {
    const r=await fetch(base+accion,{method:'POST',headers:{'Content-Type':'application/json',...(bearer?{Authorization:`Bearer ${bearer}`}:{})},body:JSON.stringify(d)});
    const body=await r.json();assert.equal(r.status,expected,JSON.stringify(body));return body;
  }
  async function edit(d: any) {
    const result=structuredClone(d);result.operacionId=randomUUID();
    result.versionEsperada=version((await store.collection('productos').doc(d.productoId).get()).data()!);
    delete result.producto.stockInicial;for(const v of result.variantes) delete v.stockInicial;
    return result;
  }
  try {
    const email=uid+'@example.test',password=randomUUID();await auth.createUser({uid,email,password});
    const login=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password,returnSecureToken:true})});
    assert.equal(login.status,200);token=(await login.json()).idToken;
    await db.doc(`accesos/${uid}`).set({roles:['emprendedora'],estado:'activo',tiendaId});
    await store.set({propietarioUid:uid,estadoPublicacion:'publicado'});
    await store.collection('configuracion').doc('general').set({mostrarPrecios:true});
    await publicStore.set({estadoPublicacion:'publicado',habilitada:true,mostrarPrecios:true});
    await type.set({activo:true});await category.set({activo:true});
    await type.collection('versiones').doc('1').set({campos:[{clave:'material',tipo:'texto',obligatorio:true}],opcionesVariante:['color','talla']});
    await t.test('exige sesión',()=>post(input(),'crearProducto',401,''));
    await t.test('propiedad ajena se rechaza',async()=>{await store.update({propietarioUid:'otra'});await post(input(),'crearProducto',403);await store.update({propietarioUid:uid});});
    await t.test('rol administrador no equivale a propietaria',async()=>{await db.doc(`accesos/${uid}`).update({roles:['administrador']});await post(input(),'crearProducto',403);await db.doc(`accesos/${uid}`).update({roles:['emprendedora']});});
    await t.test('acceso desactivado se rechaza',async()=>{await db.doc(`accesos/${uid}`).update({estado:'desactivado'});await post(input(),'crearProducto',403);await db.doc(`accesos/${uid}`).update({estado:'activo'});});
    await t.test('rechaza campos privilegiados',async()=>{const d=input();d.producto.estadoPublicacion='publicado';await post(d,'crearProducto',400);});
    await t.test('rechaza atributos ajenos y obligatorios ausentes',async()=>{const d=input();d.producto.atributosEspecificos={otro:'x'};await post(d,'crearProducto',400);d.producto.atributosEspecificos={};await post(d,'crearProducto',400);});
    await t.test('rechaza tipo o categoría inactivos',async()=>{await category.update({activo:false});await post(input(),'crearProducto',400);await category.update({activo:true});});
    await t.test('rechaza precio fraccionario y stock negativo',async()=>{const d=input();d.producto.precioBase=1.5;await post(d,'crearProducto',400);d.producto.precioBase=10;d.producto.stockInicial=-1;await post(d,'crearProducto',400);});
    const simple=input();
    await t.test('crea producto simple sin publicar y con fechas servidor',async()=>{
      await post(simple);const data=(await store.collection('productos').doc(simple.productoId).get()).data()!;
      assert.equal(data.estadoPublicacion,'borrador');assert.equal(data.stock,3);assert.deepEqual(data.imagenes,[]);assert(data.creadoEn.toMillis()>0);
      assert.equal((await publicStore.collection('productos').doc(simple.productoId).get()).exists,false);
    });
    await t.test('reintento no reescribe y claves reordenadas son equivalentes',async()=>{
      const ref=store.collection('productos').doc(simple.productoId),before=(await ref.get()).updateTime!;
      await post(Object.fromEntries(Object.entries(simple).reverse()));assert(before.isEqual((await ref.get()).updateTime!));
      await post({...simple,producto:{...simple.producto,nombre:'Otro'}},'crearProducto',409);
    });
    await t.test('no sobrescribe ID existente',()=>post({...simple,operacionId:randomUUID()},'crearProducto',409));
    await t.test('edición cambia texto y precio pero conserva stock e imágenes',async()=>{
      const ref=store.collection('productos').doc(simple.productoId);await ref.update({imagenes:['foto-fixture']});
      const before=(await ref.get()).data()!;const d=await edit(simple);d.producto.nombre='Gorro nuevo';d.producto.precioBase=6000;
      await post(d,'editarProducto');const after=(await ref.get()).data()!;
      assert.equal(after.nombre,'Gorro nuevo');assert.equal(after.stock,3);assert.equal(after.precioBase,6000);assert.deepEqual(after.imagenes,['foto-fixture']);assert(before.creadoEn.isEqual(after.creadoEn));
      await post({...d,operacionId:randomUUID()},'editarProducto',409);
    });
    await t.test('edición no modifica stock ni estructura',async()=>{
      let d=await edit(simple);d.producto.stockInicial=999;await post(d,'editarProducto',400);
      d=await edit(simple);d.producto.modalidad='a_pedido';await post(d,'editarProducto',409);
    });
    await t.test('producto a pedido sin stock',async()=>{const d=input();d.producto.modalidad='a_pedido';d.producto.manejaStock=false;delete d.producto.stockInicial;await post(d);assert(!('stock' in (await store.collection('productos').doc(d.productoId).get()).data()!));});
    await t.test('pieza única exige exactamente una unidad',async()=>{const d=input();d.producto.modalidad='pieza_unica';await post(d,'crearProducto',400);d.producto.stockInicial=1;await post(d);});
    const multi=input();multi.producto.tieneVariantes=true;delete multi.producto.precioBase;delete multi.producto.stockInicial;
    multi.variantes=[{varianteId:'azul_m',opciones:{color:'Azul',talla:'M'},precio:5000,activa:true,stockInicial:2}];
    await t.test('crea variantes con precio y stock solo en ellas',async()=>{await post(multi);const p=(await store.collection('productos').doc(multi.productoId).get()).data()!;assert(!('stock' in p));assert(!('precioBase' in p));});
    await t.test('rechaza combinaciones duplicadas y opciones incompletas',async()=>{
      const d=structuredClone(multi);d.productoId='p_'+randomUUID();d.operacionId=randomUUID();d.variantes.push({...d.variantes[0],varianteId:'otra'});await post(d,'crearProducto',400);
      d.variantes.pop();delete d.variantes[0].opciones.talla;await post(d,'crearProducto',400);
    });
    await t.test('edita variante y añade otra sin cambiar stock existente',async()=>{
      const ref=store.collection('productos').doc(multi.productoId);const before=version((await ref.get()).data()!);
      const d=await edit(multi);d.variantes[0].precio=7000;d.variantes.push({varianteId:'rojo_m',opciones:{color:'Rojo',talla:'M'},precio:6000,stockInicial:4,activa:true});
      await post(d,'editarProducto');assert.notEqual(version((await ref.get()).data()!),before);assert.equal((await ref.collection('variantes').doc('azul_m').get()).data()!.stock,2);
      multi.variantes=d.variantes;
    });
    await t.test('no omite variantes ni cambia su identidad',async()=>{let d=await edit(multi);d.variantes.pop();await post(d,'editarProducto',400);d=await edit(multi);d.variantes[0].opciones.color='Verde';await post(d,'editarProducto',409);});
    await t.test('publicación refleja edición solo al publicar de nuevo',async()=>{
      const ref=store.collection('productos').doc(multi.productoId);await ref.update({imagenes:['fixture-solo-test']});
      const publish=async()=>post({tiendaId,productoId:multi.productoId,operacionId:randomUUID(),versionEsperada:version((await ref.get()).data()!)},'publicarProducto');
      await publish();const pub=publicStore.collection('productos').doc(multi.productoId);
      const d=await edit(multi);d.producto.nombre='Nombre editado';d.variantes[0].activa=false;
      await post(d,'editarProducto');assert.equal((await pub.get()).data()!.nombre,'Gorro');assert.equal((await pub.collection('variantes').doc('azul_m').get()).exists,true);
      await publish();assert.equal((await pub.get()).data()!.nombre,'Nombre editado');assert.equal((await pub.collection('variantes').doc('azul_m').get()).exists,false);
    });
    await t.test('dos ediciones concurrentes no se pisan',async()=>{
      const d=await edit(simple);const requests=[d,{...d,operacionId:randomUUID(),producto:{...d.producto,nombre:'Alternativo'}}];
      const statuses=await Promise.all(requests.map(body=>fetch(base+'editarProducto',{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`},body:JSON.stringify(body)}).then(r=>r.status)));
      assert.deepEqual(statuses.sort(),[200,409]);
    });
  } finally {
    await new Promise<void>(resolve=>{server.close(()=>resolve());server.closeAllConnections();});
    await db.recursiveDelete(store);await db.recursiveDelete(publicStore);await db.recursiveDelete(type);await category.delete();
    await db.doc(`accesos/${uid}`).delete();await auth.deleteUser(uid).catch(e=>{if(e.code!=='auth/user-not-found')throw e;});await db.terminate();
  }
});
