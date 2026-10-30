import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import type { DocumentData, Firestore } from 'firebase-admin/firestore';
import { ErrorOperacion, version } from './publicacion.ts';

export const accionesCatalogo = ['crearElementoCatalogo', 'editarElementoCatalogo', 'cambiarEstadoCatalogo'] as const;
export type AccionCatalogo = typeof accionesCatalogo[number];
export const coleccionesCatalogo = ['sectores', 'categorias', 'tiposEmprendimiento', 'tiposProducto'] as const;
export type ColeccionCatalogo = typeof coleccionesCatalogo[number];

type CampoTipoProducto = { clave: string; etiqueta: string; tipo: 'texto'; obligatorio: boolean };
type DefinicionTipoProducto = { campos: CampoTipoProducto[]; opcionesVariante: string[] };
type SolicitudCatalogo =
  | { accion: 'crearElementoCatalogo'; operacionId: string; coleccion: ColeccionCatalogo; nombre: string; id: string; definicion?: DefinicionTipoProducto }
  | { accion: 'editarElementoCatalogo'; operacionId: string; coleccion: ColeccionCatalogo; documentoId: string; versionEsperada: string; nombre: string; definicion?: DefinicionTipoProducto }
  | { accion: 'cambiarEstadoCatalogo'; operacionId: string; coleccion: ColeccionCatalogo; documentoId: string; versionEsperada: string; activo: boolean };

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
  exigir(typeof value === 'string' && value.trim().length > 0 && value.trim().length <= 100, 'datos-invalidos', 'El nombre es obligatorio y admite hasta 100 caracteres.');
  return value.trim();
}

function slug(value: string): string {
  const normalized = value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 80);
  exigir(normalized.length > 0, 'datos-invalidos', 'El nombre debe incluir letras o números.');
  return normalized;
}

function nombreNormalizado(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-CL')
    .replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+/g, ' ');
}

function coleccion(value: unknown): ColeccionCatalogo {
  exigir(typeof value === 'string' && coleccionesCatalogo.includes(value as ColeccionCatalogo), 'datos-invalidos', 'Tipo de catálogo no permitido.');
  return value as ColeccionCatalogo;
}

function versionEsperada(value: unknown): string {
  exigir(typeof value === 'string' && /^[0-9]{1,12}:[0-9]{1,9}$/.test(value), 'datos-invalidos', 'Se requiere la versión vigente del documento.');
  return value;
}

function definirTipoProducto(value: unknown): DefinicionTipoProducto {
  const input = objeto(value);
  camposExactos(input, ['campos', 'opcionesVariante']);
  exigir(Array.isArray(input.campos) && input.campos.length <= 20, 'datos-invalidos', 'Un tipo admite hasta 20 campos propios.');
  exigir(Array.isArray(input.opcionesVariante) && input.opcionesVariante.length <= 10, 'datos-invalidos', 'Un tipo admite hasta 10 opciones de variante.');
  const definiciones = input.campos.map((item) => {
    const campo = objeto(item);
    camposExactos(campo, ['clave', 'etiqueta', 'tipo', 'obligatorio']);
    exigir(typeof campo.clave === 'string' && /^[a-z][a-zA-Z0-9_]{0,39}$/.test(campo.clave), 'datos-invalidos', 'La clave debe comenzar con minúscula y usar letras, números o guion bajo.');
    exigir(typeof campo.etiqueta === 'string' && campo.etiqueta.trim().length > 0 && campo.etiqueta.trim().length <= 80, 'datos-invalidos', 'La etiqueta del campo es obligatoria y admite hasta 80 caracteres.');
    exigir(campo.tipo === 'texto' && typeof campo.obligatorio === 'boolean', 'datos-invalidos', 'Por ahora los atributos del producto son de texto.');
    return { clave: campo.clave, etiqueta: campo.etiqueta.trim(), tipo: 'texto' as const, obligatorio: campo.obligatorio };
  });
  exigir(new Set(definiciones.map(field => field.clave)).size === definiciones.length, 'datos-invalidos', 'Las claves de campos no se pueden repetir.');
  const opciones = input.opcionesVariante.map((item) => {
    exigir(typeof item === 'string' && item.trim().length > 0 && item.trim().length <= 40, 'datos-invalidos', 'Cada opción de variante admite hasta 40 caracteres.');
    return item.trim();
  });
  exigir(new Set(opciones.map(item => item.toLocaleLowerCase('es-CL'))).size === opciones.length, 'datos-invalidos', 'Las opciones de variante no se pueden repetir.');
  return { campos: definiciones, opcionesVariante: opciones };
}

function canon(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canon).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canon((value as Record<string, unknown>)[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function esAdmin(data: DocumentData | undefined) {
  return data?.estado === 'activo' && Array.isArray(data.roles) && data.roles.includes('administrador');
}

function parsearSolicitud(accion: AccionCatalogo, input: unknown): SolicitudCatalogo {
  const data = objeto(input);
  const base = ['operacionId', 'coleccion', 'nombre'];
  if (accion === 'crearElementoCatalogo') {
    camposExactos(data, base, ['definicion']);
    exigir(id(data.operacionId), 'datos-invalidos', 'ID de operación inválido.');
    const tipo = coleccion(data.coleccion);
    exigir((tipo === 'tiposProducto') === Object.hasOwn(data, 'definicion'), 'datos-invalidos', 'Los tipos de producto requieren una definición versionada.');
    return { accion, operacionId: data.operacionId as string, coleccion: tipo, nombre: nombre(data.nombre), id: slug(nombre(data.nombre)), ...(tipo === 'tiposProducto' ? { definicion: definirTipoProducto(data.definicion) } : {}) };
  }
  if (accion === 'editarElementoCatalogo') {
    camposExactos(data, [...base, 'documentoId', 'versionEsperada'], ['definicion']);
    exigir(id(data.operacionId) && id(data.documentoId), 'datos-invalidos', 'IDs inválidos.');
    const tipo = coleccion(data.coleccion);
    versionEsperada(data.versionEsperada);
    exigir((tipo === 'tiposProducto') === Object.hasOwn(data, 'definicion'), 'datos-invalidos', 'Los tipos de producto requieren una definición versionada.');
    return { accion, operacionId: data.operacionId as string, coleccion: tipo, documentoId: data.documentoId as string, versionEsperada: data.versionEsperada as string, nombre: nombre(data.nombre), ...(tipo === 'tiposProducto' ? { definicion: definirTipoProducto(data.definicion) } : {}) };
  }
  camposExactos(data, ['operacionId', 'coleccion', 'documentoId', 'versionEsperada', 'activo']);
  exigir(id(data.operacionId) && id(data.documentoId) && typeof data.activo === 'boolean', 'datos-invalidos', 'IDs o estado inválidos.');
  return { accion, operacionId: data.operacionId as string, coleccion: coleccion(data.coleccion), documentoId: data.documentoId as string, versionEsperada: versionEsperada(data.versionEsperada), activo: data.activo };
}

export function crearCatalogos(db: Firestore) {
  return async (accion: AccionCatalogo, identidad: { uid: string }, input: unknown) => {
    exigir(accionesCatalogo.includes(accion), 'datos-invalidos', 'Operación de catálogo desconocida.');
    exigir(identidad && id(identidad.uid), 'no-autenticado', 'Identidad verificada requerida.');
    const solicitud = parsearSolicitud(accion, input);
    const adminRef = db.doc(`accesos/${identidad.uid}`);
    const operationId = solicitud.operacionId;
    const receiptRef = db.doc(`operacionesAdministracion/${identidad.uid}_${operationId}`);
    const signature = createHash('sha256').update(canon([solicitud.accion, solicitud])).digest('hex');
    const documentId = solicitud.accion === 'crearElementoCatalogo' ? solicitud.id : solicitud.documentoId;
    const itemRef = db.doc(`${solicitud.coleccion}/${documentId}`);
    const auditRef = db.doc(`auditoriaAdministracion/${identidad.uid}_${operationId}`);

    return db.runTransaction(async tx => {
      const [adminSnapshot, receipt, item] = await tx.getAll(adminRef, receiptRef, itemRef);
      exigir(esAdmin(adminSnapshot.data()), 'sin-permiso', 'Se requiere una cuenta administradora activa.');
      if (receipt.exists) {
        exigir(receipt.data()!.firma === signature, 'id-reutilizado', 'operacionId corresponde a otra solicitud.');
        return receipt.data()!.resultado as Record<string, unknown>;
      }

      const now = FieldValue.serverTimestamp();
      let resultado: Record<string, unknown>;
      let before: Record<string, unknown> | null = null;
      let after: Record<string, unknown>;
      if (solicitud.accion === 'crearElementoCatalogo') {
        exigir(!item.exists, 'conflicto', 'Ya existe un valor con ese ID. Cambia el nombre o edita el registro existente.');
        const currentCatalog = await tx.get(db.collection(solicitud.coleccion).limit(501));
        exigir(currentCatalog.size <= 500, 'limite-local', 'El catálogo supera el máximo administrable de 500 valores.');
        exigir(currentCatalog.docs.every(existing => nombreNormalizado(String(existing.data().nombre ?? existing.id)) !== nombreNormalizado(solicitud.nombre)), 'conflicto', 'Ya existe un valor con ese nombre en este catálogo.');
        after = { nombre: solicitud.nombre, nombreNormalizado: nombreNormalizado(solicitud.nombre), activo: true, creadoEn: now, actualizadoEn: now };
        if (solicitud.coleccion === 'tiposProducto') {
          after.versionActual = 1;
          const definitionRef = itemRef.collection('versiones').doc('1');
          tx.create(itemRef, after);
          tx.create(definitionRef, { version: 1, ...solicitud.definicion, creadoEn: now, actualizadoEn: now });
        } else {
          tx.create(itemRef, after);
        }
        resultado = { accion, coleccion: solicitud.coleccion, documentoId: documentId, activo: true, versionActual: solicitud.coleccion === 'tiposProducto' ? 1 : null };
      } else {
        exigir(item.exists, 'no-encontrado', 'El valor de catálogo ya no existe. Actualiza el listado.');
        const current = item.data()!;
        exigir(version(current) === solicitud.versionEsperada, 'conflicto', 'El valor cambió; actualiza el listado antes de guardar.');
        before = { nombre: current.nombre, activo: current.activo, ...(current.versionActual ? { versionActual: current.versionActual } : {}) };
        if (solicitud.accion === 'editarElementoCatalogo') {
          const normalized = nombreNormalizado(solicitud.nombre);
          const nombreActual = nombreNormalizado(String(current.nombre ?? documentId));
          if (nombreActual !== normalized) {
            const currentCatalog = await tx.get(db.collection(solicitud.coleccion).limit(501));
            exigir(currentCatalog.size <= 500, 'limite-local', 'El catálogo supera el máximo administrable de 500 valores.');
            exigir(currentCatalog.docs.every(existing => existing.id === documentId || nombreNormalizado(String(existing.data().nombre ?? existing.id)) !== normalized), 'conflicto', 'Ya existe otro valor con ese nombre en este catálogo.');
          }
          const update: DocumentData = { nombre: solicitud.nombre, nombreNormalizado: normalized, actualizadoEn: now };
          let currentVersion = typeof current.versionActual === 'number' ? current.versionActual : null;
          if (solicitud.coleccion === 'tiposProducto') {
            exigir(Number.isSafeInteger(currentVersion) && Number(currentVersion) > 0, 'datos-invalidos', 'El tipo de producto no tiene una versión de definición válida.');
            const currentDefinitionRef = itemRef.collection('versiones').doc(String(currentVersion));
            const currentDefinition = await tx.get(currentDefinitionRef);
            exigir(currentDefinition.exists, 'datos-invalidos', 'No se encontró la definición vigente del tipo de producto.');
            const oldSchema = { campos: currentDefinition.data()!.campos, opcionesVariante: currentDefinition.data()!.opcionesVariante };
            const changedSchema = canon(oldSchema) !== canon(solicitud.definicion);
            if (changedSchema) {
              currentVersion = Number(currentVersion) + 1;
              exigir(currentVersion <= 1000, 'limite-local', 'Se alcanzó el máximo de versiones de este tipo de producto.');
              update.versionActual = currentVersion;
              const newDefinitionRef = itemRef.collection('versiones').doc(String(currentVersion));
              tx.create(newDefinitionRef, { version: currentVersion, ...solicitud.definicion, creadoEn: now, actualizadoEn: now });
            }
          }
          tx.update(itemRef, update);
          after = { ...before, nombre: solicitud.nombre, ...(currentVersion ? { versionActual: currentVersion } : {}) };
          resultado = { accion, coleccion: solicitud.coleccion, documentoId: documentId, activo: current.activo === true, versionActual: currentVersion };
        } else {
          exigir(current.activo !== solicitud.activo, 'conflicto', solicitud.activo ? 'El valor ya está activo.' : 'El valor ya está inactivo.');
          tx.update(itemRef, { activo: solicitud.activo, actualizadoEn: now });
          after = { ...before, activo: solicitud.activo };
          resultado = { accion, coleccion: solicitud.coleccion, documentoId: documentId, activo: solicitud.activo, versionActual: current.versionActual ?? null };
        }
      }

      tx.create(receiptRef, { firma: signature, resultado, creadaPor: identidad.uid, creadoEn: now });
      tx.create(auditRef, { actorUid: identidad.uid, accion: solicitud.accion, coleccion: solicitud.coleccion, documentoId: documentId, antes: before, despues: after!, creadoEn: now });
      return resultado;
    });
  };
}
