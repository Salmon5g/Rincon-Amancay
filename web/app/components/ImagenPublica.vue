<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
const props = withDefaults(defineProps<{ ruta?: string; alt: string; ajuste?: 'cover' | 'contain'; zoom?: boolean }>(), { ajuste: 'contain', zoom: true })
const { $catalogoPublico } = useNuxtApp()
const url = ref('')
const estado = ref('Sin imagen')
const mostrarAmpliada = ref(false)
const escala = ref(1)
let revision = 0
let urlTemporal = ''
let overflowAnterior = ''
function liberarUrlTemporal() {
  if (urlTemporal) URL.revokeObjectURL(urlTemporal)
  urlTemporal = ''
}
function abrirAmpliada(evento?: MouseEvent) {
  if (!props.zoom || !url.value) return
  evento?.stopPropagation()
  escala.value = 1
  mostrarAmpliada.value = true
  overflowAnterior = document.body.style.overflow
  document.body.style.overflow = 'hidden'
}
function cerrarAmpliada() {
  if (!mostrarAmpliada.value) return
  mostrarAmpliada.value = false
  if (import.meta.client) document.body.style.overflow = overflowAnterior
}
function ajustarZoom(delta: number) {
  escala.value = Math.min(4, Math.max(1, Math.round((escala.value + delta) * 10) / 10))
}
function zoomRueda(evento: WheelEvent) {
  ajustarZoom(evento.deltaY < 0 ? 0.25 : -0.25)
}
function imagenFallida() {
  cerrarAmpliada()
  liberarUrlTemporal()
  url.value = ''
  estado.value = 'Imagen no disponible'
}
watch(() => props.ruta, async (ruta) => {
  const actual = ++revision
  cerrarAmpliada()
  liberarUrlTemporal()
  url.value = ''
  estado.value = ruta ? 'Cargando imagen…' : 'Sin imagen'
  if (!ruta) return
  const urlPublica = $catalogoPublico.resolverUrlImagen(ruta)
  if (urlPublica) {
    url.value = urlPublica
    return
  }
  try {
    const blob = await $catalogoPublico.leerImagen(ruta)
    if (actual !== revision) return
    urlTemporal = URL.createObjectURL(blob)
    url.value = urlTemporal
  } catch { if (actual === revision) estado.value = 'Imagen no disponible' }
}, { immediate: true })
onBeforeUnmount(() => {
  revision++
  if (mostrarAmpliada.value && import.meta.client) document.body.style.overflow = overflowAnterior
  liberarUrlTemporal()
})
</script>
<template>
  <div
    class="imagen-publica"
    :class="{ 'ajuste-contain': props.ajuste === 'contain', 'ajuste-cover': props.ajuste === 'cover', 'imagen-ampliable': url && props.zoom }"
    :title="url && props.zoom ? 'Toca para ampliar la imagen' : undefined"
    @click="abrirAmpliada"
  >
    <img v-if="url" :src="url" :alt="alt" @error="imagenFallida" />
    <span v-else role="img" :aria-label="`${alt}: ${estado}`">🌼<small>{{ estado }}</small></span>
  </div>
  <Teleport to="body">
    <div
      v-if="mostrarAmpliada && url"
      class="visor-imagen"
      role="dialog"
      aria-modal="true"
      :aria-label="`Vista ampliada: ${alt}`"
      tabindex="-1"
      @click.self="cerrarAmpliada"
      @keydown.esc.window="cerrarAmpliada"
      @wheel.prevent="zoomRueda"
      @dblclick="ajustarZoom(escala > 1 ? -3 : 1)"
    >
      <button class="visor-cerrar" type="button" aria-label="Cerrar imagen ampliada" @click="cerrarAmpliada">×</button>
      <div class="visor-contenido">
        <img :src="url" :alt="alt" :style="{ transform: `scale(${escala})` }" @click.stop />
      </div>
      <div class="visor-controles" aria-label="Controles de zoom">
        <button type="button" aria-label="Reducir zoom" :disabled="escala <= 1" @click="ajustarZoom(-0.5)">−</button>
        <span>{{ Math.round(escala * 100) }}%</span>
        <button type="button" aria-label="Aumentar zoom" :disabled="escala >= 4" @click="ajustarZoom(0.5)">+</button>
        <button type="button" @click="escala = 1">Ajustar</button>
      </div>
      <p class="visor-ayuda">Usa +/− para ampliar · toca fuera de la imagen para cerrar</p>
    </div>
  </Teleport>
</template>
<style scoped>
.imagen-publica { background: #fdf1d4; border-radius: 16px; overflow: hidden; aspect-ratio: 4 / 3; width: 100%; display: grid; place-items: center; }
img { width: 100%; height: 100%; object-fit: contain; }
.ajuste-cover img { object-fit: cover; }
.ajuste-contain img { object-fit: contain; }
.imagen-ampliable { cursor: zoom-in; }
span { display: grid; text-align: center; gap: .6rem; font-size: 2rem; color: #89727d; }
small { font-size: .8rem; }
.visor-imagen { position: fixed; z-index: 10000; inset: 0; display: grid; grid-template-rows: 1fr auto auto; justify-items: center; align-items: center; gap: 1rem; padding: 4rem 1.25rem 1.25rem; background: rgb(25 13 20 / 94%); color: white; touch-action: manipulation; }
.visor-contenido { width: min(100%, 1100px); height: min(75vh, 900px); min-width: 0; min-height: 0; display: grid; place-items: center; overflow: auto; }
.visor-contenido img { display: block; width: auto; height: auto; max-width: 100%; max-height: 75vh; object-fit: contain; transition: transform .16s ease; }
.visor-cerrar { position: absolute; top: max(1rem, env(safe-area-inset-top)); right: 1rem; width: 3rem; height: 3rem; border: 1px solid rgb(255 255 255 / 45%); border-radius: 50%; color: white; background: #75264f; font: inherit; font-size: 2rem; line-height: 1; cursor: pointer; }
.visor-controles { display: flex; align-items: center; justify-content: center; gap: .75rem; padding: .5rem .75rem; border: 1px solid rgb(255 255 255 / 30%); border-radius: 999px; background: #fff8ef; color: #3a1228; }
.visor-controles button { min-width: 2.5rem; min-height: 2.5rem; border: 1px solid #ead5c6; border-radius: 999px; color: #75264f; background: white; font: inherit; font-size: 1.25rem; cursor: pointer; }
.visor-controles button:last-child { padding-inline: .9rem; font-size: .95rem; }
.visor-controles button:disabled { opacity: .45; cursor: default; }
.visor-ayuda { margin: 0; color: #fff8ef; font-size: .9rem; text-align: center; }
@media (max-width: 600px) { .visor-imagen { padding-inline: .75rem; } .visor-contenido { height: 70vh; } .visor-contenido img { max-height: 70vh; } }
</style>
