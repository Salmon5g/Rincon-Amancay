<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
const props = defineProps<{ ruta?: string; alt: string }>()
const { $catalogoPublico } = useNuxtApp()
const url = ref('')
const estado = ref('Sin imagen')
let revision = 0
function imagenFallida() {
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
  estado.value = 'Imagen no disponible'
}
watch(() => props.ruta, async (ruta) => {
  const actual = ++revision
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
  estado.value = ruta ? 'Cargando imagen…' : 'Sin imagen'
  if (!ruta) return
  try {
    const blob = await $catalogoPublico.leerImagen(ruta)
    if (actual !== revision) return
    url.value = URL.createObjectURL(blob)
  } catch { if (actual === revision) estado.value = 'Imagen no disponible' }
}, { immediate: true })
onBeforeUnmount(() => { revision++; if (url.value) URL.revokeObjectURL(url.value) })
</script>
<template>
  <div class="imagen-publica">
    <img v-if="url" :src="url" :alt="alt" @error="imagenFallida" />
    <span v-else role="img" :aria-label="`${alt}: ${estado}`">🌼<small>{{ estado }}</small></span>
  </div>
</template>
<style scoped>
.imagen-publica { background: #fdf1d4; border-radius: 16px; overflow: hidden; aspect-ratio: 4 / 3; width: 100%; display: grid; place-items: center; }
img { width: 100%; height: 100%; object-fit: cover; }
span { display: grid; text-align: center; gap: .6rem; font-size: 2rem; color: #89727d; }
small { font-size: .8rem; }
</style>
