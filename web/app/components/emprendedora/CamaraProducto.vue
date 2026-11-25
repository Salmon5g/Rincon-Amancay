<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps<{ titulo?: string }>()
const emit = defineEmits<{ capturada: [archivo: File]; cerrar: [] }>()

const video = ref<HTMLVideoElement | null>(null)
const dialogo = ref<HTMLElement | null>(null)
const estado = ref<'iniciando' | 'lista' | 'error'>('iniciando')
const mensaje = ref('')
const camaras = ref<MediaDeviceInfo[]>([])
const camaraActual = ref('')
const capturando = ref(false)
let flujo: MediaStream | null = null

function detenerFlujo() {
  flujo?.getTracks().forEach((pista) => pista.stop())
  flujo = null
}

function mensajeError(fallo: unknown): string {
  const nombre = fallo instanceof DOMException ? fallo.name : ''
  if (nombre === 'NotAllowedError' || nombre === 'SecurityError') return 'Permite el acceso a la cámara en el navegador para tomar la foto.'
  if (nombre === 'NotFoundError' || nombre === 'OverconstrainedError') return 'No encontramos una cámara conectada a este equipo.'
  if (nombre === 'NotReadableError') return 'La cámara está siendo usada por otra aplicación. Ciérrala e inténtalo de nuevo.'
  return 'No se pudo abrir la cámara. Puedes elegir una foto desde tus archivos.'
}

async function iniciar(deviceId?: string) {
  estado.value = 'iniciando'
  mensaje.value = ''
  detenerFlujo()
  try {
    if (!navigator.mediaDevices?.getUserMedia) throw new DOMException('Sin cámara', 'NotFoundError')
    flujo = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: deviceId
        ? { deviceId: { exact: deviceId }, width: { ideal: 1920 }, height: { ideal: 1440 } }
        : { facingMode: 'environment', width: { ideal: 1920 }, height: { ideal: 1440 } },
    })
    camaraActual.value = flujo.getVideoTracks()[0]?.getSettings().deviceId ?? deviceId ?? ''
    const dispositivos = await navigator.mediaDevices.enumerateDevices().catch(() => [])
    camaras.value = dispositivos.filter((dispositivo) => dispositivo.kind === 'videoinput')
    await nextTick()
    if (video.value) {
      video.value.srcObject = flujo
      await video.value.play().catch(() => {})
    }
    estado.value = 'lista'
  } catch (fallo) {
    detenerFlujo()
    estado.value = 'error'
    mensaje.value = mensajeError(fallo)
  }
}

function cambiarCamara() {
  if (camaras.value.length < 2) return
  const indice = camaras.value.findIndex((camara) => camara.deviceId === camaraActual.value)
  const siguiente = camaras.value[(indice + 1) % camaras.value.length]
  if (siguiente) void iniciar(siguiente.deviceId)
}

async function capturar() {
  const elemento = video.value
  if (!elemento || estado.value !== 'lista' || capturando.value || !elemento.videoWidth) return
  capturando.value = true
  try {
    const lienzo = document.createElement('canvas')
    lienzo.width = elemento.videoWidth
    lienzo.height = elemento.videoHeight
    const contexto = lienzo.getContext('2d')
    if (!contexto) throw new Error('sin-contexto')
    contexto.drawImage(elemento, 0, 0, lienzo.width, lienzo.height)
    const blob = await new Promise<Blob | null>((resolve) => lienzo.toBlob(resolve, 'image/jpeg', 0.92))
    if (!blob) throw new Error('sin-imagen')
    emit('capturada', new File([blob], `foto-camara-${Date.now()}.jpg`, { type: 'image/jpeg', lastModified: Date.now() }))
  } catch {
    mensaje.value = 'No se pudo tomar la foto. Inténtalo de nuevo.'
  } finally {
    capturando.value = false
  }
}

function alTeclado(evento: KeyboardEvent) {
  if (evento.key === 'Escape') emit('cerrar')
}

onMounted(() => {
  document.addEventListener('keydown', alTeclado)
  dialogo.value?.focus()
  void iniciar()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', alTeclado)
  detenerFlujo()
})
</script>

<template>
  <Teleport to="body">
    <div class="fondo-camara" @click.self="emit('cerrar')">
      <section ref="dialogo" class="dialogo-camara" role="dialog" aria-modal="true" :aria-label="props.titulo ?? 'Tomar foto'" tabindex="-1">
        <header>
          <div><span>CÁMARA</span><h3>{{ props.titulo ?? 'Tomar foto del producto' }}</h3></div>
          <button type="button" class="cerrar-camara" aria-label="Cerrar cámara" @click="emit('cerrar')">✕</button>
        </header>
        <div class="visor-camara">
          <video ref="video" playsinline muted aria-label="Vista previa de la cámara" />
          <div v-if="estado === 'lista'" class="guia-camara" aria-hidden="true"><span class="marco-cuadrado" /></div>
          <div v-if="estado === 'iniciando'" class="estado-camara"><span class="rueda-camara" />Abriendo la cámara…</div>
          <div v-else-if="estado === 'error'" class="estado-camara error">{{ mensaje }}</div>
        </div>
        <p class="ayuda-camara">Ubica el producto dentro del cuadro, con luz de frente y un fondo simple.</p>
        <p v-if="estado === 'lista' && mensaje" class="ayuda-camara error" role="alert">{{ mensaje }}</p>
        <footer>
          <button type="button" class="boton-camara secundario" @click="emit('cerrar')">Cancelar</button>
          <button v-if="camaras.length > 1" type="button" class="boton-camara secundario" :disabled="estado === 'iniciando'" @click="cambiarCamara">Cambiar cámara</button>
          <button v-if="estado === 'error'" type="button" class="boton-camara principal" @click="iniciar()">Reintentar</button>
          <button v-else type="button" class="boton-camara principal" :disabled="estado !== 'lista' || capturando" @click="capturar"><span class="punto-captura" />Tomar foto</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.fondo-camara{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:16px;background:rgba(43,20,28,.55);backdrop-filter:blur(4px);animation:aparecer .18s ease}
.dialogo-camara{display:grid;gap:14px;width:min(720px,100%);max-height:calc(100vh - 32px);overflow:auto;padding:20px;border-radius:26px;background:#fffaf3;box-shadow:0 24px 60px rgba(43,20,28,.3);outline:none}
header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
header span{color:#e76b62;font-size:11px;font-weight:800;letter-spacing:.16em}
header h3{margin:4px 0 0;color:#5b1b3c;font:400 28px/1.1 'DM Serif Display',Georgia,serif}
.cerrar-camara{display:grid;place-items:center;width:42px;height:42px;border:1px solid #ecd8cb;border-radius:50%;background:#fff;color:#74244e;font-size:16px;cursor:pointer}
.visor-camara{position:relative;display:grid;place-items:center;overflow:hidden;aspect-ratio:4/3;border-radius:20px;background:#2b1a20}
.visor-camara video{width:100%;height:100%;object-fit:contain}
.guia-camara{position:absolute;inset:0;background-image:linear-gradient(rgba(255,255,255,.28) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.28) 1px,transparent 1px);background-size:33.333% 33.333%;background-position:-1px -1px;pointer-events:none}
.marco-cuadrado{position:absolute;top:50%;left:50%;width:min(72%,calc(100% * 3 / 4 * .9));aspect-ratio:1;border:2px solid rgba(255,248,240,.95);border-radius:14px;box-shadow:0 0 0 999px rgba(20,8,12,.28);transform:translate(-50%,-50%)}
.estado-camara{position:absolute;display:flex;align-items:center;gap:10px;max-width:80%;color:#fff;font-size:14px;text-align:center}
.estado-camara.error{color:#ffe1d6}
.rueda-camara{width:18px;height:18px;border:2px solid #ffffff60;border-top-color:#fff;border-radius:50%;animation:girar .8s linear infinite}
.ayuda-camara{margin:0;color:#7a6468;font-size:13px;text-align:center}
.ayuda-camara.error{color:#852e3f}
footer{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:10px}
.boton-camara{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:48px;padding:0 20px;border-radius:15px;font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-camara.secundario{border:1px solid #ecd8cb;background:#fff;color:#74244e}
.boton-camara.principal{border:1px solid #74244e;background:#74244e;color:#fff}
.boton-camara:disabled{opacity:.5;cursor:default}
.boton-camara:focus-visible,.cerrar-camara:focus-visible{outline:3px solid rgba(116,36,78,.35);outline-offset:2px}
.punto-captura{width:14px;height:14px;border:2px solid #fff;border-radius:50%;background:#f3a07f}
@keyframes girar{to{transform:rotate(360deg)}}
@keyframes aparecer{from{opacity:0}to{opacity:1}}
@media(max-width:560px){.dialogo-camara{padding:16px;border-radius:22px}header h3{font-size:24px}footer{display:grid;grid-template-columns:1fr 1fr}footer .principal{grid-column:1/-1}}
@media(prefers-reduced-motion:reduce){.fondo-camara{animation:none}}
</style>
