import { createHash } from 'node:crypto';
import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import type { DocumentData, Firestore } from 'firebase-admin/firestore';
import { ErrorOperacion, version } from './publicacion.ts';

export const accionesFeria = [
  'listarFeriasAdmin', 'crearFeria', 'editarFeria', 'cambiarEstadoFeria',
  'leerParticipantesFeria', 'invitarParticipanteFeria', 'agregarParticipanteFeria',
  'retirarParticipanteFeria', 'registrarAsistenciaFeria', 'responderInvitacionFeria',
] as const;
export type AccionFeria = typeof accionesFeria[number];
type TipoParticipante = 'emprendedora' | 'asociacion';
type EstadoFeria = 'borrador' | 'publicada' | 'cancelada';
type Asistencia = 'pendiente' | 'asistio' | 'no_asistio';

function exigir(ok: unknown, code: string, message: string): asserts ok { if (!ok) throw new ErrorOperacion(code, message); }
function objeto(value: unknown): Record<string, unknown> { exigir(value !== null && typeof value === 'object' && !Array.isArray(value), 'datos-invalidos', 'Se requiere un objeto.'); return value as Record<string, unknown>; }
function exactos(data: Record<string, unknown>, requeridos: string[], opcionales: string[] = []) { exigir(requeridos.every(key => Object.hasOwn(data, key)) && Object.keys(data).every(key => [...requeridos, ...opcionales].includes(key)), 'datos-invalidos', 'Campos ausentes o no permitidos.'); }
function id(value: unknown): value is string { return typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value); }
function admin(data: DocumentData | undefined) { return data?.estado === 'activo' && Array.isArray(data.roles) && data.roles.includes('administrador'); }
function operationId(value: unknown): value is string { return id(value); }
function texto(value: unknown, max: number, label: string, requerido = true): string {
  exigir(typeof value === 'string' && value.trim().length <= max && (!requerido || value.trim().length > 0), 'datos-invalidos', `${label} ${requerido ? `es obligatorio y admite hasta ${max} caracteres` : `admite hasta ${max} caracteres`}.`);
  return value.trim();
}
function fecha(value: unknown, label: string): Date {
  exigir(typeof value === 'string' && /(?:Z|[+-][0-9]{2}:[0-9]{2})$/.test(value), 'datos-invalidos', `${label} debe incluir una zona horaria.`);
  const result = new Date(value);
  exigir(Number.isFinite(result.getTime()), 'datos-invalidos', `${label} no es válida.`);
  return result;
}
function https(value: unknown, label: string, optional = false): string | undefined {
  if (optional && (value === undefined || value === '')) return undefined;
  exigir(typeof value === 'string' && value.length <= 2048, 'datos-invalidos', `${label} no es válida.`);
  try {
    const url = new URL(value);
    exigir(url.protocol === 'https:' && !url.username && !url.password, 'datos-invalidos', `${label} debe ser una URL HTTPS pública.`);
  } catch (error) {
    if (error instanceof ErrorOperacion) throw error;
    throw new ErrorOperacion('datos-invalidos', `${label} debe ser una URL HTTPS válida.`);
  }
  return value;
}
function coordenadas(lat: unknown, lon: unknown) {
  exigir((lat === undefined && lon === undefined) || (Number.isFinite(lat) && Number.isFinite(lon)), 'datos-invalidos', 'Completa ambas coordenadas o déjalas vacías.');
  if (lat !== undefined && lon !== undefined) exigir(Number(lat) >= -90 && Number(lat) <= 90 && Number(lon) >= -180 && Number(lon) <= 180, 'datos-invalidos', 'Las coordenadas están fuera de rango.');
}
function fechaVersión(value: unknown) { exigir(typeof value === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(value), 'datos-invalidos', 'Se requiere la versión vigente.'); return value; }
function firma(value: unknown) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }
function estadoFeria(value: unknown): value is EstadoFeria { return value === 'borrador' || value === 'publicada' || value === 'cancelada'; }

type DatosFeria = { nombre: string; descripcion: string; organizador: string; direccion: string; fechaInicio: Date; fechaFin: Date; imagenUrl?: string; latitud?: number; longitud?: number };
function parsearDatosFeria(data: Record<string, unknown>): DatosFeria {
  const nombre = texto(data.nombre, 120, 'El nombre');
  const descripcion = texto(data.descripcion, 2000, 'La descripción', false);
  const organizador = texto(data.organizador, 120, 'La persona u organización responsable');
  const direccion = texto(data.direccion, 300, 'La dirección');
  const fechaInicio = fecha(data.fechaInicio, 'La fecha de inicio');
  const fechaFin = fecha(data.fechaFin, 'La fecha de término');
  exigir(fechaFin > fechaInicio, 'datos-invalidos', 'El término debe ser posterior al inicio.');
  coordenadas(data.latitud, data.longitud);
  const imagenUrl = https(data.imagenUrl, 'La imagen', true);
  return { nombre, descripcion, organizador, direccion, fechaInicio, fechaFin, ...(imagenUrl ? { imagenUrl } : {}), ...(data.latitud !== undefined ? { latitud: Number(data.latitud), longitud: Number(data.longitud) } : {}) };
}
function parsear(accion: AccionFeria, input: unknown) {
  const data = objeto(input);
  if (accion === 'listarFeriasAdmin') { exactos(data, []); return { accion } as const; }
  if (accion === 'crearFeria') {
    exactos(data, ['operacionId', 'nombre', 'descripcion', 'organizador', 'direccion', 'fechaInicio', 'fechaFin'], ['imagenUrl', 'latitud', 'longitud']);
    exigir(operationId(data.operacionId), 'datos-invalidos', 'ID de operación inválido.');
    return { accion, operacionId: data.operacionId, datos: parsearDatosFeria(data) } as const;
  }
  if (accion === 'editarFeria') {
    exactos(data, ['operacionId', 'feriaId', 'versionEsperada', 'nombre', 'descripcion', 'organizador', 'direccion', 'fechaInicio', 'fechaFin'], ['imagenUrl', 'latitud', 'longitud']);
    exigir(operationId(data.operacionId) && id(data.feriaId), 'datos-invalidos', 'IDs inválidos.');
    return { accion, operacionId: data.operacionId, feriaId: data.feriaId, versionEsperada: fechaVersión(data.versionEsperada), datos: parsearDatosFeria(data) } as const;
  }
  if (accion === 'cambiarEstadoFeria') {
    exactos(data, ['operacionId', 'feriaId', 'versionEsperada', 'estado']);
    exigir(operationId(data.operacionId) && id(data.feriaId) && (data.estado === 'publicada' || data.estado === 'cancelada'), 'datos-invalidos', 'Estado o IDs inválidos.');
    return { accion, operacionId: data.operacionId, feriaId: data.feriaId, versionEsperada: fechaVersión(data.versionEsperada), estado: data.estado } as const;
  }
  if (accion === 'leerParticipantesFeria') {
    exactos(data, ['feriaId']); exigir(id(data.feriaId), 'datos-invalidos', 'ID de feria inválido.');
    return { accion, feriaId: data.feriaId } as const;
  }
  if (accion === 'invitarParticipanteFeria' || accion === 'agregarParticipanteFeria') {
    exactos(data, ['operacionId', 'feriaId', 'versionFeriaEsperada', 'tipo', 'entidadId']);
    exigir(operationId(data.operacionId) && id(data.feriaId) && id(data.entidadId) && (data.tipo === 'emprendedora' || data.tipo === 'asociacion'), 'datos-invalidos', 'Datos del participante inválidos.');
    return { accion, operacionId: data.operacionId, feriaId: data.feriaId, versionFeriaEsperada: fechaVersión(data.versionFeriaEsperada), tipo: data.tipo as TipoParticipante, entidadId: data.entidadId } as const;
  }
  if (accion === 'retirarParticipanteFeria') {
    exactos(data, ['operacionId', 'feriaId', 'versionFeriaEsperada', 'participanteId']);
    exigir(operationId(data.operacionId) && id(data.feriaId) && id(data.participanteId), 'datos-invalidos', 'IDs inválidos.');
    return { accion, operacionId: data.operacionId, feriaId: data.feriaId, versionFeriaEsperada: fechaVersión(data.versionFeriaEsperada), participanteId: data.participanteId } as const;
  }
  if (accion === 'registrarAsistenciaFeria') {
    exactos(data, ['operacionId', 'feriaId', 'versionFeriaEsperada', 'participanteId', 'asistencia']);
    exigir(operationId(data.operacionId) && id(data.feriaId) && id(data.participanteId) && (data.asistencia === 'asistio' || data.asistencia === 'no_asistio'), 'datos-invalidos', 'Datos de asistencia inválidos.');
    return { accion, operacionId: data.operacionId, feriaId: data.feriaId, versionFeriaEsperada: fechaVersión(data.versionFeriaEsperada), participanteId: data.participanteId, asistencia: data.asistencia as Exclude<Asistencia, 'pendiente'> } as const;
  }
  exactos(data, ['feriaId', 'respuesta']);
  exigir(id(data.feriaId) && (data.respuesta === 'confirmar' || data.respuesta === 'rechazar'), 'datos-invalidos', 'Respuesta o ID de feria inválido.');
  return { accion, feriaId: data.feriaId, respuesta: data.respuesta } as const;
}

async function comprobarAdmin(db: Firestore, uid: string) {
  exigir(admin((await db.doc(`accesos/${uid}`).get()).data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
}
function nombreParticipante(value: unknown, fallback: string) { return typeof value === 'string' && value.trim() ? value.trim().slice(0, 120) : fallback; }
function fechaIso(data: DocumentData, campo: string) { const value = data[campo]; return value instanceof Timestamp ? value.toDate().toISOString() : null; }

export function crearFerias(db: Firestore) {
  return async (accion: AccionFeria, identidad: { uid: string }, input: unknown) => {
    exigir(accionesFeria.includes(accion) && id(identidad?.uid), 'no-autenticado', 'Identidad verificada requerida.');
    const request = parsear(accion, input);
    const actorRef = db.doc(`accesos/${identidad.uid}`);
    if (request.accion === 'listarFeriasAdmin') {
      await comprobarAdmin(db, identidad.uid);
      const snapshot = await db.collection('ferias').limit(101).get();
      exigir(snapshot.size <= 100, 'limite-local', 'Hay más de 100 ferias; se requiere paginación.');
      const ferias = snapshot.docs.map(doc => {
        const data = doc.data();
        return { id: doc.id, nombre: String(data.nombre ?? ''), descripcion: String(data.descripcion ?? ''), organizador: String(data.organizador ?? ''), direccion: String(data.direccion ?? ''), fechaInicio: fechaIso(data, 'fechaInicio'), fechaFin: fechaIso(data, 'fechaFin'), imagenUrl: typeof data.imagenUrl === 'string' ? data.imagenUrl : '', latitud: typeof data.latitud === 'number' ? data.latitud : null, longitud: typeof data.longitud === 'number' ? data.longitud : null, estado: estadoFeria(data.estado) ? data.estado : 'borrador', versionEsperada: version(data) };
      }).sort((a, b) => String(a.fechaInicio).localeCompare(String(b.fechaInicio)));
      return { ferias };
    }

    if (request.accion === 'leerParticipantesFeria') {
      await comprobarAdmin(db, identidad.uid);
      const feriaRef = db.doc(`ferias/${request.feriaId}`);
      const [feria, participantes, accesos, asociaciones] = await Promise.all([
        feriaRef.get(), feriaRef.collection('participantes').limit(201).get(),
        db.collection('accesos').where('roles', 'array-contains', 'emprendedora').limit(101).get(),
        db.collection('asociaciones').where('activo', '==', true).limit(101).get(),
      ]);
      exigir(feria.exists, 'no-encontrado', 'La feria no existe.');
      exigir(participantes.size <= 200 && accesos.size <= 100 && asociaciones.size <= 100, 'limite-local', 'La feria o el padrón supera el límite de 100 participantes por tipo.');
      const candidatos = await Promise.all(accesos.docs.map(async accessDoc => {
        const uid = accessDoc.id; const access = accessDoc.data();
        const [perfil, user] = await Promise.all([db.doc(`emprendedoras/${uid}`).get(), db.doc(`usuarios/${uid}`).get()]);
        const storeId = perfil.data()?.tiendaId;
        if (access.estado !== 'activo' || !id(storeId) || access.tiendaId !== storeId) return null;
        const store = await db.doc(`tiendasPrivadas/${storeId}`).get();
        if (store.data()?.propietarioUid !== uid) return null;
        return { tipo: 'emprendedora' as const, entidadId: uid, nombre: nombreParticipante(user.data()?.nombreMostrar, uid), nombreTienda: nombreParticipante(store.data()?.nombre, 'Tienda sin nombre') };
      }));
      const existentes = participantes.docs.map(doc => {
        const data = doc.data();
        return { id: doc.id, tipo: data.tipo === 'asociacion' ? 'asociacion' : 'emprendedora', entidadId: String(data.entidadId ?? ''), nombre: String(data.nombre ?? ''), nombreTienda: String(data.nombreTienda ?? ''), estado: String(data.estado ?? 'invitada'), asistencia: String(data.asistencia ?? 'pendiente'), versionEsperada: version(data) };
      });
      const presentes = new Set(existentes.filter(item => item.estado !== 'retirada').map(item => `${item.tipo}:${item.entidadId}`));
      const emprendedoras = candidatos.filter((item): item is NonNullable<typeof item> => Boolean(item)).filter(item => !presentes.has(`emprendedora:${item.entidadId}`));
      const disponiblesAsociaciones = asociaciones.docs.map(doc => ({ tipo: 'asociacion' as const, entidadId: doc.id, nombre: nombreParticipante(doc.data().nombre, doc.id) })).filter(item => !presentes.has(`asociacion:${item.entidadId}`));
      return { feria: { id: feria.id, estado: String(feria.data()?.estado ?? 'borrador'), versionEsperada: version(feria.data()!) }, participantes: existentes, disponibles: [...emprendedoras, ...disponiblesAsociaciones] };
    }

    if (request.accion === 'responderInvitacionFeria') {
      const feriaRef = db.doc(`ferias/${request.feriaId}`);
      const participantRef = feriaRef.collection('participantes').doc(`emprendedora_${identidad.uid}`);
      return db.runTransaction(async tx => {
        const [access, fair, participant] = await tx.getAll(actorRef, feriaRef, participantRef);
        const accessData = access.data();
        exigir(accessData?.estado === 'activo' && Array.isArray(accessData.roles) && accessData.roles.includes('emprendedora'), 'sin-permiso', 'Solo una cuenta emprendedora activa puede responder.');
        exigir(fair.exists && fair.data()?.estado === 'publicada', 'no-encontrado', 'La feria no está publicada o no existe.');
        exigir(participant.exists && participant.data()?.entidadId === identidad.uid, 'conflicto', 'No tienes una invitación para esta feria.');
        const estado = request.respuesta === 'confirmar' ? 'confirmada' : 'rechazada';
        if (participant.data()?.estado === estado) return { feriaId: request.feriaId, estado };
        exigir(participant.data()?.estado === 'invitada', 'conflicto', 'La invitación ya fue respondida.');
        const now = FieldValue.serverTimestamp();
        tx.update(participantRef, { estado, actualizadoEn: now }); tx.update(feriaRef, { actualizadoEn: now });
        return { feriaId: request.feriaId, estado };
      });
    }

    const operationId = request.operacionId;
    const receiptRef = db.doc(`operacionesAdministracion/${identidad.uid}_${operationId}`);
    const auditRef = db.doc(`auditoriaAdministracion/${identidad.uid}_${operationId}`);
    const signature = firma(request);
    if (request.accion === 'crearFeria') {
      exigir(request.datos.fechaInicio > new Date(), 'datos-invalidos', 'La fecha de inicio de una nueva feria debe ser futura.');
      const feriaRef = db.collection('ferias').doc();
      return db.runTransaction(async tx => {
        const [actor, receipt] = await tx.getAll(actorRef, receiptRef);
        exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) { exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'El identificador de operación corresponde a otros datos.'); return receipt.data()!.resultado as Record<string, unknown>; }
        const now = FieldValue.serverTimestamp();
        const data = { ...request.datos, fechaInicio: Timestamp.fromDate(request.datos.fechaInicio), fechaFin: Timestamp.fromDate(request.datos.fechaFin), estado: 'borrador', creadoEn: now, actualizadoEn: now };
        tx.create(feriaRef, data);
        const result = { feriaId: feriaRef.id, estado: 'borrador' };
        tx.create(receiptRef, { firma: signature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: feriaRef.id, antes: null, despues: { nombre: data.nombre, estado: data.estado }, creadoEn: now });
        return result;
      });
    }

    if (request.accion === 'editarFeria' || request.accion === 'cambiarEstadoFeria') {
      const feriaRef = db.doc(`ferias/${request.feriaId}`);
      return db.runTransaction(async tx => {
        const [actor, receipt, fair] = await tx.getAll(actorRef, receiptRef, feriaRef);
        exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) { exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'El identificador de operación corresponde a otros datos.'); return receipt.data()!.resultado as Record<string, unknown>; }
        exigir(fair.exists, 'no-encontrado', 'La feria ya no existe.');
        const current = fair.data()!;
        exigir(version(current) === request.versionEsperada, 'conflicto', 'La feria cambió. Actualiza antes de continuar.');
        exigir(current.estado !== 'cancelada', 'conflicto', 'Una feria cancelada es histórica y no puede modificarse.');
        const now = FieldValue.serverTimestamp(); const before = { nombre: current.nombre, estado: current.estado };
        let result: Record<string, unknown>;
        if (request.accion === 'editarFeria') {
          exigir(current.fechaInicio.toDate() > new Date(), 'conflicto', 'No se puede editar una feria que ya comenzó.');
          const update: DocumentData = { ...request.datos, fechaInicio: Timestamp.fromDate(request.datos.fechaInicio), fechaFin: Timestamp.fromDate(request.datos.fechaFin), actualizadoEn: now };
          if (!request.datos.imagenUrl) update.imagenUrl = FieldValue.delete();
          if (request.datos.latitud === undefined) { update.latitud = FieldValue.delete(); update.longitud = FieldValue.delete(); }
          tx.update(feriaRef, update); result = { feriaId: feriaRef.id, estado: current.estado };
        } else {
          exigir(request.estado !== current.estado, 'conflicto', 'La feria ya está en ese estado.');
          if (request.estado === 'publicada') {
            exigir(current.estado === 'borrador' && current.fechaInicio.toDate() > new Date(), 'conflicto', 'Solo se puede publicar un borrador cuya fecha de inicio siga pendiente.');
          }
          tx.update(feriaRef, { estado: request.estado, actualizadoEn: now }); result = { feriaId: feriaRef.id, estado: request.estado };
        }
        tx.create(receiptRef, { firma: signature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: feriaRef.id, antes: before, despues: result, creadoEn: now });
        return result;
      });
    }

    const feriaId = request.feriaId; const feriaRef = db.doc(`ferias/${feriaId}`);
    if (request.accion === 'invitarParticipanteFeria' || request.accion === 'agregarParticipanteFeria') {
      const participantId = `${request.tipo}_${request.entidadId}`;
      const participantRef = feriaRef.collection('participantes').doc(participantId);
      const entityRef = db.doc(request.tipo === 'asociacion' ? `asociaciones/${request.entidadId}` : `accesos/${request.entidadId}`);
      const profileRef = request.tipo === 'emprendedora' ? db.doc(`emprendedoras/${request.entidadId}`) : null;
      const userRef = request.tipo === 'emprendedora' ? db.doc(`usuarios/${request.entidadId}`) : null;
      return db.runTransaction(async tx => {
        const refs = [actorRef, receiptRef, feriaRef, participantRef, entityRef, ...(profileRef ? [profileRef] : []), ...(userRef ? [userRef] : [])];
        const reads = await tx.getAll(...refs); const [actor, receipt, fair, participant, entity, profile, user] = reads;
        exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) { exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'El identificador de operación corresponde a otros datos.'); return receipt.data()!.resultado as Record<string, unknown>; }
        exigir(fair.exists && fair.data()?.estado !== 'cancelada', 'conflicto', 'No se puede agregar participantes a una feria cancelada.');
        exigir(version(fair.data()!) === request.versionFeriaEsperada, 'conflicto', 'La feria cambió. Actualiza el listado antes de continuar.');
        exigir(fair.data()!.fechaInicio.toDate() > new Date(), 'conflicto', 'No se agregan participantes después del inicio de la feria.');
        let participantName: string; let storeName = '';
        if (request.tipo === 'emprendedora') {
          const access = entity.data(); const entrepreneur = profile?.data();
          exigir(access?.estado === 'activo' && Array.isArray(access.roles) && access.roles.includes('emprendedora') && entrepreneur && entrepreneur.tiendaId === access.tiendaId, 'conflicto', 'La cuenta emprendedora no está activa o su perfil no coincide.');
          participantName = nombreParticipante(user?.data()?.nombreMostrar, request.entidadId);
          const store = await tx.get(db.doc(`tiendasPrivadas/${String(entrepreneur.tiendaId)}`));
          exigir(store.exists && store.data()?.propietarioUid === request.entidadId, 'conflicto', 'No se encontró una tienda coherente para la emprendedora.');
          storeName = nombreParticipante(store.data()?.nombre, 'Tienda sin nombre');
        } else {
          exigir(entity.exists && entity.data()?.activo === true, 'conflicto', 'La asociación debe estar activa.');
          participantName = nombreParticipante(entity.data()?.nombre, request.entidadId);
        }
        if (participant.exists) exigir(participant.data()?.estado === 'retirada', 'conflicto', 'La persona o asociación ya participa en esta feria.');
        const now = FieldValue.serverTimestamp();
        const state = request.accion === 'invitarParticipanteFeria' ? 'invitada' : 'agregada_admin';
        const record = { tipo: request.tipo, entidadId: request.entidadId, nombre: participantName, ...(storeName ? { nombreTienda: storeName } : {}), estado: state, asistencia: 'pendiente', actualizadoEn: now, ...(participant.exists ? {} : { creadoPor: identidad.uid, creadoEn: now }) };
        if (participant.exists) tx.update(participantRef, record); else tx.create(participantRef, record);
        tx.update(feriaRef, { actualizadoEn: now });
        const result = { feriaId, participanteId: participantRef.id, estado: state };
        tx.create(receiptRef, { firma: signature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: `${feriaId}/participantes/${participantRef.id}`, antes: participant.exists ? { estado: participant.data()?.estado, asistencia: participant.data()?.asistencia } : null, despues: { tipo: request.tipo, entidadId: request.entidadId, estado: state, asistencia: 'pendiente' }, creadoEn: now });
        return result;
      });
    }

    if (request.accion === 'retirarParticipanteFeria' || request.accion === 'registrarAsistenciaFeria') {
      const participantRef = feriaRef.collection('participantes').doc(request.participanteId);
      return db.runTransaction(async tx => {
        const [actor, receipt, fair, participant] = await tx.getAll(actorRef, receiptRef, feriaRef, participantRef);
        exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) { exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'El identificador de operación corresponde a otros datos.'); return receipt.data()!.resultado as Record<string, unknown>; }
        exigir(fair.exists && participant.exists, 'no-encontrado', 'No se encontró la feria o la participación.');
        const fairData = fair.data()!;
        exigir(version(fairData) === request.versionFeriaEsperada, 'conflicto', 'La feria cambió. Actualiza antes de continuar.');
        const participantData = participant.data()!;
        const now = FieldValue.serverTimestamp(); let after: Record<string, unknown>;
        if (request.accion === 'retirarParticipanteFeria') {
          exigir(participantData.estado !== 'retirada', 'conflicto', 'La participación ya fue retirada.');
          exigir(fairData.fechaFin.toDate() > new Date(), 'conflicto', 'No se puede retirar participantes después del término de la feria.');
          after = { estado: 'retirada', asistencia: participantData.asistencia ?? 'pendiente' };
          tx.update(participantRef, { estado: 'retirada', actualizadoEn: now });
        } else {
          exigir(['confirmada', 'agregada_admin'].includes(String(participantData.estado)), 'conflicto', 'Solo se registra asistencia de participantes confirmados o agregados por administración.');
          exigir(fairData.estado === 'publicada', 'conflicto', 'La asistencia solo se registra en una feria publicada.');
          exigir(fairData.fechaInicio.toDate() <= new Date(), 'conflicto', 'La asistencia se registra desde la hora de inicio de la feria.');
          after = { estado: participantData.estado, asistencia: request.asistencia };
          tx.update(participantRef, { asistencia: request.asistencia, actualizadoEn: now });
        }
        tx.update(feriaRef, { actualizadoEn: now });
        const result = { feriaId, participanteId: participantRef.id, ...after };
        tx.create(receiptRef, { firma: signature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: `${feriaId}/participantes/${participantRef.id}`, antes: { estado: participantData.estado, asistencia: participantData.asistencia ?? 'pendiente' }, despues: after, creadoEn: now });
        return result;
      });
    }
    throw new ErrorOperacion('datos-invalidos', 'Acción de feria no reconocida.');
  };
}
