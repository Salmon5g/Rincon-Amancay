<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { agregarAdministradorPlataforma, listarAdministradoresPlataforma, quitarAdministradorPlataforma } from '~/repositories/admin'
import type { AdministradorPlataforma } from '~/types/admin'

const { $amancay } = useNuxtApp()
const modo = useRuntimeConfig().public.firebaseModo
const habilitado = ['local', 'real-api'].includes(modo)
const actualUid = $amancay.firebase.auth.currentUser?.uid ?? ''
const cuentas = ref<AdministradorPlataforma[]>([])
const cargando = ref(false)
const enviando = ref(false)
const error = ref('')
const exito = ref('')
const correo = ref('')
const cuentaPorQuitar = ref<AdministradorPlataforma | null>(null)
const pendiente = ref<{ operacion: 'agregarAdministrador' | 'quitarAdministrador'; solicitud: Record<string, unknown> } | null>(null)

function idOperacion() { return crypto.randomUUID().replaceAll('-', '') }
function explicar(errorValue: unknown) {
  const code = (errorValue as { code?: string })?.code
  if (code === 'sin-permiso') return 'La cuenta no tiene permisos de administrador activos.'
  if (code === 'no-encontrado') return 'Primero crea y verifica esa cuenta en Firebase Authentication.'
  if (code === 'conflicto') return 'La cuenta ya tiene ese estado o requiere revisión. Actualiza el listado.'
  if (code === 'conexion-interrumpida' || (errorValue as { resultadoIncierto?: boolean })?.resultadoIncierto) return 'No se confirmó la respuesta. Reintenta la misma operación sin cambiar los datos.'
  if (code === 'permission-denied') return 'Firestore rechazó la lectura. Revisa las reglas y permisos publicados.'
  return 'No se pudo completar la operación. Actualiza el listado y revisa el mensaje antes de volver a intentarlo.'
}

async function cargar() {
  if (!habilitado) return
  cargando.value = true; error.value = ''
  try { cuentas.value = await listarAdministradoresPlataforma($amancay) }
  catch (cause) { error.value = explicar(cause) }
  finally { cargando.value = false }
}

async function enviarPendiente() {
  if (!pendiente.value || enviando.value) return
  enviando.value = true; error.value = ''; exito.value = ''
  const current = pendiente.value
  try {
    await $amancay.llamar(current.operacion, current.solicitud)
    pendiente.value = null; correo.value = ''; cuentaPorQuitar.value = null
    exito.value = current.operacion === 'agregarAdministrador' ? 'Cuenta administradora agregada.' : 'Se retiró el rol administrador. La cuenta de acceso no fue eliminada.'
    await cargar()
  } catch (cause) {
    error.value = explicar(cause)
    if (!(cause as { resultadoIncierto?: boolean })?.resultadoIncierto) pendiente.value = null
  } finally { enviando.value = false }
}

function agregar() {
  const limpio = correo.value.trim().toLowerCase()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(limpio) || pendiente.value) return
  pendiente.value = { operacion: 'agregarAdministrador', solicitud: { operacionId: idOperacion(), correo: limpio } }
  void enviarPendiente()
}

function quitar(cuenta: AdministradorPlataforma) {
  if (!cuenta.versionEsperada || !cuenta.uid || pendiente.value) return
  pendiente.value = { operacion: 'quitarAdministrador', solicitud: { operacionId: idOperacion(), uidDestino: cuenta.uid, versionEsperada: cuenta.versionEsperada } }
  void enviarPendiente()
}

onMounted(() => { void cargar() })
</script>

<template>
  <section class="cuentas-admin">
    <div class="nota">
      <span aria-hidden="true">🛡️</span>
      <p>Para otorgar este rol, la persona debe tener una cuenta de Firebase Authentication con correo verificado. Aquí se asignan permisos; las contraseñas no se ven ni se modifican. Se conserva al menos una cuenta administradora activa.</p>
    </div>
    <p v-if="!habilitado" class="nota" role="status">Este entorno es de solo lectura. Conecta la API autenticada para gestionar cuentas.</p>
    <form class="formulario" @submit.prevent="agregar">
      <h2>Agregar otra cuenta administradora</h2>
      <label>Correo de la cuenta existente<input v-model="correo" type="email" autocomplete="email" required :disabled="!habilitado || enviando || !!pendiente" placeholder="persona@correo.cl" /></label>
      <p>Primero debe estar registrada y verificar su correo desde Firebase Authentication. No se crea ni se envía una contraseña desde esta pantalla.</p>
      <button class="boton" type="submit" :disabled="!habilitado || enviando || !!pendiente || !correo.trim()">Asignar rol</button>
    </form>
    <div class="encabezado-lista">
      <div><h2>Cuentas con rol administrador</h2><p>{{ cuentas.length }} cargadas</p></div>
      <button class="secundario" type="button" :disabled="cargando || enviando" @click="cargar">{{ cargando ? 'Actualizando…' : 'Actualizar listado' }}</button>
    </div>
    <p v-if="cargando" role="status">Consultando Authentication y Firestore…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="exito" class="exito" role="status">{{ exito }}</p>
    <div v-if="pendiente" class="nota pendiente" role="status">
      <p>La operación no tuvo confirmación. Usa el mismo envío para comprobar el resultado; no cambies el correo ni abras otra operación.</p>
      <button class="boton" type="button" :disabled="enviando" @click="enviarPendiente">{{ enviando ? 'Procesando…' : 'Reintentar operación' }}</button>
    </div>
    <ul v-if="cuentas.length" class="lista">
      <li v-for="cuenta in cuentas" :key="cuenta.uid" class="fila">
        <span class="avatar" aria-hidden="true">{{ (cuenta.nombreMostrar || cuenta.correo || '?').slice(0, 1).toUpperCase() }}</span>
        <div class="detalle"><strong>{{ cuenta.nombreMostrar || cuenta.correo || 'Cuenta sin nombre' }}</strong><small>{{ cuenta.correo || 'Correo no disponible' }} · UID {{ cuenta.uid }}</small><small v-if="!cuenta.consistente" class="error">Authentication deshabilitado, correo sin verificar o acceso inactivo; revisar la cuenta.</small></div>
        <span class="estado" :class="cuenta.consistente ? 'activo' : 'inconsistente'">{{ cuenta.consistente ? 'Activa' : 'Revisar' }}</span>
        <button v-if="cuenta.uid !== actualUid && cuenta.estado === 'activo'" class="secundario" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="cuentaPorQuitar = cuenta">Quitar rol</button>
        <span v-else-if="cuenta.uid === actualUid" class="propia">Tu cuenta</span>
      </li>
    </ul>
    <p v-else-if="!cargando" class="vacio">No hay cuentas administradoras cargadas.</p>
    <div v-if="cuentaPorQuitar" class="confirmacion" role="group" aria-label="Confirmar retiro del rol">
      <div><strong>¿Quitar el rol a {{ cuentaPorQuitar.correo || cuentaPorQuitar.uid }}?</strong><p>Su usuario de Authentication y otros roles se conservan. Esta acción no se puede aplicar a tu propia cuenta.</p></div>
      <button class="boton" type="button" :disabled="enviando || !!pendiente" @click="quitar(cuentaPorQuitar)">{{ enviando ? 'Procesando…' : 'Confirmar retiro' }}</button>
      <button class="secundario" type="button" :disabled="enviando || !!pendiente" @click="cuentaPorQuitar = null">Cancelar</button>
    </div>
  </section>
</template>

<style scoped>
.cuentas-admin{--vino:#75264f;--vino-oscuro:#5a1c3c;--crema:#fff8ef;--borde:#ead9cc;--texto:#3a1228;color:var(--texto);display:grid;gap:20px}.nota,.formulario,.fila,.confirmacion{background:#fff;border:1px solid var(--borde);border-radius:20px;padding:18px}.nota{background:var(--crema);display:flex;gap:12px;align-items:flex-start}.nota p,.formulario p,.encabezado-lista p,.confirmacion p{margin:5px 0;color:#775f6c;line-height:1.55}.formulario{display:grid;gap:12px}.formulario h2,.encabezado-lista h2{font-family:'DM Serif Display',serif;font-weight:400;margin:0;color:var(--vino)}label{display:grid;gap:7px;font-weight:600}input{border:1px solid var(--borde);border-radius:14px;padding:12px;font:inherit;color:var(--texto)}.boton,.secundario{border:0;border-radius:999px;padding:11px 18px;font:inherit;font-weight:700;cursor:pointer}.boton{background:var(--vino);color:#fff;justify-self:start}.secundario{background:#f4e7ed;color:var(--vino)}button:disabled{opacity:.55;cursor:not-allowed}.encabezado-lista{display:flex;justify-content:space-between;align-items:center}.lista{list-style:none;padding:0;margin:0;display:grid;gap:10px}.fila{display:flex;align-items:center;gap:14px}.avatar{width:42px;height:42px;border-radius:50%;display:grid;place-items:center;background:#f5e4ed;color:var(--vino);font-weight:700;flex:none}.detalle{display:grid;gap:4px;flex:1;min-width:0}.detalle small{overflow-wrap:anywhere;color:#775f6c}.estado,.propia{border-radius:999px;padding:6px 10px;font-size:.8rem;font-weight:700}.activo{background:#e7f4df;color:#34562b}.inconsistente{background:#fff0d9;color:#805311}.propia{background:#f6e7ef;color:var(--vino)}.confirmacion{display:flex;gap:14px;align-items:center;flex-wrap:wrap;border-color:#d79ab8}.confirmacion>div{flex:1;min-width:220px}.error{color:#a32335}.exito{color:#27603b}.pendiente{display:grid}.vacio{padding:28px;text-align:center;color:#775f6c;border:1px dashed var(--borde);border-radius:18px}@media(max-width:760px){.fila{align-items:flex-start;flex-wrap:wrap}.detalle{flex-basis:calc(100% - 60px)}.encabezado-lista{align-items:flex-start;gap:10px;flex-wrap:wrap}}
</style>
