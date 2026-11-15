<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import '~/assets/amancay-tokens.css'
import type { AsociacionPublica, TiendaPublica } from '~/types/comprador-publico'
import type { CatalogosPublicos } from '~/repositories/catalogo-publico'

useHead({
  title: 'Directorio local — Rincón Amancay',
  link: [
    { rel: 'icon', href: '/favicon.png' },
    { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap' },
  ],
})

const { $catalogoPublico } = useNuxtApp()
const route = useRoute()
const router = useRouter()
const tiendas = ref<TiendaPublica[]>([])
const asociaciones = ref<AsociacionPublica[]>([])
const catalogos = ref<CatalogosPublicos>({ sectores: [], tiposEmprendimiento: [], categorias: [] })
const busqueda = ref('')
const tipoSeleccionado = ref('')
const sectorSeleccionado = ref('')
const asociacionSeleccionada = ref('')
const cargando = ref(false)
const error = ref(false)

const vista = computed(() => route.query.vista === 'asociaciones' ? 'asociaciones' : 'emprendedoras')
const asociacionId = computed(() => {
  const valor = route.query.asociacion
  return typeof valor === 'string' ? valor : Array.isArray(valor) && typeof valor[0] === 'string' ? valor[0] : ''
})
const asociacionActual = computed(() => asociaciones.value.find((asociacion) => asociacion.id === asociacionId.value) ?? null)

function normalizar(valor: string) {
  return valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-CL').trim()
}

function nombreCatalogo(id: string, elementos: { id: string; nombre: string }[], alternativo = '') {
  return elementos.find((elemento) => elemento.id === id)?.nombre ?? alternativo
}

function nombreTipo(tienda: TiendaPublica) {
  return nombreCatalogo(tienda.tipoEmprendimientoId, catalogos.value.tiposEmprendimiento, 'Emprendimiento local')
}

function nombreSector(sectorId: string) {
  return nombreCatalogo(sectorId, catalogos.value.sectores)
}

const asociacionesPorTienda = computed(() => {
  const resultado = new Map<string, AsociacionPublica[]>()
  for (const asociacion of asociaciones.value) {
    for (const integrante of asociacion.integrantes) {
      const lista = resultado.get(integrante.tiendaId) ?? []
      lista.push(asociacion)
      resultado.set(integrante.tiendaId, lista)
    }
  }
  return resultado
})

function asociacionesDeTienda(tiendaId: string) {
  return asociacionesPorTienda.value.get(tiendaId) ?? []
}

const tiendasFiltradas = computed(() => {
  const termino = normalizar(busqueda.value)
  return tiendas.value.filter((tienda) => {
    if (tipoSeleccionado.value && tienda.tipoEmprendimientoId !== tipoSeleccionado.value) return false
    if (sectorSeleccionado.value && tienda.sectorId !== sectorSeleccionado.value) return false
    const asociacionesTienda = asociacionesDeTienda(tienda.id)
    if (asociacionSeleccionada.value && !asociacionesTienda.some((asociacion) => asociacion.id === asociacionSeleccionada.value)) return false
    if (!termino) return true
    const campos = [
      tienda.nombre,
      tienda.descripcion,
      nombreTipo(tienda),
      nombreSector(tienda.sectorId),
      ...asociacionesTienda.map((asociacion) => asociacion.nombre),
    ]
    return normalizar(campos.join(' ')).includes(termino)
  })
})

const asociacionesFiltradas = computed(() => {
  const termino = normalizar(busqueda.value)
  if (!termino) return asociaciones.value
  return asociaciones.value.filter((asociacion) => normalizar([
    asociacion.nombre,
    asociacion.descripcion,
    nombreSector(asociacion.sectorId),
    ...asociacion.integrantes.map((integrante) => integrante.nombre),
  ].join(' ')).includes(termino))
})

function hrefTienda(tiendaId: string) {
  return { path: '/comprador', query: { tienda: tiendaId } }
}

function hrefAsociacion(id: string) {
  return { path: '/comprador/directorio', query: { vista: 'asociaciones', asociacion: id } }
}

async function cambiarVista(nuevaVista: 'emprendedoras' | 'asociaciones') {
  busqueda.value = ''
  tipoSeleccionado.value = ''
  sectorSeleccionado.value = ''
  asociacionSeleccionada.value = ''
  await router.push({ path: '/comprador/directorio', query: nuevaVista === 'asociaciones' ? { vista: nuevaVista } : {} })
}

function limpiarFiltros() {
  busqueda.value = ''
  tipoSeleccionado.value = ''
  sectorSeleccionado.value = ''
  asociacionSeleccionada.value = ''
}

async function cargarDirectorio() {
  cargando.value = true
  error.value = false
  try {
    const [catalogosCargados, asociacionesCargadas] = await Promise.all([
      $catalogoPublico.listarCatalogosPublicos(),
      $catalogoPublico.listarAsociacionesPublicas(),
    ])
    catalogos.value = catalogosCargados
    asociaciones.value = asociacionesCargadas

    const todasLasTiendas: TiendaPublica[] = []
    let cursor: string | null = null
    do {
      const pagina = await $catalogoPublico.listarTiendas(cursor)
      todasLasTiendas.push(...pagina.elementos)
      cursor = pagina.cursorSiguiente
    } while (cursor)
    tiendas.value = todasLasTiendas
  } catch {
    error.value = true
  } finally {
    cargando.value = false
  }
}

onMounted(() => void cargarDirectorio())
</script>

<template>
  <div class="comprador modo-explorar directorio-comprador">
    <CompradorLateral activo="directorio" />

    <main class="contenido directorio-contenido">
      <nav class="directorio-migas" aria-label="Ruta">
        <NuxtLink to="/comprador">Explorar</NuxtLink>
        <span aria-hidden="true">/</span>
        <span class="actual">Directorio</span>
        <template v-if="asociacionActual">
          <span aria-hidden="true">/</span>
          <span class="actual">{{ asociacionActual.nombre }}</span>
        </template>
      </nav>

      <header class="directorio-encabezado">
        <div>
          <p class="directorio-sobretitulo">PERSONAS Y COMUNIDAD LOCAL</p>
          <h1>Directorio local</h1>
          <p class="directorio-bajada">Conoce a las emprendedoras y asociaciones de tu territorio.</p>
        </div>
        <img class="directorio-emblema" src="/logo-amancay.png" alt="" aria-hidden="true" />
      </header>

      <div v-if="!asociacionId" class="directorio-pestanas" role="tablist" aria-label="Secciones del directorio">
        <button
          id="pestana-emprendedoras"
          type="button"
          role="tab"
          :aria-selected="vista === 'emprendedoras'"
          aria-controls="panel-directorio"
          :class="{ seleccionada: vista === 'emprendedoras' }"
          @click="cambiarVista('emprendedoras')"
        >
          Emprendedoras
          <span>{{ tiendas.length }}</span>
        </button>
        <button
          id="pestana-asociaciones"
          type="button"
          role="tab"
          :aria-selected="vista === 'asociaciones'"
          aria-controls="panel-directorio"
          :class="{ seleccionada: vista === 'asociaciones' }"
          @click="cambiarVista('asociaciones')"
        >
          Asociaciones
          <span>{{ asociaciones.length }}</span>
        </button>
      </div>

      <section v-if="asociacionId" class="directorio-detalle-asociacion" aria-labelledby="titulo-asociacion">
        <NuxtLink class="directorio-volver" :to="{ path: '/comprador/directorio', query: { vista: 'asociaciones' } }">
          <span aria-hidden="true">←</span> Todas las asociaciones
        </NuxtLink>

        <article v-if="asociacionActual" class="asociacion-cabecera">
          <div class="asociacion-sello" aria-hidden="true">{{ asociacionActual.nombre.slice(0, 1).toLocaleUpperCase('es-CL') }}</div>
          <div class="asociacion-cabecera-texto">
            <p class="directorio-sobretitulo">ASOCIACIÓN LOCAL</p>
            <h2 id="titulo-asociacion">{{ asociacionActual.nombre }}</h2>
            <p v-if="asociacionActual.descripcion" class="asociacion-descripcion">{{ asociacionActual.descripcion }}</p>
            <div class="asociacion-datos">
              <span v-if="nombreSector(asociacionActual.sectorId)">{{ nombreSector(asociacionActual.sectorId) }}</span>
              <span>{{ asociacionActual.integrantes.length }} {{ asociacionActual.integrantes.length === 1 ? 'emprendimiento integrante' : 'emprendimientos integrantes' }}</span>
            </div>
          </div>
        </article>

        <section v-if="asociacionActual" class="integrantes-seccion" aria-labelledby="titulo-integrantes">
          <div class="directorio-titulo-fila">
            <div>
              <h2 id="titulo-integrantes">Emprendedoras integrantes</h2>
              <p>Visita la tienda de cada integrante para conocer sus productos.</p>
            </div>
            <span class="integrantes-total">{{ asociacionActual.integrantes.length }}</span>
          </div>

          <div v-if="asociacionActual.integrantes.length" class="integrantes-lista">
            <article v-for="integrante in asociacionActual.integrantes" :key="integrante.tiendaId" class="integrante-tarjeta">
              <div class="directorio-avatar">
                <ImagenPublica
                  v-if="integrante.logoMiniatura"
                  :ruta="integrante.logoMiniatura"
                  :alt="`Logo de ${integrante.nombre}`"
                  ajuste="contain"
                  :zoom="false"
                />
                <span v-else aria-hidden="true">{{ integrante.nombre.slice(0, 1).toLocaleUpperCase('es-CL') }}</span>
              </div>
              <div class="integrante-info">
                <h3>{{ integrante.nombre }}</h3>
                <p>{{ nombreCatalogo(integrante.tipoEmprendimientoId, catalogos.tiposEmprendimiento, 'Emprendimiento local') }}</p>
                <p v-if="nombreSector(integrante.sectorId)">{{ nombreSector(integrante.sectorId) }}</p>
              </div>
              <NuxtLink class="directorio-boton" :to="hrefTienda(integrante.tiendaId)">
                Visitar tienda <span aria-hidden="true">→</span>
              </NuxtLink>
            </article>
          </div>
          <div v-else class="directorio-vacio">
            <span aria-hidden="true">🌼</span>
            <h3>Aún no hay tiendas publicadas en esta asociación</h3>
            <p>Cuando sus integrantes publiquen sus tiendas, aparecerán aquí.</p>
          </div>
        </section>

        <div v-else-if="cargando" class="directorio-estado" role="status">Cargando la asociación…</div>
        <div v-else-if="error" class="directorio-error" role="alert">
          <p>No pudimos cargar el directorio. Revisa tu conexión e inténtalo de nuevo.</p>
          <button class="directorio-boton" type="button" @click="cargarDirectorio">Reintentar</button>
        </div>
        <div v-else class="directorio-vacio">
          <span aria-hidden="true">🌼</span>
          <h2>Esta asociación ya no está disponible</h2>
          <p>Vuelve al directorio para consultar las asociaciones activas.</p>
        </div>
      </section>

      <section v-else id="panel-directorio" class="directorio-panel" role="tabpanel" :aria-labelledby="vista === 'asociaciones' ? 'pestana-asociaciones' : 'pestana-emprendedoras'">
        <label class="directorio-busqueda">
          <span aria-hidden="true">⌕</span>
          <input
            v-model="busqueda"
            type="search"
            :placeholder="vista === 'asociaciones' ? 'Buscar asociaciones o integrantes' : 'Buscar por nombre, tienda o descripción'"
            :aria-label="vista === 'asociaciones' ? 'Buscar asociaciones' : 'Buscar emprendedoras'"
          />
        </label>

        <div v-if="vista === 'emprendedoras'" class="directorio-filtros" aria-label="Filtros de emprendedoras">
          <label>
            <span>Tipo de emprendimiento</span>
            <select v-model="tipoSeleccionado" aria-label="Filtrar por tipo de emprendimiento">
              <option value="">Todos los tipos</option>
              <option v-for="tipo in catalogos.tiposEmprendimiento" :key="tipo.id" :value="tipo.id">{{ tipo.nombre }}</option>
            </select>
          </label>
          <label>
            <span>Sector</span>
            <select v-model="sectorSeleccionado" aria-label="Filtrar por sector">
              <option value="">Todos los sectores</option>
              <option v-for="sector in catalogos.sectores" :key="sector.id" :value="sector.id">{{ sector.nombre }}</option>
            </select>
          </label>
          <label>
            <span>Asociación</span>
            <select v-model="asociacionSeleccionada" aria-label="Filtrar por asociación">
              <option value="">Todas las asociaciones</option>
              <option v-for="asociacion in asociaciones" :key="asociacion.id" :value="asociacion.id">{{ asociacion.nombre }}</option>
            </select>
          </label>
          <button v-if="busqueda || tipoSeleccionado || sectorSeleccionado || asociacionSeleccionada" class="directorio-limpiar" type="button" @click="limpiarFiltros">
            Limpiar filtros
          </button>
        </div>

        <section v-if="vista === 'emprendedoras'" class="directorio-resultados" aria-labelledby="titulo-emprendedoras">
          <div class="directorio-titulo-fila">
            <div>
              <h2 id="titulo-emprendedoras">Emprendedoras del territorio</h2>
              <p>Encuentra un emprendimiento y descubre su tienda.</p>
            </div>
            <span class="directorio-conteo">{{ tiendasFiltradas.length }} {{ tiendasFiltradas.length === 1 ? 'resultado' : 'resultados' }}</span>
          </div>

          <div v-if="cargando && !tiendas.length" class="directorio-estado" role="status">Cargando emprendedoras y asociaciones…</div>
          <div v-else-if="error" class="directorio-error" role="alert">
            <p>No pudimos cargar el directorio. Revisa tu conexión e inténtalo de nuevo.</p>
            <button class="directorio-boton" type="button" @click="cargarDirectorio">Reintentar</button>
          </div>
          <div v-else-if="tiendasFiltradas.length" class="emprendedoras-grid">
            <article v-for="tienda in tiendasFiltradas" :key="tienda.id" class="emprendedora-tarjeta">
              <div class="emprendedora-cabecera">
                <div class="directorio-avatar">
                  <ImagenPublica
                    v-if="tienda.logoMiniatura || tienda.logo"
                    :ruta="tienda.logoMiniatura || tienda.logo"
                    :alt="`Logo de ${tienda.nombre}`"
                    ajuste="contain"
                    :zoom="false"
                  />
                  <span v-else aria-hidden="true">{{ tienda.nombre.slice(0, 1).toLocaleUpperCase('es-CL') }}</span>
                </div>
                <div class="emprendedora-identidad">
                  <p class="directorio-card-etiqueta">EMPRENDIMIENTO LOCAL</p>
                  <h3>{{ tienda.nombre }}</h3>
                </div>
              </div>

              <p class="emprendedora-descripcion">{{ tienda.descripcion || 'Conoce este emprendimiento local y su catálogo.' }}</p>
              <div class="emprendedora-metadatos">
                <span class="emprendedora-chip">{{ nombreTipo(tienda) }}</span>
                <span v-if="nombreSector(tienda.sectorId)" class="emprendedora-chip">{{ nombreSector(tienda.sectorId) }}</span>
              </div>
              <div v-if="asociacionesDeTienda(tienda.id).length" class="emprendedora-asociaciones">
                <span>Asociación</span>
                <NuxtLink
                  v-for="asociacion in asociacionesDeTienda(tienda.id)"
                  :key="asociacion.id"
                  :to="hrefAsociacion(asociacion.id)"
                >
                  {{ asociacion.nombre }}
                </NuxtLink>
              </div>
              <NuxtLink class="directorio-boton directorio-boton-principal" :to="hrefTienda(tienda.id)">
                Ver tienda <span aria-hidden="true">→</span>
              </NuxtLink>
            </article>
          </div>
          <div v-else class="directorio-vacio">
            <span aria-hidden="true">🌼</span>
            <h3>{{ tiendas.length ? 'No encontramos coincidencias' : 'Aún no hay emprendimientos publicados' }}</h3>
            <p>{{ tiendas.length ? 'Prueba con otro nombre o ajusta los filtros.' : 'Las tiendas públicas aparecerán aquí cuando estén disponibles.' }}</p>
            <button v-if="busqueda || tipoSeleccionado || sectorSeleccionado || asociacionSeleccionada" class="directorio-limpiar" type="button" @click="limpiarFiltros">
              Limpiar búsqueda y filtros
            </button>
          </div>
        </section>

        <section v-else class="directorio-resultados" aria-labelledby="titulo-asociaciones">
          <div class="directorio-titulo-fila">
            <div>
              <h2 id="titulo-asociaciones">Asociaciones del territorio</h2>
              <p>Conoce sus comunidades y a las emprendedoras que las integran.</p>
            </div>
            <span class="directorio-conteo">{{ asociacionesFiltradas.length }} {{ asociacionesFiltradas.length === 1 ? 'resultado' : 'resultados' }}</span>
          </div>

          <div v-if="cargando && !asociaciones.length" class="directorio-estado" role="status">Cargando asociaciones…</div>
          <div v-else-if="error" class="directorio-error" role="alert">
            <p>No pudimos cargar el directorio. Revisa tu conexión e inténtalo de nuevo.</p>
            <button class="directorio-boton" type="button" @click="cargarDirectorio">Reintentar</button>
          </div>
          <div v-else-if="asociacionesFiltradas.length" class="asociaciones-grid">
            <article v-for="asociacion in asociacionesFiltradas" :key="asociacion.id" class="asociacion-tarjeta">
              <div class="asociacion-sello" aria-hidden="true">{{ asociacion.nombre.slice(0, 1).toLocaleUpperCase('es-CL') }}</div>
              <div class="asociacion-tarjeta-cuerpo">
                <div class="asociacion-tarjeta-titulo">
                  <h3>{{ asociacion.nombre }}</h3>
                  <span>{{ asociacion.integrantes.length }} {{ asociacion.integrantes.length === 1 ? 'integrante' : 'integrantes' }}</span>
                </div>
                <p>{{ asociacion.descripcion || 'Una comunidad que impulsa el trabajo local y colaborativo.' }}</p>
                <div class="asociacion-tarjeta-pie">
                  <span v-if="nombreSector(asociacion.sectorId)" class="emprendedora-chip">{{ nombreSector(asociacion.sectorId) }}</span>
                  <NuxtLink class="directorio-boton" :to="hrefAsociacion(asociacion.id)">
                    Ver integrantes <span aria-hidden="true">→</span>
                  </NuxtLink>
                </div>
              </div>
            </article>
          </div>
          <div v-else class="directorio-vacio">
            <span aria-hidden="true">🌼</span>
            <h3>{{ asociaciones.length ? 'No encontramos asociaciones' : 'Aún no hay asociaciones publicadas' }}</h3>
            <p>{{ asociaciones.length ? 'Prueba con otro nombre o territorio.' : 'Las asociaciones públicas aparecerán aquí cuando estén disponibles.' }}</p>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.comprador {
  min-height: 100vh;
  min-height: 100dvh;
  min-width: 0;
  background: var(--amancay-fondo);
  color: var(--amancay-tinta);
  font-family: var(--amancay-fuente-cuerpo);
}

.comprador.modo-explorar {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
}

.directorio-contenido {
  width: 100%;
  min-width: 0;
  padding: 1.8rem clamp(1rem, 3vw, 2.5rem) 4rem;
  box-sizing: border-box;
}

.directorio-migas {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-bottom: 1.1rem;
  color: var(--amancay-tinta-suave);
  font-size: 0.86rem;
}

.directorio-migas a,
.directorio-volver {
  color: var(--amancay-bordo);
  font-weight: 700;
  text-decoration: none;
}

.directorio-migas a:hover,
.directorio-volver:hover {
  text-decoration: underline;
}

.directorio-migas .actual {
  color: var(--amancay-tinta);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.directorio-encabezado {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1.5rem;
  margin-bottom: 1.5rem;
  padding: 1.4rem 1.6rem;
  border: 1px solid var(--amancay-borde);
  border-radius: 24px;
  background: linear-gradient(110deg, var(--amancay-superficie), rgba(255, 251, 242, 0.78));
}

.directorio-sobretitulo,
.directorio-card-etiqueta {
  margin: 0 0 0.35rem;
  color: var(--amancay-bordo);
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.directorio-encabezado h1 {
  margin: 0;
  color: var(--amancay-bordo);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 400;
  line-height: 1.1;
}

.directorio-bajada {
  margin: 0.55rem 0 0;
  color: var(--amancay-tinta-suave);
  font-size: 1rem;
}

.directorio-emblema {
  flex: none;
  width: 72px;
  height: 72px;
  padding: 8px;
  border-radius: 50%;
  background: var(--amancay-naranja-fondo);
  border: 1px solid var(--amancay-borde);
  object-fit: contain;
}

.directorio-pestanas {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.55rem;
  width: min(100%, 560px);
  margin-bottom: 1rem;
}

.directorio-pestanas button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  min-width: 0;
  min-height: 48px;
  padding: 0.7rem 1rem;
  border: 1px solid var(--amancay-borde);
  border-radius: 999px;
  background: var(--amancay-superficie);
  color: var(--amancay-bordo);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.directorio-pestanas button.seleccionada {
  border-color: var(--amancay-bordo);
  background: var(--amancay-bordo);
  color: #fff;
}

.directorio-pestanas button span {
  display: inline-grid;
  place-items: center;
  min-width: 1.45rem;
  height: 1.45rem;
  padding: 0 0.25rem;
  border-radius: 999px;
  background: rgba(107, 30, 61, 0.1);
  font-size: 0.75rem;
}

.directorio-pestanas button.seleccionada span {
  background: rgba(255, 255, 255, 0.18);
}

.directorio-panel {
  min-width: 0;
}

.directorio-busqueda {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-width: 0;
  min-height: 54px;
  padding: 0 1rem;
  border: 1.5px solid var(--amancay-borde);
  border-radius: 999px;
  background: var(--amancay-superficie);
  color: var(--amancay-tinta-suave);
}

.directorio-busqueda > span {
  flex: none;
  color: var(--amancay-bordo);
  font-size: 1.5rem;
  line-height: 1;
}

.directorio-busqueda input {
  width: 100%;
  min-width: 0;
  padding: 0.8rem 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--amancay-tinta);
  font: inherit;
}

.directorio-busqueda:focus-within {
  border-color: var(--amancay-bordo);
  box-shadow: 0 0 0 3px rgba(107, 30, 61, 0.1);
}

.directorio-filtros {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  align-items: end;
  gap: 0.7rem;
  margin: 0.8rem 0 1.7rem;
}

.directorio-filtros label {
  display: grid;
  gap: 0.35rem;
  min-width: 0;
  color: var(--amancay-tinta-suave);
  font-size: 0.75rem;
  font-weight: 700;
}

.directorio-filtros select {
  width: 100%;
  min-width: 0;
  min-height: 46px;
  padding: 0.65rem 2rem 0.65rem 0.85rem;
  border: 1px solid var(--amancay-borde);
  border-radius: 14px;
  background: var(--amancay-superficie);
  color: var(--amancay-tinta);
  font: inherit;
  font-size: 0.86rem;
}

.directorio-filtros select:focus-visible,
.directorio-pestanas button:focus-visible,
.directorio-boton:focus-visible,
.directorio-limpiar:focus-visible {
  outline: 3px solid rgba(107, 30, 61, 0.3);
  outline-offset: 2px;
}

.directorio-limpiar {
  min-height: 42px;
  padding: 0.45rem 0.8rem;
  border: 0;
  background: transparent;
  color: var(--amancay-bordo);
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.directorio-resultados {
  min-width: 0;
  margin-top: 1.7rem;
}

.directorio-titulo-fila {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.directorio-titulo-fila h2 {
  margin: 0;
  color: var(--amancay-bordo);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(1.45rem, 2.6vw, 2rem);
  font-weight: 400;
  line-height: 1.15;
}

.directorio-titulo-fila p {
  margin: 0.3rem 0 0;
  color: var(--amancay-tinta-suave);
  font-size: 0.9rem;
}

.directorio-conteo,
.integrantes-total {
  flex: none;
  color: var(--amancay-tinta-suave);
  font-size: 0.84rem;
  font-weight: 600;
}

.emprendedoras-grid,
.asociaciones-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
  gap: 1rem;
  min-width: 0;
}

.emprendedora-tarjeta,
.asociacion-tarjeta,
.integrante-tarjeta {
  min-width: 0;
  border: 1.5px solid var(--amancay-borde);
  border-radius: 20px;
  background: var(--amancay-superficie);
  box-shadow: 0 7px 22px rgba(107, 30, 61, 0.045);
}

.emprendedora-tarjeta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.8rem;
  padding: 1rem;
}

.emprendedora-cabecera {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  width: 100%;
  min-width: 0;
}

.directorio-avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 66px;
  height: 66px;
  overflow: hidden;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--amancay-naranja-fondo);
  box-shadow: 0 2px 10px rgba(107, 30, 61, 0.1);
  color: var(--amancay-bordo);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 1.8rem;
}

.directorio-avatar :deep(.imagen-publica) {
  width: 100%;
  height: 100%;
  aspect-ratio: 1;
  border-radius: 50%;
}

.directorio-avatar :deep(.imagen-publica span) {
  font-size: 1.4rem;
}

.directorio-avatar :deep(.imagen-publica small) {
  display: none;
}

.emprendedora-identidad {
  min-width: 0;
}

.directorio-card-etiqueta {
  margin-bottom: 0.15rem;
  color: var(--amancay-tinta-suave);
  font-size: 0.62rem;
}

.emprendedora-identidad h3,
.integrante-info h3,
.asociacion-tarjeta h3 {
  margin: 0;
  color: var(--amancay-bordo);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 1.35rem;
  font-weight: 400;
  line-height: 1.15;
  overflow-wrap: anywhere;
}

.emprendedora-descripcion,
.asociacion-tarjeta-cuerpo > p {
  display: -webkit-box;
  width: 100%;
  margin: 0;
  overflow: hidden;
  color: var(--amancay-tinta-suave);
  font-size: 0.88rem;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.emprendedora-metadatos {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  width: 100%;
}

.emprendedora-chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-height: 30px;
  padding: 0.25rem 0.65rem;
  border: 1px solid var(--amancay-borde);
  border-radius: 999px;
  background: #fff7e9;
  color: var(--amancay-tinta);
  font-size: 0.78rem;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.emprendedora-asociaciones {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem 0.55rem;
  width: 100%;
  color: var(--amancay-tinta-suave);
  font-size: 0.78rem;
}

.emprendedora-asociaciones > span {
  font-weight: 700;
}

.emprendedora-asociaciones a {
  color: var(--amancay-bordo);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.directorio-boton {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  min-height: 42px;
  padding: 0.55rem 1rem;
  border: 1.5px solid var(--amancay-bordo);
  border-radius: 999px;
  background: var(--amancay-superficie);
  color: var(--amancay-bordo);
  font: inherit;
  font-size: 0.86rem;
  font-weight: 700;
  text-align: center;
  text-decoration: none;
  overflow-wrap: anywhere;
  cursor: pointer;
}

.directorio-boton:hover {
  background: var(--amancay-bordo-suave);
}

.directorio-boton-principal {
  width: 100%;
  margin-top: auto;
}

.asociacion-tarjeta {
  display: flex;
  align-items: flex-start;
  gap: 0.9rem;
  padding: 1rem;
}

.asociacion-sello {
  display: grid;
  place-items: center;
  flex: none;
  width: 64px;
  height: 64px;
  border: 1px solid rgba(107, 30, 61, 0.12);
  border-radius: 50%;
  background: linear-gradient(145deg, var(--amancay-naranja-fondo), var(--amancay-bordo-suave));
  color: var(--amancay-bordo);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 1.8rem;
}

.asociacion-tarjeta-cuerpo {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.65rem;
  flex: 1;
  min-width: 0;
}

.asociacion-tarjeta-titulo {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.3rem 0.65rem;
  width: 100%;
}

.asociacion-tarjeta-titulo > span {
  color: var(--amancay-tinta-suave);
  font-size: 0.78rem;
  font-weight: 600;
}

.asociacion-tarjeta-pie {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  width: 100%;
}

.directorio-detalle-asociacion {
  display: grid;
  gap: 1.2rem;
  min-width: 0;
}

.directorio-volver {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  min-height: 42px;
}

.asociacion-cabecera {
  display: flex;
  align-items: center;
  gap: 1.2rem;
  min-width: 0;
  padding: clamp(1rem, 3vw, 1.8rem);
  border: 1.5px solid #f4c6b4;
  border-radius: 24px;
  background: linear-gradient(110deg, var(--amancay-superficie), rgba(251, 227, 184, 0.42));
  box-shadow: 0 8px 24px rgba(107, 30, 61, 0.06);
}

.asociacion-cabecera .asociacion-sello {
  width: 84px;
  height: 84px;
  font-size: 2.1rem;
}

.asociacion-cabecera-texto {
  min-width: 0;
}

.asociacion-cabecera h2 {
  margin: 0;
  color: var(--amancay-bordo);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(1.7rem, 3.4vw, 2.5rem);
  font-weight: 400;
  line-height: 1.1;
  overflow-wrap: anywhere;
}

.asociacion-descripcion {
  margin: 0.5rem 0;
  color: var(--amancay-tinta-suave);
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.asociacion-datos {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.asociacion-datos span {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0.25rem 0.7rem;
  border: 1px solid var(--amancay-borde);
  border-radius: 999px;
  background: rgba(255, 251, 242, 0.8);
  color: var(--amancay-tinta);
  font-size: 0.8rem;
  font-weight: 600;
}

.integrantes-seccion {
  min-width: 0;
}

.integrantes-lista {
  display: grid;
  gap: 0.75rem;
}

.integrante-tarjeta {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  padding: 0.85rem 1rem;
}

.integrante-info {
  flex: 1;
  min-width: 0;
}

.integrante-info h3 {
  font-size: 1.2rem;
}

.integrante-info p {
  margin: 0.2rem 0 0;
  color: var(--amancay-tinta-suave);
  font-size: 0.82rem;
  overflow-wrap: anywhere;
}

.directorio-estado,
.directorio-error,
.directorio-vacio {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  min-width: 0;
  padding: 2.2rem 1rem;
  border: 1.5px dashed var(--amancay-borde);
  border-radius: 20px;
  background: rgba(255, 251, 242, 0.85);
  color: var(--amancay-tinta-suave);
  text-align: center;
}

.directorio-estado {
  border-style: solid;
  background: var(--amancay-mostaza-suave);
}

.directorio-error {
  border-style: solid;
  background: var(--amancay-bordo-suave);
  color: var(--amancay-bordo-oscuro);
}

.directorio-vacio > span {
  font-size: 2rem;
}

.directorio-vacio h2,
.directorio-vacio h3,
.directorio-vacio p,
.directorio-error p {
  margin: 0;
}

.directorio-vacio h2,
.directorio-vacio h3 {
  color: var(--amancay-bordo);
}

@media (max-width: 1000px) {
  .comprador.modo-explorar {
    grid-template-columns: minmax(0, 1fr);
  }

  .directorio-filtros {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .directorio-limpiar {
    justify-self: start;
  }
}

@media (max-width: 560px) {
  .directorio-contenido {
    padding: 1rem 0.8rem 2.5rem;
  }

  .directorio-encabezado {
    gap: 0.7rem;
    padding: 1rem;
    border-radius: 18px;
  }

  .directorio-encabezado h1 {
    font-size: clamp(1.85rem, 8vw, 2.4rem);
  }

  .directorio-bajada {
    font-size: 0.9rem;
  }

  .directorio-emblema {
    width: 52px;
    height: 52px;
    padding: 5px;
  }

  .directorio-pestanas {
    width: 100%;
    gap: 0.4rem;
  }

  .directorio-pestanas button {
    min-height: 46px;
    gap: 0.35rem;
    padding: 0.55rem 0.5rem;
    font-size: 0.84rem;
  }

  .directorio-filtros {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.55rem;
    margin-bottom: 1.2rem;
  }

  .directorio-filtros select {
    min-height: 48px;
  }

  .directorio-titulo-fila {
    align-items: flex-start;
  }

  .directorio-titulo-fila p {
    font-size: 0.82rem;
  }

  .directorio-conteo {
    padding-top: 0.25rem;
    font-size: 0.75rem;
    text-align: right;
  }

  .emprendedoras-grid,
  .asociaciones-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .asociacion-cabecera {
    align-items: flex-start;
    gap: 0.8rem;
    padding: 1rem;
    border-radius: 18px;
  }

  .asociacion-cabecera .asociacion-sello {
    width: 58px;
    height: 58px;
    font-size: 1.5rem;
  }

  .asociacion-cabecera h2 {
    font-size: 1.6rem;
  }

  .integrante-tarjeta {
    flex-wrap: wrap;
    padding: 0.8rem;
  }

  .integrante-tarjeta .directorio-boton {
    width: 100%;
  }

  .asociacion-tarjeta {
    gap: 0.7rem;
    padding: 0.8rem;
  }

  .asociacion-sello {
    width: 52px;
    height: 52px;
  }

  .asociacion-tarjeta-pie {
    align-items: stretch;
  }

  .asociacion-tarjeta-pie .directorio-boton {
    width: 100%;
  }
}
</style>
