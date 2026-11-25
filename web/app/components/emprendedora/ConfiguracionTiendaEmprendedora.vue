<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { ConfiguracionTiendaWeb, CuentaEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { Operacion } from '~/lib/cliente-base'

type Bloque = 'historial' | 'valores' | 'avisos' | 'calificaciones'

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb }>()
const emit = defineEmits<{ actualizada: [configuracion: ConfiguracionTiendaWeb] }>()

const configuracion = () => props.cuenta.tienda.configuracion
const valores = reactive({ ...configuracion().valoresDefecto })
const avisos = reactive({ horaInicio: configuracion().avisosContacto.horaInicio, horaFin: configuracion().avisosContacto.horaFin })
const guardando = ref<Bloque | null>(null)
const mensajes = reactive<Record<Bloque, { tipo: 'ok' | 'error'; texto: string } | null>>({ historial: null, valores: null, avisos: null, calificaciones: null })

watch(() => props.cuenta.tienda.configuracion, (actual) => {
  Object.assign(valores, actual.valoresDefecto)
  avisos.horaInicio = actual.avisosContacto.horaInicio
  avisos.horaFin = actual.avisosContacto.horaFin
})

const opcionesModalidad = [
  { id: 'regular', nombre: 'Regular', detalle: 'Disponible en forma habitual.' },
  { id: 'a_pedido', nombre: 'A pedido', detalle: 'Se prepara tras la compra.' },
  { id: 'pieza_unica', nombre: 'Pieza única', detalle: 'Una sola unidad que no se repite.' },
] as const
const opcionesAgotado = [
  { id: 'mostrar_agotado', nombre: 'Mostrar agotado' },
  { id: 'ocultar', nombre: 'Ocultar producto' },
  { id: 'pasar_a_pedido', nombre: 'Pasar a pedido' },
] as const

function llamar() {
  const { $amancay } = useNuxtApp()
  const cliente = $amancay as typeof $amancay & { llamar?: (operacion: Operacion, solicitud: Record<string, unknown>) => Promise<Record<string, unknown>> }
  if (typeof cliente?.llamar !== 'function') throw Object.assign(new Error('Este sitio está en modo de solo consulta; no se pueden guardar cambios.'), { code: 'solo-consulta' })
  return cliente.llamar.bind(cliente)
}

function mensajeError(fallo: unknown) {
  const codigo = (fallo as { code?: string })?.code
  if (codigo === 'solo-consulta') return (fallo as Error).message
  if (codigo === 'sin-permiso') return 'Tu cuenta no puede cambiar la configuración de esta tienda.'
  if (codigo === 'datos-invalidos') return 'Revisa los datos e inténtalo de nuevo.'
  if (codigo === 'conexion-interrumpida' || (fallo as { resultadoIncierto?: boolean })?.resultadoIncierto) return 'No se pudo confirmar el guardado. Inténtalo de nuevo.'
  return 'No se pudo guardar el cambio. Inténtalo de nuevo.'
}

async function guardar(bloque: Bloque, operacion: Operacion, solicitud: Record<string, unknown>, aplicar: (actual: ConfiguracionTiendaWeb) => ConfiguracionTiendaWeb, exito: string) {
  if (guardando.value) return
  guardando.value = bloque
  mensajes[bloque] = null
  try {
    await llamar()(operacion, { tiendaId: props.cuenta.tienda.id, ...solicitud })
    emit('actualizada', aplicar(configuracion()))
    mensajes[bloque] = { tipo: 'ok', texto: exito }
  } catch (fallo) {
    mensajes[bloque] = { tipo: 'error', texto: mensajeError(fallo) }
  } finally {
    guardando.value = null
  }
}

function cambiarHistorial(activo: boolean) {
  void guardar('historial', 'configurarHistorialVentas', { historialVentasActivo: activo },
    (actual) => ({ ...actual, historialVentasActivo: activo }),
    activo ? 'Las próximas ventas se guardarán en el historial.' : 'El historial quedó desactivado. Las ventas siguen descontando stock.')
}

function elegirModalidad(modalidad: ConfiguracionTiendaWeb['valoresDefecto']['modalidad']) {
  valores.modalidad = modalidad
  if (modalidad === 'pieza_unica') {
    valores.manejaStock = true
    valores.alAgotarse = 'ocultar'
  }
}

function guardarValores() {
  const nuevos = { ...valores }
  void guardar('valores', 'configurarValoresDefecto', nuevos,
    (actual) => ({ ...actual, valoresDefecto: nuevos }),
    'Los productos nuevos empezarán con estos valores.')
}

function cambiarAvisos(habilitado: boolean) {
  if (avisos.horaInicio === avisos.horaFin) {
    mensajes.avisos = { tipo: 'error', texto: 'La hora de inicio y de término no pueden ser iguales.' }
    return
  }
  const horario = { habilitado, horaInicio: avisos.horaInicio, horaFin: avisos.horaFin, zonaHoraria: 'America/Santiago' }
  void guardar('avisos', 'configurarAvisosContacto', horario,
    (actual) => ({ ...actual, avisosContacto: horario }),
    habilitado ? `Recibirás avisos entre las ${horario.horaInicio} y las ${horario.horaFin}.` : 'Los avisos de consultas quedaron desactivados.')
}

function cambiarCalificaciones(habilitado: boolean) {
  void guardar('calificaciones', 'configurarCalificaciones', { habilitado },
    (actual) => ({ ...actual, calificacionesHabilitadas: habilitado }),
    habilitado ? 'Las compradoras podrán calificar tu tienda y tus productos.' : 'Las calificaciones quedaron desactivadas.')
}

const valoresCambiados = () => {
  const actual = configuracion().valoresDefecto
  return actual.modalidad !== valores.modalidad || actual.manejaStock !== valores.manejaStock || actual.alAgotarse !== valores.alAgotarse
}
const horarioCambiado = () => configuracion().avisosContacto.horaInicio !== avisos.horaInicio || configuracion().avisosContacto.horaFin !== avisos.horaFin
</script>

<template>
  <section class="configuracion" aria-label="Configuración de la tienda">
    <article class="bloque">
      <div class="fila-interruptor">
        <span class="texto"><strong>Historial de ventas</strong><small>Guarda cada venta con su fecha, lugar y precio para revisarla después.</small></span>
        <button type="button" role="switch" class="interruptor" :aria-checked="cuenta.tienda.configuracion.historialVentasActivo" :disabled="guardando !== null" @click="cambiarHistorial(!cuenta.tienda.configuracion.historialVentasActivo)">
          <span class="sr-only">Historial de ventas</span>
        </button>
      </div>
      <p v-if="mensajes.historial" class="mensaje" :class="mensajes.historial.tipo" role="status">{{ mensajes.historial.texto }}</p>
    </article>

    <article class="bloque">
      <div class="texto"><strong>Valores para productos nuevos</strong><small>Se aplican al empezar un producto nuevo; siempre puedes cambiarlos en el asistente.</small></div>
      <fieldset>
        <legend>Modalidad</legend>
        <div class="opciones">
          <button v-for="opcion in opcionesModalidad" :key="opcion.id" type="button" class="opcion" :class="{ activa: valores.modalidad === opcion.id }" :aria-pressed="valores.modalidad === opcion.id" :disabled="guardando !== null" @click="elegirModalidad(opcion.id)">
            <strong>{{ opcion.nombre }}</strong><small>{{ opcion.detalle }}</small>
          </button>
        </div>
      </fieldset>
      <fieldset>
        <legend>¿Maneja stock?</legend>
        <div class="segmentado" role="radiogroup" aria-label="¿Maneja stock?">
          <button type="button" role="radio" :aria-checked="valores.manejaStock" :class="{ activo: valores.manejaStock }" :disabled="guardando !== null || valores.modalidad === 'pieza_unica'" @click="valores.manejaStock = true">Sí</button>
          <button type="button" role="radio" :aria-checked="!valores.manejaStock" :class="{ activo: !valores.manejaStock }" :disabled="guardando !== null || valores.modalidad === 'pieza_unica'" @click="valores.manejaStock = false">No</button>
        </div>
      </fieldset>
      <label v-if="valores.manejaStock" class="campo">Cuando se agote
        <select v-model="valores.alAgotarse" :disabled="guardando !== null || valores.modalidad === 'pieza_unica'">
          <option v-for="opcion in opcionesAgotado" :key="opcion.id" :value="opcion.id">{{ opcion.nombre }}</option>
        </select>
      </label>
      <div class="acciones"><button class="boton-vino" type="button" :disabled="guardando !== null || !valoresCambiados()" @click="guardarValores">{{ guardando === 'valores' ? 'Guardando…' : 'Guardar valores' }}</button></div>
      <p v-if="mensajes.valores" class="mensaje" :class="mensajes.valores.tipo" role="status">{{ mensajes.valores.texto }}</p>
    </article>

    <article class="bloque">
      <div class="fila-interruptor">
        <span class="texto"><strong>Avisos de consultas</strong><small>Te avisa en la app del teléfono cuando una compradora te escribe, solo dentro de este horario. Las consultas se guardan siempre.</small></span>
        <button type="button" role="switch" class="interruptor" :aria-checked="cuenta.tienda.configuracion.avisosContacto.habilitado" :disabled="guardando !== null" @click="cambiarAvisos(!cuenta.tienda.configuracion.avisosContacto.habilitado)">
          <span class="sr-only">Avisos de consultas</span>
        </button>
      </div>
      <div class="horario">
        <label class="campo">Desde<input v-model="avisos.horaInicio" type="time" :disabled="guardando !== null" /></label>
        <label class="campo">Hasta<input v-model="avisos.horaFin" type="time" :disabled="guardando !== null" /></label>
        <button v-if="horarioCambiado()" class="boton-claro" type="button" :disabled="guardando !== null" @click="cambiarAvisos(cuenta.tienda.configuracion.avisosContacto.habilitado)">{{ guardando === 'avisos' ? 'Guardando…' : 'Guardar horario' }}</button>
      </div>
      <p v-if="mensajes.avisos" class="mensaje" :class="mensajes.avisos.tipo" role="status">{{ mensajes.avisos.texto }}</p>
    </article>

    <article class="bloque">
      <div class="fila-interruptor">
        <span class="texto"><strong>Calificaciones</strong><small>Permite que las compradoras con sesión califiquen tu tienda y tus productos.</small></span>
        <button type="button" role="switch" class="interruptor" :aria-checked="cuenta.tienda.configuracion.calificacionesHabilitadas" :disabled="guardando !== null" @click="cambiarCalificaciones(!cuenta.tienda.configuracion.calificacionesHabilitadas)">
          <span class="sr-only">Calificaciones</span>
        </button>
      </div>
      <p v-if="mensajes.calificaciones" class="mensaje" :class="mensajes.calificaciones.tipo" role="status">{{ mensajes.calificaciones.texto }}</p>
    </article>
  </section>
</template>

<style scoped>
.configuracion{display:grid;gap:14px}
.bloque{display:grid;gap:14px;padding:18px 20px;border:1px solid var(--borde,#ecd8cb);border-radius:22px;background:#fff}
.fila-interruptor{display:flex;align-items:center;justify-content:space-between;gap:16px}
.texto{display:grid;gap:4px}
.texto strong{color:var(--vino,#74244e);font-size:16px}
.texto small{color:var(--suave,#7a6468);font-size:13px;line-height:1.45}
.interruptor{position:relative;flex:0 0 auto;width:52px;height:30px;padding:0;border:0;border-radius:999px;background:#e6d4d8;cursor:pointer;transition:background .15s ease}
.interruptor::after{position:absolute;top:3px;left:3px;width:24px;height:24px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.2);content:'';transition:transform .15s ease}
.interruptor[aria-checked=true]{background:var(--vino,#74244e)}
.interruptor[aria-checked=true]::after{transform:translateX(22px)}
.interruptor:disabled{opacity:.6;cursor:default}
fieldset{display:grid;gap:8px;margin:0;padding:0;border:0}
legend{margin-bottom:6px;color:var(--texto,#3f222c);font-size:13px;font-weight:800}
.opciones{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
.opcion{display:grid;gap:3px;padding:12px 14px;border:1px solid var(--borde,#ecd8cb);border-radius:16px;background:#fffdf9;text-align:left;cursor:pointer}
.opcion strong{color:var(--vino,#74244e);font-size:14px}
.opcion small{color:var(--suave,#7a6468);font-size:12px}
.opcion.activa{border:2px solid var(--vino,#74244e);background:#fff1ec}
.segmentado{display:grid;grid-template-columns:1fr 1fr;max-width:280px;overflow:hidden;border:1px solid var(--vino,#74244e);border-radius:14px;background:#f9ebe8}
.segmentado button{min-height:44px;border:0;border-radius:13px;background:transparent;color:var(--vino,#74244e);font:700 14px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.segmentado button.activo{background:var(--vino,#74244e);color:#fff}
.segmentado button:disabled{cursor:default}
.campo{display:grid;gap:6px;max-width:280px;color:var(--texto,#3f222c);font-size:13px;font-weight:800}
.campo input,.campo select{min-height:44px;padding:0 12px;border:1px solid var(--borde,#ecd8cb);border-radius:13px;background:#fffdf9;color:var(--texto,#3f222c);font:500 14px 'Plus Jakarta Sans',sans-serif}
.horario{display:flex;align-items:flex-end;flex-wrap:wrap;gap:12px}
.horario .campo{width:140px}
.acciones{display:flex;justify-content:flex-end}
.boton-claro,.boton-vino{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 18px;border-radius:13px;font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.boton-claro{border:1px solid var(--borde,#ecd8cb);background:#fff;color:var(--vino,#74244e)}
.boton-vino{border:0;background:var(--vino,#74244e);color:#fff}
.boton-claro:disabled,.boton-vino:disabled{opacity:.5;cursor:default}
.interruptor:focus-visible,.opcion:focus-visible,.segmentado button:focus-visible,.boton-claro:focus-visible,.boton-vino:focus-visible,.campo input:focus-visible,.campo select:focus-visible{outline:3px solid rgba(116,36,78,.3);outline-offset:2px}
.mensaje{margin:0;padding:10px 12px;border-radius:12px;font-size:13px}
.mensaje.ok{background:#edf7ed;color:#286344}
.mensaje.error{background:#fff0ee;color:#852e3f}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}
@media(prefers-reduced-motion:reduce){.interruptor,.interruptor::after{transition:none}}
@media(max-width:620px){.opciones{grid-template-columns:minmax(0,1fr)}}
</style>
