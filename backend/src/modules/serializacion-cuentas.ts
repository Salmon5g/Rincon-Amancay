import { randomUUID } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import type { Firestore } from 'firebase-admin/firestore';
import { ErrorOperacion } from './publicacion.ts';

const pendientes=new Map<string,Promise<unknown>>();
// Sin vencimiento automático: Auth no admite un fencing token que impida a un
// proceso antiguo modificar una cuenta tras perder una concesión temporal.
export async function conBloqueoPersistente<T>(db:Firestore,uid:string,actorUid:string,ejecutar:()=>Promise<T>):Promise<T> {
  if(!/^[a-zA-Z0-9_-]{1,128}$/.test(uid) || !/^[a-zA-Z0-9_-]{1,128}$/.test(actorUid))throw new ErrorOperacion('datos-invalidos','Identificador inválido.');
  const ref=db.doc(`ejecucionesCuentas/${uid}`),ejecutorId=randomUUID();
  await db.runTransaction(async tx=>{
    const [actor,lock]=await tx.getAll(db.doc(`accesos/${actorUid}`),ref);
    if(actor.data()?.estado!=='activo' || !actor.data()?.roles?.includes('administrador'))throw new ErrorOperacion('sin-permiso','Se requiere administrador activo.');
    if(lock.exists)throw new ErrorOperacion('pendiente','Otra ejecución conserva la cuenta reservada. Si terminó inesperadamente, requiere revisión técnica; no forzar un reintento.');
    tx.create(ref,{ejecutorId,actorUid,creadoEn:FieldValue.serverTimestamp(),revision:process.env.K_REVISION??'local',pid:process.pid});
  });
  try {return await ejecutar();}
  finally {
    await db.runTransaction(async tx=>{
      const lock=await tx.get(ref);
      if(lock.data()?.ejecutorId!==ejecutorId)throw new ErrorOperacion('pendiente','La reserva de ejecución cambió; requiere revisión técnica.');
      tx.delete(ref);
    });
  }
}
export async function porCuenta<T>(db:Firestore,uid:string,actorUid:string,ejecutar:()=>Promise<T>):Promise<T> {
  const anterior=pendientes.get(uid)??Promise.resolve();
  const actual=anterior.catch(()=>undefined).then(()=>conBloqueoPersistente(db,uid,actorUid,ejecutar));
  pendientes.set(uid,actual);
  try {return await actual;} finally {if(pendientes.get(uid)===actual)pendientes.delete(uid);}
}
