import { collection, getDocs, query, where } from 'firebase/firestore'
import type { DocumentData, Firestore, QueryDocumentSnapshot, Timestamp } from 'firebase/firestore'
import type {
  Acceso,
  Catalogos,
  DatosAdmin,
  ElementoCatalogo,
  Emprendedora,
  Invitacion,
  InvitacionAlta,
} from '~/types/admin'

// Estas lecturas requieren solo Firestore y funcionan con cliente local o real.
export type ClienteAmancay = { firebase: { db: Firestore } }

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
    }
  })
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
