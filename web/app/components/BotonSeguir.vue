<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { AccesoComprador, SesionComprador } from '~/lib/acceso-comprador'
import { pedirAccesoComprador } from '~/lib/acceso-comprador-ui'
import type { SeguimientosComprador, TipoSeguimiento } from '~/lib/seguimientos-comprador'

const props = withDefaults(defineProps<{
  tipo: TipoSeguimiento
  entidadId: string
  nombre?: string
}>(), { nombre: '' })

// El plugin de Firebase es solo de navegador: los servicios se piden dentro de
// onMounted y de los manejadores, nunca durante el render del servidor.
const nuxt = useNuxtApp()
const acceso = () => nuxt.$accesoComprador as AccesoComprador
const seguimientos = () => nuxt.$seguimientosComprador as SeguimientosComprador

const listo = ref(false)
const sesion = ref<SesionComprador | null>(null)
const siguiendo = ref(false)
const guardando = ref(false)
const aviso = ref('')
let dejarDeObservarSesion: (() => void) | undefined
let dejarDeObservarSeguimiento: (() => void) | undefined

const etiquetaEntidad = computed(() => props.tipo === 'tienda' ? 'tienda' : 'asociación')
const textoBoton = computed(() => siguiendo.value ? 'Siguiendo ✓' : `Seguir ${etiquetaEntidad.value}`)

function observarSeguimiento() {
  dejarDeObservarSeguimiento?.()
  dejarDeObservarSeguimiento = undefined
  siguiendo.value = false
  if (!sesion.value) return
  dejarDeObservarSeguimiento = seguimientos().observar(props.tipo, props.entidadId, valor => {
    siguiendo.value = valor
  })
}

onMounted(() => {
  dejarDeObservarSesion = acceso().observar(nueva => {
    sesion.value = nueva
    listo.value = true
    observarSeguimiento()
  })
})

onBeforeUnmount(() => {
  dejarDeObservarSesion?.()
  dejarDeObservarSeguimiento?.()
})

async function alternar() {
  aviso.value = ''
  if (!sesion.value) {
    pedirAccesoComprador()
    return
  }
  if (guardando.value) return
  guardando.value = true
  try {
    siguiendo.value = await seguimientos().alternar(props.tipo, props.entidadId)
    const referencia = props.nombre ? `a ${props.nombre}` : `esta ${etiquetaEntidad.value}`
    aviso.value = siguiendo.value
      ? `Ahora sigues ${referencia}.`
      : `Dejaste de seguir ${referencia}.`
  } catch (error) {
    const codigo = (error as { code?: string })?.code
    aviso.value = codigo === 'no-autenticado'
      ? 'Inicia sesión para seguir.'
      : codigo === 'permission-denied'
        ? 'No se pudo guardar el seguimiento. La tienda o asociación debe estar publicada.'
        : codigo === 'unavailable'
          ? 'No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.'
          : (error as Error).message || 'No se pudo guardar el seguimiento.'
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <div class="seguimiento">
    <button
      class="seguimiento-boton"
      :class="{ activo: siguiendo }"
      type="button"
      :aria-pressed="siguiendo"
      :disabled="guardando || !listo"
      @click="alternar"
    >
      <span aria-hidden="true">{{ siguiendo ? '♥' : '♡' }}</span>
      {{ guardando ? 'Guardando…' : textoBoton }}
    </button>
    <p v-if="!listo" class="seguimiento-nota">Comprobando tu sesión…</p>
    <p v-else-if="!sesion" class="seguimiento-nota">Inicia sesión para seguir esta {{ etiquetaEntidad }}.</p>
    <p v-else-if="aviso" class="seguimiento-nota" role="status" aria-live="polite">{{ aviso }}</p>
  </div>
</template>

<style scoped>
.seguimiento {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.35rem;
}

.seguimiento-boton {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  min-height: 42px;
  padding: 0.55rem 1rem;
  border: 1.5px solid var(--amancay-bordo);
  border-radius: 999px;
  background: transparent;
  color: var(--amancay-bordo);
  font: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
}

.seguimiento-boton:hover:not(:disabled) {
  background: var(--amancay-bordo-suave, #f3dee3);
}

.seguimiento-boton.activo {
  background: var(--amancay-bordo);
  color: #fff;
}

.seguimiento-boton:disabled {
  cursor: wait;
  opacity: 0.6;
}

.seguimiento-boton:focus-visible {
  outline: 3px solid var(--amancay-mostaza);
  outline-offset: 2px;
}

.seguimiento-nota {
  margin: 0;
  max-width: 34ch;
  color: var(--amancay-tinta-suave);
  font-size: 0.78rem;
  line-height: 1.4;
}
</style>
