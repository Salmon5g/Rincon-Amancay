<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PasoGuiaWeb } from '~/types/ayuda-web'

const props = defineProps<{
  activo: boolean
  paso: PasoGuiaWeb | null
  numero: number
  total: number
  tituloRecorrido: string
}>()

const emit = defineEmits<{ (evento: 'continuar'): void; (evento: 'cerrar'): void }>()
const rect = ref<DOMRect | null>(null)
let observador: ResizeObserver | undefined
let elemento: Element | null = null
let temporizador: ReturnType<typeof setTimeout> | undefined

const borde = 7
const foco = computed(() => {
  if (!rect.value) return null
  const izquierda = Math.max(4, rect.value.left - borde)
  const arriba = Math.max(4, rect.value.top - borde)
  const derecha = Math.min(window.innerWidth - 4, rect.value.right + borde)
  const abajo = Math.min(window.innerHeight - 4, rect.value.bottom + borde)
  return { left: izquierda, top: arriba, width: Math.max(0, derecha - izquierda), height: Math.max(0, abajo - arriba) }
})
const altoPantalla = ref(0)
const objetivoAbajo = computed(() => Boolean(foco.value && foco.value.top + foco.value.height > altoPantalla.value * 0.58))

function actualizar() {
  if (!props.activo || !props.paso || !import.meta.client) return
  const encontrado = Array.from(document.querySelectorAll(props.paso.objetivo)).find(candidato => {
    const estilo = window.getComputedStyle(candidato)
    return estilo.display !== 'none' && estilo.visibility !== 'hidden' && candidato.getClientRects().length > 0
  }) ?? null
  if (encontrado !== elemento) {
    observador?.disconnect()
    elemento = encontrado
    if (elemento) observador?.observe(elemento)
  }
  rect.value = elemento?.getBoundingClientRect() ?? null
  altoPantalla.value = window.innerHeight
}

async function ubicarObjetivo() {
  rect.value = null
  if (!props.activo || !props.paso || !import.meta.client) return
  await nextTick()
  for (let intento = 0; intento < 14; intento++) {
    const encontrado = Array.from(document.querySelectorAll(props.paso.objetivo)).find(candidato => {
      const estilo = window.getComputedStyle(candidato)
      return estilo.display !== 'none' && estilo.visibility !== 'hidden' && candidato.getClientRects().length > 0
    }) ?? null
    if (encontrado) {
      elemento = encontrado
      const inicial = encontrado.getBoundingClientRect()
      if (inicial.left < 8 || inicial.right > window.innerWidth - 8 || inicial.top < 12 || inicial.bottom > window.innerHeight - 110) {
        encontrado.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' })
        await new Promise(resolve => setTimeout(resolve, 250))
      }
      actualizar()
      await nextTick()
      document.querySelector<HTMLButtonElement>('.guia-foco')?.focus({ preventScroll: true })
      return
    }
    await new Promise(resolve => setTimeout(resolve, 100))
  }
}

function alCanviarMida() { actualizar() }
function alDesplaçar() { actualizar() }
function alTeclar(evento: KeyboardEvent) {
  if (props.activo && evento.key === 'Escape') emit('cerrar')
}

watch(() => [props.activo, props.paso?.objetivo] as const, async ([activo]) => {
  clearTimeout(temporizador)
  if (activo) await ubicarObjetivo()
  else { rect.value = null; elemento = null; observador?.disconnect() }
}, { flush: 'post' })

onMounted(() => {
  observador = new ResizeObserver(actualizar)
  window.addEventListener('resize', alCanviarMida)
  window.addEventListener('scroll', alDesplaçar, true)
  window.addEventListener('keydown', alTeclar)
  if (props.activo) void ubicarObjetivo()
})

onBeforeUnmount(() => {
  clearTimeout(temporizador)
  observador?.disconnect()
  window.removeEventListener('resize', alCanviarMida)
  window.removeEventListener('scroll', alDesplaçar, true)
  window.removeEventListener('keydown', alTeclar)
})
</script>

<template>
  <div v-if="activo && paso && foco" class="guia-spotlight" aria-live="polite">
    <div class="guia-sombra" :style="{ top: '0', left: '0', right: '0', height: `${foco.top}px` }" aria-hidden="true"></div>
    <div class="guia-sombra" :style="{ top: `${foco.top + foco.height}px`, left: '0', right: '0', bottom: '0' }" aria-hidden="true"></div>
    <div class="guia-sombra" :style="{ top: `${foco.top}px`, left: '0', width: `${foco.left}px`, height: `${foco.height}px` }" aria-hidden="true"></div>
    <div class="guia-sombra" :style="{ top: `${foco.top}px`, left: `${foco.left + foco.width}px`, right: '0', height: `${foco.height}px` }" aria-hidden="true"></div>
    <button
      class="guia-foco"
      type="button"
      :style="{ left: `${foco.left}px`, top: `${foco.top}px`, width: `${foco.width}px`, height: `${foco.height}px` }"
      :aria-label="`Paso ${numero} de ${total}. ${paso.titulo}. ${paso.explicacion}. Toca para continuar.`"
      @click="emit('continuar')"
    ></button>
    <section class="guia-tarjeta" :class="{ 'guia-tarjeta-arriba': objetivoAbajo }" role="dialog" aria-modal="true" :aria-label="`${tituloRecorrido}, paso ${numero} de ${total}`">
      <p class="guia-progreso">{{ tituloRecorrido }} · Paso {{ numero }} de {{ total }}</p>
      <h2>{{ paso.titulo }}</h2>
      <p class="guia-explicacion">{{ paso.explicacion }}</p>
      <div class="guia-acciones">
        <button class="guia-saltar" type="button" @click="emit('cerrar')">Saltar guía</button>
        <span>Toca o pulsa Intro sobre lo iluminado</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.guia-spotlight{position:fixed;z-index:11000;inset:0;pointer-events:none}
.guia-sombra{position:fixed;z-index:0;background:rgba(32,22,28,.68);pointer-events:auto}
.guia-foco{position:fixed;z-index:1;padding:0;border:3px solid var(--amancay-mostaza,#f2b73b);border-radius:16px;background:transparent;box-shadow:0 0 0 3px rgba(255,255,255,.62);cursor:pointer;pointer-events:auto;animation:latido-foco 1.8s ease-in-out infinite}
.guia-foco:focus-visible{outline:3px solid #fff;outline-offset:5px}
.guia-tarjeta{position:fixed;z-index:2;top:auto;right:12px;bottom:max(16px,env(safe-area-inset-bottom));left:12px;width:min(440px,calc(100vw - 24px));margin-inline:auto;padding:20px;border:1px solid rgba(107,30,61,.12);border-radius:23px;background:var(--amancay-superficie,#fffbf2);box-shadow:0 18px 50px rgba(28,16,24,.25);color:var(--amancay-tinta,#2e1b20);font-family:var(--amancay-fuente-cuerpo,system-ui,sans-serif);pointer-events:auto}
.guia-tarjeta-arriba{top:max(14px,env(safe-area-inset-top));bottom:auto}
.guia-progreso{margin:0;color:var(--amancay-bordo,#6b1e3d);font-size:.8rem;font-weight:700;letter-spacing:.03em}
.guia-tarjeta h2{margin:7px 0;color:var(--amancay-bordo-oscuro,#4e142c);font:400 clamp(1.4rem,4vw,1.8rem)/1.15 var(--amancay-fuente-titulo,Georgia,serif)}
.guia-explicacion{margin:0;color:var(--amancay-tinta,#2e1b20);font-size:1rem;line-height:1.55}
.guia-acciones{display:flex;align-items:center;gap:12px;margin-top:15px}
.guia-saltar{min-height:44px;padding:0 13px;border:1px solid var(--amancay-borde,#e7d5be);border-radius:999px;background:#fff;color:var(--amancay-bordo,#6b1e3d);font:600 .9rem var(--amancay-fuente-cuerpo,system-ui,sans-serif);cursor:pointer}
.guia-acciones span{margin-left:auto;color:var(--amancay-tinta-suave,#7c6a6e);font-size:.8rem}
@keyframes latido-foco{50%{box-shadow:0 0 0 6px rgba(255,255,255,.35)}}
@media(max-width:480px){.guia-tarjeta{padding:17px 16px}.guia-explicacion{font-size:.94rem}}
@media(prefers-reduced-motion:reduce){.guia-foco{animation:none}}
</style>
