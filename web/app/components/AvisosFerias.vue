<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { AccesoComprador, SesionComprador } from '~/lib/acceso-comprador'
import { pedirAccesoComprador } from '~/lib/acceso-comprador-ui'
import type { AvisosFerias } from '~/lib/avisos-ferias'

// Preferencia de avisos de ferias del comprador. El plugin de Firebase es solo
// de navegador: los servicios se piden dentro de onMounted y de los manejadores.
const nuxt = useNuxtApp()
const acceso = () => nuxt.$accesoComprador as AccesoComprador
const avisos = () => nuxt.$avisosFerias as AvisosFerias

const listo = ref(false)
const sesion = ref<SesionComprador | null>(null)
const activo = ref(false)
const guardando = ref(false)
const aviso = ref('')
const error = ref('')
const navegadorDisponible = ref(false)
let dejarDeObservarSesion: (() => void) | undefined
let dejarDeObservarAvisos: (() => void) | undefined

function observarAvisos() {
  dejarDeObservarAvisos?.()
  dejarDeObservarAvisos = undefined
  activo.value = false
  if (!sesion.value) return
  dejarDeObservarAvisos = avisos().observar(valor => {
    activo.value = valor
  })
}

onMounted(() => {
  navegadorDisponible.value = avisos().soportaNavegador()
  dejarDeObservarSesion = acceso().observar(nueva => {
    sesion.value = nueva
    listo.value = true
    observarAvisos()
  })
})

onBeforeUnmount(() => {
  dejarDeObservarSesion?.()
  dejarDeObservarAvisos?.()
})

const descripcion = computed(() => {
  if (!listo.value) return 'Comprobando tu sesión…'
  if (!sesion.value) {
    return 'Inicia sesión para recibir un aviso cuando una tienda o asociación que sigues confirme su participación en una feria.'
  }
  if (activo.value) {
    return navegadorDisponible.value
      ? 'Te avisaremos por notificación del navegador y, si no está disponible, por correo.'
      : 'Te avisaremos por correo cuando una tienda o asociación que sigues confirme su participación.'
  }
  return navegadorDisponible.value
    ? 'Activa las notificaciones del navegador o recibe el aviso por correo.'
    : 'Recibe un correo cuando una tienda o asociación que sigues confirme su participación en una feria.'
})

const textoBoton = computed(() => {
  if (!listo.value) return 'Comprobando…'
  if (!sesion.value) return 'Iniciar sesión'
  return activo.value ? 'Desactivar avisos' : 'Activar avisos'
})

async function alternar() {
  aviso.value = ''
  error.value = ''
  if (!sesion.value) {
    pedirAccesoComprador()
    return
  }
  if (guardando.value) return
  guardando.value = true
  try {
    if (activo.value) {
      await avisos().desactivar()
      activo.value = false
      aviso.value = 'Desactivaste los avisos de ferias.'
    } else {
      const canal = await avisos().activar()
      activo.value = true
      aviso.value = canal === 'navegador'
        ? 'Avisos activos: te avisaremos por notificación del navegador.'
        : 'Avisos activos: te avisaremos por correo.'
    }
  } catch (causa) {
    const codigo = (causa as { code?: string })?.code
    error.value = codigo === 'no-autenticado'
      ? 'Inicia sesión para activar los avisos.'
      : codigo === 'permission-denied'
        ? 'No se pudo guardar la preferencia. Inténtalo de nuevo.'
        : (causa as Error).message || 'No se pudo cambiar la preferencia de avisos.'
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <section class="avisos" aria-labelledby="titulo-avisos-ferias">
    <div class="avisos-texto">
      <p class="avisos-sobretitulo">AVISOS DE FERIAS</p>
      <h2 id="titulo-avisos-ferias" class="avisos-titulo">Entérate cuando participen</h2>
      <p class="avisos-bajada">{{ descripcion }}</p>
    </div>
    <button
      class="avisos-boton"
      :class="{ activo }"
      type="button"
      :aria-pressed="activo"
      :disabled="guardando || !listo"
      @click="alternar"
    >
      <span aria-hidden="true">{{ activo ? '🔔' : '🔕' }}</span>
      {{ guardando ? 'Guardando…' : textoBoton }}
    </button>
    <p v-if="aviso" class="avisos-nota" role="status" aria-live="polite">{{ aviso }}</p>
    <p v-if="error" class="avisos-error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.avisos {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
  margin: 1.25rem 0;
  padding: 1rem 1.15rem;
  border: 1px solid var(--amancay-borde);
  border-radius: var(--amancay-radio-tarjeta);
  background: var(--amancay-superficie);
}

.avisos-texto {
  flex: 1 1 20rem;
  min-width: 0;
}

.avisos-sobretitulo {
  margin: 0 0 0.15rem;
  color: var(--amancay-tinta-suave);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.avisos-titulo {
  margin: 0 0 0.2rem;
  color: var(--amancay-bordo);
  font-family: var(--amancay-fuente-titulo);
  font-size: 1.15rem;
}

.avisos-bajada {
  margin: 0;
  color: var(--amancay-tinta-suave);
  font-size: 0.85rem;
  line-height: 1.45;
}

.avisos-boton {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  min-height: 44px;
  padding: 0.55rem 1.1rem;
  border: 1.5px solid var(--amancay-bordo);
  border-radius: var(--amancay-radio-chip);
  background: transparent;
  color: var(--amancay-bordo);
  font: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
}

.avisos-boton:hover:not(:disabled) {
  background: var(--amancay-bordo-suave);
}

.avisos-boton.activo {
  background: var(--amancay-bordo);
  color: #fff;
}

.avisos-boton:disabled {
  cursor: wait;
  opacity: 0.6;
}

.avisos-boton:focus-visible {
  outline: 3px solid var(--amancay-mostaza);
  outline-offset: 2px;
}

.avisos-nota,
.avisos-error {
  flex: 1 1 100%;
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.4;
}

.avisos-nota {
  color: var(--amancay-tinta-suave);
}

.avisos-error {
  color: #8c1d2f;
}
</style>
