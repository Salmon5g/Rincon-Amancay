<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import DialogoAyudaWeb from '~/components/DialogoAyudaWeb.vue'
import GuiaSpotlightWeb from '~/components/GuiaSpotlightWeb.vue'
import type { OpcionAyudaWeb, PasoGuiaWeb } from '~/types/ayuda-web'

type PasoAdmin = PasoGuiaWeb & {
  preparar?: () => void | Promise<void>
  alternativo?: string
}

const props = defineProps<{
  autorizado: boolean
  listo: boolean
  correo: string
  seccionActual: string
  navegar: (id: string) => Promise<void>
}>()

const ayudaAbierta = ref(false)
const guiaActiva = ref(false)
const guiaTematica = ref(false)
const buscandoPaso = ref(false)
const pasoActual = ref(0)
const pasosActivos = ref<PasoAdmin[]>([])
let revisionInicio = 0

const tutoriales = computed<Record<string, PasoAdmin[]>>(() => ({
  inicio: [
    { id: 'inicio', objetivo: '[data-guia="admin-inicio"]', titulo: 'Este es tu panel', explicacion: 'Desde aquí puedes entrar a cada área de administración y revisar lo más importante.', preparar: () => props.navegar('inicio') },
    { id: 'resumen', objetivo: '[data-guia="admin-resumen"]', titulo: 'Un resumen de la plataforma', explicacion: 'Estas cifras muestran emprendedoras, asociaciones, ferias y catálogos. Toca una tarjeta para abrir esa sección.' },
    { id: 'secciones', objetivo: '[data-guia="admin-secciones"]', titulo: 'Accesos a cada tarea', explicacion: 'También puedes elegir una tarjeta para ir directamente al área que quieres gestionar.' },
  ],
  emprendedoras: [
    { id: 'emprendedoras-inicio', objetivo: '[data-guia="admin-contenido-emprendedoras"]', titulo: 'Cuentas emprendedoras', explicacion: 'Aquí revisas las cuentas y las invitaciones. Puedes crear un acceso o enviar una invitación para que la emprendedora defina su contraseña.', preparar: () => props.navegar('emprendedoras') },
    { id: 'emprendedoras-alta', objetivo: '[data-guia="admin-accion-emprendedoras"]', titulo: 'Crear o invitar', explicacion: 'Usa este botón para registrar una cuenta o preparar una invitación. El formulario explica qué datos necesitas.' },
    { id: 'emprendedoras-buscar', objetivo: '[data-guia="admin-herramientas-emprendedoras"]', titulo: 'Encuentra una cuenta', explicacion: 'Busca por nombre o correo y filtra por estado. En cada fila aparecen las acciones disponibles para esa cuenta.' },
  ],
  asociaciones: [
    { id: 'asociaciones-inicio', objetivo: '[data-guia="admin-contenido-asociaciones"]', titulo: 'Asociaciones de la comunidad', explicacion: 'Consulta sus fichas, estado e integrantes. Los registros históricos se conservan si una asociación se desactiva.', preparar: () => props.navegar('asociaciones') },
    { id: 'asociaciones-crear', objetivo: '[data-guia="admin-accion-asociaciones"]', alternativo: '[data-guia="admin-contenido-asociaciones"]', titulo: 'Crear o editar una asociación', explicacion: 'Si el panel permite cambios, usa el botón de la esquina superior para completar la ficha pública y los datos de contacto.' },
    { id: 'asociaciones-lista', objetivo: '[data-guia="admin-herramientas-asociaciones"]', titulo: 'Busca y gestiona integrantes', explicacion: 'Busca una asociación y usa «Integrantes» para agregar o retirar emprendedoras.' },
  ],
  ferias: [
    { id: 'ferias-inicio', objetivo: '[data-guia="admin-ferias-intro"]', titulo: 'Ferias y eventos', explicacion: 'Aquí organizas fechas, lugar, imagen y participantes. Las invitaciones pueden llegar a las emprendedoras como avisos.', preparar: () => props.navegar('ferias') },
    { id: 'ferias-crear', objetivo: '[data-guia="admin-ferias-acciones"]', titulo: 'Crea y administra ferias', explicacion: 'Crea un borrador, completa el mapa y los horarios, y publica la feria cuando esté lista. En cada feria puedes gestionar participantes.' },
  ],
  'registro-acciones': [
    { id: 'acciones-inicio', objetivo: '[data-guia="admin-registro-intro"]', titulo: 'Registro de acciones', explicacion: 'Este historial ayuda a revisar quién hizo cambios en cuentas, asociaciones o ferias y cuándo.', preparar: () => props.navegar('registro-acciones') },
    { id: 'acciones-filtros', objetivo: '[data-guia="admin-registro-herramientas"]', titulo: 'Busca un cambio', explicacion: 'Escribe un nombre o elige un área para acotar la lista. Puedes actualizarla para ver las acciones más recientes.' },
  ],
  catalogos: [
    { id: 'catalogos-inicio', objetivo: '[data-guia="admin-contenido-catalogos"]', titulo: 'Catálogos de la plataforma', explicacion: 'Estos valores alimentan los formularios de las tiendas y productos. Desactivar un valor evita su uso nuevo, pero conserva los registros existentes.', preparar: () => props.navegar('catalogos') },
    { id: 'catalogos-alta', objetivo: '[data-guia="admin-accion-catalogos"]', alternativo: '[data-guia="admin-contenido-catalogos"]', titulo: 'Agrega o edita valores', explicacion: 'Si el panel permite cambios, usa el botón de la esquina superior para añadir un sector, categoría o tipo. Los nombres deben ser únicos dentro del catálogo.' },
    { id: 'catalogos-tipo', objetivo: '[data-guia="admin-catalogo-tipo"]', titulo: 'Elige qué catálogo revisar', explicacion: 'Selecciona sectores, categorías o tipos para ver solo esos valores.' },
    { id: 'catalogos-buscar', objetivo: '[data-guia="admin-herramientas-catalogos"]', titulo: 'Busca y filtra valores', explicacion: 'Puedes buscar por nombre y separar valores activos de los inactivos.' },
  ],
  configuracion: [
    { id: 'configuracion-inicio', objetivo: '[data-guia="admin-config-cabecera"]', titulo: 'Configuración general', explicacion: 'Aquí eliges cómo podrán entrar las personas compradoras a la vitrina.', preparar: () => props.navegar('configuracion') },
    { id: 'configuracion-opciones', objetivo: '[data-guia="admin-config-opciones"]', alternativo: '[data-guia="admin-config-cabecera"]', titulo: 'Elige la forma de acceso', explicacion: 'Puedes permitir explorar libremente, sugerir iniciar sesión o solicitar una cuenta. La preferencia se comparte con la app.' },
  ],
  administradores: [
    { id: 'administradores-inicio', objetivo: '[data-guia="admin-cuentas-contenido"]', titulo: 'Cuentas administradoras', explicacion: 'Aquí asignas o retiras el rol administrador a cuentas existentes. No se crean ni se cambian contraseñas desde esta sección.', preparar: () => props.navegar('administradores') },
    { id: 'administradores-asignar', objetivo: '[data-guia="admin-cuentas-formulario"]', titulo: 'Asigna un rol con cuidado', explicacion: 'La persona debe tener una cuenta registrada y el correo verificado. El panel conserva al menos una cuenta administradora activa.' },
    { id: 'administradores-lista', objetivo: '[data-guia="admin-cuentas-listado"]', titulo: 'Revisa las cuentas con permisos', explicacion: 'Actualiza el listado y retira el rol solo cuando corresponda. Tu propia cuenta no se puede quitar desde aquí.' },
  ],
}))

const pasosCompletos = computed<PasoAdmin[]>(() => [
  ...tutoriales.value.inicio,
  tutoriales.value.emprendedoras[0]!,
  tutoriales.value.asociaciones[0]!,
  tutoriales.value.ferias[0]!,
  tutoriales.value['registro-acciones'][0]!,
  tutoriales.value.catalogos[0]!,
  tutoriales.value.configuracion[0]!,
  tutoriales.value.administradores[0]!,
  { id: 'ayuda', objetivo: '[data-guia="admin-ayuda"]', titulo: 'La ayuda queda a mano', explicacion: 'Pulsa «?» cuando quieras repetir un tutorial de una sección o recorrer el panel completo.' },
])

const paso = computed(() => pasosActivos.value[pasoActual.value] ?? null)
const nombresSecciones: Record<string, string> = {
  inicio: 'Inicio', emprendedoras: 'Emprendedoras', asociaciones: 'Asociaciones', ferias: 'Ferias y eventos',
  'registro-acciones': 'Registro de acciones', catalogos: 'Catálogos', configuracion: 'Configuración general', administradores: 'Administradores',
}
const tituloSeccion = computed(() => nombresSecciones[props.seccionActual] ?? 'Administración')
const opcionesAyuda = computed<OpcionAyudaWeb[]>(() => [
  { id: 'inicio', titulo: 'Inicio y resumen', icono: '⌂' },
  { id: 'emprendedoras', titulo: 'Cuentas emprendedoras', icono: '♙' },
  { id: 'asociaciones', titulo: 'Asociaciones', icono: '♧' },
  { id: 'ferias', titulo: 'Ferias y eventos', icono: '▦' },
  { id: 'registro-acciones', titulo: 'Registro de acciones', icono: '▤' },
  { id: 'catalogos', titulo: 'Catálogos', icono: '▦' },
  { id: 'configuracion', titulo: 'Configuración general', icono: '⚙' },
  { id: 'administradores', titulo: 'Cuentas administradoras', icono: '♙' },
])

const descripcionAyuda = computed(() => props.seccionActual === 'inicio'
  ? 'En Inicio puedes revisar el resumen y elegir un área de la plataforma.'
  : `En ${tituloSeccion.value} puedes consultar y gestionar esta parte de la plataforma.`)

function claveGuia() {
  const cuenta = props.correo.trim().toLocaleLowerCase('es-CL') || 'administracion'
  return `amancay:guia-web:admin:${encodeURIComponent(cuenta)}`
}

async function mostrarPaso(indice: number) {
  pasoActual.value = indice
  buscandoPaso.value = true
  await paso.value?.preparar?.()
  await nextTick()
  const actual = paso.value
  if (actual && actual.alternativo) {
    const visible = (selector: string) => Array.from(document.querySelectorAll(selector)).some((elemento) => {
      const estilo = window.getComputedStyle(elemento)
      return estilo.display !== 'none' && estilo.visibility !== 'hidden' && elemento.getClientRects().length > 0
    })
    if (!visible(actual.objetivo) && visible(actual.alternativo)) {
      pasosActivos.value[indice] = { ...actual, objetivo: actual.alternativo }
    }
  }
  buscandoPaso.value = false
}

async function iniciarGuia(id?: string) {
  ayudaAbierta.value = false
  guiaTematica.value = Boolean(id)
  pasosActivos.value = [...(id ? (tutoriales.value[id] ?? []) : pasosCompletos.value)]
  if (!pasosActivos.value.length) { guiaTematica.value = false; return }
  guiaActiva.value = true
  await mostrarPaso(0)
}

function terminarGuia(completada: boolean) {
  guiaActiva.value = false
  buscandoPaso.value = false
  if (completada && !guiaTematica.value) {
    try { localStorage.setItem(claveGuia(), 'completa') } catch { /* La guía podrá volver a aparecer en otra visita. */ }
  }
  guiaTematica.value = false
}

async function continuarGuia() {
  if (paso.value?.id === 'ayuda') {
    terminarGuia(true)
    ayudaAbierta.value = true
    return
  }
  const siguiente = pasoActual.value + 1
  if (siguiente >= pasosActivos.value.length) {
    terminarGuia(!guiaTematica.value)
    return
  }
  await mostrarPaso(siguiente)
}

let temporizadorInicio: ReturnType<typeof setTimeout> | undefined
watch(() => [props.autorizado, props.listo, props.correo] as const, ([autorizado, listo]) => {
  const revision = ++revisionInicio
  clearTimeout(temporizadorInicio)
  if (!autorizado || !listo) {
    guiaActiva.value = false
    ayudaAbierta.value = false
    return
  }
  try { if (localStorage.getItem(claveGuia()) === 'completa') return } catch { /* La guía sigue disponible desde «?». */ }
  temporizadorInicio = setTimeout(() => {
    if (revision !== revisionInicio || !props.autorizado || !props.listo || guiaActiva.value || ayudaAbierta.value) return
    void iniciarGuia()
  }, 460)
}, { immediate: true })

onBeforeUnmount(() => {
  revisionInicio++
  clearTimeout(temporizadorInicio)
})
</script>

<template>
  <template v-if="autorizado">
    <button class="ayuda-admin" type="button" data-guia="admin-ayuda" aria-label="Ayuda y guía de administración" title="Ayuda" @click="ayudaAbierta = true">?</button>
    <DialogoAyudaWeb
      :abierto="ayudaAbierta"
      :titulo="`Ayuda de ${tituloSeccion}`"
      :descripcion="descripcionAyuda"
      :opciones="opcionesAyuda"
      @cerrar="ayudaAbierta = false"
      @elegir="id => iniciarGuia(id)"
      @recorrido-completo="iniciarGuia()"
    />
    <GuiaSpotlightWeb
      :activo="guiaActiva"
      :paso="buscandoPaso ? null : paso"
      :numero="pasoActual + 1"
      :total="pasosActivos.length"
      :titulo-recorrido="guiaTematica ? 'Tutorial de administración' : 'Guía de administración'"
      @continuar="continuarGuia"
      @cerrar="terminarGuia(!guiaTematica)"
    />
  </template>
</template>

<style scoped>
.ayuda-admin{display:grid;place-items:center;flex:none;width:44px;height:44px;padding:0;border:1.5px solid #ead9cc;border-radius:50%;background:#fff;color:#75264f;font:700 1.25rem 'Plus Jakarta Sans',system-ui,sans-serif;cursor:pointer;transition:background .15s,border-color .15s,transform .15s}
.ayuda-admin:hover,.ayuda-admin:focus-visible{border-color:#75264f;background:#f7e9e8;outline:3px solid rgba(117,38,79,.16);outline-offset:2px}
@media(prefers-reduced-motion:reduce){.ayuda-admin{transition:none}}
</style>
