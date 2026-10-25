<template>
  <!-- Ejemplo comprador: detalle de producto estilo Figma 09 "Así ven tu catálogo".
       Copiar a pages/ al inicializar Nuxt. La foto real (Storage) reemplaza .am-foto. -->
  <div class="am-pagina">
    <header class="am-barra">
      <div class="am-barra-marca">
        <span class="am-barra-logo">✿</span>
        <span><div class="am-barra-sub">Rincón</div><div class="am-barra-titulo">Amancay</div></span>
      </div>
      <span class="am-barra-ayuda">?</span>
    </header>
    <main class="am-contenido">
      <div class="am-foto">🏺</div>
      <div class="am-etiqueta">{{ producto.categoriaId }}</div>
      <h1 class="am-titulo">{{ producto.nombre }}</h1>
      <p class="am-bajada">{{ producto.descripcion }}</p>
      <p class="am-precio">{{ precioTexto }}</p>
      <span class="am-badge">{{ disponibilidad }}</span>
      <div class="am-chips">
        <button
          v-for="v in variantes"
          :key="v.id"
          class="am-chip"
          :class="{ activo: v.id === seleccionada }"
          @click="$emit('elegir', v.id)"
        >{{ nombreVariante(v.opciones) }}</button>
      </div>
      <button class="am-boton-mostaza">Contactar a la tienda</button>
      <p class="am-desc">El precio y la disponibilidad los define la tienda. El stock exacto no se muestra.</p>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ProductoPublico, VariantePublica } from '~/app/types/comprador-publico';
import { etiquetaDisponibilidad, nombreVariante } from '~/app/types/comprador-publico';

const props = defineProps<{ producto: ProductoPublico; variantes: VariantePublica[]; seleccionada?: string }>();
defineEmits<{ (e: 'elegir', id: string): void }>();
const disponibilidad = computed(() => etiquetaDisponibilidad(props.producto.estadoDisponibilidad));
const precioTexto = computed(() => {
  if (props.producto.tieneVariantes) return 'Precio según variante';
  return props.producto.precioBase ?? 'Consultar precio';
});
</script>
