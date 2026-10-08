import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import type { Firestore } from 'firebase-admin/firestore';
import type { Auth } from 'firebase-admin/auth';
import { ErrorOperacion } from './publicacion.ts';

function exigir(ok: unknown, code: string, message: string): asserts ok {
  if (!ok) throw new ErrorOperacion(code, message);
}
export type AltaEmprendedora = {
  operacionId: string; uidDestino: string; tiendaId: string; nombreMostrar: string;
  nombreTienda: string; descripcion: string; sectorId: string; tipoEmprendimientoId: string;
  mostrarPrecios: boolean; historialVentasActivo: boolean; formaContacto: string;
};
export function validarAlta(input: unknown): AltaEmprendedora {
  exigir(input && typeof input === 'object' && !Array.isArray(input), 'datos-invalidos', 'Solicitud inválida.');
  const d = input as Record<string, unknown>;
  const ids = ['operacionId', 'uidDestino', 'tiendaId', 'sectorId', 'tipoEmprendimientoId'];
  const texts = { nombreMostrar: 100, nombreTienda: 120, descripcion: 2000 };
  const keys = [...ids, ...Object.keys(texts), 'mostrarPrecios', 'historialVentasActivo', 'formaContacto'];
  exigir(Object.keys(d).length === keys.length && Object.keys(d).every(k => keys.includes(k)), 'datos-invalidos', 'Campos ausentes o no permitidos. No enviar roles ni contraseñas.');
  for (const k of ids) exigir(typeof d[k] === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(d[k]), 'datos-invalidos', `Identificador inválido: ${k}.`);
  for (const [k, max] of Object.entries(texts)) exigir(typeof d[k] === 'string' && d[k].trim().length > 0 && d[k].length <= max, 'datos-invalidos', `Texto inválido: ${k}.`);
  exigir(typeof d.mostrarPrecios === 'boolean' && typeof d.historialVentasActivo === 'boolean', 'datos-invalidos', 'Preferencias booleanas requeridas.');
  exigir(['whatsapp', 'formulario', 'ambos'].includes(d.formaContacto as string), 'datos-invalidos', 'Forma de contacto inválida.');
  return Object.fromEntries(keys.map(k => [k, d[k]])) as AltaEmprendedora;
}

export function crearCuentas(db: Firestore, auth: Auth) {
  return async (identidad: {uid: string}, input: unknown) => {
    exigir(identidad && typeof identidad.uid === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');
    const d = validarAlta(input);
    const adminRef = db.doc(`accesos/${identidad.uid}`);
    const esAdmin = (data: FirebaseFirestore.DocumentData | undefined) => data?.estado === 'activo' && Array.isArray(data.roles) && data.roles.includes('administrador');
    // Comprobar antes de consultar Auth evita revelar cuentas a no administradores.
    exigir(esAdmin((await adminRef.get()).data()), 'sin-permiso', 'Se requiere administrador activo.');
    exigir(identidad.uid !== d.uidDestino, 'sin-permiso', 'El alta no permite asignarse una tienda a sí mismo.');
    try {
      const account = await auth.getUser(d.uidDestino);
      exigir(!account.disabled && Boolean(account.email), 'conflicto', 'La cuenta destino debe estar habilitada y tener correo.');
    } catch (error) {
      if (error instanceof ErrorOperacion) throw error;
      if ((error as {code?: string}).code === 'auth/user-not-found') throw new ErrorOperacion('no-encontrado', 'La identidad destino no existe.');
      throw new ErrorOperacion('pendiente', 'No se pudo comprobar la cuenta destino.');
    }
    const firma = createHash('sha256').update(JSON.stringify(d)).digest('hex');
    const receiptRef = db.doc(`operacionesCuentas/${identidad.uid}_${d.operacionId}`);
    const userRef = db.doc(`usuarios/${d.uidDestino}`);
    const accessRef = db.doc(`accesos/${d.uidDestino}`);
    const profileRef = db.doc(`emprendedoras/${d.uidDestino}`);
    const storeRef = db.doc(`tiendasPrivadas/${d.tiendaId}`);
    const configRef = storeRef.collection('configuracion').doc('general');
    return db.runTransaction(async tx => {
      const [admin, receipt, user, access, profile, store, config, publicStore, sector, type] = await tx.getAll(
        adminRef, receiptRef, userRef, accessRef, profileRef, storeRef, configRef,
        db.doc(`tiendasPublicas/${d.tiendaId}`), db.doc(`sectores/${d.sectorId}`), db.doc(`tiposEmprendimiento/${d.tipoEmprendimientoId}`),
      );
      exigir(esAdmin(admin.data()), 'sin-permiso', 'Se requiere administrador activo.');
      if (receipt.exists) {
        exigir(receipt.data()!.firma === firma, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
        return receipt.data()!.resultado as {uid: string; tiendaId: string; operacionId: string};
      }
      exigir(!profile.exists && !store.exists && !config.exists && !publicStore.exists, 'conflicto', 'La cuenta o tienda ya tiene una asignación. No se sobrescribió.');
      const products = await tx.get(storeRef.collection('productos').limit(1));
      const sales = await tx.get(storeRef.collection('ventas').limit(1));
      exigir(products.empty && sales.empty, 'conflicto', 'La ruta conserva datos de una tienda anterior.');
      const a = access.data();
      exigir(!access.exists || (a?.estado === 'activo' && Array.isArray(a.roles) && a.roles.length === 1 && a.roles[0] === 'comprador' && !('tiendaId' in a)), 'conflicto', 'El acceso existente no admite esta alta.');
      exigir(sector.data()?.activo === true && type.data()?.activo === true, 'datos-invalidos', 'Sector o tipo de emprendimiento inexistente/inactivo.');
      const now = FieldValue.serverTimestamp();
      const dates = {creadoEn: now, actualizadoEn: now};
      if (!user.exists) tx.create(userRef, {nombreMostrar: d.nombreMostrar, ...dates});
      if (access.exists) tx.update(accessRef, {roles: ['comprador', 'emprendedora'], tiendaId: d.tiendaId, actualizadoEn: now});
      else tx.create(accessRef, {roles: ['emprendedora'], estado: 'activo', tiendaId: d.tiendaId, ...dates});
      tx.create(profileRef, {tiendaId: d.tiendaId, creadaPor: identidad.uid, ...dates});
      tx.create(storeRef, {propietarioUid: d.uidDestino, nombre: d.nombreTienda, descripcion: d.descripcion,
        sectorId: d.sectorId, tipoEmprendimientoId: d.tipoEmprendimientoId, estadoPublicacion: 'borrador', ...dates});
      tx.create(configRef, {mostrarPrecios: d.mostrarPrecios, historialVentasActivo: d.historialVentasActivo, formaContacto: d.formaContacto, ...dates});
      const resultado = {uid: d.uidDestino, tiendaId: d.tiendaId, operacionId: d.operacionId};
      tx.create(receiptRef, {firma, resultado, creadaPor: identidad.uid, creadoEn: now});
      return resultado;
    });
  };
}
