import { collection, doc, getDocFromServer, getDocsFromServer } from 'firebase/firestore'
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
}

export type CuentaEmprendedoraWeb = {
  uid: string
  correo: string
  nombreMostrar: string
  tienda: { id: string; nombre: string; descripcion: string; estadoPublicacion: string }
  productos: ProductoEmprendedoraWeb[]
}

function texto(valor: unknown, alternativo = ''): string {
  return typeof valor === 'string' ? valor : alternativo
}

function numero(valor: unknown): number | null {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null
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

  const productos = productosDocs.docs
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
      estadoPublicacion: texto(tienda.estadoPublicacion, 'borrador'),
    },
    productos,
  }
}