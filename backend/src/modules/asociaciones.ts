import { createHash } from 'node:crypto';
import { FieldValue, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { ErrorOperacion, version } from './publicacion.ts';

export const accionesAsociacion = [
  'listarAsociaciones',
  'leerIntegrantesAsociacion',
  'crearAsociacion',
  'editarAsociacion',
  'cambiarEstadoAsociacion',
  'asignarEmprendedora',
  'quitarEmprendedora',
] as const;
export type AccionAsociacion = typeof accionesAsociacion[number];

type Contacto = { tipo: 'whatsapp' | 'correo' | 'web'; valor: string };
type Solicitud =
  | { accion: 'listarAsociaciones' }
  | { accion: 'leerIntegrantesAsociacion'; asociacionId: string }
  | { accion: 'crearAsociacion'; operacionId: string; id: string; nombre: string; descripcion: string; sectorId: string; contacto: Contacto; logoUrl?: string }
  | { accion: 'editarAsociacion'; operacionId: string; asociacionId: string; versionEsperada: string; nombre: string; descripcion: string; sectorId: string; contacto: Contacto; logoUrl?: string }
  | { accion: 'cambiarEstadoAsociacion'; operacionId: string; asociacionId: string; versionEsperada: string; activo: boolean }
  | { accion: 'asignarEmprendedora'; operacionId: string; asociacionId: string; versionAsociacionEsperada: string; uidEmprendedora: string; versionRelacionEsperada?: string }
  | { accion: 'quitarEmprendedora'; operacionId: string; asociacionId: string; uidEmprendedora: string; versionRelacionEsperada: string };

function exigir(ok: unknown, code: string, message: string): asserts ok {
  if (!ok) throw new ErrorOperacion(code, message);
}

function objeto(value: unknown): Record<string, unknown> {
  exigir(value !== null && typeof value === 'object' && !Array.isArray(value), 'datos-invalidos', 'Se requiere un objeto.');
  return value as Record<string, unknown>;
}

function camposExactos(data: Record<string, unknown>, required: string[], optional: string[] = []) {
  exigir(required.every(key => Object.hasOwn(data, key)) && Object.keys(data).every(key => [...required, ...optional].includes(key)), 'datos-invalidos', 'Campos ausentes o no permitidos.');
}

function id(value: unknown): value is string {
  return typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value);
}

function nombre(value: unknown): string {
  exigir(typeof value === 'string' && value.trim().length > 0 && value.trim().length <= 120, 'datos-invalidos', 'El nombre admite hasta 120 caracteres y no puede estar vacío.');
  return value.trim();
}

function texto(value: unknown, max: number, label: string): string {
  exigir(typeof value === 'string' && value.length <= max, 'datos-invalidos', `${label} admite hasta ${max} caracteres.`);
  return value.trim();
}

function slug(value: string): string {
  const normalized = value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 80);
  exigir(normalized.length > 0, 'datos-invalidos', 'El nombre debe incluir letras o números.');
  return normalized;
}

function normalizado(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-CL')
    .replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+/g, ' ');
}

function parsearVersion(value: unknown): string {
  exigir(typeof value === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(value), 'datos-invalidos', 'Se requiere la versión vigente del documento.');
  return value;
}

function contacto(value: unknown): Contacto {
  const data = objeto(value);
  camposExactos(data, ['tipo', 'valor']);
  exigir(['whatsapp', 'correo', 'web'].includes(String(data.tipo)), 'datos-invalidos', 'El contacto debe ser WhatsApp, correo o sitio web.');
  exigir(typeof data.valor === 'string' && data.valor.trim().length > 0 && data.valor.trim().length <= 254, 'datos-invalidos', 'El contacto es obligatorio y admite hasta 254 caracteres.');
  const tipo = data.tipo as Contacto['tipo'];
  const valor = (data.valor as string).trim();
  if (tipo === 'correo') exigir(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(valor), 'datos-invalidos', 'El correo de contacto no es válido.');
  if (tipo === 'whatsapp') exigir(/^\+?[0-9][0-9\s()-]{6,22}$/.test(valor), 'datos-invalidos', 'El teléfono de WhatsApp no es válido. Incluye el código de país.');
  if (tipo === 'web') {
    try {
      const url = new URL(valor);
      exigir(url.protocol === 'https:' && !url.username && !url.password, 'datos-invalidos', 'El sitio de contacto debe usar HTTPS.');
    } catch (error) {
      if (error instanceof ErrorOperacion) throw error;
      throw new ErrorOperacion('datos-invalidos', 'El sitio de contacto debe ser una URL HTTPS válida.');
    }
  }
  return { tipo, valor };
}

function logo(value: unknown): string | undefined {
  if (value === undefined || value === '') return undefined;
  exigir(typeof value === 'string' && value.length <= 2048, 'datos-invalidos', 'La dirección del logo admite hasta 2048 caracteres.');
  try {
    const url = new URL(value);
    exigir(url.protocol === 'https:' && !url.username && !url.password, 'datos-invalidos', 'El logo debe estar en una dirección HTTPS pública.');
  } catch (error) {
    if (error instanceof ErrorOperacion) throw error;
    throw new ErrorOperacion('datos-invalidos', 'El logo debe ser una URL HTTPS válida.');
  }
  return value;
}

function parsear(accion: AccionAsociacion, input: unknown): Solicitud {
  const data = objeto(input);
  if (accion === 'listarAsociaciones') {
    camposExactos(data, []);
    return { accion };
  }
  if (accion === 'leerIntegrantesAsociacion') {
    camposExactos(data, ['asociacionId']);
    exigir(id(data.asociacionId), 'datos-invalidos', 'ID de asociación inválido.');
    return { accion, asociacionId: data.asociacionId as string };
  }
  if (accion === 'crearAsociacion') {
    camposExactos(data, ['operacionId', 'nombre', 'descripcion', 'sectorId', 'contacto'], ['logoUrl']);
    exigir(id(data.operacionId) && id(data.sectorId), 'datos-invalidos', 'ID de operación o sector inválido.');
    const nombreLimpio = nombre(data.nombre);
    return {
      accion,
      operacionId: data.operacionId as string,
      id: slug(nombreLimpio),
      nombre: nombreLimpio,
      descripcion: texto(data.descripcion, 2000, 'La descripción'),
      sectorId: data.sectorId as string,
      contacto: contacto(data.contacto),
      ...(logo(data.logoUrl) ? { logoUrl: logo(data.logoUrl) } : {}),
    };
  }
  if (accion === 'editarAsociacion') {
    camposExactos(data, ['operacionId', 'asociacionId', 'versionEsperada', 'nombre', 'descripcion', 'sectorId', 'contacto'], ['logoUrl']);
    exigir(id(data.operacionId) && id(data.asociacionId) && id(data.sectorId), 'datos-invalidos', 'IDs inválidos.');
    const logoUrl = logo(data.logoUrl);
    return {
      accion,
      operacionId: data.operacionId as string,
      asociacionId: data.asociacionId as string,
      versionEsperada: parsearVersion(data.versionEsperada),
      nombre: nombre(data.nombre),
      descripcion: texto(data.descripcion, 2000, 'La descripción'),
      sectorId: data.sectorId as string,
      contacto: contacto(data.contacto),
      ...(logoUrl ? { logoUrl } : {}),
    };
  }
  if (accion === 'cambiarEstadoAsociacion') {
    camposExactos(data, ['operacionId', 'asociacionId', 'versionEsperada', 'activo']);
    exigir(id(data.operacionId) && id(data.asociacionId) && typeof data.activo === 'boolean', 'datos-invalidos', 'IDs o estado inválidos.');
    return { accion, operacionId: data.operacionId as string, asociacionId: data.asociacionId as string, versionEsperada: parsearVersion(data.versionEsperada), activo: data.activo };
  }
  if (accion === 'asignarEmprendedora') {
    camposExactos(data, ['operacionId', 'asociacionId', 'versionAsociacionEsperada', 'uidEmprendedora'], ['versionRelacionEsperada']);
    exigir(id(data.operacionId) && id(data.asociacionId) && id(data.uidEmprendedora), 'datos-invalidos', 'IDs inválidos.');
    if (data.versionRelacionEsperada !== undefined) parsearVersion(data.versionRelacionEsperada);
    return {
      accion,
      operacionId: data.operacionId as string,
      asociacionId: data.asociacionId as string,
      versionAsociacionEsperada: parsearVersion(data.versionAsociacionEsperada),
      uidEmprendedora: data.uidEmprendedora as string,
      ...(typeof data.versionRelacionEsperada === 'string' ? { versionRelacionEsperada: data.versionRelacionEsperada } : {}),
    };
  }
  camposExactos(data, ['operacionId', 'asociacionId', 'uidEmprendedora', 'versionRelacionEsperada']);
  exigir(id(data.operacionId) && id(data.asociacionId) && id(data.uidEmprendedora), 'datos-invalidos', 'IDs inválidos.');
  return {
    accion,
    operacionId: data.operacionId as string,
    asociacionId: data.asociacionId as string,
    uidEmprendedora: data.uidEmprendedora as string,
    versionRelacionEsperada: parsearVersion(data.versionRelacionEsperada),
  };
}

function esAdmin(data: DocumentData | undefined) {
  return data?.estado === 'activo' && Array.isArray(data.roles) && data.roles.includes('administrador');
}

function firma(value: unknown) {
  return createHash('sha256').update(JSON.stringify(value)).digest('hex');
}

async function lote<T>(items: T[], size: number, fn: (batch: T[]) => Promise<void>) {
  for (let index = 0; index < items.length; index += size) await fn(items.slice(index, index + size));
}

async function cargarPersonas(db: Firestore, uids: string[]) {
  const unique = [...new Set(uids)];
  const refs = unique.flatMap(uid => [db.doc(`accesos/${uid}`), db.doc(`emprendedoras/${uid}`), db.doc(`usuarios/${uid}`)]);
  const list: FirebaseFirestore.DocumentSnapshot[] = [];
  await lote(refs, 90, async batch => { list.push(...await db.getAll(...batch)); });
  const base = new Map<string, { acceso: DocumentData | undefined; perfil: DocumentData | undefined; usuario: DocumentData | undefined }>();
  unique.forEach((uid, index) => base.set(uid, {
    acceso: list[index * 3]?.data(),
    perfil: list[index * 3 + 1]?.data(),
    usuario: list[index * 3 + 2]?.data(),
  }));
  const tiendaRefs = [...new Set([...base.values()].map(persona => persona.perfil?.tiendaId).filter((tiendaId): tiendaId is string => typeof tiendaId === 'string' && id(tiendaId)))].map(tiendaId => db.doc(`tiendasPrivadas/${tiendaId}`));
  const tiendas: FirebaseFirestore.DocumentSnapshot[] = [];
  await lote(tiendaRefs, 90, async batch => { tiendas.push(...await db.getAll(...batch)); });
  const tiendasPorId = new Map(tiendas.map(tienda => [tienda.id, tienda.data()]));
  return new Map([...base].map(([uid, persona]) => {
    const tiendaId = persona.perfil?.tiendaId;
    const tienda = typeof tiendaId === 'string' ? tiendasPorId.get(tiendaId) : undefined;
    return [uid, {
      uid,
      tiendaId: typeof tiendaId === 'string' ? tiendaId : '',
      nombreMostrar: typeof persona.usuario?.nombreMostrar === 'string' ? persona.usuario.nombreMostrar : uid,
      nombreTienda: typeof tienda?.nombre === 'string' ? tienda.nombre : typeof tiendaId === 'string' ? tiendaId : 'Sin tienda',
      estadoCuenta: persona.acceso?.estado === 'activo' ? 'activo' : persona.acceso?.estado ?? 'inconsistente',
      acceso: persona.acceso,
      perfil: persona.perfil,
      tienda,
    }];
  }));
}

export function crearAsociaciones(db: Firestore) {
  return async (accion: AccionAsociacion, identidad: { uid: string }, input: unknown) => {
    exigir(accionesAsociacion.includes(accion), 'datos-invalidos', 'Operación de asociación desconocida.');
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');
    const solicitud = parsear(accion, input);
    const actorRef = db.doc(`accesos/${identidad.uid}`);

    if (solicitud.accion === 'listarAsociaciones') {
      exigir(esAdmin((await actorRef.get()).data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      const result = await db.collection('asociaciones').limit(201).get();
      exigir(result.size <= 200, 'limite-local', 'Hay más de 200 asociaciones; usa paginación antes de continuar.');
      const asociaciones = result.docs.map(snapshot => {
        const data = snapshot.data();
        return {
          id: snapshot.id,
          nombre: data.nombre,
          descripcion: data.descripcion ?? '',
          sectorId: data.sectorId,
          contacto: data.contacto,
          ...(typeof data.logoUrl === 'string' ? { logoUrl: data.logoUrl } : {}),
          activo: data.activo === true,
          versionEsperada: version(data),
        };
      }).sort((a, b) => String(a.nombre).localeCompare(String(b.nombre), 'es-CL'));
      return { asociaciones };
    }

    if (solicitud.accion === 'leerIntegrantesAsociacion') {
      exigir(esAdmin((await actorRef.get()).data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      const asociacionRef = db.doc(`asociaciones/${solicitud.asociacionId}`);
      const [asociacion, relaciones, accesos] = await Promise.all([
        asociacionRef.get(),
        asociacionRef.collection('integrantes').limit(201).get(),
        db.collection('accesos').where('roles', 'array-contains', 'emprendedora').limit(201).get(),
      ]);
      exigir(asociacion.exists, 'no-encontrado', 'La asociación ya no existe.');
      const asociacionData = asociacion.data()!;
      exigir(relaciones.size <= 200 && accesos.size <= 200, 'limite-local', 'La asociación o el padrón supera 200 registros; usa paginación antes de continuar.');
      const uids = [...new Set([...relaciones.docs.map(doc => doc.id), ...accesos.docs.map(doc => doc.id)])];
      const personas = await cargarPersonas(db, uids);
      const accesoActual = new Map(accesos.docs.map(doc => [doc.id, doc.data()]));
      const relacionesPorUid = new Map(relaciones.docs.map(doc => [doc.id, doc]));
      const miembros = relaciones.docs.map(relation => {
        const person = personas.get(relation.id);
        const data = relation.data();
        return {
          uid: relation.id,
          tiendaId: String(data.tiendaId ?? person?.tiendaId ?? ''),
          nombreMostrar: person?.nombreMostrar ?? relation.id,
          nombreTienda: person?.nombreTienda ?? 'Sin tienda',
          estadoRelacion: data.estado === 'activa' ? 'activa' : 'retirada',
          estadoCuenta: person?.estadoCuenta ?? 'inconsistente',
          versionEsperada: version(data),
        };
      }).sort((a, b) => a.nombreTienda.localeCompare(b.nombreTienda, 'es-CL'));
      const disponibles = accesos.docs.flatMap(snapshot => {
        const uid = snapshot.id;
        const access = accesoActual.get(uid);
        const person = personas.get(uid);
        const relation = relacionesPorUid.get(uid);
        if (access?.estado !== 'activo' || !Array.isArray(access.roles) || !access.roles.includes('emprendedora')) return [];
        if (!person?.perfil || person.perfil.tiendaId !== access.tiendaId || person.tienda?.propietarioUid !== uid || !id(String(access.tiendaId ?? ''))) return [];
        if (relation?.data().estado === 'activa') return [];
        return [{
          uid,
          tiendaId: person.tiendaId,
          nombreMostrar: person.nombreMostrar,
          nombreTienda: person.nombreTienda,
          ...(relation && version(relation.data()) ? { versionRelacionEsperada: version(relation.data()) } : {}),
        }];
      }).sort((a, b) => a.nombreTienda.localeCompare(b.nombreTienda, 'es-CL'));
      return { asociacion: { id: solicitud.asociacionId, activo: asociacionData.activo === true, versionEsperada: version(asociacionData) }, miembros, disponibles };
    }

    const operationId = solicitud.operacionId;
    const receiptRef = db.doc(`operacionesAdministracion/${identidad.uid}_${operationId}`);
    const auditRef = db.doc(`auditoriaAdministracion/${identidad.uid}_${operationId}`);
    const signature = firma(solicitud);

    if (solicitud.accion === 'crearAsociacion') {
      const associationRef = db.doc(`asociaciones/${solicitud.id}`);
      const sectorRef = db.doc(`sectores/${solicitud.sectorId}`);
      return db.runTransaction(async tx => {
        const [actor, receipt, association, sector] = await tx.getAll(actorRef, receiptRef, associationRef, sectorRef);
        exigir(esAdmin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) {
          exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
          return receipt.data()!.resultado as Record<string, unknown>;
        }
        exigir(!association.exists, 'conflicto', 'Ya existe una asociación con ese ID.');
        exigir(sector.data()?.activo === true, 'datos-invalidos', 'El sector debe existir y estar activo.');
        const current = await tx.get(db.collection('asociaciones').limit(501));
        exigir(current.size <= 500, 'limite-local', 'El catálogo supera el máximo administrable de 500 asociaciones.');
        exigir(current.docs.every(doc => normalizado(String(doc.data().nombre ?? doc.id)) !== normalizado(solicitud.nombre)), 'conflicto', 'Ya existe otra asociación con ese nombre.');
        const now = FieldValue.serverTimestamp();
        const document: DocumentData = {
          nombre: solicitud.nombre,
          nombreNormalizado: normalizado(solicitud.nombre),
          descripcion: solicitud.descripcion,
          sectorId: solicitud.sectorId,
          contacto: solicitud.contacto,
          ...(solicitud.logoUrl ? { logoUrl: solicitud.logoUrl } : {}),
          activo: true,
          creadoEn: now,
          actualizadoEn: now,
        };
        tx.create(associationRef, document);
        const resultado = { accion: solicitud.accion, asociacionId: solicitud.id, activo: true };
        tx.create(receiptRef, { firma: signature, resultado, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, {
          actorUid: identidad.uid,
          accion: solicitud.accion,
          documentoId: solicitud.id,
          antes: null,
          despues: { nombre: solicitud.nombre, descripcion: solicitud.descripcion, sectorId: solicitud.sectorId, contacto: solicitud.contacto, ...(solicitud.logoUrl ? { logoUrl: solicitud.logoUrl } : {}), activo: true },
          creadoEn: now,
        });
        return resultado;
      });
    }

    if (solicitud.accion === 'editarAsociacion') {
      const associationRef = db.doc(`asociaciones/${solicitud.asociacionId}`);
      const sectorRef = db.doc(`sectores/${solicitud.sectorId}`);
      return db.runTransaction(async tx => {
        const [actor, receipt, association, sector] = await tx.getAll(actorRef, receiptRef, associationRef, sectorRef);
        exigir(esAdmin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) {
          exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
          return receipt.data()!.resultado as Record<string, unknown>;
        }
        exigir(association.exists, 'no-encontrado', 'La asociación ya no existe.');
        const current = association.data()!;
        exigir(version(current) === solicitud.versionEsperada, 'conflicto', 'La asociación cambió; actualiza el listado antes de guardar.');
        exigir(current.sectorId === solicitud.sectorId ? sector.exists : sector.data()?.activo === true, 'datos-invalidos', 'El sector debe existir y estar activo para asignarlo.');
        if (normalizado(String(current.nombre ?? solicitud.asociacionId)) !== normalizado(solicitud.nombre)) {
          const associations = await tx.get(db.collection('asociaciones').limit(501));
          exigir(associations.size <= 500, 'limite-local', 'El catálogo supera el máximo administrable de 500 asociaciones.');
          exigir(associations.docs.every(doc => doc.id === solicitud.asociacionId || normalizado(String(doc.data().nombre ?? doc.id)) !== normalizado(solicitud.nombre)), 'conflicto', 'Ya existe otra asociación con ese nombre.');
        }
        const now = FieldValue.serverTimestamp();
        const update: DocumentData = {
          nombre: solicitud.nombre,
          nombreNormalizado: normalizado(solicitud.nombre),
          descripcion: solicitud.descripcion,
          sectorId: solicitud.sectorId,
          contacto: solicitud.contacto,
          ...(solicitud.logoUrl ? { logoUrl: solicitud.logoUrl } : { logoUrl: FieldValue.delete() }),
          actualizadoEn: now,
        };
        const before = { nombre: current.nombre, descripcion: current.descripcion, sectorId: current.sectorId, contacto: current.contacto, ...(typeof current.logoUrl === 'string' ? { logoUrl: current.logoUrl } : {}), activo: current.activo };
        const after = { nombre: solicitud.nombre, descripcion: solicitud.descripcion, sectorId: solicitud.sectorId, contacto: solicitud.contacto, ...(solicitud.logoUrl ? { logoUrl: solicitud.logoUrl } : {}), activo: current.activo };
        tx.update(associationRef, update);
        const resultado = { accion: solicitud.accion, asociacionId: solicitud.asociacionId, activo: current.activo === true };
        tx.create(receiptRef, { firma: signature, resultado, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion: solicitud.accion, documentoId: solicitud.asociacionId, antes: before, despues: after, creadoEn: now });
        return resultado;
      });
    }

    if (solicitud.accion === 'cambiarEstadoAsociacion') {
      const associationRef = db.doc(`asociaciones/${solicitud.asociacionId}`);
      return db.runTransaction(async tx => {
        const [actor, receipt, association] = await tx.getAll(actorRef, receiptRef, associationRef);
        exigir(esAdmin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) {
          exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
          return receipt.data()!.resultado as Record<string, unknown>;
        }
        exigir(association.exists, 'no-encontrado', 'La asociación ya no existe.');
        const current = association.data()!;
        exigir(version(current) === solicitud.versionEsperada, 'conflicto', 'La asociación cambió; actualiza el listado antes de guardar.');
        exigir(current.activo !== solicitud.activo, 'conflicto', solicitud.activo ? 'La asociación ya está activa.' : 'La asociación ya está inactiva.');
        const now = FieldValue.serverTimestamp();
        tx.update(associationRef, { activo: solicitud.activo, actualizadoEn: now });
        const resultado = { accion: solicitud.accion, asociacionId: solicitud.asociacionId, activo: solicitud.activo };
        tx.create(receiptRef, { firma: signature, resultado, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion: solicitud.accion, documentoId: solicitud.asociacionId, antes: { activo: current.activo }, despues: { activo: solicitud.activo }, creadoEn: now });
        return resultado;
      });
    }

    if (solicitud.accion === 'asignarEmprendedora') {
      const associationRef = db.doc(`asociaciones/${solicitud.asociacionId}`);
      const relationRef = associationRef.collection('integrantes').doc(solicitud.uidEmprendedora);
      const accessRef = db.doc(`accesos/${solicitud.uidEmprendedora}`);
      const profileRef = db.doc(`emprendedoras/${solicitud.uidEmprendedora}`);
      return db.runTransaction(async tx => {
        const [actor, receipt, association, relation, access, profile] = await tx.getAll(actorRef, receiptRef, associationRef, relationRef, accessRef, profileRef);
        exigir(esAdmin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
        if (receipt.exists) {
          exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
          return receipt.data()!.resultado as Record<string, unknown>;
        }
        exigir(association.exists && association.data()?.activo === true, 'conflicto', 'La asociación debe existir y estar activa.');
        const associationData = association.data()!;
        exigir(version(associationData) === solicitud.versionAsociacionEsperada, 'conflicto', 'La asociación cambió; actualiza antes de asignar.');
        const a = access.data();
        const p = profile.data();
        exigir(a?.estado === 'activo' && Array.isArray(a.roles) && a.roles.includes('emprendedora') && id(a.tiendaId), 'datos-invalidos', 'Solo se asignan cuentas emprendedoras activas.');
        exigir(p?.tiendaId === a.tiendaId, 'conflicto', 'La relación de la cuenta con su tienda no coincide.');
        const storeRef = db.doc(`tiendasPrivadas/${a.tiendaId}`);
        const store = await tx.get(storeRef);
        exigir(store.data()?.propietarioUid === solicitud.uidEmprendedora, 'conflicto', 'La tienda no pertenece a la emprendedora indicada.');
        if (relation.exists) {
          const relationData = relation.data()!;
          exigir(relationData.estado !== 'activa', 'conflicto', 'La emprendedora ya pertenece a esta asociación.');
          exigir(Boolean(solicitud.versionRelacionEsperada) && version(relationData) === solicitud.versionRelacionEsperada, 'conflicto', 'La relación cambió; actualiza antes de volver a asignar.');
        } else {
          exigir(!solicitud.versionRelacionEsperada, 'conflicto', 'La relación ya no está disponible; actualiza el listado.');
        }
        const now = FieldValue.serverTimestamp();
        const result = { accion: solicitud.accion, asociacionId: solicitud.asociacionId, uidEmprendedora: solicitud.uidEmprendedora, estado: 'activa' };
        const record: DocumentData = {
          tiendaId: a.tiendaId,
          estado: 'activa',
          ...(relation.exists ? {} : { asignadaPor: identidad.uid, asignadaEn: now, creadoEn: now }),
          ...(relation.exists ? { retiradaPor: FieldValue.delete(), retiradaEn: FieldValue.delete() } : {}),
          actualizadoEn: now,
        };
        if (relation.exists) tx.update(relationRef, record); else tx.create(relationRef, record);
        tx.create(receiptRef, { firma: signature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
        tx.create(auditRef, { actorUid: identidad.uid, accion: solicitud.accion, documentoId: `${solicitud.asociacionId}/${solicitud.uidEmprendedora}`, antes: relation.exists ? { estado: relation.data()?.estado } : null, despues: { estado: 'activa', tiendaId: a.tiendaId }, creadoEn: now });
        return result;
      });
    }

    const associationRef = db.doc(`asociaciones/${solicitud.asociacionId}`);
    const relationRef = associationRef.collection('integrantes').doc(solicitud.uidEmprendedora);
    return db.runTransaction(async tx => {
      const [actor, receipt, association, relation] = await tx.getAll(actorRef, receiptRef, associationRef, relationRef);
      exigir(esAdmin(actor.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      if (receipt.exists) {
        exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
        return receipt.data()!.resultado as Record<string, unknown>;
      }
      exigir(association.exists && relation.exists && relation.data()?.estado === 'activa', 'no-encontrado', 'La emprendedora no está asignada a esta asociación.');
      const relationData = relation.data()!;
      exigir(version(relationData) === solicitud.versionRelacionEsperada, 'conflicto', 'La relación cambió; actualiza antes de quitarla.');
      const now = FieldValue.serverTimestamp();
      const result = { accion: solicitud.accion, asociacionId: solicitud.asociacionId, uidEmprendedora: solicitud.uidEmprendedora, estado: 'retirada' };
      tx.update(relationRef, { estado: 'retirada', retiradaPor: identidad.uid, retiradaEn: now, actualizadoEn: now });
      tx.create(receiptRef, { firma: signature, resultado: result, creadaPor: identidad.uid, creadoEn: now });
      tx.create(auditRef, { actorUid: identidad.uid, accion: solicitud.accion, documentoId: `${solicitud.asociacionId}/${solicitud.uidEmprendedora}`, antes: { estado: relation.data()?.estado }, despues: { estado: 'retirada' }, creadoEn: now });
      return result;
    });
  };
}
