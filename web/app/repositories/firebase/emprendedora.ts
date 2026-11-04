import { collection, doc, getDocFromServer, getDocsFromServer, query, where } from 'firebase/firestore'
import type { Timestamp } from 'firebase/firestore'
import { versionDocumento } from '~/lib/cliente-base'
import type { ServiciosCliente } from '~/lib/cliente-base'

export type ProductoEmprendedoraWeb = {
  id: string
  nombre: string
  descripcion: string
  estado: 'publicado' | 'borrador' | 'archivado'
  precioBase: number | null
  stock: number | null
  manejaStock: boolean
  modalidad: string
  imagenPublica?: string
}

export type ContactoEmprendedoraWeb = {
  tipo: 'whatsapp' | 'correo' | 'web'
  valor: string
}

export type CuentaEmprendedoraWeb = {
  uid: string
  correo: string
  nombreMostrar: string
  tienda: {
    id: string
    nombre: string
    descripcion: string
    tipoEmprendimientoId: string
    sectorId: string
    contacto: ContactoEmprendedoraWeb | null
    mostrarContacto: boolean
    versionEsperada: string
    estadoPublicacion: string
    tieneLogo: boolean
    logoPublico: string | null
    contactoVisible: boolean
  }
  productos: ProductoEmprendedoraWeb[]
}

function texto(valor: unknown, alternativo = ''): string {
  return typeof valor === 'string' ? valor : alternativo
}

function numero(valor: unknown): number | null {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null
}

function contactoEmprendedora(valor: unknown): ContactoEmprendedoraWeb | null {
  if (!valor || typeof valor !== 'object' || Array.isArray(valor)) return null
  const datos = valor as Record<string, unknown>
  if (!['whatsapp', 'correo', 'web'].includes(String(datos.tipo)) || typeof datos.valor !== 'string' || !datos.valor.trim()) return null
  return { tipo: datos.tipo as ContactoEmprendedoraWeb['tipo'], valor: datos.valor }
}

function versionTienda(valor: unknown): string {
  if (!valor || typeof valor !== 'object' || !('seconds' in valor) || !('nanoseconds' in valor)) return ''
  const timestamp = valor as Timestamp
  return versionDocumento(timestamp)
}

export async function cargarCuentaEmprendedora(firebase: ServiciosCliente, uid: string): Promise<CuentaEmprendedoraWeb> {
  const { db, auth } = firebase
  const usuario = auth.currentUser
  if (!usuario || usuario.uid !== uid) throw new Error('La sesión no coincide con la cuenta solicitada.')

  const [accesoDoc, perfilDoc] = await Promise.all([
    getDocFromServer(doc(db, 'accesos', uid)),
    getDocFromServer(doc(db, 'emprendedoras', uid)),
  ])
  if (!accesoDoc.exists()) throw new Error('Esta cuenta todavía no tiene un perfil habilitado en Rincón Amancay.')
  const acceso = accesoDoc.data()
  if (acceso.estado !== 'activo' || !Array.isArray(acceso.roles) || !acceso.roles.includes('emprendedora')) {
    throw new Error('Esta cuenta no tiene acceso activo como emprendedora.')
  }
  const tiendaId = texto(acceso.tiendaId)
  if (!tiendaId) throw new Error('La cuenta no tiene una tienda asignada.')
  if (!perfilDoc.exists()) throw new Error('No se encontró el perfil de emprendedora.')
  if (perfilDoc.data().tiendaId !== tiendaId) throw new Error('El perfil y el acceso no coinciden con la tienda asignada.')

  const [tiendaDoc, usuarioDoc, productosDocs] = await Promise.all([
    getDocFromServer(doc(db, 'tiendasPrivadas', tiendaId)),
    getDocFromServer(doc(db, 'usuarios', uid)),
    getDocsFromServer(collection(db, 'tiendasPrivadas', tiendaId, 'productos')),
  ])
  if (!tiendaDoc.exists()) throw new Error('No se encontró la ficha privada de la tienda.')
  const tienda = tiendaDoc.data()
  if (tienda.propietarioUid !== uid) throw new Error('La tienda asignada no pertenece a esta cuenta.')
  const contacto = contactoEmprendedora(tienda.contacto)
  let logoPublico: string | null = null
  if (tienda.estadoPublicacion === 'publicado') {
    try {
      const tiendaPublica = await getDocFromServer(doc(db, 'tiendasPublicas', tiendaId))
      if (tiendaPublica.exists()) {
        const datosPublicos = tiendaPublica.data()
        logoPublico = texto(datosPublicos.logoMiniatura) || texto(datosPublicos.logo) || null
      }
    } catch {}
  }

  const baseProductos = productosDocs.docs
    .map((productoDoc): ProductoEmprendedoraWeb | null => {
      const datos = productoDoc.data()
      const original = texto(datos.estadoPublicacion, 'borrador')
      if (original === 'eliminado') return null
      const estado = original === 'publicado' ? 'publicado' : original === 'archivado' ? 'archivado' : 'borrador'
      return {
        id: productoDoc.id,
        nombre: texto(datos.nombre, 'Producto sin nombre'),
        descripcion: texto(datos.descripcion),
        estado,
        precioBase: numero(datos.precioBase),
        stock: numero(datos.stock),
        manejaStock: datos.manejaStock === true,
        modalidad: texto(datos.modalidad),
      }
    })
    .filter((producto): producto is ProductoEmprendedoraWeb => producto !== null)

  const imagenesPublicas = new Map<string, string>()
  if (tienda.estadoPublicacion === 'publicado') {
    try {
      const publicos = await getDocsFromServer(query(
        collection(db, 'tiendasPublicas', tiendaId, 'productos'),
        where('estadoPublicacion', '==', 'publicado'),
      ))
      for (const productoDoc of publicos.docs) {
        const datos = productoDoc.data()
        const rutas = Array.isArray(datos.miniaturas) ? datos.miniaturas : datos.imagenes
        if (Array.isArray(rutas)) {
          const primera = rutas.find((ruta): ruta is string => typeof ruta === 'string' && ruta.trim() !== '')
          if (primera) imagenesPublicas.set(productoDoc.id, primera)
        }
      }
    } catch {}
  }

  const productos = baseProductos
    .map((producto) => ({ ...producto, ...(imagenesPublicas.has(producto.id) ? { imagenPublica: imagenesPublicas.get(producto.id) } : {}) }))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))

  const datosUsuario = usuarioDoc.exists() ? usuarioDoc.data() : {}
  return {
    uid,
    correo: usuario.email ?? '',
    nombreMostrar: texto(datosUsuario.nombreMostrar),
    tienda: {
      id: tiendaId,
      nombre: texto(tienda.nombre),
      descripcion: texto(tienda.descripcion),
      tipoEmprendimientoId: texto(tienda.tipoEmprendimientoId),
      sectorId: texto(tienda.sectorId),
      contacto,
      mostrarContacto: tienda.mostrarContacto === true,
      versionEsperada: versionTienda(tienda.actualizadoEn),
      estadoPublicacion: texto(tienda.estadoPublicacion, 'borrador'),
      tieneLogo: typeof tienda.logo === 'string' && tienda.logo.trim() !== '',
      logoPublico,
      contactoVisible: tienda.mostrarContacto === true && contacto !== null,
    },
    productos,
  }
}
