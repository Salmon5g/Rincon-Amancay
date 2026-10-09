import { porCuenta } from './serializacion-cuentas.ts';
import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import type { Firestore, DocumentData } from 'firebase-admin/firestore';
import type { Auth } from 'firebase-admin/auth';
import { ErrorOperacion, version } from './publicacion.ts';

function exigir(ok: unknown, code: string, message: string): asserts ok {
  if (!ok) throw new ErrorOperacion(code, message);
}
function id(v: unknown) {return typeof v==='string' && /^[a-zA-Z0-9_-]{1,128}$/.test(v);}
function admin(d: DocumentData | undefined) {return d?.estado==='activo' && Array.isArray(d.roles) && d.roles.includes('administrador');}
type Resultado = {uid: string; tiendaId: string; operacionId: string; estado: 'completada'; autenticacion: 'deshabilitada' | 'ausente'};

export function crearDesactivacion(db: Firestore, auth: Pick<Auth,'updateUser'|'revokeRefreshTokens'>) {
  return async (identidad: {uid:string}, input: unknown): Promise<Resultado> => {
    exigir(identidad && id(identidad.uid),'no-autenticado','Identidad verificada requerida.');
    exigir(input && typeof input==='object' && !Array.isArray(input),'datos-invalidos','Solicitud inválida.');
    const d=input as Record<string,unknown>;
    const keys=['uidDestino','operacionId','versionEsperada','motivo'];
    exigir(Object.keys(d).length===keys.length && keys.every(k=>Object.hasOwn(d,k)),'datos-invalidos','Campos ausentes o no permitidos.');
    exigir(id(d.uidDestino) && id(d.operacionId),'datos-invalidos','IDs inválidos.');
    exigir(typeof d.versionEsperada==='string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(d.versionEsperada),'datos-invalidos','Versión de accesos requerida.');
    exigir(typeof d.motivo==='string' && d.motivo.trim().length>0 && d.motivo.length<=500,'datos-invalidos','Motivo requerido, de hasta 500 caracteres.');
    exigir(identidad.uid!==d.uidDestino,'sin-permiso','No se permite desactivar la propia cuenta.');
    const uid=d.uidDestino as string, operacionId=d.operacionId as string;
    return porCuenta(db, uid, identidad.uid, async () => {
      const firma=createHash('sha256').update(JSON.stringify([uid,operacionId,d.versionEsperada,d.motivo])).digest('hex');
      const actor=db.doc(`accesos/${identidad.uid}`),target=db.doc(`accesos/${uid}`);
      const receipt=db.doc(`desactivaciones/${identidad.uid}_${operacionId}`);
      const phase=await db.runTransaction(async tx=>{
        const [who,previous,access]=await tx.getAll(actor,receipt,target);
        exigir(admin(who.data()),'sin-permiso','Se requiere administrador activo.');
        if(previous.exists) {
          exigir(previous.data()!.firma===firma,'id-reutilizado','operacionId corresponde a otra solicitud.');
          if(previous.data()!.estado!=='completada') exigir(access.data()?.operacionCuentaPendiente===receipt.path && access.data()?.estado==='desactivado' && access.data()?.tiendaId===previous.data()!.tiendaId && !access.data()?.roles?.includes('administrador'),'conflicto','La cuenta cambió durante la operación pendiente; requiere revisión.');
          return previous.data()!;
        }
        exigir(access.exists,'no-encontrado','No existe el acceso de la cuenta destino.');
        const a=access.data()!;
        exigir(!a.operacionCuentaPendiente,'conflicto','Completar primero la operación de cuenta pendiente.');
        exigir(Array.isArray(a.roles) && a.roles.includes('emprendedora') && !a.roles.includes('administrador'),'sin-permiso','Esta operación solo desactiva emprendedoras sin rol administrador.');
        exigir(['activo','desactivado'].includes(a.estado) && id(a.tiendaId),'conflicto','Acceso o asignación de tienda inconsistente.');
        exigir(version(a)===d.versionEsperada,'conflicto','El acceso cambió; volver a leer.');
        const shop=db.doc(`tiendasPrivadas/${a.tiendaId}`),pub=db.doc(`tiendasPublicas/${a.tiendaId}`);
        const [store,profile,publicStore]=await tx.getAll(shop,db.doc(`emprendedoras/${uid}`),pub);
        exigir(store.data()?.propietarioUid===uid && profile.data()?.tiendaId===a.tiendaId,'conflicto','La propiedad de la tienda no coincide.');
        const now=FieldValue.serverTimestamp();
        const state={firma,uidDestino:uid,tiendaId:a.tiendaId,operacionId,motivo:d.motivo,realizadaPor:identidad.uid,estado:'pendienteAuth',creadoEn:now,actualizadoEn:now};
        tx.update(target,{estado:'desactivado',operacionCuentaPendiente:receipt.path,actualizadoEn:now});
        if(publicStore.exists) tx.update(pub,{habilitada:false,actualizadoEn:now});
        tx.create(receipt,state);
        return state;
      });
      if(phase.estado==='completada') return (phase as DocumentData).resultado as Resultado;

      // Auth no participa en la transacción. Si falla, el bloqueo Firestore
      // permanece y este comprobante permite reintentar sin reabrir la tienda.
      let autenticacion: Resultado['autenticacion']='deshabilitada';
      try {
        await auth.updateUser(uid,{disabled:true});
        await auth.revokeRefreshTokens(uid);
      } catch(error) {
        if((error as {code?:string}).code==='auth/user-not-found') autenticacion='ausente';
        else throw new ErrorOperacion('pendiente','La gestión y el catálogo ya están bloqueados. Falta completar Authentication; reintentar la misma solicitud.');
      }
      return db.runTransaction(async tx=>{
        const [previous,access]=await tx.getAll(receipt,target);
        exigir(access.data()?.operacionCuentaPendiente===receipt.path && previous.data()?.firma===firma && access.data()?.estado==='desactivado' && access.data()?.tiendaId===phase.tiendaId,'conflicto','El estado cambió durante la desactivación; requiere revisión administrativa.');
        if(previous.data()!.estado==='completada') return previous.data()!.resultado as Resultado;
        const resultado: Resultado={uid,tiendaId:phase.tiendaId,operacionId,estado:'completada',autenticacion};
        tx.update(target,{operacionCuentaPendiente:FieldValue.delete(),actualizadoEn:FieldValue.serverTimestamp()});
        tx.update(receipt,{estado:'completada',resultado,actualizadoEn:FieldValue.serverTimestamp()});
        return resultado;
      });
    });
  };
}
