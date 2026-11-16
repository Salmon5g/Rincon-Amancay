<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { cargarCuentaEmprendedora, leerTipoProductoEmprendedora, listarTiposProductoEmprendedora, type CuentaEmprendedoraWeb, type ProductoEmprendedoraWeb, type TipoProductoEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { ServiciosCliente, Operacion } from '~/lib/cliente-base'
import type { ElementoEtiqueta } from '~/repositories/catalogo-publico'

type VarianteFormulario = {
  id: string
  opciones: Record<string, string>
  precio: string
  stock: string
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

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb; productoId?: string | null }>()
const emit = defineEmits<{ cancelar: []; guardado: [resultado: { productoId: string; publicado: boolean }] }>()

const { $catalogoPublico } = useNuxtApp()
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
  precioBase: '',
  stockInicial: '1',
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

function cliente() {
  const { $amancay } = useNuxtApp()
  if (!$amancay?.firebase) throw new Error('El acceso no está disponible en este momento.')
  return $amancay as typeof $amancay & {
    llamar: (operacion: Operacion, solicitud: Record<string, unknown>) => Promise<Record<string, unknown>>
    subirImagenProducto: (tiendaId: string, productoId: string, archivo: Blob, archivoId: string) => Promise<string>
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
  } else if (imagenesActuales.value >= 5) {
    error.value = 'El producto ya tiene cinco imágenes; este editor no permite agregar más.'
  } else {
    error.value = ''
    limpiarFotoPreview()
    fotoSeleccionada.value = archivo
    fotoPreview.value = URL.createObjectURL(archivo)
  }
  input.value = ''
}

function entero(valor: string, etiqueta: string): number | null {
  if (!/^\d+$/.test(valor.trim())) {
    error.value = `${etiqueta} debe ser un número entero igual o mayor que cero.`
    return null
  }
  const numero = Number(valor)
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

async function guardar(publicar: boolean) {
  if (guardando.value) return
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
      await servicio.subirImagenProducto(props.cuenta.tienda.id, pendiente.productoId, archivo, pendiente.archivoId)
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
  <section class="editor-producto" aria-labelledby="titulo-editor-producto">
    <header class="encabezado-editor-producto">
      <span class="sobre-titulo">{{ esEdicion ? 'MI CATÁLOGO' : 'NUEVA CREACIÓN' }}</span>
      <h2 id="titulo-editor-producto">{{ esEdicion ? 'Editar producto' : 'Agregar producto' }}</h2>
      <p>Completa los datos del producto. Si tu tienda está publicada, podrás actualizar el catálogo público desde aquí.</p>
    </header>

    <div v-if="cargando" class="estado-editor" role="status"><span class="rueda" />Cargando los tipos y categorías…</div>
    <form v-else class="formulario-producto" @submit.prevent="guardar(true)">
      <div class="campos-producto">
        <label class="campo-producto">Nombre del producto *<input v-model="formulario.nombre" maxlength="120" required autocomplete="off" :disabled="guardando || bloqueadoPorReintento" /></label>
        <label class="campo-producto campo-completo">Descripción *<textarea v-model="formulario.descripcion" maxlength="2000" rows="4" required :disabled="guardando || bloqueadoPorReintento" /></label>
        <label class="campo-producto">Tipo de producto *
          <select v-model="formulario.tipoProductoId" required :disabled="guardando || esEdicion || bloqueadoPorReintento" @change="tipoCambiado">
            <option value="" disabled>Selecciona un tipo</option>
            <option v-for="tipo in tipos" :key="tipo.id" :value="tipo.id">{{ tipo.nombre }}</option>
            <option v-if="esEdicion && definicion && !tipos.some((tipo) => tipo.id === definicion?.id)" :value="definicion.id">{{ definicion.nombre }} · histórico</option>
          </select>
        </label>
        <label class="campo-producto">Categoría *
          <select v-model="formulario.categoriaId" required :disabled="guardando || bloqueadoPorReintento">
            <option value="" disabled>Selecciona una categoría</option>
            <option v-for="categoria in categoriasDisponibles" :key="categoria.id" :value="categoria.id">{{ categoria.nombre }}</option>
          </select>
        </label>
      </div>

      <section v-if="tipoSeleccionado?.campos.length" class="seccion-producto">
        <h3>Detalles del producto</h3>
        <div class="campos-producto">
          <label v-for="campo in tipoSeleccionado.campos" :key="campo.clave" class="campo-producto">
            {{ campo.etiqueta }}{{ campo.obligatorio ? ' *' : '' }}
            <input v-model="formulario.atributosEspecificos[campo.clave]" maxlength="500" :required="campo.obligatorio" :disabled="guardando || bloqueadoPorReintento" />
          </label>
        </div>
      </section>

      <section class="seccion-producto">
        <h3>Modalidad y stock</h3>
        <div class="campos-producto">
          <label class="campo-producto">Modalidad de venta
            <select v-model="formulario.modalidad" :disabled="guardando || esEdicion || bloqueadoPorReintento" @change="modalidadCambiada">
              <option value="regular">Regular</option><option value="a_pedido">A pedido</option><option value="pieza_unica">Pieza única</option>
            </select>
          </label>
          <label class="campo-producto">Control de stock
            <select v-model="formulario.manejaStock" :disabled="guardando || esEdicion || formulario.modalidad === 'pieza_unica' || bloqueadoPorReintento">
              <option :value="true">Sí, descontar unidades</option><option :value="false">No controlar stock</option>
            </select>
          </label>
          <label class="campo-producto">Al agotarse
            <select v-model="formulario.alAgotarse" :disabled="guardando || esEdicion || formulario.modalidad === 'pieza_unica' || bloqueadoPorReintento">
              <option value="mostrar_agotado">Mostrar agotado</option><option value="ocultar">Ocultar del catálogo</option><option value="pasar_a_pedido">Aceptar pedidos</option>
            </select>
          </label>
          <label v-if="tipoSeleccionado?.opcionesVariante.length && formulario.modalidad !== 'pieza_unica'" class="campo-producto">
            ¿Tiene variantes?
            <select v-model="formulario.tieneVariantes" :disabled="guardando || esEdicion || bloqueadoPorReintento" @change="formulario.variantes = formulario.tieneVariantes ? [inicializarVariante(tipoSeleccionado?.opcionesVariante)] : []">
              <option :value="false">No, un precio</option><option :value="true">Sí, por ejemplo talla o color</option>
            </select>
          </label>
          <label v-if="!formulario.tieneVariantes" class="campo-producto">Precio (CLP) *<input v-model="formulario.precioBase" type="number" min="0" step="1" required :disabled="guardando || bloqueadoPorReintento" /></label>
          <label v-if="!formulario.tieneVariantes && formulario.manejaStock" class="campo-producto">{{ esEdicion ? 'Stock actual' : 'Stock inicial' }}{{ esEdicion ? '' : ' *' }}
            <input v-model="formulario.stockInicial" type="number" min="0" step="1" :required="!esEdicion" :disabled="guardando || esEdicion || bloqueadoPorReintento" />
          </label>
        </div>
        <p v-if="esEdicion" class="ayuda-producto">El tipo, la modalidad, el control de stock y la estructura de variantes no se pueden cambiar en una edición. El stock se conserva; las ventas se registran en la sección Ventas.</p>
      </section>

      <section v-if="formulario.tieneVariantes" class="seccion-producto">
        <div class="titulo-variantes"><div><h3>Variantes y precios</h3><p>Agrega una combinación por variante. Cada una conserva su precio y stock.</p></div><button class="boton-secundario" type="button" :disabled="guardando || formulario.variantes.length >= 20" @click="agregarVariante">＋ Variante</button></div>
        <article v-for="(variante, indice) in formulario.variantes" :key="variante.id" class="fila-variante">
          <strong>Variante {{ indice + 1 }}</strong>
          <div class="campos-producto">
            <label v-for="opcion in tipoSeleccionado?.opcionesVariante ?? []" :key="opcion" class="campo-producto">{{ opcion }} *
              <input v-model="variante.opciones[opcion]" maxlength="100" required :disabled="guardando || variante.existente || bloqueadoPorReintento" />
            </label>
            <label class="campo-producto">Precio (CLP) *<input v-model="variante.precio" type="number" min="0" step="1" required :disabled="guardando || bloqueadoPorReintento" /></label>
            <label v-if="formulario.manejaStock" class="campo-producto">{{ variante.existente ? 'Stock actual' : 'Stock inicial' }}
              <input v-model="variante.stock" type="number" min="0" step="1" :disabled="guardando || variante.existente || bloqueadoPorReintento" />
            </label>
          </div>
          <div class="acciones-variante"><label class="check-variante"><input v-model="variante.activa" type="checkbox" :disabled="guardando || bloqueadoPorReintento" /> Disponible para venta</label>
            <button v-if="!variante.existente" class="enlace-quitar" type="button" :disabled="guardando || bloqueadoPorReintento" @click="quitarVariante(variante.id)">Quitar variante</button>
          </div>
        </article>
      </section>

      <section class="seccion-producto">
        <h3>Imagen</h3>
        <div class="selector-imagen">
          <div v-if="fotoPreview" class="preview-imagen"><img :src="fotoPreview" alt="Vista previa de la imagen seleccionada" /></div>
          <div v-else-if="productoOriginal?.imagenPublica" class="preview-imagen"><ImagenPublica :ruta="productoOriginal.imagenPublica" :alt="productoOriginal.nombre" ajuste="contain" :zoom="false" /></div>
          <div v-else class="preview-vacia" aria-hidden="true">✿</div>
          <div><strong>{{ fotoSeleccionada?.name || (imagenesActuales ? `${imagenesActuales} imagen(es) ya guardada(s)` : 'Agrega una imagen para publicar') }}</strong><p>PNG, JPEG o WebP · máximo 5 MiB. Las imágenes se guardan de forma privada y se preparan al publicar.</p>
            <button class="boton-secundario" type="button" :disabled="guardando || bloqueadoPorReintento || imagenesActuales >= 5" @click="entradaFoto?.click()">{{ fotoSeleccionada ? 'Cambiar imagen' : 'Elegir imagen' }}</button>
            <input ref="entradaFoto" class="entrada-oculta" type="file" accept="image/png,image/jpeg,image/webp" @change="seleccionarFoto" />
          </div>
        </div>
        <p v-if="!tiendaPublicada" class="ayuda-producto">Tu tienda aún no está publicada. Puedes guardar el producto como borrador; para mostrarlo, publica primero la tienda.</p>
      </section>

      <p v-if="error" class="mensaje mensaje-error" role="alert">{{ error }}</p>
      <p v-if="aviso" class="mensaje mensaje-aviso" role="status">{{ aviso }}</p>
      <p v-if="bloqueadoPorReintento && !guardando" class="mensaje mensaje-aviso" role="status">Los datos están bloqueados para que el reintento use la misma operación. Vuelve a guardar sin modificar los campos.</p>
      <div class="acciones-editor">
        <button class="boton-secundario" type="button" :disabled="guardando" @click="emit('cancelar')">Cancelar</button>
        <button v-if="!productoOriginal || productoOriginal.estado !== 'publicado'" class="boton-secundario" type="button" :disabled="guardando" @click="guardar(false)">{{ guardando ? 'Guardando…' : 'Guardar borrador' }}</button>
        <button class="boton-principal" type="button" :disabled="guardando || !tiendaPublicada || !puedePublicar" @click="guardar(true)">
          <span v-if="guardando" class="rueda rueda-clara" />{{ guardando ? 'Guardando…' : botonPublicacion }}
        </button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.editor-producto{display:grid;gap:18px;padding-bottom:24px}.encabezado-editor-producto{margin-bottom:2px}.encabezado-editor-producto h2{margin:7px 0 5px;color:var(--amancay-bordo-oscuro);font:400 clamp(32px,4vw,48px)/1.08 'DM Serif Display',Georgia,serif}.encabezado-editor-producto p,.ayuda-producto,.titulo-variantes p{margin:0;color:var(--suave);font-size:13px;line-height:1.6}.formulario-producto{display:grid;gap:17px;padding:22px;border:1px solid var(--borde);border-radius:22px;background:var(--amancay-superficie);box-shadow:0 8px 25px rgba(46,27,32,.04)}.campos-producto{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px 18px}.campo-producto{display:grid;align-content:start;gap:7px;min-width:0;color:var(--texto);font-size:12px;font-weight:700}.campo-producto input,.campo-producto textarea,.campo-producto select{width:100%;min-width:0;min-height:46px;padding:11px 13px;border:1px solid #e8d7c5;border-radius:12px;background:#fffdf9;color:var(--texto);font:500 13px 'Plus Jakarta Sans',sans-serif;outline:none}.campo-producto textarea{min-height:100px;resize:vertical;line-height:1.5}.campo-producto input:focus,.campo-producto textarea:focus,.campo-producto select:focus{border-color:var(--naranja);box-shadow:0 0 0 3px rgba(246,193,119,.22)}.campo-completo{grid-column:1/-1}.seccion-producto{display:grid;gap:13px;padding-top:17px;border-top:1px solid var(--borde)}.seccion-producto h3{margin:0;color:var(--vino);font:400 23px/1.2 'DM Serif Display',Georgia,serif}.titulo-variantes{display:flex;align-items:center;justify-content:space-between;gap:12px}.ayuda-producto{font-size:11px}.fila-variante{display:grid;gap:12px;padding:15px;border:1px solid var(--borde);border-radius:16px;background:#fffdf9}.fila-variante>strong{color:var(--vino);font-size:12px}.acciones-variante{display:flex;align-items:center;justify-content:space-between;gap:12px}.check-variante{display:flex;align-items:center;gap:8px;color:var(--texto);font-size:11px}.check-variante input{accent-color:var(--vino)}.enlace-quitar{border:0;background:transparent;color:var(--vino);font:700 11px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.selector-imagen{display:flex;align-items:center;gap:15px;padding:14px;border:1px dashed var(--borde);border-radius:15px;background:#fffdf9}.selector-imagen strong{color:var(--texto);font-size:12px}.selector-imagen p{max-width:560px;margin:5px 0 11px;color:var(--suave);font-size:11px;line-height:1.55}.preview-imagen,.preview-vacia{display:grid;place-items:center;flex:0 0 86px;width:86px;height:86px;overflow:hidden;border-radius:14px;background:var(--amancay-naranja-fondo);color:var(--vino);font:36px 'DM Serif Display',Georgia,serif}.preview-imagen img{width:100%;height:100%;object-fit:contain}.entrada-oculta{display:none}.boton-secundario,.boton-principal{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:42px;padding:0 15px;border-radius:13px;font:700 11px 'Plus Jakarta Sans',sans-serif;cursor:pointer}.boton-secundario{border:1px solid var(--borde);background:#fff;color:var(--vino)}.boton-principal{border:1px solid var(--vino);background:var(--vino);color:#fff}.boton-secundario:disabled,.boton-principal:disabled{opacity:.5;cursor:not-allowed}.acciones-editor{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:10px;padding-top:4px}.mensaje{margin:0;padding:12px 14px;border-radius:12px;font-size:12px;line-height:1.55}.mensaje-error{background:#fff0ee;color:#852e3f}.mensaje-aviso{background:#edf7ed;color:#286344}.estado-editor{display:flex;align-items:center;gap:10px;padding:20px;border:1px solid var(--borde);border-radius:16px;background:var(--amancay-superficie);color:var(--suave);font-size:13px}.rueda{width:18px;height:18px;border:2px solid var(--borde);border-top-color:var(--vino);border-radius:50%;animation:girar .8s linear infinite}.rueda-clara{border-color:#ffffff70;border-top-color:white}@keyframes girar{to{transform:rotate(360deg)}}
@media(max-width:680px){.formulario-producto{padding:16px}.campos-producto{grid-template-columns:1fr;gap:13px}.campo-completo{grid-column:auto}.selector-imagen{align-items:flex-start}.titulo-variantes{align-items:flex-start}.acciones-editor{justify-content:stretch}.acciones-editor button{flex:1}.encabezado-editor-producto h2{font-size:36px}.seccion-producto h3{font-size:21px}}
</style>
