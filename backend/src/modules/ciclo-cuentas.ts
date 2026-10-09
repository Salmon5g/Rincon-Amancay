import { createHash } from 'node:crypto';
import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import type { Firestore, DocumentData } from 'firebase-admin/firestore';
import type { Auth } from 'firebase-admin/auth';
import { crearCuentas, validarAlta } from './cuentas.ts';
import { ErrorOperacion, version } from './publicacion.ts';
import { porCuenta } from './serializacion-cuentas.ts';

export const accionesCuentas = ['registrarComprador', 'invitarEmprendedora', 'consultarInvitacion', 'aceptarInvitacion', 'cancelarInvitacion', 'reactivarEmprendedora'] as const;
export type AccionCuenta = typeof accionesCuentas[number];
function exigir(ok: unknown, code: string, mensaje: string): asserts ok {if(!ok) throw new ErrorOperacion(code,mensaje);}
function id(v: unknown): v is string {return typeof v==='string' && /^[a-zA-Z0-9_-]{1,128}$/.test(v);}
function admin(d: DocumentData | undefined) {return d?.estado==='activo' && Array.isArray(d.roles) && d.roles.includes('administrador');}
function campos(input: unknown, keys: string[]) {
  exigir(input && typeof input==='object' && !Array.isArray(input),'datos-invalidos','Solicitud inválida.');
  const d=input as Record<string,unknown>;
  exigir(Object.keys(d).length===keys.length && keys.every(k=>Object.hasOwn(d,k)),'datos-invalidos','Campos ausentes o no permitidos.');
  return d;
}
function huella(input: unknown) {return createHash('sha256').update(JSON.stringify(input)).digest('hex');}

export function crearCicloCuentas(db: Firestore, auth: Auth) {
  const alta=crearCuentas(db,auth);
  async function verificada(uid: string) {
    const user=await auth.getUser(uid);
    exigir(!user.disabled && user.emailVerified && user.email,'sin-permiso','Verificar el correo antes de continuar.');
    return user.email.toLowerCase();
  }
  return async (accion: AccionCuenta, identidad: {uid:string}, input: unknown): Promise<Record<string,unknown>> => {
    exigir(id(identidad?.uid),'no-autenticado','Identidad requerida.');
    const uid=identidad.uid, actor=db.doc(`accesos/${uid}`);
    if(accion==='registrarComprador') {
      const d=campos(input,['nombreMostrar']);
      exigir(typeof d.nombreMostrar==='string' && d.nombreMostrar.trim().length>0 && d.nombreMostrar.length<=100,'datos-invalidos','Nombre requerido, hasta 100 caracteres.');
      const nombreMostrar=d.nombreMostrar.trim();
      await verificada(uid);
      return db.runTransaction(async tx=>{
        const perfil=db.doc(`usuarios/${uid}`);
        const [access,user]=await tx.getAll(actor,perfil);
        if(access.exists) {
          exigir(access.data()!.estado==='activo' && !access.data()!.operacionCuentaPendiente,'conflicto','La cuenta requiere revisión administrativa.');
          exigir(access.data()!.roles?.includes('comprador'),'conflicto','La cuenta ya tiene otro tipo de acceso.');
          return {uid,estado:'activo'};
        }
        const now=FieldValue.serverTimestamp();
        if(!user.exists) tx.create(perfil,{nombreMostrar,creadoEn:now,actualizadoEn:now});
        tx.create(actor,{roles:['comprador'],estado:'activo',creadoEn:now,actualizadoEn:now});
        return {uid,estado:'activo'};
      });
    }
    if(accion==='invitarEmprendedora') {
      const d=campos(input,['operacionId','correo','nombreMostrar','nombreTienda','descripcion','sectorId','tipoEmprendimientoId','mostrarPrecios','historialVentasActivo','formaContacto']);
      exigir(id(d.operacionId) && typeof d.correo==='string' && d.correo.length<=254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(d.correo),'datos-invalidos','Correo u operación inválidos.');
      const correo=d.correo.toLowerCase(), invitacionId=huella([uid,d.operacionId]);
      const {correo: _correo,...resto}=d;
      const solicitud=validarAlta({...resto,uidDestino:'pendiente',tiendaId:`i_${invitacionId}`});
      const firma=huella([correo,solicitud]), ref=db.doc(`invitaciones/${invitacionId}`);
      return db.runTransaction(async tx=>{
        const [who,previous,sector,tipo]=await tx.getAll(actor,ref,db.doc(`sectores/${solicitud.sectorId}`),db.doc(`tiposEmprendimiento/${solicitud.tipoEmprendimientoId}`));
        exigir(admin(who.data()),'sin-permiso','Se requiere administrador activo.');
        if(previous.exists) {
          exigir(previous.data()!.firma===firma,'id-reutilizado','operacionId corresponde a otra invitación.');
          return {invitacionId,tiendaId:solicitud.tiendaId,estado:previous.data()!.estado,venceEn:previous.data()!.venceEn.toDate().toISOString()};
        }
        exigir(sector.data()?.activo===true && tipo.data()?.activo===true,'datos-invalidos','Sector o tipo inactivo.');
        const venceEn=Timestamp.fromMillis(Date.now()+7*24*60*60*1000);
        // El correo no es público. En desarrollo el administrador entrega el ID
        // manualmente. No se envían mensajes ni se crean contraseñas en servidor.
        tx.create(ref,{correo,alta:solicitud,firma,creadaPor:uid,estado:'pendiente',creadoEn:FieldValue.serverTimestamp(),venceEn});
        return {invitacionId,tiendaId:solicitud.tiendaId,estado:'pendiente',venceEn:venceEn.toDate().toISOString()};
      });
    }
    if(accion==='consultarInvitacion' || accion==='aceptarInvitacion' || accion==='cancelarInvitacion') {
      const d=campos(input,['invitacionId']);
      exigir(id(d.invitacionId),'datos-invalidos','ID de invitación inválido.');
      const ref=db.doc(`invitaciones/${d.invitacionId}`);
      if(accion==='cancelarInvitacion') return db.runTransaction(async tx=>{
        const [who,invite]=await tx.getAll(actor,ref);
        exigir(admin(who.data()),'sin-permiso','Se requiere administrador activo.');
        exigir(invite.exists,'no-encontrado','Invitación no encontrada.');
        exigir(['pendiente','cancelada'].includes(invite.data()!.estado),'conflicto','No se puede cancelar una invitación aceptada.');
        if(invite.data()!.estado==='pendiente') tx.update(ref,{estado:'cancelada',canceladaPor:uid,canceladaEn:FieldValue.serverTimestamp()});
        return {invitacionId:d.invitacionId,estado:'cancelada'};
      });
      const correo=await verificada(uid), invite=(await ref.get()).data();
      exigir(invite && invite.correo===correo,'sin-permiso','Invitación no disponible para esta cuenta.');
      if(accion==='consultarInvitacion') return {invitacionId:d.invitacionId,nombreTienda:invite.alta.nombreTienda,estado:invite.estado==='pendiente' && invite.venceEn.toMillis()<=Date.now() ? 'vencida' : invite.estado,venceEn:invite.venceEn.toDate().toISOString()};
      return alta({uid:invite.creadaPor},{...invite.alta,uidDestino:uid},{id:d.invitacionId,uidAcepta:uid});
    }
    if(accion==='reactivarEmprendedora') {
      const d=campos(input,['uidDestino','operacionId','versionEsperada','motivo']);
      exigir(id(d.uidDestino) && id(d.operacionId),'datos-invalidos','IDs inválidos.');
      exigir(typeof d.versionEsperada==='string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(d.versionEsperada),'datos-invalidos','Versión de accesos requerida.');
      exigir(typeof d.motivo==='string' && d.motivo.trim().length>0 && d.motivo.length<=500,'datos-invalidos','Motivo requerido, hasta 500 caracteres.');
      exigir(uid!==d.uidDestino,'sin-permiso','No se permite reactivar la propia cuenta.');
      const destino=d.uidDestino;
      return porCuenta(db,destino,uid,async()=>{
        const target=db.doc(`accesos/${destino}`),receipt=db.doc(`reactivaciones/${uid}_${d.operacionId}`);
        const firma=huella([destino,d.operacionId,d.versionEsperada,d.motivo]);
        const phase=await db.runTransaction(async tx=>{
          const [who,previous,access]=await tx.getAll(actor,receipt,target);
          exigir(admin(who.data()),'sin-permiso','Se requiere administrador activo.');
          if(previous.exists) {
            exigir(previous.data()!.firma===firma,'id-reutilizado','operacionId corresponde a otra solicitud.');
            if(previous.data()!.estado!=='completada') exigir(access.data()?.estado==='desactivado' && access.data()?.operacionCuentaPendiente===receipt.path,'conflicto','La cuenta cambió durante la operación pendiente.');
            return previous.data()!;
          }
          const a=access.data();
          exigir(a?.estado==='desactivado' && !a.operacionCuentaPendiente,'conflicto','La cuenta debe estar desactivada sin operaciones pendientes.');
          exigir(Array.isArray(a.roles) && a.roles.includes('emprendedora') && !a.roles.includes('administrador') && id(a.tiendaId),'sin-permiso','Solo se reactivan emprendedoras sin rol administrador.');
          exigir(version(a)===d.versionEsperada,'conflicto','El acceso cambió; volver a leer.');
          const pub=db.doc(`tiendasPublicas/${a.tiendaId}`);
          const [shop,profile,publicShop]=await tx.getAll(db.doc(`tiendasPrivadas/${a.tiendaId}`),db.doc(`emprendedoras/${destino}`),pub);
          exigir(shop.data()?.propietarioUid===destino && profile.data()?.tiendaId===a.tiendaId,'conflicto','La propiedad de la tienda no coincide.');
          const now=FieldValue.serverTimestamp();
          const state={firma,uidDestino:destino,tiendaId:a.tiendaId,estado:'pendienteAuth',operacionId:d.operacionId,motivo:d.motivo,solicitud:d,realizadaPor:uid,creadoEn:now};
          tx.update(target,{operacionCuentaPendiente:receipt.path,actualizadoEn:now});
          if(publicShop.exists) tx.update(pub,{habilitada:false,actualizadoEn:now});
          tx.create(receipt,state);return state;
        });
        if(phase.estado==='completada') return (phase as DocumentData).resultado;
        try {
          await auth.revokeRefreshTokens(destino);
          await auth.updateUser(destino,{disabled:false});
        } catch {
          throw new ErrorOperacion('pendiente','La gestión sigue bloqueada. Revisar Authentication y reintentar la misma solicitud. No se recrean identidades eliminadas.');
        }
        return db.runTransaction(async tx=>{
          const [who,previous,access]=await tx.getAll(actor,receipt,target);
          exigir(admin(who.data()),'sin-permiso','Se requiere administrador activo.');
          exigir(previous.data()?.firma===firma && access.data()?.operacionCuentaPendiente===receipt.path && access.data()?.estado==='desactivado' && access.data()?.tiendaId===phase.tiendaId && !access.data()?.roles?.includes('administrador'),'conflicto','La cuenta cambió durante la reactivación.');
          const resultado={uid:destino,tiendaId:phase.tiendaId,operacionId:d.operacionId,estado:'completada',autenticacion:'habilitada'};
          const now=FieldValue.serverTimestamp();
          tx.update(target,{estado:'activo',sesionesRevocadasHasta:Math.floor(Date.now()/1000),operacionCuentaPendiente:FieldValue.delete(),actualizadoEn:now});
          tx.update(receipt,{estado:'completada',resultado,actualizadoEn:now});return resultado;
        });
      });
    }
    throw new ErrorOperacion('datos-invalidos','Operación desconocida.');
  };
}
