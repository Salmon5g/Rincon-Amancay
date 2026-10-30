<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { guardarConfiguracionNavegacion, leerConfiguracionNavegacion } from '~/repositories/admin'
import type { ConfiguracionNavegacion, ModoNavegacionComprador } from '~/types/admin'

const { $amancay } = useNuxtApp()
const modoFirebase = useRuntimeConfig().public.firebaseModo
const habilitado = ['local', 'real-api'].includes(modoFirebase)
const configuracion = ref<ConfiguracionNavegacion | null>(null)
const modo = ref<ModoNavegacionComprador>('permitir')
const cargando = ref(false)
const enviando = ref(false)
const error = ref('')
const aviso = ref('')
const pendiente = ref<{ operacion: 'guardarConfiguracionNavegacion'; solicitud: Record<string, unknown> } | null>(null)

function explicar(cause: unknown) {
  const code = (cause as { code?: string })?.code
  if (code === 'conflicto') return 'Otra sesión cambió la configuración. Vuelve a cargarla antes de editar.'
  if (code === 'sin-permiso') return 'La API no reconoce una cuenta administradora activa.'
  if (code === 'conexion-interrumpida' || (cause as { resultadoIncierto?: boolean })?.resultadoIncierto) return 'No se confirmó el guardado. Reintenta la misma solicitud sin cambiar el modo.'
  return 'No se pudo guardar la configuración. Revisa la conexión y las reglas.'
}

async function cargar() {
  if (!habilitado) return
  cargando.value = true; error.value = ''; aviso.value = ''
  try { configuracion.value = await leerConfiguracionNavegacion($amancay); modo.value = configuracion.value.modo }
  catch (cause) { error.value = explicar(cause) }
  finally { cargando.value = false }
}

async function enviar() {
  if (!pendiente.value || enviando.value) return
  enviando.value = true; error.value = ''; aviso.value = ''
  try {
    await $amancay.llamar(pendiente.value.operacion, pendiente.value.solicitud)
    pendiente.value = null; aviso.value = 'La preferencia se guardó en Firebase.'
    await cargar()
  } catch (cause) {
    error.value = explicar(cause)
    if (!(cause as { resultadoIncierto?: boolean })?.resultadoIncierto) pendiente.value = null
  } finally { enviando.value = false }
}

function guardar() {
  if (!configuracion.value || !habilitado || pendiente.value || modo.value === configuracion.value.modo) return
  pendiente.value = { operacion: 'guardarConfiguracionNavegacion', solicitud: { operacionId: crypto.randomUUID().replaceAll('-', ''), versionEsperada: configuracion.value.versionEsperada, modo: modo.value } }
  void enviar()
}

onMounted(() => { void cargar() })
</script>

<template>
  <section class="configuracion-admin">
    <div class="cabecera">
      <div><p class="sobre">Preferencias de acceso</p><h2>Navegación del comprador</h2><p>Define la política que se guarda en la configuración central de la plataforma.</p></div>
      <button class="secundario" type="button" :disabled="cargando || enviando" @click="cargar">{{ cargando ? 'Leyendo…' : 'Volver a cargar' }}</button>
    </div>
    <p v-if="!habilitado" class="nota" role="status">Este entorno es de solo lectura. Conecta la API autenticada para guardar preferencias.</p>
    <p v-if="cargando" role="status">Leyendo configuración desde Firebase…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="aviso" class="exito" role="status">{{ aviso }}</p>
    <template v-if="configuracion">
      <fieldset :disabled="!habilitado || enviando || !!pendiente">
        <legend>¿Cómo puede entrar una persona compradora?</legend>
        <label class="opcion" :class="{ seleccionada: modo === 'permitir' }"><input v-model="modo" type="radio" value="permitir" /><span><strong>Exploración libre</strong><small>Puede recorrer la vitrina sin crear una cuenta. Es el valor actual y conserva el acceso sencillo desde enlaces y QR.</small></span></label>
        <label class="opcion" :class="{ seleccionada: modo === 'invitar' }"><input v-model="modo" type="radio" value="invitar" /><span><strong>Sugerir que inicie sesión</strong><small>La visita sigue disponible; las pantallas cliente pueden ofrecer iniciar sesión para funciones personalizadas.</small></span></label>
        <label class="opcion" :class="{ seleccionada: modo === 'exigir' }"><input v-model="modo" type="radio" value="exigir" /><span><strong>Solicitar una cuenta</strong><small>Política prevista para una futura experiencia con ingreso de comprador.</small></span></label>
      </fieldset>
      <div class="nota importante"><span aria-hidden="true">ℹ️</span><p>La preferencia queda guardada en <code>configuracionAplicacion/navegacion</code>. Actualmente la vitrina web y la app aún no consumen este valor; hasta integrar ese control en ambas, conserva “Exploración libre”.</p></div>
      <div class="acciones"><button class="boton" type="button" :disabled="!habilitado || enviando || !!pendiente || !configuracion || modo === configuracion.modo" @click="guardar">{{ enviando ? 'Guardando…' : 'Guardar configuración' }}</button><span v-if="configuracion.actualizadoEn">Último cambio: {{ new Date(configuracion.actualizadoEn).toLocaleString('es-CL') }}</span></div>
      <div v-if="pendiente" class="nota pendiente" role="status"><p>No se recibió confirmación. Mantén la opción seleccionada y reintenta la misma operación.</p><button class="boton" type="button" :disabled="enviando" @click="enviar">{{ enviando ? 'Procesando…' : 'Reintentar guardado' }}</button></div>
    </template>
  </section>
</template>

<style scoped>
.configuracion-admin{--vino:#75264f;--vino-oscuro:#5a1c3c;--crema:#fff8ef;--borde:#ead9cc;--texto:#3a1228;color:var(--texto);display:grid;gap:20px}.cabecera{display:flex;align-items:center;justify-content:space-between;gap:16px}.cabecera h2{font:400 1.8rem 'DM Serif Display',serif;color:var(--vino);margin:3px 0}.cabecera p{color:#775f6c;margin:5px 0}.sobre{font-size:.76rem;text-transform:uppercase;letter-spacing:.12em;color:var(--vino)!important;font-weight:700}fieldset{border:1px solid var(--borde);border-radius:20px;background:#fff;padding:20px;display:grid;gap:12px}legend{font-weight:700;color:var(--vino);padding:0 8px}.opcion{display:flex;gap:12px;align-items:flex-start;padding:16px;border:1px solid var(--borde);border-radius:15px;cursor:pointer}.opcion.seleccionada{background:#fff4e7;border-color:#d8a46c}.opcion input{accent-color:var(--vino);margin-top:4px}.opcion span{display:grid;gap:5px}.opcion small{color:#775f6c;line-height:1.5}.nota{background:var(--crema);border:1px solid var(--borde);border-radius:18px;padding:16px;color:#6d5663;line-height:1.55}.importante{display:flex;gap:12px;align-items:flex-start}.importante p{margin:0}.boton,.secundario{border:0;border-radius:999px;padding:11px 18px;font:inherit;font-weight:700;cursor:pointer}.boton{background:var(--vino);color:#fff}.secundario{background:#f4e7ed;color:var(--vino)}.acciones{display:flex;align-items:center;gap:14px;flex-wrap:wrap}.acciones span{color:#775f6c;font-size:.9rem}.pendiente{display:grid}.error{color:#a32335}.exito{color:#27603b}button:disabled,fieldset:disabled{opacity:.6}code{background:#f4e7ed;border-radius:5px;padding:2px 4px}@media(max-width:680px){.cabecera{align-items:flex-start;flex-direction:column}}
</style>
