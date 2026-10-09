import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { Timestamp, FieldValue } from 'firebase-admin/firestore';
import { db } from '../src/config/emulador.ts';
import { crearPublicacion, version } from '../src/modules/publicacion.ts';
import { crearStock } from '../src/modules/stock.ts';

test('disponibilidad pública independiente del borrador',async t=>{
  const key='disp_'+randomUUID(),uid=key,tiendaId=key;
  const store=db.doc(`tiendasPrivadas/${key}`),product=store.collection('productos').doc('p'),pub=db.doc(`tiendasPublicas/${key}/productos/p`);
  const publicar=crearPublicacion(db,async()=>true),ajustar=crearStock(db);
  async function publicacion(accion:'publicarTienda'|'retirarTienda'|'publicarProducto'|'retirarProducto') {
    const source=accion.endsWith('Producto')?product:store;
    return publicar(accion,{uid},{tiendaId,...(accion.endsWith('Producto')?{productoId:'p'}:{}),operacionId:randomUUID(),versionEsperada:version((await source.get()).data()!)});
  }
  async function request(cantidad:number,varianteId?:string) {return {tiendaId,productoId:'p',operacionId:randomUUID(),versionEsperada:version((await product.get()).data()!),cantidad,motivo:'correccion',...(varianteId?{varianteId}:{})};}
  async function stock(cantidad:number,varianteId?:string) {return ajustar({uid},await request(cantidad,varianteId));}
  async function reset(extra:Record<string,unknown>={}) {
    await db.recursiveDelete(product);await db.recursiveDelete(pub);
    await product.set({nombre:'Nombre aprobado',descripcion:'Descripción aprobada',tipoProductoId:key,versionTipoProducto:1,categoriaId:key,imagenes:['foto-aprobada'],atributosEspecificos:{},tieneVariantes:false,moneda:'CLP',unidadVenta:'unidad',pasoCantidad:1,modalidad:'regular',manejaStock:true,alAgotarse:'mostrar_agotado',precioBase:1500,stock:2,disponible:true,piezaUnicaVendida:false,estadoPublicacion:'borrador',actualizadoEn:Timestamp.now(),...extra});
  }
  try {
    await db.doc(`accesos/${uid}`).set({estado:'activo',roles:['emprendedora'],tiendaId});
    for(const c of ['sectores','tiposEmprendimiento','tiposProducto','categorias'])await db.doc(`${c}/${key}`).set({activo:true});
    await db.doc(`tiposProducto/${key}/versiones/1`).set({campos:[],opcionesVariante:['color']});
    await store.set({propietarioUid:uid,nombre:'Tienda',descripcion:'Descripción',sectorId:key,tipoEmprendimientoId:key,estadoPublicacion:'borrador',actualizadoEn:Timestamp.now()});
    await store.collection('configuracion').doc('general').set({mostrarPrecios:true});await publicacion('publicarTienda');
    await t.test('actualiza disponibilidad sin copiar nombre, precio, fotos ni política pendientes',async()=>{
      await reset();await publicacion('publicarProducto');
      await product.update({nombre:'Nombre borrador',precioBase:9999,imagenes:['foto-borrador'],alAgotarse:'ocultar'});
      const r=await stock(-2);assert.equal(r.requiereRepublicar,false);
      const p=(await pub.get()).data()!;assert.equal(p.estadoDisponibilidad,'agotado');assert.equal(p.nombre,'Nombre aprobado');assert.equal(p.precioBase,1500);assert.deepEqual(p.imagenes,['foto-aprobada']);assert(!('stock' in p));
      assert.equal((await product.get()).data()!.estadoPublicacion,'publicado');
      await stock(1);assert.equal((await pub.get()).data()!.estadoDisponibilidad,'disponible');
    });
    await t.test('ocultar al agotarse y reponer recupera la ficha aprobada',async()=>{
      await reset({alAgotarse:'ocultar'});await publicacion('publicarProducto');await stock(-2);assert.equal((await pub.get()).exists,false);
      await product.update({nombre:'Borrador que no se publica'});const d=await request(1);await ajustar({uid},d);await ajustar({uid},d);
      assert.equal((await product.get()).data()!.stock,1);assert.equal((await pub.get()).data()!.nombre,'Nombre aprobado');
      assert.equal((await store.collection('movimientosStock').doc(d.operacionId).get()).data()!.actualizacionCatalogo,'actualizado');
    });
    await t.test('pasar a pedido y regresar a disponible usa la política aprobada',async()=>{
      await reset({alAgotarse:'pasar_a_pedido'});await publicacion('publicarProducto');await stock(-2);assert.equal((await pub.get()).data()!.estadoDisponibilidad,'a_pedido');
      await stock(3);assert.equal((await pub.get()).data()!.estadoDisponibilidad,'disponible');
    });
    await t.test('pieza única perdida no se vende; reposición restaura sin publicar borrador',async()=>{
      await reset({modalidad:'pieza_unica',stock:1,alAgotarse:'ocultar'});await publicacion('publicarProducto');await stock(-1);assert.equal((await pub.get()).exists,false);
      await stock(1);assert.equal((await pub.get()).data()!.estadoDisponibilidad,'disponible');assert.equal((await product.get()).data()!.piezaUnicaVendida,false);
    });
    await t.test('variantes aprobadas ocultas reaparecen; cambios y variantes nuevas no se filtran',async()=>{
      await reset({tieneVariantes:true,alAgotarse:'ocultar'});await product.update({stock:FieldValue.delete(),precioBase:FieldValue.delete()});
      await product.collection('variantes').doc('azul').set({opciones:{color:'Azul'},precio:1500,stock:1,activa:true});
      await product.collection('variantes').doc('rojo').set({opciones:{color:'Rojo'},precio:2000,stock:0,activa:true});await publicacion('publicarProducto');
      await product.collection('variantes').doc('azul').update({precio:9999,activa:false});
      await product.collection('variantes').doc('nueva').set({opciones:{color:'Verde'},precio:999,stock:1,activa:true});
      await stock(1,'nueva');assert.equal((await pub.collection('variantes').doc('nueva').get()).exists,false);
      await stock(1,'rojo');assert.equal((await pub.collection('variantes').doc('rojo').get()).data()!.precio,2000);
      assert.equal((await pub.collection('variantes').doc('azul').get()).data()!.precio,1500);
      await stock(-1,'azul');assert.equal((await pub.collection('variantes').doc('azul').get()).exists,false);
      await stock(-1,'rojo');assert.equal((await pub.get()).exists,false);
      await stock(1,'azul');assert.equal((await pub.get()).data()!.estadoDisponibilidad,'disponible');assert.equal((await pub.collection('variantes').doc('azul').get()).data()!.activa,true);
    });
    await t.test('retirada explícita de producto impide que una reposición lo publique',async()=>{
      await reset({alAgotarse:'ocultar'});await publicacion('publicarProducto');await stock(-2);await publicacion('retirarProducto');await stock(1);
      assert.equal((await pub.get()).exists,false);assert.equal((await product.collection('publicacion').doc('vigente').get()).exists,false);
    });
    await t.test('retirar y republicar tienda invalida también productos ocultos por stock',async()=>{
      await reset({alAgotarse:'ocultar'});await publicacion('publicarProducto');await stock(-2);await publicacion('retirarTienda');await publicacion('publicarTienda');await stock(1);
      assert.equal((await pub.get()).exists,false);
      await publicacion('publicarProducto');assert.equal((await pub.get()).exists,true);
    });
    await t.test('tienda deshabilitada no se habilita al ajustar stock',async()=>{
      await db.doc(`tiendasPublicas/${key}`).update({habilitada:false});await stock(-1);assert.equal((await db.doc(`tiendasPublicas/${key}`).get()).data()!.habilitada,false);
      await db.doc(`tiendasPublicas/${key}`).update({habilitada:true});
    });
    await t.test('publicación y ajuste concurrentes no mezclan versiones',async()=>{
      await reset();await publicacion('publicarProducto');const d=await request(-1);
      const results=await Promise.allSettled([ajustar({uid},d),publicar('publicarProducto',{uid},{tiendaId,productoId:'p',operacionId:randomUUID(),versionEsperada:d.versionEsperada})]);
      assert.equal(results.filter(r=>r.status==='fulfilled').length,1);assert.equal(results.filter(r=>r.status==='rejected').length,1);
      assert.equal((await pub.get()).data()!.estadoDisponibilidad,'disponible');
    });
  } finally {
    await db.recursiveDelete(store);await db.recursiveDelete(db.doc(`tiendasPublicas/${key}`));await db.doc(`accesos/${uid}`).delete();
    for(const c of ['sectores','tiposEmprendimiento','tiposProducto','categorias'])await db.recursiveDelete(db.doc(`${c}/${key}`));await db.terminate();
  }
});
