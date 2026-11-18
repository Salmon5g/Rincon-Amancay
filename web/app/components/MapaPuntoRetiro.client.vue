<script setup lang="ts">
import { circleMarker, control, map, tileLayer } from 'leaflet';
import type { CircleMarker, Map as LeafletMap } from 'leaflet';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import 'leaflet/dist/leaflet.css';

const props = defineProps<{ latitud: number; longitud: number; nombreTienda: string }>();
const contenedor = ref<HTMLDivElement | null>(null);
let mapa: LeafletMap | undefined;
let marcador: CircleMarker | undefined;

onMounted(() => {
    if (!contenedor.value) return;
    const punto: [number, number] = [props.latitud, props.longitud];
    mapa = map(contenedor.value, { scrollWheelZoom: false, zoomControl: false }).setView(punto, 16);
    mapa.attributionControl.setPrefix(false);
    control.zoom({ position: 'topright', zoomInTitle: 'Acercar el mapa', zoomOutTitle: 'Alejar el mapa' }).addTo(mapa);
    tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(mapa);
    marcador = circleMarker(punto, {
        radius: 8,
        color: '#fff',
        weight: 2,
        fillColor: '#82234e',
        fillOpacity: 1,
        interactive: false,
    }).addTo(mapa);
});

watch(() => [props.latitud, props.longitud] as const, ([latitud, longitud]) => {
    const punto: [number, number] = [latitud, longitud];
    marcador?.setLatLng(punto);
    mapa?.panTo(punto);
});

onBeforeUnmount(() => mapa?.remove());
</script>

<template>
  <div
    ref="contenedor"
    class="mapa-punto"
    role="region"
    :aria-label="`Mapa del punto de venta o retiro de ${nombreTienda}`"
  ></div>
</template>

<style scoped>
.mapa-punto {
    position: relative;
    isolation: isolate;
    width: 100%;
    box-sizing: border-box;
    aspect-ratio: 16 / 10;
    min-height: 140px;
    max-height: 240px;
    overflow: hidden;
    border: 1px solid var(--line, #e8d6c1);
    border-radius: 12px;
}
</style>