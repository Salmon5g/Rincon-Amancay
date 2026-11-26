<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import '~/assets/amancay-tokens.css'
import type { ProductoPublico, TiendaPublica } from '~/types/comprador-publico'
import type { CatalogosPublicos } from '~/repositories/catalogo-publico'

type ProductoCatalogo = { producto: ProductoPublico; tienda: TiendaPublica }

const TIPOS_PRODUCTO = [
  { id: 'alimento_reposteria', nombre: 'Alimento/Repostería' },
  { id: 'artesania_manualidad', nombre: 'Artesanía/Manualidad' },
  { id: 'plantas', nombre: 'Plantas' },
  { id: 'producto_agricola', nombre: 'Producto agrícola' },
  { id: 'vestuario', nombre: 'Vestuario' },
  { id: 'accesorio', nombre: 'Accesorio' },
  { id: 'cosmetica_natural', nombre: 'Cosmética natural' },
]

useHead({
  title: 'Productos locales — Rincón Amancay',
  link: [
    { rel: 'icon', href: '/favicon.png' },
    { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap' },
  ],
})

const { $catalogoPublico } = useNuxtApp()
const productos = ref<ProductoCatalogo[]>([])
const tiendas = ref<TiendaPublica[]>([])
const catalogos = ref<CatalogosPublicos>({ sectores: [], tiposEmprendimiento: [], categorias: [] })
const tiposProductoFiltro = TIPOS_PRODUCTO
const busqueda = ref('')
const tipoProductoSeleccionado = ref('')
const tiendaSeleccionada = ref('')
const disponibilidadSeleccionada = ref('')
const cargando = ref(false)
const cargandoMas = ref(false)
const error = ref(false)
const tiendasConError = ref(0)
const revisionPaginas = ref(0)
const cursoresProductos = new Map<string, string | null>()
const productosPorTienda = new Map<string, ProductoCatalogo[]>()
const cargasEnCurso = new Map<string, Promise<void>>()
const erroresPorTienda = new Set<string>()
let indiceSiguienteTienda = 0
let revisionCatalogo = 0

const filtrosActivos = computed(() => Boolean(busqueda.value.trim() || tipoProductoSeleccionado.value || tiendaSeleccionada.value || disponibilidadSeleccionada.value))
const hayMasProductos = computed(() => {
  revisionPaginas.value
  const tiendasARevisar = tiendaSeleccionada.value
    ? tiendas.value.filter((tienda) => tienda.id === tiendaSeleccionada.value)
    : tiendas.value
  return tiendasARevisar.some((tienda) => !erroresPorTienda.has(tienda.id) && (!cursoresProductos.has(tienda.id) || cursoresProductos.get(tienda.id) !== null))
})

function normalizar(valor: string) {
  return valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-CL').trim()
}

const productosFiltrados = computed(() => {
  const termino = normalizar(busqueda.value)
  return productos.value.filter(({ producto, tienda }) => {
    if (tipoProductoSeleccionado.value && producto.tipoProductoId !== tipoProductoSeleccionado.value) return false
    if (tiendaSeleccionada.value && tienda.id !== tiendaSeleccionada.value) return false
    if (disponibilidadSeleccionada.value && producto.estadoDisponibilidad !== disponibilidadSeleccionada.value) return false
    return !termino || normalizar([
      producto.nombre,
      producto.descripcion,
      tienda.nombre,
      producto.unidadVenta,
      ...Object.values(producto.atributosEspecificos),
    ].join(' ')).includes(termino)
  })
})

function nombreCategoria(id: string) {
  return catalogos.value.categorias.find((categoria) => categoria.id === id)?.nombre ?? 'Producto local'
}

function limpiarFiltros() {
  busqueda.value = ''
  tipoProductoSeleccionado.value = ''
  tiendaSeleccionada.value = ''
  disponibilidadSeleccionada.value = ''
}

async function listarTodasLasTiendas(revision: number): Promise<TiendaPublica[]> {
  const resultado: TiendaPublica[] = []
  let cursor: string | null = null
  do {
    if (revision !== revisionCatalogo) break
    const pagina = await $catalogoPublico.listarTiendas(cursor)
    if (revision !== revisionCatalogo) break
    resultado.push(...pagina.elementos)
    cursor = pagina.cursorSiguiente
  } while (cursor)
  return resultado
}

function siguienteGrupo(cantidad: number): TiendaPublica[] {
  const grupo: TiendaPublica[] = []
  for (let revisadas = 0; revisadas < tiendas.value.length && grupo.length < cantidad; revisadas++) {
    const tienda = tiendas.value[indiceSiguienteTienda]
    indiceSiguienteTienda = (indiceSiguienteTienda + 1) % tiendas.value.length
    if (tienda && !erroresPorTienda.has(tienda.id) && (!cursoresProductos.has(tienda.id) || cursoresProductos.get(tienda.id) !== null)) grupo.push(tienda)
  }
  return grupo
}

async function cargarPaginaTienda(tienda: TiendaPublica, revision: number): Promise<void> {
  const curso = cargasEnCurso.get(tienda.id)
  if (curso) return curso
  if (erroresPorTienda.has(tienda.id) || (cursoresProductos.has(tienda.id) && cursoresProductos.get(tienda.id) === null)) return
  const tarea = (async () => {
    try {
      const pagina = await $catalogoPublico.listarProductos(tienda.id, cursoresProductos.get(tienda.id) ?? null)
      if (revision !== revisionCatalogo) return
      const anteriores = productosPorTienda.get(tienda.id) ?? []
      const ids = new Set(anteriores.map(({ producto }) => producto.id))
      productosPorTienda.set(tienda.id, [...anteriores, ...pagina.elementos.filter((producto) => !ids.has(producto.id)).map((producto) => ({ producto, tienda }))])
      cursoresProductos.set(tienda.id, pagina.cursorSiguiente)
      productos.value = tiendas.value.flatMap((item) => productosPorTienda.get(item.id) ?? [])
      revisionPaginas.value++
    } catch {
      if (revision !== revisionCatalogo) return
      erroresPorTienda.add(tienda.id)
      tiendasConError.value = erroresPorTienda.size
      revisionPaginas.value++
    }
  })()
  cargasEnCurso.set(tienda.id, tarea)
  try { await tarea } finally {
    if (cargasEnCurso.get(tienda.id) === tarea) cargasEnCurso.delete(tienda.id)
  }
}

async function cargarGrupo(cantidad: number, revision: number): Promise<void> {
  const grupo = siguienteGrupo(cantidad)
  await Promise.all(grupo.map((tienda) => cargarPaginaTienda(tienda, revision)))
}

async function cargarMas(): Promise<void> {
  if (cargandoMas.value || !hayMasProductos.value) return
  cargandoMas.value = true
  try {
    const revision = revisionCatalogo
    const tienda = tiendas.value.find((item) => item.id === tiendaSeleccionada.value)
    if (tienda) await cargarPaginaTienda(tienda, revision)
    else await cargarGrupo(2, revision)
  }
  finally { cargandoMas.value = false }
}

async function cargar() {
  revisionCatalogo++
  const revision = revisionCatalogo
  cargando.value = true
  error.value = false
  tiendasConError.value = 0
  productos.value = []
  tiendas.value = []
  cursoresProductos.clear()
  productosPorTienda.clear()
  cargasEnCurso.clear()
  erroresPorTienda.clear()
  indiceSiguienteTienda = 0
  revisionPaginas.value++
  try {
    const [catalogosCargados, tiendasCargadas] = await Promise.all([
      $catalogoPublico.listarCatalogosPublicos(),
      listarTodasLasTiendas(revision),
    ])
    if (revision !== revisionCatalogo) return
    catalogos.value = catalogosCargados
    tiendas.value = tiendasCargadas
    if (tiendasCargadas.length) await cargarGrupo(2, revision)
  } catch {
    if (revision === revisionCatalogo) error.value = true
  } finally {
    if (revision === revisionCatalogo) cargando.value = false
  }
}

onUnmounted(() => {
  revisionCatalogo++
})

function disponibilidad(valor: string) {
  if (valor === 'disponible') return 'Disponible'
  if (valor === 'a_pedido') return 'A pedido'
  if (valor === 'agotado') return 'Agotado'
  if (valor === 'no_disponible') return 'No disponible'
  return 'Consultar'
}

function precio(producto: ProductoPublico, tienda: TiendaPublica) {
  if (!tienda.mostrarPrecios || producto.precioBase === null) return 'Consultar precio'
  try {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: producto.moneda || 'CLP', maximumFractionDigits: 0 }).format(producto.precioBase)
  } catch {
    return `${producto.precioBase.toLocaleString('es-CL')} ${producto.moneda || ''}`.trim()
  }
}

onMounted(() => void cargar())
</script>

<template>
  <div class="comprador catalogo-independiente">
    <CompradorLateral activo="productos" />
    <main class="contenido">
      <nav class="migas-catalogo" aria-label="Ruta">
        <NuxtLink to="/comprador">Explorar</NuxtLink><span aria-hidden="true">/</span><span aria-current="page">Productos</span>
      </nav>
      <header class="encabezado-catalogo">
        <div>
          <p class="sobretitulo">HECHO CERCA, ELEGIDO CON CARIÑO</p>
          <h1>Productos</h1>
          <p class="bajada">Explora los productos publicados por las tiendas de Rincón Amancay.</p>
        </div>
        <AccesoComprador />
      </header>
      <AvisosFerias />

      <section class="filtros-catalogo" aria-label="Buscar y filtrar productos">
        <label class="campo-busqueda">
          <span aria-hidden="true">⌕</span>
          <input v-model="busqueda" type="search" placeholder="Buscar producto, descripción o tienda" aria-label="Buscar productos" />
        </label>
        <label class="campo-filtro">
          <span>Tipo de producto</span>
          <select v-model="tipoProductoSeleccionado" aria-label="Filtrar por tipo de producto">
            <option value="">Todos los tipos</option>
            <option v-for="tipo in tiposProductoFiltro" :key="tipo.id" :value="tipo.id">{{ tipo.nombre }}</option>
          </select>
        </label>
        <label class="campo-filtro">
          <span>Tienda</span>
          <select v-model="tiendaSeleccionada" aria-label="Filtrar por tienda">
            <option value="">Todas las tiendas</option>
            <option v-for="tienda in tiendas" :key="tienda.id" :value="tienda.id">{{ tienda.nombre }}</option>
          </select>
        </label>
        <label class="campo-filtro">
          <span>Disponibilidad</span>
          <select v-model="disponibilidadSeleccionada" aria-label="Filtrar por disponibilidad">
            <option value="">Cualquier estado</option>
            <option value="disponible">Disponible</option>
            <option value="a_pedido">A pedido</option>
            <option value="agotado">Agotado</option>
          </select>
        </label>
        <button v-if="busqueda || tipoProductoSeleccionado || tiendaSeleccionada || disponibilidadSeleccionada" class="limpiar" type="button" @click="limpiarFiltros">Limpiar filtros</button>
      </section>

      <div v-if="!cargando" class="resumen-catalogo" aria-live="polite">
        <span>{{ productosFiltrados.length }} {{ productosFiltrados.length === 1 ? 'producto mostrado' : 'productos mostrados' }}</span>
        <span v-if="busqueda || tipoProductoSeleccionado || tiendaSeleccionada || disponibilidadSeleccionada">con los filtros actuales</span>
        <span v-if="filtrosActivos && hayMasProductos">El filtro considera lo cargado; puedes ampliar la búsqueda por páginas.</span>
      </div>
      <p v-if="tiendasConError && !cargando" class="aviso-parcial" role="status">No se pudo cargar el catálogo de {{ tiendasConError }} {{ tiendasConError === 1 ? 'tienda' : 'tiendas' }}. Los demás productos sí están disponibles.</p>

      <p v-if="cargando" class="estado-catalogo" role="status">Cargando productos…</p>
      <div v-else-if="error" class="estado-catalogo estado-error" role="alert">
        <p>No pudimos cargar los productos. Revisa tu conexión e inténtalo otra vez.</p>
        <button class="accion-secundaria" type="button" @click="cargar">Reintentar</button>
      </div>
      <div v-else-if="!productosFiltrados.length" class="estado-catalogo estado-vacio">
        <span aria-hidden="true">🌼</span>
        <h2>{{ hayMasProductos ? (filtrosActivos ? 'Sigue buscando en el catálogo' : 'Sigue explorando productos') : 'No encontramos productos' }}</h2>
        <p>{{ hayMasProductos ? (filtrosActivos ? 'No hay coincidencias entre las páginas cargadas. Carga más para ampliar la búsqueda.' : 'Hay más tiendas y productos por cargar.') : productos.length ? 'Prueba con otra búsqueda o limpia los filtros.' : 'Todavía no hay productos publicados.' }}</p>
        <button v-if="busqueda || tipoProductoSeleccionado || tiendaSeleccionada || disponibilidadSeleccionada" class="accion-secundaria" type="button" @click="limpiarFiltros">Limpiar filtros</button>
      </div>
      <div v-else class="rejilla-productos">
        <article v-for="{ producto, tienda } in productosFiltrados" :key="`${tienda.id}-${producto.id}`" class="tarjeta-producto">
          <NuxtLink class="enlace-producto" :to="{ path: '/comprador', query: { tienda: tienda.id, producto: producto.id } }" :aria-label="`Ver ${producto.nombre} en ${tienda.nombre}`">
            <span class="foto-producto">
              <ImagenPublica :ruta="producto.imagenes[0]" :alt="producto.nombre" ajuste="contain" :zoom="false" />
              <span class="badge-disponibilidad" :class="`estado-${producto.estadoDisponibilidad}`">{{ disponibilidad(producto.estadoDisponibilidad) }}</span>
            </span>
            <span class="datos-producto">
              <span class="meta-producto">{{ nombreCategoria(producto.categoriaId) }}</span>
              <strong>{{ producto.nombre }}</strong>
              <span class="descripcion-producto">{{ producto.descripcion || 'Producto de emprendimiento local.' }}</span>
            </span>
          </NuxtLink>
          <div class="pie-producto">
            <NuxtLink class="tienda-producto" :to="{ path: '/comprador', query: { tienda: tienda.id } }">{{ tienda.nombre }}</NuxtLink>
            <strong>{{ precio(producto, tienda) }}</strong>
          </div>
        </article>
      </div>
      <div v-if="hayMasProductos && !cargando && !error" class="mas-productos">
        <button class="accion-secundaria" type="button" :disabled="cargandoMas" @click="cargarMas">{{ cargandoMas ? 'Cargando más productos…' : tiendaSeleccionada ? 'Cargar más productos de esta tienda' : filtrosActivos ? 'Cargar más para ampliar la búsqueda' : 'Cargar más productos' }}</button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.catalogo-independiente { display: grid; min-height: 100vh; grid-template-columns: 260px minmax(0, 1fr); background: var(--amancay-fondo); color: var(--amancay-tinta); font-family: var(--amancay-fuente-cuerpo); }
.contenido { width: min(100%, 1440px); margin: 0 auto; padding: 1.8rem clamp(1rem, 3vw, 2.8rem) 5rem; box-sizing: border-box; }
.migas-catalogo { display: flex; gap: .65rem; align-items: center; margin-bottom: 1.4rem; color: var(--amancay-tinta-suave); font-size: .88rem; }
.migas-catalogo a { color: inherit; text-decoration: none; }
.migas-catalogo a:hover { color: var(--amancay-bordo); }
.encabezado-catalogo { display: flex; align-items: center; justify-content: space-between; gap: 1.5rem; margin-bottom: 1.4rem; }
.sobretitulo { margin: 0 0 .4rem; color: var(--amancay-bordo); font-size: .76rem; font-weight: 800; letter-spacing: .12em; }
h1 { margin: 0; color: var(--amancay-bordo); font-family: 'DM Serif Display', Georgia, serif; font-size: clamp(2.2rem, 4vw, 3.5rem); font-weight: 400; }
.bajada { max-width: 60ch; margin: .45rem 0 0; color: var(--amancay-tinta-suave); line-height: 1.6; }
.filtros-catalogo { display: grid; grid-template-columns: minmax(230px, 1.6fr) repeat(3, minmax(150px, 1fr)) auto; gap: .8rem; align-items: end; margin-top: 1.6rem; padding: 1rem; border: 1px solid var(--amancay-borde); border-radius: 20px; background: var(--amancay-superficie); box-shadow: 0 8px 24px rgba(73, 40, 44, .05); }
.campo-busqueda { display: flex; align-items: center; gap: .65rem; min-height: 48px; padding: 0 .9rem; border: 1px solid var(--amancay-borde); border-radius: 14px; background: var(--amancay-fondo); color: var(--amancay-bordo); font-size: 1.25rem; }
.campo-busqueda input, .campo-filtro select { width: 100%; border: 0; outline: 0; background: transparent; color: var(--amancay-tinta); font: inherit; }
.campo-busqueda input { min-width: 0; font-size: .92rem; }
.campo-filtro { display: grid; gap: .35rem; color: var(--amancay-tinta-suave); font-size: .76rem; font-weight: 700; }
.campo-filtro select { box-sizing: border-box; min-height: 48px; padding: 0 .75rem; border: 1px solid var(--amancay-borde); border-radius: 14px; background: var(--amancay-fondo); font-size: .88rem; }
.limpiar, .accion-secundaria { min-height: 44px; padding: .65rem .9rem; border: 1px solid var(--amancay-borde); border-radius: 13px; background: var(--amancay-superficie); color: var(--amancay-bordo); font: inherit; font-weight: 700; cursor: pointer; }
.resumen-catalogo { display: flex; gap: .35rem; margin: 1.2rem 0 .8rem; color: var(--amancay-tinta-suave); font-size: .88rem; }
.resumen-catalogo span:first-child { color: var(--amancay-bordo); font-weight: 800; }
.mas-productos { display: flex; justify-content: center; margin: 1.4rem 0 0; }
.mas-productos .accion-secundaria { min-width: 220px; }
.mas-productos .accion-secundaria:disabled { cursor: progress; opacity: .7; }
.aviso-parcial { padding: .8rem 1rem; border-radius: 13px; background: #fff1dc; color: #6f421d; }
.estado-catalogo { display: grid; justify-items: center; gap: .65rem; min-height: 220px; align-content: center; padding: 2rem; border: 1px dashed var(--amancay-borde); border-radius: 22px; color: var(--amancay-tinta-suave); text-align: center; }
.estado-catalogo p, .estado-catalogo h2 { margin: 0; }
.estado-vacio > span { font-size: 2rem; }
.estado-vacio h2 { color: var(--amancay-bordo); font-family: 'DM Serif Display', Georgia, serif; font-size: 1.65rem; font-weight: 400; }
.estado-error { border-color: #d88f81; }
.rejilla-productos { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(220px, 100%), 1fr)); gap: 1rem; }
.tarjeta-producto { display: flex; min-width: 0; flex-direction: column; overflow: hidden; border: 1px solid var(--amancay-borde); border-radius: 22px; background: var(--amancay-superficie); box-shadow: 0 8px 24px rgba(73, 40, 44, .055); transition: transform .16s ease, box-shadow .16s ease; }
.tarjeta-producto:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(73, 40, 44, .1); }
.enlace-producto { color: inherit; text-decoration: none; }
.foto-producto { position: relative; display: block; height: 220px; overflow: hidden; background: var(--amancay-superficie-variante); }
.foto-producto :deep(.imagen-publica), .foto-producto :deep(img) { width: 100%; height: 100%; object-fit: contain; }
.badge-disponibilidad { position: absolute; right: .65rem; bottom: .65rem; padding: .35rem .55rem; border: 1px solid rgba(255,255,255,.65); border-radius: 999px; background: var(--amancay-superficie); color: var(--amancay-bordo); font-size: .7rem; font-weight: 800; }
.badge-disponibilidad.estado-agotado, .badge-disponibilidad.estado-no_disponible { color: #745252; }
.badge-disponibilidad.estado-disponible { color: #286344; }
.datos-producto { display: grid; gap: .32rem; padding: .9rem .95rem .35rem; }
.meta-producto { color: var(--amancay-tinta-suave); font-size: .74rem; }
.datos-producto strong { color: var(--amancay-bordo); font-family: 'DM Serif Display', Georgia, serif; font-size: 1.2rem; font-weight: 400; }
.descripcion-producto { display: -webkit-box; min-height: 2.7em; overflow: hidden; color: var(--amancay-tinta-suave); font-size: .85rem; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.pie-producto { display: grid; gap: .35rem; margin-top: auto; padding: .8rem .95rem 1rem; }
.tienda-producto { overflow: hidden; color: var(--amancay-tinta-suave); font-size: .8rem; text-overflow: ellipsis; white-space: nowrap; text-decoration: none; }
.tienda-producto:hover { color: var(--amancay-bordo); text-decoration: underline; }
.pie-producto > strong { color: var(--amancay-bordo); }
@media (max-width: 1100px) { .catalogo-independiente { grid-template-columns: minmax(0, 1fr); } .filtros-catalogo { grid-template-columns: repeat(2, minmax(0, 1fr)); } .campo-busqueda { grid-column: 1 / -1; } }
@media (max-width: 600px) { .encabezado-catalogo { align-items: flex-start; flex-direction: column-reverse; } .filtros-catalogo { grid-template-columns: minmax(0, 1fr); } .campo-busqueda { grid-column: auto; } .limpiar { width: 100%; } .foto-producto { height: 190px; } }
</style>
