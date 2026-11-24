<script setup lang="ts">
import type { OpcionAyudaWeb } from '~/types/ayuda-web'

defineProps<{
  abierto: boolean
  titulo: string
  descripcion: string
  opciones: OpcionAyudaWeb[]
}>()

const emit = defineEmits<{
  (evento: 'cerrar'): void
  (evento: 'elegir', id: string): void
  (evento: 'recorrido-completo'): void
}>()
</script>

<template>
  <Teleport to="body">
    <div v-if="abierto" class="ayuda-fondo" @click.self="emit('cerrar')">
      <section class="ayuda-panel" role="dialog" aria-modal="true" :aria-label="titulo">
        <header class="ayuda-cabecera">
          <div>
            <h2>{{ titulo }}</h2>
            <p>{{ descripcion }}</p>
          </div>
          <button class="ayuda-cerrar" type="button" aria-label="Cerrar ayuda" @click="emit('cerrar')">×</button>
        </header>

        <p v-if="opciones.length" class="ayuda-instruccion">Elige qué tutorial quieres repetir</p>
        <div v-if="opciones.length" class="ayuda-opciones">
          <button v-for="opcion in opciones" :key="opcion.id" class="ayuda-opcion" type="button" @click="emit('elegir', opcion.id)">
            <span class="ayuda-icono" aria-hidden="true">{{ opcion.icono }}</span>
            <span>{{ opcion.titulo }}</span>
            <span class="ayuda-flecha" aria-hidden="true">›</span>
          </button>
        </div>

        <SelectorTextoAccesible />

        <div class="ayuda-separador"></div>
        <button class="ayuda-recorrido" type="button" @click="emit('recorrido-completo')">
          <span class="ayuda-icono ayuda-icono-recorrido" aria-hidden="true">▶</span>
          <span>Recorrido completo</span>
          <span class="ayuda-flecha" aria-hidden="true">›</span>
        </button>
        <footer class="ayuda-pie">
          <button type="button" @click="emit('cerrar')">Cerrar</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ayuda-fondo{position:fixed;z-index:12000;inset:0;display:grid;place-items:center;padding:16px;background:rgba(36,24,31,.58);backdrop-filter:blur(3px)}
.ayuda-panel{display:grid;gap:14px;width:min(100%,760px);max-height:min(calc(90dvh / var(--amancay-escala-texto,1)),calc(900px / var(--amancay-escala-texto,1)));overflow:auto;padding:clamp(20px,3vw,30px);border:1px solid rgba(107,30,61,.1);border-radius:28px;background:var(--amancay-superficie,#fffbf2);box-shadow:0 24px 70px rgba(45,20,32,.25);color:var(--amancay-tinta,#2e1b20);font-family:var(--amancay-fuente-cuerpo,system-ui,sans-serif)}
.ayuda-cabecera{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}
.ayuda-cabecera h2{margin:0;color:var(--amancay-bordo,#6b1e3d);font:400 clamp(1.65rem,3vw,2rem)/1.12 var(--amancay-fuente-titulo,Georgia,serif)}
.ayuda-cabecera p{margin:8px 0 0;color:var(--amancay-tinta-suave,#7c6a6e);font-size:.95rem;line-height:1.55}
.ayuda-cerrar{flex:0 0 44px;width:44px;height:44px;border:1px solid var(--amancay-borde,#e7d5be);border-radius:50%;background:#fff;color:var(--amancay-bordo,#6b1e3d);font-size:1.55rem;line-height:1;cursor:pointer}
.ayuda-instruccion{margin:2px 0 -3px;color:var(--amancay-bordo,#6b1e3d);font-size:.92rem;font-weight:700}
.ayuda-opciones{display:grid;gap:9px}
.ayuda-opcion,.ayuda-recorrido{display:grid;grid-template-columns:42px minmax(0,1fr) 22px;align-items:center;gap:13px;width:100%;min-height:62px;padding:9px 14px;border:1px solid #f49a70;border-radius:17px;background:#fff;color:var(--amancay-tinta,#2e1b20);text-align:left;font:500 1rem var(--amancay-fuente-cuerpo,system-ui,sans-serif);cursor:pointer;transition:transform .15s ease,box-shadow .15s ease,background .15s ease}
.ayuda-opcion:hover,.ayuda-opcion:focus-visible{transform:translateY(-1px);background:#fffdfa;box-shadow:0 5px 16px rgba(107,30,61,.1);outline:3px solid rgba(107,30,61,.24);outline-offset:2px}
.ayuda-icono{display:grid;place-items:center;width:42px;height:42px;border-radius:50%;background:#fde7d8;color:var(--amancay-bordo,#6b1e3d);font-size:1.2rem}
.ayuda-flecha{justify-self:end;color:var(--amancay-bordo,#6b1e3d);font:400 1.8rem/1 var(--amancay-fuente-titulo,Georgia,serif)}
.ayuda-separador{height:1px;margin:2px 4px;background:var(--amancay-borde,#e7d5be)}
.ayuda-recorrido{min-height:64px;border-color:var(--amancay-bordo,#6b1e3d);background:var(--amancay-bordo,#6b1e3d);color:#fff;font-weight:700}
.ayuda-recorrido:hover,.ayuda-recorrido:focus-visible{background:var(--amancay-bordo-oscuro,#4e142c);outline:3px solid rgba(107,30,61,.28);outline-offset:2px}
.ayuda-icono-recorrido{background:#e96168;color:#fff}
.ayuda-pie{display:flex;justify-content:flex-end;padding-top:1px}
.ayuda-pie button{min-width:72px;min-height:44px;border:0;background:transparent;color:var(--amancay-bordo,#6b1e3d);font:600 .95rem var(--amancay-fuente-cuerpo,system-ui,sans-serif);cursor:pointer}
@media(max-width:560px){.ayuda-fondo{align-items:end;padding:8px}.ayuda-panel{gap:12px;max-height:calc(92dvh / var(--amancay-escala-texto,1));padding:20px 16px 14px;border-radius:25px}.ayuda-opcion{min-height:58px;grid-template-columns:38px minmax(0,1fr) 20px;padding:8px 11px}.ayuda-icono{width:38px;height:38px}.ayuda-cabecera p{font-size:.9rem}}
@media(prefers-reduced-motion:reduce){.ayuda-opcion{transition:none}}
</style>
