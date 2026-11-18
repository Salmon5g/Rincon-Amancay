<script setup lang="ts">
import { circleMarker, control, map as crearMapa, tileLayer } from 'leaflet'
import type { CircleMarker, Map as LeafletMap } from 'leaflet'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import 'leaflet/dist/leaflet.css'

type ResultadoLugar = {
  place_id: number
  display_name: string
  lat: string
  lon: string
}

type UbicacionFeria = {
  latitud: number
  longitud: number
  direccion: string
}

const props = withDefaults(defineProps<{
  latitud: number | null
  longitud: number | null
  direccion: string
  disabled?: boolean
}>(), { disabled: false })

const emit = defineEmits<{
  (event: 'ubicacion-seleccionada', ubicacion: UbicacionFeria): void
}>()

const contenedor = ref<HTMLDivElement | null>(null)
const consulta = ref('')
const resultados = ref<ResultadoLugar[]>([])
const buscando = ref(false)
const estadoBusqueda = ref('')
const nombreUbicacion = ref(props.direccion.trim())
let mapa: LeafletMap | undefined
let marcador: CircleMarker | undefined
let temporizadorBusqueda: ReturnType<typeof setTimeout> | undefined
let controladorBusqueda: AbortController | undefined
let solicitudUbicacion = 0
let ultimoPedidoGeocodificador = 0

const tieneCoordenadas = (latitud: number | null, longitud: number | null) =>
  latitud !== null && longitud !== null && Number.isFinite(latitud) && Number.isFinite(longitud)
const ubicacionMarcada = computed(() => tieneCoordenadas(props.latitud, props.longitud))

function direccionLegible(direccion: string | undefined, respaldo: string) {
  return (direccion?.trim() || respaldo).slice(0, 300)
}

function actualizarMarcador(latitud: number, longitud: number, centrar: boolean) {
  const punto: [number, number] = [latitud, longitud]
  if (marcador) marcador.setLatLng(punto)
  else {
    marcador = circleMarker(punto, {
      radius: 10,
      color: '#fff',
      weight: 3,
      fillColor: '#75264f',
      fillOpacity: 1,
    }).addTo(mapa!)
  }
  if (centrar) mapa?.setView(punto, Math.max(mapa.getZoom(), 15))
}

function confirmarUbicacion(latitud: number, longitud: number, direccion: string) {
  nombreUbicacion.value = direccionLegible(direccion, `Punto en el mapa (${latitud.toFixed(5)}, ${longitud.toFixed(5)})`)
  emit('ubicacion-seleccionada', { latitud, longitud, direccion: nombreUbicacion.value })
}

async function consultarNominatim(url: URL, signal: AbortSignal) {
  const espera = Math.max(0, 1000 - (Date.now() - ultimoPedidoGeocodificador))
  if (espera) await new Promise(resolve => setTimeout(resolve, espera))
  if (signal.aborted) throw new DOMException('Solicitud cancelada', 'AbortError')
  ultimoPedidoGeocodificador = Date.now()
  const respuesta = await fetch(url, {
    signal,
    headers: { 'Accept-Language': 'es-CL,es;q=0.9' },
  })
  if (!respuesta.ok) throw new Error('No se pudo consultar el servicio de direcciones.')
  return respuesta
}

async function buscarDirecciones(texto: string, signal: AbortSignal) {
  const url = new URL('https://nominatim.openstreetmap.org/search')
  url.search = new URLSearchParams({
    q: texto,
    format: 'jsonv2',
    addressdetails: '1',
    limit: '5',
    countrycodes: 'cl',
  }).toString()
  const respuesta = await consultarNominatim(url, signal)
  return await respuesta.json() as ResultadoLugar[]
}

async function buscarDireccionDelPunto(latitud: number, longitud: number, signal: AbortSignal) {
  const url = new URL('https://nominatim.openstreetmap.org/reverse')
  url.search = new URLSearchParams({
    lat: String(latitud),
    lon: String(longitud),
    format: 'jsonv2',
    zoom: '18',
    addressdetails: '1',
  }).toString()
  const respuesta = await consultarNominatim(url, signal)
  const datos = await respuesta.json() as { display_name?: string }
  return datos.display_name?.trim() ?? ''
}

async function seleccionarPunto(latitud: number, longitud: number, direccionEncontrada?: string) {
  if (props.disabled || !Number.isFinite(latitud) || !Number.isFinite(longitud)) return
  if (temporizadorBusqueda) clearTimeout(temporizadorBusqueda)
  buscando.value = false
  const idSolicitud = ++solicitudUbicacion
  controladorBusqueda?.abort()
  const controlador = new AbortController()
  controladorBusqueda = controlador
  actualizarMarcador(latitud, longitud, true)
  resultados.value = []
  estadoBusqueda.value = direccionEncontrada ? '' : 'Buscando la dirección del punto…'
  confirmarUbicacion(latitud, longitud, direccionEncontrada || `Punto en el mapa (${latitud.toFixed(5)}, ${longitud.toFixed(5)})`)

  if (direccionEncontrada) return

  try {
    const direccion = await buscarDireccionDelPunto(latitud, longitud, controlador.signal)
    if (controlador.signal.aborted || idSolicitud !== solicitudUbicacion) return
    if (direccion) {
      confirmarUbicacion(latitud, longitud, direccion)
      estadoBusqueda.value = ''
    } else {
      estadoBusqueda.value = 'No encontramos una dirección cercana. El punto y sus coordenadas quedaron seleccionados; puedes editar el texto.'
    }
  } catch (cause) {
    if ((cause as { name?: string }).name !== 'AbortError' && idSolicitud === solicitudUbicacion) {
      estadoBusqueda.value = 'No se pudo resolver la dirección. El punto quedó seleccionado y puedes editar el texto.'
    }
  }
}

function elegirResultado(resultado: ResultadoLugar) {
  estadoBusqueda.value = ''
  void seleccionarPunto(Number(resultado.lat), Number(resultado.lon), resultado.display_name)
}

watch(consulta, (texto, _, alLimpiar) => {
  if (temporizadorBusqueda) clearTimeout(temporizadorBusqueda)
  controladorBusqueda?.abort()
  resultados.value = []
  const busqueda = texto.trim()
  if (busqueda.length < 3 || props.disabled) {
    buscando.value = false
    estadoBusqueda.value = ''
    return
  }
  const controlador = new AbortController()
  controladorBusqueda = controlador
  buscando.value = true
  temporizadorBusqueda = setTimeout(async () => {
    try {
      const encontrados = await buscarDirecciones(busqueda, controlador.signal)
      if (!controlador.signal.aborted) {
        resultados.value = encontrados
        estadoBusqueda.value = encontrados.length ? '' : 'No encontramos direcciones con ese texto.'
      }
    } catch (cause) {
      if ((cause as { name?: string }).name !== 'AbortError') estadoBusqueda.value = 'No se pudo buscar. Puedes marcar el punto directamente en el mapa.'
    } finally {
      if (!controlador.signal.aborted) buscando.value = false
    }
  }, 1000)
  alLimpiar(() => {
    if (temporizadorBusqueda) clearTimeout(temporizadorBusqueda)
    controlador.abort()
  })
})

watch(() => props.direccion, (direccion) => {
  nombreUbicacion.value = direccion.trim()
})

watch(() => [props.latitud, props.longitud] as const, ([latitud, longitud]) => {
  if (tieneCoordenadas(latitud, longitud)) actualizarMarcador(latitud!, longitud!, true)
  else if (marcador) {
    marcador.remove()
    marcador = undefined
  }
})

onMounted(() => {
  if (!contenedor.value) return
  const tienePunto = tieneCoordenadas(props.latitud, props.longitud)
  const puntoInicial: [number, number] = tienePunto
    ? [props.latitud!, props.longitud!]
    : [-41.4693, -72.9414]
  mapa = crearMapa(contenedor.value, { scrollWheelZoom: false, zoomControl: false })
    .setView(puntoInicial, tienePunto ? 15 : 12)
  mapa.attributionControl.setPrefix(false)
  control.zoom({ position: 'topright', zoomInTitle: 'Acercar el mapa', zoomOutTitle: 'Alejar el mapa' }).addTo(mapa)
  tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(mapa)
  if (tienePunto) actualizarMarcador(props.latitud!, props.longitud!, false)
  mapa.on('click', evento => {
    if (!props.disabled) void seleccionarPunto(evento.latlng.lat, evento.latlng.lng)
  })
})

onBeforeUnmount(() => {
  if (temporizadorBusqueda) clearTimeout(temporizadorBusqueda)
  controladorBusqueda?.abort()
  mapa?.remove()
})
</script>

<template>
  <section class="selector-ubicacion-feria" aria-labelledby="titulo-ubicacion-feria">
    <div class="selector-ubicacion-cabecera">
      <div>
        <h4 id="titulo-ubicacion-feria">Ubicación en el mapa</h4>
        <p>Busca una dirección o marca el punto donde se realizará la feria.</p>
      </div>
      <span class="nota-mapa">OpenStreetMap</span>
    </div>
    <label class="campo-busqueda-feria">
      Buscar dirección o lugar
      <input
        v-model="consulta"
        type="search"
        autocomplete="off"
        maxlength="180"
        placeholder="Ej.: Plaza de Armas, Puerto Montt"
        :disabled="disabled"
        aria-controls="resultados-ubicacion-feria"
      />
    </label>
    <ul v-if="resultados.length" id="resultados-ubicacion-feria" class="resultados-ubicacion" aria-label="Direcciones encontradas">
      <li v-for="resultado in resultados" :key="resultado.place_id">
        <button type="button" :disabled="disabled" @click="elegirResultado(resultado)">
          <span aria-hidden="true">📍</span>{{ resultado.display_name }}
        </button>
      </li>
    </ul>
    <p v-if="buscando" class="estado-mapa" role="status">Buscando direcciones…</p>
    <p v-else-if="estadoBusqueda" class="estado-mapa" role="status">{{ estadoBusqueda }}</p>
    <p class="instruccion-mapa">También puedes hacer clic en el mapa para fijar el punto. Al seleccionarlo, completaremos «Dirección o lugar».</p>
    <div ref="contenedor" class="mapa-feria" role="region" aria-label="Mapa para elegir la ubicación de la feria"></div>
    <p v-if="ubicacionMarcada && nombreUbicacion" class="ubicacion-confirmada" aria-live="polite">
      <span aria-hidden="true">📍</span> Ubicación marcada: {{ nombreUbicacion }}
    </p>
  </section>
</template>

<style scoped>
.selector-ubicacion-feria {
  display: grid;
  gap: 9px;
  padding: 15px;
  border: 1px solid #ead9cc;
  border-radius: 16px;
  background: #fffaf4;
}

.selector-ubicacion-cabecera {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.selector-ubicacion-cabecera h4 {
  margin: 0;
  color: #75264f;
  font-size: 1.05rem;
}

.selector-ubicacion-cabecera p,
.instruccion-mapa,
.estado-mapa {
  margin: 3px 0 0;
  color: #775f6c;
  font-size: 0.88rem;
  line-height: 1.45;
}

.nota-mapa {
  flex: 0 0 auto;
  padding: 5px 9px;
  border: 1px solid #ead9cc;
  border-radius: 999px;
  color: #775f6c;
  font-size: 0.75rem;
}

.campo-busqueda-feria {
  display: grid;
  gap: 6px;
  font-weight: 600;
}

.resultados-ubicacion {
  display: grid;
  gap: 4px;
  max-height: 180px;
  margin: -3px 0 0;
  padding: 5px;
  overflow: auto;
  border: 1px solid #ead9cc;
  border-radius: 12px;
  background: #fff;
  list-style: none;
}

.resultados-ubicacion button {
  display: flex;
  width: 100%;
  align-items: flex-start;
  gap: 8px;
  padding: 9px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #3a1228;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.resultados-ubicacion button:hover,
.resultados-ubicacion button:focus-visible {
  background: #f4e7ed;
}

.mapa-feria {
  position: relative;
  z-index: 0;
  width: 100%;
  height: clamp(230px, 32vw, 360px);
  overflow: hidden;
  border: 1px solid #ead9cc;
  border-radius: 14px;
  background: #e8e5d7;
}

.mapa-feria :deep(.leaflet-container) {
  width: 100%;
  height: 100%;
  font: inherit;
}

.mapa-feria :deep(.leaflet-control-zoom a) {
  color: #75264f;
}

.mapa-feria :deep(.leaflet-control-attribution) {
  font-size: 10px;
}

.ubicacion-confirmada {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 0;
  padding: 8px 11px;
  border-radius: 999px;
  background: #e8f4df;
  color: #27603b;
  font-size: 0.88rem;
  font-weight: 700;
  overflow-wrap: anywhere;
}

@media (max-width: 600px) {
  .selector-ubicacion-feria {
    padding: 11px;
  }

  .mapa-feria {
    height: 270px;
  }

  .ubicacion-confirmada {
    border-radius: 12px;
  }
}
</style>
