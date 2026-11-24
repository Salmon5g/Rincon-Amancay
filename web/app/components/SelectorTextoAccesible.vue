<script setup lang="ts">
import { opcionesTextoAccesible, useTextoAccesible } from '~/composables/useTextoAccesible'

const { tamano, cambiarTamano } = useTextoAccesible()
</script>

<template>
  <section class="tamano-texto" aria-labelledby="titulo-tamano-texto">
    <div class="tamano-texto-cabecera">
      <span class="tamano-texto-icono" aria-hidden="true">Aa</span>
      <div>
        <h3 id="titulo-tamano-texto">Tamaño del texto</h3>
        <p>Elige la letra que te resulte más cómoda.</p>
      </div>
    </div>
    <div class="tamano-texto-opciones" role="group" aria-label="Tamaño del texto">
      <button
        v-for="opcion in opcionesTextoAccesible"
        :key="opcion.id"
        type="button"
        :aria-pressed="tamano === opcion.id"
        :class="{ seleccionado: tamano === opcion.id }"
        @click="cambiarTamano(opcion.id)"
      >
        <span :class="`muestra-${opcion.id}`">{{ opcion.muestra }}</span>
        <small>{{ opcion.etiqueta }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.tamano-texto{display:grid;gap:11px;padding:15px 16px;border:1px solid var(--amancay-borde,#e7d5be);border-radius:18px;background:var(--amancay-superficie-variante,#f6e8d3);color:var(--amancay-tinta,#2e1b20)}
.tamano-texto-cabecera{display:flex;align-items:center;gap:11px}
.tamano-texto-icono{display:grid;place-items:center;flex:0 0 40px;height:40px;border-radius:50%;background:#fff;color:var(--amancay-bordo,#6b1e3d);font-family:Georgia,serif;font-size:1rem;font-weight:700}
.tamano-texto h3{margin:0;color:var(--amancay-bordo,#6b1e3d);font-size:.92rem}
.tamano-texto p{margin:3px 0 0;color:var(--amancay-tinta-suave,#7c6a6e);font-size:.82rem;line-height:1.4}
.tamano-texto-opciones{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.tamano-texto-opciones button{display:flex;align-items:center;justify-content:center;gap:8px;min-height:46px;padding:7px;border:1px solid var(--amancay-borde,#e7d5be);border-radius:12px;background:#fff;color:var(--amancay-bordo,#6b1e3d);font:600 .8rem var(--amancay-fuente-cuerpo,system-ui,sans-serif);cursor:pointer}
.tamano-texto-opciones button:hover,.tamano-texto-opciones button:focus-visible{outline:3px solid rgba(107,30,61,.22);outline-offset:2px}
.tamano-texto-opciones button.seleccionado{border:2px solid var(--amancay-bordo,#6b1e3d);background:#fff9f0}
.tamano-texto-opciones button>span{font-family:Georgia,serif;font-size:1.15rem}
.tamano-texto-opciones button>span.muestra-grande{font-size:1.35rem}
.tamano-texto-opciones button>span.muestra-muy-grande{font-size:1.55rem}
@media(max-width:420px){.tamano-texto{padding:13px}.tamano-texto-opciones button{flex-direction:column;gap:3px;min-height:54px}.tamano-texto-opciones button>span{font-size:1rem}.tamano-texto-opciones button>span.muestra-grande{font-size:1.18rem}.tamano-texto-opciones button>span.muestra-muy-grande{font-size:1.36rem}}
</style>
