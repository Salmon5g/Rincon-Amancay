import { collection, doc, getDocFromServer, getDocs, getDocsFromServer, query, where, limit } from 'firebase/firestore'
import type { DocumentData, Firestore, QueryDocumentSnapshot, Timestamp } from 'firebase/firestore'
import type { Auth } from 'firebase/auth'
import type { Operacion } from '~/lib/cliente-base'
import type {
  Acceso,
  Catalogos,
  DatosAdmin,
  ElementoCatalogo,
  CampoTipoProducto,
  Emprendedora,
  Invitacion,
  InvitacionAlta,
} from '~/types/admin'

// Estas lecturas requieren solo Firestore y funcionan con cliente local o real.
export type ClienteAmancay = { firebase: { db: Firestore; auth: Auth } }
export type ClienteAdminConApi = ClienteAmancay & {
  llamar: (operacion: Operacion, solicitud: Record<string, unknown>) => Promise<Record<string, unknown>>
}

function texto(valor: unknown, alterno = ''): string {
  return typeof valor === 'string' ? valor : alterno
}

function listaDeTextos(valor: unknown): string[] {
  return Array.isArray(valor) ? valor.filter((v): v is string => typeof v === 'string') : []
}

function comoFecha(valor: unknown): Date | null {
  return valor && typeof (valor as Timestamp).toDate === 'function'
    ? (valor as Timestamp).toDate()
    : null
}

function versionDe(datos: DocumentData): string | undefined {
  const actualizadoEn = datos.actualizadoEn as Timestamp | undefined
  return actualizadoEn && typeof actualizadoEn.seconds === 'number' && typeof actualizadoEn.nanoseconds === 'number'
    ? `${actualizadoEn.seconds}:${actualizadoEn.nanoseconds}`
    : undefined
}

function mapearAcceso(snap: QueryDocumentSnapshot<DocumentData>): Acceso {
  const datos = snap.data()
  const acceso: Acceso = {
    uid: snap.id,
    roles: listaDeTextos(datos.roles),
    estado: datos.estado === 'desactivado' ? 'desactivado' : 'activo',
  }
  if (typeof datos.tiendaId === 'string') acceso.tiendaId = datos.tiendaId
  if (typeof datos.operacionCuentaPendiente === 'string') {
    acceso.operacionCuentaPendiente = datos.operacionCuentaPendiente
  }
  return acceso
}

function mapearEmprendedora(snap: QueryDocumentSnapshot<DocumentData>): Emprendedora | null {
  const datos = snap.data()
  if (typeof datos.tiendaId !== 'string') return null
  const emprendedora: Emprendedora = { uid: snap.id, tiendaId: datos.tiendaId }
  if (typeof datos.creadaPor === 'string') emprendedora.creadaPor = datos.creadaPor
  return emprendedora
}

function mapearInvitacion(snap: QueryDocumentSnapshot<DocumentData>): Invitacion {
  const datos = snap.data()
  const alta = (datos.alta ?? {}) as Partial<InvitacionAlta>
  return {
    id: snap.id,
    correo: texto(datos.correo),
    estado: texto(datos.estado, 'pendiente'),
    alta: {
      nombreMostrar: texto(alta.nombreMostrar),
      nombreTienda: texto(alta.nombreTienda),
      descripcion: texto(alta.descripcion),
      tiendaId: texto(alta.tiendaId),
      sectorId: texto(alta.sectorId),
      tipoEmprendimientoId: texto(alta.tipoEmprendimientoId),
    },
    venceEn: comoFecha(datos.venceEn),
  }
}

async function leerCatalogo(cliente: ClienteAmancay, coleccion: string): Promise<ElementoCatalogo[]> {
  // Las reglas solo permiten listar documentos activos: la consulta debe
  // expresar esa condición incluso si la colección todavía está vacía.
  const snap = await getDocs(query(collection(cliente.firebase.db, coleccion), where('activo', '==', true)))
  return snap.docs.map((doc) => {
    const datos = doc.data()
    return {
      id: doc.id,
      nombre: texto(datos.nombre, doc.id),
      activo: datos.activo === true,
      ...(versionDe(datos) ? { versionEsperada: versionDe(datos) } : {}),
    }
  })
}

async function comprobarAdmin(cliente: ClienteAmancay): Promise<void> {
  await cliente.firebase.auth.authStateReady()
  const uid = cliente.firebase.auth.currentUser?.uid
  if (!uid) throw new Error('Inicia sesión con una cuenta administradora.')
  const snapshot = await getDocFromServer(doc(cliente.firebase.db, 'accesos', uid))
  if (!snapshot.exists() || !esAdministrador(snapshot.data())) throw new Error('Se requiere una cuenta administradora activa.')
}

function esAdministrador(acceso: Record<string, unknown> | undefined): boolean {
  return acceso?.estado === 'activo' && Array.isArray(acceso.roles) && acceso.roles.includes('administrador')
}

async function leerCatalogoAdmin(cliente: ClienteAmancay, nombreColeccion: string): Promise<ElementoCatalogo[]> {
  const resultado = await getDocsFromServer(query(collection(cliente.firebase.db, nombreColeccion), limit(200)))
  const elementos = resultado.docs.map((snapshot) => {
    const datos = snapshot.data()
    return {
      id: snapshot.id,
      nombre: texto(datos.nombre, snapshot.id),
      activo: datos.activo === true,
      ...(versionDe(datos) ? { versionEsperada: versionDe(datos) } : {}),
    }
  })
  if (nombreColeccion !== 'tiposProducto') return elementos

  return Promise.all(elementos.map(async (elemento, index) => {
    const documento = resultado.docs[index]
    if (!documento) throw new Error('El listado de tipos de producto quedó incompleto. Actualiza la página.')
    const versionActual = Number(documento.data().versionActual)
    if (!Number.isSafeInteger(versionActual) || versionActual < 1) {
      throw new Error(`El tipo de producto “${elemento.nombre}” no tiene una versión válida.`)
    }
    const definicion = await getDocFromServer(doc(cliente.firebase.db, 'tiposProducto', elemento.id, 'versiones', String(versionActual)))
    if (!definicion.exists()) throw new Error(`No se encontró la definición vigente de “${elemento.nombre}”.`)
    const datos = definicion.data()
    const campos = Array.isArray(datos.campos) ? datos.campos.filter((campo): campo is CampoTipoProducto =>
      !!campo && typeof campo.clave === 'string' && typeof campo.etiqueta === 'string'
      && campo.tipo === 'texto' && typeof campo.obligatorio === 'boolean') : []
    const opcionesVariante = Array.isArray(datos.opcionesVariante)
      ? datos.opcionesVariante.filter((opcion): opcion is string => typeof opcion === 'string') : []
    return { ...elemento, versionActual, campos, opcionesVariante }
  }))
}

export async function listarAccesos(cliente: ClienteAmancay): Promise<Acceso[]> {
  const snap = await getDocs(collection(cliente.firebase.db, 'accesos'))
  return snap.docs.map(mapearAcceso)
}

export async function listarEmprendedoras(cliente: ClienteAmancay): Promise<Emprendedora[]> {
  const snap = await getDocs(collection(cliente.firebase.db, 'emprendedoras'))
  return snap.docs.map(mapearEmprendedora).filter((e): e is Emprendedora => e !== null)
}

export async function listarInvitaciones(cliente: ClienteAmancay): Promise<Invitacion[]> {
  const snap = await getDocs(collection(cliente.firebase.db, 'invitaciones'))
  return snap.docs.map(mapearInvitacion)
}

export async function listarCatalogos(cliente: ClienteAmancay): Promise<Catalogos> {
  const [sectores, categorias, tiposEmprendimiento, tiposProducto] = await Promise.all([
    leerCatalogo(cliente, 'sectores'),
    leerCatalogo(cliente, 'categorias'),
    leerCatalogo(cliente, 'tiposEmprendimiento'),
    leerCatalogo(cliente, 'tiposProducto'),
  ])
  return { sectores, categorias, tiposEmprendimiento, tiposProducto }
}

// Lectura administrativa de todos los estados. Firestore Rules vuelven a
// validar el rol; esta comprobación solo entrega un error de interfaz claro.
export async function listarCatalogosAdmin(cliente: ClienteAmancay): Promise<Catalogos> {
  await comprobarAdmin(cliente)
  const [sectores, categorias, tiposEmprendimiento, tiposProducto] = await Promise.all([
    leerCatalogoAdmin(cliente, 'sectores'),
    leerCatalogoAdmin(cliente, 'categorias'),
    leerCatalogoAdmin(cliente, 'tiposEmprendimiento'),
    leerCatalogoAdmin(cliente, 'tiposProducto'),
  ])
  return { sectores, categorias, tiposEmprendimiento, tiposProducto }
}

export type ColeccionCatalogoEditable = 'sectores' | 'categorias' | 'tiposEmprendimiento' | 'tiposProducto'
export type DefinicionCatalogoProducto = { campos: CampoTipoProducto[]; opcionesVariante: string[] }

export function crearValorCatalogo(cliente: ClienteAdminConApi, solicitud: {
  operacionId: string; coleccion: ColeccionCatalogoEditable; nombre: string
  definicion?: DefinicionCatalogoProducto
}) {
  return cliente.llamar('crearElementoCatalogo', solicitud)
}

export function editarValorCatalogo(cliente: ClienteAdminConApi, solicitud: {
  operacionId: string; coleccion: ColeccionCatalogoEditable; documentoId: string
  versionEsperada: string; nombre: string; definicion?: DefinicionCatalogoProducto
}) {
  return cliente.llamar('editarElementoCatalogo', solicitud)
}

export function cambiarEstadoValorCatalogo(cliente: ClienteAdminConApi, solicitud: {
  operacionId: string; coleccion: ColeccionCatalogoEditable; documentoId: string
  versionEsperada: string; activo: boolean
}) {
  return cliente.llamar('cambiarEstadoCatalogo', solicitud)
}

// Lectura completa del panel. Las reglas exigen administrador activo para accesos,
// emprendedoras e invitaciones; los catálogos son de lectura pública.
export async function cargarDatosAdmin(cliente: ClienteAmancay): Promise<DatosAdmin> {
  const [accesos, emprendedoras, invitaciones, catalogos] = await Promise.all([
    listarAccesos(cliente),
    listarEmprendedoras(cliente),
    listarInvitaciones(cliente),
    listarCatalogos(cliente),
  ])
  return { accesos, emprendedoras, invitaciones, catalogos }
}
