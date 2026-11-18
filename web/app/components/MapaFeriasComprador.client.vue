<script setup lang="ts">
import { circleMarker, control, latLngBounds, map as crearMapa, tileLayer } from 'leaflet'
import type { CircleMarker, Map as LeafletMap } from 'leaflet'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import 'leaflet/dist/leaflet.css'
import type { FeriaPublica } from '~/types/comprador-publico'

const props = withDefaults(defineProps<{
  ferias: FeriaPublica[]
  seleccionadaId?: string
  ubicacionUsuario?: { latitud: number; longitud: number } | null
  modo?: 'directorio' | 'detalle'
}>(), { seleccionadaId: '', ubicacionUsuario: null, modo: 'directorio' })

const emit = defineEmits<{
  (event: 'seleccionar', feriaId: string): void
}>()

const contenedor = ref<HTMLDivElement | null>(null)
const CENTRO_PUERTO_MONTT: [number, number] = [-41.4693, -72.9414]
let mapa: LeafletMap | undefined
const marcadores = new Map<string, CircleMarker>()
let marcadorUsuario: CircleMarker | undefined

function tieneCoordenadas(feria: FeriaPublica) {
  return feria.latitud !== null && Number.isFinite(feria.latitud) && feria.latitud >= -90 && feria.latitud <= 90
    && feria.longitud !== null && Number.isFinite(feria.longitud) && feria.longitud >= -180 && feria.longitud <= 180
}

function feriaPorId(id: string) {
  return props.ferias.find(feria => feria.id === id && tieneCoordenadas(feria))
}

function enfocarFeria(id: string, animar = true) {
  const feria = feriaPorId(id)
  if (!mapa || !feria) return
  const punto: [number, number] = [feria.latitud!, feria.longitud!]
  mapa.flyTo(punto, Math.max(mapa.getZoom(), 15), { animate: animar, duration: 0.65 })
}

function centrarUsuario(animar = true) {
  const ubicacion = props.ubicacionUsuario
  if (!mapa || !ubicacion) return
  mapa.setView(
    [ubicacion.latitud, ubicacion.longitud],
    Math.max(mapa.getZoom(), 13),
    { animate: animar },
  )
}

function actualizarMarcadorUsuario() {
  if (!mapa) return
  const ubicacion = props.ubicacionUsuario
  if (!ubicacion) {
    marcadorUsuario?.remove()
    marcadorUsuario = undefined
    return
  }
  const punto: [number, number] = [ubicacion.latitud, ubicacion.longitud]
  if (marcadorUsuario) marcadorUsuario.setLatLng(punto)
  else {
    marcadorUsuario = circleMarker(punto, {
      radius: 9,
      color: '#fff',
      weight: 3,
      fillColor: '#e8a33d',
      fillOpacity: 1,
      interactive: false,
    }).addTo(mapa)
  }
  marcadorUsuario.unbindTooltip().bringToFront()
}

function actualizarMarcadores() {
  if (!mapa) return
  const feriasConPunto = props.ferias.filter(tieneCoordenadas)
  const idsActuales = new Set(feriasConPunto.map(feria => feria.id))

  for (const [id, marcador] of marcadores) {
    if (!idsActuales.has(id)) {
      marcador.remove()
      marcadores.delete(id)
    }
  }

  for (const feria of feriasConPunto) {
    const punto: [number, number] = [feria.latitud!, feria.longitud!]
    let marcador = marcadores.get(feria.id)
    if (marcador) {
      marcador.setLatLng(punto)
    } else {
      marcador = circleMarker(punto, { bubblingMouseEvents: false }).addTo(mapa)
      marcador.on('click', () => emit('seleccionar', feria.id))
      marcadores.set(feria.id, marcador)
    }

    const seleccionada = feria.id === props.seleccionadaId
    marcador.setStyle({
      radius: seleccionada ? 11 : 8,
      color: seleccionada ? '#e8a33d' : '#fff',
      weight: seleccionada ? 4 : 3,
      fillColor: '#75264f',
      fillOpacity: 1,
    })
    marcador.unbindTooltip()
    if (seleccionada) {
      marcador.bindTooltip(feria.nombre, {
        className: 'etiqueta-feria-seleccionada',
        direction: 'top',
        offset: [0, -10],
        permanent: true,
      })
    }
  }
}

function encuadrarFerias() {
  if (!mapa) return
  const puntos = props.ferias.filter(tieneCoordenadas)
  if (puntos.length === 0) {
    mapa.setView(CENTRO_PUERTO_MONTT, 12)
    return
  }
  if (puntos.length === 1) {
    mapa.setView([puntos[0]!.latitud!, puntos[0]!.longitud!], props.modo === 'detalle' ? 15 : 13)
    return
  }
  mapa.fitBounds(latLngBounds(puntos.map(feria => [feria.latitud!, feria.longitud!])), { padding: [36, 36], maxZoom: 14 })
}

function sincronizarSeleccion() {
  actualizarMarcadores()
  actualizarMarcadorUsuario()
  if (props.ubicacionUsuario) centrarUsuario(false)
  else if (props.seleccionadaId) enfocarFeria(props.seleccionadaId, false)
  else encuadrarFerias()
}

onMounted(async () => {
  if (!contenedor.value) return
  mapa = crearMapa(contenedor.value, { scrollWheelZoom: false, zoomControl: false }).setView(CENTRO_PUERTO_MONTT, 12)
  mapa.attributionControl.setPrefix(false)
  control.zoom({ position: 'topright', zoomInTitle: 'Acercar el mapa', zoomOutTitle: 'Alejar el mapa' }).addTo(mapa)
  tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(mapa)
  actualizarMarcadores()
  actualizarMarcadorUsuario()
  if (props.ubicacionUsuario || props.seleccionadaId) sincronizarSeleccion()
  else encuadrarFerias()
  await nextTick()
  mapa.invalidateSize()
})

watch(() => props.seleccionadaId, (id, anterior) => {
  actualizarMarcadores()
  if (id && id !== anterior) enfocarFeria(id)
  else if (!id && props.ubicacionUsuario) centrarUsuario(false)
})

watch(() => props.ubicacionUsuario, ubicacion => {
  actualizarMarcadorUsuario()
  if (ubicacion) centrarUsuario()
})

watch(() => props.ferias, async () => {
  actualizarMarcadores()
  await nextTick()
  if (props.ubicacionUsuario) centrarUsuario(false)
  else if (props.seleccionadaId) enfocarFeria(props.seleccionadaId, false)
  else encuadrarFerias()
  mapa?.invalidateSize()
})

onBeforeUnmount(() => {
  mapa?.remove()
  mapa = undefined
  marcadores.clear()
  marcadorUsuario = undefined
})
</script>

<template>
  <div
    ref="contenedor"
    :id="`mapa-ferias-${modo}`"
    class="mapa-ferias-comprador"
    :class="`mapa-ferias-${modo}`"
    role="region"
    :aria-label="modo === 'detalle' ? `Mapa de ubicación de ${ferias[0]?.nombre ?? 'la feria'}` : 'Mapa de ferias publicadas'"
  ></div>
</template>

<style>
.mapa-ferias-comprador {
    position: relative;
    z-index: 0;
    width: 100%;
    overflow: hidden;
    border: 1px solid var(--amancay-borde, #ead9cc);
    border-radius: 18px;
    background: #edf0e7;
}

.mapa-ferias-directorio {
    height: clamp(260px, 42vw, 440px);
}

.mapa-ferias-detalle {
    height: clamp(230px, 30vw, 330px);
}

.mapa-ferias-comprador .leaflet-container {
    width: 100%;
    height: 100%;
    font-family: var(--amancay-fuente-cuerpo, sans-serif);
}

.mapa-ferias-comprador .leaflet-control-zoom a {
    color: var(--amancay-bordo, #75264f);
}

.mapa-ferias-comprador .leaflet-control-attribution {
    border-radius: 8px 0 0;
    font-size: 0.68rem;
}

.etiqueta-feria-seleccionada {
    border: 1px solid var(--amancay-borde, #ead9cc) !important;
    border-radius: 999px !important;
    color: var(--amancay-bordo, #75264f) !important;
    font-weight: 700;
}

@media (max-width: 560px) {
    .mapa-ferias-directorio {
        height: 300px;
    }

    .mapa-ferias-detalle {
        height: 240px;
    }
}
</style>
