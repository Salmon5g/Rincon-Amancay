import { collection, documentId, getDocs, limit, orderBy, query, startAfter, where } from 'firebase/firestore'
import { getBlob, ref as storageRef } from 'firebase/storage'
import type { DocumentData, QueryDocumentSnapshot } from 'firebase/firestore'
import type { ServiciosCliente } from '~/lib/cliente-base'
import type { CatalogoPublicoRepository, PaginaRepositorio } from '~/repositories/catalogo-publico'
import type { ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico'

const TAMANO_PAGINA = 20
const MAXIMO_IMAGEN_BYTES = 5 * 1024 * 1024

function texto(valor: unknown, alternativo = ''): string {
  return typeof valor === 'string' ? valor : alternativo
}

function numero(valor: unknown): number | null {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null
}

function mapaDeTextos(valor: unknown): Record<string, string> {
  if (!valor || typeof valor !== 'object' || Array.isArray(valor)) return {}
  return Object.fromEntries(Object.entries(valor).filter((par): par is [string, string] => typeof par[1] === 'string'))
}

function listaDeTextos(valor: unknown): string[] {
  return Array.isArray(valor) ? valor.filter((elemento): elemento is string => typeof elemento === 'string') : []
}

function validarId(valor: string): string {
  if (!/^[a-zA-Z0-9_-]{1,128}$/.test(valor)) throw new Error('El identificador solicitado no es válido.')
  return valor
}

async function ejecutarConErrorNormalizado<T>(operacion: () => Promise<T>): Promise<T> {
  try {
    return await operacion()
  } catch (error) {
    const codigo = (error as { code?: string })?.code
    const code = codigo === 'permission-denied'
      ? 'sin-permiso'
      : ['unavailable', 'storage/retry-limit-exceeded', 'auth/network-request-failed'].includes(String(codigo))
        ? 'sin-conexion'
        : 'fallo-catalogo'
    const mensaje = error instanceof Error ? error.message : 'Falló una consulta del catálogo.'
    throw Object.assign(new Error(mensaje), { code })
  }
}

function mapearTienda(documento: QueryDocumentSnapshot<DocumentData>): TiendaPublica {
  const datos = documento.data()
  return {
    id: documento.id,
    nombre: texto(datos.nombre, documento.id),
    descripcion: texto(datos.descripcion),
    tipoEmprendimientoId: texto(datos.tipoEmprendimientoId),
    sectorId: texto(datos.sectorId),
    mostrarPrecios: datos.mostrarPrecios === true,
  }
}

function mapearProducto(documento: QueryDocumentSnapshot<DocumentData>): ProductoPublico {
  const datos = documento.data()
  return {
    id: documento.id,
    nombre: texto(datos.nombre, documento.id),
    descripcion: texto(datos.descripcion),
    categoriaId: texto(datos.categoriaId),
    tipoProductoId: texto(datos.tipoProductoId),
    tieneVariantes: datos.tieneVariantes === true,
    moneda: texto(datos.moneda, 'CLP'),
    unidadVenta: texto(datos.unidadVenta),
    modalidad: texto(datos.modalidad),
    estadoDisponibilidad: texto(datos.estadoDisponibilidad, 'consultar'),
    precioBase: numero(datos.precioBase),
    atributosEspecificos: mapaDeTextos(datos.atributosEspecificos),
    imagenes: listaDeTextos(datos.imagenes),
  }
}

function mapearVariante(documento: QueryDocumentSnapshot<DocumentData>): VariantePublica {
  const datos = documento.data()
  return {
    id: documento.id,
    opciones: mapaDeTextos(datos.opciones),
    precio: numero(datos.precio),
    estadoDisponibilidad: texto(datos.estadoDisponibilidad, 'consultar'),
    activa: datos.activa === true,
  }
}

function cursorSiguiente<T>(documentos: QueryDocumentSnapshot<DocumentData>[], elementos: T[]): PaginaRepositorio<T> {
  return {
    elementos,
    cursorSiguiente: documentos.length === TAMANO_PAGINA ? documentos.at(-1)?.id ?? null : null,
  }
}

export function crearCatalogoPublicoFirebase(firebase: ServiciosCliente): CatalogoPublicoRepository {
  const db = firebase.db
  const storage = firebase.storage

  return {
    async listarTiendas(cursor = null) {
      return ejecutarConErrorNormalizado(async () => {
        const consulta = query(
          collection(db, 'tiendasPublicas'),
          where('estadoPublicacion', '==', 'publicado'),
          where('habilitada', '==', true),
          orderBy(documentId()),
          ...(cursor ? [startAfter(cursor)] : []),
          limit(TAMANO_PAGINA),
        )
        const resultado = await getDocs(consulta)
        return cursorSiguiente(resultado.docs, resultado.docs.map(mapearTienda))
      })
    },

    async listarProductos(tiendaId, cursor = null) {
      return ejecutarConErrorNormalizado(async () => {
        const tienda = validarId(tiendaId)
        const consulta = query(
          collection(db, 'tiendasPublicas', tienda, 'productos'),
          where('estadoPublicacion', '==', 'publicado'),
          orderBy(documentId()),
          ...(cursor ? [startAfter(cursor)] : []),
          limit(TAMANO_PAGINA),
        )
        const resultado = await getDocs(consulta)
        return cursorSiguiente(resultado.docs, resultado.docs.map(mapearProducto))
      })
    },

    async listarVariantes(tiendaId, productoId) {
      return ejecutarConErrorNormalizado(async () => {
        const consulta = query(
          collection(db, 'tiendasPublicas', validarId(tiendaId), 'productos', validarId(productoId), 'variantes'),
          where('activa', '==', true),
          orderBy(documentId()),
          limit(100),
        )
        const resultado = await getDocs(consulta)
        return resultado.docs.map(mapearVariante)
      })
    },

    leerImagen(ruta) {
      if (!ruta || ruta.startsWith('/') || ruta.includes('..')) return Promise.reject(new Error('La ruta de la imagen no es válida.'))
      return ejecutarConErrorNormalizado(() => getBlob(storageRef(storage, ruta), MAXIMO_IMAGEN_BYTES))
    },
  }
}
