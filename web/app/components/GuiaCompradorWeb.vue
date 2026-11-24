<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { OpcionAyudaWeb, PasoGuiaWeb } from '~/types/ayuda-web'

const props = defineProps<{ conSesion: boolean; listo: boolean; bloqueado: boolean }>()
const ruta = useRoute()
const ayudaAbierta = ref(false)
const guiaActiva = ref(false)
const guiaIndividual = ref(false)
const pasoActual = ref(0)
const pasosActivos = ref<PasoGuiaWeb[]>([])
const espera = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

const pasos = computed<PasoGuiaWeb[]>(() => {
  const resultado: PasoGuiaWeb[] = [
    { id: 'inicio', objetivo: '[data-guia="comprador-inicio"]', titulo: 'Tu inicio', explicacion: 'En la portada puedes descubrir tiendas, productos destacados y próximas ferias.' },
    { id: 'productos', objetivo: '[data-guia="comprador-productos"]', titulo: 'Busca productos', explicacion: 'Aquí puedes buscar y filtrar productos por categoría o tienda.' },
    { id: 'tiendas', objetivo: '[data-guia="comprador-tiendas"]', titulo: 'Conoce las tiendas', explicacion: 'En esta sección encontrarás emprendimientos y podrás abrir sus catálogos.' },
    { id: 'directorio', objetivo: '[data-guia="comprador-directorio"]', titulo: 'Explora el directorio', explicacion: 'Aquí aparecen emprendedoras y asociaciones de la comunidad.' },
    { id: 'ferias', objetivo: '[data-guia="comprador-ferias"]', titulo: 'Encuentra ferias', explicacion: 'Revisa fechas, lugares y el calendario de ferias.' },
  ]
  if (props.conSesion) {
    resultado.push(
      { id: 'avisos', objetivo: '[data-guia="comprador-avisos"]', titulo: 'Activa tus avisos', explicacion: 'Puedes recibir avisos cuando una tienda o asociación que sigues confirme que participará en una feria.' },
      { id: 'cuenta', objetivo: '[data-guia="comprador-cuenta"]', titulo: 'Tu cuenta', explicacion: 'Desde aquí puedes revisar tu cuenta y cerrar sesión.' },
    )
  } else {
    resultado.push({ id: 'cuenta', objetivo: '[data-guia="comprador-cuenta"]', titulo: 'Inicia sesión cuando quieras', explicacion: 'Puedes recorrer la vitrina sin cuenta. Al iniciar sesión podrás seguir tiendas y activar avisos.' })
  }
  resultado.push({ id: 'ayuda', objetivo: '[data-guia="comprador-ayuda"]', titulo: 'La ayuda está aquí', explicacion: 'Toca «?» para repetir un tutorial específico o recorrer la web completa.' })
  return resultado
})

const paso = computed(() => pasosActivos.value[pasoActual.value] ?? null)
const tituloAyuda = computed(() => {
  if (ruta.path.includes('/ferias')) return 'Ferias'
  if (ruta.path.includes('/directorio')) return 'Directorio'
  if (ruta.path.includes('/tiendas')) return 'Tiendas'
  if (ruta.path.includes('/productos')) return 'Productos'
  return 'Inicio'
})
const descripcionAyuda = computed(() => {
  if (ruta.path.includes('/ferias')) return 'En Ferias puedes revisar fechas, lugares, detalles y el calendario.'
  if (ruta.path.includes('/directorio')) return 'El Directorio te acerca a las emprendedoras y asociaciones de la comunidad.'
  if (ruta.path.includes('/tiendas')) return 'En Tiendas puedes encontrar emprendimientos y visitar sus catálogos.'
  if (ruta.path.includes('/productos')) return 'En Productos puedes buscar y filtrar artículos publicados por las tiendas.'
  return 'En Inicio puedes descubrir productos, tiendas y próximas ferias.'
})
const opcionesAyuda = computed<OpcionAyudaWeb[]>(() => {
  const opciones = [
    { id: 'inicio', titulo: 'Inicio y portada', icono: '⌂' },
    { id: 'productos', titulo: 'Buscar productos', icono: '⌕' },
    { id: 'tiendas', titulo: 'Tiendas', icono: '▤' },
    { id: 'directorio', titulo: 'Directorio local', icono: '♧' },
    { id: 'ferias', titulo: 'Ferias', icono: '▦' },
  ]
  if (props.conSesion) opciones.push(
    { id: 'avisos', titulo: 'Avisos de ferias', icono: '♢' },
    { id: 'cuenta', titulo: 'Mi sesión', icono: '◉' },
  )
  else opciones.push({ id: 'cuenta', titulo: 'Iniciar sesión', icono: '◉' })
  return opciones
})

function claveGuia() { return `amancay:guia-web:comprador:${props.conSesion ? 'con-sesion' : 'sin-sesion'}` }
function rutaPaso(id: string) {
  if (id === 'productos') return '/comprador/productos'
  if (id === 'avisos') return '/comprador'
  if (id === 'tiendas') return '/comprador/tiendas'
  if (id === 'directorio') return '/comprador/directorio'
  if (id === 'ferias') return '/comprador/ferias'
  return '/comprador'
}

async function mostrarPaso(indice: number) {
  pasoActual.value = indice
  const destino = rutaPaso(pasosActivos.value[indice]?.id ?? '')
  if (ruta.path !== destino) await navigateTo(destino)
}

async function iniciarRecorrido(id?: string) {
  ayudaAbierta.value = false
  pasosActivos.value = pasos.value
  guiaIndividual.value = Boolean(id)
  guiaActiva.value = true
  const indice = id ? pasosActivos.value.findIndex(item => item.id === id) : 0
  if (indice < 0) { guiaActiva.value = false; return }
  await mostrarPaso(indice)
}

async function finalizarRecorrido(completado: boolean) {
  guiaActiva.value = false
  if (completado && !guiaIndividual.value) {
    try { localStorage.setItem(claveGuia(), 'completa') } catch { /* La guía puede volver a aparecer en otra visita. */ }
  }
  guiaIndividual.value = false
}

async function continuar() {
  const id = paso.value?.id
  if (guiaIndividual.value) { await finalizarRecorrido(false); return }
  if (id === 'ayuda') {
    await finalizarRecorrido(true)
    ayudaAbierta.value = true
    return
  }
  const siguiente = pasoActual.value + 1
  if (siguiente >= pasosActivos.value.length) { await finalizarRecorrido(true); return }
  await mostrarPaso(siguiente)
}

function abrirAyuda() { ayudaAbierta.value = true }
function escucharAyuda() { abrirAyuda() }

watch(() => [props.listo, props.bloqueado, props.conSesion, ruta.path, ruta.query.tienda] as const, async ([listo, bloqueado, , path, tienda]) => {
  if (!import.meta.client || !listo || bloqueado || path !== '/comprador' || tienda || guiaActiva.value) return
  try { if (localStorage.getItem(claveGuia()) === 'completa') return } catch {}
  await espera(420)
  if (ruta.path === '/comprador' && !guiaActiva.value && !ayudaAbierta.value) await iniciarRecorrido()
}, { immediate: true })

onMounted(() => window.addEventListener('amancay:ayuda-comprador', escucharAyuda))
onBeforeUnmount(() => window.removeEventListener('amancay:ayuda-comprador', escucharAyuda))
</script>

<template>
  <DialogoAyudaWeb
    :abierto="ayudaAbierta"
    :titulo="`Ayuda de ${tituloAyuda}`"
    :descripcion="descripcionAyuda"
    :opciones="opcionesAyuda"
    @cerrar="ayudaAbierta = false"
    @elegir="id => iniciarRecorrido(id)"
    @recorrido-completo="iniciarRecorrido()"
  />
  <GuiaSpotlightWeb
    :activo="guiaActiva"
    :paso="paso"
    :numero="guiaIndividual ? 1 : pasoActual + 1"
    :total="guiaIndividual ? 1 : pasosActivos.length"
    titulo-recorrido="Guía de Inicio"
    @continuar="continuar"
    @cerrar="finalizarRecorrido(!guiaIndividual)"
  />
</template>
