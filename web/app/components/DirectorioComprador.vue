<template>
  <!-- Ejemplo comprador: directorio de tiendas estilo Amancay (Figma 08/09).
       Copiar a pages/ al inicializar Nuxt. Usa app/assets/amancay-*.css
       y app/types/comprador-publico.ts. Lee tiendasPublicas publicadas. -->
  <div class="am-pagina">
    <header class="am-barra">
      <div class="am-barra-marca">
        <span class="am-barra-logo">✿</span>
        <span><div class="am-barra-sub">Rincón</div><div class="am-barra-titulo">Amancay</div></span>
      </div>
      <span class="am-barra-ayuda">?</span>
    </header>
    <main class="am-contenido">
      <div class="am-etiqueta">Directorio</div>
      <h1 class="am-titulo">¿Qué buscas hoy?</h1>
      <p class="am-bajada">Explora tiendas y encuentra eso que necesitas.</p>
      <input class="am-buscador" :value="busqueda" placeholder="Buscar tienda, producto…" @input="$emit('buscar', $event)" />
      <div class="am-chips">
        <button v-for="t in tiendas" :key="t.id" class="am-tarjeta" @click="$emit('abrir', t.id)">
          <span class="am-miniatura">🏠</span>
          <span><div class="am-etiqueta">{{ t.tipoEmprendimientoId }}</div><div class="am-nombre">{{ t.nombre }}</div></span>
        </button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import type { TiendaPublica } from '~/app/types/comprador-publico';
defineProps<{ tiendas: TiendaPublica[]; busqueda: string }>();
defineEmits<{ (e: 'buscar', ev: Event): void; (e: 'abrir', id: string): void }>();
</script>
