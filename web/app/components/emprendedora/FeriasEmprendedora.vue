<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  actualizarInvitacionEnCache,
  agruparPorDia,
  claveDia,
  diaFeria,
  horarioFeria,
  invitacionesEnCache,
  obtenerInvitacionesFeria,
  type EstadoInvitacionFeria,
  type InvitacionFeriaWeb,
} from '~/lib/ferias-emprendedora'
import type { CuentaEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { Operacion } from '~/lib/cliente-base'

type Filtro = 'pendientes' | 'proximas' | 'todas'

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb }>()
const emit = defineEmits<{ pendientes: [cantidad: number] }>()

const invitaciones = ref<InvitacionFeriaWeb[]>(invitacionesEnCache(props.cuenta.uid) ?? [])
const cargando = ref(false)
const error = ref('')
const aviso = ref('')
const filtro = ref<Filtro>('pendientes')
const respondiendo = ref<string | null>(null)
const porRechazar = ref<InvitacionFeriaWeb | null>(null)
const calendarioAbierto = ref(false)
const diaSeleccionado = ref<string | null>(null)
const mesVisible = ref(inicioMes(new Date()))

const etiquetas: Record<EstadoInvitacionFeria, string> = {
  pendiente: 'Invitación pendiente',
  aceptada: 'Participación confirmada',
  rechazada: 'Invitación rechazada',
  agregada_admin: 'Inscrita por administración',
}

const pendientes = computed(() => invitaciones.value.filter((invitacion) => invitacion.estado === 'pendiente'))
const porDia = computed(() => agruparPorDia(invitaciones.value))
const visibles = computed(() => {
  if (diaSeleccionado.value) return porDia.value.get(diaSeleccionado.value) ?? []
  if (filtro.value === 'pendientes') return pendientes.value
  if (filtro.value === 'proximas') return invitaciones.value.filter((invitacion) => invitacion.estado === 'aceptada' || invitacion.estado === 'agregada_admin')
  return invitaciones.value
})

function inicioMes(fecha: Date) {
  const [anio, mes] = claveDia(fecha).split('-').map(Number)
  return { anio: anio!, mes: mes! }
}

const nombreMes = computed(() => {
  const texto = new Intl.DateTimeFormat('es-CL', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(Date.UTC(mesVisible.value.anio, mesVisible.value.mes - 1, 1)))
  return texto.charAt(0).toLocaleUpperCase('es-CL') + texto.slice(1)
})

const celdas = computed(() => {
  const { anio, mes } = mesVisible.value
  const primero = new Date(Date.UTC(anio, mes - 1, 1))
  const desplazamiento = (primero.getUTCDay() + 6) % 7
  const diasMes = new Date(Date.UTC(anio, mes, 0)).getUTCDate()
  const hoy = claveDia(new Date())
  const lista: ({ clave: string; numero: number; ferias: InvitacionFeriaWeb[]; hoy: boolean } | null)[] = Array.from({ length: desplazamiento }, () => null)
  for (let dia = 1; dia <= diasMes; dia++) {
    const clave = `${anio}-${String(mes).padStart(2, '0')}-${String(dia).padStart(2, '0')}`
    lista.push({ clave, numero: dia, ferias: porDia.value.get(clave) ?? [], hoy: clave === hoy })
  }
  return lista
})

function cambiarMes(delta: number) {
  const total = mesVisible.value.anio * 12 + (mesVisible.value.mes - 1) + delta
  mesVisible.value = { anio: Math.floor(total / 12), mes: (total % 12) + 1 }
}

function abrirCalendario() {
  calendarioAbierto.value = !calendarioAbierto.value
  if (!calendarioAbierto.value) {
    diaSeleccionado.value = null
    return
  }
  const proxima = invitaciones.value
    .filter((invitacion) => invitacion.fechaInicio && invitacion.estado !== 'rechazada' && invitacion.fechaInicio.getTime() >= Date.now() - 86_400_000)
    .sort((a, b) => a.fechaInicio!.getTime() - b.fechaInicio!.getTime())[0]
  if (proxima?.fechaInicio) mesVisible.value = inicioMes(proxima.fechaInicio)
}

function elegirDia(clave: string, ferias: InvitacionFeriaWeb[]) {
  if (!ferias.length) return
  diaSeleccionado.value = diaSeleccionado.value === clave ? null : clave
}

function llamarApi() {
  const { $amancay } = useNuxtApp()
  const cliente = $amancay as typeof $amancay & { llamar?: (operacion: Operacion, solicitud: Record<string, unknown>) => Promise<Record<string, unknown>> }
  if (typeof cliente?.llamar !== 'function') throw Object.assign(new Error('Este sitio está en modo de solo consulta; no se pueden cargar las ferias.'), { code: 'solo-consulta' })
  return cliente.llamar.bind(cliente)
}

function mensajeError(fallo: unknown, alternativo: string) {
  const codigo = (fallo as { code?: string })?.code
  if (codigo === 'solo-consulta') return (fallo as Error).message
  if (codigo === 'sin-permiso') return 'Tu cuenta no tiene acceso a las invitaciones a ferias.'
  if (codigo === 'no-encontrado') return 'La feria ya no está publicada.'
  if (codigo === 'conflicto') return 'Esta invitación ya fue respondida. Actualiza la lista.'
  if (codigo === 'conexion-interrumpida' || (fallo as { resultadoIncierto?: boolean })?.resultadoIncierto) return 'No se pudo confirmar con el servidor. Revisa tu conexión e inténtalo de nuevo.'
  return alternativo
}

async function cargar(forzar = false) {
  cargando.value = true
  error.value = ''
  try {
    invitaciones.value = await obtenerInvitacionesFeria(props.cuenta.uid, llamarApi(), forzar)
    emit('pendientes', pendientes.value.length)
    if (!pendientes.value.length && filtro.value === 'pendientes' && invitaciones.value.length) filtro.value = 'proximas'
  } catch (fallo) {
    error.value = mensajeError(fallo, 'No se pudieron cargar las ferias. Inténtalo de nuevo.')
  } finally {
    cargando.value = false
  }
}

async function responder(invitacion: InvitacionFeriaWeb, acepta: boolean) {
  if (respondiendo.value) return
  respondiendo.value = invitacion.id
  error.value = ''
  aviso.value = ''
  try {
    const llamar = llamarApi()
    await llamar('responderInvitacionFeria', { feriaId: invitacion.id, respuesta: acepta ? 'confirmar' : 'rechazar' })
    const estado: EstadoInvitacionFeria = acepta ? 'aceptada' : 'rechazada'
    invitaciones.value = invitaciones.value.map((item) => item.id === invitacion.id ? { ...item, estado } : item)
    actualizarInvitacionEnCache(props.cuenta.uid, invitacion.id, estado)
    emit('pendientes', pendientes.value.length)
    aviso.value = acepta ? `Confirmaste tu participación en «${invitacion.nombre}».` : `Rechazaste la invitación a «${invitacion.nombre}».`
    porRechazar.value = null
  } catch (fallo) {
    error.value = mensajeError(fallo, 'No se pudo enviar tu respuesta. Inténtalo de nuevo.')
  } finally {
    respondiendo.value = null
  }
}

onMounted(() => { void cargar() })
</script>

<template>
  <section class="ferias" aria-label="Invitaciones a ferias">
    <div class="barra">
      <div class="filtros" role="group" aria-label="Filtrar ferias">
        <button type="button" :class="{ activo: filtro === 'pendientes' && !diaSeleccionado }" :aria-pressed="filtro === 'pendientes'" @click="filtro = 'pendientes'; diaSeleccionado = null">
          Pendientes <span v-if="pendientes.length" class="contador">{{ pendientes.length }}</span>
        </button>
        <button type="button" :class="{ activo: filtro === 'proximas' && !diaSeleccionado }" :aria-pressed="filtro === 'proximas'" @click="filtro = 'proximas'; diaSeleccionado = null">Confirmadas</button>
        <button type="button" :class="{ activo: filtro === 'todas' && !diaSeleccionado }" :aria-pressed="filtro === 'todas'" @click="filtro = 'todas'; diaSeleccionado = null">Todas</button>
      </div>
      <div class="acciones-barra">
        <button class="boton-claro" type="button" :aria-expanded="calendarioAbierto" @click="abrirCalendario">{{ calendarioAbierto ? 'Ocultar calendario' : 'Ver calendario de las ferias' }}</button>
        <button class="boton-claro" type="button" :disabled="cargando" @click="cargar(true)">{{ cargando ? 'Actualizando…' : 'Actualizar' }}</button>
      </div>
    </div>

    <div v-if="calendarioAbierto" class="calendario" aria-label="Calendario de ferias">
      <div class="cabeza-calendario">
        <button type="button" aria-label="Mes anterior" @click="cambiarMes(-1)">‹</button>
        <strong>{{ nombreMes }}</strong>
        <button type="button" aria-label="Mes siguiente" @click="cambiarMes(1)">›</button>
      </div>
      <div class="dias-semana" aria-hidden="true"><span>L</span><span>M</span><span>M</span><span>J</span><span>V</span><span>S</span><span>D</span></div>
      <div class="rejilla">
        <template v-for="(celda, indice) in celdas" :key="celda?.clave ?? `vacio-${indice}`">
          <span v-if="!celda" class="celda vacia" />
          <button
            v-else
            type="button"
            class="celda"
            :class="{ hoy: celda.hoy, 'con-feria': celda.ferias.length, seleccionada: diaSeleccionado === celda.clave, pendiente: celda.ferias.some((f) => f.estado === 'pendiente') }"
            :disabled="!celda.ferias.length"
            :aria-label="celda.ferias.length ? `${celda.numero}: ${celda.ferias.length} ${celda.ferias.length === 1 ? 'feria' : 'ferias'}` : String(celda.numero)"
            @click="elegirDia(celda.clave, celda.ferias)"
          >
            {{ celda.numero }}<span v-if="celda.ferias.length" class="punto" aria-hidden="true" />
          </button>
        </template>
      </div>
      <p class="leyenda"><span class="punto pendiente" aria-hidden="true" /> Invitación pendiente <span class="punto" aria-hidden="true" /> Confirmada</p>
    </div>

    <p v-if="diaSeleccionado" class="filtro-dia">
      Ferias del {{ diaFeria(porDia.get(diaSeleccionado)?.[0]?.fechaInicio ?? null).toLocaleLowerCase('es-CL') }}
      <button type="button" @click="diaSeleccionado = null">Ver todas</button>
    </p>

    <p v-if="error" class="mensaje error" role="alert">{{ error }}</p>
    <p v-if="aviso" class="mensaje ok" role="status">{{ aviso }}</p>

    <p v-if="cargando && !invitaciones.length" class="estado" role="status">Cargando ferias…</p>
    <div v-else-if="!invitaciones.length && !error" class="vacio">
      <span aria-hidden="true">✿</span>
      <strong>Aún no tienes invitaciones a ferias</strong>
      <p>Cuando la administración te invite o te inscriba en una feria, aparecerá aquí para que puedas responder.</p>
    </div>
    <p v-else-if="!visibles.length && !error" class="estado">{{ filtro === 'pendientes' ? 'No tienes invitaciones pendientes por responder.' : 'No hay ferias en esta vista.' }}</p>

    <ul v-else class="lista">
      <li v-for="invitacion in visibles" :key="invitacion.id" class="tarjeta" :class="invitacion.estado">
        <div class="cabeza">
          <h3>{{ invitacion.nombre }}</h3>
          <span class="estado-chip" :class="invitacion.estado">{{ etiquetas[invitacion.estado] }}</span>
        </div>
        <dl>
          <div><dt>Cuándo</dt><dd>{{ diaFeria(invitacion.fechaInicio) }} · {{ horarioFeria(invitacion.fechaInicio, invitacion.fechaFin) }}</dd></div>
          <div><dt>Dónde</dt><dd>{{ invitacion.donde }}</dd></div>
          <div><dt>Organiza</dt><dd>{{ invitacion.organiza }}</dd></div>
        </dl>
        <div v-if="invitacion.estado === 'pendiente'" class="acciones">
          <button class="boton-claro" type="button" :disabled="!!respondiendo" @click="porRechazar = invitacion">Rechazar</button>
          <button class="boton-vino" type="button" :disabled="!!respondiendo" @click="responder(invitacion, true)">{{ respondiendo === invitacion.id ? 'Enviando…' : 'Aceptar invitación' }}</button>
        </div>
        <p v-else-if="invitacion.estado === 'agregada_admin'" class="nota">La administración te inscribió directamente; no necesitas responder.</p>
      </li>
    </ul>

    <Teleport to="body">
      <div v-if="porRechazar" class="fondo-dialogo" @click.self="porRechazar = null" @keydown.esc="porRechazar = null">
        <div class="dialogo" role="dialog" aria-modal="true" aria-labelledby="titulo-rechazo">
          <h3 id="titulo-rechazo">¿Rechazar la invitación?</h3>
          <p>No participarás en «{{ porRechazar.nombre }}». Si cambias de opinión tendrás que pedir una nueva invitación a la administración.</p>
          <div class="acciones">
            <button class="boton-claro" type="button" :disabled="!!respondiendo" @click="porRechazar = null">Volver</button>
            <button class="boton-vino" type="button" :disabled="!!respondiendo" @click="responder(porRechazar, false)">{{ respondiendo ? 'Enviando…' : 'Sí, rechazar' }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.ferias{display:grid;gap:16px}
.barra{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.filtros,.acciones-barra{display:flex;flex-wrap:wrap;gap:8px}
.filtros button{display:inline-flex;align-items:center;gap:6px;min-height:40px;padding:0 16px;border:1px solid var(--borde,#ecd8cb);border-radius:999px;background:#fff;color:var(--vino,#74244e);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.filtros button.activo{border-color:var(--vino,#74244e);background:var(--vino,#74244e);color:#fff}
.contador{display:inline-grid;place-items:center;min-width:20px;height:20px;padding:0 6px;border-radius:999px;background:var(--naranja,#e9812d);color:#fff;font-size:11px}
.boton-claro,.boton-vino{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 18px;border-radius:13px;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-claro{border:1px solid var(--borde,#ecd8cb);background:#fff;color:var(--vino,#74244e)}
.boton-vino{border:0;background:var(--vino,#74244e);color:#fff}
.boton-claro:disabled,.boton-vino:disabled{opacity:.5;cursor:default}
.filtros button:focus-visible,.boton-claro:focus-visible,.boton-vino:focus-visible,.celda:focus-visible,.cabeza-calendario button:focus-visible{outline:3px solid rgba(116,36,78,.35);outline-offset:2px}
.calendario{display:grid;gap:10px;max-width:420px;padding:16px;border:1px solid var(--borde,#ecd8cb);border-radius:22px;background:#fff}
.cabeza-calendario{display:flex;align-items:center;justify-content:space-between}
.cabeza-calendario strong{color:var(--vino,#74244e);font:400 20px 'DM Serif Display',Georgia,serif}
.cabeza-calendario button{width:40px;height:40px;border:1px solid var(--borde,#ecd8cb);border-radius:50%;background:#fff;color:var(--vino,#74244e);font-size:20px;cursor:pointer}
.dias-semana,.rejilla{display:grid;grid-template-columns:repeat(7,1fr);gap:4px;text-align:center}
.dias-semana span{color:var(--suave,#7a6468);font-size:11px;font-weight:800}
.celda{position:relative;display:grid;place-items:center;aspect-ratio:1;border:0;border-radius:12px;background:transparent;color:var(--texto,#3f222c);font:600 13px 'Plus Jakarta Sans',sans-serif}
.celda:disabled{cursor:default;opacity:.75}
.celda.hoy{box-shadow:inset 0 0 0 1px var(--vino,#74244e)}
.celda.con-feria{background:#fbf0f1;color:var(--vino,#74244e);font-weight:800;cursor:pointer}
.celda.seleccionada{background:var(--vino,#74244e);color:#fff}
.punto{display:inline-block;width:6px;height:6px;border-radius:50%;background:#3c7a48}
.celda .punto{position:absolute;bottom:5px}
.celda.pendiente .punto,.punto.pendiente{background:var(--naranja,#e9812d)}
.celda.seleccionada .punto{background:#fff}
.leyenda{display:flex;align-items:center;flex-wrap:wrap;gap:6px 8px;margin:0;color:var(--suave,#7a6468);font-size:11.5px}
.filtro-dia{display:flex;align-items:center;gap:10px;margin:0;color:var(--vino,#74244e);font-weight:800}
.filtro-dia button{border:0;background:transparent;color:var(--vino,#74244e);font:700 12px 'Plus Jakarta Sans',sans-serif;text-decoration:underline;cursor:pointer}
.lista{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:14px;margin:0;padding:0;list-style:none}
.tarjeta{display:grid;align-content:start;gap:12px;padding:18px;border:1px solid var(--borde,#ecd8cb);border-radius:22px;background:#fff}
.tarjeta.pendiente{border-left:4px solid var(--naranja,#e9812d)}
.tarjeta.rechazada{opacity:.75}
.cabeza{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}
.cabeza h3{margin:0;color:var(--vino,#74244e);font:400 22px/1.15 'DM Serif Display',Georgia,serif}
.estado-chip{flex:0 0 auto;padding:4px 10px;border-radius:999px;background:#f1e8e8;color:var(--vino,#74244e);font-size:11px;font-weight:800}
.estado-chip.pendiente{background:#fde4d0;color:#9a4a10}
.estado-chip.aceptada,.estado-chip.agregada_admin{background:#e3f1e5;color:#2f6b40}
dl{display:grid;gap:6px;margin:0}
dl div{display:grid;grid-template-columns:72px minmax(0,1fr);gap:8px;font-size:13.5px}
dt{color:var(--suave,#7a6468);font-weight:700}
dd{margin:0;color:var(--texto,#3f222c)}
.acciones{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:10px}
.nota{margin:0;color:var(--suave,#7a6468);font-size:12.5px}
.mensaje{margin:0;padding:12px 14px;border-radius:14px;font-size:13px}
.mensaje.error{background:#fff0ee;color:#852e3f}
.mensaje.ok{background:#edf7ed;color:#286344}
.estado{color:var(--suave,#7a6468);font-size:14px}
.vacio{display:grid;justify-items:center;gap:6px;padding:32px 20px;border:1px dashed var(--borde,#ecd8cb);border-radius:20px;text-align:center;color:var(--suave,#7a6468)}
.vacio span{color:var(--vino,#74244e);font-size:34px}
.vacio strong{color:var(--vino,#74244e)}
.vacio p{max-width:380px;margin:0;font-size:13px}
.fondo-dialogo{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:16px;background:rgba(43,20,28,.45)}
.dialogo{display:grid;gap:12px;width:min(440px,100%);padding:22px;border-radius:22px;background:#fffaf3;box-shadow:0 24px 60px rgba(43,20,28,.3)}
.dialogo h3{margin:0;color:#5b1b3c;font:400 24px 'DM Serif Display',Georgia,serif}
.dialogo p{margin:0;color:#5f4b50;font-size:14px;line-height:1.5}
.dialogo .boton-claro{border-color:#ecd8cb;color:#74244e}
.dialogo .boton-vino{background:#74244e}
@media(max-width:560px){.lista{grid-template-columns:minmax(0,1fr)}.acciones .boton-vino,.acciones .boton-claro{flex:1}}
</style>
