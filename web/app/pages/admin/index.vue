<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listarCatalogos } from '~/repositories/admin'
import type { DatosAdmin, ElementoCatalogo } from '~/types/admin'

useHead({
  title: 'Administración — Rincón Amancay',
  link: [
    { rel: 'icon', type: 'image/png', href: '/favicon.png' },
    { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
    { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
    {
      rel: 'stylesheet',
      href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap',
    },
  ],
})

type Item = { titulo: string; detalle: string; estado: string }
type Seccion = {
  id: string
  icono: string
  titulo: string
  descripcion: string
  accion: string
  items: Item[]
  aviso?: string
}

const vacio: DatosAdmin = {
  accesos: [],
  emprendedoras: [],
  invitaciones: [],
  catalogos: { sectores: [], categorias: [], tiposEmprendimiento: [], tiposProducto: [] },
}

// Lecturas en el cliente (no SSR). Sin sesión solo hay catálogos públicos;
// accesos, emprendedoras e invitaciones exigen administradora (ver firestore.rules).
const { $amancay } = useNuxtApp()
const cargando = ref(false)
const errorDatos = ref('')
const datos = ref<DatosAdmin>(vacio)
const formularioAbierto = ref(false)
const borrador = ref({
  nombre: '',
  correo: '',
  nombreTienda: '',
  descripcion: '',
  sectorId: '',
  tipoEmprendimientoId: '',
  ubicacion: '',
  fechaInicio: '',
  fechaFin: '',
  tipoCatalogo: 'sectores',
  valor: '',
  clave: '',
})
const tiposCatalogo = [
  { id: 'sectores', nombre: 'Sectores' },
  { id: 'categorias', nombre: 'Categorías' },
  { id: 'tiposEmprendimiento', nombre: 'Tipos de emprendimiento' },
  { id: 'tiposProducto', nombre: 'Tipos de producto' },
]

const SIN_MODELO = 'Sección en preparación. El formulario de muestra todavía no guarda datos.'

function activos(lista: ElementoCatalogo[]) {
  return lista.filter((valor) => valor.activo)
}

const secciones = computed<Seccion[]>(() => {
  function grupo(titulo: string, lista: ElementoCatalogo[]): Item {
    const vigentes = activos(lista)
    return {
      titulo,
      detalle: vigentes.map((valor) => valor.nombre).join(', ') || 'Sin valores',
      estado: `${vigentes.length} valores`,
    }
  }

  return [
    {
      id: 'emprendedoras',
      icono: '👩‍🎨',
      titulo: 'Emprendedoras',
      descripcion: 'Invitaciones, desactivación y reactivación de cuentas.',
      accion: '+ Invitar emprendedora',
      items: [],
    },
    {
      id: 'asociaciones',
      icono: '🤝',
      titulo: 'Asociaciones',
      descripcion: 'Sin modelo en Firestore todavía; pendiente de contrato.',
      accion: '+ Crear asociación',
      items: [],
      aviso: SIN_MODELO,
    },
    {
      id: 'ferias',
      icono: '🎪',
      titulo: 'Ferias y eventos',
      descripcion: 'Sin modelo en Firestore todavía; pendiente de contrato.',
      accion: '+ Crear feria',
      items: [],
      aviso: SIN_MODELO,
    },
    {
      id: 'catalogos',
      icono: '🗂️',
      titulo: 'Catálogos',
      descripcion: 'Tipos de producto, categorías, tipos de emprendimiento y sectores.',
      accion: '+ Agregar valor',
      items: [
        grupo('Tipos de producto', datos.value.catalogos.tiposProducto),
        grupo('Categorías', datos.value.catalogos.categorias),
        grupo('Tipos de emprendimiento', datos.value.catalogos.tiposEmprendimiento),
        grupo('Sectores', datos.value.catalogos.sectores),
      ],
    },
    {
      id: 'configuracion',
      icono: '⚙️',
      titulo: 'Configuración general',
      descripcion: 'Sin modelo en Firestore todavía; pendiente de contrato.',
      accion: 'Guardar cambios',
      items: [],
      aviso: SIN_MODELO,
    },
    {
      id: 'administradores',
      icono: '🛡️',
      titulo: 'Administradores',
      descripcion: 'Cuentas con rol administrador de la plataforma.',
      accion: '+ Agregar administrador',
      items: [],
    },
  ]
})

function mensajeError(error: unknown, alterno: string) {
  const e = error as { code?: string; message?: string }
  if (e?.code === 'permission-denied') return 'Sin permiso para leer estos datos.'
  if (e?.code === 'unavailable') return 'No hay conexión con los emuladores.'
  return e?.message || alterno
}

async function cargarDatos() {
  cargando.value = true
  errorDatos.value = ''
  try {
    const catalogos = await listarCatalogos($amancay as never)
    datos.value = { ...vacio, catalogos }
  } catch (error) {
    errorDatos.value = mensajeError(error, 'No se pudieron cargar los catálogos.')
  } finally {
    cargando.value = false
  }
}

onMounted(cargarDatos)

const activa = ref('inicio')
const busqueda = ref('')
const filtro = ref('Todos')
const seccion = computed(() => secciones.value.find((s) => s.id === activa.value) ?? null)
const hoy = new Date().toLocaleDateString('es-CL', { day: 'numeric', month: 'long' }).toUpperCase()
const aviso = ref('')
let temporizador: ReturnType<typeof setTimeout>

const resumen = computed(() =>
  secciones.value
    .filter((s) => s.items.length > 0)
    .map((s) => ({ id: s.id, icono: s.icono, titulo: s.titulo, total: s.items.length })),
)

const estados = computed(() => ['Todos', ...new Set(seccion.value?.items.map((i) => i.estado) ?? [])])

const visibles = computed(() => {
  const q = busqueda.value.trim().toLowerCase()
  return (seccion.value?.items ?? []).filter(
    (i) =>
      (filtro.value === 'Todos' || i.estado === filtro.value) &&
      (!q || `${i.titulo} ${i.detalle}`.toLowerCase().includes(q)),
  )
})

function tono(estado: string) {
  if (/^(activ|abiert)/i.test(estado)) return 'ok'
  if (/^invitad/i.test(estado)) return 'pend'
  if (/^cerrad/i.test(estado)) return 'off'
  return ''
}

function iniciales(t: string) {
  return t.split(' ').slice(0, 2).map((p) => p[0]).join('').toUpperCase()
}

function ir(id: string) {
  activa.value = id
  formularioAbierto.value = false
  busqueda.value = ''
  filtro.value = 'Todos'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function abrirFormulario() {
  borrador.value = {
    nombre: '',
    correo: '',
    nombreTienda: '',
    descripcion: '',
    sectorId: '',
    tipoEmprendimientoId: '',
    ubicacion: '',
    fechaInicio: '',
    fechaFin: '',
    tipoCatalogo: 'sectores',
    valor: '',
    clave: '',
  }
  formularioAbierto.value = true
}

function cerrarFormulario() {
  formularioAbierto.value = false
}

function guardarVistaPrevia() {
  formularioAbierto.value = false
  aviso.value = 'Vista previa: no se guardaron ni enviaron datos.'
  clearTimeout(temporizador)
  temporizador = setTimeout(() => (aviso.value = ''), 3500)
}

function proximamente() {
  aviso.value = 'Esta acción estará disponible en la versión final.'
  clearTimeout(temporizador)
  temporizador = setTimeout(() => (aviso.value = ''), 2500)
}
</script>

<template>
  <div class="admin">
    <aside class="lateral">
      <div class="marca">
        <img class="marca-icono" src="/logo-amancay.png" alt="Rincón Amancay" />
        <span class="marca-texto"><small>Rincón</small><strong>Amancay</strong></span>
        <span class="modo">Admin</span>
      </div>
      <nav class="menu" aria-label="Secciones">
        <button type="button" :class="{ on: activa === 'inicio' }" @click="ir('inicio')">
          <span class="mi" aria-hidden="true">🏠</span> Inicio
        </button>
        <p class="menu-titulo">Gestión</p>
        <button
          v-for="s in secciones"
          :key="s.id"
          type="button"
          :class="{ on: activa === s.id }"
          @click="ir(s.id)"
        >
          <span class="mi" aria-hidden="true">{{ s.icono }}</span> {{ s.titulo }}
        </button>
      </nav>
      <div class="pie">
        <img class="pie-avatar" src="/logo-amancay.png" alt="" />
        <span><b>Equipo Amancay</b><small>Modo administrador</small></span>
      </div>
    </aside>

    <main class="contenido">
      <p v-if="errorDatos" class="aviso-error" role="alert">{{ errorDatos }}</p>
      <p v-else-if="cargando" class="aviso-carga" role="status">Cargando datos…</p>

      <template v-if="!seccion">
        <header class="encabezado">
          <p class="sobretitulo">Hola, equipo Amancay</p>
          <h1>Administra la plataforma, paso a paso</h1>
          <p class="sub">Emprendedoras, asociaciones, ferias y catálogos en un solo lugar.</p>
        </header>

        <section class="destacada">
          <div class="destacada-texto">
            <p class="fecha">Hoy, {{ hoy }}</p>
            <h2>¿Qué quieres gestionar hoy?</h2>
            <p>Elige una sección o invita a una nueva emprendedora.</p>
            <button class="boton boton-ambar" type="button" @click="ir('emprendedoras')">
              + Invitar emprendedora
            </button>
          </div>
          <div class="destacada-arte" aria-hidden="true"><img src="/logo-amancay.png" alt="" /></div>
        </section>

        <section class="resumen" aria-label="Resumen">
          <button v-for="r in resumen" :key="r.id" type="button" class="dato" @click="ir(r.id)">
            <span class="dato-num">{{ r.total }}</span>
            <span class="dato-txt">{{ r.titulo }}</span>
          </button>
        </section>

        <h2 class="titulo-bloque">Secciones</h2>
        <div class="rejilla">
          <button v-for="s in secciones" :key="s.id" class="tarjeta" type="button" @click="ir(s.id)">
            <span class="icono" aria-hidden="true">{{ s.icono }}</span>
            <h3>{{ s.titulo }}</h3>
            <p>{{ s.descripcion }}</p>
            <span class="flecha" aria-hidden="true">→</span>
          </button>
        </div>
      </template>

      <template v-else>
        <nav class="migas" aria-label="Ruta">
          <button type="button" @click="ir('inicio')">Inicio</button>
          <span aria-hidden="true">/</span>
          <span>{{ seccion.titulo }}</span>
        </nav>

        <header class="encabezado fila-encabezado">
          <div>
            <p class="sobretitulo">Administración</p>
            <h1>{{ seccion.titulo }}</h1>
            <p class="sub">{{ seccion.descripcion }}</p>
          </div>
          <button class="boton boton-primario" type="button" @click="abrirFormulario">
            {{ seccion.accion }}
          </button>
        </header>

        <section v-if="formularioAbierto" class="formulario" aria-labelledby="titulo-formulario">
          <div class="formulario-encabezado">
            <div>
              <h2 id="titulo-formulario">{{ seccion.accion }}</h2>
              <p>Vista previa: estos datos todavía no se guardan ni se envían.</p>
            </div>
            <button class="editar" type="button" @click="cerrarFormulario">Cancelar</button>
          </div>
          <form class="campos-formulario" @submit.prevent="guardarVistaPrevia">
            <template v-if="seccion.id === 'emprendedoras'">
              <label>Nombre de la emprendedora<input v-model="borrador.nombre" required autocomplete="name" /></label>
              <label>Correo electrónico<input v-model="borrador.correo" type="email" required autocomplete="email" /></label>
              <label>Nombre de la tienda<input v-model="borrador.nombreTienda" required /></label>
              <label>Descripción<textarea v-model="borrador.descripcion" rows="3" /></label>
              <label>Sector
                <select v-model="borrador.sectorId">
                  <option value="">Seleccionar sector</option>
                  <option v-for="opcion in datos.catalogos.sectores" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
                </select>
              </label>
              <label>Tipo de emprendimiento
                <select v-model="borrador.tipoEmprendimientoId">
                  <option value="">Seleccionar tipo</option>
                  <option v-for="opcion in datos.catalogos.tiposEmprendimiento" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
                </select>
              </label>
            </template>
            <template v-else-if="seccion.id === 'administradores'">
              <label>Nombre<input v-model="borrador.nombre" required autocomplete="name" /></label>
              <label>Correo electrónico<input v-model="borrador.correo" type="email" required autocomplete="email" /></label>
            </template>
            <template v-else-if="seccion.id === 'catalogos'">
              <label>Tipo de catálogo
                <select v-model="borrador.tipoCatalogo">
                  <option v-for="tipo in tiposCatalogo" :key="tipo.id" :value="tipo.id">{{ tipo.nombre }}</option>
                </select>
              </label>
              <label>Nombre del valor<input v-model="borrador.valor" required /></label>
            </template>
            <template v-else-if="seccion.id === 'asociaciones'">
              <label>Nombre de la asociación<input v-model="borrador.nombre" required /></label>
              <label>Descripción<textarea v-model="borrador.descripcion" rows="3" /></label>
            </template>
            <template v-else-if="seccion.id === 'ferias'">
              <label>Nombre de la feria<input v-model="borrador.nombre" required /></label>
              <label>Ubicación<input v-model="borrador.ubicacion" /></label>
              <label>Fecha de inicio<input v-model="borrador.fechaInicio" type="date" /></label>
              <label>Fecha de término<input v-model="borrador.fechaFin" type="date" /></label>
              <label>Descripción<textarea v-model="borrador.descripcion" rows="3" /></label>
            </template>
            <template v-else>
              <label>Nombre de la configuración<input v-model="borrador.clave" required /></label>
              <label>Valor<input v-model="borrador.valor" required /></label>
            </template>
            <div class="acciones-formulario">
              <button class="boton boton-primario" type="submit">Guardar (vista previa)</button>
              <button class="editar" type="button" @click="cerrarFormulario">Cerrar</button>
            </div>
          </form>
        </section>

        <div class="herramientas">
          <label class="buscar">
            <span aria-hidden="true">🔎</span>
            <input v-model="busqueda" type="search" placeholder="Buscar en esta sección" />
          </label>
          <div class="chips">
            <button
              v-for="e in estados"
              :key="e"
              type="button"
              class="chip"
              :class="{ on: filtro === e }"
              @click="filtro = e"
            >
              {{ e }}
            </button>
          </div>
        </div>

        <ul v-if="visibles.length" class="lista">
          <li v-for="item in visibles" :key="item.titulo" class="fila">
            <span class="avatar" aria-hidden="true">{{ iniciales(item.titulo) }}</span>
            <div class="fila-texto">
              <b>{{ item.titulo }}</b>
              <small>{{ item.detalle }}</small>
            </div>
            <span class="etiqueta" :class="tono(item.estado)">{{ item.estado }}</span>
            <button class="editar" type="button" @click="proximamente">Editar</button>
          </li>
        </ul>
        <div v-else class="vacio">
          <span aria-hidden="true">{{ seccion.aviso ? '🔒' : '🌼' }}</span>
          <p>{{ seccion.aviso || 'No encontramos resultados.' }}</p>
          <button v-if="!seccion.aviso" class="editar" type="button" @click="(busqueda = ''), (filtro = 'Todos')">
            Limpiar filtros
          </button>
        </div>
      </template>
    </main>

    <div class="aviso" :class="{ visible: aviso }" role="status">{{ aviso }}</div>
  </div>
</template>

<style scoped>
.admin {
  --vino: #75264f;
  --vino-oscuro: #5a1c3c;
  --crema: #fff8ef;
  --crema-2: #fdf1d4;
  --durazno: #f5b183;
  --ambar: #f6c15b;
  --coral: #e96869;
  --rosado: #f7e9e8;
  --tinta: #3a1228;
  --suave: #89727d;
  --borde: #ead9cc;
  --sombra: 0 6px 20px rgba(117, 38, 79, 0.08);

  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  width: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  background: radial-gradient(900px 400px at 100% 0, var(--crema-2), transparent 70%), var(--crema);
  color: var(--tinta);
  font-family: 'Plus Jakarta Sans', system-ui, -apple-system, 'Segoe UI', sans-serif;
}

:global(html),
:global(body) {
  margin: 0;
  min-height: 100%;
}

.admin::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: url('/logo-amancay.png') no-repeat right -4% bottom -6% / min(62vw, 620px);
  opacity: 0.09;
}

.lateral,
.contenido {
  position: relative;
  z-index: 1;
}

button {
  font-family: inherit;
}

button:focus-visible,
input:focus-visible {
  outline: 2px solid var(--vino);
  outline-offset: 2px;
}

/* Lateral */
.lateral {
  position: sticky;
  top: 0;
  align-self: start;
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 1.25rem 1rem;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(6px);
  border-right: 1.5px solid var(--borde);
}

.marca {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 1.5rem;
}

.marca-icono {
  width: 44px;
  height: 44px;
  padding: 6px;
  box-sizing: border-box;
  border-radius: 50%;
  object-fit: contain;
  background: var(--crema-2);
  border: 1.5px solid var(--borde);
}

.marca-texto {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.marca-texto small {
  font-size: 0.7rem;
  color: var(--suave);
}

.marca-texto strong {
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 1.15rem;
  color: var(--vino);
}

.modo {
  margin-left: auto;
  font-size: 0.66rem;
  font-weight: 700;
  color: var(--vino);
  background: var(--rosado);
  border-radius: 999px;
  padding: 0.2rem 0.6rem;
}

.menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  overflow-y: auto;
}

.menu-titulo {
  margin: 1rem 0.9rem 0.3rem;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--suave);
}

.menu button {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.65rem 0.9rem;
  border: 0;
  border-radius: 14px;
  background: none;
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--suave);
  text-align: left;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.mi {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--crema);
  font-size: 0.95rem;
}

.menu button:hover {
  background: var(--crema);
  color: var(--vino);
}

.menu button.on {
  background: var(--rosado);
  color: var(--vino);
}

.menu button.on .mi {
  background: var(--durazno);
}

.menu button.on::before {
  content: '';
  position: absolute;
  left: -1rem;
  top: 20%;
  bottom: 20%;
  width: 4px;
  border-radius: 0 4px 4px 0;
  background: var(--vino);
}

.pie {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  margin-top: 0.8rem;
  padding: 0.7rem;
  background: var(--crema);
  border-radius: 16px;
}

.pie span {
  display: flex;
  flex-direction: column;
  font-size: 0.85rem;
  line-height: 1.2;
}

.pie small {
  color: var(--suave);
  font-size: 0.72rem;
}

.pie-avatar,
.avatar {
  flex: none;
  display: grid;
  place-items: center;
  font-weight: 700;
  color: var(--vino);
  background: var(--durazno);
}

.pie-avatar {
  width: 42px;
  height: 42px;
  padding: 5px;
  box-sizing: border-box;
  border-radius: 50%;
  object-fit: contain;
  background: #fff;
  border: 1.5px solid var(--borde);
}

/* Contenido */
.contenido {
  width: 100%;
  min-width: 0;
  padding: 2.2rem clamp(1.25rem, 3vw, 3.5rem) 4rem;
  box-sizing: border-box;
}

.encabezado {
  margin-bottom: 1.4rem;
}

.fila-encabezado {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.fila-encabezado .sub {
  margin-bottom: 0;
}

.sobretitulo {
  margin: 0 0 0.35rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--vino);
}

h1 {
  margin: 0;
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 2.2rem;
  line-height: 1.12;
  color: var(--vino);
}

.sub {
  margin: 0.45rem 0 0;
  color: var(--suave);
  font-size: 0.96rem;
  max-width: 54ch;
}

.titulo-bloque {
  margin: 1.8rem 0 0.9rem;
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 1.4rem;
  color: var(--vino);
}

.migas {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  font-size: 0.85rem;
  color: var(--suave);
}

.migas button {
  padding: 0;
  background: none;
  border: 0;
  font-size: inherit;
  font-weight: 600;
  color: var(--vino);
  cursor: pointer;
}

.migas span:last-child {
  color: var(--tinta);
  font-weight: 600;
}

/* Botones */
.boton {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  padding: 0.7rem 1.5rem;
  font-size: 0.95rem;
  font-weight: 700;
  border-radius: 999px;
  border: 1.5px solid var(--vino);
  cursor: pointer;
  transition: background 0.15s, transform 0.1s, box-shadow 0.15s;
}

.boton:active {
  transform: scale(0.98);
}

.boton-primario {
  color: #fff;
  background: var(--vino);
  box-shadow: 0 6px 16px rgba(117, 38, 79, 0.25);
}

.boton-primario:hover {
  background: var(--vino-oscuro);
}

.boton-ambar {
  color: var(--tinta);
  background: var(--ambar);
  border-color: var(--ambar);
}

.boton-ambar:hover {
  background: #f9cd75;
}

.formulario {
  margin: 0 0 1.4rem;
  padding: 1.4rem;
  background: rgba(255, 255, 255, 0.94);
  border: 1.5px solid var(--borde);
  border-radius: 22px;
  box-shadow: var(--sombra);
}

.formulario-encabezado {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.formulario-encabezado h2 {
  margin: 0;
  color: var(--vino);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 1.35rem;
  font-weight: 400;
}

.formulario-encabezado p {
  margin: 0.35rem 0 0;
  color: var(--suave);
  font-size: 0.86rem;
}

.campos-formulario {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.campos-formulario label {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  color: var(--tinta);
  font-size: 0.84rem;
  font-weight: 600;
}

.campos-formulario input,
.campos-formulario select,
.campos-formulario textarea {
  width: 100%;
  min-height: 44px;
  padding: 0.65rem 0.8rem;
  box-sizing: border-box;
  color: var(--tinta);
  background: #fff;
  border: 1.5px solid var(--borde);
  border-radius: 12px;
  font: inherit;
}

.campos-formulario textarea {
  resize: vertical;
}

.acciones-formulario {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  grid-column: 1 / -1;
  padding-top: 0.25rem;
}

/* Hero */
.destacada {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.8rem;
  border-radius: 26px;
  background: linear-gradient(135deg, var(--vino) 0%, var(--vino-oscuro) 100%);
  color: var(--crema);
  box-shadow: 0 14px 30px rgba(117, 38, 79, 0.28);
}

.destacada::before,
.destacada::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.destacada::before {
  width: 260px;
  height: 260px;
  right: -60px;
  top: -90px;
  background: rgba(245, 177, 131, 0.16);
}

.destacada::after {
  width: 120px;
  height: 120px;
  right: 150px;
  bottom: -60px;
  background: rgba(246, 193, 91, 0.14);
}

.destacada-texto {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.4rem;
}

.fecha {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--durazno);
}

.destacada h2 {
  margin: 0;
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 1.9rem;
  line-height: 1.12;
}

.destacada-texto p:not(.fecha) {
  margin: 0 0 0.9rem;
  opacity: 0.85;
}

.destacada-arte {
  position: relative;
  z-index: 1;
  flex: none;
  width: 130px;
  height: 130px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--crema);
  box-shadow: 0 0 0 10px rgba(245, 177, 131, 0.25);
}

.destacada-arte img {
  width: 100px;
  height: 100px;
  object-fit: contain;
}

/* Resumen */
.resumen {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.8rem;
  margin-top: 1rem;
}

.dato {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.1rem;
  padding: 0.9rem 1rem;
  background: #fff;
  border: 1.5px solid var(--borde);
  border-radius: 18px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s;
}

.dato:hover {
  border-color: var(--vino);
}

.dato-num {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 1.9rem;
  line-height: 1;
  color: var(--vino);
}

.dato-txt {
  font-size: 0.8rem;
  color: var(--suave);
  font-weight: 600;
}

/* Tarjetas */
.rejilla {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.tarjeta {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.35rem;
  padding: 1.2rem;
  background: #fff;
  border: 1.5px solid var(--borde);
  border-radius: 22px;
  text-align: left;
  color: inherit;
  cursor: pointer;
  transition: border-color 0.15s, transform 0.15s, box-shadow 0.15s;
}

.tarjeta:hover {
  border-color: var(--vino);
  transform: translateY(-3px);
  box-shadow: var(--sombra);
}

.icono {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 15px;
  background: var(--durazno);
  font-size: 1.35rem;
}

.tarjeta h3 {
  margin: 0.3rem 0 0;
  font-size: 1.02rem;
  color: var(--vino);
}

.tarjeta p {
  margin: 0;
  padding-right: 1.5rem;
  font-size: 0.88rem;
  line-height: 1.5;
  color: var(--suave);
}

.flecha {
  position: absolute;
  right: 1.1rem;
  bottom: 1rem;
  font-weight: 700;
  color: var(--vino);
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.15s, transform 0.15s;
}

.tarjeta:hover .flecha {
  opacity: 1;
  transform: none;
}

/* Herramientas y lista */
.herramientas {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.buscar {
  flex: 1;
  min-width: 220px;
  max-width: 420px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0 1rem;
  background: #fff;
  border: 1.5px solid var(--borde);
  border-radius: 999px;
}

.buscar:focus-within {
  border-color: var(--vino);
}

.buscar input {
  flex: 1;
  min-width: 0;
  padding: 0.7rem 0;
  border: 0;
  outline: 0;
  background: none;
  font: inherit;
  font-size: 0.92rem;
  color: var(--tinta);
}

.chips {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.chip {
  padding: 0.5rem 1rem;
  border-radius: 999px;
  border: 1.5px solid var(--borde);
  background: #fff;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--tinta);
  cursor: pointer;
}

.chip.on {
  background: var(--vino);
  border-color: var(--vino);
  color: #fff;
}

.lista {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.fila {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  padding: 0.8rem 1rem 0.8rem 0.8rem;
  background: #fff;
  border: 1.5px solid var(--borde);
  border-radius: 20px;
  transition: box-shadow 0.15s, border-color 0.15s;
}

.fila:hover {
  border-color: var(--durazno);
  box-shadow: var(--sombra);
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 15px;
  font-size: 0.95rem;
}

.fila-texto {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.fila-texto b {
  color: var(--vino);
}

.fila-texto small {
  color: var(--suave);
}

.etiqueta {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--vino);
  background: var(--rosado);
  border-radius: 999px;
  padding: 0.3rem 0.8rem;
  white-space: nowrap;
}

.etiqueta::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--suave);
}

.etiqueta.ok {
  background: #e8f1e4;
  color: #2f5d2b;
}

.etiqueta.ok::before {
  background: #4c9a45;
}

.etiqueta.pend {
  background: var(--crema-2);
  color: #8a5a00;
}

.etiqueta.pend::before {
  background: var(--ambar);
}

.etiqueta.off::before {
  background: #b9a5ad;
}

.editar {
  padding: 0.5rem 1.1rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--vino);
  background: transparent;
  border: 1.5px solid var(--vino);
  border-radius: 999px;
  cursor: pointer;
}

.editar:hover {
  background: var(--rosado);
}

.vacio {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  padding: 2.5rem 1rem;
  background: #fff;
  border: 1.5px dashed var(--borde);
  border-radius: 22px;
  color: var(--suave);
}

.vacio span {
  font-size: 2.2rem;
}

.vacio p {
  margin: 0;
}

.aviso {
  position: fixed;
  left: 50%;
  bottom: 1.5rem;
  transform: translate(-50%, 8px);
  padding: 0.8rem 1.2rem;
  background: var(--vino);
  color: #fff;
  border-radius: 14px;
  font-size: 0.9rem;
  box-shadow: 0 10px 24px rgba(58, 18, 40, 0.3);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s, transform 0.2s;
}

.aviso.visible {
  opacity: 1;
  transform: translate(-50%, 0);
}

.aviso-error,
.aviso-carga {
  margin: 0 0 1rem;
  padding: 0.7rem 1rem;
  border-radius: 14px;
  font-size: 0.88rem;
  font-weight: 600;
}

.aviso-error {
  background: var(--rosado, #f7e9e8);
  color: #b23b3c;
}

.aviso-carga {
  background: var(--crema-2, #fdf1d4);
  color: #8a5a00;
}

@media (prefers-reduced-motion: reduce) {
  * {
    transition: none !important;
  }
}

@media (max-width: 860px) {
  .admin {
    grid-template-columns: minmax(0, 1fr);
  }

  .admin::before {
    background-size: 90vw;
    background-position: center bottom -8%;
  }

  .lateral {
    position: static;
    height: auto;
    border-right: 0;
    border-bottom: 1.5px solid var(--borde);
    padding: 0.9rem 1rem 0.6rem;
  }

  .campos-formulario {
    grid-template-columns: minmax(0, 1fr);
  }

  .marca {
    margin-bottom: 0.7rem;
  }

  .menu {
    flex-direction: row;
    overflow-x: auto;
    padding-bottom: 0.3rem;
  }

  .menu-titulo,
  .pie {
    display: none;
  }

  .menu button {
    white-space: nowrap;
  }

  .menu button.on::before {
    display: none;
  }

  .contenido {
    padding: 1.25rem 1rem 3rem;
  }

  h1 {
    font-size: 1.7rem;
  }

  .destacada-arte {
    display: none;
  }

  .resumen {
    grid-template-columns: repeat(2, 1fr);
  }

  .fila {
    flex-wrap: wrap;
  }
}
</style>
