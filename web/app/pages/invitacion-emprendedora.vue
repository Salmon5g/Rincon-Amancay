<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onAuthStateChanged, type User } from 'firebase/auth'

useHead({
  title: 'Invitación para emprendedoras — Rincón Amancay',
  link: [
    { rel: 'icon', type: 'image/png', href: '/favicon.png' },
    { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
    { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
    { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap' },
  ],
})

const route = useRoute()
const { $amancay } = useNuxtApp()
const config = useRuntimeConfig().public
const invitacionId = computed(() => String(route.query.invitacionId ?? ''))
const idValido = computed(() => /^[a-f0-9]{64}$/.test(invitacionId.value))
const servicioDisponible = computed(() => ['local', 'real-api'].includes(config.firebaseModo))
const clienteInicializado = ref(false)
const usuario = ref<User | null>(null)
const modoFormulario = ref<'registro' | 'ingreso'>('registro')
const correo = ref('')
const clave = ref('')
const repetirClave = ref('')
const fase = ref<'cargando' | 'acceso' | 'verificacion' | 'completada'>('cargando')
const tienda = ref('')
const error = ref('')
const aviso = ref('')
const enviando = ref(false)
let desconectar: (() => void) | undefined
let uidProcesando = ''

function mensajeError(errorRecibido: unknown) {
  const codigo = (errorRecibido as { code?: string })?.code ?? ''
  if (codigo === 'auth/email-already-in-use') return 'Ese correo ya tiene una cuenta. Cambia a “Ya tengo cuenta” e inicia sesión.'
  if (codigo === 'auth/invalid-credential' || codigo === 'auth/user-not-found' || codigo === 'auth/wrong-password') return 'No se pudo iniciar sesión. Revisa el correo y la contraseña.'
  if (codigo === 'auth/weak-password') return 'La contraseña debe tener al menos 6 caracteres.'
  if (codigo === 'auth/too-many-requests') return 'Hubo varios intentos seguidos. Espera un momento antes de probar otra vez.'
  if (codigo === 'auth/unauthorized-continue-uri' || codigo === 'auth/unauthorized-domain') return 'Firebase todavía no autoriza el dominio de esta página para enlaces de verificación.'
  if (codigo === 'sin-permiso') return 'El correo verificado de esta sesión no coincide con la invitación. Cierra sesión e ingresa con el correo destinatario.'
  if (codigo === 'no-encontrado') return 'La invitación no está disponible. Pide a la administradora que revise o vuelva a emitirla.'
  if (codigo === 'conflicto') return 'La invitación venció, fue cancelada o los datos asociados cambiaron. Contacta a la administradora.'
  if (codigo === 'conexion-interrumpida' || (errorRecibido as { resultadoIncierto?: boolean })?.resultadoIncierto) return 'No pudimos confirmar el resultado. Comprueba tu conexión y vuelve a revisar la invitación.'
  if (codigo === 'auth/network-request-failed' || codigo === 'unavailable') return 'No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.'
  return 'No se pudo completar el proceso. Revisa los datos e inténtalo de nuevo.'
}

async function completarInvitacion(persona: User) {
  if (!idValido.value || uidProcesando === persona.uid || fase.value === 'completada') return
  uidProcesando = persona.uid
  enviando.value = true
  error.value = ''
  aviso.value = ''
  try {
    await persona.reload()
    const actual = $amancay.firebase.auth.currentUser
    if (!actual || actual.uid !== persona.uid) throw new Error('La sesión cambió. Inicia sesión nuevamente con el correo invitado.')
    if (!actual.emailVerified) {
      fase.value = 'verificacion'
      return
    }
    await actual.getIdToken(true)
    const invitacion = await $amancay.llamar('consultarInvitacion', { invitacionId: invitacionId.value })
    tienda.value = typeof invitacion.nombreTienda === 'string' ? invitacion.nombreTienda : 'tu tienda'
    if (invitacion.estado === 'aceptada') {
      fase.value = 'completada'
      return
    }
    if (invitacion.estado !== 'pendiente') {
      throw Object.assign(new Error('La invitación no está pendiente.'), { code: 'conflicto' })
    }
    await $amancay.llamar('aceptarInvitacion', { invitacionId: invitacionId.value })
    fase.value = 'completada'
  } catch (fallo) {
    fase.value = 'acceso'
    error.value = mensajeError(fallo)
  } finally {
    enviando.value = false
    uidProcesando = ''
  }
}

async function enviarVerificacion() {
  error.value = ''
  aviso.value = ''
  enviando.value = true
  try {
    if (!usuario.value) throw new Error('Inicia sesión primero.')
    const continuarUrl = new URL('/invitacion-emprendedora', window.location.origin)
    continuarUrl.searchParams.set('invitacionId', invitacionId.value)
    const continuar = continuarUrl.toString()
    await $amancay.enviarVerificacion(continuar)
    fase.value = 'verificacion'
    aviso.value = `Enviamos el enlace de verificación a ${usuario.value.email ?? 'tu correo'}. Al abrirlo volverás aquí para terminar.`
  } catch (fallo) {
    error.value = mensajeError(fallo)
  } finally {
    enviando.value = false
  }
}

async function comprobarVerificacion() {
  if (usuario.value) await completarInvitacion(usuario.value)
}

async function acceder() {
  error.value = ''
  aviso.value = ''
  const direccion = correo.value.trim().toLowerCase()
  if (!direccion) {
    error.value = 'Escribe el correo al que llegó la invitación.'
    return
  }
  if (modoFormulario.value === 'registro' && clave.value !== repetirClave.value) {
    error.value = 'Las contraseñas no coinciden.'
    return
  }
  enviando.value = true
  try {
    const credencial = modoFormulario.value === 'registro'
      ? await $amancay.crearIdentidad(direccion, clave.value)
      : await $amancay.iniciarSesion(direccion, clave.value)
    usuario.value = credencial.user
    correo.value = ''
    clave.value = ''
    repetirClave.value = ''
    if (credencial.user.emailVerified) await completarInvitacion(credencial.user)
    else fase.value = 'verificacion'
  } catch (fallo) {
    error.value = mensajeError(fallo)
  } finally {
    enviando.value = false
  }
}

async function cambiarCuenta() {
  error.value = ''
  aviso.value = ''
  try {
    await $amancay.cerrarSesion()
    usuario.value = null
    fase.value = 'acceso'
  } catch (fallo) {
    error.value = mensajeError(fallo)
  }
}

onMounted(() => {
  clienteInicializado.value = true
  if (!idValido.value || !servicioDisponible.value) {
    fase.value = 'acceso'
    return
  }
  desconectar = onAuthStateChanged($amancay.firebase.auth, (persona) => {
    usuario.value = persona
    if (persona) void completarInvitacion(persona)
    else fase.value = 'acceso'
  })
})

onBeforeUnmount(() => desconectar?.())
</script>

<template>
  <main class="pagina-invitacion">
    <header class="barra">
      <a class="marca" href="/comprador" aria-label="Rincón Amancay, volver al catálogo">
        <img src="/logo-amancay.png" alt="" />
        <span><small>Rincón</small><b>Amancay</b></span>
      </a>
      <a class="volver" href="/comprador">Volver al catálogo</a>
    </header>

    <section class="tarjeta" aria-labelledby="titulo-invitacion">
      <div class="flor" aria-hidden="true">🌼</div>
      <p class="sobretitulo">Acceso de emprendedora</p>
      <h1 id="titulo-invitacion">Te invitaron a Rincón Amancay</h1>

      <p v-if="!clienteInicializado" class="mensaje" role="status">Cargando invitación…</p>
      <p v-else-if="!idValido" class="mensaje error" role="alert">El enlace no tiene un identificador válido. Pide a la administradora que revise la invitación.</p>
      <p v-else-if="!servicioDisponible" class="mensaje" role="status">Esta invitación requiere que el equipo habilite la API autenticada.</p>
      <p v-else-if="fase === 'cargando'" class="mensaje" role="status">Comprobando tu sesión…</p>

      <template v-else-if="fase === 'completada'">
        <div class="mensaje exito" role="status">
          <strong>¡Listo{{ tienda ? `, ${tienda}` : '' }}!</strong>
          <span>Tu cuenta quedó vinculada a la tienda. Inicia sesión en la aplicación para completar tu ficha y comenzar a administrarla.</span>
        </div>
        <a class="boton principal" href="/comprador">Ir al catálogo</a>
      </template>

      <template v-else-if="usuario">
        <p class="intro">Usa el correo exacto al que enviaron la invitación. La contraseña la eliges tú; no se comparte con la administradora.</p>
        <div class="cuenta-actual">
          <span>Sesión actual</span>
          <strong>{{ usuario.email || 'Correo no disponible' }}</strong>
        </div>
        <p v-if="fase === 'verificacion' || !usuario.emailVerified" class="mensaje" role="status">
          Confirma tu correo desde el enlace que te enviamos. Después vuelve a esta página y el alta terminará automáticamente.
        </p>
        <div class="acciones">
          <button v-if="!usuario.emailVerified" class="boton principal" type="button" :disabled="enviando" @click="enviarVerificacion">
            {{ enviando ? 'Enviando…' : 'Enviar o reenviar verificación' }}
          </button>
          <button class="boton secundario" type="button" :disabled="enviando" @click="comprobarVerificacion">Ya verifiqué mi correo</button>
          <button class="enlace" type="button" :disabled="enviando" @click="cambiarCuenta">Cerrar esta sesión y usar otra cuenta</button>
        </div>
      </template>

      <template v-else>
        <p class="intro">Crea tu acceso o inicia sesión con el correo exacto al que enviaron la invitación. Tu contraseña es privada: la eliges tú.</p>
        <div class="selector-modo" role="group" aria-label="Tipo de acceso">
          <button type="button" :class="{ seleccionado: modoFormulario === 'registro' }" @click="modoFormulario = 'registro'; error = ''">Crear mi acceso</button>
          <button type="button" :class="{ seleccionado: modoFormulario === 'ingreso' }" @click="modoFormulario = 'ingreso'; error = ''">Ya tengo cuenta</button>
        </div>
        <form class="formulario" @submit.prevent="acceder">
          <label>Correo electrónico
            <input v-model="correo" type="email" autocomplete="email" required maxlength="254" :disabled="enviando" />
          </label>
          <label>Contraseña
            <input v-model="clave" type="password" :autocomplete="modoFormulario === 'registro' ? 'new-password' : 'current-password'" minlength="6" required :disabled="enviando" />
          </label>
          <label v-if="modoFormulario === 'registro'">Repite la contraseña
            <input v-model="repetirClave" type="password" autocomplete="new-password" minlength="6" required :disabled="enviando" />
          </label>
          <button class="boton principal" type="submit" :disabled="enviando">
            {{ enviando ? 'Procesando…' : modoFormulario === 'registro' ? 'Crear acceso seguro' : 'Iniciar sesión' }}
          </button>
        </form>
      </template>

      <p v-if="aviso" class="mensaje exito" role="status">{{ aviso }}</p>
      <p v-if="error" class="mensaje error" role="alert">{{ error }}</p>
      <p class="nota-seguridad">La invitación solo se acepta desde el correo destinatario una vez verificado. El enlace por sí solo no concede permisos.</p>
    </section>

    <footer>Rincón Amancay · Emprendimientos locales</footer>
  </main>
</template>

<style scoped>
.pagina-invitacion {
  --vino: #75264f;
  --vino-oscuro: #5a1c3c;
  --crema: #fff8ef;
  --crema-2: #fdf1d4;
  --durazno: #f5b183;
  --tinta: #3a1228;
  --borde: #ead9cc;
  min-height: 100vh;
  padding: 1rem clamp(1rem, 4vw, 3rem) 2rem;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--tinta);
  background: radial-gradient(850px 420px at 100% 0, var(--crema-2), transparent 70%), var(--crema);
  font-family: 'Plus Jakarta Sans', Arial, sans-serif;
}
.barra { width: min(100%, 1120px); display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.marca { display: flex; align-items: center; gap: .65rem; color: var(--vino); text-decoration: none; }
.marca img { width: 48px; height: 48px; object-fit: contain; }
.marca span { display: grid; line-height: 1.1; }
.marca small { color: #907c83; font-size: .72rem; }
.marca b { font-family: 'DM Serif Display', Georgia, serif; font-size: 1.35rem; font-weight: 400; }
.volver, .enlace { color: var(--vino); font-weight: 700; text-decoration: none; }
.volver { padding: .7rem 1rem; border: 1px solid var(--borde); border-radius: 999px; background: #fff; }
.tarjeta { width: min(100%, 540px); margin: auto 0; padding: clamp(1.35rem, 5vw, 2.5rem); box-sizing: border-box; background: rgba(255,255,255,.95); border: 1px solid var(--borde); border-radius: 28px; box-shadow: 0 18px 45px rgba(58,18,40,.1); }
.flor { display: grid; place-items: center; width: 54px; height: 54px; margin-bottom: 1rem; border-radius: 50%; background: var(--crema-2); font-size: 1.7rem; }
.sobretitulo { margin: 0 0 .35rem; color: var(--vino); font-size: .72rem; font-weight: 700; letter-spacing: .09em; text-transform: uppercase; }
h1 { margin: 0; color: var(--vino); font-family: 'DM Serif Display', Georgia, serif; font-size: clamp(2rem, 7vw, 2.6rem); font-weight: 400; line-height: 1.08; }
.intro { margin: .9rem 0 1.1rem; color: #76666e; line-height: 1.6; }
.selector-modo { display: grid; grid-template-columns: 1fr 1fr; gap: .45rem; margin: 1rem 0; padding: .35rem; border-radius: 999px; background: var(--crema); }
.selector-modo button { min-height: 44px; border: 0; border-radius: 999px; color: #76666e; background: transparent; font: inherit; font-size: .83rem; font-weight: 700; cursor: pointer; }
.selector-modo button.seleccionado { color: #fff; background: var(--vino); }
.formulario { display: grid; gap: .9rem; }
.formulario label { display: grid; gap: .4rem; font-size: .86rem; font-weight: 700; }
.formulario input { width: 100%; min-height: 48px; padding: .7rem .85rem; box-sizing: border-box; border: 1.5px solid var(--borde); border-radius: 12px; color: var(--tinta); background: #fff; font: inherit; }
.formulario input:focus-visible { outline: 2px solid var(--vino); outline-offset: 2px; }
.boton { display: inline-flex; min-height: 48px; align-items: center; justify-content: center; padding: .7rem 1.2rem; border: 1.5px solid var(--vino); border-radius: 999px; font: inherit; font-weight: 700; text-decoration: none; cursor: pointer; }
.boton:disabled { opacity: .6; cursor: wait; }
.principal { color: #fff; background: var(--vino); box-shadow: 0 6px 16px rgba(117,38,79,.2); }
.principal:hover { background: var(--vino-oscuro); }
.secundario { color: var(--vino); background: #fff; }
.acciones { display: grid; gap: .7rem; margin-top: 1rem; }
.enlace { min-height: 42px; border: 0; background: transparent; font: inherit; cursor: pointer; }
.cuenta-actual { display: grid; gap: .2rem; margin: 1rem 0; padding: .8rem 1rem; border: 1px solid var(--borde); border-radius: 14px; background: var(--crema); overflow-wrap: anywhere; }
.cuenta-actual span { color: #76666e; font-size: .76rem; }
.mensaje { display: grid; gap: .35rem; margin: 1rem 0; padding: .85rem 1rem; border-radius: 14px; background: var(--crema-2); color: #745117; font-size: .88rem; line-height: 1.5; }
.mensaje.exito { background: #edf6e8; color: #355b2f; }
.mensaje.error { background: #f8e9e9; color: #a12f3c; }
.nota-seguridad { margin: 1.2rem 0 0; padding-top: 1rem; border-top: 1px solid var(--borde); color: #86777e; font-size: .75rem; line-height: 1.5; }
footer { margin-top: 2rem; color: #907c83; font-size: .75rem; }
@media (max-width: 480px) { .pagina-invitacion { padding-inline: .75rem; } .volver { padding: .6rem .7rem; font-size: .78rem; } }
</style>
