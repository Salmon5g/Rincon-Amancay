<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { combinarDictado } from '~/lib/dictado-producto'

type ResultadoReconocimiento = { results: ArrayLike<ArrayLike<{ transcript: string }>> }
type ErrorReconocimiento = { error: string }
type Reconocimiento = {
  lang: string
  interimResults: boolean
  maxAlternatives: number
  continuous: boolean
  onresult: ((evento: ResultadoReconocimiento) => void) | null
  onerror: ((evento: ErrorReconocimiento) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
  abort: () => void
  processLocally?: boolean
}
type OpcionesLocales = { langs: string[]; processLocally: boolean }
type ConstructorReconocimiento = (new () => Reconocimiento) & {
  available?: (opciones: OpcionesLocales) => Promise<string>
  install?: (opciones: OpcionesLocales) => Promise<boolean>
}

const CLAVE_MODO_LOCAL = 'amancay-dictado-local'
const IDIOMAS_LOCALES = ['es-CL', 'es-419', 'es-US', 'es-ES', 'es-MX', 'es']
const ALTERNATIVA_WINDOWS = 'También puedes usar el dictado de Windows: haz clic en el campo y presiona la tecla Windows + H.'

const props = defineProps<{ valor: string | number | null | undefined; etiqueta: string; numerico?: boolean; deshabilitado?: boolean }>()
const emit = defineEmits<{ actualizar: [valor: string]; aviso: [mensaje: string] }>()

const disponible = ref(false)
const escuchando = ref(false)
const preparando = ref(false)
let reconocimiento: Reconocimiento | null = null

function constructorDisponible(): ConstructorReconocimiento | null {
  if (!import.meta.client) return null
  const ventana = window as unknown as { SpeechRecognition?: ConstructorReconocimiento; webkitSpeechRecognition?: ConstructorReconocimiento }
  return ventana.SpeechRecognition ?? ventana.webkitSpeechRecognition ?? null
}

onMounted(() => { disponible.value = Boolean(constructorDisponible()) })

function detener() {
  reconocimiento?.abort()
  reconocimiento = null
  escuchando.value = false
}

onBeforeUnmount(detener)

function mensajeError(codigo: string): string {
  if (codigo === 'not-allowed') return 'Permite el uso del micrófono en el navegador para dictar.'
  if (codigo === 'no-speech') return 'No se escuchó nada. Toca el micrófono e inténtalo de nuevo.'
  if (codigo === 'audio-capture') return 'No encontramos un micrófono conectado.'
  return `No se pudo usar el dictado. Inténtalo de nuevo. ${ALTERNATIVA_WINDOWS}`
}

function modoLocalActivo() {
  try { return sessionStorage.getItem(CLAVE_MODO_LOCAL) === '1' } catch { return false }
}

function cambiarModoLocal(activo: boolean) {
  try {
    if (activo) sessionStorage.setItem(CLAVE_MODO_LOCAL, '1')
    else sessionStorage.removeItem(CLAVE_MODO_LOCAL)
  } catch {}
}

async function idiomaLocal(Constructor: ConstructorReconocimiento): Promise<string | null> {
  if (typeof Constructor.available !== 'function') return null
  for (const idioma of IDIOMAS_LOCALES) {
    const opciones = { langs: [idioma], processLocally: true }
    let estado = ''
    try { estado = await Constructor.available(opciones) } catch { continue }
    if (estado === 'available') return idioma
    if ((estado === 'downloadable' || estado === 'downloading') && typeof Constructor.install === 'function') {
      emit('aviso', 'Descargando el español para dictar con este equipo. Puede tardar un momento…')
      try {
        if (await Constructor.install(opciones)) return idioma
      } catch {}
    }
  }
  return null
}

function errorDeServicio(local: boolean, Constructor: ConstructorReconocimiento) {
  if (!local && typeof Constructor.available === 'function') {
    cambiarModoLocal(true)
    emit('aviso', 'El servicio de voz del navegador no respondió. Toca el micrófono otra vez para dictar con el reconocimiento de este equipo.')
    return
  }
  cambiarModoLocal(false)
  emit('aviso', `El servicio de voz de este navegador no está disponible (suele pasar en Brave, Opera o redes que lo bloquean). Prueba en Chrome o Edge. ${ALTERNATIVA_WINDOWS}`)
}

async function alternar() {
  if (props.deshabilitado || preparando.value) return
  if (escuchando.value) {
    reconocimiento?.stop()
    return
  }
  const Constructor = constructorDisponible()
  if (!Constructor) return
  window.dispatchEvent(new CustomEvent('amancay-dictado-inicio'))
  const local = modoLocalActivo()
  let idioma = 'es-CL'
  if (local) {
    preparando.value = true
    const encontrado = await idiomaLocal(Constructor).finally(() => { preparando.value = false })
    if (!encontrado) {
      errorDeServicio(true, Constructor)
      return
    }
    idioma = encontrado
  }
  const nuevo = new Constructor()
  if (local) nuevo.processLocally = true
  nuevo.lang = idioma
  nuevo.interimResults = false
  nuevo.maxAlternatives = 1
  nuevo.continuous = false
  nuevo.onresult = (evento) => {
    const texto = Array.from(evento.results).map((resultado) => resultado[0]?.transcript ?? '').join(' ')
    const combinado = combinarDictado(String(props.valor ?? ''), texto, Boolean(props.numerico))
    if (combinado === null) {
      emit('aviso', props.numerico ? `No entendí la cifra para «${props.etiqueta}». Dila de nuevo, por ejemplo «2500» o «uno dos tres».` : 'No se reconoció texto. Inténtalo de nuevo.')
      return
    }
    emit('aviso', '')
    emit('actualizar', combinado)
  }
  nuevo.onerror = (evento) => {
    if (evento.error === 'aborted') return
    if (['network', 'service-not-allowed', 'language-not-supported'].includes(evento.error)) errorDeServicio(local, Constructor)
    else emit('aviso', mensajeError(evento.error))
  }
  nuevo.onend = () => {
    if (reconocimiento === nuevo) {
      reconocimiento = null
      escuchando.value = false
    }
  }
  reconocimiento = nuevo
  try {
    nuevo.start()
    escuchando.value = true
  } catch {
    reconocimiento = null
    emit('aviso', 'No se pudo iniciar el dictado. Inténtalo de nuevo.')
  }
}

function alIniciarOtro() {
  if (escuchando.value) detener()
}

onMounted(() => window.addEventListener('amancay-dictado-inicio', alIniciarOtro))
onBeforeUnmount(() => window.removeEventListener('amancay-dictado-inicio', alIniciarOtro))
</script>

<template>
  <button
    v-if="disponible"
    type="button"
    class="boton-dictado"
    :class="{ escuchando }"
    :disabled="deshabilitado || preparando"
    :aria-busy="preparando"
    :aria-pressed="escuchando"
    :aria-label="escuchando ? `Detener dictado de ${etiqueta}` : `Dictar ${etiqueta}`"
    :title="escuchando ? 'Escuchando… toca para detener' : `Dictar ${etiqueta}`"
    @click.prevent.stop="alternar"
  >
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z" /></svg>
  </button>
</template>

<style scoped>
.boton-dictado{position:absolute;top:50%;right:6px;display:grid;place-items:center;width:42px;height:42px;padding:0;border:0;border-radius:50%;background:transparent;color:var(--vino,#74244e);cursor:pointer;transform:translateY(-50%);transition:background .15s ease,color .15s ease}
.boton-dictado svg{width:22px;height:22px;fill:currentColor}
.boton-dictado:hover:not(:disabled){background:#f4e6ea}
.boton-dictado:focus-visible{outline:3px solid rgba(116,36,78,.35);outline-offset:1px}
.boton-dictado:disabled{opacity:.45;cursor:default}
.boton-dictado.escuchando{background:var(--vino,#74244e);color:#fff;animation:latido 1.2s ease-in-out infinite}
@keyframes latido{0%,100%{box-shadow:0 0 0 0 rgba(116,36,78,.35)}50%{box-shadow:0 0 0 8px rgba(116,36,78,0)}}
@media(prefers-reduced-motion:reduce){.boton-dictado.escuchando{animation:none}}
</style>
