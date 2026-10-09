import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import type { Firestore } from 'firebase-admin/firestore';
import { ErrorOperacion, version, estado } from './publicacion.ts';

function exigir(ok: unknown, code: string, message: string): asserts ok {
  if (!ok) throw new ErrorOperacion(code, message);
}
function id(v: unknown) {return typeof v === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(v);}
export function crearStock(db: Firestore) {
  return async (identidad: {uid: string}, input: unknown) => {
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');
    exigir(input && typeof input === 'object' && !Array.isArray(input), 'datos-invalidos', 'Solicitud inválida.');
    const d=input as Record<string, unknown>;
    const required=['tiendaId','productoId','operacionId','versionEsperada','cantidad','motivo'];
    exigir(required.every(k=>Object.hasOwn(d,k)) && Object.keys(d).every(k=>[...required,'varianteId','nota'].includes(k)), 'datos-invalidos', 'Campos ausentes o no permitidos.');
    exigir(id(d.tiendaId) && id(d.productoId) && id(d.operacionId) && (!Object.hasOwn(d,'varianteId') || id(d.varianteId)), 'datos-invalidos', 'IDs inválidos.');
    exigir(typeof d.versionEsperada === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(d.versionEsperada), 'datos-invalidos', 'Versión inválida.');
    exigir(Number.isSafeInteger(d.cantidad) && d.cantidad !== 0, 'datos-invalidos', 'Cantidad entera distinta de cero requerida.');
    exigir(['reposicion','perdida','correccion'].includes(d.motivo as string), 'datos-invalidos', 'Motivo inválido; esta operación no registra ventas.');
    exigir(d.motivo !== 'reposicion' || Number(d.cantidad)>0, 'datos-invalidos', 'Reposición debe sumar.');
    exigir(d.motivo !== 'perdida' || Number(d.cantidad)<0, 'datos-invalidos', 'Pérdida debe restar.');
    exigir(!Object.hasOwn(d,'nota') || (typeof d.nota === 'string' && d.nota.trim().length>0 && d.nota.length<=500), 'datos-invalidos', 'Nota inválida.');
    const firma=createHash('sha256').update(JSON.stringify([identidad.uid,d.tiendaId,d.productoId,d.varianteId??null,d.operacionId,d.versionEsperada,d.cantidad,d.motivo,d.nota??null])).digest('hex');
    const store=db.doc(`tiendasPrivadas/${d.tiendaId}`);
    const product=store.collection('productos').doc(d.productoId as string);
    const target=d.varianteId ? product.collection('variantes').doc(d.varianteId as string) : product;
    const pub=db.doc(`tiendasPublicas/${d.tiendaId}/productos/${d.productoId}`);
    const receipt=store.collection('operacionesStock').doc(d.operacionId as string);
    const history=store.collection('movimientosStock').doc(d.operacionId as string);
    return db.runTransaction(async tx=>{
      const [access,shop,p,previous,current,publicProduct,movement,aprobacion,publicShop]=await tx.getAll(db.doc(`accesos/${identidad.uid}`),store,product,receipt,target,pub,history,product.collection('publicacion').doc('vigente'),db.doc(`tiendasPublicas/${d.tiendaId}`));
      const a=access.data();
      exigir(a?.estado==='activo' && Array.isArray(a.roles) && a.roles.includes('emprendedora') && a.tiendaId===d.tiendaId && shop.data()?.propietarioUid===identidad.uid, 'sin-permiso', 'Se requiere propietaria activa asignada.');
      if(previous.exists) {
        exigir(previous.data()!.firma===firma,'id-reutilizado','operacionId corresponde a otra solicitud.');
        return previous.data()!.resultado as {operacionId:string;stockAnterior:number;stockNuevo:number;requiereRepublicar:boolean};
      }
      exigir(!movement.exists,'conflicto','Ya existe un movimiento con este identificador.');
      exigir(p.exists && current.exists,'no-encontrado','Producto o variante inexistente.');
      const data=p.data()!;
      exigir(data.manejaStock===true,'datos-invalidos','El producto no maneja stock.');
      exigir(typeof data.tieneVariantes==='boolean' && data.tieneVariantes===Boolean(d.varianteId),'datos-invalidos','Especificar variante únicamente en productos con variantes.');
      exigir(version(data)===d.versionEsperada,'conflicto','El producto cambió; volver a leer.');
      exigir(data.piezaUnicaVendida===false,'conflicto','No se puede ajustar una pieza marcada como vendida.');
      const stockAnterior=current.data()!.stock;
      exigir(Number.isSafeInteger(stockAnterior) && stockAnterior>=0,'datos-invalidos','Stock existente inválido.');
      const stockNuevo=stockAnterior+Number(d.cantidad);
      exigir(Number.isSafeInteger(stockNuevo) && stockNuevo>=0,'conflicto','El ajuste dejaría stock negativo o fuera de rango.');
      if(data.modalidad==='pieza_unica') exigir(!data.tieneVariantes && stockNuevo<=1,'conflicto','Una pieza única solo admite cero o una unidad.');
      const publicVariants=await tx.get(pub.collection('variantes').limit(101));
      exigir(publicVariants.size<=100,'limite-local','Demasiadas variantes públicas; no se aplicó el ajuste.');
      const aprobada=aprobacion.data();
      const sincronizar=aprobada?.schema===1 && aprobada.cicloCatalogo===(shop.data()?.cicloCatalogo??0) && shop.data()?.estadoPublicacion==='publicado' && publicShop.exists && data.estadoPublicacion==='publicado';
      const requiereRepublicar=!sincronizar && (publicProduct.exists || !publicVariants.empty || data.estadoPublicacion==='publicado');
      const publicarVariantes: {id:string;data:FirebaseFirestore.DocumentData}[]=[];
      let proyeccion:FirebaseFirestore.DocumentData|null=null;
      if(sincronizar) {
        const policy=aprobada.politica;
        exigir(policy && ['regular','a_pedido','pieza_unica'].includes(policy.modalidad) && typeof policy.manejaStock==='boolean' && ['mostrar_agotado','ocultar','pasar_a_pedido'].includes(policy.alAgotarse),'conflicto','Publicación aprobada inconsistente.');
        const base=aprobada.producto;
        exigir(base && base.tieneVariantes===data.tieneVariantes,'conflicto','La estructura publicada cambió.');
        if(base.tieneVariantes) {
          const ids=Object.keys(aprobada.variantes);
          exigir(ids.length>0 && ids.length<=100 && ids.every(id),'conflicto','Variantes aprobadas inválidas.');
          const saldos=await tx.getAll(...ids.map(key=>product.collection('variantes').doc(key)));
          const states:string[]=[];
          for(const saldo of saldos) {
            exigir(saldo.exists,'conflicto','Falta una variante aprobada; revisar la publicación.');
            const disponibilidad=estado(policy,saldo.id===d.varianteId?stockNuevo:saldo.data()!.stock);
            if(!disponibilidad)continue;
            states.push(disponibilidad);
            publicarVariantes.push({id:saldo.id,data:{...aprobada.variantes[saldo.id],estadoDisponibilidad:disponibilidad}});
          }
          if(states.length) proyeccion={...base,estadoDisponibilidad:states.includes('disponible')?'disponible':states.includes('a_pedido')?'a_pedido':'agotado'};
        } else {
          const disponibilidad=estado(policy,stockNuevo);
          if(disponibilidad) proyeccion={...base,estadoDisponibilidad:disponibilidad};
        }
      }
      const now=FieldValue.serverTimestamp();
      if(d.varianteId) tx.update(target,{stock:stockNuevo,actualizadoEn:now});
      tx.update(product,{actualizadoEn:now,...(!d.varianteId?{stock:stockNuevo}:{}),...(requiereRepublicar?{estadoPublicacion:'archivado'}:{})});
      const nuevas=new Set(publicarVariantes.map(v=>v.id));
      for(const variant of publicVariants.docs) if(!nuevas.has(variant.id))tx.delete(variant.ref);
      for(const variant of publicarVariantes)tx.set(pub.collection('variantes').doc(variant.id),{...variant.data,actualizadoEn:now});
      if(proyeccion)tx.set(pub,{...proyeccion,actualizadoEn:now});else tx.delete(pub);
      const actualizacionCatalogo=sincronizar?(proyeccion?'actualizado':'oculto'):(requiereRepublicar?'requiere_republicar':'sin_publicacion');
      const resultado={operacionId:d.operacionId as string,stockAnterior,stockNuevo,requiereRepublicar,actualizacionCatalogo};
      tx.create(history,{productoId:d.productoId,...(d.varianteId?{varianteId:d.varianteId}:{}),cantidad:d.cantidad,motivo:d.motivo,...(d.nota?{nota:d.nota}:{}),stockAnterior,stockNuevo,realizadoPor:identidad.uid,creadoEn:now,retiradoDelCatalogo:publicProduct.exists && !proyeccion,actualizacionCatalogo});
      tx.create(receipt,{firma,resultado,creadoEn:now});
      return resultado;
    });
  };
}
