<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { leerProductoEmprendedora, leerTipoProductoEmprendedora, listarTiposProductoEmprendedora, type CuentaEmprendedoraWeb, type ProductoEmprendedoraWeb, type TipoProductoEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { ServiciosCliente, Operacion } from '~/lib/cliente-base'
import type { ElementoEtiqueta } from '~/repositories/catalogo-publico'
import { enteroNoNegativoFormulario, textoNumericoFormulario, type ValorNumericoFormulario } from '~/lib/numeros-producto'
import { OPCIONES_FOTO_INICIALES, procesarFotoProducto, type OpcionesFotoProducto } from '~/lib/foto-producto'

type VarianteFormulario = {
  id: string
  opciones: Record<string, string>
  precio: ValorNumericoFormulario
  stock: ValorNumericoFormulario
  activa: boolean
  existente: boolean
  tieneImagen?: boolean
  imagenPublica?: string
}

type ProductoApi = {
  nombre: string
  descripcion: string
  tipoProductoId: string
  versionTipoProducto: number
  categoriaId: string
  atributosEspecificos: Record<string, string>
  tieneVariantes: boolean
  moneda: 'CLP'
  unidadVenta: 'unidad'
  pasoCantidad: 1
  modalidad: 'regular' | 'a_pedido' | 'pieza_unica'
  manejaStock: boolean
  alAgotarse: 'mostrar_agotado' | 'ocultar' | 'pasar_a_pedido'
  precioBase?: number
  stockInicial?: number
}

type FotoVariantePendiente = {
  archivoId: string
  operacionId: string
  ruta: string
  asignada: boolean
}

type IntencionGuardado = {
  firma: string
  productoId: string
  operacionId: string
  archivoId: string
  publicacionId: string
  productoGuardado: boolean
  fotoSubida: boolean
  fotosVariantes: Record<string, FotoVariantePendiente>
}

type PasoEditor = 1 | 2 | 3 | 4 | 5 | 6 | 7
type BorradorLocalProducto = {
  version?: number
  paso: number
  formulario: typeof formulario
  foto: File | null
  opcionesFoto?: OpcionesFotoProducto
  fotosVariantes?: Record<string, File>
}
type DestinoCamara = { destino: 'principal' } | { destino: 'variante'; id: string }

const VERSION_BORRADOR = 2
const ORDEN_TIPOS = ['alimento_reposteria', 'artesania_manualidad', 'plantas', 'producto_agricola', 'vestuario', 'accesorio', 'cosmetica_natural']
const MAXIMO_IMAGENES = 5

const iconos = {
  restaurante: 'M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z',
  paleta: 'M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z',
  estrella: 'M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
  hoja: 'M6.05 8.05a7.007 7.007 0 0 0-.02 9.88c1.47-3.4 4.09-6.24 7.36-7.93A15.952 15.952 0 0 0 8.03 16.2c2.86 1.36 6.37.86 8.71-1.5C20.53 10.91 21 3 21 3s-7.91.47-11.7 4.26z',
  percha: 'M21.6 18.2 13 11.75v-.91c1.65-.49 2.8-2.17 2.43-4.05-.26-1.31-1.3-2.4-2.61-2.7C10.54 3.57 8.5 5.3 8.5 7.5h2c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5c0 .84-.69 1.52-1.53 1.5-.54-.01-.97.45-.97.99v1.76L2.4 18.2c-.77.58-.36 1.8.6 1.8h18c.96 0 1.37-1.22.6-1.8zM6 18l6-4.5 6 4.5H6z',
  caja: 'M20.54 5.23 19.15 3.55C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5 6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z',
  reloj: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
  ojo: 'M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z',
  ojoTachado: 'M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78 3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z',
  intercambio: 'M6.99 11 3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z',
  tienda: 'M20 4H4v2h16V4zm1 10v-2l-1-5H4l-1 5v2h1v6h10v-6h4v6h2v-6h1zm-9 4H6v-4h6v4z',
  galeria: 'M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2zm-11-4 2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 2h14v-2H4V6H2z',
  etiqueta: 'M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z',
  camara: 'M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4zM9 2 7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z',
  sol: 'M6.76 4.84 4.96 3.05 3.55 4.46l1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7 1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91 1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z',
  marco: 'M3 5v4h2V5h4V3H5c-1.1 0-2 .9-2 2zm2 10H3v4c0 1.1.9 2 2 2h4v-2H5v-4zm14 4h-4v2h4c1.1 0 2-.9 2-2v-4h-2v4zm0-16h-4v2h4v4h2V5c0-1.1-.9-2-2-2z',
  fondo: 'M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z',
} as const

const iconoTipo: Record<string, string> = {
  alimento_reposteria: iconos.restaurante,
  producto_agricola: iconos.restaurante,
  artesania_manualidad: iconos.paleta,
  plantas: iconos.hoja,
  vestuario: iconos.percha,
  accesorio: iconos.estrella,
  cosmetica_natural: iconos.estrella,
}

const opcionesModalidad = [
  { id: 'regular' as const, nombre: 'Regular', detalle: 'Producto siempre disponible en forma habitual.', icono: iconos.caja },
  { id: 'a_pedido' as const, nombre: 'A pedido', detalle: 'Se realiza especialmente tras la compra.', icono: iconos.reloj },
  { id: 'pieza_unica' as const, nombre: 'Pieza única', detalle: 'Producto único, no se repite.', icono: iconos.estrella },
]
const opcionesAgotado = [
  { id: 'mostrar_agotado' as const, nombre: 'Mostrar agotado', detalle: 'El producto sigue visible, pero se muestra como agotado.', icono: iconos.ojo },
  { id: 'ocultar' as const, nombre: 'Ocultar producto', detalle: 'No se mostrará hasta que vuelva a tener stock.', icono: iconos.ojoTachado },
  { id: 'pasar_a_pedido' as const, nombre: 'Pasar a pedido', detalle: 'Al agotarse, cambiará automáticamente a pedido.', icono: iconos.intercambio },
]
const consejosFoto = [
  { titulo: 'Buena luz', detalle: 'Cerca de una ventana, sin flash.', icono: iconos.sol },
  { titulo: 'Fondo simple', detalle: 'Liso y despejado, que destaque el producto.', icono: iconos.fondo },
  { titulo: 'Bien encuadrado', detalle: 'Centrado y completo dentro de la foto.', icono: iconos.marco },
]

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb; productoId?: string | null }>()
const emit = defineEmits<{ cancelar: []; guardado: [resultado: { productoId: string; publicado: boolean }] }>()

const { $catalogoPublico } = useNuxtApp()
const runtime = useRuntimeConfig()
const paso = ref<PasoEditor>(1)
const restaurandoBorrador = ref(false)
const modoSoloLectura = computed(() => runtime.public.firebaseModo === 'real-lectura')
const escrituraDisponible = computed(() => ['local', 'real-api'].includes(String(runtime.public.firebaseModo)))
const pasosEditor = [
  { id: 1 as const, titulo: 'Foto' },
  { id: 2 as const, titulo: 'Datos' },
  { id: 3 as const, titulo: 'Tipo' },
  { id: 4 as const, titulo: 'Categoría' },
  { id: 5 as const, titulo: 'Variantes' },
  { id: 6 as const, titulo: 'Disponibilidad' },
]
const cargando = ref(true)
const guardando = ref(false)
const error = ref('')
const aviso = ref('')
const categorias = ref<ElementoEtiqueta[]>([])
const tipos = ref<TipoProductoEmprendedoraWeb[]>([])
const definicion = ref<TipoProductoEmprendedoraWeb | null>(null)
const productoOriginal = ref<ProductoEmprendedoraWeb | null>(null)
const versionEsperada = ref('')
const idProductoActivo = ref<string | null>(null)
const intencion = ref<IntencionGuardado | null>(null)
const fotoSeleccionada = ref<File | null>(null)
const fotoPreview = ref('')
const entradaFoto = ref<HTMLInputElement | null>(null)
const entradaCamara = ref<HTMLInputElement | null>(null)
const entradaFotoVariante = ref<HTMLInputElement | null>(null)
const entradaCamaraVariante = ref<HTMLInputElement | null>(null)
const varianteFotoObjetivo = ref<string | null>(null)
const fotosVariantes = ref<Record<string, File>>({})
const previewsVariantes = ref<Record<string, string>>({})
const fotoOriginal = ref<File | null>(null)
const previewOriginal = ref('')
const opcionesFoto = reactive<OpcionesFotoProducto>({ ...OPCIONES_FOTO_INICIALES })
const verOriginal = ref(false)
const procesandoFoto = ref(false)
const camaraAbierta = ref<DestinoCamara | null>(null)
let turnoProcesamiento = 0

const formulario = reactive({
  nombre: '',
  descripcion: '',
  tipoProductoId: '',
  categoriaId: '',
  modalidad: 'regular' as ProductoApi['modalidad'],
  manejaStock: true,
  alAgotarse: 'mostrar_agotado' as ProductoApi['alAgotarse'],
  tieneVariantes: false,
  precioBase: '' as ValorNumericoFormulario,
  stockInicial: '1' as ValorNumericoFormulario,
  atributosEspecificos: {} as Record<string, string>,
  variantes: [] as VarianteFormulario[],
})

const esEdicion = computed(() => Boolean(props.productoId || idProductoActivo.value))
const tiendaPublicada = computed(() => props.cuenta.tienda.estadoPublicacion === 'publicado')
const tipoSeleccionado = computed(() => definicion.value)
const tiposVisibles = computed(() => {
  if (esEdicion.value) {
    const actual = definicion.value
    const lista = tipos.value.filter((tipo) => ORDEN_TIPOS.includes(tipo.id))
    return actual && !lista.some((tipo) => tipo.id === actual.id) ? [actual, ...lista] : lista
  }
  const vigentes = tipos.value.filter((tipo) => ORDEN_TIPOS.includes(tipo.id))
  const lista = vigentes.length ? vigentes : tipos.value
  return [...lista].sort((a, b) => indiceTipo(a.id) - indiceTipo(b.id))
})
const categoriasDisponibles = computed(() => {
  const tipoId = formulario.tipoProductoId
  const resultado = categorias.value
    .filter((categoria) => categoria.tipoProductoId === tipoId)
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-CL'))
  const actual = productoOriginal.value?.categoriaId
  if (actual && !resultado.some((item) => item.id === actual)) {
    resultado.unshift(categorias.value.find((item) => item.id === actual) ?? { id: actual, nombre: actual })
  }
  return resultado
})
const nombreCategoria = computed(() => categoriasDisponibles.value.find((categoria) => categoria.id === formulario.categoriaId)?.nombre ?? '')
const admiteVariantes = computed(() => Boolean(tipoSeleccionado.value?.opcionesVariante.length) && formulario.modalidad !== 'pieza_unica')
const imagenesActuales = computed(() => productoOriginal.value?.imagenes.length ?? 0)
const cantidadFotosVariantes = computed(() => formulario.tieneVariantes ? formulario.variantes.filter((variante) => fotosVariantes.value[variante.id]).length : 0)
const puedePublicar = computed(() => tiendaPublicada.value && (imagenesActuales.value > 0 || fotoSeleccionada.value !== null))
const botonPublicacion = computed(() => productoOriginal.value?.estado === 'publicado' ? 'Guardar y actualizar el catálogo' : 'Guardar y publicar')
const bloqueadoPorReintento = computed(() => Boolean(intencion.value))
const claveBorrador = computed(() => `producto:${props.cuenta.uid}:${props.cuenta.tienda.id}`)
const precioResumen = computed(() => formulario.tieneVariantes
  ? `${formulario.variantes.length} ${formulario.variantes.length === 1 ? 'variante' : 'variantes'}`
  : formulario.precioBase !== '' ? `$${Number(formulario.precioBase).toLocaleString('es-CL')}` : 'Precio pendiente')
const textoModalidad = computed(() => opcionesModalidad.find((opcion) => opcion.id === formulario.modalidad)?.nombre ?? formulario.modalidad)
const textoAgotado = computed(() => formulario.alAgotarse === 'ocultar' ? 'ocultar el producto' : formulario.alAgotarse === 'pasar_a_pedido' ? 'pasar a pedido' : 'mostrarlo como agotado')
const cabecera = computed(() => {
  switch (paso.value) {
    case 1: return { titulo: 'Agrega una foto', bajada: 'Muéstrale a tus clientes lo que vendes.' }
    case 2: return { titulo: 'Datos del producto', bajada: 'Completa la información para que más personas encuentren tu producto.' }
    case 3: return { titulo: 'Tipo de producto', bajada: 'Elige el tipo para mostrar los campos que necesitas.' }
    case 4: return { titulo: 'Categoría y detalles', bajada: `Elige una categoría de ${tipoSeleccionado.value?.nombre ?? 'este tipo'} y completa sus detalles.` }
    case 5: return admiteVariantes.value || formulario.tieneVariantes
      ? { titulo: 'Variantes', bajada: 'Cada variante puede tener su propio precio, foto y stock.' }
      : { titulo: 'Precio y stock', bajada: 'Indica el precio y las unidades que tienes disponibles.' }
    case 6: return { titulo: 'Disponibilidad', bajada: 'Cuéntanos cómo quieres vender este producto.' }
    default: return { titulo: 'Revisa tu producto', bajada: 'Verifica que toda la información esté correcta antes de publicarlo.' }
  }
})
const pasoActualValido = computed(() => !errorDelPaso(paso.value))

function indiceTipo(id: string) {
  const indice = ORDEN_TIPOS.indexOf(id)
  return indice === -1 ? ORDEN_TIPOS.length : indice
}

function abrirBaseBorradores(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const solicitud = indexedDB.open('amancay-productos-web', 1)
    solicitud.onupgradeneeded = () => {
      if (!solicitud.result.objectStoreNames.contains('borradores')) solicitud.result.createObjectStore('borradores')
    }
    solicitud.onsuccess = () => resolve(solicitud.result)
    solicitud.onerror = () => reject(solicitud.error ?? new Error('No se pudo abrir el guardado local.'))
  })
}

async function leerBorradorLocal(): Promise<BorradorLocalProducto | null> {
  const db = await abrirBaseBorradores()
  try {
    return await new Promise((resolve, reject) => {
      const solicitud = db.transaction('borradores', 'readonly').objectStore('borradores').get(claveBorrador.value)
      solicitud.onsuccess = () => resolve((solicitud.result as BorradorLocalProducto | undefined) ?? null)
      solicitud.onerror = () => reject(solicitud.error ?? new Error('No se pudo recuperar el avance guardado.'))
    })
  } finally { db.close() }
}

async function guardarBorradorLocal() {
  if (!import.meta.client || esEdicion.value) return
  const db = await abrirBaseBorradores()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaccion = db.transaction('borradores', 'readwrite')
      transaccion.objectStore('borradores').put({
        version: VERSION_BORRADOR,
        paso: paso.value,
        formulario: JSON.parse(JSON.stringify(formulario)),
        foto: fotoOriginal.value,
        opcionesFoto: { ...opcionesFoto },
        fotosVariantes: { ...fotosVariantes.value },
      } satisfies BorradorLocalProducto, claveBorrador.value)
      transaccion.oncomplete = () => resolve()
      transaccion.onerror = () => reject(transaccion.error ?? new Error('No se pudo guardar el avance en este navegador.'))
      transaccion.onabort = () => reject(transaccion.error ?? new Error('Se canceló el guardado local.'))
    })
  } finally { db.close() }
}

async function borrarBorradorLocal() {
  if (!import.meta.client) return
  const db = await abrirBaseBorradores()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaccion = db.transaction('borradores', 'readwrite')
      transaccion.objectStore('borradores').delete(claveBorrador.value)
      transaccion.oncomplete = () => resolve()
      transaccion.onerror = () => reject(transaccion.error ?? new Error('No se pudo eliminar el avance guardado.'))
    })
  } finally { db.close() }
}

function pasoDesdeBorrador(borrador: BorradorLocalProducto): PasoEditor {
  const guardado = Number(borrador.paso)
  if (borrador.version === VERSION_BORRADOR) return guardado >= 1 && guardado <= 7 ? guardado as PasoEditor : 1
  const equivalencias: Record<number, PasoEditor> = { 1: 1, 2: 2, 3: 3, 4: 5, 5: 6 }
  return equivalencias[guardado] ?? 1
}

let temporizadorBorrador: ReturnType<typeof setTimeout> | undefined
watch(() => [fotoOriginal.value, opcionesFoto.recorteCuadrado, opcionesFoto.mejorarLuz] as const, () => { void reprocesarFoto() })

watch(() => [paso.value, JSON.stringify(formulario), fotoOriginal.value, opcionesFoto.recorteCuadrado, opcionesFoto.mejorarLuz, Object.keys(fotosVariantes.value).join(',')] as const, () => {
  if (!import.meta.client || cargando.value || restaurandoBorrador.value || esEdicion.value) return
  aviso.value = ''
  clearTimeout(temporizadorBorrador)
  temporizadorBorrador = setTimeout(() => { void guardarBorradorLocal().catch(() => {}) }, 350)
})

function cliente() {
  const { $amancay } = useNuxtApp()
  if (!$amancay?.firebase) throw new Error('El acceso no está disponible en este momento.')
  if (typeof $amancay.llamar !== 'function' || typeof $amancay.subirImagenProducto !== 'function') {
    throw new Error('Este sitio está en modo de solo lectura: no tiene habilitada la API que guarda productos e imágenes. Configura la API HTTPS de escritura para que la web emprendedora pueda sincronizar los cambios.')
  }
  return $amancay as typeof $amancay & {
    llamar: (operacion: Operacion, solicitud: Record<string, unknown>) => Promise<Record<string, unknown>>
    subirImagenProducto: (tiendaId: string, productoId: string, archivo: Blob, archivoId: string, reemplazarIndice?: number) => Promise<string>
  }
}

function limpiarFotoPreview() {
  if (fotoPreview.value.startsWith('blob:')) URL.revokeObjectURL(fotoPreview.value)
  fotoPreview.value = ''
}

function limpiarFotoPrincipal() {
  turnoProcesamiento += 1
  fotoOriginal.value = null
  fotoSeleccionada.value = null
  verOriginal.value = false
  procesandoFoto.value = false
  limpiarFotoPreview()
  if (previewOriginal.value) URL.revokeObjectURL(previewOriginal.value)
  previewOriginal.value = ''
}

async function reprocesarFoto() {
  const original = fotoOriginal.value
  const turno = ++turnoProcesamiento
  if (!original) return
  procesandoFoto.value = true
  try {
    const procesada = await procesarFotoProducto(original, { ...opcionesFoto })
    if (turno !== turnoProcesamiento) return
    limpiarFotoPreview()
    fotoSeleccionada.value = procesada
    fotoPreview.value = URL.createObjectURL(procesada)
  } catch (fallo) {
    if (turno !== turnoProcesamiento) return
    fotoSeleccionada.value = original
    limpiarFotoPreview()
    fotoPreview.value = URL.createObjectURL(original)
    error.value = fallo instanceof Error ? `${fallo.message} Se usará la foto original.` : 'No se pudo ajustar la foto; se usará la original.'
  } finally {
    if (turno === turnoProcesamiento) procesandoFoto.value = false
  }
}

function aplicarFotoPrincipal(archivo: File, opciones: OpcionesFotoProducto = OPCIONES_FOTO_INICIALES) {
  const problema = errorArchivoImagen(archivo)
  if (problema) {
    error.value = problema
    return
  }
  error.value = ''
  if (previewOriginal.value) URL.revokeObjectURL(previewOriginal.value)
  previewOriginal.value = URL.createObjectURL(archivo)
  verOriginal.value = false
  Object.assign(opcionesFoto, opciones)
  fotoOriginal.value = archivo
}

function aplicarFotoVariante(id: string, archivo: File) {
  const problema = errorArchivoImagen(archivo)
  if (problema) {
    error.value = problema
    return
  }
  error.value = ''
  quitarFotoVariante(id)
  fotosVariantes.value = { ...fotosVariantes.value, [id]: archivo }
  previewsVariantes.value = { ...previewsVariantes.value, [id]: URL.createObjectURL(archivo) }
}

function abrirCamara(destino: DestinoCamara) {
  if (guardando.value || intencion.value) return
  const tactil = import.meta.client && window.matchMedia('(pointer: coarse)').matches
  if (tactil || !navigator.mediaDevices?.getUserMedia) {
    if (destino.destino === 'principal') entradaCamara.value?.click()
    else {
      varianteFotoObjetivo.value = destino.id
      entradaCamaraVariante.value?.click()
    }
    return
  }
  camaraAbierta.value = destino
}

function alCapturarFoto(archivo: File) {
  const destino = camaraAbierta.value
  camaraAbierta.value = null
  if (!destino || guardando.value || intencion.value) return
  if (destino.destino === 'principal') aplicarFotoPrincipal(archivo)
  else if (formulario.variantes.some((variante) => variante.id === destino.id)) aplicarFotoVariante(destino.id, archivo)
}

function avisoDictado(mensaje: string) {
  error.value = mensaje
}

function quitarFotoVariante(id: string) {
  const preview = previewsVariantes.value[id]
  if (preview) URL.revokeObjectURL(preview)
  const { [id]: _archivo, ...fotos } = fotosVariantes.value
  const { [id]: _preview, ...previews } = previewsVariantes.value
  fotosVariantes.value = fotos
  previewsVariantes.value = previews
}

function limpiarFotosVariantes() {
  Object.values(previewsVariantes.value).forEach((preview) => URL.revokeObjectURL(preview))
  fotosVariantes.value = {}
  previewsVariantes.value = {}
}

onBeforeUnmount(() => {
  limpiarFotoPrincipal()
  limpiarFotosVariantes()
})

function inicializarVariante(opciones: string[] = [], existente?: ProductoEmprendedoraWeb['variantes'][number]): VarianteFormulario {
  return {
    id: existente?.id ?? crypto.randomUUID(),
    opciones: existente ? { ...existente.opciones } : Object.fromEntries(opciones.map((opcion) => [opcion, ''])),
    precio: existente ? String(existente.precio) : '',
    stock: existente?.stock === null || existente?.stock === undefined ? '1' : String(existente.stock),
    activa: existente?.activa ?? true,
    existente: Boolean(existente),
    ...(existente?.tieneImagen ? { tieneImagen: true } : {}),
    ...(existente?.imagenPublica ? { imagenPublica: existente.imagenPublica } : {}),
  }
}

function seleccionarTipo(tipo: TipoProductoEmprendedoraWeb | undefined) {
  definicion.value = tipo ?? null
  if (!tipo) return
  formulario.tipoProductoId = tipo.id
  formulario.atributosEspecificos = Object.fromEntries(tipo.campos.map((campo) => [campo.clave, '']))
  if (!categoriasDisponibles.value.some((categoria) => categoria.id === formulario.categoriaId)) {
    formulario.categoriaId = categoriasDisponibles.value[0]?.id ?? ''
  }
  if (!productoOriginal.value) {
    limpiarFotosVariantes()
    formulario.tieneVariantes = tipo.opcionesVariante.length > 0 && formulario.modalidad !== 'pieza_unica'
    formulario.variantes = formulario.tieneVariantes ? [inicializarVariante(tipo.opcionesVariante)] : []
  }
}

function elegirTipo(tipo: TipoProductoEmprendedoraWeb) {
  if (esEdicion.value || guardando.value || bloqueadoPorReintento.value || formulario.tipoProductoId === tipo.id) return
  error.value = ''
  seleccionarTipo(tipo)
}

function elegirModalidad(modalidad: ProductoApi['modalidad']) {
  formulario.modalidad = modalidad
  if (modalidad === 'pieza_unica') {
    formulario.tieneVariantes = false
    formulario.variantes = []
    limpiarFotosVariantes()
    formulario.manejaStock = true
    formulario.stockInicial = '1'
    formulario.alAgotarse = 'ocultar'
  }
}

function cambiarSinVariantes(sinVariantes: boolean) {
  if (sinVariantes) {
    formulario.tieneVariantes = false
    formulario.variantes = []
    limpiarFotosVariantes()
  } else if (tipoSeleccionado.value?.opcionesVariante.length) {
    formulario.tieneVariantes = true
    if (!formulario.variantes.length) formulario.variantes = [inicializarVariante(tipoSeleccionado.value.opcionesVariante)]
  }
}

function agregarVariante() {
  if (!tipoSeleccionado.value || formulario.variantes.length >= 20) return
  formulario.variantes.push(inicializarVariante(tipoSeleccionado.value.opcionesVariante))
}

function quitarVariante(id: string) {
  if (formulario.variantes.find((variante) => variante.id === id)?.existente) return
  formulario.variantes = formulario.variantes.filter((variante) => variante.id !== id)
  quitarFotoVariante(id)
}

function errorArchivoImagen(archivo: File): string {
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(archivo.type)) return 'La imagen debe ser PNG, JPEG o WebP.'
  if (archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) return 'La imagen debe pesar hasta 5 MiB.'
  return ''
}

function seleccionarFoto(evento: Event) {
  const input = evento.target as HTMLInputElement
  const archivo = input.files?.[0]
  input.value = ''
  if (!archivo || guardando.value || intencion.value) return
  aplicarFotoPrincipal(archivo)
}

function abrirFotoVariante(id: string, camara: boolean) {
  if (guardando.value || intencion.value) return
  varianteFotoObjetivo.value = id
  ;(camara ? entradaCamaraVariante.value : entradaFotoVariante.value)?.click()
}

function seleccionarFotoVariante(evento: Event) {
  const input = evento.target as HTMLInputElement
  const archivo = input.files?.[0]
  const id = varianteFotoObjetivo.value
  input.value = ''
  varianteFotoObjetivo.value = null
  if (!archivo || !id || guardando.value || intencion.value) return
  aplicarFotoVariante(id, archivo)
}

function entero(valor: ValorNumericoFormulario, etiqueta: string): number | null {
  const texto = textoNumericoFormulario(valor)
  if (!/^\d+$/.test(texto)) {
    error.value = `${etiqueta} debe ser un número entero igual o mayor que cero.`
    return null
  }
  const numero = Number(texto)
  if (!Number.isSafeInteger(numero)) {
    error.value = `${etiqueta} está fuera del rango permitido.`
    return null
  }
  return numero
}

function prepararSolicitud(): { producto: ProductoApi; variantes: Record<string, unknown>[] } | null {
  error.value = ''
  const tipo = tipoSeleccionado.value
  if (!tipo || tipo.id !== formulario.tipoProductoId) {
    error.value = 'Selecciona un tipo de producto válido.'
    return null
  }
  const categoria = formulario.categoriaId
  if (!categoriasDisponibles.value.some((item) => item.id === categoria)) {
    error.value = 'Selecciona una categoría del tipo elegido.'
    return null
  }
  const nombre = formulario.nombre.trim()
  const descripcion = formulario.descripcion.trim()
  if (!nombre || nombre.length > 120) {
    error.value = 'El nombre es obligatorio y admite hasta 120 caracteres.'
    return null
  }
  if (!descripcion || descripcion.length > 2000) {
    error.value = 'La descripción es obligatoria y admite hasta 2.000 caracteres.'
    return null
  }
  const atributosEspecificos = Object.fromEntries(tipo.campos
    .map((campo) => [campo.clave, formulario.atributosEspecificos[campo.clave]?.trim() ?? ''] as const)
    .filter(([, valor]) => valor !== ''))
  for (const campo of tipo.campos) {
    const valor = formulario.atributosEspecificos[campo.clave]?.trim() ?? ''
    if (valor.length > 500) {
      error.value = `${campo.etiqueta} admite hasta 500 caracteres.`
      return null
    }
    if (campo.obligatorio && !valor) {
      error.value = `Completa el campo «${campo.etiqueta}».`
      return null
    }
  }
  const producto: ProductoApi = {
    nombre,
    descripcion,
    tipoProductoId: tipo.id,
    versionTipoProducto: tipo.version,
    categoriaId: categoria,
    atributosEspecificos,
    tieneVariantes: formulario.tieneVariantes,
    moneda: 'CLP',
    unidadVenta: 'unidad',
    pasoCantidad: 1,
    modalidad: formulario.modalidad,
    manejaStock: formulario.manejaStock,
    alAgotarse: formulario.alAgotarse,
  }

  if (formulario.tieneVariantes) {
    if (!tipo.opcionesVariante.length || formulario.variantes.length < 1 || formulario.variantes.length > 20) {
      error.value = 'Agrega entre una y veinte variantes para este tipo de producto.'
      return null
    }
  } else {
    const precio = entero(formulario.precioBase, 'El precio')
    if (precio === null) return null
    producto.precioBase = precio
    if (formulario.manejaStock) {
      const stock = entero(formulario.stockInicial, 'El stock inicial')
      if (stock === null) return null
      if (formulario.modalidad === 'pieza_unica' && stock !== 1) {
        error.value = 'Una pieza única debe comenzar con una unidad de stock.'
        return null
      }
      if (!idProductoActivo.value) producto.stockInicial = stock
    }
  }

  const combinaciones = new Set<string>()
  const variantes: Record<string, unknown>[] = []
  for (const variante of formulario.tieneVariantes ? formulario.variantes : []) {
    const opciones = Object.fromEntries(tipo.opcionesVariante.map((clave) => [clave, variante.opciones[clave]?.trim() ?? '']))
    if (Object.values(opciones).some((valor) => !valor || valor.length > 100)) {
      error.value = 'Completa todas las opciones de cada variante (hasta 100 caracteres por opción).'
      return null
    }
    const firma = JSON.stringify(Object.values(opciones).map((valor) => valor.trim().toLocaleLowerCase('es-CL')))
    if (combinaciones.has(firma)) {
      error.value = 'No repitas la misma combinación de opciones.'
      return null
    }
    combinaciones.add(firma)
    const precio = entero(variante.precio, 'El precio de la variante')
    if (precio === null) return null
    const entrada: Record<string, unknown> = {
      varianteId: variante.id,
      opciones,
      precio,
      activa: variante.activa,
    }
    if (formulario.manejaStock && !variante.existente) {
      const stock = entero(variante.stock, 'El stock inicial de la variante')
      if (stock === null) return null
      entrada.stockInicial = stock
    }
    variantes.push(entrada)
  }
  const totalImagenes = imagenesActuales.value + (fotoSeleccionada.value && !imagenesActuales.value ? 1 : 0) + cantidadFotosVariantes.value
  if (totalImagenes > MAXIMO_IMAGENES) {
    error.value = `Un producto admite hasta ${MAXIMO_IMAGENES} fotos en total, contando la principal y las de variantes. Quita alguna foto de variante.`
    return null
  }
  return { producto, variantes }
}

async function cargarFormulario() {
  cargando.value = true
  error.value = ''
  try {
    const servicio = cliente()
    const [tiposDisponibles, catalogos, original] = await Promise.all([
      listarTiposProductoEmprendedora(servicio.firebase as ServiciosCliente),
      $catalogoPublico.listarCatalogosPublicos(),
      props.productoId
        ? leerProductoEmprendedora(servicio.firebase as ServiciosCliente, props.cuenta.tienda.id, props.cuenta.tienda.mostrarPrecios, props.productoId)
        : Promise.resolve(null),
    ])
    tipos.value = tiposDisponibles
    categorias.value = catalogos.categorias
    productoOriginal.value = original ?? null
    idProductoActivo.value = original?.id ?? null
    versionEsperada.value = original?.versionEsperada ?? ''
    if (original) {
      formulario.nombre = original.nombre
      formulario.descripcion = original.descripcion
      formulario.modalidad = original.modalidad as ProductoApi['modalidad']
      formulario.manejaStock = original.manejaStock
      formulario.alAgotarse = original.alAgotarse as ProductoApi['alAgotarse']
      formulario.tieneVariantes = original.tieneVariantes
      formulario.precioBase = original.precioBase === null ? '' : String(original.precioBase)
      formulario.stockInicial = original.stock === null ? '0' : String(original.stock)
      formulario.atributosEspecificos = { ...original.atributosEspecificos }
      formulario.variantes = original.variantes.map((variante) => inicializarVariante([], variante))
      let tipo = tiposDisponibles.find((elemento) => elemento.id === original.tipoProductoId && elemento.version === original.versionTipoProducto)
      if (!tipo) tipo = await leerTipoProductoEmprendedora(servicio.firebase as ServiciosCliente, original.tipoProductoId, original.versionTipoProducto)
      definicion.value = tipo
      formulario.tipoProductoId = tipo.id
      formulario.categoriaId = original.categoriaId
    } else {
      const valoresDefecto = props.cuenta.tienda.configuracion?.valoresDefecto
      elegirModalidad(valoresDefecto?.modalidad ?? 'regular')
      if (formulario.modalidad !== 'pieza_unica') {
        formulario.manejaStock = valoresDefecto?.manejaStock ?? true
        formulario.alAgotarse = valoresDefecto?.alAgotarse ?? 'mostrar_agotado'
      }
      const tipo = tiposVisibles.value[0]
      if (tipo) seleccionarTipo(tipo)
      const borrador = await leerBorradorLocal().catch(() => null)
      if (borrador) {
        restaurandoBorrador.value = true
        Object.assign(formulario, borrador.formulario)
        paso.value = pasoDesdeBorrador(borrador)
        if (borrador.foto instanceof File) {
          aplicarFotoPrincipal(borrador.foto, borrador.opcionesFoto ?? { recorteCuadrado: false, mejorarLuz: false })
        }
        const tipoBorrador = tiposDisponibles.find((elemento) => elemento.id === formulario.tipoProductoId)
        if (tipoBorrador) definicion.value = tipoBorrador
        if (!categoriasDisponibles.value.some((categoria) => categoria.id === formulario.categoriaId)) formulario.categoriaId = ''
        for (const [id, archivo] of Object.entries(borrador.fotosVariantes ?? {})) {
          if (!(archivo instanceof File) || !formulario.variantes.some((variante) => variante.id === id)) continue
          fotosVariantes.value[id] = archivo
          previewsVariantes.value[id] = URL.createObjectURL(archivo)
        }
        aviso.value = 'Recuperamos tu avance guardado en este navegador. Puedes continuar donde quedaste.'
        restaurandoBorrador.value = false
      }
    }
    if (!tiposDisponibles.length && !original) error.value = 'No hay tipos de producto activos. Pide a administración que configure el catálogo.'
    else if (!categorias.value.length) error.value = 'No hay categorías activas para crear productos.'
  } catch (fallo) {
    error.value = fallo instanceof Error ? fallo.message : 'No se pudieron cargar los datos del editor.'
  } finally {
    cargando.value = false
  }
}

onMounted(cargarFormulario)

onBeforeUnmount(() => clearTimeout(temporizadorBorrador))

function errorDelPaso(numero: PasoEditor): string {
  const tipo = tipoSeleccionado.value
  if (numero === 2) {
    if (!formulario.nombre.trim() || formulario.nombre.trim().length > 120) return 'Escribe un nombre de hasta 120 caracteres.'
    if (!formulario.descripcion.trim() || formulario.descripcion.trim().length > 2000) return 'Completa una descripción de hasta 2.000 caracteres.'
  } else if (numero === 3) {
    if (!tipo || tipo.id !== formulario.tipoProductoId) return 'Selecciona un tipo de producto.'
  } else if (numero === 4) {
    if (!categoriasDisponibles.value.length) return 'Este tipo aún no tiene categorías activas. Pide a administración que las configure.'
    if (!categoriasDisponibles.value.some((categoria) => categoria.id === formulario.categoriaId)) return 'Selecciona una categoría.'
    const campoFaltante = tipo?.campos.find((campo) => campo.obligatorio && !formulario.atributosEspecificos[campo.clave]?.trim())
    if (campoFaltante) return `Completa el campo «${campoFaltante.etiqueta}».`
  } else if (numero === 5) {
    if (formulario.tieneVariantes) {
      if (!formulario.variantes.length) return 'Agrega al menos una variante o indica que el producto no tiene variantes.'
      const incompleta = formulario.variantes.some((variante) => !tipo?.opcionesVariante.every((opcion) => variante.opciones[opcion]?.trim()) || enteroNoNegativoFormulario(variante.precio) === null)
      if (incompleta) return 'Completa las opciones y el precio de cada variante.'
      if (formulario.manejaStock && formulario.variantes.some((variante) => !variante.existente && enteroNoNegativoFormulario(variante.stock) === null)) return 'Ingresa el stock inicial de cada variante como un entero de cero o más.'
    } else {
      if (enteroNoNegativoFormulario(formulario.precioBase) === null) return 'Ingresa el precio del producto como un número entero.'
      if (formulario.manejaStock && !esEdicion.value && enteroNoNegativoFormulario(formulario.stockInicial) === null) return 'Ingresa un stock inicial entero de cero o más.'
    }
    if (imagenesActuales.value + (fotoSeleccionada.value && !imagenesActuales.value ? 1 : 0) + cantidadFotosVariantes.value > MAXIMO_IMAGENES) return `Un producto admite hasta ${MAXIMO_IMAGENES} fotos en total. Quita alguna foto de variante.`
  } else if (numero === 6) {
    if (formulario.modalidad === 'pieza_unica' && formulario.manejaStock && !formulario.tieneVariantes && textoNumericoFormulario(formulario.stockInicial) !== '1') return 'Una pieza única debe comenzar con una unidad de stock.'
    if (formulario.manejaStock && !formulario.tieneVariantes && !esEdicion.value && enteroNoNegativoFormulario(formulario.stockInicial) === null) return 'Vuelve al paso de variantes e ingresa un stock inicial entero de cero o más.'
  }
  return ''
}

function validarPasoActual(): boolean {
  error.value = errorDelPaso(paso.value)
  return !error.value
}

function irAPaso(destino: PasoEditor) {
  if (guardando.value || bloqueadoPorReintento.value) return
  error.value = ''
  paso.value = destino
}

function avanzarPaso() {
  if (!validarPasoActual()) return
  if (paso.value < 7) paso.value = (paso.value + 1) as PasoEditor
  error.value = ''
  void guardarBorradorLocal().catch(() => {})
}

function retrocederPaso() {
  if (paso.value > 1) paso.value = (paso.value - 1) as PasoEditor
  error.value = ''
}

async function guardarAvance() {
  if (!escrituraDisponible.value) {
    try {
      await guardarBorradorLocal()
      aviso.value = 'Avance guardado en este navegador. La sincronización con Firebase requiere habilitar la API de escritura.'
    } catch (fallo) {
      error.value = fallo instanceof Error ? fallo.message : 'No se pudo guardar el avance local.'
    }
    return
  }
  const solicitud = prepararSolicitud()
  if (solicitud) {
    await guardar(false)
    return
  }
  try {
    await guardarBorradorLocal()
    aviso.value = 'Avance guardado en este navegador. Puedes seguir editando o volver después para continuar.'
  } catch (fallo) {
    error.value = fallo instanceof Error ? fallo.message : 'No se pudo guardar el avance local.'
  }
}

async function descartarAvance() {
  if (esEdicion.value) return emit('cancelar')
  try { await borrarBorradorLocal() } catch {}
  limpiarFotoPrincipal()
  limpiarFotosVariantes()
  emit('cancelar')
}

async function releerProducto(servicio: ReturnType<typeof cliente>, productoId: string, mensaje: string) {
  let producto: ProductoEmprendedoraWeb
  try {
    producto = await leerProductoEmprendedora(servicio.firebase as ServiciosCliente, props.cuenta.tienda.id, props.cuenta.tienda.mostrarPrecios, productoId)
  } catch {
    throw new Error(mensaje)
  }
  productoOriginal.value = producto
  versionEsperada.value = producto.versionEsperada
  return producto
}

async function guardar(publicar: boolean) {
  if (guardando.value) return
  if (procesandoFoto.value) {
    error.value = 'Espera a que termine de ajustarse la foto.'
    return
  }
  if (!escrituraDisponible.value) {
    error.value = 'La API de escritura no está habilitada en este sitio. Configura una API HTTPS en modo real-api para guardar productos e imágenes.'
    return
  }
  const solicitud = prepararSolicitud()
  if (!solicitud) return
  const archivo = fotoSeleccionada.value
  const fotosPorVariante = formulario.tieneVariantes
    ? Object.fromEntries(formulario.variantes.flatMap((variante) => fotosVariantes.value[variante.id] ? [[variante.id, fotosVariantes.value[variante.id]!] as const] : []))
    : {}
  const yaPublicado = productoOriginal.value?.estado === 'publicado'
  const sincronizarPublico = publicar || (yaPublicado && Boolean(archivo))
  if (sincronizarPublico && !tiendaPublicada.value) {
    error.value = 'Publica primero la ficha de tu tienda para que este producto aparezca en el catálogo.'
    return
  }
  if (sincronizarPublico && imagenesActuales.value === 0 && !archivo) {
    error.value = 'Agrega una imagen antes de publicar el producto.'
    return
  }
  const servicio = cliente()
  const idProducto = idProductoActivo.value ?? intencion.value?.productoId ?? crypto.randomUUID()
  const firma = JSON.stringify({
    productoId: idProducto,
    producto: solicitud.producto,
    variantes: solicitud.variantes,
    archivo: archivo ? [archivo.name, archivo.size, archivo.type, archivo.lastModified] : null,
    fotosVariantes: Object.entries(fotosPorVariante).map(([id, foto]) => [id, foto.name, foto.size, foto.lastModified]),
  })
  if (!intencion.value || (!intencion.value.productoGuardado && intencion.value.firma !== firma)) {
    intencion.value = {
      firma,
      productoId: idProducto,
      operacionId: crypto.randomUUID(),
      archivoId: crypto.randomUUID(),
      publicacionId: crypto.randomUUID(),
      productoGuardado: false,
      fotoSubida: false,
      fotosVariantes: Object.fromEntries(Object.keys(fotosPorVariante).map((id) => [id, { archivoId: crypto.randomUUID(), operacionId: crypto.randomUUID(), ruta: '', asignada: false }])),
    }
  }
  const pendiente = intencion.value
  guardando.value = true
  error.value = ''
  aviso.value = ''
  try {
    if (!pendiente.productoGuardado) {
      if (!idProductoActivo.value && !versionEsperada.value) {
        await servicio.llamar('crearProducto', {
          tiendaId: props.cuenta.tienda.id,
          productoId: pendiente.productoId,
          operacionId: pendiente.operacionId,
          producto: solicitud.producto,
          variantes: solicitud.variantes,
        })
      } else {
        if (!versionEsperada.value) throw new Error('La ficha no tiene una versión para editar. Actualiza el editor y vuelve a intentarlo.')
        await servicio.llamar('editarProducto', {
          tiendaId: props.cuenta.tienda.id,
          productoId: idProductoActivo.value ?? pendiente.productoId,
          operacionId: pendiente.operacionId,
          versionEsperada: versionEsperada.value,
          producto: solicitud.producto,
          variantes: solicitud.variantes,
        })
      }
      pendiente.productoGuardado = true
      idProductoActivo.value = pendiente.productoId
    }

    let productoActualizado = await releerProducto(servicio, pendiente.productoId, 'El producto se guardó, pero no se pudo volver a leer. Actualiza el editor para continuar.')

    if (archivo && !pendiente.fotoSubida) {
      const indicePrincipal = productoActualizado.imagenes.length ? 0 : undefined
      await servicio.subirImagenProducto(props.cuenta.tienda.id, pendiente.productoId, archivo, pendiente.archivoId, indicePrincipal)
      pendiente.fotoSubida = true
      productoActualizado = await releerProducto(servicio, pendiente.productoId, 'La imagen se guardó, pero no se pudo volver a leer el producto.')
    }

    let variantesActualizadas = false
    for (const [varianteId, foto] of Object.entries(fotosPorVariante)) {
      const estado = pendiente.fotosVariantes[varianteId]
      if (!estado || estado.asignada) continue
      if (!estado.ruta) estado.ruta = await servicio.subirImagenProducto(props.cuenta.tienda.id, pendiente.productoId, foto, estado.archivoId)
      await servicio.llamar('asignarImagenVariante', {
        tiendaId: props.cuenta.tienda.id,
        productoId: pendiente.productoId,
        varianteId,
        operacionId: estado.operacionId,
        imagen: estado.ruta,
      })
      estado.asignada = true
      variantesActualizadas = true
    }
    if (variantesActualizadas) productoActualizado = await releerProducto(servicio, pendiente.productoId, 'Las fotos de variantes se guardaron, pero no se pudo volver a leer el producto.')

    if (sincronizarPublico) {
      if (!versionEsperada.value) throw new Error('El producto se guardó, pero no tiene una versión para publicar. Actualiza la página e inténtalo de nuevo.')
      await servicio.llamar('publicarProducto', {
        tiendaId: props.cuenta.tienda.id,
        productoId: pendiente.productoId,
        operacionId: pendiente.publicacionId,
        versionEsperada: versionEsperada.value,
      })
    }

    limpiarFotoPrincipal()
    limpiarFotosVariantes()
    if (!props.productoId) await borrarBorradorLocal().catch(() => {})
    emit('guardado', { productoId: pendiente.productoId, publicado: sincronizarPublico || productoActualizado.estado === 'publicado' })
  } catch (fallo) {
    const mensaje = fallo instanceof Error ? fallo.message : 'No se pudo guardar el producto.'
    const resultadoIncierto = typeof fallo === 'object' && fallo !== null && 'resultadoIncierto' in fallo
      && (fallo as { resultadoIncierto?: unknown }).resultadoIncierto === true
    if (!pendiente.productoGuardado && !resultadoIncierto) intencion.value = null
    error.value = pendiente.productoGuardado
      ? `El producto ya se guardó. ${mensaje} Reintenta para completar las fotos o la publicación sin cambiar los datos.`
      : resultadoIncierto
        ? `No se confirmó el guardado. ${mensaje} Reintenta sin cambiar los datos para continuar la misma operación.`
        : mensaje
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <section class="editor-producto" aria-label="Editor de producto">
    <div v-if="cargando" class="estado-editor" role="status"><span class="rueda" />Cargando tus productos, tipos y categorías…</div>
    <form v-else class="asistente-producto" novalidate @submit.prevent="paso === 7 ? guardar(true) : avanzarPaso()">
      <div v-if="modoSoloLectura" class="aviso-solo-lectura" role="status">
        <span class="aviso-icono">i</span>
        <div><strong>El sitio publicado está en modo de solo lectura.</strong><p>El avance se conserva en este navegador, pero para sincronizar productos e imágenes con Firebase se debe habilitar una API HTTPS de escritura.</p></div>
      </div>

      <input ref="entradaFoto" class="entrada-oculta" type="file" accept="image/png,image/jpeg,image/webp" @change="seleccionarFoto" />
      <input ref="entradaCamara" class="entrada-oculta" type="file" accept="image/png,image/jpeg,image/webp" capture="environment" @change="seleccionarFoto" />
      <input ref="entradaFotoVariante" class="entrada-oculta" type="file" accept="image/png,image/jpeg,image/webp" @change="seleccionarFotoVariante" />
      <input ref="entradaCamaraVariante" class="entrada-oculta" type="file" accept="image/png,image/jpeg,image/webp" capture="environment" @change="seleccionarFotoVariante" />

      <div class="asistente-columnas">
        <main class="panel-paso">
          <img class="flor-fondo-paso" src="/amancay-flor-principal-v3.png" alt="" aria-hidden="true" />
          <div class="cabecera-paso">
            <span class="rotulo-nuevo">{{ esEdicion ? 'EDITAR PRODUCTO' : 'NUEVO PRODUCTO' }}</span>
            <h3>{{ cabecera.titulo }}</h3>
            <p>{{ cabecera.bajada }}</p>
            <strong class="contador-paso">{{ paso === 7 ? 'Último paso' : `Paso ${paso} de 6` }}</strong>
          </div>

          <nav class="progreso-pasos" aria-label="Progreso del producto" :style="{ '--avance': String(Math.min(paso - 1, 5) / 5) }">
            <button
              v-for="elemento in pasosEditor"
              :key="elemento.id"
              type="button"
              :class="{ actual: paso === elemento.id, completado: paso > elemento.id }"
              :aria-current="paso === elemento.id ? 'step' : undefined"
              :aria-label="`Paso ${elemento.id}: ${elemento.titulo}`"
              :disabled="guardando || bloqueadoPorReintento || elemento.id > paso"
              @click="irAPaso(elemento.id)"
            >
              <span>{{ paso > elemento.id ? '✓' : elemento.id }}</span><small>{{ elemento.titulo }}</small>
            </button>
          </nav>

          <section v-if="paso === 1" class="contenido-paso contenido-foto">
            <div class="foto-producto-grande" :class="{ procesando: procesandoFoto }">
              <img v-if="verOriginal && previewOriginal" :src="previewOriginal" alt="Foto original, sin ajustes" />
              <img v-else-if="fotoPreview" :src="fotoPreview" alt="Vista previa de la nueva foto del producto" />
              <span v-if="fotoOriginal" class="etiqueta-foto">{{ verOriginal ? 'Original' : 'Así se publicará' }}</span>
              <ImagenPublica v-else-if="productoOriginal?.imagenPublica" :ruta="productoOriginal.imagenPublica" :alt="productoOriginal.nombre" ajuste="contain" :zoom="false" />
              <div v-else class="foto-vacia"><span>✿</span><strong>Tu producto merece una buena foto</strong><small>JPG, PNG o WebP · hasta 5 MiB</small></div>
            </div>
            <div class="acciones-foto">
              <button class="boton-secundario" type="button" :disabled="guardando || bloqueadoPorReintento" @click="entradaFoto?.click()"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.galeria" /></svg>{{ fotoSeleccionada || productoOriginal?.imagenPublica ? 'Cambiar desde galería' : 'Elegir de la galería' }}</button>
              <button class="boton-secundario" type="button" :disabled="guardando || bloqueadoPorReintento" @click="abrirCamara({ destino: 'principal' })"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.camara" /></svg>Tomar foto</button>
            </div>
            <div v-if="fotoOriginal" class="ajustes-foto" aria-label="Ajustes de la foto">
              <div class="selector-segmentado" role="radiogroup" aria-label="Encuadre de la foto">
                <button type="button" role="radio" :aria-checked="opcionesFoto.recorteCuadrado" :class="{ activo: opcionesFoto.recorteCuadrado }" :disabled="guardando || bloqueadoPorReintento" @click="opcionesFoto.recorteCuadrado = true">Recorte cuadrado</button>
                <button type="button" role="radio" :aria-checked="!opcionesFoto.recorteCuadrado" :class="{ activo: !opcionesFoto.recorteCuadrado }" :disabled="guardando || bloqueadoPorReintento" @click="opcionesFoto.recorteCuadrado = false">Foto completa</button>
              </div>
              <label class="interruptor-luz">
                <span class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.sol" /></svg></span>
                <span><strong>Ajuste suave de luz</strong><small>Aclara un poco la foto si quedó oscura.</small></span>
                <input v-model="opcionesFoto.mejorarLuz" type="checkbox" role="switch" :disabled="guardando || bloqueadoPorReintento" />
              </label>
              <button type="button" class="boton-comparar" :aria-pressed="verOriginal" :disabled="procesandoFoto" @click="verOriginal = !verOriginal">{{ verOriginal ? 'Ver foto ajustada' : 'Comparar con la original' }}</button>
              <span v-if="procesandoFoto" class="texto-suave" role="status">Ajustando la foto…</span>
            </div>
            <span v-if="fotoSeleccionada && !fotoOriginal" class="foto-elegida">✓ Foto elegida</span>
            <span v-else-if="imagenesActuales" class="texto-suave">{{ imagenesActuales }} foto(s) guardada(s)</span>
            <ul class="consejos-foto" aria-label="Consejos para una buena foto">
              <li v-for="consejo in consejosFoto" :key="consejo.titulo"><span class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="consejo.icono" /></svg></span><span><strong>{{ consejo.titulo }}</strong><small>{{ consejo.detalle }}</small></span></li>
            </ul>
            <p v-if="esEdicion" class="ayuda-producto">Al elegir otra foto, reemplazará la imagen principal y conservará las demás. Si el producto ya está publicado, el cambio se verá en el catálogo.</p>
          </section>

          <section v-else-if="paso === 2" class="contenido-paso campos-producto">
            <label class="campo-producto campo-completo">Nombre del producto *<span class="campo-con-dictado"><input v-model="formulario.nombre" maxlength="120" autocomplete="off" placeholder="Ej.: Taza de flores silvestres" :disabled="guardando || bloqueadoPorReintento" /><EmprendedoraBotonDictado :valor="formulario.nombre" etiqueta="el nombre" :deshabilitado="guardando || bloqueadoPorReintento" @actualizar="formulario.nombre = $event.slice(0, 120)" @aviso="avisoDictado" /></span></label>
            <label class="campo-producto campo-completo">Descripción *<span class="campo-con-dictado area"><textarea v-model="formulario.descripcion" maxlength="2000" rows="5" placeholder="Cuenta sus características, materiales y medidas." :disabled="guardando || bloqueadoPorReintento" /><EmprendedoraBotonDictado :valor="formulario.descripcion" etiqueta="la descripción" :deshabilitado="guardando || bloqueadoPorReintento" @actualizar="formulario.descripcion = $event.slice(0, 2000)" @aviso="avisoDictado" /></span><span class="contador-caracteres">{{ formulario.descripcion.length }}/2000</span></label>
          </section>

          <section v-else-if="paso === 3" class="contenido-paso">
            <div class="lista-tipos" role="radiogroup" aria-label="Tipo de producto">
              <button
                v-for="tipo in tiposVisibles"
                :key="tipo.id"
                type="button"
                role="radio"
                class="tarjeta-opcion"
                :class="{ seleccionada: formulario.tipoProductoId === tipo.id }"
                :aria-checked="formulario.tipoProductoId === tipo.id"
                :disabled="guardando || bloqueadoPorReintento || (esEdicion && formulario.tipoProductoId !== tipo.id)"
                @click="elegirTipo(tipo)"
              >
                <span class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconoTipo[tipo.id] ?? iconos.estrella" /></svg></span>
                <span class="texto-opcion"><strong>{{ tipo.nombre }}</strong><small v-if="esEdicion && !ORDEN_TIPOS.includes(tipo.id)">Tipo histórico</small></span>
                <span class="radio-opcion" :class="{ marcado: formulario.tipoProductoId === tipo.id }" />
              </button>
            </div>
            <p v-if="esEdicion" class="ayuda-producto">El tipo se conserva al editar para respetar el historial del producto.</p>
          </section>

          <section v-else-if="paso === 4" class="contenido-paso">
            <label class="campo-producto selector-categoria">Categoría *
              <select v-model="formulario.categoriaId" :disabled="guardando || bloqueadoPorReintento || !categoriasDisponibles.length">
                <option value="" disabled>Elige una categoría</option>
                <option v-for="categoria in categoriasDisponibles" :key="categoria.id" :value="categoria.id">{{ categoria.nombre }}</option>
              </select>
            </label>
            <div v-if="tipoSeleccionado?.campos.length" class="campos-producto campos-detalle">
              <div class="campo-completo"><h4>Detalles de {{ tipoSeleccionado.nombre }}</h4><p class="ayuda-producto">Completa los campos específicos para este producto.</p></div>
              <label v-for="campo in tipoSeleccionado.campos" :key="campo.clave" class="campo-producto">{{ campo.etiqueta }}{{ campo.obligatorio ? ' *' : '' }}<span class="campo-con-dictado"><input v-model="formulario.atributosEspecificos[campo.clave]" maxlength="500" :placeholder="`Ingresa ${campo.etiqueta.toLocaleLowerCase('es-CL')}`" :disabled="guardando || bloqueadoPorReintento" /><EmprendedoraBotonDictado :valor="formulario.atributosEspecificos[campo.clave]" :etiqueta="campo.etiqueta.toLocaleLowerCase('es-CL')" :deshabilitado="guardando || bloqueadoPorReintento" @actualizar="formulario.atributosEspecificos[campo.clave] = $event.slice(0, 500)" @aviso="avisoDictado" /></span></label>
            </div>
            <div v-else class="nota-tipo"><span>✿</span><p>Este tipo de producto no requiere detalles adicionales.</p></div>
          </section>

          <section v-else-if="paso === 5" class="contenido-paso precio-paso">
            <label v-if="admiteVariantes" class="casilla-sin-variantes">
              <input type="checkbox" :checked="!formulario.tieneVariantes" :disabled="guardando || esEdicion || bloqueadoPorReintento" @change="cambiarSinVariantes(($event.target as HTMLInputElement).checked)" />
              <span>Este producto no tiene variantes</span>
            </label>
            <div v-if="!formulario.tieneVariantes" class="campos-sin-variantes">
              <label class="campo-producto">Precio (CLP) *<span class="campo-con-dictado"><input v-model="formulario.precioBase" type="number" min="0" step="1" inputmode="numeric" placeholder="Ingresa el precio" :disabled="guardando || bloqueadoPorReintento" /><EmprendedoraBotonDictado :valor="formulario.precioBase" etiqueta="el precio" numerico :deshabilitado="guardando || bloqueadoPorReintento" @actualizar="formulario.precioBase = $event" @aviso="avisoDictado" /></span></label>
              <label v-if="formulario.manejaStock" class="campo-producto">{{ esEdicion ? 'Stock actual' : 'Stock inicial' }}<span class="campo-con-dictado"><input v-model="formulario.stockInicial" type="number" min="0" step="1" inputmode="numeric" placeholder="Cantidad disponible" :disabled="guardando || esEdicion || formulario.modalidad === 'pieza_unica' || bloqueadoPorReintento" /><EmprendedoraBotonDictado :valor="formulario.stockInicial" etiqueta="el stock inicial" numerico :deshabilitado="guardando || esEdicion || formulario.modalidad === 'pieza_unica' || bloqueadoPorReintento" @actualizar="formulario.stockInicial = $event" @aviso="avisoDictado" /></span></label>
              <p v-if="esEdicion && formulario.manejaStock" class="ayuda-producto">Ajusta las unidades desde el catálogo para conservar el historial.</p>
            </div>
            <div v-else class="seccion-variantes">
              <div class="titulo-variantes"><div><h4>Variantes</h4><p>Cada variante guarda su foto, precio y stock por separado.</p></div></div>
              <article v-for="(variante, indice) in formulario.variantes" :key="variante.id" class="fila-variante">
                <div class="foto-variante">
                  <div class="miniatura-variante">
                    <img v-if="previewsVariantes[variante.id]" :src="previewsVariantes[variante.id]" :alt="`Foto de la variante ${indice + 1}`" />
                    <ImagenPublica v-else-if="variante.imagenPublica" :ruta="variante.imagenPublica" :alt="`Foto de la variante ${indice + 1}`" ajuste="contain" :zoom="false" />
                    <span v-else-if="variante.tieneImagen" class="foto-guardada">✓</span>
                    <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.galeria" /></svg>
                  </div>
                  <div class="acciones-foto-variante">
                    <strong>Foto de esta variante</strong>
                    <small v-if="previewsVariantes[variante.id]">Nueva foto elegida</small>
                    <small v-else-if="variante.tieneImagen">Foto guardada</small>
                    <div>
                      <button type="button" class="chip-foto" :disabled="guardando || bloqueadoPorReintento" @click="abrirFotoVariante(variante.id, false)">Galería</button>
                      <button type="button" class="chip-foto" :disabled="guardando || bloqueadoPorReintento" @click="abrirCamara({ destino: 'variante', id: variante.id })">Cámara</button>
                      <button v-if="previewsVariantes[variante.id]" type="button" class="enlace-quitar" :disabled="guardando || bloqueadoPorReintento" @click="quitarFotoVariante(variante.id)">Quitar</button>
                    </div>
                  </div>
                </div>
                <div class="campos-producto">
                  <label v-for="opcion in tipoSeleccionado?.opcionesVariante ?? []" :key="opcion" class="campo-producto">{{ opcion }} *<span class="campo-con-dictado"><input v-model="variante.opciones[opcion]" maxlength="100" :placeholder="opcion" :disabled="guardando || variante.existente || bloqueadoPorReintento" /><EmprendedoraBotonDictado v-if="!variante.existente" :valor="variante.opciones[opcion]" :etiqueta="opcion.toLocaleLowerCase('es-CL')" :deshabilitado="guardando || bloqueadoPorReintento" @actualizar="variante.opciones[opcion] = $event.slice(0, 100)" @aviso="avisoDictado" /></span></label>
                  <label class="campo-producto">Precio (CLP) *<span class="campo-con-dictado"><input v-model="variante.precio" type="number" min="0" step="1" inputmode="numeric" placeholder="Ej.: 18.000" :disabled="guardando || bloqueadoPorReintento" /><EmprendedoraBotonDictado :valor="variante.precio" etiqueta="el precio de la variante" numerico :deshabilitado="guardando || bloqueadoPorReintento" @actualizar="variante.precio = $event" @aviso="avisoDictado" /></span></label>
                  <label v-if="formulario.manejaStock" class="campo-producto">{{ variante.existente ? 'Stock actual' : 'Stock' }}<span class="campo-con-dictado"><input v-model="variante.stock" type="number" min="0" step="1" inputmode="numeric" placeholder="Ej.: 3" :disabled="guardando || variante.existente || bloqueadoPorReintento" /><EmprendedoraBotonDictado v-if="!variante.existente" :valor="variante.stock" etiqueta="el stock de la variante" numerico :deshabilitado="guardando || bloqueadoPorReintento" @actualizar="variante.stock = $event" @aviso="avisoDictado" /></span></label>
                </div>
                <div class="acciones-variante"><label class="check-variante"><input v-model="variante.activa" type="checkbox" :disabled="guardando || bloqueadoPorReintento" /> Variante activa</label><button v-if="!variante.existente" class="enlace-quitar" type="button" :disabled="guardando || bloqueadoPorReintento" @click="quitarVariante(variante.id)">Quitar variante nueva</button></div>
              </article>
              <button class="boton-secundario agregar-variante" type="button" :disabled="guardando || formulario.variantes.length >= 20 || bloqueadoPorReintento" @click="agregarVariante">＋ Agregar variante</button>
              <p v-if="esEdicion" class="ayuda-producto">El stock existente se modifica desde el catálogo para conservar el historial.</p>
            </div>
          </section>

          <section v-else-if="paso === 6" class="contenido-paso disponibilidad-paso">
            <div class="bloque-pregunta">
              <h4>Tipo de disponibilidad</h4>
              <p class="ayuda-producto">Selecciona cómo estará disponible este producto.</p>
            </div>
            <div class="opciones-disponibilidad" role="radiogroup" aria-label="Tipo de disponibilidad">
              <button
                v-for="opcion in opcionesModalidad"
                :key="opcion.id"
                type="button"
                role="radio"
                class="tarjeta-opcion con-detalle"
                :class="{ seleccionada: formulario.modalidad === opcion.id }"
                :aria-checked="formulario.modalidad === opcion.id"
                :disabled="guardando || esEdicion || (opcion.id === 'pieza_unica' && formulario.tieneVariantes) || bloqueadoPorReintento"
                @click="elegirModalidad(opcion.id)"
              >
                <span class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="opcion.icono" /></svg></span>
                <span class="texto-opcion"><strong>{{ opcion.nombre }}</strong><small>{{ opcion.detalle }}</small></span>
                <span class="radio-opcion" :class="{ marcado: formulario.modalidad === opcion.id }" />
              </button>
            </div>
            <div class="bloque-pregunta">
              <h4>¿Maneja stock?</h4>
              <p class="ayuda-producto">Indica si llevas control de la cantidad disponible.</p>
            </div>
            <div class="selector-segmentado" role="radiogroup" aria-label="¿Maneja stock?">
              <button type="button" role="radio" :aria-checked="formulario.manejaStock" :class="{ activo: formulario.manejaStock }" :disabled="guardando || esEdicion || formulario.modalidad === 'pieza_unica' || bloqueadoPorReintento" @click="formulario.manejaStock = true">Sí</button>
              <button type="button" role="radio" :aria-checked="!formulario.manejaStock" :class="{ activo: !formulario.manejaStock }" :disabled="guardando || esEdicion || formulario.modalidad === 'pieza_unica' || bloqueadoPorReintento" @click="formulario.manejaStock = false">No</button>
            </div>
            <template v-if="formulario.manejaStock && formulario.modalidad !== 'pieza_unica'">
              <div class="bloque-pregunta">
                <h4>Cuando se agote</h4>
                <p class="ayuda-producto">Elige qué quieres que pase cuando no haya más stock.</p>
              </div>
              <div class="opciones-disponibilidad" role="radiogroup" aria-label="Cuando se agote">
                <button
                  v-for="opcion in opcionesAgotado"
                  :key="opcion.id"
                  type="button"
                  role="radio"
                  class="tarjeta-opcion con-detalle"
                  :class="{ seleccionada: formulario.alAgotarse === opcion.id }"
                  :aria-checked="formulario.alAgotarse === opcion.id"
                  :disabled="guardando || esEdicion || bloqueadoPorReintento"
                  @click="formulario.alAgotarse = opcion.id"
                >
                  <span class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="opcion.icono" /></svg></span>
                  <span class="texto-opcion"><strong>{{ opcion.nombre }}</strong><small>{{ opcion.detalle }}</small></span>
                  <span class="radio-opcion" :class="{ marcado: formulario.alAgotarse === opcion.id }" />
                </button>
              </div>
            </template>
            <p v-if="formulario.modalidad === 'pieza_unica'" class="ayuda-producto">Una pieza única se vende con una unidad y se oculta del catálogo al venderse.</p>
            <p v-if="esEdicion" class="ayuda-producto">La modalidad y el control de stock se conservan al editar. Las ventas y los ajustes de stock se administran desde sus secciones.</p>
          </section>

          <section v-else class="contenido-paso revision-paso">
            <article class="tarjeta-revision">
              <div class="cabeza-revision">
                <div class="resumen-imagen"><img v-if="fotoPreview" :src="fotoPreview" alt="Vista previa" /><ImagenPublica v-else-if="productoOriginal?.imagenPublica" :ruta="productoOriginal.imagenPublica" :alt="formulario.nombre || 'Producto'" ajuste="contain" :zoom="false" /><svg v-else viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.camara" /></svg></div>
                <div class="resumen-datos"><span>{{ tipoSeleccionado?.nombre || 'PRODUCTO' }}</span><strong>{{ formulario.nombre || 'Nombre del producto' }}</strong><small>{{ nombreCategoria || 'Categoría pendiente' }}</small></div>
              </div>
              <p class="descripcion-revision">{{ formulario.descripcion || 'Sin descripción.' }}</p>
              <ul class="lineas-revision">
                <li v-if="!formulario.tieneVariantes">Precio: {{ precioResumen }}</li>
                <li v-else>Variantes: {{ precioResumen }}</li>
                <li>Modalidad: {{ textoModalidad }}</li>
                <li>Control de stock: {{ formulario.manejaStock ? 'Activado' : 'No se controla' }}</li>
                <li v-if="formulario.manejaStock && formulario.modalidad !== 'pieza_unica'">Al agotarse: {{ textoAgotado }}</li>
                <li v-if="formulario.manejaStock && !formulario.tieneVariantes">{{ esEdicion ? 'Stock actual' : 'Stock inicial' }}: {{ textoNumericoFormulario(formulario.stockInicial) || 'pendiente' }}</li>
                <li v-for="variante in formulario.tieneVariantes ? formulario.variantes : []" :key="variante.id">
                  {{ Object.values(variante.opciones).filter(Boolean).join(' / ') || 'Variante' }}: {{ textoNumericoFormulario(variante.precio) ? `$${Number(variante.precio).toLocaleString('es-CL')}` : 'precio pendiente' }}<template v-if="formulario.manejaStock"> · stock {{ textoNumericoFormulario(variante.stock) || 'pendiente' }}</template><template v-if="previewsVariantes[variante.id] || variante.tieneImagen"> · con foto</template>
                </li>
              </ul>
            </article>
            <p v-if="!fotoSeleccionada && !imagenesActuales" class="aviso-sin-foto">Puedes guardar el borrador sin foto, pero tendrás que agregar una imagen antes de publicarlo.</p>
            <p v-else-if="fotoSeleccionada && esEdicion" class="aviso-sin-foto">Al guardar, la nueva foto reemplazará la imagen principal y conservará las demás.</p>
            <div class="revision-final" aria-label="Secciones para revisar antes de guardar">
              <article class="fila-revision"><div class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.tienda" /></svg></div><div><strong>Información general</strong><p>Nombre, descripción y categoría</p></div><button type="button" @click="irAPaso(2)">Editar →</button></article>
              <article class="fila-revision"><div class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.galeria" /></svg></div><div><strong>Fotos del producto</strong><p>{{ fotoSeleccionada ? 'Nueva imagen principal elegida' : imagenesActuales ? `${imagenesActuales} foto(s) guardada(s)` : 'Imagen principal pendiente' }}<template v-if="cantidadFotosVariantes"> · {{ cantidadFotosVariantes }} de variantes</template></p></div><button type="button" @click="irAPaso(1)">Editar →</button></article>
              <article class="fila-revision"><div class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.etiqueta" /></svg></div><div><strong>Precio y variantes</strong><p>Precio, opciones y stock inicial</p></div><button type="button" @click="irAPaso(5)">Editar →</button></article>
              <article class="fila-revision"><div class="icono-circulo"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="iconos.caja" /></svg></div><div><strong>Stock y disponibilidad</strong><p>Modalidad, control de stock y agotamiento</p></div><button type="button" @click="irAPaso(6)">Editar →</button></article>
            </div>
            <p v-if="!tiendaPublicada" class="ayuda-producto">Tu tienda aún no está publicada. Puedes guardar el producto como borrador; para mostrarlo, publica primero la tienda.</p>
          </section>

          <p v-if="error" class="mensaje mensaje-error" role="alert">{{ error }}</p>
          <p v-if="aviso" class="mensaje mensaje-aviso" role="status">{{ aviso }}</p>
          <p v-if="bloqueadoPorReintento && !guardando" class="mensaje mensaje-aviso" role="status">Los datos quedaron bloqueados para que el reintento use la misma operación. Reintenta sin modificarlos.</p>

          <footer class="acciones-editor">
            <button v-if="paso === 1" class="boton-secundario" type="button" :disabled="guardando" @click="emit('cancelar')">Volver</button>
            <button v-else class="boton-secundario" type="button" :disabled="guardando" @click="retrocederPaso">Atrás</button>
            <button v-if="!productoOriginal || productoOriginal.estado !== 'publicado'" class="boton-secundario guardar-borrador" type="button" :disabled="guardando || bloqueadoPorReintento" @click="guardarAvance">{{ guardando ? 'Guardando…' : 'Guardar borrador' }}</button>
            <button v-if="paso < 7" class="boton-principal" :class="{ atenuado: !pasoActualValido }" type="button" :disabled="guardando || bloqueadoPorReintento" @click="avanzarPaso">Continuar <span>→</span></button>
            <button v-else class="boton-principal" type="submit" :disabled="guardando || bloqueadoPorReintento || !escrituraDisponible || (productoOriginal?.estado !== 'publicado' && (!tiendaPublicada || !puedePublicar))"><span v-if="guardando" class="rueda rueda-clara" />{{ guardando ? 'Guardando…' : botonPublicacion }}</button>
          </footer>
          <button v-if="!esEdicion" class="descartar-borrador" type="button" :disabled="guardando" @click="descartarAvance">♲ Descartar borrador local</button>
        </main>

        <aside class="vista-previa-panel">
          <span class="sobre-titulo">VISTA PREVIA</span>
          <div class="tarjeta-previa">
            <div class="imagen-previa-producto"><img v-if="fotoPreview" :src="fotoPreview" :alt="formulario.nombre || 'Producto'" /><ImagenPublica v-else-if="productoOriginal?.imagenPublica" :ruta="productoOriginal.imagenPublica" :alt="formulario.nombre || 'Producto'" ajuste="contain" :zoom="false" /><span v-else>✿</span></div>
            <div class="cuerpo-previa"><small>{{ props.cuenta.tienda.nombre }}</small><h4>{{ formulario.nombre || 'Nombre del producto' }}</h4><p>{{ formulario.descripcion || 'Aquí aparecerá una descripción breve para tus clientes.' }}</p><span class="categoria-previa">{{ nombreCategoria || 'Categoría' }}</span><strong>{{ precioResumen }}</strong></div>
          </div>
          <p class="texto-previa">Así se verá la ficha en tu catálogo público.</p>
          <div v-if="esEdicion && productoOriginal" class="estado-publicacion"><span :class="productoOriginal.estado">●</span>{{ productoOriginal.estado === 'publicado' ? 'Publicado' : productoOriginal.estado === 'borrador' ? 'Borrador' : 'Retirado' }}</div>
        </aside>
      </div>
    </form>
    <EmprendedoraCamaraProducto
      v-if="camaraAbierta"
      :titulo="camaraAbierta.destino === 'principal' ? 'Foto del producto' : 'Foto de la variante'"
      @capturada="alCapturarFoto"
      @cerrar="camaraAbierta = null"
    />
  </section>
</template>

<style scoped>
.editor-producto{display:grid;gap:22px;padding-bottom:30px;color:var(--texto)}.encabezado-editor-producto{display:flex;align-items:flex-end;justify-content:space-between;gap:24px}.sobre-titulo{display:block;color:var(--vino);font-size:11px;font-weight:800;letter-spacing:.16em}.encabezado-editor-producto h2{margin:8px 0 6px;color:var(--amancay-bordo-oscuro);font:400 clamp(38px,5vw,60px)/1.02 'DM Serif Display',Georgia,serif}.encabezado-editor-producto p,.ayuda-producto,.texto-previa{margin:0;color:var(--suave);font-size:14px;line-height:1.65}.fecha-editor{padding-bottom:6px;color:var(--vino);font-size:11px;font-weight:800;text-transform:uppercase}.asistente-producto{display:grid;gap:18px}.asistente-columnas{display:grid;grid-template-columns:minmax(0,1fr) 290px;align-items:start;gap:22px}.panel-paso,.vista-previa-panel{min-width:0;border:1px solid var(--borde);border-radius:25px;background:var(--amancay-superficie);box-shadow:0 12px 34px rgba(63,34,44,.045)}.panel-paso{position:relative;overflow:hidden;padding:clamp(20px,3vw,34px)}.panel-paso::before{position:absolute;top:-185px;right:-145px;width:430px;height:430px;border-radius:50%;background:radial-gradient(circle at 52% 55%,rgba(245,184,102,.22),rgba(239,141,111,.12) 38%,transparent 69%);content:'';pointer-events:none}.cabecera-paso,.progreso-pasos,.contenido-paso,.mensaje,.acciones-editor,.descartar-borrador{position:relative}.cabecera-paso .rotulo-paso{display:flex;align-items:center;justify-content:space-between;gap:16px}.rotulo-paso span{color:#e76b62;font-size:11px;font-weight:800;letter-spacing:.15em}.rotulo-paso strong{color:var(--vino);font-size:12px}.cabecera-paso h3{margin:12px 0 5px;color:var(--amancay-bordo-oscuro);font:400 clamp(34px,4vw,48px)/1.06 'DM Serif Display',Georgia,serif}.cabecera-paso>p{margin:0;color:var(--suave);font-size:15px;line-height:1.55}.progreso-pasos{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));margin:26px 0 28px}.progreso-pasos::before{position:absolute;top:16px;right:8%;left:8%;height:3px;background:#ead9cd;content:''}.progreso-pasos button{z-index:1;display:grid;justify-items:center;gap:8px;border:0;background:transparent;color:var(--suave);cursor:pointer}.progreso-pasos button span{display:grid;place-items:center;width:34px;height:34px;border:1px solid #e8d5c9;border-radius:50%;background:#fff8f3;font-size:12px;font-weight:800}.progreso-pasos button.actual span,.progreso-pasos button.completado span{border-color:var(--vino);background:var(--vino);color:#fff}.progreso-pasos button small{font-size:10px;font-weight:700}.progreso-pasos button:disabled{cursor:default}.contenido-paso{display:grid;align-content:start;gap:20px;min-height:320px}.contenido-foto{justify-items:center;gap:16px}.foto-producto-grande{display:grid;place-items:center;width:min(100%,500px);height:290px;overflow:hidden;border:1px solid #ead9cd;border-radius:24px;background:linear-gradient(145deg,#fff9ef,#f6e6e5)}.foto-producto-grande>img{width:100%;height:100%;object-fit:contain}.foto-producto-grande :deep(img){max-width:100%;max-height:100%;object-fit:contain}.foto-vacia{display:grid;justify-items:center;gap:9px;padding:24px;text-align:center;color:var(--suave)}.foto-vacia span,.nota-tipo>span,.resumen-imagen>span,.imagen-previa-producto>span{color:var(--vino);font:58px 'DM Serif Display',Georgia,serif}.foto-vacia strong{color:var(--vino);font-size:15px}.foto-vacia small{font-size:12px}.acciones-foto{display:flex;align-items:center;justify-content:center;flex-wrap:wrap;gap:12px}.foto-elegida{padding:9px 15px;border-radius:99px;background:#e7f2e8;color:#34734b;font-size:12px;font-weight:800}.texto-suave{color:var(--suave);font-size:12px}.contenido-foto>.ayuda-producto{max-width:520px;text-align:center}.campos-producto{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px 20px}.campo-producto{display:grid;align-content:start;gap:8px;min-width:0;color:var(--texto);font-size:13px;font-weight:800}.campo-producto input,.campo-producto textarea,.campo-producto select{width:100%;min-width:0;min-height:52px;padding:13px 15px;border:1px solid #e7d5c6;border-radius:15px;background:#fffdf9;color:var(--texto);font:500 14px 'Plus Jakarta Sans',sans-serif;outline:none}.campo-producto textarea{min-height:145px;resize:vertical;line-height:1.6}.campo-producto input:focus,.campo-producto textarea:focus,.campo-producto select:focus{border-color:var(--vino);box-shadow:0 0 0 3px rgba(116,36,78,.12)}.campo-producto input:disabled,.campo-producto textarea:disabled,.campo-producto select:disabled{background:#f8f2ed;color:#786b6c}.campo-completo{grid-column:1/-1}.contador-caracteres{justify-self:end;color:var(--suave);font-size:11px;font-weight:500}.tipo-paso>.campo-producto{max-width:520px}.campos-detalle{padding-top:18px;border-top:1px solid var(--borde)}.campos-detalle h4,.stock-paso h4{margin:0;color:var(--vino);font:400 24px 'DM Serif Display',Georgia,serif}.nota-tipo{display:flex;align-items:center;gap:16px;padding:20px;border:1px dashed var(--borde);border-radius:18px;background:#fffaf4}.nota-tipo>span{font-size:34px}.nota-tipo p{margin:0;color:var(--suave);font-size:13px}.pregunta-seccion{margin:0;color:var(--texto);font-size:15px;font-weight:800}.opciones-si-no{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.opciones-si-no button{min-height:50px;padding:10px 14px;border:1px solid var(--borde);border-radius:14px;background:#fff;color:var(--vino);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.opciones-si-no button.seleccionada{border:2px solid var(--vino);background:#fbf0f1}.opciones-si-no button:disabled{opacity:.65;cursor:default}.seccion-variantes{display:grid;gap:14px}.titulo-variantes{display:flex;align-items:center;justify-content:space-between;gap:16px}.titulo-variantes h4{margin:0;color:var(--vino);font:400 26px 'DM Serif Display',Georgia,serif}.titulo-variantes p{margin:3px 0 0;color:var(--suave);font-size:12px}.fila-variante{display:grid;gap:14px;padding:18px;border:1px solid var(--borde);border-radius:18px;background:#fffdf9}.fila-variante>strong{color:var(--vino);font-size:13px}.acciones-variante{display:flex;align-items:center;justify-content:space-between;gap:14px}.check-variante{display:flex;align-items:center;gap:8px;color:var(--texto);font-size:12px}.check-variante input,.opciones-agotado input{width:17px;height:17px;accent-color:var(--vino)}.enlace-quitar{border:0;background:transparent;color:var(--vino);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.opciones-disponibilidad{display:grid;gap:11px}.opcion-disponibilidad{display:grid;grid-template-columns:48px minmax(0,1fr) auto;align-items:center;gap:14px;min-height:80px;padding:14px 17px;border:1px solid var(--borde);border-radius:18px;background:#fffdf9;text-align:left;cursor:pointer}.opcion-disponibilidad.seleccionada{border:2px solid var(--vino);background:#fff5f0}.opcion-disponibilidad:disabled{cursor:default;opacity:.7}.icono-disponibilidad{display:grid;place-items:center;width:46px;height:46px;border-radius:50%;background:#fde9df;color:var(--vino);font-size:22px}.opcion-disponibilidad strong,.opciones-agotado strong{display:block;color:var(--vino);font-size:14px}.opcion-disponibilidad small,.opciones-agotado small{display:block;margin-top:3px;color:var(--suave);font-size:12px;line-height:1.45}.radio-disponibilidad{color:var(--vino);font-size:22px}.stock-paso{gap:13px}.stock-paso>.campo-producto{max-width:360px;margin-top:5px}.ayuda-stock-variantes{padding:12px;border-radius:12px;background:#fbf0f1;color:var(--suave);font-size:12px}.opciones-agotado{display:grid;gap:8px}.opciones-agotado label{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border:1px solid var(--borde);border-radius:14px;background:#fffdf9}.opciones-agotado input{flex:0 0 auto;margin-top:2px}.resumen-revision{display:grid;grid-template-columns:82px minmax(0,1fr) auto;align-items:center;gap:15px;padding:17px;border:1px solid var(--borde);border-radius:18px;background:#fff}.resumen-imagen{display:grid;place-items:center;width:82px;height:82px;overflow:hidden;border-radius:15px;background:#f7e8dd}.resumen-imagen img{width:100%;height:100%;object-fit:contain}.resumen-imagen :deep(img){max-width:100%;max-height:100%;object-fit:contain}.resumen-imagen>span{font-size:35px}.resumen-datos{display:grid;gap:4px;min-width:0}.resumen-datos>span{color:var(--vino);font-size:9px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.resumen-datos>strong{overflow:hidden;color:var(--texto);font:400 22px 'DM Serif Display',Georgia,serif;text-overflow:ellipsis;white-space:nowrap}.resumen-datos p{display:-webkit-box;overflow:hidden;margin:0;color:var(--suave);font-size:11px;line-height:1.4;-webkit-box-orient:vertical;-webkit-line-clamp:2}.resumen-datos>b{color:var(--vino);font-size:13px}.editar-resumen{border:0;background:transparent;color:var(--vino);font:700 11px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.aviso-solo-lectura{display:flex;align-items:flex-start;gap:13px;padding:15px 17px;border:1px solid #edceaa;border-radius:16px;background:#fff4e3;color:#66482e}.aviso-solo-lectura strong{font-size:13px}.aviso-solo-lectura p{margin:4px 0 0;font-size:12px;line-height:1.5}.aviso-icono{display:grid;place-items:center;flex:0 0 24px;height:24px;border-radius:50%;background:#f5dcae;font-weight:800}.vista-previa-panel{position:sticky;top:20px;display:grid;gap:14px;padding:20px}.tarjeta-previa{overflow:hidden;border:1px solid var(--borde);border-radius:18px;background:#fff}.imagen-previa-producto{display:grid;place-items:center;height:210px;overflow:hidden;background:linear-gradient(140deg,#fbf0e1,#f5e0df)}.imagen-previa-producto img{width:100%;height:100%;object-fit:contain}.imagen-previa-producto :deep(img){max-width:100%;max-height:100%;object-fit:contain}.imagen-previa-producto>span{font-size:54px}.cuerpo-previa{display:grid;gap:7px;padding:15px}.cuerpo-previa>small{color:var(--suave);font-size:10px}.cuerpo-previa h4{overflow:hidden;margin:0;color:var(--vino);font:400 23px/1.15 'DM Serif Display',Georgia,serif;text-overflow:ellipsis}.cuerpo-previa p{display:-webkit-box;overflow:hidden;min-height:42px;margin:0;color:var(--suave);font-size:11px;line-height:1.5;-webkit-box-orient:vertical;-webkit-line-clamp:3}.categoria-previa{justify-self:start;padding:6px 9px;border-radius:999px;background:#faf0df;color:#6f5860;font-size:10px}.cuerpo-previa>strong{color:var(--vino);font:400 23px 'DM Serif Display',Georgia,serif}.texto-previa{font-size:11px}.estado-publicacion{display:flex;align-items:center;gap:7px;padding-top:10px;border-top:1px solid var(--borde);color:var(--suave);font-size:11px}.estado-publicacion .publicado{color:#3c7a48}.estado-publicacion .borrador{color:#e9a62f}.estado-publicacion .archivado{color:#987e83}.acciones-editor{display:flex;align-items:center;justify-content:flex-end;flex-wrap:wrap;gap:10px;padding-top:22px;border-top:1px solid var(--borde)}.boton-secundario,.boton-principal{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:48px;padding:0 19px;border-radius:15px;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.boton-secundario{border:1px solid var(--borde);background:#fff;color:var(--vino)}.boton-principal{border:1px solid var(--vino);background:var(--vino);color:#fff}.boton-principal span:last-child{font-size:17px}.boton-secundario:disabled,.boton-principal:disabled{opacity:.45;cursor:not-allowed}.boton-texto,.descartar-borrador{border:0;background:transparent;color:var(--vino);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.guardar-borrador{margin-right:auto}.descartar-borrador{justify-self:center;padding:6px}.mensaje{margin:0;padding:13px 15px;border-radius:14px;font-size:12px;line-height:1.55}.mensaje-error{background:#fff0ee;color:#852e3f}.mensaje-aviso{background:#edf7ed;color:#286344}.estado-editor{display:flex;align-items:center;gap:10px;padding:20px;border:1px solid var(--borde);border-radius:16px;background:var(--amancay-superficie);color:var(--suave);font-size:13px}.rueda{width:18px;height:18px;border:2px solid var(--borde);border-top-color:var(--vino);border-radius:50%;animation:girar .8s linear infinite}.rueda-clara{border-color:#ffffff70;border-top-color:white}@keyframes girar{to{transform:rotate(360deg)}}.entrada-oculta{display:none}
.revision-final{display:grid;gap:10px}.fila-revision{display:grid;grid-template-columns:42px minmax(0,1fr) auto;align-items:center;gap:12px;padding:13px 15px;border:1px solid var(--borde);border-radius:16px;background:#fff}.icono-revision{display:grid;place-items:center;width:40px;height:40px;border-radius:50%;background:#fde8df;color:var(--vino);font-size:18px}.fila-revision strong{display:block;color:var(--vino);font-size:13px}.fila-revision p{display:-webkit-box;overflow:hidden;margin:3px 0 0;color:var(--suave);font-size:11px;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}.fila-revision button{border:0;background:transparent;color:var(--vino);font:700 11px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.cabecera-paso,.progreso-pasos,.contenido-paso,.mensaje,.acciones-editor,.descartar-borrador{z-index:1}
.panel-paso{overflow:clip}
.flor-fondo-paso{position:absolute;top:-30px;right:-70px;z-index:0;width:min(360px,62%);opacity:.2;pointer-events:none;user-select:none}
.rotulo-nuevo{display:block;color:#e76b62;font-size:12px;font-weight:800;letter-spacing:.16em}
.cabecera-paso h3{margin:10px 0 6px}
.contador-paso{display:block;margin-top:14px;color:var(--vino);font-size:13px;font-weight:800}
.progreso-pasos{grid-template-columns:repeat(6,minmax(0,1fr));margin:18px 0 26px}
.progreso-pasos::before{right:calc(100% / 12);left:calc(100% / 12)}
.progreso-pasos::after{position:absolute;top:16px;left:calc(100% / 12);width:calc((100% - 100% / 6) * var(--avance,0));height:3px;background:var(--vino);content:'';transition:width .25s ease}
.progreso-pasos button.actual span{box-shadow:0 0 0 4px rgba(116,36,78,.14)}
.icono-circulo{display:grid;flex:0 0 auto;place-items:center;width:46px;height:46px;border-radius:50%;background:#fde4d8;color:#e2725b}
.icono-circulo svg{width:22px;height:22px;fill:currentColor}
.boton-secundario svg{width:18px;height:18px;fill:currentColor}
.lista-tipos,.opciones-disponibilidad{display:grid;gap:12px}
.tarjeta-opcion{display:grid;grid-template-columns:48px minmax(0,1fr) 24px;align-items:center;gap:16px;width:100%;min-height:72px;padding:12px 20px;border:1px solid #ecd8cb;border-radius:20px;background:#fffdf9;color:var(--texto);text-align:left;cursor:pointer;transition:border-color .15s ease,box-shadow .15s ease,transform .15s ease,background .15s ease}
.tarjeta-opcion:hover:not(:disabled){border-color:#d8b3a3;box-shadow:0 8px 22px rgba(63,34,44,.07);transform:translateY(-1px)}
.tarjeta-opcion:focus-visible,.selector-segmentado button:focus-visible,.chip-foto:focus-visible,.casilla-sin-variantes:focus-within{outline:3px solid rgba(116,36,78,.35);outline-offset:2px}
.tarjeta-opcion.seleccionada{border:2px solid var(--vino);background:#fff1ec;box-shadow:0 8px 22px rgba(116,36,78,.08)}
.tarjeta-opcion:disabled{cursor:default}
.tarjeta-opcion:disabled:not(.seleccionada){opacity:.55;background:#f4eeee}
.texto-opcion strong{display:block;color:var(--vino);font-size:16px;font-weight:800;line-height:1.3}
.texto-opcion small{display:block;margin-top:3px;color:var(--suave);font-size:12.5px;line-height:1.45}
.radio-opcion{position:relative;width:22px;height:22px;border:2px solid #5d4a4f;border-radius:50%}
.radio-opcion.marcado{border-color:var(--vino)}
.radio-opcion.marcado::after{position:absolute;inset:3px;border-radius:50%;background:var(--vino);content:''}
.bloque-pregunta h4{margin:6px 0 0;color:var(--texto);font:800 17px 'Plus Jakarta Sans',sans-serif}
.bloque-pregunta p{margin:4px 0 0}
.selector-segmentado{display:grid;grid-template-columns:1fr 1fr;overflow:hidden;border:1px solid var(--vino);border-radius:16px;background:#f9ebe8}
.selector-segmentado button{min-height:50px;border:0;border-radius:15px;background:transparent;color:var(--vino);font:700 15px 'Plus Jakarta Sans',sans-serif;cursor:pointer;transition:background .15s ease,color .15s ease}
.selector-segmentado button.activo{background:var(--vino);color:#fff}
.selector-segmentado button:disabled{cursor:default}
.selector-segmentado button:disabled:not(.activo){opacity:.6}
.casilla-sin-variantes{display:flex;align-items:center;gap:14px;min-height:62px;padding:12px 20px;border:1px solid #ecd8cb;border-radius:18px;background:#fffdf9;color:var(--texto);font-size:15px;cursor:pointer}
.casilla-sin-variantes input{width:22px;height:22px;accent-color:var(--vino)}
.campos-sin-variantes{display:grid;gap:16px;max-width:460px}
.selector-categoria{max-width:560px}
.selector-categoria select{appearance:none;padding-right:44px;background:#fffdf9 url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath fill='%2374244e' d='M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z'/%3E%3C/svg%3E") no-repeat right 14px center/22px;font-size:15px;cursor:pointer}
.campos-detalle h4{margin:0}
.campos-detalle .ayuda-producto{margin-top:4px}
.foto-variante{display:flex;align-items:center;gap:16px}
.miniatura-variante{display:grid;flex:0 0 auto;place-items:center;width:88px;height:88px;overflow:hidden;border-radius:16px;background:linear-gradient(145deg,#f7c8a5,#ef9f84);color:#fff}
.miniatura-variante img{width:100%;height:100%;object-fit:cover}
.miniatura-variante :deep(img){width:100%;height:100%;object-fit:cover}
.miniatura-variante svg{width:34px;height:34px;fill:currentColor}
.foto-guardada{font-size:30px;font-weight:800}
.acciones-foto-variante strong{display:block;color:var(--texto);font-size:15px}
.acciones-foto-variante small{display:block;margin-top:2px;color:#34734b;font-size:12px;font-weight:700}
.acciones-foto-variante div{display:flex;align-items:center;flex-wrap:wrap;gap:8px;margin-top:8px}
.chip-foto{min-height:40px;padding:0 18px;border:0;border-radius:999px;background:#f4e6ea;color:var(--vino);font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer;transition:background .15s ease}
.chip-foto:hover:not(:disabled){background:#ecd3db}
.chip-foto:disabled{opacity:.5;cursor:default}
.agregar-variante{justify-self:start}
.consejos-foto{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;width:100%;max-width:640px;margin:4px 0 0;padding:0;list-style:none}
.consejos-foto li{display:flex;align-items:center;gap:10px;padding:12px;border:1px solid var(--borde);border-radius:16px;background:rgba(255,253,249,.92)}
.consejos-foto .icono-circulo{width:38px;height:38px}
.consejos-foto .icono-circulo svg{width:20px;height:20px}
.consejos-foto strong{display:block;color:var(--vino);font-size:13px}
.consejos-foto small{display:block;margin-top:2px;color:var(--suave);font-size:11.5px;line-height:1.4}
.tarjeta-revision{display:grid;gap:12px;padding:20px;border:1px solid #ecd8cb;border-radius:22px;background:#fff}
.cabeza-revision{display:flex;align-items:center;gap:16px;min-width:0}
.resumen-imagen svg{width:34px;height:34px;fill:#ee9a6c}
.resumen-datos small{color:var(--suave);font-size:13px}
.resumen-datos>strong{font-size:24px}
.descripcion-revision{margin:0;color:var(--texto);font-size:15px;line-height:1.55;white-space:pre-line}
.lineas-revision{display:grid;gap:5px;margin:0;padding:0;color:var(--suave);font-size:14px;list-style:none}
.aviso-sin-foto{margin:0;padding:13px 16px;border-radius:14px;background:#f6e6e6;color:var(--texto);font-size:13px;line-height:1.5}
.fila-revision{grid-template-columns:46px minmax(0,1fr) auto;padding:14px 16px;border-radius:18px;transition:box-shadow .15s ease}
.fila-revision:hover{box-shadow:0 8px 20px rgba(63,34,44,.06)}
.fila-revision strong{font-size:15px}
.fila-revision p{font-size:12.5px}
.fila-revision button{font-size:13px}
.boton-principal.atenuado{opacity:.55}
.boton-principal:hover:not(:disabled){box-shadow:0 8px 20px rgba(116,36,78,.22)}
@media(prefers-reduced-motion:reduce){.tarjeta-opcion,.progreso-pasos::after,.selector-segmentado button,.chip-foto{transition:none}.tarjeta-opcion:hover:not(:disabled){transform:none}}
.campo-con-dictado{position:relative;display:block;min-width:0}
.campo-con-dictado input,.campo-con-dictado textarea{padding-right:54px}
.campo-con-dictado.area :deep(.boton-dictado){top:8px;transform:none}
.campo-con-dictado input[type=number]{-moz-appearance:textfield;appearance:textfield}
.campo-con-dictado input[type=number]::-webkit-inner-spin-button,.campo-con-dictado input[type=number]::-webkit-outer-spin-button{margin:0;-webkit-appearance:none}
.foto-producto-grande{position:relative;height:320px}
.foto-producto-grande>img{position:absolute;inset:0}
.foto-producto-grande.procesando>img{opacity:.55;transition:opacity .15s ease}
.etiqueta-foto{position:absolute;top:12px;left:12px;padding:6px 11px;border-radius:999px;background:rgba(255,250,243,.92);color:var(--vino);font-size:11px;font-weight:800;box-shadow:0 4px 12px rgba(63,34,44,.12)}
.ajustes-foto{display:grid;gap:10px;width:100%;max-width:520px}
.ajustes-foto .selector-segmentado button{padding:0 8px;font-size:clamp(12px,3.4vw,15px)}
.interruptor-luz{display:grid;grid-template-columns:42px minmax(0,1fr) auto;align-items:center;gap:12px;padding:10px 14px;border:1px solid #ecd8cb;border-radius:18px;background:#fffdf9;cursor:pointer}
.interruptor-luz .icono-circulo{width:40px;height:40px}
.interruptor-luz strong{display:block;color:var(--vino);font-size:14px}
.interruptor-luz small{display:block;margin-top:2px;color:var(--suave);font-size:12px}
.interruptor-luz input{position:relative;width:46px;height:26px;margin:0;border-radius:999px;background:#e6d4d8;cursor:pointer;transition:background .15s ease;appearance:none}
.interruptor-luz input::after{position:absolute;top:3px;left:3px;width:20px;height:20px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.2);content:'';transition:transform .15s ease}
.interruptor-luz input:checked{background:var(--vino)}
.interruptor-luz input:checked::after{transform:translateX(20px)}
.interruptor-luz input:focus-visible{outline:3px solid rgba(116,36,78,.35);outline-offset:2px}
.interruptor-luz input:disabled{opacity:.5;cursor:default}
.boton-comparar{justify-self:center;min-height:40px;padding:0 16px;border:0;border-radius:999px;background:#f4e6ea;color:var(--vino);font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-comparar[aria-pressed=true]{background:var(--vino);color:#fff}
.boton-comparar:disabled{opacity:.5;cursor:default}
.boton-comparar:focus-visible{outline:3px solid rgba(116,36,78,.35);outline-offset:2px}
@media(max-width:980px){.asistente-columnas{grid-template-columns:minmax(0,1fr) 250px;gap:14px}.vista-previa-panel{padding:15px}.imagen-previa-producto{height:175px}}
@media(max-width:760px){.editor-producto{gap:16px;padding-bottom:95px}.encabezado-editor-producto{align-items:flex-start}.encabezado-editor-producto h2{font-size:38px}.fecha-editor{display:none}.asistente-columnas{grid-template-columns:minmax(0,1fr)}.vista-previa-panel{position:static;grid-row:2}.tarjeta-previa{display:grid;grid-template-columns:110px minmax(0,1fr)}.imagen-previa-producto{height:100%;min-height:145px}.cuerpo-previa{padding:12px}.cuerpo-previa h4{font-size:20px}.panel-paso{padding:20px 16px;border-radius:21px}.progreso-pasos{margin:21px 0 23px}.progreso-pasos button{gap:6px}.progreso-pasos button span{width:30px;height:30px}.progreso-pasos::before{top:14px}.progreso-pasos button small{font-size:9px}.cabecera-paso h3{font-size:36px}.cabecera-paso>p{font-size:14px}.contenido-paso{min-height:280px}.foto-producto-grande{height:min(66vw,310px)}.campos-producto{grid-template-columns:minmax(0,1fr);gap:14px}.campo-completo{grid-column:auto}.opciones-si-no{grid-template-columns:1fr}.acciones-editor{position:fixed;z-index:5;right:0;bottom:0;left:0;display:grid;grid-template-columns:auto auto minmax(140px,1fr);gap:8px;padding:12px max(14px,env(safe-area-inset-left)) calc(12px + env(safe-area-inset-bottom));border-top:1px solid var(--borde);background:rgba(255,250,243,.97);box-shadow:0 -8px 24px #48212c12;backdrop-filter:blur(12px)}.acciones-editor .boton-secundario,.acciones-editor .boton-principal{min-height:46px;padding:0 12px;font-size:11px}.guardar-borrador{margin-right:0}.acciones-editor .boton-texto{padding:0 6px}.descartar-borrador{margin-top:8px}.resumen-revision{grid-template-columns:64px minmax(0,1fr);gap:10px;padding:12px}.resumen-imagen{width:64px;height:64px}.editar-resumen{grid-column:2;justify-self:start;padding:4px 0}.aviso-solo-lectura{padding:12px}}
@media(max-width:390px){.acciones-editor{grid-template-columns:auto minmax(0,1fr)}.guardar-borrador{display:none}.progreso-pasos button small{font-size:8px}.progreso-pasos button span{width:27px;height:27px}.progreso-pasos::before{top:12px}.cabecera-paso h3{font-size:32px}}
@media(max-width:760px){.flor-fondo-paso{top:-10px;right:-90px;width:300px}.progreso-pasos::before,.progreso-pasos::after{top:14px}.progreso-pasos button small{display:none}.tarjeta-opcion{grid-template-columns:44px minmax(0,1fr) 22px;gap:12px;min-height:66px;padding:10px 14px;border-radius:18px}.icono-circulo{width:42px;height:42px}.texto-opcion strong{font-size:15px}.consejos-foto{grid-template-columns:minmax(0,1fr)}.foto-variante{align-items:flex-start}.miniatura-variante{width:76px;height:76px}.acciones-editor{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.acciones-editor .guardar-borrador{grid-column:1/-1;grid-row:1}}
@media(max-width:390px){.progreso-pasos::before,.progreso-pasos::after{top:12px}.acciones-editor{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}}
</style>
