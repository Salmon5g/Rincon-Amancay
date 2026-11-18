<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { cargarCuentaEmprendedora, leerTipoProductoEmprendedora, listarTiposProductoEmprendedora, type CuentaEmprendedoraWeb, type ProductoEmprendedoraWeb, type TipoProductoEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { ServiciosCliente, Operacion } from '~/lib/cliente-base'
import type { ElementoEtiqueta } from '~/repositories/catalogo-publico'
import { enteroNoNegativoFormulario, textoNumericoFormulario, type ValorNumericoFormulario } from '~/lib/numeros-producto'

type VarianteFormulario = {
  id: string
  opciones: Record<string, string>
  precio: ValorNumericoFormulario
  stock: ValorNumericoFormulario
  activa: boolean
  existente: boolean
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

type IntencionGuardado = {
  firma: string
  productoId: string
  operacionId: string
  archivoId: string
  publicacionId: string
  productoGuardado: boolean
  fotoSubida: boolean
}

type PasoEditor = 1 | 2 | 3 | 4 | 5
type BorradorLocalProducto = {
  paso: PasoEditor
  formulario: typeof formulario
  foto: File | null
}

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb; productoId?: string | null }>()
const emit = defineEmits<{ cancelar: []; guardado: [resultado: { productoId: string; publicado: boolean }] }>()

const { $catalogoPublico } = useNuxtApp()
const runtime = useRuntimeConfig()
const paso = ref<PasoEditor>(1)
const restaurandoBorrador = ref(false)
const modoSoloLectura = computed(() => runtime.public.firebaseModo === 'real-lectura')
const escrituraDisponible = computed(() => ['local', 'real-api'].includes(String(runtime.public.firebaseModo)))
const pasosEditor = [
  { id: 1 as const, titulo: 'Foto', resumen: 'Muestra tu producto' },
  { id: 2 as const, titulo: 'Datos', resumen: 'Nombre y categoría' },
  { id: 3 as const, titulo: 'Tipo', resumen: 'Detalles del producto' },
  { id: 4 as const, titulo: 'Precio', resumen: 'Variantes y stock' },
  { id: 5 as const, titulo: 'Publicación', resumen: 'Disponibilidad y revisión' },
]
const pasoActual = computed(() => pasosEditor.find((elemento) => elemento.id === paso.value) ?? pasosEditor[0])
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
const categoriasDisponibles = computed(() => {
  const resultado = [...categorias.value]
  const actual = productoOriginal.value?.categoriaId
  if (actual && !resultado.some((item) => item.id === actual)) resultado.unshift({ id: actual, nombre: actual })
  return resultado
})
const imagenesActuales = computed(() => productoOriginal.value?.imagenes.length ?? 0)
const puedePublicar = computed(() => tiendaPublicada.value && (imagenesActuales.value > 0 || fotoSeleccionada.value !== null))
const botonPublicacion = computed(() => productoOriginal.value?.estado === 'publicado' ? 'Guardar y actualizar el catálogo' : 'Guardar y publicar')
const bloqueadoPorReintento = computed(() => Boolean(intencion.value))
const claveBorrador = computed(() => `producto:${props.cuenta.uid}:${props.cuenta.tienda.id}`)
const precioResumen = computed(() => formulario.tieneVariantes
  ? `${formulario.variantes.length} ${formulario.variantes.length === 1 ? 'variante' : 'variantes'}`
  : formulario.precioBase ? `$${Number(formulario.precioBase).toLocaleString('es-CL')}` : 'Precio pendiente')

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
        paso: paso.value,
        formulario: JSON.parse(JSON.stringify(formulario)),
        foto: fotoSeleccionada.value,
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

let temporizadorBorrador: ReturnType<typeof setTimeout> | undefined
watch(() => [paso.value, JSON.stringify(formulario), fotoSeleccionada.value] as const, () => {
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

onBeforeUnmount(limpiarFotoPreview)

function inicializarVariante(opciones: string[] = [], existente?: ProductoEmprendedoraWeb['variantes'][number]): VarianteFormulario {
  return {
    id: existente?.id ?? crypto.randomUUID(),
    opciones: existente ? { ...existente.opciones } : Object.fromEntries(opciones.map((opcion) => [opcion, ''])),
    precio: existente ? String(existente.precio) : '',
    stock: existente?.stock === null || existente?.stock === undefined ? '1' : String(existente.stock),
    activa: existente?.activa ?? true,
    existente: Boolean(existente),
  }
}

function seleccionarTipo(tipo: TipoProductoEmprendedoraWeb | undefined) {
  definicion.value = tipo ?? null
  if (!tipo) return
  formulario.tipoProductoId = tipo.id
  formulario.atributosEspecificos = Object.fromEntries(tipo.campos.map((campo) => [campo.clave, '']))
  if (!productoOriginal.value) {
    formulario.tieneVariantes = tipo.opcionesVariante.length > 0
    formulario.variantes = formulario.tieneVariantes ? [inicializarVariante(tipo.opcionesVariante)] : []
  }
}

function tipoCambiado() {
  seleccionarTipo(tipos.value.find((tipo) => tipo.id === formulario.tipoProductoId))
}

function modalidadCambiada() {
  if (formulario.modalidad === 'pieza_unica') {
    formulario.tieneVariantes = false
    formulario.variantes = []
    formulario.manejaStock = true
    formulario.stockInicial = '1'
    formulario.alAgotarse = 'ocultar'
  }
}

function agregarVariante() {
  if (!tipoSeleccionado.value || formulario.variantes.length >= 20) return
  formulario.variantes.push(inicializarVariante(tipoSeleccionado.value.opcionesVariante))
}

function quitarVariante(id: string) {
  formulario.variantes = formulario.variantes.filter((variante) => variante.id !== id || variante.existente)
}

function seleccionarFoto(evento: Event) {
  if (guardando.value || intencion.value) return
  const input = evento.target as HTMLInputElement
  const archivo = input.files?.[0]
  if (!archivo) return
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(archivo.type)) {
    error.value = 'La imagen debe ser PNG, JPEG o WebP.'
  } else if (archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) {
    error.value = 'La imagen debe pesar hasta 5 MiB.'
  } else {
    error.value = ''
    limpiarFotoPreview()
    fotoSeleccionada.value = archivo
    fotoPreview.value = URL.createObjectURL(archivo)
  }
  input.value = ''
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
    error.value = 'Selecciona una categoría activa.'
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
  return { producto, variantes }
}

async function cargarFormulario() {
  cargando.value = true
  error.value = ''
  try {
    const servicio = cliente()
    const [tiposDisponibles, catalogos] = await Promise.all([
      listarTiposProductoEmprendedora(servicio.firebase as ServiciosCliente),
      $catalogoPublico.listarCatalogosPublicos(),
    ])
    tipos.value = tiposDisponibles
    categorias.value = catalogos.categorias
    const original = props.productoId ? props.cuenta.productos.find((producto) => producto.id === props.productoId) : null
    productoOriginal.value = original ?? null
    idProductoActivo.value = original?.id ?? null
    versionEsperada.value = original?.versionEsperada ?? ''
    if (original) {
      formulario.nombre = original.nombre
      formulario.descripcion = original.descripcion
      formulario.categoriaId = original.categoriaId
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
    } else {
      formulario.modalidad = 'regular'
      formulario.manejaStock = true
      formulario.alAgotarse = 'mostrar_agotado'
      const tipo = tiposDisponibles[0]
      if (tipo) seleccionarTipo(tipo)
      formulario.categoriaId = catalogos.categorias[0]?.id ?? ''
      const borrador = await leerBorradorLocal().catch(() => null)
      if (borrador) {
        restaurandoBorrador.value = true
        Object.assign(formulario, borrador.formulario)
        paso.value = [1, 2, 3, 4, 5].includes(borrador.paso) ? borrador.paso : 1
        if (borrador.foto instanceof File) {
          fotoSeleccionada.value = borrador.foto
          fotoPreview.value = URL.createObjectURL(borrador.foto)
        }
        const tipoBorrador = tiposDisponibles.find((tipo) => tipo.id === formulario.tipoProductoId)
        if (tipoBorrador) definicion.value = tipoBorrador
        aviso.value = 'Recuperamos tu avance guardado en este navegador. Puedes continuar donde quedaste.'
        restaurandoBorrador.value = false
      }
    }
    if (!tiposDisponibles.length && !original) error.value = 'No hay tipos de producto activos. Pide a administración que configure el catálogo.'
    if (!categoriasDisponibles.value.length) error.value = 'No hay categorías activas para crear productos.'
  } catch (fallo) {
    error.value = fallo instanceof Error ? fallo.message : 'No se pudieron cargar los datos del editor.'
  } finally {
    cargando.value = false
  }
}

onMounted(cargarFormulario)

onBeforeUnmount(() => clearTimeout(temporizadorBorrador))

function validarPasoActual(): boolean {
  error.value = ''
  if (paso.value === 1) return true
  if (paso.value === 2) {
    if (!formulario.nombre.trim() || formulario.nombre.trim().length > 120) error.value = 'Escribe un nombre de hasta 120 caracteres.'
    else if (!formulario.descripcion.trim() || formulario.descripcion.trim().length > 2000) error.value = 'Completa una descripción de hasta 2.000 caracteres.'
    else if (!categoriasDisponibles.value.some((categoria) => categoria.id === formulario.categoriaId)) error.value = 'Selecciona una categoría.'
  } else if (paso.value === 3) {
    if (!tipoSeleccionado.value || tipoSeleccionado.value.id !== formulario.tipoProductoId) error.value = 'Selecciona un tipo de producto.'
    else {
      const campoFaltante = tipoSeleccionado.value.campos.find((campo) => campo.obligatorio && !formulario.atributosEspecificos[campo.clave]?.trim())
      if (campoFaltante) error.value = `Completa el campo «${campoFaltante.etiqueta}».`
    }
  } else if (paso.value === 4) {
    if (formulario.tieneVariantes && !formulario.variantes.length) error.value = 'Agrega al menos una variante o indica que el producto no tiene variantes.'
    else if (!formulario.tieneVariantes && enteroNoNegativoFormulario(formulario.precioBase) === null) error.value = 'Ingresa el precio del producto como un número entero.'
    else if (formulario.tieneVariantes) {
      const camposIncompletos = formulario.variantes.some((variante) => !tipoSeleccionado.value?.opcionesVariante.every((opcion) => variante.opciones[opcion]?.trim()) || enteroNoNegativoFormulario(variante.precio) === null)
      if (camposIncompletos) error.value = 'Completa las opciones y el precio de cada variante.'
    }
  } else {
    const stockInvalido = formulario.manejaStock && !formulario.tieneVariantes && (enteroNoNegativoFormulario(formulario.stockInicial) === null || (formulario.modalidad === 'pieza_unica' && textoNumericoFormulario(formulario.stockInicial) !== '1'))
    if (stockInvalido) error.value = formulario.modalidad === 'pieza_unica' ? 'Una pieza única debe comenzar con una unidad de stock.' : 'Ingresa un stock inicial entero igual o mayor que cero.'
    else if (formulario.manejaStock && formulario.tieneVariantes && formulario.variantes.some((variante) => !variante.existente && enteroNoNegativoFormulario(variante.stock) === null)) error.value = 'Ingresa el stock inicial de cada variante.'
    else if (tipoSeleccionado.value?.campos.some((campo) => campo.obligatorio && !formulario.atributosEspecificos[campo.clave]?.trim())) error.value = 'Completa los detalles obligatorios del producto.'
  }
  return !error.value
}

function avanzarPaso() {
  if (!validarPasoActual()) return
  if (paso.value < 5) paso.value = (paso.value + 1) as PasoEditor
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
  fotoSeleccionada.value = null
  limpiarFotoPreview()
  emit('cancelar')
}

async function guardar(publicar: boolean) {
  if (guardando.value) return
  if (!escrituraDisponible.value) {
    error.value = 'La API de escritura no está habilitada en este sitio. Configura una API HTTPS en modo real-api para guardar productos e imágenes.'
    return
  }
  const solicitud = prepararSolicitud()
  if (!solicitud) return
  const archivo = fotoSeleccionada.value
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

    let actualizada = await cargarCuentaEmprendedora(servicio.firebase as ServiciosCliente, props.cuenta.uid)
    let productoActualizado = actualizada.productos.find((producto) => producto.id === pendiente.productoId)
    if (!productoActualizado) throw new Error('El producto se guardó, pero no se pudo volver a leer. Actualiza el editor para continuar.')
    productoOriginal.value = productoActualizado
    versionEsperada.value = productoActualizado.versionEsperada

    if (archivo && !pendiente.fotoSubida) {
      const indicePrincipal = productoActualizado.imagenes.length ? 0 : undefined
      await servicio.subirImagenProducto(props.cuenta.tienda.id, pendiente.productoId, archivo, pendiente.archivoId, indicePrincipal)
      pendiente.fotoSubida = true
      actualizada = await cargarCuentaEmprendedora(servicio.firebase as ServiciosCliente, props.cuenta.uid)
      productoActualizado = actualizada.productos.find((producto) => producto.id === pendiente.productoId)
      if (!productoActualizado) throw new Error('La imagen se guardó, pero no se pudo volver a leer el producto.')
      productoOriginal.value = productoActualizado
      versionEsperada.value = productoActualizado.versionEsperada
    }

    if (sincronizarPublico) {
      if (!versionEsperada.value) throw new Error('El producto se guardó, pero no tiene una versión para publicar. Actualiza la página e inténtalo de nuevo.')
      await servicio.llamar('publicarProducto', {
        tiendaId: props.cuenta.tienda.id,
        productoId: pendiente.productoId,
        operacionId: pendiente.publicacionId,
        versionEsperada: versionEsperada.value,
      })
    }

    fotoSeleccionada.value = null
    limpiarFotoPreview()
    if (entradaFoto.value) entradaFoto.value.value = ''
    if (!props.productoId) await borrarBorradorLocal().catch(() => {})
    emit('guardado', { productoId: pendiente.productoId, publicado: sincronizarPublico || productoActualizado.estado === 'publicado' })
  } catch (fallo) {
    const mensaje = fallo instanceof Error ? fallo.message : 'No se pudo guardar el producto.'
    const resultadoIncierto = typeof fallo === 'object' && fallo !== null && 'resultadoIncierto' in fallo
      && (fallo as { resultadoIncierto?: unknown }).resultadoIncierto === true
    if (!pendiente.productoGuardado && !resultadoIncierto) intencion.value = null
    error.value = pendiente.productoGuardado
      ? `El producto ya se guardó. ${mensaje} Reintenta para completar la imagen o publicación sin cambiar los datos.`
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
    <form v-else class="asistente-producto" novalidate @submit.prevent="paso === 5 ? guardar(true) : avanzarPaso()">
      <div v-if="modoSoloLectura" class="aviso-solo-lectura" role="status">
        <span class="aviso-icono">i</span>
        <div><strong>El sitio publicado está en modo de solo lectura.</strong><p>El avance se conserva en este navegador, pero para sincronizar productos e imágenes con Firebase se debe habilitar una API HTTPS de escritura.</p></div>
      </div>

      <div class="asistente-columnas">
        <main class="panel-paso">
          <div class="cabecera-paso">
            <div class="rotulo-paso"><span>{{ esEdicion ? 'EDITAR PRODUCTO' : 'NUEVO PRODUCTO' }}</span><strong>Paso {{ paso }} de 5</strong></div>
            <h3>{{ pasoActual.titulo === 'Foto' ? 'Agrega una foto' : pasoActual.titulo === 'Datos' ? 'Datos del producto' : pasoActual.titulo === 'Tipo' ? 'Tipo de producto' : pasoActual.titulo === 'Precio' ? 'Precio y variantes' : 'Disponibilidad' }}</h3>
            <p>{{ pasoActual.resumen }}<template v-if="paso === 1">. Muéstrale a tus clientes lo que vendes.</template><template v-else-if="paso === 5">. Revisa la información antes de guardar.</template></p>
          </div>

          <nav class="progreso-pasos" aria-label="Progreso del producto">
            <button v-for="elemento in pasosEditor" :key="elemento.id" type="button" :class="{ actual: paso === elemento.id, completado: paso > elemento.id }" :aria-current="paso === elemento.id ? 'step' : undefined" :disabled="guardando || bloqueadoPorReintento || elemento.id > paso" @click="paso = elemento.id; error = ''">
              <span>{{ paso > elemento.id ? '✓' : elemento.id }}</span><small>{{ elemento.titulo }}</small>
            </button>
          </nav>

          <section v-if="paso === 1" class="contenido-paso contenido-foto">
            <input ref="entradaFoto" class="entrada-oculta" type="file" accept="image/png,image/jpeg,image/webp" @change="seleccionarFoto" />
            <div class="foto-producto-grande">
              <img v-if="fotoPreview" :src="fotoPreview" alt="Vista previa de la nueva foto del producto" />
              <ImagenPublica v-else-if="productoOriginal?.imagenPublica" :ruta="productoOriginal.imagenPublica" :alt="productoOriginal.nombre" ajuste="contain" :zoom="false" />
              <div v-else class="foto-vacia"><span>✿</span><strong>Tu producto merece una buena foto</strong><small>JPG, PNG o WebP · hasta 5 MiB</small></div>
            </div>
            <div class="acciones-foto">
              <button class="boton-secundario" type="button" :disabled="guardando || bloqueadoPorReintento" @click="entradaFoto?.click()">{{ fotoSeleccionada || productoOriginal?.imagenPublica ? 'Cambiar imagen' : 'Elegir de tus archivos' }}</button>
              <span v-if="fotoSeleccionada" class="foto-elegida">✓ Foto elegida</span>
              <span v-else-if="imagenesActuales" class="texto-suave">{{ imagenesActuales }} foto(s) guardada(s)</span>
            </div>
            <p class="ayuda-producto">Al reemplazar la imagen principal se conservará el resto de la galería. Si el producto ya está publicado, el cambio se reflejará en el catálogo público.</p>
          </section>

          <section v-else-if="paso === 2" class="contenido-paso campos-producto">
            <label class="campo-producto campo-completo">Nombre del producto *<input v-model="formulario.nombre" maxlength="120" autocomplete="off" placeholder="Ej.: Taza de flores silvestres" :disabled="guardando || bloqueadoPorReintento" /></label>
            <label class="campo-producto campo-completo">Descripción *<textarea v-model="formulario.descripcion" maxlength="2000" rows="5" placeholder="Cuenta sus características, materiales y medidas." :disabled="guardando || bloqueadoPorReintento" /><span class="contador-caracteres">{{ formulario.descripcion.length }}/2000</span></label>
            <label class="campo-producto campo-completo">Categoría *
              <select v-model="formulario.categoriaId" :disabled="guardando || bloqueadoPorReintento"><option value="" disabled>Elige una categoría</option><option v-for="categoria in categoriasDisponibles" :key="categoria.id" :value="categoria.id">{{ categoria.nombre }}</option></select>
            </label>
          </section>

          <section v-else-if="paso === 3" class="contenido-paso tipo-paso">
            <label class="campo-producto">Tipo de producto *
              <select v-model="formulario.tipoProductoId" :disabled="guardando || esEdicion || bloqueadoPorReintento" @change="tipoCambiado">
                <option value="" disabled>Selecciona un tipo</option><option v-for="tipo in tipos" :key="tipo.id" :value="tipo.id">{{ tipo.nombre }}</option><option v-if="esEdicion && definicion && !tipos.some((tipo) => tipo.id === definicion?.id)" :value="definicion.id">{{ definicion.nombre }} · histórico</option>
              </select>
            </label>
            <p v-if="esEdicion" class="ayuda-producto">El tipo se conserva al editar para respetar el historial del producto.</p>
            <div v-if="tipoSeleccionado?.campos.length" class="campos-producto campos-detalle">
              <h4 class="campo-completo">Detalles de {{ tipoSeleccionado.nombre }}</h4>
              <label v-for="campo in tipoSeleccionado.campos" :key="campo.clave" class="campo-producto">{{ campo.etiqueta }}{{ campo.obligatorio ? ' *' : '' }}<input v-model="formulario.atributosEspecificos[campo.clave]" maxlength="500" :placeholder="`Ingresa ${campo.etiqueta.toLocaleLowerCase('es-CL')}`" :disabled="guardando || bloqueadoPorReintento" /></label>
            </div>
            <div v-else class="nota-tipo"><span>✿</span><p>Este tipo de producto no requiere detalles adicionales.</p></div>
          </section>

          <section v-else-if="paso === 4" class="contenido-paso precio-paso">
            <div v-if="tipoSeleccionado?.opcionesVariante.length && formulario.modalidad !== 'pieza_unica'" class="selector-variantes">
              <p class="pregunta-seccion">¿Este producto tiene variantes?</p>
              <div class="opciones-si-no">
                <button type="button" :class="{ seleccionada: formulario.tieneVariantes }" :disabled="guardando || esEdicion || bloqueadoPorReintento" @click="formulario.tieneVariantes = true; formulario.variantes.length || (formulario.variantes = [inicializarVariante(tipoSeleccionado?.opcionesVariante)])">Sí, por ejemplo talla o color</button>
                <button type="button" :class="{ seleccionada: !formulario.tieneVariantes }" :disabled="guardando || esEdicion || bloqueadoPorReintento" @click="formulario.tieneVariantes = false; formulario.variantes = []">No tiene variantes</button>
              </div>
            </div>
            <label v-if="!formulario.tieneVariantes" class="campo-producto">Precio (CLP) *<input v-model="formulario.precioBase" type="number" min="0" step="1" placeholder="Ej.: 6.990" :disabled="guardando || bloqueadoPorReintento" /></label>
            <div v-else class="seccion-variantes">
              <div class="titulo-variantes"><div><h4>Variantes</h4><p>Cada variante conserva su precio y stock.</p></div><button class="boton-secundario" type="button" :disabled="guardando || formulario.variantes.length >= 20 || bloqueadoPorReintento" @click="agregarVariante">＋ Agregar variante</button></div>
              <article v-for="(variante, indice) in formulario.variantes" :key="variante.id" class="fila-variante">
                <strong>Variante {{ indice + 1 }}</strong>
                <div class="campos-producto">
                  <label v-for="opcion in tipoSeleccionado?.opcionesVariante ?? []" :key="opcion" class="campo-producto">{{ opcion }} *<input v-model="variante.opciones[opcion]" maxlength="100" :disabled="guardando || variante.existente || bloqueadoPorReintento" /></label>
                  <label class="campo-producto">Precio (CLP) *<input v-model="variante.precio" type="number" min="0" step="1" placeholder="Ej.: 18.000" :disabled="guardando || bloqueadoPorReintento" /></label>
                  <label v-if="formulario.manejaStock" class="campo-producto">{{ variante.existente ? 'Stock actual' : 'Stock inicial' }}<input v-model="variante.stock" type="number" min="0" step="1" placeholder="Ej.: 3" :disabled="guardando || variante.existente || bloqueadoPorReintento" /></label>
                </div>
                <div class="acciones-variante"><label class="check-variante"><input v-model="variante.activa" type="checkbox" :disabled="guardando || bloqueadoPorReintento" /> Disponible para venta</label><button v-if="!variante.existente" class="enlace-quitar" type="button" :disabled="guardando || bloqueadoPorReintento" @click="quitarVariante(variante.id)">Quitar variante</button></div>
              </article>
              <p v-if="esEdicion" class="ayuda-producto">Las combinaciones existentes se conservan; el stock de esas variantes se administra desde Ventas y Productos.</p>
            </div>
            <label v-if="formulario.tieneVariantes && !tipoSeleccionado?.opcionesVariante.length" class="campo-producto">Precio (CLP) *<input v-model="formulario.precioBase" type="number" min="0" step="1" :disabled="guardando || bloqueadoPorReintento" /></label>
          </section>

          <section v-else class="contenido-paso disponibilidad-paso">
            <div class="opciones-disponibilidad">
              <button v-for="opcion in [{ id: 'regular', nombre: 'Regular', detalle: 'Disponible en forma habitual.', icono: '▣' }, { id: 'a_pedido', nombre: 'A pedido', detalle: 'Se prepara especialmente tras la compra.', icono: '◷' }, { id: 'pieza_unica', nombre: 'Pieza única', detalle: 'Una creación única que no se repite.', icono: '✦' }]" :key="opcion.id" type="button" class="opcion-disponibilidad" :class="{ seleccionada: formulario.modalidad === opcion.id }" :disabled="guardando || esEdicion || (opcion.id === 'pieza_unica' && formulario.tieneVariantes) || bloqueadoPorReintento" @click="formulario.modalidad = opcion.id as ProductoApi['modalidad']; modalidadCambiada()"><span class="icono-disponibilidad">{{ opcion.icono }}</span><span><strong>{{ opcion.nombre }}</strong><small>{{ opcion.detalle }}</small></span><span class="radio-disponibilidad">{{ formulario.modalidad === opcion.id ? '●' : '○' }}</span></button>
            </div>
            <div class="seccion-producto stock-paso">
              <h4>¿Manejas stock?</h4>
              <p class="ayuda-producto">Indica si llevas el control de las unidades disponibles.</p>
              <div class="opciones-si-no"><button type="button" :class="{ seleccionada: formulario.manejaStock }" :disabled="guardando || esEdicion || formulario.modalidad === 'pieza_unica' || bloqueadoPorReintento" @click="formulario.manejaStock = true">Sí, descontar unidades</button><button type="button" :class="{ seleccionada: !formulario.manejaStock }" :disabled="guardando || esEdicion || formulario.modalidad === 'pieza_unica' || bloqueadoPorReintento" @click="formulario.manejaStock = false">No controlar stock</button></div>
              <label v-if="formulario.manejaStock && !formulario.tieneVariantes" class="campo-producto">{{ esEdicion ? 'Stock actual' : 'Stock inicial' }}<input v-model="formulario.stockInicial" type="number" min="0" step="1" :disabled="guardando || esEdicion || bloqueadoPorReintento" /></label>
              <div v-if="formulario.manejaStock && formulario.tieneVariantes" class="ayuda-stock-variantes">El stock inicial de cada variante se completa en el paso anterior.</div>
              <template v-if="formulario.manejaStock && formulario.modalidad !== 'pieza_unica'">
                <h4>Cuando se agote</h4>
                <div class="opciones-agotado"><label><input v-model="formulario.alAgotarse" type="radio" value="mostrar_agotado" :disabled="guardando || esEdicion || bloqueadoPorReintento" /><span><strong>Mostrar agotado</strong><small>El producto sigue visible para tus clientes.</small></span></label><label><input v-model="formulario.alAgotarse" type="radio" value="ocultar" :disabled="guardando || esEdicion || bloqueadoPorReintento" /><span><strong>Ocultar producto</strong><small>Volverá al catálogo cuando tenga stock.</small></span></label><label><input v-model="formulario.alAgotarse" type="radio" value="pasar_a_pedido" :disabled="guardando || esEdicion || bloqueadoPorReintento" /><span><strong>Pasar a pedido</strong><small>Permite seguir recibiendo encargos.</small></span></label></div>
              </template>
              <p v-if="esEdicion" class="ayuda-producto">La modalidad y el control de stock se conservan al editar. Las ventas y los ajustes de stock se administran desde sus secciones.</p>
            </div>
            <section class="resumen-revision">
              <div class="resumen-imagen"><img v-if="fotoPreview" :src="fotoPreview" alt="Vista previa" /><ImagenPublica v-else-if="productoOriginal?.imagenPublica" :ruta="productoOriginal.imagenPublica" :alt="formulario.nombre || 'Producto'" ajuste="contain" :zoom="false" /><span v-else>✿</span></div>
              <div class="resumen-datos"><span>{{ tipoSeleccionado?.nombre || 'PRODUCTO' }} · {{ categoriasDisponibles.find((categoria) => categoria.id === formulario.categoriaId)?.nombre || 'Categoría' }}</span><strong>{{ formulario.nombre || 'Nombre del producto' }}</strong><p>{{ formulario.descripcion || 'Agrega una descripción para tus clientes.' }}</p><b>{{ precioResumen }}</b></div>
              <button type="button" class="editar-resumen" @click="paso = 2">Revisar datos</button>
            </section>
            <div class="revision-final" aria-label="Resumen para revisar antes de guardar">
              <article class="fila-revision"><div class="icono-revision">▤</div><div><strong>Información general</strong><p>{{ formulario.nombre || 'Nombre pendiente' }} · {{ categoriasDisponibles.find((categoria) => categoria.id === formulario.categoriaId)?.nombre || 'Categoría pendiente' }}</p></div><button type="button" @click="paso = 2">Editar →</button></article>
              <article class="fila-revision"><div class="icono-revision">▧</div><div><strong>Fotos del producto</strong><p>{{ fotoSeleccionada?.name || (imagenesActuales ? `${imagenesActuales} foto(s) guardada(s)` : 'Agrega una foto antes de publicar') }}</p></div><button type="button" @click="paso = 1">Editar →</button></article>
              <article class="fila-revision"><div class="icono-revision">◆</div><div><strong>Precio y variantes</strong><p>{{ precioResumen }}<template v-if="formulario.tieneVariantes"> · {{ formulario.variantes.map((variante) => `${Object.values(variante.opciones).filter(Boolean).join(' / ') || 'Opción pendiente'}: $${Number(variante.precio || 0).toLocaleString('es-CL')}`).join(' · ') }}</template></p></div><button type="button" @click="paso = 4">Editar →</button></article>
              <article class="fila-revision"><div class="icono-revision">▣</div><div><strong>Stock y disponibilidad</strong><p>{{ formulario.modalidad.replace('_', ' ') }} · {{ formulario.manejaStock ? 'Control de stock activo' : 'Sin control de stock' }}<template v-if="formulario.manejaStock && !formulario.tieneVariantes"> · {{ formulario.stockInicial || 'Stock pendiente' }} unidad(es)</template></p></div><button type="button" @click="paso = 5">Revisar ↓</button></article>
            </div>
            <p v-if="!tiendaPublicada" class="ayuda-producto">Tu tienda aún no está publicada. Puedes guardar el producto como borrador; para mostrarlo, publica primero la tienda.</p>
          </section>

          <p v-if="error" class="mensaje mensaje-error" role="alert">{{ error }}</p>
          <p v-if="aviso" class="mensaje mensaje-aviso" role="status">{{ aviso }}</p>
          <p v-if="bloqueadoPorReintento && !guardando" class="mensaje mensaje-aviso" role="status">Los datos quedaron bloqueados para que el reintento use la misma operación. Reintenta sin modificarlos.</p>

          <footer class="acciones-editor">
            <button v-if="paso === 1" class="boton-texto" type="button" :disabled="guardando" @click="emit('cancelar')">Volver</button>
            <button v-else class="boton-secundario" type="button" :disabled="guardando" @click="retrocederPaso">Atrás</button>
            <button v-if="!productoOriginal || productoOriginal.estado !== 'publicado'" class="boton-secundario guardar-borrador" type="button" :disabled="guardando || bloqueadoPorReintento" @click="guardarAvance">{{ guardando ? 'Guardando…' : 'Guardar borrador' }}</button>
            <button v-if="paso < 5" class="boton-principal" type="button" :disabled="guardando || bloqueadoPorReintento" @click="avanzarPaso">Continuar <span>→</span></button>
            <button v-else class="boton-principal" type="submit" :disabled="guardando || bloqueadoPorReintento || !escrituraDisponible || (productoOriginal?.estado !== 'publicado' && (!tiendaPublicada || !puedePublicar))"><span v-if="guardando" class="rueda rueda-clara" />{{ guardando ? 'Guardando…' : botonPublicacion }}</button>
          </footer>
          <button v-if="!esEdicion" class="descartar-borrador" type="button" :disabled="guardando" @click="descartarAvance">♲ Descartar borrador local</button>
        </main>

        <aside class="vista-previa-panel">
          <span class="sobre-titulo">VISTA PREVIA</span>
          <div class="tarjeta-previa">
            <div class="imagen-previa-producto"><img v-if="fotoPreview" :src="fotoPreview" :alt="formulario.nombre || 'Producto'" /><ImagenPublica v-else-if="productoOriginal?.imagenPublica" :ruta="productoOriginal.imagenPublica" :alt="formulario.nombre || 'Producto'" ajuste="contain" :zoom="false" /><span v-else>✿</span></div>
            <div class="cuerpo-previa"><small>{{ props.cuenta.tienda.nombre }}</small><h4>{{ formulario.nombre || 'Nombre del producto' }}</h4><p>{{ formulario.descripcion || 'Aquí aparecerá una descripción breve para tus clientes.' }}</p><span class="categoria-previa">{{ categoriasDisponibles.find((categoria) => categoria.id === formulario.categoriaId)?.nombre || 'Categoría' }}</span><strong>{{ precioResumen }}</strong></div>
          </div>
          <p class="texto-previa">Así se verá la ficha en tu catálogo público.</p>
          <div v-if="esEdicion && productoOriginal" class="estado-publicacion"><span :class="productoOriginal.estado">●</span>{{ productoOriginal.estado === 'publicado' ? 'Publicado' : productoOriginal.estado === 'borrador' ? 'Borrador' : 'Retirado' }}</div>
        </aside>
      </div>
    </form>
  </section>
</template>

<style scoped>
.editor-producto{display:grid;gap:22px;padding-bottom:30px;color:var(--texto)}.encabezado-editor-producto{display:flex;align-items:flex-end;justify-content:space-between;gap:24px}.sobre-titulo{display:block;color:var(--vino);font-size:11px;font-weight:800;letter-spacing:.16em}.encabezado-editor-producto h2{margin:8px 0 6px;color:var(--amancay-bordo-oscuro);font:400 clamp(38px,5vw,60px)/1.02 'DM Serif Display',Georgia,serif}.encabezado-editor-producto p,.ayuda-producto,.texto-previa{margin:0;color:var(--suave);font-size:14px;line-height:1.65}.fecha-editor{padding-bottom:6px;color:var(--vino);font-size:11px;font-weight:800;text-transform:uppercase}.asistente-producto{display:grid;gap:18px}.asistente-columnas{display:grid;grid-template-columns:minmax(0,1fr) 290px;align-items:start;gap:22px}.panel-paso,.vista-previa-panel{min-width:0;border:1px solid var(--borde);border-radius:25px;background:var(--amancay-superficie);box-shadow:0 12px 34px rgba(63,34,44,.045)}.panel-paso{position:relative;overflow:hidden;padding:clamp(20px,3vw,34px)}.panel-paso::before{position:absolute;top:-185px;right:-145px;width:430px;height:430px;border-radius:50%;background:radial-gradient(circle at 52% 55%,rgba(245,184,102,.22),rgba(239,141,111,.12) 38%,transparent 69%);content:'';pointer-events:none}.cabecera-paso,.progreso-pasos,.contenido-paso,.mensaje,.acciones-editor,.descartar-borrador{position:relative}.cabecera-paso .rotulo-paso{display:flex;align-items:center;justify-content:space-between;gap:16px}.rotulo-paso span{color:#e76b62;font-size:11px;font-weight:800;letter-spacing:.15em}.rotulo-paso strong{color:var(--vino);font-size:12px}.cabecera-paso h3{margin:12px 0 5px;color:var(--amancay-bordo-oscuro);font:400 clamp(34px,4vw,48px)/1.06 'DM Serif Display',Georgia,serif}.cabecera-paso>p{margin:0;color:var(--suave);font-size:15px;line-height:1.55}.progreso-pasos{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));margin:26px 0 28px}.progreso-pasos::before{position:absolute;top:16px;right:8%;left:8%;height:3px;background:#ead9cd;content:''}.progreso-pasos button{z-index:1;display:grid;justify-items:center;gap:8px;border:0;background:transparent;color:var(--suave);cursor:pointer}.progreso-pasos button span{display:grid;place-items:center;width:34px;height:34px;border:1px solid #e8d5c9;border-radius:50%;background:#fff8f3;font-size:12px;font-weight:800}.progreso-pasos button.actual span,.progreso-pasos button.completado span{border-color:var(--vino);background:var(--vino);color:#fff}.progreso-pasos button small{font-size:10px;font-weight:700}.progreso-pasos button:disabled{cursor:default}.contenido-paso{display:grid;align-content:start;gap:20px;min-height:320px}.contenido-foto{justify-items:center;gap:16px}.foto-producto-grande{display:grid;place-items:center;width:min(100%,500px);height:290px;overflow:hidden;border:1px solid #ead9cd;border-radius:24px;background:linear-gradient(145deg,#fff9ef,#f6e6e5)}.foto-producto-grande>img{width:100%;height:100%;object-fit:contain}.foto-producto-grande :deep(img){max-width:100%;max-height:100%;object-fit:contain}.foto-vacia{display:grid;justify-items:center;gap:9px;padding:24px;text-align:center;color:var(--suave)}.foto-vacia span,.nota-tipo>span,.resumen-imagen>span,.imagen-previa-producto>span{color:var(--vino);font:58px 'DM Serif Display',Georgia,serif}.foto-vacia strong{color:var(--vino);font-size:15px}.foto-vacia small{font-size:12px}.acciones-foto{display:flex;align-items:center;justify-content:center;flex-wrap:wrap;gap:12px}.foto-elegida{padding:9px 15px;border-radius:99px;background:#e7f2e8;color:#34734b;font-size:12px;font-weight:800}.texto-suave{color:var(--suave);font-size:12px}.contenido-foto>.ayuda-producto{max-width:520px;text-align:center}.campos-producto{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px 20px}.campo-producto{display:grid;align-content:start;gap:8px;min-width:0;color:var(--texto);font-size:13px;font-weight:800}.campo-producto input,.campo-producto textarea,.campo-producto select{width:100%;min-width:0;min-height:52px;padding:13px 15px;border:1px solid #e7d5c6;border-radius:15px;background:#fffdf9;color:var(--texto);font:500 14px 'Plus Jakarta Sans',sans-serif;outline:none}.campo-producto textarea{min-height:145px;resize:vertical;line-height:1.6}.campo-producto input:focus,.campo-producto textarea:focus,.campo-producto select:focus{border-color:var(--vino);box-shadow:0 0 0 3px rgba(116,36,78,.12)}.campo-producto input:disabled,.campo-producto textarea:disabled,.campo-producto select:disabled{background:#f8f2ed;color:#786b6c}.campo-completo{grid-column:1/-1}.contador-caracteres{justify-self:end;color:var(--suave);font-size:11px;font-weight:500}.tipo-paso>.campo-producto{max-width:520px}.campos-detalle{padding-top:18px;border-top:1px solid var(--borde)}.campos-detalle h4,.stock-paso h4{margin:0;color:var(--vino);font:400 24px 'DM Serif Display',Georgia,serif}.nota-tipo{display:flex;align-items:center;gap:16px;padding:20px;border:1px dashed var(--borde);border-radius:18px;background:#fffaf4}.nota-tipo>span{font-size:34px}.nota-tipo p{margin:0;color:var(--suave);font-size:13px}.pregunta-seccion{margin:0;color:var(--texto);font-size:15px;font-weight:800}.opciones-si-no{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.opciones-si-no button{min-height:50px;padding:10px 14px;border:1px solid var(--borde);border-radius:14px;background:#fff;color:var(--vino);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.opciones-si-no button.seleccionada{border:2px solid var(--vino);background:#fbf0f1}.opciones-si-no button:disabled{opacity:.65;cursor:default}.seccion-variantes{display:grid;gap:14px}.titulo-variantes{display:flex;align-items:center;justify-content:space-between;gap:16px}.titulo-variantes h4{margin:0;color:var(--vino);font:400 26px 'DM Serif Display',Georgia,serif}.titulo-variantes p{margin:3px 0 0;color:var(--suave);font-size:12px}.fila-variante{display:grid;gap:14px;padding:18px;border:1px solid var(--borde);border-radius:18px;background:#fffdf9}.fila-variante>strong{color:var(--vino);font-size:13px}.acciones-variante{display:flex;align-items:center;justify-content:space-between;gap:14px}.check-variante{display:flex;align-items:center;gap:8px;color:var(--texto);font-size:12px}.check-variante input,.opciones-agotado input{width:17px;height:17px;accent-color:var(--vino)}.enlace-quitar{border:0;background:transparent;color:var(--vino);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.opciones-disponibilidad{display:grid;gap:11px}.opcion-disponibilidad{display:grid;grid-template-columns:48px minmax(0,1fr) auto;align-items:center;gap:14px;min-height:80px;padding:14px 17px;border:1px solid var(--borde);border-radius:18px;background:#fffdf9;text-align:left;cursor:pointer}.opcion-disponibilidad.seleccionada{border:2px solid var(--vino);background:#fff5f0}.opcion-disponibilidad:disabled{cursor:default;opacity:.7}.icono-disponibilidad{display:grid;place-items:center;width:46px;height:46px;border-radius:50%;background:#fde9df;color:var(--vino);font-size:22px}.opcion-disponibilidad strong,.opciones-agotado strong{display:block;color:var(--vino);font-size:14px}.opcion-disponibilidad small,.opciones-agotado small{display:block;margin-top:3px;color:var(--suave);font-size:12px;line-height:1.45}.radio-disponibilidad{color:var(--vino);font-size:22px}.stock-paso{gap:13px}.stock-paso>.campo-producto{max-width:360px;margin-top:5px}.ayuda-stock-variantes{padding:12px;border-radius:12px;background:#fbf0f1;color:var(--suave);font-size:12px}.opciones-agotado{display:grid;gap:8px}.opciones-agotado label{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border:1px solid var(--borde);border-radius:14px;background:#fffdf9}.opciones-agotado input{flex:0 0 auto;margin-top:2px}.resumen-revision{display:grid;grid-template-columns:82px minmax(0,1fr) auto;align-items:center;gap:15px;padding:17px;border:1px solid var(--borde);border-radius:18px;background:#fff}.resumen-imagen{display:grid;place-items:center;width:82px;height:82px;overflow:hidden;border-radius:15px;background:#f7e8dd}.resumen-imagen img{width:100%;height:100%;object-fit:contain}.resumen-imagen :deep(img){max-width:100%;max-height:100%;object-fit:contain}.resumen-imagen>span{font-size:35px}.resumen-datos{display:grid;gap:4px;min-width:0}.resumen-datos>span{color:var(--vino);font-size:9px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.resumen-datos>strong{overflow:hidden;color:var(--texto);font:400 22px 'DM Serif Display',Georgia,serif;text-overflow:ellipsis;white-space:nowrap}.resumen-datos p{display:-webkit-box;overflow:hidden;margin:0;color:var(--suave);font-size:11px;line-height:1.4;-webkit-box-orient:vertical;-webkit-line-clamp:2}.resumen-datos>b{color:var(--vino);font-size:13px}.editar-resumen{border:0;background:transparent;color:var(--vino);font:700 11px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.aviso-solo-lectura{display:flex;align-items:flex-start;gap:13px;padding:15px 17px;border:1px solid #edceaa;border-radius:16px;background:#fff4e3;color:#66482e}.aviso-solo-lectura strong{font-size:13px}.aviso-solo-lectura p{margin:4px 0 0;font-size:12px;line-height:1.5}.aviso-icono{display:grid;place-items:center;flex:0 0 24px;height:24px;border-radius:50%;background:#f5dcae;font-weight:800}.vista-previa-panel{position:sticky;top:20px;display:grid;gap:14px;padding:20px}.tarjeta-previa{overflow:hidden;border:1px solid var(--borde);border-radius:18px;background:#fff}.imagen-previa-producto{display:grid;place-items:center;height:210px;overflow:hidden;background:linear-gradient(140deg,#fbf0e1,#f5e0df)}.imagen-previa-producto img{width:100%;height:100%;object-fit:contain}.imagen-previa-producto :deep(img){max-width:100%;max-height:100%;object-fit:contain}.imagen-previa-producto>span{font-size:54px}.cuerpo-previa{display:grid;gap:7px;padding:15px}.cuerpo-previa>small{color:var(--suave);font-size:10px}.cuerpo-previa h4{overflow:hidden;margin:0;color:var(--vino);font:400 23px/1.15 'DM Serif Display',Georgia,serif;text-overflow:ellipsis}.cuerpo-previa p{display:-webkit-box;overflow:hidden;min-height:42px;margin:0;color:var(--suave);font-size:11px;line-height:1.5;-webkit-box-orient:vertical;-webkit-line-clamp:3}.categoria-previa{justify-self:start;padding:6px 9px;border-radius:999px;background:#faf0df;color:#6f5860;font-size:10px}.cuerpo-previa>strong{color:var(--vino);font:400 23px 'DM Serif Display',Georgia,serif}.texto-previa{font-size:11px}.estado-publicacion{display:flex;align-items:center;gap:7px;padding-top:10px;border-top:1px solid var(--borde);color:var(--suave);font-size:11px}.estado-publicacion .publicado{color:#3c7a48}.estado-publicacion .borrador{color:#e9a62f}.estado-publicacion .archivado{color:#987e83}.acciones-editor{display:flex;align-items:center;justify-content:flex-end;flex-wrap:wrap;gap:10px;padding-top:22px;border-top:1px solid var(--borde)}.boton-secundario,.boton-principal{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:48px;padding:0 19px;border-radius:15px;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.boton-secundario{border:1px solid var(--borde);background:#fff;color:var(--vino)}.boton-principal{border:1px solid var(--vino);background:var(--vino);color:#fff}.boton-principal span:last-child{font-size:17px}.boton-secundario:disabled,.boton-principal:disabled{opacity:.45;cursor:not-allowed}.boton-texto,.descartar-borrador{border:0;background:transparent;color:var(--vino);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.guardar-borrador{margin-right:auto}.descartar-borrador{justify-self:center;padding:6px}.mensaje{margin:0;padding:13px 15px;border-radius:14px;font-size:12px;line-height:1.55}.mensaje-error{background:#fff0ee;color:#852e3f}.mensaje-aviso{background:#edf7ed;color:#286344}.estado-editor{display:flex;align-items:center;gap:10px;padding:20px;border:1px solid var(--borde);border-radius:16px;background:var(--amancay-superficie);color:var(--suave);font-size:13px}.rueda{width:18px;height:18px;border:2px solid var(--borde);border-top-color:var(--vino);border-radius:50%;animation:girar .8s linear infinite}.rueda-clara{border-color:#ffffff70;border-top-color:white}@keyframes girar{to{transform:rotate(360deg)}}.entrada-oculta{display:none}
.revision-final{display:grid;gap:10px}.fila-revision{display:grid;grid-template-columns:42px minmax(0,1fr) auto;align-items:center;gap:12px;padding:13px 15px;border:1px solid var(--borde);border-radius:16px;background:#fff}.icono-revision{display:grid;place-items:center;width:40px;height:40px;border-radius:50%;background:#fde8df;color:var(--vino);font-size:18px}.fila-revision strong{display:block;color:var(--vino);font-size:13px}.fila-revision p{display:-webkit-box;overflow:hidden;margin:3px 0 0;color:var(--suave);font-size:11px;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}.fila-revision button{border:0;background:transparent;color:var(--vino);font:700 11px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
@media(max-width:980px){.asistente-columnas{grid-template-columns:minmax(0,1fr) 250px;gap:14px}.vista-previa-panel{padding:15px}.imagen-previa-producto{height:175px}}
@media(max-width:760px){.editor-producto{gap:16px;padding-bottom:95px}.encabezado-editor-producto{align-items:flex-start}.encabezado-editor-producto h2{font-size:38px}.fecha-editor{display:none}.asistente-columnas{grid-template-columns:minmax(0,1fr)}.vista-previa-panel{position:static;grid-row:2}.tarjeta-previa{display:grid;grid-template-columns:110px minmax(0,1fr)}.imagen-previa-producto{height:100%;min-height:145px}.cuerpo-previa{padding:12px}.cuerpo-previa h4{font-size:20px}.panel-paso{padding:20px 16px;border-radius:21px}.progreso-pasos{margin:21px 0 23px}.progreso-pasos button{gap:6px}.progreso-pasos button span{width:30px;height:30px}.progreso-pasos::before{top:14px}.progreso-pasos button small{font-size:9px}.cabecera-paso h3{font-size:36px}.cabecera-paso>p{font-size:14px}.contenido-paso{min-height:280px}.foto-producto-grande{height:min(66vw,310px)}.campos-producto{grid-template-columns:minmax(0,1fr);gap:14px}.campo-completo{grid-column:auto}.opciones-si-no{grid-template-columns:1fr}.acciones-editor{position:fixed;z-index:5;right:0;bottom:0;left:0;display:grid;grid-template-columns:auto auto minmax(140px,1fr);gap:8px;padding:12px max(14px,env(safe-area-inset-left)) calc(12px + env(safe-area-inset-bottom));border-top:1px solid var(--borde);background:rgba(255,250,243,.97);box-shadow:0 -8px 24px #48212c12;backdrop-filter:blur(12px)}.acciones-editor .boton-secundario,.acciones-editor .boton-principal{min-height:46px;padding:0 12px;font-size:11px}.guardar-borrador{margin-right:0}.acciones-editor .boton-texto{padding:0 6px}.descartar-borrador{margin-top:8px}.resumen-revision{grid-template-columns:64px minmax(0,1fr);gap:10px;padding:12px}.resumen-imagen{width:64px;height:64px}.editar-resumen{grid-column:2;justify-self:start;padding:4px 0}.aviso-solo-lectura{padding:12px}}
@media(max-width:390px){.acciones-editor{grid-template-columns:auto minmax(0,1fr)}.guardar-borrador{display:none}.progreso-pasos button small{font-size:8px}.progreso-pasos button span{width:27px;height:27px}.progreso-pasos::before{top:12px}.cabecera-paso h3{font-size:32px}}
</style>
