import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import type { DocumentData, Firestore } from 'firebase-admin/firestore';
import type { Auth } from 'firebase-admin/auth';
import { ErrorOperacion, version } from './publicacion.ts';

export const accionesAdministracion = ['leerConfiguracionNavegacion', 'guardarConfiguracionNavegacion', 'listarAdministradores', 'agregarAdministrador', 'quitarAdministrador'] as const;
export type AccionAdministracion = typeof accionesAdministracion[number];
export type ModoNavegacion = 'permitir' | 'invitar' | 'exigir';

function exigir(ok: unknown, code: string, message: string): asserts ok { if (!ok) throw new ErrorOperacion(code, message); }
function objeto(value: unknown): Record<string, unknown> { exigir(value !== null && typeof value === 'object' && !Array.isArray(value), 'datos-invalidos', 'Se requiere un objeto.'); return value as Record<string, unknown>; }
function exactos(data: Record<string, unknown>, keys: string[]) { exigir(keys.every(key => Object.hasOwn(data, key)) && Object.keys(data).every(key => keys.includes(key)), 'datos-invalidos', 'Campos ausentes o no permitidos.'); }
function id(value: unknown): value is string { return typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value); }
function admin(data: DocumentData | undefined) { return data?.estado === 'activo' && Array.isArray(data.roles) && data.roles.includes('administrador'); }
function versionEsperada(value: unknown) { exigir(typeof value === 'string' && /^(?:0:0|[0-9]{1,12}:[0-9]{1,9})$/.test(value), 'datos-invalidos', 'Se requiere la versión vigente.'); return value; }
function firma(value: unknown) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }
function correo(value: unknown) {
  exigir(typeof value === 'string' && value.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()), 'datos-invalidos', 'Escribe un correo válido.');
  return value.trim().toLowerCase();
}

export function crearAdministracionPlataforma(db: Firestore, auth: Auth) {
  return async (accion: AccionAdministracion, identidad: { uid: string }, input: unknown) => {
    exigir(accionesAdministracion.includes(accion), 'datos-invalidos', 'Acción administrativa desconocida.');
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');
    const data = objeto(input);
    const actorRef = db.doc(`accesos/${identidad.uid}`);

    if (accion === 'leerConfiguracionNavegacion') {
      exactos(data, []);
      exigir(admin((await actorRef.get()).data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      const snapshot = await db.doc('configuracionAplicacion/navegacion').get();
      const actual = snapshot.data();
      return {
        modo: (['permitir', 'invitar', 'exigir'].includes(actual?.modo) ? actual!.modo : 'permitir') as ModoNavegacion,
        versionEsperada: snapshot.exists ? version(actual!) : '0:0',
        actualizadoEn: actual?.actualizadoEn?.toDate?.().toISOString?.() ?? null,
      };
    }

    if (accion === 'listarAdministradores') {
      exactos(data, []);
      exigir(admin((await actorRef.get()).data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      const snapshot = await db.collection('accesos').where('roles', 'array-contains', 'administrador').limit(101).get();
      exigir(snapshot.size <= 100, 'limite-local', 'Hay más de 100 cuentas administradoras; requiere paginación.');
      const cuentas = await Promise.all(snapshot.docs.map(async doc => {
        const access = doc.data();
        const identityPromise = auth.getUser(doc.id).catch(error => {
          if ((error as { code?: string }).code === 'auth/user-not-found') return null;
          throw new ErrorOperacion('pendiente', 'No se pudo comprobar una cuenta en Authentication.');
        });
        const [profile, identity] = await Promise.all([db.doc(`usuarios/${doc.id}`).get(), identityPromise]);
        return {
          uid: doc.id,
          correo: identity?.email ?? '',
          nombreMostrar: typeof profile.data()?.nombreMostrar === 'string' ? profile.data()!.nombreMostrar : identity?.displayName ?? '',
          estado: access.estado === 'activo' ? 'activo' : 'desactivado',
          roles: Array.isArray(access.roles) ? access.roles.filter((role): role is string => typeof role === 'string') : [],
          consistente: Boolean(identity && identity.emailVerified && !identity.disabled && access.estado === 'activo'),
          versionEsperada: access.actualizadoEn ? version(access) : '',
        };
      }));
      cuentas.sort((a, b) => a.nombreMostrar.localeCompare(b.nombreMostrar, 'es-CL') || a.correo.localeCompare(b.correo, 'es-CL'));
      return { administradores: cuentas };
    }

    if (accion === 'guardarConfiguracionNavegacion') {
      exactos(data, ['operacionId', 'versionEsperada', 'modo']);
      exigir(id(data.operacionId), 'datos-invalidos', 'ID de operación inválido.');
      const expected = versionEsperada(data.versionEsperada);
      exigir(data.modo === 'permitir' || data.modo === 'invitar' || data.modo === 'exigir', 'datos-invalidos', 'Modo de navegación inválido.');
      const request = { operacionId: data.operacionId, versionEsperada: expected, modo: data.modo as ModoNavegacion };
      const requestSignature = firma(request);
      const configRef = db.doc('configuracionAplicacion/navegacion');
      const receiptRef = db.doc(`operacionesAdministracion/${identidad.uid}_${request.operacionId}`);
      const auditRef = db.doc(`auditoriaAdministracion/${identidad.uid}_${request.operacionId}`);
      return db.runTransaction(async tx => {
        const [actor, receipt, config] = await tx.getAll(actorRef, receiptRef, configRef);
        exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) {
          exigir(receipt.data()!.firma === requestSignature, 'id-reutilizado', 'operacionId ya corresponde a otros datos.');
          return receipt.data()!.resultado as Record<string, unknown>;
        }
        const current = config.data();
        exigir((config.exists ? version(current!) : '0:0') === request.versionEsperada, 'conflicto', 'La configuración cambió. Actualiza antes de guardar.');
        const currentMode = current?.modo ?? 'permitir';
        const now = FieldValue.serverTimestamp();
        const result = { modo: request.modo, estado: 'actualizada' };
        tx.set(configRef, { modo: request.modo, actualizadoEn: now, ...(config.exists ? {} : { creadoEn: now }) }, { merge: true });
        tx.create(receiptRef, { firma: requestSignature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: configRef.path, antes: { modo: currentMode }, despues: { modo: request.modo }, creadoEn: now });
        return result;
      });
    }

    if (accion === 'agregarAdministrador') {
      exactos(data, ['operacionId', 'correo']);
      exigir(id(data.operacionId), 'datos-invalidos', 'ID de operación inválido.');
      const address = correo(data.correo);
      let identity;
      try { identity = await auth.getUserByEmail(address); }
      catch (error) {
        if ((error as { code?: string }).code === 'auth/user-not-found') throw new ErrorOperacion('no-encontrado', 'Primero debe existir una cuenta de Authentication con ese correo.');
        throw new ErrorOperacion('pendiente', 'No se pudo comprobar la identidad en Authentication.');
      }
      exigir(identity.emailVerified && !identity.disabled, 'conflicto', 'La cuenta debe tener el correo verificado y estar habilitada en Authentication.');
      exigir(identity.uid !== identidad.uid, 'conflicto', 'No puedes volver a asignarte el rol administrador.');
      const targetRef = db.doc(`accesos/${identity.uid}`);
      const userRef = db.doc(`usuarios/${identity.uid}`);
      const entrepreneurRef = db.doc(`emprendedoras/${identity.uid}`);
      const operationId = data.operacionId as string;
      const receiptRef = db.doc(`operacionesAdministracion/${identidad.uid}_${operationId}`);
      const auditRef = db.doc(`auditoriaAdministracion/${identidad.uid}_${operationId}`);
      const requestSignature = firma({ accion, operationId, uid: identity.uid, correo: address });
      return db.runTransaction(async tx => {
        const [actor, receipt, access, profile, entrepreneur] = await tx.getAll(actorRef, receiptRef, targetRef, userRef, entrepreneurRef);
        exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) {
          exigir(receipt.data()!.firma === requestSignature, 'id-reutilizado', 'operacionId ya corresponde a otros datos.');
          return receipt.data()!.resultado as Record<string, unknown>;
        }
        const current = access.data();
        const roles = Array.isArray(current?.roles) ? current!.roles.filter((role: unknown): role is string => typeof role === 'string') : [];
        exigir(current?.estado !== 'desactivado', 'conflicto', 'No se puede promover una cuenta desactivada.');
        exigir(!roles.includes('administrador'), 'conflicto', 'La cuenta ya tiene el rol administrador.');
        exigir(!roles.includes('emprendedora') && !entrepreneur.exists, 'conflicto', 'No se puede asignar administrador a una cuenta vinculada como emprendedora.');
        exigir(roles.every(role => role === 'comprador'), 'conflicto', 'La cuenta tiene roles que requieren revisión antes de promoverla.');
        const nextRoles = [...roles, 'administrador'];
        const now = FieldValue.serverTimestamp();
        const result = { uid: identity.uid, correo: address, estado: 'activo' };
        if (access.exists) tx.update(targetRef, { estado: 'activo', roles: nextRoles, actualizadoEn: now });
        else tx.create(targetRef, { estado: 'activo', roles: nextRoles, creadoEn: now, actualizadoEn: now });
        if (profile.exists) tx.update(userRef, { nombreMostrar: profile.data()?.nombreMostrar ?? identity.displayName ?? address.split('@')[0], actualizadoEn: now });
        else tx.create(userRef, { nombreMostrar: identity.displayName ?? address.split('@')[0], creadoEn: now, actualizadoEn: now });
        tx.create(receiptRef, { firma: requestSignature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: identity.uid, antes: { roles, estado: current?.estado ?? null }, despues: { roles: nextRoles, estado: 'activo', correo: address }, creadoEn: now });
        return result;
      });
    }

    exactos(data, ['operacionId', 'uidDestino', 'versionEsperada']);
    exigir(id(data.operacionId) && id(data.uidDestino) && data.uidDestino !== identidad.uid, 'datos-invalidos', 'ID de operación o destino inválido.');
    const expected = versionEsperada(data.versionEsperada);
    exigir(expected !== '0:0', 'datos-invalidos', 'La cuenta requiere una versión vigente para quitar permisos.');
    const targetRef = db.doc(`accesos/${data.uidDestino}`);
    const operationId = data.operacionId as string;
    const receiptRef = db.doc(`operacionesAdministracion/${identidad.uid}_${operationId}`);
    const auditRef = db.doc(`auditoriaAdministracion/${identidad.uid}_${operationId}`);
    const requestSignature = firma({ accion, operationId, uid: data.uidDestino, versionEsperada: expected });
    return db.runTransaction(async tx => {
      const [actor, receipt, access] = await tx.getAll(actorRef, receiptRef, targetRef);
      const admins = await tx.get(db.collection('accesos').where('roles', 'array-contains', 'administrador').limit(101));
      exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      if (receipt.exists) {
        exigir(receipt.data()!.firma === requestSignature, 'id-reutilizado', 'operacionId ya corresponde a otros datos.');
        return receipt.data()!.resultado as Record<string, unknown>;
      }
      exigir(access.exists && admin(access.data()), 'conflicto', 'La cuenta ya no tiene el rol administrador activo.');
      const current = access.data()!;
      exigir(version(current) === expected, 'conflicto', 'La cuenta cambió; actualiza el listado antes de continuar.');
      exigir(admins.size <= 100, 'limite-local', 'No se puede comprobar con seguridad la cantidad de administradores.');
      const totalActivos = admins.docs.filter(doc => doc.data().estado === 'activo').length;
      exigir(totalActivos > 1, 'conflicto', 'Debe quedar al menos una cuenta administradora activa.');
      const roles = (current.roles as unknown[]).filter(role => role !== 'administrador');
      const now = FieldValue.serverTimestamp();
      const result = { uid: data.uidDestino, estado: 'sin-rol-administrador', roles };
      tx.update(targetRef, { roles, actualizadoEn: now });
      tx.create(receiptRef, { firma: requestSignature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
      tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: data.uidDestino, antes: { roles: current.roles, estado: current.estado }, despues: { roles, estado: current.estado }, creadoEn: now });
      return result;
    });
  };
}
