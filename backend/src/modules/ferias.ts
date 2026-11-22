import { createHash } from 'node:crypto';
import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import type { DocumentData, Firestore } from 'firebase-admin/firestore';
import type { Auth } from 'firebase-admin/auth';
import type { SolicitudCorreoFeria } from './correo-ferias.ts';
import { ErrorOperacion, version } from './publicacion.ts';

export const accionesFeria = [
  'listarFeriasAdmin', 'listarInvitacionesFeria', 'crearFeria', 'editarFeria', 'cambiarEstadoFeria',
  'leerParticipantesFeria', 'invitarParticipanteFeria', 'agregarParticipanteFeria',
  'cancelarInvitacionFeria', 'retirarParticipanteFeria', 'registrarAsistenciaFeria', 'responderInvitacionFeria', 'enviarRecordatorioFeria', 'enviarAvisoConfirmadasFeria',
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

type DatosFeria = { nombre: string; descripcion: string; organizador: string; direccion: string; fechaInicio: Date; fechaFin: Date; imagenUrl: string; latitud?: number; longitud?: number };
function parsearDatosFeria(data: Record<string, unknown>): DatosFeria {
  const nombre = texto(data.nombre, 120, 'El nombre');
  const descripcion = texto(data.descripcion, 2000, 'La descripción');
  const organizador = texto(data.organizador, 120, 'La persona u organización responsable');
  const direccion = texto(data.direccion, 300, 'La dirección');
  const fechaInicio = fecha(data.fechaInicio, 'La fecha de inicio');
  const fechaFin = fecha(data.fechaFin, 'La fecha de término');
  exigir(fechaFin > fechaInicio, 'datos-invalidos', 'El término debe ser posterior al inicio.');
  coordenadas(data.latitud, data.longitud);
  const imagenUrl = https(data.imagenUrl, 'La imagen') as string;
  return { nombre, descripcion, organizador, direccion, fechaInicio, fechaFin, imagenUrl, ...(data.latitud !== undefined ? { latitud: Number(data.latitud), longitud: Number(data.longitud) } : {}) };
}
function parsear(accion: AccionFeria, input: unknown) {
  const data = objeto(input);
  if (accion === 'listarFeriasAdmin' || accion === 'listarInvitacionesFeria') { exactos(data, []); return { accion } as const; }
  if (accion === 'crearFeria') {
    exactos(data, ['operacionId', 'nombre', 'descripcion', 'imagenUrl', 'organizador', 'direccion', 'fechaInicio', 'fechaFin'], ['latitud', 'longitud']);
    exigir(operationId(data.operacionId), 'datos-invalidos', 'ID de operación inválido.');
    return { accion, operacionId: data.operacionId, datos: parsearDatosFeria(data) } as const;
  }
  if (accion === 'editarFeria') {
    exactos(data, ['operacionId', 'feriaId', 'versionEsperada', 'nombre', 'descripcion', 'imagenUrl', 'organizador', 'direccion', 'fechaInicio', 'fechaFin'], ['latitud', 'longitud']);
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
  if (accion === 'cancelarInvitacionFeria' || accion === 'retirarParticipanteFeria') {
    exactos(data, ['operacionId', 'feriaId', 'versionFeriaEsperada', 'participanteId']);
    exigir(operationId(data.operacionId) && id(data.feriaId) && id(data.participanteId), 'datos-invalidos', 'IDs inválidos.');
    return { accion, operacionId: data.operacionId, feriaId: data.feriaId, versionFeriaEsperada: fechaVersión(data.versionFeriaEsperada), participanteId: data.participanteId } as const;
  }
  if (accion === 'enviarRecordatorioFeria' || accion === 'enviarAvisoConfirmadasFeria') {
    exactos(data, ['operacionId', 'feriaId', 'participanteId']);
    exigir(
      operationId(data.operacionId) &&
      id(data.feriaId) &&
      id(data.participanteId),
      'datos-invalidos',
      'Datos del recordatorio inválidos.'
    )
    return {
      accion,
      operacionId: data.operacionId,
      feriaId: data.feriaId,
      participanteId: data.participanteId
    } as const;
  }
  if (accion === 'registrarAsistenciaFeria') {
    exactos(data, ['operacionId', 'feriaId', 'versionFeriaEsperada', 'participanteId', 'asistencia']);
    exigir(operationId(data.operacionId) && id(data.feriaId) && id(data.participanteId) && (data.asistencia === 'asistio' || data.asistencia === 'no_asistio'), 'datos-invalidos', 'Datos de asistencia inválidos.');
    return { accion, operacionId: data.operacionId, feriaId: data.feriaId, versionFeriaEsperada: fechaVersión(data.versionFeriaEsperada), participanteId: data.participanteId, asistencia: data.asistencia as Exclude<Asistencia, 'pendiente'> } as const;
  }
  exactos(data, ['feriaId', 'respuesta'], ['asociacionId']);
  exigir(id(data.feriaId) && (data.respuesta === 'confirmar' || data.respuesta === 'rechazar') && (data.asociacionId === undefined || id(data.asociacionId)), 'datos-invalidos', 'Respuesta o ID de feria inválido.');
  return { accion, feriaId: data.feriaId, respuesta: data.respuesta, ...(typeof data.asociacionId === 'string' ? { asociacionId: data.asociacionId } : {}) } as const;
}

async function comprobarAdmin(db: Firestore, uid: string) {
  exigir(admin((await db.doc(`accesos/${uid}`).get()).data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
}
function nombreParticipante(value: unknown, fallback: string) { return typeof value === 'string' && value.trim() ? value.trim().slice(0, 120) : fallback; }
function fechaIso(data: DocumentData, campo: string) { const value = data[campo]; return value instanceof Timestamp ? value.toDate().toISOString() : null; }

export function crearFerias(db: Firestore, notificar?: (uid: string, tipo: 'tienda' | 'asociacion', entidadId: string, feriaId: string, nombreFeria: string) => Promise<void>, auth?: Auth, enviarCorreo?: (solicitud: SolicitudCorreoFeria) => Promise<string>, notificarInvitacion?: (uid: string, feriaId: string, nombreFeria: string) => Promise<void>) {
  const avisarSeguidores = async (tipo: 'tienda' | 'asociacion', entidadId: string, feriaId: string, nombreFeria: string) => {
    if (!notificar) return;
    const coleccion = tipo === 'tienda' ? 'tiendasSeguidas' : 'asociacionesSeguidas';
    const campo = tipo === 'tienda' ? 'tiendaId' : 'asociacionId';
    const seguidores = await db.collectionGroup(coleccion).where(campo, '==', entidadId).limit(500).get();
    await Promise.all(seguidores.docs.map(async seguimiento => {
      const uid = seguimiento.ref.parent.parent?.id;
      if (!uid) return;
      const usuario = await db.doc(`usuarios/${uid}`).get();
      if (usuario.get('avisosFerias') === true) await notificar(uid, tipo, entidadId, feriaId, nombreFeria);
    }));
  };
  return async (accion: AccionFeria, identidad: { uid: string }, input: unknown) => {
    exigir(accionesFeria.includes(accion) && id(identidad?.uid), 'no-autenticado', 'Identidad verificada requerida.');
    const request = parsear(accion, input);
    const actorRef = db.doc(`accesos/${identidad.uid}`);
    if (request.accion === 'listarInvitacionesFeria') {
      const access = await actorRef.get();
      const accessData = access.data();
      exigir(accessData?.estado === 'activo' && Array.isArray(accessData.roles) && accessData.roles.includes('emprendedora'), 'sin-permiso', 'Solo una cuenta emprendedora activa puede consultar sus invitaciones.');
      const ferias = await db.collection('ferias').where('estado', '==', 'publicada').limit(101).get();
      exigir(ferias.size <= 100, 'limite-local', 'Hay más de 100 ferias publicadas; se requiere paginación.');
      if (!ferias.size) return { invitaciones: [] };
      const participantes = await db.getAll(...ferias.docs.map(doc => doc.ref.collection('participantes').doc(`emprendedora_${identidad.uid}`)));
      const invitaciones = participantes.flatMap((participante, indice) => {
        if (!participante.exists) return [];
        const data = participante.data()!;
        if (data.tipo !== 'emprendedora' || data.entidadId !== identidad.uid || !['invitada', 'confirmada', 'rechazada'].includes(String(data.estado))) return [];
        const feria = ferias.docs[indice];
        const feriaData = feria.data();
        return [{
          id: feria.id,
          nombre: String(feriaData.nombre ?? ''),
          donde: String(feriaData.direccion ?? ''),
          fechaInicio: fechaIso(feriaData, 'fechaInicio'),
          fechaFin: fechaIso(feriaData, 'fechaFin'),
          organiza: String(feriaData.organizador ?? ''),
          estado: String(data.estado),
        }];
      }).sort((a, b) => (a.fechaInicio ?? '').localeCompare(b.fechaInicio ?? ''));
      return { invitaciones };
    }
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
      const [feria, participantes, accesos, asociaciones, tipos] = await Promise.all([
        feriaRef.get(), feriaRef.collection('participantes').limit(201).get(),
        db.collection('accesos').where('roles', 'array-contains', 'emprendedora').limit(101).get(),
        db.collection('asociaciones').where('activo', '==', true).limit(101).get(),
        db.collection('tiposEmprendimiento').limit(201).get(),
      ]);
      exigir(feria.exists, 'no-encontrado', 'La feria no existe.');
      exigir(participantes.size <= 200 && accesos.size <= 100 && asociaciones.size <= 100, 'limite-local', 'La feria o el padrón supera el límite de 100 participantes por tipo.');
      const nombreTipo = new Map(tipos.docs.map(doc => [doc.id, nombreParticipante(doc.data().nombre, doc.id)]));
      const tipoPorUid = new Map<string, string>();
      const integrantes = await Promise.all(asociaciones.docs.map(doc => doc.ref.collection('integrantes').where('estado', '==', 'activa').limit(201).get()));
      const asociacionesPorUid = new Map<string, { id: string; nombre: string }[]>();
      asociaciones.docs.forEach((doc, i) => integrantes[i].docs.forEach(relacion => {
        asociacionesPorUid.set(relacion.id, [...(asociacionesPorUid.get(relacion.id) ?? []), { id: doc.id, nombre: nombreParticipante(doc.data().nombre, doc.id) }]);
      }));
      const candidatos = await Promise.all(accesos.docs.map(async accessDoc => {
        const uid = accessDoc.id; const access = accessDoc.data();
        const [perfil, user] = await Promise.all([db.doc(`emprendedoras/${uid}`).get(), db.doc(`usuarios/${uid}`).get()]);
        const storeId = perfil.data()?.tiendaId;
        if (access.estado !== 'activo' || !id(storeId) || access.tiendaId !== storeId) return null;
        const store = await db.doc(`tiendasPrivadas/${storeId}`).get();
        if (typeof store.data()?.tipoEmprendimientoId === 'string') tipoPorUid.set(uid, store.data()!.tipoEmprendimientoId);
        if (store.data()?.propietarioUid !== uid) return null;
        return { tipo: 'emprendedora' as const, entidadId: uid, nombre: nombreParticipante(user.data()?.nombreMostrar, uid), nombreTienda: nombreParticipante(store.data()?.nombre, 'Tienda sin nombre') };
      }));
      const controles = await Promise.all(participantes.docs.map(doc => doc.ref.collection('controlRecordatorios').doc('actual').get()))
      const controlesAvisos = await Promise.all(participantes.docs.map(doc => doc.ref.collection('controlesAvisos').doc('actual').get()))
      const existentes = participantes.docs.map((doc, indice) => {
        const data = doc.data();
        const estado = String(data.estado ?? 'invitada');
        const asistencia = ['confirmada', 'agregada_admin'].includes(estado) ? String(data.asistencia ?? 'pendiente') : null;
        const tipo = data.tipo === 'asociacion' ? 'asociacion' : 'emprendedora';
        const entidadId = String(data.entidadId ?? '');
        const tipoId = tipo === 'emprendedora' ? tipoPorUid.get(entidadId) : undefined;
        const tipoEmprendimiento = tipoId ? { id: tipoId, nombre: nombreTipo.get(tipoId) ?? tipoId } : null;
        const asociacionesParticipante = tipo === 'asociacion' ? [{ id: entidadId, nombre: String(data.nombre ?? entidadId) }] : asociacionesPorUid.get(entidadId) ?? [];
        return {
          id: doc.id, tipo, entidadId, nombre: String(data.nombre ?? ''), nombreTienda: String(data.nombreTienda ?? ''), estado, asistencia, tipoEmprendimiento, asociaciones: asociacionesParticipante, versionEsperada: version(data),
          recordatorio: controles[indice].exists ? { estado: controles[indice].get('estado') === 'aceptado' ? 'aceptado' : 'reservado', enviadoEn: controles[indice].data()?.enviadoEn instanceof Timestamp ? controles[indice].data()!.enviadoEn.toDate().toISOString() : null, } : null,
          avisoConfirmacion: controlesAvisos[indice].exists ? { estado: controlesAvisos[indice].get('estado') === 'aceptado' ? 'aceptado' : 'reservado', enviadoEn: controlesAvisos[indice].data()?.enviadoEn instanceof Timestamp ? controlesAvisos[indice].data()!.enviadoEn.toDate().toISOString() : null, } : null
        };
      });
      const presentes = new Set(existentes.filter(item => item.estado !== 'retirada').map(item => `${item.tipo}:${item.entidadId}`));
      const emprendedoras = candidatos.filter((item): item is NonNullable<typeof item> => Boolean(item)).filter(item => !presentes.has(`emprendedora:${item.entidadId}`));
      const disponiblesAsociaciones = asociaciones.docs.map(doc => ({ tipo: 'asociacion' as const, entidadId: doc.id, nombre: nombreParticipante(doc.data().nombre, doc.id) })).filter(item => !presentes.has(`asociacion:${item.entidadId}`));
      return { feria: { id: feria.id, estado: String(feria.data()?.estado ?? 'borrador'), versionEsperada: version(feria.data()!) }, participantes: existentes, disponibles: [...emprendedoras, ...disponiblesAsociaciones] };
    }

    if (request.accion === 'responderInvitacionFeria') {
      const feriaRef = db.doc(`ferias/${request.feriaId}`);
      const associationId = request.asociacionId;
      const participantRef = feriaRef.collection('participantes').doc(associationId ? `asociacion_${associationId}` : `emprendedora_${identidad.uid}`);
      const associationRef = associationId ? db.doc(`asociaciones/${associationId}`) : null;
      const relationRef = associationRef?.collection('integrantes').doc(identidad.uid);
      const respuesta = await db.runTransaction(async tx => {
        const refs = [actorRef, db.doc(`emprendedoras/${identidad.uid}`), feriaRef, participantRef, ...(associationRef && relationRef ? [associationRef, relationRef] : [])];
        const reads = await tx.getAll(...refs);
        const [access, profile, fair, participant, association, relation] = reads;
        const accessData = access.data();
        exigir(accessData?.estado === 'activo' && Array.isArray(accessData.roles) && accessData.roles.includes('emprendedora'), 'sin-permiso', 'Solo una cuenta emprendedora activa puede responder.');
        if (associationId) exigir(id(accessData.tiendaId) && profile.data()?.tiendaId === accessData.tiendaId && association?.exists && association.data()?.activo === true && relation?.exists && relation.data()?.estado === 'activa' && relation.data()?.tiendaId === accessData.tiendaId, 'sin-permiso', 'La cuenta ya no representa a esta asociación.');
        exigir(fair.exists && fair.data()?.estado === 'publicada', 'no-encontrado', 'La feria no está publicada o no existe.');
        exigir(participant.exists && participant.data()?.tipo === (associationId ? 'asociacion' : 'emprendedora') && participant.data()?.entidadId === (associationId ?? identidad.uid), 'conflicto', 'No tienes una invitación para esta feria.');
        const estado = request.respuesta === 'confirmar' ? 'confirmada' : 'rechazada';
        if (participant.data()?.estado === estado) return { feriaId: request.feriaId, estado, notificar: false };
        exigir(participant.data()?.estado === 'invitada', 'conflicto', 'La invitación ya fue respondida.');
        const now = FieldValue.serverTimestamp();
        tx.update(participantRef, { estado, ...(estado === 'confirmada' ? { asistencia: 'pendiente' } : { asistencia: FieldValue.delete() }), actualizadoEn: now }); tx.update(feriaRef, { actualizadoEn: now });
        return { feriaId: request.feriaId, estado, notificar: estado === 'confirmada' };
      });
      if (respuesta.notificar) {
        const fair = await feriaRef.get();
        if (fair.exists) {
          const tipo = associationId ? 'asociacion' : 'tienda';
          const entidadId = associationId ?? (await db.doc(`emprendedoras/${identidad.uid}`).get()).get('tiendaId');
          if (typeof entidadId === 'string') await avisarSeguidores(tipo, entidadId, request.feriaId, String(fair.get('nombre') ?? 'una feria')).catch(() => undefined);
        }
      }
      return { feriaId: respuesta.feriaId, estado: respuesta.estado };
    }

    if (request.accion === 'enviarRecordatorioFeria' || request.accion === 'enviarAvisoConfirmadasFeria') {
      exigir(
        Boolean(auth && enviarCorreo),
        'servicio-no-configurado',
        'El envío de recordatorios no está configurado en el servidor.'
      )

      const esAviso = request.accion === 'enviarAvisoConfirmadasFeria'
      const estadoDestinatario = esAviso ? 'confirmada' : 'invitada'

      const feriaRef = db.doc(`ferias/${request.feriaId}`)
      const participanteRef = feriaRef.collection('participantes').doc(request.participanteId)
      const controlRef = participanteRef.collection(esAviso ? 'controlAvisos' : 'controlRecordatorios').doc('actual')
      const intentoRef = participanteRef.collection(esAviso ? 'avisosConfirmacion' : 'recordatorios').doc(request.operacionId)


      const [actor, feria, participante] = await db.getAll(
        actorRef, feriaRef, participanteRef
      )

      exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.')
      exigir(
        feria.exists && feria.data()?.estado === 'publicada',
        'conflicto',
        'La feria debe estar publicada.'
      )

      const feriaData = feria.data()!

      exigir(
        feriaData.fechaInicio instanceof Timestamp &&
        feriaData.fechaInicio.toMillis() > Date.now(),
        'conflicto',
        'La feria ya comenzó.'
      )

      const participanteData = participante.data()

      exigir(
        participante.exists && participanteData?.estado === estadoDestinatario,
        'conflicto',
        esAviso ? 'La participante ya no tiene su participación confirmada.' : 'La invitación ya no está pendiente.'
      )

      exigir(
        participanteData?.tipo === 'emprendedora' ||
        participanteData?.tipo === 'asociacion',
        'datos-invalidos',
        'Tipo de participante inválido.'
      )

      exigir(
        id(participanteData.entidadId) &&
        request.participanteId === `${participanteData.tipo}_${participanteData.entidadId}`,
        'datos-invalidos',
        'Participante inconsistente.'
      )

      let email: string | undefined

      if (participanteData.tipo === 'emprendedora') {
        try {
          const usuario = await auth!.getUser(participanteData.entidadId)
          if (!usuario.disabled && usuario.emailVerified) {
            email = usuario.email
          }
        } catch {
          // Sin destinatario válido.
        }
      } else {
        const asociacion = await db.doc(
          `asociaciones/${participanteData.entidadId}`
        ).get()

        const contacto = asociacion.data()?.contacto

        if (
          asociacion.data()?.activo === true &&
          contacto?.tipo === 'correo' &&
          typeof contacto.valor === 'string'
        ) {
          email = contacto.valor
        }
      }

      exigir(
        typeof email === 'string' &&
        /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email),
        'sin-destinatario',
        'No hay correo verificado para esta participante o contacto de correo activo para la asociación.'
      )

      const correo = email.trim().toLowerCase()
      const destinoHash = firma(correo)

      const inicio = feriaData.fechaInicio.toDate().toLocaleString(
        'es-CL',
        {
          timeZone: 'America/Santiago',
          dateStyle: 'full',
          timeStyle: 'short',
        }
      )

      const titulo = String(feriaData.nombre ?? 'Feria').slice(0, 120)
      const asunto = `esAviso ? 'Aviso de participación confirmada en ${titulo}' : 'Recordatorio de invitación: ${titulo}'`

      const nombreDestinatario = nombreParticipante(participanteData.nombreTienda || participanteData.nombre, 'participante')

      const texto = esAviso ? [
        `Hola, ${nombreDestinatario}:`,
        '',
        'Te informamos desde Rincón Amancay que tu participación en la siguiente feria está confirmada.',
        '',
        `Feria: ${titulo}`,
        `Fecha de inicio: ${inicio}`,
        `Lugar: ${String(feriaData.direccion ?? '').slice(0, 300)}`,
        `Organiza: ${String(feriaData.organizador ?? '').slice(0, 120)}`,
        '',
        'Te recomendamos preparar tus materiales y presentarte con anticipación.',
        '',
        '¡Esperamos contar con tu presencia!',
        '',
        'Equipo organizador de Rincón Amancay'
      ].join('\n')
        : [
          `Hola, ${nombreDestinatario}:`,
          '',
          'Tienes pendiente responder a una invitación para participar en una feria de Rincón Amancay.',
          '',
          `Feria: ${titulo}`,
          `Fecha: ${inicio}`,
          `Lugar: ${String(feriaData.direccion ?? '').slice(0, 300)}`,
          '',
          'Ingresa a Rincón Amancay para confirmar o rechazar tu invitación.',
          '',
          'Equipo organizador de Rincón Amancay'
        ].join('\n')

      const claveIdempotencia = `${esAviso ? 'w10' : 'w09'}-${firma([
        request.feriaId,
        request.participanteId,
        request.operacionId,
      ])}`

      const ahora = Date.now()

      const reserva = await db.runTransaction(async tx => {
        const [
          adminActual,
          feriaActual,
          participanteActual,
          control,
          intento,
        ] = await tx.getAll(
          actorRef,
          feriaRef,
          participanteRef,
          controlRef,
          intentoRef
        )

        exigir(
          admin(adminActual.data()),
          'sin-permiso',
          'Se requiere una cuenta administradora activa.'
        )

        exigir(
          feriaActual.data()?.estado === 'publicada' &&
          feriaActual.data()?.fechaInicio?.toMillis() > Date.now() &&
          participanteActual.data()?.estado === estadoDestinatario,
          'conflicto',
          'La feria o invitación cambió. Actualiza antes de enviar.'
        )

        if (intento.exists) {
          exigir(
            intento.data()?.destinoHash === destinoHash,
            'id-reutilizado',
            'Este identificador corresponde a otro destinatario.'
          )

          if (intento.data()?.estado === 'aceptado') {
            return {
              tipo: 'completado' as const,
              dato: intento.data()!,
            }
          }

          exigir(
            intento.data()?.estado === 'reservado' &&
            ahora - Number(intento.data()?.creadoMs) < 24 * 60 * 60 * 1000,
            'conflicto',
            'El intento anterior requiere revisión; no puede reintentarse automáticamente.'
          )

          return { tipo: 'reservado' as const }
        }

        if (control.exists) {
          const anterior = control.data()!

          exigir(
            anterior.estado !== 'reservado' ||
            ahora - Number(anterior.creadoMs) > 24 * 60 * 60 * 1000,
            'conflicto',
            esAviso ? 'Ya existe un aviso en proceso.' : 'Ya existe un recordatorio en proceso.'
          )

          exigir(
            anterior.estado !== 'aceptado' ||
            ahora - Number(anterior.creadoMs) >= 24 * 60 * 60 * 1000,
            'conflicto',
            esAviso ? 'Ya se envió un aviso en las últimas 24 horas.' : 'Ya se envió un recordatorio en las últimas 24 horas.'
          )
        }

        const registro = {
          estado: 'reservado',
          creadoMs: ahora,
          destinoHash,
          actorUid: identidad.uid,
          creadoEn: FieldValue.serverTimestamp(),
        }

        tx.create(intentoRef, registro)
        tx.set(controlRef, {
          ...registro,
          operacionId: request.operacionId,
        })

        return { tipo: 'nuevo' as const }
      })

      if (reserva.tipo === 'completado') {
        return {
          feriaId: request.feriaId,
          participanteId: request.participanteId,
          estado: 'aceptado',
          mensajeId: String(reserva.dato.mensajeId),
          enviadoEn: fechaIso(reserva.dato, 'enviadoEn'),
        }
      }

      let mensajeId: string

      try {
        mensajeId = await enviarCorreo!({
          para: correo,
          asunto,
          texto,
          claveIdempotencia,
        })
      } catch {
        throw new ErrorOperacion(
          'envio-incierto',
          'No se pudo confirmar el envío. Reintenta con la misma solicitud, sin crear otra.'
        )
      }

      await db.runTransaction(async tx => {
        const [intento, control] = await tx.getAll(intentoRef, controlRef)

        if (intento.data()?.estado === 'aceptado') return

        exigir(
          intento.exists &&
          intento.data()?.estado === 'reservado' &&
          control.data()?.operacionId === request.operacionId,
          'conflicto',
          'No se encontró la reserva vigente del envío.'
        )

        const datos = {
          estado: 'aceptado',
          mensajeId,
          enviadoEn: FieldValue.serverTimestamp(),
        }

        tx.update(intentoRef, datos)
        tx.update(controlRef, datos)

        tx.create(
          db.doc(
            `auditoriaAdministracion/${identidad.uid}_${request.operacionId}`
          ),
          {
            actorUid: identidad.uid,
            accion: request.accion,
            documentoId: `${request.feriaId}/participantes/${request.participanteId}`,
            resultado: 'aceptado',
            mensajeId,
            creadoEn: FieldValue.serverTimestamp(),
          }
        )
      })

      return {
        feriaId: request.feriaId,
        participanteId: request.participanteId,
        estado: 'aceptado',
        mensajeId,
        enviadoEn: new Date().toISOString(),
      }
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
      const transaccion = await db.runTransaction(async tx => {
        const refs = [actorRef, receiptRef, feriaRef, participantRef, entityRef, ...(profileRef ? [profileRef] : []), ...(userRef ? [userRef] : [])];
        const reads = await tx.getAll(...refs); const [actor, receipt, fair, participant, entity, profile, user] = reads;
        exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) {
          exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'El identificador de operación corresponde a otros datos.');
          return { resultado: receipt.data()!.resultado as Record<string, unknown>, notificarInvitacion: false };
        }
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
        const record = { tipo: request.tipo, entidadId: request.entidadId, nombre: participantName, ...(storeName ? { nombreTienda: storeName } : {}), estado: state, ...(state === 'agregada_admin' ? { asistencia: 'pendiente' } : participant.exists ? { asistencia: FieldValue.delete() } : {}), actualizadoEn: now, ...(participant.exists ? {} : { creadoPor: identidad.uid, creadoEn: now }) };
        if (participant.exists) tx.update(participantRef, record); else tx.create(participantRef, record);
        tx.update(feriaRef, { actualizadoEn: now });
        const result = { feriaId, participanteId: participantRef.id, estado: state };
        tx.create(receiptRef, { firma: signature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: `${feriaId}/participantes/${participantRef.id}`, antes: participant.exists ? { estado: participant.data()?.estado, asistencia: participant.data()?.asistencia ?? null } : null, despues: { tipo: request.tipo, entidadId: request.entidadId, estado: state, asistencia: state === 'agregada_admin' ? 'pendiente' : null }, creadoEn: now });
        return {
          resultado: result,
          notificarInvitacion: request.accion === 'invitarParticipanteFeria' && request.tipo === 'emprendedora',
        };
      });
      const resultado = transaccion.resultado;
      if (transaccion.notificarInvitacion && typeof request.entidadId === 'string' && typeof feriaId === 'string') {
        const feria = await feriaRef.get();
        if (feria.exists) {
          if (!notificarInvitacion) {
            console.error('[ferias] Invitación guardada, pero el envío push no está configurado.', { feriaId });
          } else {
            await notificarInvitacion(request.entidadId, feriaId, String(feria.get('nombre') ?? 'una feria')).catch(error => {
              const codigo = typeof error === 'object' && error !== null && 'code' in error ? String(error.code) : 'desconocido';
              console.error('[ferias] No se pudo enviar el push de invitación.', { feriaId, codigo });
            });
          }
        }
      }
      if (request.accion === 'agregarParticipanteFeria' && resultado.estado === 'agregada_admin') {
        const feria = await feriaRef.get();
        if (feria.exists) {
          const tipo = request.tipo === 'asociacion' ? 'asociacion' : 'tienda';
          const entidadId = tipo === 'asociacion'
            ? request.entidadId
            : (await db.doc(`emprendedoras/${request.entidadId}`).get()).get('tiendaId');
          if (typeof entidadId === 'string') await avisarSeguidores(tipo, entidadId, request.feriaId, String(feria.get('nombre') ?? 'una feria')).catch(() => undefined);
        }
      }
      return resultado;
    }

    if (request.accion === 'cancelarInvitacionFeria') {
      const participantRef = feriaRef.collection('participantes').doc(request.participanteId);
      return db.runTransaction(async tx => {
        const [actor, receipt, fair, participant] = await tx.getAll(actorRef, receiptRef, feriaRef, participantRef);
        exigir(admin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) { exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'El identificador de operación corresponde a otros datos.'); return receipt.data()!.resultado as Record<string, unknown>; }
        exigir(fair.exists && participant.exists, 'no-encontrado', 'No se encontró la feria o la invitación.');
        exigir(version(fair.data()!) === request.versionFeriaEsperada, 'conflicto', 'La feria cambió. Actualiza antes de continuar.');
        const participantData = participant.data()!;
        exigir(participantData.estado === 'invitada', 'conflicto', 'Solo se puede cancelar una invitación que sigue pendiente.');
        const now = FieldValue.serverTimestamp();
        const before = { tipo: participantData.tipo, entidadId: participantData.entidadId, estado: participantData.estado };
        const after = { tipo: participantData.tipo, entidadId: participantData.entidadId, estado: 'invitacion_cancelada' };
        tx.delete(participantRef);
        tx.update(feriaRef, { actualizadoEn: now });
        const result = { feriaId, participanteId: participantRef.id, estado: 'invitacion_cancelada' };
        tx.create(receiptRef, { firma: signature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion, documentoId: `${feriaId}/participantes/${participantRef.id}`, antes: before, despues: after, creadoEn: now });
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
          exigir(['confirmada', 'agregada_admin'].includes(String(participantData.estado)), 'conflicto', 'Solo se puede retirar a un participante confirmado o agregado directamente.');
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
