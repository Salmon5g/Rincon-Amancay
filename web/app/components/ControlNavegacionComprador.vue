<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { mensajeErrorAcceso } from '~/lib/acceso-comprador'
import type { AccesoComprador, SesionComprador } from '~/lib/acceso-comprador'
import type { PoliticaNavegacion } from '~/repositories/catalogo-publico'

// Aplica la política de navegación elegida en Administración → Configuración.
// El plugin de Firebase es solo de navegador, así que la lectura ocurre al montar;
// mientras tanto se muestra el contenido para conservar el HTML estático.
const nuxt = useNuxtApp()
const modo = ref<PoliticaNavegacion>('permitir')
const sesion = ref<SesionComprador | null>(null)
const omitido = ref(false)
const enviando = ref(false)
const error = ref('')
let desconectar: (() => void) | undefined

const bloqueado = computed(() => modo.value === 'exigir' && !sesion.value)
const sugerencia = computed(() => modo.value === 'invitar' && !sesion.value && !omitido.value)

function leerOmitido() {
  try { return sessionStorage.getItem('amancay-omitir-acceso') === '1' } catch { return false }
}

async function iniciarConGoogle() {
  if (enviando.value) return
  enviando.value = true; error.value = ''
  try { await (nuxt.$accesoComprador as AccesoComprador).iniciarConGoogle() }
  catch (causa) { error.value = mensajeErrorAcceso(causa) }
  finally { enviando.value = false }
}

function omitir() {
  omitido.value = true
  try { sessionStorage.setItem('amancay-omitir-acceso', '1') } catch { /* almacenamiento no disponible */ }
}

onMounted(async () => {
  omitido.value = leerOmitido()
  const acceso = nuxt.$accesoComprador as AccesoComprador
  sesion.value = acceso.sesionActual()
  desconectar = acceso.observar((nueva) => { sesion.value = nueva })
  try { modo.value = await nuxt.$catalogoPublico.leerPoliticaNavegacion() }
  catch { modo.value = 'permitir' }
})
onBeforeUnmount(() => desconectar?.())
</script>

<template>
  <section v-if="bloqueado" class="puerta" aria-live="polite">
    <div class="tarjeta">
      <p class="sobre">Acceso del comprador</p>
      <h1>Inicia sesión para continuar</h1>
      <p>La administración pidió que las personas compradoras inicien sesión antes de recorrer la vitrina.</p>
      <button class="boton" type="button" :disabled="enviando" @click="iniciarConGoogle">{{ enviando ? 'Abriendo Google…' : 'Continuar con Google' }}</button>
      <p class="nota">Al ingresar se crea o reutiliza tu cuenta de comprador.</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </div>
  </section>
  <template v-else>
    <div v-if="sugerencia" class="sugerencia" role="status">
      <p>Puedes recorrer la vitrina libremente. Si inicias sesión con Google, guardamos tu cuenta de comprador.</p>
      <div class="acciones">
        <button class="boton" type="button" :disabled="enviando" @click="iniciarConGoogle">{{ enviando ? 'Abriendo Google…' : 'Iniciar sesión con Google' }}</button>
        <button class="secundario" type="button" :disabled="enviando" @click="omitir">Omitir por ahora</button>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </div>
    <slot />
  </template>
</template>

<style scoped>
.puerta{min-height:100vh;display:grid;place-items:center;padding:24px;background:#fff8ef;color:#3a1228}
.tarjeta{max-width:460px;background:#fff;border:1px solid #ead9cc;border-radius:24px;padding:28px;display:grid;gap:14px;justify-items:start}
.tarjeta h1{font:400 1.8rem 'DM Serif Display',serif;color:#75264f;margin:0}
.tarjeta p{margin:0;color:#6d5663;line-height:1.55}
.sobre{font-size:.76rem;text-transform:uppercase;letter-spacing:.12em;color:#75264f;font-weight:700}
.boton,.secundario{border:0;border-radius:999px;padding:11px 18px;font:inherit;font-weight:700;cursor:pointer}
.boton{background:#75264f;color:#fff}
.secundario{background:#f4e7ed;color:#75264f}
.sugerencia{display:grid;gap:10px;padding:14px 18px;background:#fff4e7;border-bottom:1px solid #ead9cc;color:#3a1228}
.sugerencia p{margin:0;color:#6d5663}
.sugerencia .acciones{display:flex;gap:10px;flex-wrap:wrap}
.error{color:#a32335}
button:disabled{opacity:.6;cursor:not-allowed}
</style>
