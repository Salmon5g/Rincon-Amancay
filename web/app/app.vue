<script setup lang="ts">
import { computed } from 'vue'

const ruta = useRoute()
// La política de acceso solo aplica a las rutas del comprador.
const esComprador = computed(() => ruta.path === '/comprador' || ruta.path.startsWith('/comprador/'))
</script>

<template>
  <ControlNavegacionComprador v-if="esComprador">
    <NuxtPage />
  </ControlNavegacionComprador>
  <NuxtPage v-else />
</template>
