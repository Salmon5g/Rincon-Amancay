import { collection, doc, getDocFromServer, getDocs, getDocsFromServer, query, where, limit } from 'firebase/firestore'
import type { DocumentData, Firestore, QueryDocumentSnapshot, Timestamp } from 'firebase/firestore'
import type { Auth } from 'firebase/auth'
import type { Operacion } from '~/lib/cliente-base'
import type {
  Acceso,
  Asociacion,
  Catalogos,
  DatosAdmin,
  ElementoCatalogo,
  CampoTipoProducto,
  ContactoAsociacion,
  EmprendedoraAdmin,
  IntegrantesAsociacion,
  InvitacionEmprendedoraAdmin,
  Emprendedora,
  Invitacion,
  InvitacionAlta,
  AdministradorPlataforma,
  ConfiguracionNavegacion,
  ModoNavegacionComprador,
  FeriaAdmin,
  ParticipantesFeria,
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

export async function listarEmprendedorasAdmin(cliente: ClienteAdminConApi): Promise<{
  emprendedoras: EmprendedoraAdmin[]
  invitaciones: InvitacionEmprendedoraAdmin[]
}> {
  await comprobarAdmin(cliente)
  const data = await cliente.llamar('listarEmprendedorasAdmin', {})
  if (!Array.isArray(data.emprendedoras) || !Array.isArray(data.invitaciones)) {
    throw new Error('La API no devolvió las cuentas de emprendedoras.')
  }
  const emprendedoras = data.emprendedoras.map((value): EmprendedoraAdmin => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('La API devolvió una cuenta inválida.')
    const account = value as Record<string, unknown>
    if (typeof account.uid !== 'string' || typeof account.tiendaId !== 'string' || typeof account.estado !== 'string'
      || typeof account.consistente !== 'boolean') throw new Error('La API devolvió una cuenta incompleta.')
    return {
      uid: account.uid,
      correo: typeof account.correo === 'string' ? account.correo : '',
      nombreMostrar: typeof account.nombreMostrar === 'string' ? account.nombreMostrar : '',
      nombreTienda: typeof account.nombreTienda === 'string' ? account.nombreTienda : 'Tienda sin ficha',
      tiendaId: account.tiendaId,
      estado: account.estado,
      consistente: account.consistente,
      ...(typeof account.versionAcceso === 'string' ? { versionAcceso: account.versionAcceso } : {}),
      ...(typeof account.versionPerfil === 'string' ? { versionPerfil: account.versionPerfil } : {}),
    }
  })
  const invitaciones = data.invitaciones.map((value): InvitacionEmprendedoraAdmin => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('La API devolvió una invitación inválida.')
    const invite = value as Record<string, unknown>
    if (typeof invite.invitacionId !== 'string' || typeof invite.estado !== 'string') throw new Error('La API devolvió una invitación incompleta.')
    return {
      invitacionId: invite.invitacionId,
      correo: typeof invite.correo === 'string' ? invite.correo : '',
      nombreTienda: typeof invite.nombreTienda === 'string' ? invite.nombreTienda : '',
      estado: invite.estado,
    }
  })
  return { emprendedoras, invitaciones }
}

export function editarCuentaEmprendedora(cliente: ClienteAdminConApi, solicitud: {
  uidDestino: string
  operacionId: string
  versionEsperada: string
  nombreMostrar: string
}) {
  return cliente.llamar('editarCuentaEmprendedora', solicitud)
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

function mapearAsociacion(value: unknown): Asociacion {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('La API devolvió una asociación inválida.')
  const data = value as Record<string, unknown>
  const contacto = data.contacto as Partial<ContactoAsociacion> | undefined
  if (typeof data.id !== 'string' || typeof data.nombre !== 'string' || typeof data.sectorId !== 'string'
    || typeof data.activo !== 'boolean' || !contacto || !['whatsapp', 'correo', 'web'].includes(String(contacto.tipo))
    || typeof contacto.valor !== 'string') throw new Error('La API devolvió una asociación incompleta.')
  return {
    id: data.id,
    nombre: data.nombre,
    descripcion: typeof data.descripcion === 'string' ? data.descripcion : '',
    sectorId: data.sectorId,
    contacto: { tipo: contacto.tipo as ContactoAsociacion['tipo'], valor: contacto.valor },
    ...(typeof data.logoUrl === 'string' ? { logoUrl: data.logoUrl } : {}),
    activo: data.activo,
    ...(typeof data.versionEsperada === 'string' ? { versionEsperada: data.versionEsperada } : {}),
  }
}

export async function listarAsociacionesAdmin(cliente: ClienteAdminConApi): Promise<Asociacion[]> {
  await comprobarAdmin(cliente)
  const data = await cliente.llamar('listarAsociaciones', {})
  if (!Array.isArray(data.asociaciones)) throw new Error('La API no devolvió el listado de asociaciones.')
  return data.asociaciones.map(mapearAsociacion)
}

export async function leerIntegrantesAsociacion(cliente: ClienteAdminConApi, asociacionId: string): Promise<IntegrantesAsociacion> {
  const data = await cliente.llamar('leerIntegrantesAsociacion', { asociacionId })
  if (!data.asociacion || !Array.isArray(data.miembros) || !Array.isArray(data.disponibles)) {
    throw new Error('La API no devolvió los integrantes de la asociación.')
  }
  return data as unknown as IntegrantesAsociacion
}

export type SolicitudAsociacion = {
  nombre: string
  descripcion: string
  sectorId: string
  contacto: ContactoAsociacion
  logoUrl?: string
}

export function crearAsociacion(cliente: ClienteAdminConApi, solicitud: SolicitudAsociacion & { operacionId: string }) {
  return cliente.llamar('crearAsociacion', solicitud)
}

export function editarAsociacion(cliente: ClienteAdminConApi, solicitud: SolicitudAsociacion & {
  operacionId: string; asociacionId: string; versionEsperada: string
}) {
  return cliente.llamar('editarAsociacion', solicitud)
}

export function cambiarEstadoAsociacion(cliente: ClienteAdminConApi, solicitud: {
  operacionId: string; asociacionId: string; versionEsperada: string; activo: boolean
}) {
  return cliente.llamar('cambiarEstadoAsociacion', solicitud)
}

export function asignarEmprendedora(cliente: ClienteAdminConApi, solicitud: {
  operacionId: string; asociacionId: string; versionAsociacionEsperada: string
  uidEmprendedora: string; versionRelacionEsperada?: string
}) {
  return cliente.llamar('asignarEmprendedora', solicitud)
}

export function quitarEmprendedora(cliente: ClienteAdminConApi, solicitud: {
  operacionId: string; asociacionId: string; uidEmprendedora: string; versionRelacionEsperada: string
}) {
  return cliente.llamar('quitarEmprendedora', solicitud)
}

export async function listarAdministradoresPlataforma(cliente: ClienteAdminConApi): Promise<AdministradorPlataforma[]> {
  await comprobarAdmin(cliente)
  const data = await cliente.llamar('listarAdministradores', {})
  if (!Array.isArray(data.administradores)) throw new Error('La API no devolvió las cuentas administradoras.')
  return data.administradores.map((value): AdministradorPlataforma => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('La API devolvió una cuenta administradora inválida.')
    const item = value as Record<string, unknown>
    if (typeof item.uid !== 'string' || typeof item.estado !== 'string' || typeof item.consistente !== 'boolean') throw new Error('La API devolvió una cuenta administradora incompleta.')
    return {
      uid: item.uid,
      correo: texto(item.correo),
      nombreMostrar: texto(item.nombreMostrar),
      estado: item.estado === 'activo' ? 'activo' : 'desactivado',
      roles: listaDeTextos(item.roles),
      consistente: item.consistente,
      versionEsperada: texto(item.versionEsperada),
    }
  })
}

export function agregarAdministradorPlataforma(cliente: ClienteAdminConApi, solicitud: { operacionId: string; correo: string }) {
  return cliente.llamar('agregarAdministrador', solicitud)
}

export function quitarAdministradorPlataforma(cliente: ClienteAdminConApi, solicitud: { operacionId: string; uidDestino: string; versionEsperada: string }) {
  return cliente.llamar('quitarAdministrador', solicitud)
}

export async function leerConfiguracionNavegacion(cliente: ClienteAdminConApi): Promise<ConfiguracionNavegacion> {
  await comprobarAdmin(cliente)
  const data = await cliente.llamar('leerConfiguracionNavegacion', {})
  if (!['permitir', 'invitar', 'exigir'].includes(String(data.modo)) || typeof data.versionEsperada !== 'string') throw new Error('La API devolvió una configuración de acceso inválida.')
  return { modo: data.modo as ModoNavegacionComprador, versionEsperada: data.versionEsperada, actualizadoEn: typeof data.actualizadoEn === 'string' ? data.actualizadoEn : null }
}

export function guardarConfiguracionNavegacion(cliente: ClienteAdminConApi, solicitud: { operacionId: string; versionEsperada: string; modo: ModoNavegacionComprador }) {
  return cliente.llamar('guardarConfiguracionNavegacion', solicitud)
}

function mapearFeria(value: unknown): FeriaAdmin {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('La API devolvió una feria inválida.')
  const data = value as Record<string, unknown>
  if (typeof data.id !== 'string' || typeof data.nombre !== 'string' || typeof data.estado !== 'string' || typeof data.versionEsperada !== 'string') throw new Error('La API devolvió una feria incompleta.')
  return {
    id: data.id, nombre: data.nombre, descripcion: texto(data.descripcion), organizador: texto(data.organizador),
    direccion: texto(data.direccion), fechaInicio: typeof data.fechaInicio === 'string' ? data.fechaInicio : null,
    fechaFin: typeof data.fechaFin === 'string' ? data.fechaFin : null, imagenUrl: texto(data.imagenUrl),
    latitud: typeof data.latitud === 'number' ? data.latitud : null, longitud: typeof data.longitud === 'number' ? data.longitud : null,
    estado: data.estado === 'publicada' || data.estado === 'cancelada' ? data.estado : 'borrador', versionEsperada: data.versionEsperada,
  }
}

export async function listarFeriasAdmin(cliente: ClienteAdminConApi): Promise<FeriaAdmin[]> {
  await comprobarAdmin(cliente)
  const data = await cliente.llamar('listarFeriasAdmin', {})
  if (!Array.isArray(data.ferias)) throw new Error('La API no devolvió las ferias.')
  return data.ferias.map(mapearFeria)
}

export async function leerParticipantesFeria(cliente: ClienteAdminConApi, feriaId: string): Promise<ParticipantesFeria> {
  const data = await cliente.llamar('leerParticipantesFeria', { feriaId })
  if (!data.feria || !Array.isArray(data.participantes) || !Array.isArray(data.disponibles)) throw new Error('La API no devolvió los participantes de la feria.')
  return data as unknown as ParticipantesFeria
}

export function crearFeria(cliente: ClienteAdminConApi, solicitud: Record<string, unknown>) { return cliente.llamar('crearFeria', solicitud) }
export function editarFeria(cliente: ClienteAdminConApi, solicitud: Record<string, unknown>) { return cliente.llamar('editarFeria', solicitud) }
export function cambiarEstadoFeria(cliente: ClienteAdminConApi, solicitud: { operacionId: string; feriaId: string; versionEsperada: string; estado: 'publicada' | 'cancelada' }) { return cliente.llamar('cambiarEstadoFeria', solicitud) }
export function invitarParticipanteFeria(cliente: ClienteAdminConApi, solicitud: { operacionId: string; feriaId: string; versionFeriaEsperada: string; tipo: 'emprendedora' | 'asociacion'; entidadId: string }) { return cliente.llamar('invitarParticipanteFeria', solicitud) }
export function agregarParticipanteFeria(cliente: ClienteAdminConApi, solicitud: { operacionId: string; feriaId: string; versionFeriaEsperada: string; tipo: 'emprendedora' | 'asociacion'; entidadId: string }) { return cliente.llamar('agregarParticipanteFeria', solicitud) }
export function retirarParticipanteFeria(cliente: ClienteAdminConApi, solicitud: { operacionId: string; feriaId: string; versionFeriaEsperada: string; participanteId: string }) { return cliente.llamar('retirarParticipanteFeria', solicitud) }
export function registrarAsistenciaFeria(cliente: ClienteAdminConApi, solicitud: { operacionId: string; feriaId: string; versionFeriaEsperada: string; participanteId: string; asistencia: 'asistio' | 'no_asistio' }) { return cliente.llamar('registrarAsistenciaFeria', solicitud) }

export function responderInvitacionFeria(cliente: ClienteAdminConApi, solicitud: { feriaId: string; respuesta: 'confirmar' | 'rechazar' }) {
  return cliente.llamar('responderInvitacionFeria', solicitud)
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
  return { accesos, emprendedoras, invitaciones, catalogos, asociaciones: [] }
}
