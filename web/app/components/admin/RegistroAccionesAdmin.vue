<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { listarAuditoriaAdministracion } from '~/repositories/admin'
import type { AccionAuditoriaAdmin, AreaAuditoriaAdmin, CursorAuditoriaAdmin } from '~/types/admin'

const { $amancay } = useNuxtApp()
const acciones = ref<AccionAuditoriaAdmin[]>([])
const cursor = ref<CursorAuditoriaAdmin | null>(null)
const busqueda = ref('')
const area = ref<'Todas' | AreaAuditoriaAdmin>('Todas')
const cargando = ref(false)
const cargandoMas = ref(false)
const error = ref('')
let revision = 0

const visibles = computed(() => {
  const texto = busqueda.value.trim().toLocaleLowerCase('es-CL')
  return acciones.value.filter((item) => (area.value === 'Todas' || item.area === area.value)
    && (!texto || `${item.actorUid} ${item.accion} ${item.objetivo} ${item.area}`.toLocaleLowerCase('es-CL').includes(texto)))
})

function fechaLegible(value: string) {
  return new Intl.DateTimeFormat('es-CL', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'America/Santiago',
  }).format(new Date(value))
}

async function cargar(reiniciar = false) {
  if (cargando.value || cargandoMas.value) return
  const idRevision = ++revision
  error.value = ''
  if (reiniciar) {
    cargando.value = true
    cursor.value = null
  } else {
    cargandoMas.value = true
  }
  try {
    const pagina = await listarAuditoriaAdministracion($amancay, reiniciar ? null : cursor.value)
    if (idRevision !== revision) return
    acciones.value = reiniciar ? pagina.acciones : [...acciones.value, ...pagina.acciones]
    cursor.value = pagina.siguiente
  } catch (cause) {
    if (idRevision === revision) error.value = cause instanceof Error ? cause.message : 'No se pudo cargar el registro de acciones.'
  } finally {
    if (idRevision === revision) {
      cargando.value = false
      cargandoMas.value = false
    }
  }
}

onMounted(() => { void cargar(true) })
onBeforeUnmount(() => { revision++ })
</script>

<template>
  <section class="registro-acciones" aria-label="Registro de acciones administrativas">
    <div class="registro-intro">
      <div class="registro-sello" aria-hidden="true">📋</div>
      <div>
        <p class="registro-sobretitulo">Trazabilidad administrativa</p>
        <h2>Acciones recientes</h2>
        <p>Consulta quién hizo cada cambio en cuentas, asociaciones y ferias, y cuándo ocurrió.</p>
      </div>
      <button class="registro-actualizar" type="button" :disabled="cargando || cargandoMas" @click="cargar(true)">
        {{ cargando ? 'Actualizando…' : 'Actualizar registro' }}
      </button>
    </div>

    <div class="registro-herramientas">
      <label class="registro-buscar">
        <span aria-hidden="true">⌕</span>
        <input v-model="busqueda" type="search" placeholder="Buscar acción, administrador o registro" />
      </label>
      <label class="registro-filtro">
        <span>Área</span>
        <select v-model="area">
          <option value="Todas">Todas las áreas</option>
          <option value="Cuentas">Cuentas</option>
          <option value="Asociaciones">Asociaciones</option>
          <option value="Ferias y eventos">Ferias y eventos</option>
        </select>
      </label>
    </div>

    <p v-if="error" class="registro-error" role="alert">{{ error }}</p>
    <p v-if="cargando && !acciones.length" class="registro-estado" role="status">Cargando acciones…</p>

    <ol v-if="visibles.length" class="registro-lista">
      <li v-for="item in visibles" :key="item.id" class="registro-fila">
        <span class="registro-icono" aria-hidden="true">{{ item.area === 'Cuentas' ? '👤' : item.area === 'Asociaciones' ? '🤝' : '🎪' }}</span>
        <div class="registro-contenido">
          <div class="registro-titulo">
            <strong>{{ item.accion }}</strong>
            <span class="registro-area">{{ item.area }}</span>
          </div>
          <span class="registro-objetivo">{{ item.objetivo }}</span>
          <small class="registro-autor">Por {{ item.actorNombre || 'una cuenta administradora' }}</small>
        </div>
        <time class="registro-fecha" :datetime="item.creadoEn">{{ fechaLegible(item.creadoEn) }}</time>
      </li>
    </ol>

    <div v-else-if="!cargando && !error" class="registro-vacio">
      <span aria-hidden="true">🌼</span>
      <p>{{ acciones.length ? 'No hay acciones que coincidan con la búsqueda.' : 'Todavía no hay acciones registradas.' }}</p>
    </div>

    <div class="registro-pie">
      <span>{{ acciones.length }} {{ acciones.length === 1 ? 'acción cargada' : 'acciones cargadas' }}</span>
      <button v-if="cursor" class="registro-mas" type="button" :disabled="cargandoMas" @click="cargar()">
        {{ cargandoMas ? 'Cargando…' : 'Cargar acciones anteriores' }}
      </button>
    </div>
    <p class="registro-nota">El historial muestra acciones administrativas nuevas desde que se habilitó el registro. Los cambios anteriores que no se auditaron no se pueden reconstruir.</p>
  </section>
</template>

<style scoped>
.registro-acciones {
  display: grid;
  gap: 1.15rem;
  color: var(--tinta);
}
.registro-intro {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
  border: 1.5px solid var(--borde);
  border-radius: 22px;
  background: rgba(255, 255, 255, .84);
}
.registro-sello, .registro-icono {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 15px;
  background: var(--durazno);
  font-size: 1.35rem;
}
.registro-intro > div:nth-child(2) { min-width: 0; flex: 1; }
.registro-sobretitulo {
  margin: 0 0 .25rem;
  color: var(--vino);
  font-size: .76rem;
  font-weight: 800;
  letter-spacing: .11em;
  text-transform: uppercase;
}
.registro-intro h2 { margin: 0; color: var(--vino); font: 700 1.45rem 'DM Serif Display', Georgia, serif; }
.registro-intro p:last-child { margin: .3rem 0 0; color: var(--suave); line-height: 1.5; }
.registro-actualizar, .registro-mas {
  min-height: 44px;
  padding: .65rem 1rem;
  border: 1.5px solid var(--borde);
  border-radius: 999px;
  background: var(--crema);
  color: var(--vino);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.registro-actualizar:hover, .registro-mas:hover { border-color: var(--vino); background: var(--crema-2); }
.registro-actualizar:focus-visible, .registro-mas:focus-visible, .registro-buscar input:focus-visible, .registro-filtro select:focus-visible { outline: 2px solid var(--vino); outline-offset: 2px; }
.registro-herramientas { display: flex; flex-wrap: wrap; align-items: end; gap: .8rem; }
.registro-buscar {
  display: flex;
  align-items: center;
  flex: 1 1 300px;
  gap: .65rem;
  min-height: 48px;
  padding: 0 .9rem;
  border: 1.5px solid var(--borde);
  border-radius: 14px;
  background: rgba(255, 255, 255, .88);
  color: var(--vino);
  font-size: 1.35rem;
}
.registro-buscar input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--tinta); font: inherit; font-size: .95rem; }
.registro-buscar input:focus-visible { outline: 0; }
.registro-filtro { display: grid; gap: .3rem; min-width: 190px; color: var(--suave); font-size: .8rem; font-weight: 700; }
.registro-filtro select { min-height: 48px; padding: 0 .8rem; border: 1.5px solid var(--borde); border-radius: 14px; background: rgba(255, 255, 255, .88); color: var(--tinta); font: inherit; }
.registro-lista { display: grid; gap: .65rem; margin: 0; padding: 0; list-style: none; }
.registro-fila {
  display: flex;
  align-items: center;
  gap: .9rem;
  min-width: 0;
  padding: .9rem 1rem;
  border: 1.5px solid var(--borde);
  border-radius: 18px;
  background: rgba(255, 255, 255, .88);
}
.registro-icono { width: 42px; height: 42px; border-radius: 13px; font-size: 1.15rem; }
.registro-contenido { display: grid; flex: 1; min-width: 0; gap: .2rem; }
.registro-titulo { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; }
.registro-titulo strong { color: var(--tinta); }
.registro-area { padding: .22rem .55rem; border-radius: 999px; background: var(--rosado); color: var(--vino); font-size: .73rem; font-weight: 700; }
.registro-objetivo, .registro-autor { overflow-wrap: anywhere; color: var(--suave); }
.registro-autor { font-size: .78rem; }
.registro-fecha { flex: 0 0 auto; color: var(--suave); font-size: .82rem; text-align: right; }
.registro-estado, .registro-error, .registro-vacio { margin: 0; padding: 1.2rem; border-radius: 16px; background: rgba(255, 255, 255, .82); text-align: center; }
.registro-error { color: #a83237; background: #fff0ed; }
.registro-vacio { display: grid; justify-items: center; gap: .35rem; color: var(--suave); }
.registro-vacio span { font-size: 1.5rem; }
.registro-vacio p { margin: 0; }
.registro-pie { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .75rem; color: var(--suave); font-size: .85rem; }
.registro-nota { margin: -.45rem 0 0; color: var(--suave); font-size: .82rem; line-height: 1.5; }
button:disabled { opacity: .55; cursor: not-allowed; }
@media (max-width: 680px) {
  .registro-intro { align-items: flex-start; flex-wrap: wrap; }
  .registro-sello { width: 42px; height: 42px; }
  .registro-intro > div:nth-child(2) { flex-basis: calc(100% - 60px); }
  .registro-actualizar { margin-left: 3.4rem; }
  .registro-filtro { flex: 1 1 100%; }
  .registro-fila { align-items: flex-start; flex-wrap: wrap; }
  .registro-contenido { flex-basis: calc(100% - 58px); }
  .registro-fecha { margin-left: 3.4rem; text-align: left; }
}
</style>
