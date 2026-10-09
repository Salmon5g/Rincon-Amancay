import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { deleteApp } from 'firebase/app';
import { terminate, getDoc, doc, Timestamp } from 'firebase/firestore';
import { getBytes, ref } from 'firebase/storage';
import { obtenerFirebaseLocal } from '../app/lib/firebase-local.ts';
import { crearClienteLocal, versionDocumento, ErrorApi } from '../app/lib/cliente-local.ts';
import { auth as adminAuth, db as adminDb, bucket } from '../../backend/src/config/emulador.ts';

test('cliente compartido web contra SDK, API y emuladores',async t=>{
  const health=await fetch('http://127.0.0.1:8787/health').then(r=>r.json());assert.equal(health.proyecto,'demo-rincon-amancay');
  const suffix=randomUUID(),uid='cliente_'+suffix,tiendaId='t_'+suffix;
  const local=obtenerFirebaseLocal('test_'+suffix),client=crearClienteLocal(local);
  const store=adminDb.doc(`tiendasPrivadas/${tiendaId}`),pub=adminDb.doc(`tiendasPublicas/${tiendaId}`);
  let imagePath;
  const email=uid+'@example.test',password=randomUUID();
  try {
    await adminAuth.createUser({uid,email,password});
    await adminDb.doc(`accesos/${uid}`).set({estado:'activo',roles:['emprendedora'],tiendaId});
    await store.set({propietarioUid:uid,nombre:'Tienda prueba SDK',descripcion:'Descripción',sectorId:tiendaId,tipoEmprendimientoId:tiendaId,estadoPublicacion:'borrador',actualizadoEn:new Date()});
    await store.collection('configuracion').doc('general').set({mostrarPrecios:true});
    await adminDb.doc(`sectores/${tiendaId}`).set({activo:true});await adminDb.doc(`tiposEmprendimiento/${tiendaId}`).set({activo:true});
    await adminDb.doc(`tiposProducto/${tiendaId}`).set({activo:true});await adminDb.doc(`tiposProducto/${tiendaId}/versiones/1`).set({campos:[],opcionesVariante:[]});await adminDb.doc(`categorias/${tiendaId}`).set({activo:true});
    await t.test('inicialización reutilizable y solo demo',()=>{assert.equal(obtenerFirebaseLocal('test_'+suffix),local);assert.throws(()=>crearClienteLocal({app:{options:{projectId:'rincon-amancay'}}}));});
    await t.test('catálogo público se consulta sin iniciar sesión',async()=>{const result=await client.listarTiendas();assert(result.docs.every(d=>d.data().habilitada===true));assert.equal(await client.consultarAcceso(),null);});
    await t.test('sin sesión no llama operaciones privadas',async()=>{await assert.rejects(client.llamar('publicarTienda',{}),e=>e instanceof ErrorApi&&e.status===401);});
    await t.test('login obtiene roles desde Firestore',async()=>{await client.iniciarSesion(email,password);assert.equal((await client.consultarAcceso()).tiendaId,tiendaId);});
    await t.test('publica mediante token del SDK y conserva ID al reintentar',async()=>{
      const source=await getDoc(doc(local.db,store.path));const request={tiendaId,operacionId:randomUUID(),versionEsperada:versionDocumento(source.data().actualizadoEn)};
      assert.deepEqual(await client.llamar('publicarTienda',request),await client.llamar('publicarTienda',request));assert.equal((await pub.get()).data().habilitada,true);
    });
    await t.test('versión usa nanosegundos y conflicto llega como error estructurado',async()=>{
      assert.equal(versionDocumento(new Timestamp(100,123)),'100:123');
      await assert.rejects(client.llamar('publicarTienda',{tiendaId,operacionId:randomUUID(),versionEsperada:'1:0'}),e=>e instanceof ErrorApi&&e.code==='conflicto'&&e.status===409);
    });
    await t.test('crea ficha privada desde el cliente compartido',async()=>{
      await client.llamar('crearProducto',{tiendaId,productoId:'producto',operacionId:randomUUID(),producto:{nombre:'Producto',descripcion:'Descripción',tipoProductoId:tiendaId,versionTipoProducto:1,categoriaId:tiendaId,atributosEspecificos:{},tieneVariantes:false,moneda:'CLP',unidadVenta:'unidad',pasoCantidad:1,modalidad:'regular',manejaStock:true,alAgotarse:'mostrar_agotado',precioBase:1000,stockInicial:2},variantes:[]});
      assert.equal((await client.leerProductoPrivado(tiendaId,'producto')).data().stock,2);
    });
    await t.test('sube imagen, selecciona con versión y publica producto',async()=>{
      const bytes=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=','base64');
      imagePath=await client.subirImagen(tiendaId,'producto',new Blob([bytes],{type:'image/png'}));
      const before=versionDocumento((await client.leerProductoPrivado(tiendaId,'producto')).data().actualizadoEn);
      await client.seleccionarImagenes(tiendaId,'producto',[imagePath],before);
      await assert.rejects(client.seleccionarImagenes(tiendaId,'producto',[],before),e=>e.code==='conflicto');
      const p=await client.leerProductoPrivado(tiendaId,'producto');
      await client.llamar('publicarProducto',{tiendaId,productoId:'producto',operacionId:randomUUID(),versionEsperada:versionDocumento(p.data().actualizadoEn)});
    });
    await t.test('cerrar sesión permite catálogo y foto pero no ficha privada',async()=>{
      await client.cerrarSesion();const products=await client.listarProductos(tiendaId);assert.equal(products.docs[0].id,'producto');
      assert((await getBytes(ref(local.storage,imagePath))).byteLength>0);
      await assert.rejects(client.leerProductoPrivado(tiendaId,'producto'),e=>e.code==='permission-denied');
    });
    await t.test('401 HTTP no provoca reintento automático ni oculta el error',async()=>{
      await client.iniciarSesion(email,password);await adminAuth.updateUser(uid,{disabled:true});
      await assert.rejects(client.llamar('publicarTienda',{tiendaId,operacionId:randomUUID(),versionEsperada:'1:0'}),e=>e instanceof ErrorApi&&e.status===401);
    });
  }finally{
    await client.cerrarSesion();await terminate(local.db);await deleteApp(local.app);
    await adminDb.recursiveDelete(store);await adminDb.recursiveDelete(pub);await adminDb.recursiveDelete(adminDb.doc(`tiposProducto/${tiendaId}`));
    for(const path of [`accesos/${uid}`,`sectores/${tiendaId}`,`tiposEmprendimiento/${tiendaId}`,`categorias/${tiendaId}`])await adminDb.doc(path).delete();
    await adminAuth.deleteUser(uid).catch(e=>{if(e.code!=='auth/user-not-found')throw e;});
    if(imagePath)await bucket.file(imagePath).delete({ignoreNotFound:true});await adminDb.terminate();
  }
});
