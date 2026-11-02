<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { mensajeErrorAcceso } from '~/lib/acceso-comprador'
import type { AccesoComprador, ResultadoAcceso, SesionComprador } from '~/lib/acceso-comprador'

// Botón de acceso del comprador + diálogo. Navegar el catálogo nunca depende de esto.
// El plugin firebase.client.ts es solo de navegador: el servicio se pide dentro de
// onMounted y de los manejadores, nunca durante el render del servidor.
type Vista = 'ingreso' | 'registro' | 'recuperar' | 'verificacion'

const nuxt = useNuxtApp()
const servicio = () => nuxt.$accesoComprador as AccesoComprador

const listo = ref(false)
const sesion = ref<SesionComprador | null>(null)
const vista = ref<Vista>('ingreso')
const dialogo = ref<HTMLDialogElement | null>(null)

const nombre = ref('')
const correo = ref('')
const clave = ref('')
const repetirClave = ref('')
const correoPendiente = ref('')
const error = ref('')
const aviso = ref('')
const enviando = ref(false)
let desconectar: (() => void) | undefined

function limpiarMensajes() {
  error.value = ''
  aviso.value = ''
}

function abrir(destino: Vista) {
  limpiarMensajes()
  vista.value = destino
  if (destino === 'verificacion' && sesion.value) correoPendiente.value = sesion.value.correo
  if (!dialogo.value?.open) dialogo.value?.showModal()
}

function cerrar() {
  clave.value = ''
  repetirClave.value = ''
  dialogo.value?.close()
}

function cambiarVista(destino: Vista) {
  limpiarMensajes()
  vista.value = destino
}

function alClickearFondo(evento: MouseEvent) {
  if (evento.target === dialogo.value) cerrar()
}

function manejarResultado(resultado: ResultadoAcceso) {
  sesion.value = servicio().sesionActual()
  if (resultado.estado === 'verificacion-pendiente') {
    correoPendiente.value = resultado.correo
    vista.value = 'verificacion'
    aviso.value = resultado.envioFallido
      ? 'Tu cuenta se creó, pero no pudimos enviar el correo. Toca “Reenviar correo”.'
      : ''
    return
  }
  cerrar()
}

async function ejecutar(accion: () => Promise<ResultadoAcceso>) {
  if (enviando.value) return
  limpiarMensajes()
  enviando.value = true
  try {
    manejarResultado(await accion())
  } catch (causa) {
    sesion.value = servicio().sesionActual()
    error.value = mensajeErrorAcceso(causa)
  } finally {
    enviando.value = false
  }
}

const ingresarConCorreo = () => ejecutar(() => servicio().iniciarConCorreo(correo.value, clave.value))
const ingresarConGoogle = () => ejecutar(() => servicio().iniciarConGoogle())
const comprobarVerificacion = async () => {
  await ejecutar(() => servicio().comprobarVerificacion())
  if (!error.value && dialogo.value?.open && vista.value === 'verificacion') aviso.value = 'Todavía no vemos tu correo verificado. Abre el enlace que te enviamos y vuelve a intentarlo.'
}
const registrar = () => ejecutar(() => servicio().registrarConCorreo({
  nombre: nombre.value, correo: correo.value, clave: clave.value, repetirClave: repetirClave.value,
}))

async function reenviar() {
  if (enviando.value) return
  limpiarMensajes()
  enviando.value = true
  try {
    await servicio().reenviarVerificacion()
    aviso.value = 'Listo, te enviamos el correo otra vez. Revisa también la carpeta de spam.'
  } catch (causa) {
    error.value = mensajeErrorAcceso(causa)
  } finally {
    enviando.value = false
  }
}

async function recuperar() {
  if (enviando.value) return
  limpiarMensajes()
  enviando.value = true
  try {
    aviso.value = (await servicio().solicitarRecuperacion(correo.value)).mensaje
  } catch (causa) {
    error.value = mensajeErrorAcceso(causa)
  } finally {
    enviando.value = false
  }
}

async function salir() {
  limpiarMensajes()
  try {
    await servicio().cerrarSesion()
    cerrar()
  } catch (causa) {
    error.value = mensajeErrorAcceso(causa)
  }
}

onMounted(() => {
  desconectar = servicio().observar(nueva => {
    sesion.value = nueva
    listo.value = true
  })
})
onBeforeUnmount(() => desconectar?.())
</script>

<template>
  <div class="acceso">
    <p v-if="!listo" class="acceso-nota" aria-live="polite">&nbsp;</p>

    <button v-else-if="!sesion" class="acceso-abrir" type="button" @click="abrir('ingreso')">
      Ingresar o crear cuenta
    </button>

    <div v-else class="acceso-sesion">
      <p class="acceso-nombre">{{ sesion.nombre }}</p>
      <p v-if="!sesion.verificado" class="acceso-nota">Falta confirmar tu correo</p>
      <p v-else class="acceso-nota">{{ sesion.correo }}</p>
      <div class="acceso-acciones">
        <button v-if="!sesion.verificado" class="acceso-secundario" type="button" @click="abrir('verificacion')">Confirmar correo</button>
        <button class="acceso-secundario" type="button" @click="salir">Cerrar sesión</button>
      </div>
      <p v-if="error" class="acceso-error" role="alert">{{ error }}</p>
    </div>

    <dialog ref="dialogo" class="acceso-dialogo" aria-labelledby="acceso-titulo" @click="alClickearFondo">
      <div class="acceso-caja">
        <button class="acceso-cerrar" type="button" aria-label="Cerrar" @click="cerrar">✕</button>

        <!-- Ingreso -->
        <form v-if="vista === 'ingreso'" @submit.prevent="ingresarConCorreo">
          <h2 id="acceso-titulo">Ingresa a tu cuenta</h2>
          <p class="acceso-bajada">Puedes seguir mirando las tiendas sin cuenta. Ingresa solo para seguir tiendas o calificar.</p>
          <button class="acceso-google" type="button" :disabled="enviando" @click="ingresarConGoogle">
            <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
              <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.1C12.4 13.5 17.7 9.5 24 9.5z"/>
              <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4.1 7.1-10.1 7.1-17.5z"/>
              <path fill="#FBBC05" d="M10.5 28.7A14.5 14.5 0 0 1 9.5 24c0-1.6.3-3.2.8-4.7l-7.9-6.1A24 24 0 0 0 0 24c0 3.9.9 7.5 2.6 10.8l7.9-6.1z"/>
              <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.1 1.4-4.8 2.3-8.4 2.3-6.3 0-11.6-4-13.5-9.8l-7.9 6.1C6.5 42.6 14.6 48 24 48z"/>
            </svg>
            Continuar con Google
          </button>
          <p class="acceso-separador"><span>o con tu correo</span></p>
          <label for="acceso-correo">Correo</label>
          <input id="acceso-correo" v-model="correo" type="email" autocomplete="email" required />
          <label for="acceso-clave">Contraseña</label>
          <input id="acceso-clave" v-model="clave" type="password" autocomplete="current-password" required />
          <p v-if="error" class="acceso-error" role="alert">{{ error }}</p>
          <button class="acceso-primario" type="submit" :disabled="enviando">{{ enviando ? 'Ingresando…' : 'Ingresar' }}</button>
          <p class="acceso-enlaces">
            <button type="button" @click="cambiarVista('recuperar')">Olvidé mi contraseña</button>
            <button type="button" @click="cambiarVista('registro')">Crear cuenta</button>
          </p>
        </form>

        <!-- Registro -->
        <form v-else-if="vista === 'registro'" @submit.prevent="registrar">
          <h2 id="acceso-titulo">Crea tu cuenta</h2>
          <p class="acceso-bajada">Te enviaremos un correo para confirmar que es tuyo.</p>
          <button class="acceso-google" type="button" :disabled="enviando" @click="ingresarConGoogle">
            <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
              <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.1C12.4 13.5 17.7 9.5 24 9.5z"/>
              <path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.5 5.8c4.4-4.1 7.1-10.1 7.1-17.5z"/>
              <path fill="#FBBC05" d="M10.5 28.7A14.5 14.5 0 0 1 9.5 24c0-1.6.3-3.2.8-4.7l-7.9-6.1A24 24 0 0 0 0 24c0 3.9.9 7.5 2.6 10.8l7.9-6.1z"/>
              <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.1 1.4-4.8 2.3-8.4 2.3-6.3 0-11.6-4-13.5-9.8l-7.9 6.1C6.5 42.6 14.6 48 24 48z"/>
            </svg>
            Registrarme con Google
          </button>
          <p class="acceso-separador"><span>o con tu correo</span></p>
          <label for="acceso-nombre">Nombre</label>
          <input id="acceso-nombre" v-model="nombre" type="text" autocomplete="name" maxlength="100" required />
          <label for="acceso-correo-reg">Correo</label>
          <input id="acceso-correo-reg" v-model="correo" type="email" autocomplete="email" required />
          <label for="acceso-clave-reg">Contraseña (mínimo 6 caracteres)</label>
          <input id="acceso-clave-reg" v-model="clave" type="password" autocomplete="new-password" minlength="6" required />
          <label for="acceso-repetir">Repite la contraseña</label>
          <input id="acceso-repetir" v-model="repetirClave" type="password" autocomplete="new-password" minlength="6" required />
          <p v-if="error" class="acceso-error" role="alert">{{ error }}</p>
          <button class="acceso-primario" type="submit" :disabled="enviando">{{ enviando ? 'Creando cuenta…' : 'Crear cuenta' }}</button>
          <p class="acceso-enlaces">
            <button type="button" @click="cambiarVista('ingreso')">Ya tengo cuenta</button>
          </p>
        </form>

        <!-- Recuperar contraseña -->
        <form v-else-if="vista === 'recuperar'" @submit.prevent="recuperar">
          <h2 id="acceso-titulo">Recupera tu contraseña</h2>
          <p class="acceso-bajada">Escribe tu correo y te enviaremos un enlace para crear una contraseña nueva.</p>
          <label for="acceso-correo-rec">Correo</label>
          <input id="acceso-correo-rec" v-model="correo" type="email" autocomplete="email" required />
          <p v-if="error" class="acceso-error" role="alert">{{ error }}</p>
          <p v-if="aviso" class="acceso-aviso" role="status">{{ aviso }}</p>
          <button class="acceso-primario" type="submit" :disabled="enviando">{{ enviando ? 'Enviando…' : 'Enviar enlace' }}</button>
          <p class="acceso-enlaces">
            <button type="button" @click="cambiarVista('ingreso')">Volver a ingresar</button>
          </p>
        </form>

        <!-- Confirmar correo -->
        <div v-else>
          <h2 id="acceso-titulo">Confirma tu correo</h2>
          <p class="acceso-bajada">
            Te enviamos un correo a <b>{{ correoPendiente }}</b>. Ábrelo, toca el enlace y vuelve a esta página.
          </p>
          <p v-if="error" class="acceso-error" role="alert">{{ error }}</p>
          <p v-if="aviso" class="acceso-aviso" role="status">{{ aviso }}</p>
          <button class="acceso-primario" type="button" :disabled="enviando" @click="comprobarVerificacion">
            {{ enviando ? 'Comprobando…' : 'Ya confirmé mi correo' }}
          </button>
          <p class="acceso-enlaces">
            <button type="button" :disabled="enviando" @click="reenviar">Reenviar correo</button>
            <button type="button" @click="salir">Cerrar sesión</button>
          </p>
        </div>
      </div>
    </dialog>
  </div>
</template>

<style scoped>
.acceso { margin-top: 0.7rem; font-family: inherit; }
.acceso-nota { margin: 0; font-size: 0.78rem; color: var(--suave, #89727d); overflow-wrap: anywhere; }
.acceso-nombre { margin: 0; font-weight: 700; font-size: 0.9rem; color: var(--tinta, #3a1228); overflow-wrap: anywhere; }
.acceso-sesion { padding: 0.7rem; background: var(--crema, #fff8ef); border-radius: 16px; }
.acceso-acciones { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.6rem; }

.acceso button { font-family: inherit; cursor: pointer; }
.acceso button:disabled { opacity: 0.6; cursor: not-allowed; }
.acceso button:focus-visible, .acceso input:focus-visible { outline: 3px solid var(--ambar, #f6c15b); outline-offset: 2px; }

.acceso-abrir, .acceso-primario {
  width: 100%; border: none; border-radius: 14px; padding: 0.8rem 1rem;
  background: var(--vino, #75264f); color: #fff; font-size: 0.92rem; font-weight: 600;
}
.acceso-abrir:hover, .acceso-primario:hover:not(:disabled) { background: var(--vino-oscuro, #5a1c3c); }
.acceso-secundario {
  border: 1px solid var(--borde, #ead9cc); border-radius: 12px; padding: 0.45rem 0.8rem;
  background: #fff; color: var(--vino, #75264f); font-size: 0.8rem; font-weight: 600;
}

.acceso-dialogo {
  border: none; padding: 0; border-radius: 20px; width: min(420px, calc(100vw - 2rem));
  max-height: calc(100vh - 2rem); overflow: auto; color: var(--tinta, #3a1228);
  background: var(--crema, #fff8ef); box-shadow: 0 20px 60px rgba(58, 18, 40, 0.25);
}
.acceso-dialogo::backdrop { background: rgba(58, 18, 40, 0.45); }
.acceso-caja { position: relative; padding: 1.5rem 1.4rem 1.3rem; }
.acceso-cerrar {
  position: absolute; top: 0.7rem; right: 0.7rem; width: 2.2rem; height: 2.2rem;
  border: none; border-radius: 50%; background: var(--rosado, #f7e9e8); color: var(--vino, #75264f);
}
h2 { margin: 0 2rem 0.3rem 0; font-family: 'DM Serif Display', Georgia, serif; font-weight: 400; font-size: 1.5rem; color: var(--vino, #75264f); }
.acceso-bajada { margin: 0 0 1rem; font-size: 0.88rem; line-height: 1.4; color: var(--suave, #89727d); }

label { display: block; margin: 0.8rem 0 0.25rem; font-size: 0.82rem; font-weight: 600; }
input {
  width: 100%; box-sizing: border-box; padding: 0.7rem 0.85rem; font: inherit; font-size: 0.95rem;
  border: 1px solid var(--borde, #ead9cc); border-radius: 12px; background: #fff; color: inherit;
}
.acceso-primario { margin-top: 1.1rem; }

.acceso-google {
  display: flex; align-items: center; justify-content: center; gap: 0.6rem; width: 100%;
  padding: 0.75rem 1rem; border: 1px solid var(--borde, #ead9cc); border-radius: 14px;
  background: #fff; color: var(--tinta, #3a1228); font-size: 0.92rem; font-weight: 600;
}
.acceso-google:hover:not(:disabled) { background: var(--rosado, #f7e9e8); }
.acceso-separador { display: flex; align-items: center; gap: 0.7rem; margin: 1rem 0 0.2rem; font-size: 0.78rem; color: var(--suave, #89727d); }
.acceso-separador::before, .acceso-separador::after { content: ''; flex: 1; height: 1px; background: var(--borde, #ead9cc); }

.acceso-error { margin: 0.8rem 0 0; padding: 0.6rem 0.8rem; font-size: 0.84rem; border-radius: 10px; background: #fde8e8; color: #8a1c1c; }
.acceso-aviso { margin: 0.8rem 0 0; padding: 0.6rem 0.8rem; font-size: 0.84rem; border-radius: 10px; background: #e8f3e1; color: #2f6b2e; }
.acceso-enlaces { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.5rem; margin: 1rem 0 0; }
.acceso-enlaces button { border: none; background: none; padding: 0.3rem 0; color: var(--vino, #75264f); font-size: 0.85rem; font-weight: 600; text-decoration: underline; }

@media (prefers-reduced-motion: no-preference) {
  .acceso-abrir, .acceso-primario, .acceso-google { transition: background 0.15s; }
}
</style>