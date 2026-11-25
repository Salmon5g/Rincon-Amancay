import { createHash } from 'node:crypto';
import { FieldPath, FieldValue, Timestamp } from 'firebase-admin/firestore';
import type { DocumentData, Firestore, Query } from 'firebase-admin/firestore';
import type { Auth } from 'firebase-admin/auth';
import { ErrorOperacion, version } from './publicacion.ts';

export const accionesAdministracion = ['leerConfiguracionNavegacion', 'guardarConfiguracionNavegacion', 'listarAdministradores', 'agregarAdministrador', 'quitarAdministrador', 'listarAuditoriaAdministracion', 'resumenAdministracion'] as const;
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

const etiquetasAuditoria: Record<string, { area: 'Cuentas' | 'Asociaciones' | 'Ferias y eventos'; accion: string }> = {
  altaEmprendedora: { area: 'Cuentas', accion: 'Alta de emprendedora' },
  invitarEmprendedora: { area: 'Cuentas', accion: 'Invitación de emprendedora' },
  cancelarInvitacion: { area: 'Cuentas', accion: 'Cancelación de invitación' },
  editarCuentaEmprendedora: { area: 'Cuentas', accion: 'Edición de cuenta emprendedora' },
  desactivarEmprendedora: { area: 'Cuentas', accion: 'Desactivación de cuenta emprendedora' },
  reactivarEmprendedora: { area: 'Cuentas', accion: 'Reactivación de cuenta emprendedora' },
  agregarAdministrador: { area: 'Cuentas', accion: 'Asignación de rol administrador' },
  quitarAdministrador: { area: 'Cuentas', accion: 'Retiro de rol administrador' },
  crearAsociacion: { area: 'Asociaciones', accion: 'Creación de asociación' },
  editarAsociacion: { area: 'Asociaciones', accion: 'Edición de asociación' },
  cambiarEstadoAsociacion: { area: 'Asociaciones', accion: 'Cambio de estado de asociación' },
  asignarEmprendedora: { area: 'Asociaciones', accion: 'Asignación de integrante' },
  quitarEmprendedora: { area: 'Asociaciones', accion: 'Retiro de integrante' },
  crearFeria: { area: 'Ferias y eventos', accion: 'Creación de feria' },
  editarFeria: { area: 'Ferias y eventos', accion: 'Edición de feria' },
  cambiarEstadoFeria: { area: 'Ferias y eventos', accion: 'Cambio de estado de feria' },
  invitarParticipanteFeria: { area: 'Ferias y eventos', accion: 'Invitación a participante' },
  agregarParticipanteFeria: { area: 'Ferias y eventos', accion: 'Ingreso de participante' },
  cancelarInvitacionFeria: { area: 'Ferias y eventos', accion: 'Cancelación de invitación' },
  retirarParticipanteFeria: { area: 'Ferias y eventos', accion: 'Retiro de participante' },
  registrarAsistenciaFeria: { area: 'Ferias y eventos', accion: 'Registro de asistencia' },
  enviarRecordatorioFeria: { area: 'Ferias y eventos', accion: 'Envío de recordatorio' },
  enviarAvisoConfirmadasFeria: { area: 'Ferias y eventos', accion: 'Aviso a participantes confirmadas' },
};

type NombresAuditoria = {
  cuentas: Map<string, string>;
  ferias: Map<string, string>;
  asociaciones: Map<string, string>;
  invitaciones: Map<string, string>;
};

function referenciasAuditoria(accion: string, documentoId: unknown) {
  const idDocumento = typeof documentoId === 'string' ? documentoId : '';
  const segmento = idDocumento.split('/');
  const area = etiquetasAuditoria[accion]?.area;
  const cuentas: string[] = [];
  const ferias: string[] = [];
  const asociaciones: string[] = [];
  const invitaciones: string[] = [];
  const participante = (valor: string | undefined) => {
    if (valor?.startsWith('emprendedora_')) cuentas.push(valor.slice('emprendedora_'.length));
    else if (valor?.startsWith('asociacion_')) asociaciones.push(valor.slice('asociacion_'.length));
  };
  if (area === 'Ferias y eventos') {
    if (segmento[0]) ferias.push(segmento[0]);
    if (segmento.length > 2) participante(segmento[2]);
  } else if (area === 'Asociaciones') {
    if (segmento[0]) asociaciones.push(segmento[0]);
    if (segmento[1]) cuentas.push(segmento[1]);
  } else if (accion === 'invitarEmprendedora' || accion === 'cancelarInvitacion') {
    if (idDocumento) invitaciones.push(idDocumento);
  } else if (idDocumento) {
    cuentas.push(idDocumento);
  }
  const valido = (valor: string) => /^[a-zA-Z0-9_-]{1,128}$/.test(valor);
  return { cuentas: cuentas.filter(valido), ferias: ferias.filter(valido), asociaciones: asociaciones.filter(valido), invitaciones: invitaciones.filter(valido) };
}

function objetivoAuditoria(accion: string, documentoId: unknown, nombres: NombresAuditoria): string {
  const idDocumento = typeof documentoId === 'string' ? documentoId : '';
  const segmento = idDocumento.split('/');
  const etiqueta = etiquetasAuditoria[accion];
  const participante = (valor: string | undefined) => {
    if (valor?.startsWith('emprendedora_')) return nombres.cuentas.get(valor.slice('emprendedora_'.length)) ?? 'una emprendedora';
    if (valor?.startsWith('asociacion_')) return nombres.asociaciones.get(valor.slice('asociacion_'.length)) ?? 'una asociación';
    return 'un participante';
  };
  if (etiqueta?.area === 'Ferias y eventos') {
    const feria = nombres.ferias.get(segmento[0] ?? '');
    const base = feria ? `Feria «${feria}»` : 'Una feria';
    return segmento.length > 2 ? `${base} · ${participante(segmento[2])}` : base;
  }
  if (etiqueta?.area === 'Asociaciones') {
    const asociacion = nombres.asociaciones.get(segmento[0] ?? '');
    const base = asociacion ? `Asociación «${asociacion}»` : 'Una asociación';
    return segmento.length > 1 ? `${base} · ${nombres.cuentas.get(segmento[1] ?? '') ?? 'una integrante'}` : base;
  }
  if (accion === 'invitarEmprendedora' || accion === 'cancelarInvitacion') {
    const correoInvitacion = nombres.invitaciones.get(idDocumento);
    return correoInvitacion ? `Invitación a ${correoInvitacion}` : 'Una invitación';
  }
  const cuenta = nombres.cuentas.get(idDocumento);
  return cuenta ? `Cuenta de ${cuenta}` : 'Una cuenta';
}

async function resolverNombresAuditoria(db: Firestore, auth: Auth, actores: string[], registros: DocumentData[]): Promise<NombresAuditoria> {
  const cuentas = new Set(actores);
  const ferias = new Set<string>();
  const asociaciones = new Set<string>();
  const invitaciones = new Set<string>();
  for (const registro of registros) {
    const referencias = referenciasAuditoria(String(registro.accion ?? ''), registro.documentoId);
    referencias.cuentas.forEach(valor => cuentas.add(valor));
    referencias.ferias.forEach(valor => ferias.add(valor));
    referencias.asociaciones.forEach(valor => asociaciones.add(valor));
    referencias.invitaciones.forEach(valor => invitaciones.add(valor));
  }
  const nombres: NombresAuditoria = { cuentas: new Map(), ferias: new Map(), asociaciones: new Map(), invitaciones: new Map() };
  const uids = [...cuentas];
  for (let inicio = 0; inicio < uids.length; inicio += 100) {
    try {
      const resultado = await auth.getUsers(uids.slice(inicio, inicio + 100).map(uid => ({ uid })));
      for (const usuario of resultado.users) {
        const etiqueta = usuario.email || usuario.displayName;
        if (etiqueta) nombres.cuentas.set(usuario.uid, etiqueta);
      }
    } catch {}
  }
  const leerNombres = async (coleccion: string, ids: Set<string>, campo: string, destino: Map<string, string>) => {
    if (!ids.size) return;
    try {
      const documentos = await db.getAll(...[...ids].map(valor => db.doc(`${coleccion}/${valor}`)), { fieldMask: [campo] });
      for (const documento of documentos) {
        const valor = documento.get(campo);
        if (typeof valor === 'string' && valor.trim()) destino.set(documento.id, valor.trim().slice(0, 120));
      }
    } catch {}
  };
  await Promise.all([
    leerNombres('ferias', ferias, 'nombre', nombres.ferias),
    leerNombres('asociaciones', asociaciones, 'nombre', nombres.asociaciones),
    leerNombres('invitaciones', invitaciones, 'correo', nombres.invitaciones),
  ]);
  return nombres;
}

export function crearAdministracionPlataforma(db: Firestore, auth: Auth) {
  return async (accion: AccionAdministracion, identidad: { uid: string }, input: unknown) => {
    exigir(accionesAdministracion.includes(accion), 'datos-invalidos', 'Acción administrativa desconocida.');
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');
    const data = objeto(input);
    const actorRef = db.doc(`accesos/${identidad.uid}`);

    if (accion === 'resumenAdministracion') {
      exactos(data, []);
      exigir(admin((await actorRef.get()).data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      const contar = async (consulta: Query) => (await consulta.count().get()).data().count;
      const [emprendedoras, invitacionesPendientes, asociaciones, ferias, sectores, categorias, tiposEmprendimiento, tiposProducto] = await Promise.all([
        contar(db.collection('accesos').where('roles', 'array-contains', 'emprendedora')),
        contar(db.collection('invitaciones').where('estado', '==', 'pendiente')),
        contar(db.collection('asociaciones')),
        contar(db.collection('ferias')),
        contar(db.collection('sectores')),
        contar(db.collection('categorias')),
        contar(db.collection('tiposEmprendimiento')),
        contar(db.collection('tiposProducto')),
      ]);
      return {
        emprendedoras,
        invitacionesPendientes,
        asociaciones,
        ferias,
        catalogos: sectores + categorias + tiposEmprendimiento + tiposProducto,
      };
    }

    if (accion === 'listarAuditoriaAdministracion') {
      exactos(data, ['cursor']);
      exigir(admin((await actorRef.get()).data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      const cursorData = data.cursor;
      let cursor: { seconds: number; nanoseconds: number; id: string } | null = null;
      if (cursorData !== null) {
        const value = objeto(cursorData);
        exactos(value, ['seconds', 'nanoseconds', 'id']);
        exigir(Number.isSafeInteger(value.seconds) && Number(value.seconds) >= 0 && Number(value.seconds) <= 253_402_300_799
          && Number.isSafeInteger(value.nanoseconds) && Number(value.nanoseconds) >= 0 && Number(value.nanoseconds) < 1_000_000_000
          && typeof value.id === 'string' && /^[a-zA-Z0-9_-]{1,512}$/.test(value.id),
        'datos-invalidos', 'Cursor de auditoría inválido.');
        cursor = { seconds: Number(value.seconds), nanoseconds: Number(value.nanoseconds), id: value.id };
      }

      const pageSize = 30;
      let consulta = db.collection('auditoriaAdministracion')
        .orderBy('creadoEn', 'desc')
        .orderBy(FieldPath.documentId(), 'desc');
      if (cursor) consulta = consulta.startAfter(new Timestamp(cursor.seconds, cursor.nanoseconds), cursor.id);
      const snapshot = await consulta.limit(pageSize + 1).get();
      const documentos = snapshot.docs.slice(0, pageSize);
      const actores = documentos
        .map(documento => documento.data().actorUid)
        .filter((valor): valor is string => typeof valor === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(valor));
      const nombres = await resolverNombresAuditoria(db, auth, actores, documentos.map(documento => documento.data()));
      const acciones = documentos.flatMap(documento => {
        const registro = documento.data();
        const actorUid = typeof registro.actorUid === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(registro.actorUid) ? registro.actorUid : '';
        const metadatos = etiquetasAuditoria[String(registro.accion ?? '')];
        const momento = registro.creadoEn instanceof Timestamp ? registro.creadoEn.toDate().toISOString() : null;
        if (!actorUid || !metadatos || !momento) return [];
        return [{
          id: documento.id,
          actorUid,
          actorNombre: nombres.cuentas.get(actorUid) ?? '',
          area: metadatos.area,
          accion: metadatos.accion,
          objetivo: objetivoAuditoria(String(registro.accion), registro.documentoId, nombres),
          creadoEn: momento,
        }];
      });
      const ultimo = documentos.at(-1);
      const ultimoMomento = ultimo?.data().creadoEn;
      const siguiente = snapshot.docs.length > pageSize && ultimoMomento instanceof Timestamp
        ? { seconds: ultimoMomento.seconds, nanoseconds: ultimoMomento.nanoseconds, id: ultimo!.id }
        : null;
      return { acciones, siguiente };
    }

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
