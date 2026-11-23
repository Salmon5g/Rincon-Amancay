<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import '~/assets/amancay-tokens.css'
import type { TiendaPublica } from '~/types/comprador-publico'
import type { ElementoEtiqueta } from '~/repositories/catalogo-publico'

useHead({
  title: 'Tiendas locales — Rincón Amancay',
  link: [
    { rel: 'icon', href: '/favicon.png' },
    { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap' },
  ],
})

const { $catalogoPublico } = useNuxtApp()
const tiendas = ref<TiendaPublica[]>([])
const sectores = ref<ElementoEtiqueta[]>([])
const tipos = ref<ElementoEtiqueta[]>([])
const busqueda = ref('')
const sectorSeleccionado = ref('')
const tipoSeleccionado = ref('')
const cargando = ref(false)
const error = ref(false)

function normalizar(valor: string) {
  return valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-CL').trim()
}

function nombreDe(id: string, opciones: ElementoEtiqueta[]) {
  return opciones.find((opcion) => opcion.id === id)?.nombre ?? ''
}

const tiendasFiltradas = computed(() => {
  const termino = normalizar(busqueda.value)
  return tiendas.value.filter((tienda) => {
    if (sectorSeleccionado.value && tienda.sectorId !== sectorSeleccionado.value) return false
    if (tipoSeleccionado.value && tienda.tipoEmprendimientoId !== tipoSeleccionado.value) return false
    return !termino || normalizar([
      tienda.nombre,
      tienda.descripcion,
      tienda.direccion ?? '',
      nombreDe(tienda.sectorId, sectores.value),
      nombreDe(tienda.tipoEmprendimientoId, tipos.value),
    ].join(' ')).includes(termino)
  })
})

function limpiarFiltros() {
  busqueda.value = ''
  sectorSeleccionado.value = ''
  tipoSeleccionado.value = ''
}

async function cargar() {
  cargando.value = true
  error.value = false
  try {
    const [catalogos, primeraPagina] = await Promise.all([
      $catalogoPublico.listarCatalogosPublicos(),
      $catalogoPublico.listarTiendas(),
    ])
    sectores.value = catalogos.sectores
    tipos.value = catalogos.tiposEmprendimiento
    const resultado = [...primeraPagina.elementos]
    let cursor = primeraPagina.cursorSiguiente
    while (cursor) {
      const pagina = await $catalogoPublico.listarTiendas(cursor)
      resultado.push(...pagina.elementos)
      cursor = pagina.cursorSiguiente
    }
    tiendas.value = resultado
  } catch {
    error.value = true
  } finally {
    cargando.value = false
  }
}

function inicial(nombre: string) {
  return nombre.trim().slice(0, 1).toLocaleUpperCase('es-CL') || 'A'
}

onMounted(() => void cargar())
</script>

<template>
  <div class="comprador catalogo-independiente">
    <CompradorLateral activo="tiendas" />
    <main class="contenido">
      <nav class="migas-catalogo" aria-label="Ruta">
        <NuxtLink to="/comprador">Explorar</NuxtLink><span aria-hidden="true">/</span><span aria-current="page">Tiendas</span>
      </nav>
      <header class="encabezado-catalogo">
        <div>
          <p class="sobretitulo">EMPRENDIMIENTOS LOCALES</p>
          <h1>Tiendas</h1>
          <p class="bajada">Encuentra emprendimientos, descubre lo que hacen y visita su catálogo.</p>
        </div>
        <AccesoComprador />
      </header>
      <AvisosFerias />

      <section class="filtros-catalogo" aria-label="Buscar y filtrar tiendas">
        <label class="campo-busqueda">
          <span aria-hidden="true">⌕</span>
          <input v-model="busqueda" type="search" placeholder="Buscar por tienda, descripción o ubicación" aria-label="Buscar tiendas" />
        </label>
        <label class="campo-filtro">
          <span>Tipo de emprendimiento</span>
          <select v-model="tipoSeleccionado" aria-label="Filtrar por tipo de emprendimiento">
            <option value="">Todos los tipos</option>
            <option v-for="tipo in tipos" :key="tipo.id" :value="tipo.id">{{ tipo.nombre }}</option>
          </select>
        </label>
        <label class="campo-filtro">
          <span>Sector</span>
          <select v-model="sectorSeleccionado" aria-label="Filtrar por sector">
            <option value="">Todos los sectores</option>
            <option v-for="sector in sectores" :key="sector.id" :value="sector.id">{{ sector.nombre }}</option>
          </select>
        </label>
        <button v-if="busqueda || tipoSeleccionado || sectorSeleccionado" class="limpiar" type="button" @click="limpiarFiltros">Limpiar filtros</button>
      </section>

      <div class="resumen-catalogo" aria-live="polite">
        <span>{{ tiendasFiltradas.length }} {{ tiendasFiltradas.length === 1 ? 'tienda' : 'tiendas' }}</span>
        <span v-if="busqueda || tipoSeleccionado || sectorSeleccionado">con los filtros actuales</span>
      </div>

      <p v-if="cargando" class="estado-catalogo" role="status">Cargando tiendas…</p>
      <div v-else-if="error" class="estado-catalogo estado-error" role="alert">
        <p>No pudimos cargar las tiendas. Revisa tu conexión e inténtalo otra vez.</p>
        <button class="accion-secundaria" type="button" @click="cargar">Reintentar</button>
      </div>
      <div v-else-if="!tiendasFiltradas.length" class="estado-catalogo estado-vacio">
        <span aria-hidden="true">🌼</span>
        <h2>No encontramos tiendas</h2>
        <p>Prueba con otro texto o limpia los filtros.</p>
        <button v-if="busqueda || tipoSeleccionado || sectorSeleccionado" class="accion-secundaria" type="button" @click="limpiarFiltros">Limpiar filtros</button>
      </div>
      <div v-else class="rejilla-tiendas">
        <article v-for="tienda in tiendasFiltradas" :key="tienda.id" class="tarjeta-tienda">
          <NuxtLink class="identidad-tienda" :to="{ path: '/comprador', query: { tienda: tienda.id } }" :aria-label="`Visitar tienda ${tienda.nombre}`">
            <span class="logo-tienda">
              <ImagenPublica v-if="tienda.logoMiniatura || tienda.logo" :ruta="tienda.logoMiniatura || tienda.logo" :alt="tienda.nombre" ajuste="contain" />
              <span v-else aria-hidden="true">{{ inicial(tienda.nombre) }}</span>
            </span>
            <span class="datos-tienda">
              <span class="meta-tienda">{{ nombreDe(tienda.tipoEmprendimientoId, tipos) || 'Emprendimiento local' }}</span>
              <strong>{{ tienda.nombre }}</strong>
            </span>
          </NuxtLink>
          <p class="descripcion-tienda">{{ tienda.descripcion || 'Conoce este emprendimiento local.' }}</p>
          <div class="etiquetas-tienda">
            <span v-if="nombreDe(tienda.sectorId, sectores)">📍 {{ nombreDe(tienda.sectorId, sectores) }}</span>
            <span v-if="tienda.direccion && tienda.direccion !== nombreDe(tienda.sectorId, sectores)">{{ tienda.direccion }}</span>
          </div>
          <NuxtLink class="accion-tienda" :to="{ path: '/comprador', query: { tienda: tienda.id } }">Visitar tienda <span aria-hidden="true">→</span></NuxtLink>
        </article>
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
.filtros-catalogo { display: grid; grid-template-columns: minmax(240px, 1.6fr) repeat(2, minmax(170px, 1fr)) auto; gap: .8rem; align-items: end; margin-top: 1.6rem; padding: 1rem; border: 1px solid var(--amancay-borde); border-radius: 20px; background: var(--amancay-superficie); box-shadow: 0 8px 24px rgba(73, 40, 44, .05); }
.campo-busqueda { display: flex; align-items: center; gap: .65rem; min-height: 48px; padding: 0 .9rem; border: 1px solid var(--amancay-borde); border-radius: 14px; background: var(--amancay-fondo); color: var(--amancay-bordo); font-size: 1.25rem; }
.campo-busqueda input, .campo-filtro select { width: 100%; border: 0; outline: 0; background: transparent; color: var(--amancay-tinta); font: inherit; }
.campo-busqueda input { min-width: 0; font-size: .92rem; }
.campo-filtro { display: grid; gap: .35rem; color: var(--amancay-tinta-suave); font-size: .76rem; font-weight: 700; }
.campo-filtro select { box-sizing: border-box; min-height: 48px; padding: 0 .75rem; border: 1px solid var(--amancay-borde); border-radius: 14px; background: var(--amancay-fondo); font-size: .88rem; }
.limpiar, .accion-secundaria { min-height: 44px; padding: .65rem .9rem; border: 1px solid var(--amancay-borde); border-radius: 13px; background: var(--amancay-superficie); color: var(--amancay-bordo); font: inherit; font-weight: 700; cursor: pointer; }
.resumen-catalogo { display: flex; gap: .35rem; margin: 1.2rem 0 .8rem; color: var(--amancay-tinta-suave); font-size: .88rem; }
.resumen-catalogo span:first-child { color: var(--amancay-bordo); font-weight: 800; }
.estado-catalogo { display: grid; justify-items: center; gap: .65rem; min-height: 220px; align-content: center; padding: 2rem; border: 1px dashed var(--amancay-borde); border-radius: 22px; color: var(--amancay-tinta-suave); text-align: center; }
.estado-catalogo p, .estado-catalogo h2 { margin: 0; }
.estado-vacio > span { font-size: 2rem; }
.estado-vacio h2 { color: var(--amancay-bordo); font-family: 'DM Serif Display', Georgia, serif; font-size: 1.65rem; font-weight: 400; }
.estado-error { border-color: #d88f81; }
.rejilla-tiendas { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(290px, 100%), 1fr)); gap: 1rem; }
.tarjeta-tienda { display: flex; min-width: 0; flex-direction: column; gap: .8rem; padding: 1.1rem; border: 1px solid var(--amancay-borde); border-radius: 22px; background: var(--amancay-superficie); box-shadow: 0 8px 24px rgba(73, 40, 44, .055); transition: transform .16s ease, box-shadow .16s ease; }
.tarjeta-tienda:hover { transform: translateY(-2px); box-shadow: 0 12px 30px rgba(73, 40, 44, .1); }
.identidad-tienda { display: flex; min-width: 0; align-items: center; gap: .8rem; color: inherit; text-decoration: none; }
.logo-tienda { display: grid; width: 68px; height: 68px; flex: none; place-items: center; overflow: hidden; border: 1px solid var(--amancay-borde); border-radius: 20px; background: var(--amancay-naranja-fondo); color: var(--amancay-bordo); font-family: 'DM Serif Display', Georgia, serif; font-size: 2rem; }
.logo-tienda :deep(.imagen-publica), .logo-tienda :deep(img) { width: 100%; height: 100%; object-fit: contain; }
.datos-tienda { display: grid; min-width: 0; gap: .2rem; }
.datos-tienda strong { overflow-wrap: anywhere; color: var(--amancay-bordo); font-family: 'DM Serif Display', Georgia, serif; font-size: 1.3rem; font-weight: 400; }
.meta-tienda { color: var(--amancay-tinta-suave); font-size: .75rem; }
.descripcion-tienda { display: -webkit-box; min-height: 3em; margin: 0; overflow: hidden; color: var(--amancay-tinta-suave); line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.etiquetas-tienda { display: flex; flex-wrap: wrap; gap: .4rem; color: var(--amancay-bordo); font-size: .78rem; }
.etiquetas-tienda span { padding: .35rem .55rem; border-radius: 999px; background: var(--amancay-naranja-fondo); }
.accion-tienda { display: flex; justify-content: space-between; align-items: center; margin-top: auto; padding: .75rem .9rem; border-radius: 13px; background: var(--amancay-bordo); color: white; font-weight: 700; text-decoration: none; }
@media (max-width: 1000px) { .catalogo-independiente { grid-template-columns: minmax(0, 1fr); } .filtros-catalogo { grid-template-columns: repeat(2, minmax(0, 1fr)); } .campo-busqueda { grid-column: 1 / -1; } }
@media (max-width: 600px) { .encabezado-catalogo { align-items: flex-start; flex-direction: column-reverse; } .filtros-catalogo { grid-template-columns: minmax(0, 1fr); } .campo-busqueda { grid-column: auto; } .limpiar { width: 100%; } }
</style>
