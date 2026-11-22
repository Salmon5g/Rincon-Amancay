import { collection, doc, documentId, getDoc, getDocs, limit, onSnapshot, orderBy, query, startAfter, where } from 'firebase/firestore'
import { getBlob, ref as storageRef } from 'firebase/storage'
import type { DocumentData, DocumentSnapshot, Query, QueryDocumentSnapshot, QuerySnapshot } from 'firebase/firestore'
import type { ServiciosCliente } from '~/lib/cliente-base'
import type { CatalogoPublicoRepository, CatalogosPublicos, ElementoEtiqueta, PaginaRepositorio } from '~/repositories/catalogo-publico'
import type { AsociacionPublica, FeriaPublica, ContactoPublico, CanalesContactoPublico, IntegranteAsociacionPublica, MuestraCatalogoFeria, ParticipantePublicoFeria, ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico'

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
    const code = codigo === 'no-encontrado'
      ? 'no-encontrado'
      : codigo === 'permission-denied'
        ? 'sin-permiso'
        : ['unavailable', 'storage/retry-limit-exceeded', 'auth/network-request-failed'].includes(String(codigo))
          ? 'sin-conexion'
          : 'fallo-catalogo'
    const mensaje = error instanceof Error ? error.message : 'Falló una consulta del catálogo.'
    throw Object.assign(new Error(mensaje), { code })
  }
}

function fecha(valor: unknown): Date | null {
  return valor && typeof (valor as { toDate?: unknown }).toDate === 'function' ? (valor as { toDate(): Date }).toDate() : null
}

function mapearParticipantesFeria(valor: unknown): ParticipantePublicoFeria[] | undefined {
  if (!Array.isArray(valor)) return undefined
  return valor.flatMap((elemento): ParticipantePublicoFeria[] => {
    if (!elemento || typeof elemento !== 'object' || Array.isArray(elemento)) return []
    const item = elemento as Record<string, unknown>
    const nombre = texto(item.nombre).trim()
    const tipo = texto(item.tipo).trim()
    if (!nombre || (tipo !== 'emprendedora' && tipo !== 'asociacion')) return []
    const tiendaIds = Array.isArray(item.tiendaIds)
      ? item.tiendaIds
        .filter((tiendaId): tiendaId is string => typeof tiendaId === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(tiendaId))
        .slice(0, 3)
      : []
    const asociacionId = texto(item.asociacionId).trim()
    return [{
      tipo,
      nombre,
      tiendaIds,
      ...(tipo === 'asociacion' && /^[a-zA-Z0-9_-]{1,128}$/.test(asociacionId) ? { asociacionId } : {}),
      catalogo: [],
    }]
  })
}

function mapearFeria(documento: DocumentSnapshot<DocumentData>): FeriaPublica {
  const datos = documento.data() ?? {}
  const imagenUrl = texto(datos.imagenUrl)
  const participantes = mapearParticipantesFeria(datos.participantesPublicos)
  return {
    id: documento.id,
    nombre: texto(datos.nombre, 'Feria'),
    descripcion: texto(datos.descripcion),
    organizador: texto(datos.organizador),
    direccion: texto(datos.direccion),
    fechaInicio: fecha(datos.fechaInicio),
    fechaFin: fecha(datos.fechaFin),
    imagenUrl: imagenUrl.startsWith('https://') ? imagenUrl : '',
    latitud: numero(datos.latitud),
    longitud: numero(datos.longitud),
    ...(participantes ? { participantes } : {}),
  }
}

function mapearTienda(documento: DocumentSnapshot<DocumentData>): TiendaPublica {
  const datos = documento.data() ?? {}
  const contactoDatos = datos.contacto as Record<string, unknown> | undefined
  const canalesDatos = datos.canalesContacto as Record<string, unknown> | undefined
  const puntoDatos = datos.puntoRetiro as Record<string, unknown> | undefined
  const whatsappDatos = canalesDatos?.whatsapp as Record<string, unknown> | undefined
  const formularioDatos = canalesDatos?.formulario as Record<string, unknown> | undefined
  const correoDatos = canalesDatos?.correo as Record<string, unknown> | undefined
  
  const contacto = contactoDatos && ['whatsapp', 'correo', 'web'].includes(String(contactoDatos.tipo)) && typeof contactoDatos.valor === 'string'
    ? { tipo: contactoDatos.tipo as ContactoPublico['tipo'], valor: contactoDatos.valor }
    : undefined
  const formaContactoDeclarada = typeof datos.formaContacto === 'string' && datos.formaContacto.trim() !== ''
  const formaContacto = formaContactoDeclarada ? texto(datos.formaContacto, 'formulario') : 'formulario'
  const contactoEsWhatsapp = contacto?.tipo === 'whatsapp'
  const whatsappHabilitado = ['whatsapp', 'ambos'].includes(formaContacto)
    || (!formaContactoDeclarada && datos.mostrarContacto === true && contactoEsWhatsapp)
  const canalesContacto: CanalesContactoPublico = {
    whatsapp: {
      habilitado: whatsappDatos ? whatsappDatos.habilitado === true : whatsappHabilitado && contactoEsWhatsapp,
      numero: typeof whatsappDatos?.numero === 'string' ? whatsappDatos.numero.trim() : contactoEsWhatsapp ? contacto.valor : '',
    },
    formulario: {
      formularioId: texto(formularioDatos?.formularioId).trim() || documento.id,
      habilitado: formularioDatos ? formularioDatos.habilitado === true : ['formulario', 'ambos'].includes(formaContacto),
    },
    correo: {
      habilitado: correoDatos ? correoDatos.habilitado === true : contacto?.tipo === 'correo',
      direccion: typeof correoDatos?.direccion === 'string' ? correoDatos.direccion.trim() : contacto?.tipo === 'correo' ? contacto.valor : '',
    },
  }
  return {
    id: documento.id,
    nombre: texto(datos.nombre, documento.id),
    descripcion: texto(datos.descripcion),
    tipoEmprendimientoId: texto(datos.tipoEmprendimientoId),
    sectorId: texto(datos.sectorId),
    ...(typeof datos.direccion === 'string' && datos.direccion.trim() ? { direccion: datos.direccion.trim() } : {}),
    mostrarPrecios: datos.mostrarPrecios === true,
    ...(typeof datos.logo === 'string' ? { logo: datos.logo } : {}),
    ...(typeof datos.logoMiniatura === 'string' ? { logoMiniatura: datos.logoMiniatura } : {}),
    ...(contacto ? { contacto } : {}),
    canalesContacto,
    ...(puntoDatos
      && typeof puntoDatos.latitud === 'number'
      && typeof puntoDatos.longitud === 'number'
      && !Number.isNaN(puntoDatos.latitud)
      && !Number.isNaN(puntoDatos.longitud)
      ? {
          puntoRetiro: {
            latitud: puntoDatos.latitud,
            longitud: puntoDatos.longitud,
            ...(typeof puntoDatos.descripcion === 'string' && puntoDatos.descripcion.trim()
              ? { descripcion: puntoDatos.descripcion.trim() }
              : {}),
          },
        }
      : {}),
  }
}

function mapearProducto(documento: DocumentSnapshot<DocumentData>): ProductoPublico {
  const datos = documento.data() ?? {}
  return {
    id: documento.id,
    nombre: texto(datos.nombre, documento.id),
    descripcion: texto(datos.descripcion),
    categoriaId: texto(datos.categoriaId),
    tipoProductoId: texto(datos.tipoProductoId),
    tieneVariantes: datos.tieneVariantes === true,
    ...(typeof datos.manejaStock === 'boolean' ? { manejaStock: datos.manejaStock } : {}),
    moneda: texto(datos.moneda, 'CLP'),
    unidadVenta: texto(datos.unidadVenta),
    modalidad: texto(datos.modalidad),
    estadoDisponibilidad: texto(datos.estadoDisponibilidad, 'consultar'),
    precioBase: numero(datos.precioBase),
    stock: numero(datos.stock),
    atributosEspecificos: mapaDeTextos(datos.atributosEspecificos),
    imagenes: listaDeTextos(datos.imagenes),
  }
}

function mapearVariante(documento: QueryDocumentSnapshot<DocumentData>): VariantePublica {
  const datos = documento.data()
  return {
    id: documento.id,
    opciones: mapaDeTextos(datos.opciones),
    imagen: texto(datos.imagen).trim() || undefined,
    precio: numero(datos.precio),
    stock: numero(datos.stock),
    estadoDisponibilidad: texto(datos.estadoDisponibilidad, 'consultar'),
    activa: datos.activa === true,
  }
}

function mapearAsociacion(documento: QueryDocumentSnapshot<DocumentData>): AsociacionPublica {
  const datos = documento.data()
  const integrantes: IntegranteAsociacionPublica[] = Array.isArray(datos.integrantes)
    ? datos.integrantes.flatMap((elemento): IntegranteAsociacionPublica[] => {
      if (!elemento || typeof elemento !== 'object' || Array.isArray(elemento)) return []
      const integrante = elemento as Record<string, unknown>
      const tiendaId = texto(integrante.tiendaId)
      if (!/^[a-zA-Z0-9_-]{1,128}$/.test(tiendaId)) return []
      return [{
        tiendaId,
        nombre: texto(integrante.nombre, tiendaId),
        sectorId: texto(integrante.sectorId),
        tipoEmprendimientoId: texto(integrante.tipoEmprendimientoId),
        ...(typeof integrante.logoMiniatura === 'string' ? { logoMiniatura: integrante.logoMiniatura } : {}),
      }]
    })
    : []
  return {
    id: documento.id,
    nombre: texto(datos.nombre, documento.id),
    descripcion: texto(datos.descripcion),
    sectorId: texto(datos.sectorId),
    integrantes,
  }
}

function cursorSiguiente<T>(documentos: QueryDocumentSnapshot<DocumentData>[], elementos: T[]): PaginaRepositorio<T> {
  return {
    elementos,
    cursorSiguiente: documentos.length === TAMANO_PAGINA ? documentos.at(-1)?.id ?? null : null,
  }
}

export function crearCatalogoPublicoFirebase(firebase: ServiciosCliente, supabaseUrl = ''): CatalogoPublicoRepository {
  const db = firebase.db
  const storage = firebase.storage
  const baseSupabase = (() => {
    try {
      const parsed = new URL(supabaseUrl)
      if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash) return ''
      return parsed.origin
    } catch {
      return ''
    }
  })()

  function resolverUrlImagen(ruta: string): string | null {
    if (!baseSupabase) return null
    const segmentos = ruta.split('/')
    const logoValido = segmentos.length === 4
      && segmentos[0] === 'catalogo'
      && /^[a-zA-Z0-9_-]{1,128}$/.test(segmentos[1] ?? '')
      && segmentos[2] === 'tienda'
      && /^[0-9a-f]{64}_(1600|320)\.webp$/.test(segmentos[3] ?? '')
    const productoValido = segmentos.length === 5
      && segmentos[0] === 'catalogo'
      && /^[a-zA-Z0-9_-]{1,128}$/.test(segmentos[1] ?? '')
      && segmentos[2] === 'productos'
      && /^[a-zA-Z0-9_-]{1,128}$/.test(segmentos[3] ?? '')
      && /^[0-9a-f]{64}_(1600|320)\.webp$/.test(segmentos[4] ?? '')
    if (!logoValido && !productoValido) return null
    return `${baseSupabase}/storage/v1/object/public/amancay-catalogo/${segmentos.map(encodeURIComponent).join('/')}`
  }

  async function coleccionPublicaProductos(tiendaId: string): Promise<'productos' | 'productosConsulta'> {
    const tienda = await getDoc(doc(db, 'tiendasPublicas', validarId(tiendaId)))
    if (!tienda.exists() || tienda.data().estadoPublicacion !== 'publicado' || tienda.data().habilitada !== true) {
      throw Object.assign(new Error('La tienda solicitada ya no está disponible.'), { code: 'no-encontrado' })
    }
    return tienda.data().mostrarPrecios === true ? 'productos' : 'productosConsulta'
  }

  async function leerMuestraCatalogo(tiendaId: string): Promise<MuestraCatalogoFeria | null> {
    try {
      const tienda = await getDoc(doc(db, 'tiendasPublicas', validarId(tiendaId)))
      if (!tienda.exists() || tienda.data().estadoPublicacion !== 'publicado' || tienda.data().habilitada !== true) return null
      const coleccion = tienda.data().mostrarPrecios === true ? 'productos' : 'productosConsulta'
      const resultado = await getDocs(query(
        collection(db, 'tiendasPublicas', tiendaId, coleccion),
        where('estadoPublicacion', '==', 'publicado'),
        limit(2),
      ))
      const productos = resultado.docs
        .map((producto) => texto(producto.data().nombre).trim())
        .filter(Boolean)
      if (!productos.length) return null
      return { nombreTienda: texto(tienda.data().nombre, 'Tienda participante'), productos }
    } catch {
      return null
    }
  }

  async function cargarCatalogoParticipantes(participantes: ParticipantePublicoFeria[]): Promise<ParticipantePublicoFeria[]> {
    return Promise.all(participantes.map(async (participante) => {
      const catalogo: MuestraCatalogoFeria[] = []
      for (const tiendaId of participante.tiendaIds ?? []) {
        const muestra = await leerMuestraCatalogo(tiendaId)
        if (!muestra) continue
        catalogo.push(muestra)
        break
      }
      return { ...participante, catalogo }
    }))
  }

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
        const coleccion = await coleccionPublicaProductos(tienda)
        const consulta = query(
          collection(db, 'tiendasPublicas', tienda, coleccion),
          where('estadoPublicacion', '==', 'publicado'),
          orderBy(documentId()),
          ...(cursor ? [startAfter(cursor)] : []),
          limit(TAMANO_PAGINA),
        )
        const resultado = await getDocs(consulta)
        return cursorSiguiente(resultado.docs, resultado.docs.map(mapearProducto))
      })
    },

    async leerTienda(tiendaId) {
      return ejecutarConErrorNormalizado(async () => {
        const documento = await getDoc(doc(db, 'tiendasPublicas', validarId(tiendaId)))
        if (!documento.exists() || documento.data().estadoPublicacion !== 'publicado' || documento.data().habilitada !== true) {
          throw Object.assign(new Error('La tienda solicitada ya no está disponible.'), { code: 'no-encontrado' })
        }
        return mapearTienda(documento)
      })
    },

    observarTienda(tiendaId, alCambiar) {
      return onSnapshot(doc(db, 'tiendasPublicas', validarId(tiendaId)), (documento) => {
        if (documento.exists() && documento.data().estadoPublicacion === 'publicado' && documento.data().habilitada === true) {
          alCambiar(mapearTienda(documento))
        }
      }, () => {
        // La ficha también se puede volver a consultar al abrir la tienda.
      })
    },
    async leerProducto(tiendaId, productoId) {
      return ejecutarConErrorNormalizado(async () => {
        const tienda = validarId(tiendaId)
        const producto = validarId(productoId)
        const coleccion = await coleccionPublicaProductos(tienda)
        const documento = await getDoc(doc(db, 'tiendasPublicas', tienda, coleccion, producto))
        if (!documento.exists() || documento.data().estadoPublicacion !== 'publicado') {
          throw Object.assign(new Error('El producto solicitado ya no está disponible.'), { code: 'no-encontrado' })
        }
        return mapearProducto(documento)
      })
    },

    observarProducto(tiendaId, productoId, alCambiar, alFallar) {
      let activo = true
      let detener = () => {}
      void coleccionPublicaProductos(validarId(tiendaId)).then((coleccion) => {
        if (!activo) return
        detener = onSnapshot(doc(db, 'tiendasPublicas', validarId(tiendaId), coleccion, validarId(productoId)), (documento) => {
          if (!activo) return
          if (!documento.exists() || documento.data().estadoPublicacion !== 'publicado') {
            alCambiar(null)
            return
          }
          alCambiar(mapearProducto(documento))
        }, (error) => {
          if (activo) alFallar?.(error instanceof Error ? error : new Error('No se pudo actualizar el producto en tiempo real.'))
        })
      }).catch((error: unknown) => {
        if (activo) alFallar?.(error instanceof Error ? error : new Error('No se pudo observar el producto.'))
      })
      return () => {
        activo = false
        detener()
      }
    },

    async listarVariantes(tiendaId, productoId) {
      return ejecutarConErrorNormalizado(async () => {
        const tienda = validarId(tiendaId)
        const coleccion = await coleccionPublicaProductos(tienda)
        const consulta = query(
          collection(db, 'tiendasPublicas', tienda, coleccion, validarId(productoId), 'variantes'),
          where('activa', '==', true),
          orderBy(documentId()),
          limit(100),
        )
        const resultado = await getDocs(consulta)
        return resultado.docs.map(mapearVariante)
      })
    },

    async listarCatalogosPublicos() {
      return ejecutarConErrorNormalizado(async () => {
        const leerNombres = async (coleccion: string): Promise<ElementoEtiqueta[]> => {
          const consulta = query(collection(db, coleccion), where('activo', '==', true), orderBy(documentId()), limit(200))
          const resultado = await getDocs(consulta)
          return resultado.docs
            .map((documento) => ({ id: documento.id, nombre: texto(documento.data().nombre, documento.id) }))
            .filter((elemento) => elemento.nombre.trim() !== '')
        }
        const [sectores, tiposEmprendimiento, categorias] = await Promise.all([
          leerNombres('sectores'),
          leerNombres('tiposEmprendimiento'),
          leerNombres('categorias'),
        ])
        const catalogos: CatalogosPublicos = { sectores, tiposEmprendimiento, categorias }
        return catalogos
      })
    },

    async listarAsociacionesPublicas() {
      return ejecutarConErrorNormalizado(async () => {
        const asociaciones: AsociacionPublica[] = []
        let cursor: string | null = null
        let cantidad = 0
        do {
          const consulta: Query<DocumentData> = query(
            collection(db, 'asociacionesPublicas'),
            where('activo', '==', true),
            orderBy(documentId()),
            ...(cursor ? [startAfter(cursor)] : []),
            limit(TAMANO_PAGINA),
          )
          const resultado: QuerySnapshot<DocumentData> = await getDocs(consulta)
          asociaciones.push(...resultado.docs.map(mapearAsociacion))
          cantidad = resultado.docs.length
          cursor = resultado.docs.at(-1)?.id ?? null
        } while (cantidad === TAMANO_PAGINA && cursor)
        return asociaciones.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-CL'))
      })
    },

    async listarFeriasPublicas() {
      return ejecutarConErrorNormalizado(async () => {
        const resultado = await getDocs(query(collection(db, 'ferias'), where('estado', '==', 'publicada'), limit(100)))
        return resultado.docs.map(mapearFeria)
          .sort((a, b) => (a.fechaInicio?.getTime() ?? Infinity) - (b.fechaInicio?.getTime() ?? Infinity))
      })
    },

    async leerFeria(feriaId) {
      return ejecutarConErrorNormalizado(async () => {
        const documento = await getDoc(doc(db, 'ferias', validarId(feriaId)))
        if (!documento.exists() || documento.data().estado !== 'publicada') {
          throw Object.assign(new Error('La feria solicitada ya no está disponible.'), { code: 'no-encontrado' })
        }
        const feria = mapearFeria(documento)
        if (feria.participantes) feria.participantes = await cargarCatalogoParticipantes(feria.participantes)
        return feria
      })
    },

    resolverUrlImagen,

    async leerPoliticaNavegacion() {
      return ejecutarConErrorNormalizado(async () => {
        const documento = await getDoc(doc(db, 'configuracionAplicacion', 'navegacion'))
        const modo = documento.data()?.modo
        return modo === 'invitar' || modo === 'exigir' ? modo : 'permitir'
      })
    },

    leerImagen(ruta) {
      if (!ruta || ruta.startsWith('/') || ruta.includes('..')) return Promise.reject(new Error('La ruta de la imagen no es válida.'))
      return ejecutarConErrorNormalizado(() => getBlob(storageRef(storage, ruta), MAXIMO_IMAGEN_BYTES))
    },
  }
}
