import { collection, doc, documentId, getCountFromServer, getDocFromServer, getDocsFromServer, limit, orderBy, query, startAfter, where } from 'firebase/firestore'
import type { Firestore } from 'firebase/firestore'
import type { Timestamp } from 'firebase/firestore'
import { versionDocumento } from '~/lib/cliente-base'
import type { ServiciosCliente } from '~/lib/cliente-base'

export type ProductoEmprendedoraWeb = {
  id: string
  nombre: string
  descripcion: string
  estado: 'publicado' | 'borrador' | 'archivado'
  versionEsperada: string
  tipoProductoId: string
  versionTipoProducto: number
  categoriaId: string
  atributosEspecificos: Record<string, string>
  tieneVariantes: boolean
  precioBase: number | null
  stock: number | null
  manejaStock: boolean
  modalidad: string
  alAgotarse: string
  disponible: boolean
  piezaUnicaVendida: boolean
  imagenes: string[]
  variantes: VarianteEmprendedoraWeb[]
  variantesCargadas: boolean
  imagenPublica?: string
}

export type FiltroProductosEmprendedora = 'todos' | 'publicado' | 'borrador' | 'archivado'
export type ResumenProductosEmprendedora = { publicados: number; borradores: number; retirados: number }
export type PaginaProductosEmprendedora = { productos: ProductoEmprendedoraWeb[]; siguienteCursor: string | null }

type CacheTiposProducto = {
  listado?: { vence: number; valor: TipoProductoEmprendedoraWeb[] }
  listadoPendiente?: Promise<TipoProductoEmprendedoraWeb[]>
  definiciones: Map<string, Promise<TipoProductoEmprendedoraWeb>>
}

const cachesTiposProducto = new WeakMap<Firestore, CacheTiposProducto>()

export type VarianteEmprendedoraWeb = {
  id: string
  opciones: Record<string, string>
  precio: number
  stock: number | null
  activa: boolean
  tieneImagen: boolean
  imagenPublica?: string
}

export type CampoTipoProductoEmprendedora = {
  clave: string
  etiqueta: string
  tipo: 'texto'
  obligatorio: boolean
}

export type TipoProductoEmprendedoraWeb = {
  id: string
  nombre: string
  activo: boolean
  versionActual: number
  version: number
  campos: CampoTipoProductoEmprendedora[]
  opcionesVariante: string[]
}

export type ContactoEmprendedoraWeb = {
  tipo: 'whatsapp' | 'correo' | 'web'
  valor: string
}

export type CanalesContactoEmprendedoraWeb = {
  whatsapp: { habilitado: boolean; numero: string }
  formulario: { formularioId: string; habilitado: boolean }
  correo: { habilitado: boolean; direccion: string }
}

export type PuntoRetiroEmprendedoraWeb = {
  latitud: number
  longitud: number
  descripcion: string
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
    mostrarPrecios: boolean
    contacto: ContactoEmprendedoraWeb | null
    canalesContacto?: CanalesContactoEmprendedoraWeb
    puntoRetiro: PuntoRetiroEmprendedoraWeb | null
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

function mapaDeTextos(valor: unknown): Record<string, string> {
  if (!valor || typeof valor !== 'object' || Array.isArray(valor)) return {}
  return Object.fromEntries(Object.entries(valor).filter((par): par is [string, string] => typeof par[1] === 'string'))
}

function listaDeTextos(valor: unknown): string[] {
  return Array.isArray(valor) ? valor.filter((elemento): elemento is string => typeof elemento === 'string') : []
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

function versionProducto(valor: unknown): string {
  if (!valor || typeof valor !== 'object' || !('seconds' in valor) || !('nanoseconds' in valor)) return ''
  return versionDocumento(valor as Timestamp)
}

function mapearTipoProducto(id: string, nombre: string, activo: boolean, versionActual: number, definicion: Record<string, unknown>): TipoProductoEmprendedoraWeb {
  const campos = Array.isArray(definicion.campos) ? definicion.campos.filter((campo): campo is CampoTipoProductoEmprendedora =>
    !!campo && typeof campo === 'object' && typeof (campo as Record<string, unknown>).clave === 'string'
    && typeof (campo as Record<string, unknown>).etiqueta === 'string'
    && (campo as Record<string, unknown>).tipo === 'texto'
    && typeof (campo as Record<string, unknown>).obligatorio === 'boolean') : []
  const opcionesVariante = Array.isArray(definicion.opcionesVariante)
    ? definicion.opcionesVariante.filter((opcion): opcion is string => typeof opcion === 'string') : []
  return { id, nombre, activo, versionActual, version: versionActual, campos, opcionesVariante }
}

function cacheTipos(firebase: ServiciosCliente): CacheTiposProducto {
  let cache = cachesTiposProducto.get(firebase.db)
  if (!cache) {
    cache = { definiciones: new Map() }
    cachesTiposProducto.set(firebase.db, cache)
  }
  return cache
}

function leerDefinicionTipo(
  firebase: ServiciosCliente,
  id: string,
  nombre: string,
  activo: boolean,
  version: number,
): Promise<TipoProductoEmprendedoraWeb> {
  const cache = cacheTipos(firebase)
  const clave = `${id}:${version}`
  const existente = cache.definiciones.get(clave)
  if (existente) return existente
  const lectura = getDocFromServer(doc(firebase.db, 'tiposProducto', id, 'versiones', String(version))).then((definicion) => {
    if (!definicion.exists()) throw new Error(`No se encontró la definición vigente de “${nombre}”.`)
    return mapearTipoProducto(id, nombre, activo, version, definicion.data())
  }).catch((error: unknown) => {
    cache.definiciones.delete(clave)
    throw error
  })
  cache.definiciones.set(clave, lectura)
  return lectura
}

export async function listarTiposProductoEmprendedora(firebase: ServiciosCliente): Promise<TipoProductoEmprendedoraWeb[]> {
  const cache = cacheTipos(firebase)
  if (cache.listado && cache.listado.vence > Date.now()) return cache.listado.valor
  if (cache.listadoPendiente) return cache.listadoPendiente
  const lectura = (async () => {
    const resultado = await getDocsFromServer(query(
      collection(firebase.db, 'tiposProducto'),
      where('activo', '==', true),
      orderBy(documentId()),
      limit(200),
    ))
    const tipos = await Promise.all(resultado.docs.map((snapshot) => {
      const datos = snapshot.data()
      const versionActual = Number(datos.versionActual)
      if (!Number.isSafeInteger(versionActual) || versionActual < 1) {
        throw new Error(`El tipo de producto “${texto(datos.nombre, snapshot.id)}” no tiene una versión válida.`)
      }
      return leerDefinicionTipo(firebase, snapshot.id, texto(datos.nombre, snapshot.id), datos.activo === true, versionActual)
    }))
    cache.listado = { valor: tipos, vence: Date.now() + 30_000 }
    return tipos
  })()
  cache.listadoPendiente = lectura
  try {
    return await lectura
  } finally {
    if (cache.listadoPendiente === lectura) cache.listadoPendiente = undefined
  }
}

export async function leerTipoProductoEmprendedora(firebase: ServiciosCliente, id: string, version: number): Promise<TipoProductoEmprendedoraWeb> {
  const referencia = doc(firebase.db, 'tiposProducto', id)
  const tipo = await getDocFromServer(referencia)
  if (!tipo.exists()) throw new Error('No se encontró la definición de este producto. Actualiza la página e inténtalo de nuevo.')
  const datos = tipo.data()
  return leerDefinicionTipo(firebase, id, texto(datos.nombre, id), datos.activo === true, version)
}

function mapearProducto(id: string, datos: Record<string, unknown>): ProductoEmprendedoraWeb {
  const original = texto(datos.estadoPublicacion, 'borrador')
  return {
    id,
    nombre: texto(datos.nombre, 'Producto sin nombre'),
    descripcion: texto(datos.descripcion),
    estado: original === 'publicado' ? 'publicado' : original === 'archivado' ? 'archivado' : 'borrador',
    versionEsperada: versionProducto(datos.actualizadoEn),
    tipoProductoId: texto(datos.tipoProductoId),
    versionTipoProducto: Number.isSafeInteger(datos.versionTipoProducto) ? Number(datos.versionTipoProducto) : 1,
    categoriaId: texto(datos.categoriaId),
    atributosEspecificos: mapaDeTextos(datos.atributosEspecificos),
    tieneVariantes: datos.tieneVariantes === true,
    precioBase: numero(datos.precioBase),
    stock: numero(datos.stock),
    manejaStock: datos.manejaStock === true,
    modalidad: texto(datos.modalidad),
    alAgotarse: texto(datos.alAgotarse, 'mostrar_agotado'),
    disponible: datos.disponible !== false,
    piezaUnicaVendida: datos.piezaUnicaVendida === true,
    imagenes: listaDeTextos(datos.imagenes),
    variantes: [],
    variantesCargadas: datos.tieneVariantes !== true,
  }
}

function mapearVariante(id: string, datos: Record<string, unknown>): VarianteEmprendedoraWeb {
  return {
    id,
    opciones: mapaDeTextos(datos.opciones),
    precio: numero(datos.precio) ?? 0,
    stock: numero(datos.stock),
    activa: datos.activa !== false,
    tieneImagen: typeof datos.imagen === 'string' && datos.imagen !== '',
    ...(typeof datos.imagenPublica === 'string' && datos.imagenPublica ? { imagenPublica: datos.imagenPublica } : {}),
  }
}

async function conImagenPublica(firebase: ServiciosCliente, tiendaId: string, mostrarPrecios: boolean, producto: ProductoEmprendedoraWeb): Promise<ProductoEmprendedoraWeb> {
  if (producto.estado !== 'publicado') return producto
  try {
    const nombreColeccion = mostrarPrecios ? 'productos' : 'productosConsulta'
    const publico = await getDocFromServer(doc(firebase.db, 'tiendasPublicas', tiendaId, nombreColeccion, producto.id))
    if (!publico.exists()) return producto
    const datos = publico.data()
    const rutas = Array.isArray(datos.miniaturas) ? datos.miniaturas : datos.imagenes
    const primera = Array.isArray(rutas) ? rutas.find((ruta): ruta is string => typeof ruta === 'string' && ruta.trim() !== '') : undefined
    return primera ? { ...producto, imagenPublica: primera } : producto
  } catch {
    return producto
  }
}

export async function contarProductosEmprendedora(firebase: ServiciosCliente, tiendaId: string): Promise<ResumenProductosEmprendedora> {
  const productos = collection(firebase.db, 'tiendasPrivadas', tiendaId, 'productos')
  const [publicados, borradores, retirados] = await Promise.all([
    getCountFromServer(query(productos, where('estadoPublicacion', '==', 'publicado'))),
    getCountFromServer(query(productos, where('estadoPublicacion', '==', 'borrador'))),
    getCountFromServer(query(productos, where('estadoPublicacion', '==', 'archivado'))),
  ])
  return {
    publicados: publicados.data().count,
    borradores: borradores.data().count,
    retirados: retirados.data().count,
  }
}

export async function listarPaginaProductosEmprendedora(
  firebase: ServiciosCliente,
  tiendaId: string,
  mostrarPrecios: boolean,
  filtro: FiltroProductosEmprendedora,
  cursor: string | null = null,
  incluirImagenes = true,
): Promise<PaginaProductosEmprendedora> {
  const productos = collection(firebase.db, 'tiendasPrivadas', tiendaId, 'productos')
  const restricciones = [
    ...(filtro === 'todos' ? [] : [where('estadoPublicacion', '==', filtro)]),
    orderBy(documentId()),
    ...(cursor ? [startAfter(cursor)] : []),
    limit(21),
  ]
  const resultado = await getDocsFromServer(query(productos, ...restricciones))
  const pagina = resultado.docs.slice(0, 20)
  const siguienteCursor = resultado.docs.length > 20 ? pagina.at(-1)?.id ?? null : null
  const visibles = pagina.filter((snapshot) => snapshot.data().estadoPublicacion !== 'eliminado')
    .map((snapshot) => mapearProducto(snapshot.id, snapshot.data()))
  return {
    productos: incluirImagenes
      ? await Promise.all(visibles.map((producto) => conImagenPublica(firebase, tiendaId, mostrarPrecios, producto)))
      : visibles,
    siguienteCursor,
  }
}

export async function leerProductoEmprendedora(
  firebase: ServiciosCliente,
  tiendaId: string,
  mostrarPrecios: boolean,
  productoId: string,
): Promise<ProductoEmprendedoraWeb> {
  const productoDoc = await getDocFromServer(doc(firebase.db, 'tiendasPrivadas', tiendaId, 'productos', productoId))
  if (!productoDoc.exists() || productoDoc.data().estadoPublicacion === 'eliminado') throw new Error('No se encontró este producto. Actualiza la lista e inténtalo de nuevo.')
  const producto = mapearProducto(productoDoc.id, productoDoc.data())
  const [variantes, conImagen] = await Promise.all([
    producto.tieneVariantes
      ? getDocsFromServer(query(collection(firebase.db, 'tiendasPrivadas', tiendaId, 'productos', productoId, 'variantes'), limit(20)))
      : Promise.resolve(null),
    conImagenPublica(firebase, tiendaId, mostrarPrecios, producto),
  ])
  return {
    ...conImagen,
    variantes: variantes?.docs.map((snapshot) => mapearVariante(snapshot.id, snapshot.data())) ?? [],
    variantesCargadas: true,
  }
}

export async function leerFichaProductoEmprendedora(
  firebase: ServiciosCliente,
  tiendaId: string,
  productoId: string,
): Promise<ProductoEmprendedoraWeb> {
  const productoDoc = await getDocFromServer(doc(firebase.db, 'tiendasPrivadas', tiendaId, 'productos', productoId))
  if (!productoDoc.exists() || productoDoc.data().estadoPublicacion === 'eliminado') throw new Error('No se encontró este producto. Actualiza la lista e inténtalo de nuevo.')
  return mapearProducto(productoDoc.id, productoDoc.data())
}

export async function leerVariantesProductoEmprendedora(
  firebase: ServiciosCliente,
  tiendaId: string,
  productoId: string,
): Promise<VarianteEmprendedoraWeb[]> {
  const resultado = await getDocsFromServer(query(
    collection(firebase.db, 'tiendasPrivadas', tiendaId, 'productos', productoId, 'variantes'),
    limit(20),
  ))
  return resultado.docs.map((snapshot) => mapearVariante(snapshot.id, snapshot.data()))
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

  const [tiendaDoc, usuarioDoc, configuracionDoc] = await Promise.all([
    getDocFromServer(doc(db, 'tiendasPrivadas', tiendaId)),
    getDocFromServer(doc(db, 'usuarios', uid)),
    getDocFromServer(doc(db, 'tiendasPrivadas', tiendaId, 'configuracion', 'general')),
  ])
  if (!tiendaDoc.exists()) throw new Error('No se encontró la ficha privada de la tienda.')
  const tienda = tiendaDoc.data()
  const configuracion = configuracionDoc.exists() ? configuracionDoc.data() : {}
  const mostrarPrecios = configuracion.mostrarPrecios !== false
  if (tienda.propietarioUid !== uid) throw new Error('La tienda asignada no pertenece a esta cuenta.')
  const contacto = contactoEmprendedora(tienda.contacto)
  const canalesDatos = tienda.canalesContacto && typeof tienda.canalesContacto === 'object'
    ? tienda.canalesContacto as Record<string, unknown>
    : null
  const whatsappDatos = canalesDatos?.whatsapp && typeof canalesDatos.whatsapp === 'object'
    ? canalesDatos.whatsapp as Record<string, unknown>
    : null
  const formularioDatos = canalesDatos?.formulario && typeof canalesDatos.formulario === 'object'
    ? canalesDatos.formulario as Record<string, unknown>
    : null
  const correoDatos = canalesDatos?.correo && typeof canalesDatos.correo === 'object'
    ? canalesDatos.correo as Record<string, unknown>
    : null
  const formaContactoDeclarada = typeof configuracion.formaContacto === 'string' && configuracion.formaContacto.trim() !== ''
  const formaContacto = formaContactoDeclarada ? texto(configuracion.formaContacto, 'formulario') : 'formulario'
  const canalesContacto: CanalesContactoEmprendedoraWeb = canalesDatos ? {
    whatsapp: {
      habilitado: whatsappDatos?.habilitado === true,
      numero: texto(whatsappDatos?.numero),
    },
    formulario: {
      formularioId: texto(formularioDatos?.formularioId),
      habilitado: formularioDatos?.habilitado === true,
    },
    correo: {
      habilitado: correoDatos ? correoDatos.habilitado === true : tienda.mostrarContacto === true && contacto?.tipo === 'correo',
      direccion: correoDatos ? texto(correoDatos.direccion) : contacto?.tipo === 'correo' ? contacto.valor : '',
    },
  } : {
    whatsapp: {
      habilitado: tienda.mostrarContacto === true && contacto?.tipo === 'whatsapp'
        && (['whatsapp', 'ambos'].includes(formaContacto) || !formaContactoDeclarada),
      numero: contacto?.tipo === 'whatsapp' ? contacto.valor : '',
    },
    formulario: {
      formularioId: `contacto-${tiendaId}`,
      habilitado: ['formulario', 'ambos'].includes(formaContacto),
    },
    correo: {
      habilitado: tienda.mostrarContacto === true && contacto?.tipo === 'correo',
      direccion: contacto?.tipo === 'correo' ? contacto.valor : '',
    },
  }
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

  const datosUsuario = usuarioDoc.exists() ? usuarioDoc.data() : {}
  const puntoRetiroDatos = tienda.puntoRetiro && typeof tienda.puntoRetiro === 'object'
    ? tienda.puntoRetiro as Record<string, unknown>
    : null
  const puntoRetiro = puntoRetiroDatos
    && typeof puntoRetiroDatos.latitud === 'number' && Number.isFinite(puntoRetiroDatos.latitud)
    && puntoRetiroDatos.latitud >= -90 && puntoRetiroDatos.latitud <= 90
    && typeof puntoRetiroDatos.longitud === 'number' && Number.isFinite(puntoRetiroDatos.longitud)
    && puntoRetiroDatos.longitud >= -180 && puntoRetiroDatos.longitud <= 180
    ? {
        latitud: puntoRetiroDatos.latitud,
        longitud: puntoRetiroDatos.longitud,
        descripcion: texto(puntoRetiroDatos.descripcion),
      }
    : null
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
      mostrarPrecios,
      contacto,
      canalesContacto,
      puntoRetiro,
      mostrarContacto: tienda.mostrarContacto === true,
      versionEsperada: versionTienda(tienda.actualizadoEn),
      estadoPublicacion: texto(tienda.estadoPublicacion, 'borrador'),
      tieneLogo: typeof tienda.logo === 'string' && tienda.logo.trim() !== '',
      logoPublico,
      contactoVisible: canalesContacto.whatsapp.habilitado || canalesContacto.formulario.habilitado || canalesContacto.correo.habilitado,
    },
    productos: [],
  }
}
