<script setup lang="ts">
import { onMounted, ref } from 'vue'

useHead({
  title: 'Restablecer contraseña — Rincón Amancay',
  htmlAttrs: { lang: 'es' },
  meta: [
    { name: 'referrer', content: 'no-referrer' },
    { name: 'robots', content: 'noindex, nofollow, noarchive' },
  ],
  link: [{ rel: 'icon', type: 'image/png', href: '/favicon.png' }],
})

type Estado = 'verificando' | 'listo' | 'error' | 'guardado'
type ModoAccion = 'resetPassword' | 'verifyEmail' | 'recoverEmail'
const { $accesoComprador } = useNuxtApp()
const config = useRuntimeConfig().public
const estado = ref<Estado>('verificando')
const modoAccion = ref<ModoAccion | ''>('')
const codigo = ref('')
const correo = ref('')
const clave = ref('')
const repetirClave = ref('')
const error = ref('')
const guardando = ref(false)

const titulo = computed(() => {
  if (estado.value === 'error') return 'Revisa tu enlace'
  if (estado.value === 'guardado') {
    if (modoAccion.value === 'verifyEmail') return 'Correo verificado'
    if (modoAccion.value === 'recoverEmail') return 'Correo recuperado'
    return 'Contraseña actualizada'
  }
  return 'Crea una contraseña nueva'
})
const descripcion = computed(() => {
  if (estado.value === 'verificando') return 'Estamos comprobando el enlace de forma segura…'
  if (estado.value === 'listo') return 'Escribe una contraseña nueva para volver a entrar a Rincón Amancay.'
  if (estado.value === 'error') return error.value
  if (modoAccion.value === 'verifyEmail') return 'La dirección de correo quedó verificada. Ya puedes iniciar sesión.'
  if (modoAccion.value === 'recoverEmail') return `La cuenta volvió a usar ${correo.value}. Inicia sesión nuevamente con esa dirección.`
  return 'Tu contraseña quedó actualizada. Por seguridad, vuelve a iniciar sesión con ella.'
})

onMounted(async () => {
  const parametros = new URLSearchParams(window.location.search)
  const modo = parametros.get('mode')
  const codigoAccion = parametros.get('oobCode') ?? ''
  const apiKey = parametros.get('apiKey') ?? ''

  // Evita dejar el código de un solo uso visible en la barra e historial del navegador.
  window.history.replaceState(window.history.state, '', window.location.pathname)

  if (!['resetPassword', 'verifyEmail', 'recoverEmail'].includes(modo ?? '') || !codigoAccion
    || (apiKey && config.firebaseApiKey && apiKey !== config.firebaseApiKey)) {
    estado.value = 'error'
    error.value = 'Este enlace no es válido para la acción solicitada.'
    return
  }

  codigo.value = codigoAccion
  modoAccion.value = modo as ModoAccion
  try {
    if (modoAccion.value === 'resetPassword') {
      correo.value = await $accesoComprador.comprobarCodigoRecuperacion(codigoAccion)
      estado.value = 'listo'
    } else if (modoAccion.value === 'verifyEmail') {
      correo.value = await $accesoComprador.confirmarVerificacionCorreo(codigoAccion)
      codigo.value = ''
      estado.value = 'guardado'
    } else {
      correo.value = await $accesoComprador.recuperarDireccionCorreo(codigoAccion)
      codigo.value = ''
      estado.value = 'guardado'
    }
  } catch {
    estado.value = 'error'
    error.value = modoAccion.value === 'resetPassword'
      ? 'El enlace venció o ya fue utilizado. Solicita uno nuevo desde Rincón Amancay.'
      : 'El enlace venció, ya fue utilizado o ya no es válido. Vuelve a la aplicación y solicita uno nuevo si corresponde.'
    codigo.value = ''
  }
})

async function guardarNuevaClave() {
  error.value = ''
  if (clave.value.length < 6) {
    error.value = 'La contraseña debe tener al menos 6 caracteres.'
    return
  }
  if (clave.value !== repetirClave.value) {
    error.value = 'Las contraseñas no coinciden.'
    return
  }

  guardando.value = true
  try {
    await $accesoComprador.confirmarRecuperacion(codigo.value, clave.value)
    codigo.value = ''
    clave.value = ''
    repetirClave.value = ''
    estado.value = 'guardado'
  } catch (fallo) {
    const code = (fallo as { code?: string })?.code
    error.value = code === 'auth/weak-password'
      ? 'Firebase considera que la contraseña es demasiado débil. Prueba con una más segura.'
      : code === 'auth/expired-action-code' || code === 'auth/invalid-action-code'
        ? 'El enlace venció o ya fue utilizado. Solicita uno nuevo desde Rincón Amancay.'
        : 'No se pudo guardar la nueva contraseña. Revisa los datos e inténtalo nuevamente.'
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <main class="recuperacion">
    <section class="tarjeta" aria-labelledby="titulo">
      <header class="marca">
        <img src="/logo-amancay.png" alt="" width="66" height="66" />
        <span class="marca-texto"><small>Rincón</small><strong>Amancay</strong></span>
      </header>

      <div class="contenido">
        <p class="sobrelinea">ACCESO SEGURO</p>
        <h1 id="titulo">
          {{ titulo }}
        </h1>
        <p class="descripcion" :class="{ exito: estado === 'guardado', error: estado === 'error' }" :role="estado === 'verificando' ? 'status' : 'status'">{{ descripcion }}</p>

        <form v-if="estado === 'listo'" class="formulario" @submit.prevent="guardarNuevaClave">
          <label class="correo">Cuenta <strong>{{ correo }}</strong></label>
          <label for="clave-nueva">Nueva contraseña
            <input id="clave-nueva" v-model="clave" type="password" autocomplete="new-password" minlength="6" maxlength="128" required />
          </label>
          <label for="repetir-clave">Repite la contraseña
            <input id="repetir-clave" v-model="repetirClave" type="password" autocomplete="new-password" minlength="6" maxlength="128" required />
          </label>
          <p class="ayuda">Usa al menos 6 caracteres y evita compartirla con otras personas.</p>
          <p v-if="error" class="mensaje-error" role="alert">{{ error }}</p>
          <button class="boton-principal" type="submit" :disabled="guardando">
            {{ guardando ? 'Guardando…' : 'Guardar nueva contraseña' }}
          </button>
        </form>

        <div v-if="estado === 'guardado'" class="acciones">
          <NuxtLink class="boton-principal enlace-boton" :to="modoAccion === 'recoverEmail' ? '/admin' : '/comprador'">Ir a Rincón Amancay</NuxtLink>
          <NuxtLink class="enlace-secundario" to="/admin">Acceso de administración</NuxtLink>
        </div>
        <div v-else-if="estado === 'error'" class="acciones">
          <NuxtLink class="boton-principal enlace-boton" to="/comprador">Volver a Rincón Amancay</NuxtLink>
          <NuxtLink class="enlace-secundario" to="/admin">Acceso de administración</NuxtLink>
        </div>
      </div>

      <footer>Un espacio para descubrir y apoyar los emprendimientos locales.</footer>
    </section>
  </main>
</template>

<style scoped>
.recuperacion {
  --fondo: #fbf3e4;
  --superficie: #fffbf2;
  --bordo: #6b1e3d;
  --bordo-oscuro: #4e142c;
  --tinta: #2e1b20;
  --suave: #7c6a6e;
  --borde: #e7d5be;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 28px 16px;
  color: var(--tinta);
  background: radial-gradient(ellipse at 50% 0%, #fbe3b8 0, transparent 42%), var(--fondo);
  font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
}
.tarjeta {
  width: min(100%, 480px);
  overflow: hidden;
  border: 1px solid var(--borde);
  border-radius: 26px;
  background: var(--superficie);
  box-shadow: 0 20px 60px #4e142c1a;
}
.marca {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 28px;
  border-bottom: 1px solid var(--borde);
  background: #fffdf8;
}
.marca img { width: 66px; height: 66px; object-fit: contain; }
.marca-texto { display: grid; line-height: 1.05; color: var(--bordo); }
.marca-texto small { color: var(--suave); font-size: 14px; }
.marca-texto strong { font: 700 25px Georgia, "Times New Roman", serif; }
.contenido { padding: 30px 32px 32px; }
.sobrelinea { margin: 0 0 8px; color: var(--bordo); font-size: 12px; font-weight: 800; letter-spacing: .13em; }
h1 { margin: 0; color: var(--bordo); font: 400 clamp(27px, 6vw, 34px)/1.14 Georgia, "Times New Roman", serif; }
.descripcion { margin: 13px 0 24px; color: var(--suave); line-height: 1.55; }
.formulario { display: grid; gap: 16px; }
.formulario label { display: grid; gap: 8px; color: var(--tinta); font-size: 14px; font-weight: 700; }
.formulario input { width: 100%; min-height: 48px; padding: 12px 14px; border: 1px solid var(--borde); border-radius: 13px; outline: none; background: #fff; color: var(--tinta); font: inherit; }
.formulario input:focus { border-color: var(--bordo); box-shadow: 0 0 0 3px #6b1e3d20; }
.correo { padding: 13px 14px; border-radius: 13px; background: #f3dee3; color: var(--suave) !important; font-weight: 500 !important; overflow-wrap: anywhere; }
.correo strong { color: var(--bordo); }
.ayuda { margin: -7px 0 0; color: var(--suave); font-size: 12px; line-height: 1.5; }
.mensaje-error { margin: 0; color: #a32335; font-size: 14px; line-height: 1.5; }
.error { color: #a32335; }
.exito { color: #2f6b2e; }
.boton-principal { display: inline-flex; min-height: 50px; align-items: center; justify-content: center; border: 0; border-radius: 16px; padding: 12px 18px; background: var(--bordo); color: white; font: inherit; font-weight: 750; text-align: center; text-decoration: none; cursor: pointer; }
.boton-principal:hover { background: var(--bordo-oscuro); }
.boton-principal:focus-visible,.enlace-secundario:focus-visible { outline: 3px solid #f2b73b; outline-offset: 3px; }
.boton-principal:disabled { opacity: .65; cursor: wait; }
.acciones { display: grid; gap: 15px; margin-top: 26px; }
.enlace-secundario { justify-self: center; color: var(--bordo); font-size: 14px; font-weight: 700; }
footer { padding: 16px 22px; border-top: 1px solid var(--borde); color: var(--suave); background: #f6e8d3; font-size: 12px; line-height: 1.45; text-align: center; }
@media (max-width: 480px) { .contenido { padding: 26px 22px 28px; }.marca { padding: 20px 22px; } }
</style>
