<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { DocumentData, QueryDocumentSnapshot } from 'firebase/firestore'
import {
  listarPaginaConsultasEmprendedora,
  marcarConsultaLeidaEmprendedora,
  type ConsultaEmprendedoraWeb,
  type EstadoConsulta,
} from '~/repositories/firebase/consultas-emprendedora'
import type { CuentaEmprendedoraWeb } from '~/repositories/firebase/emprendedora'
import type { Operacion, ServiciosCliente } from '~/lib/cliente-base'

type Filtro = 'todas' | EstadoConsulta

const props = defineProps<{ cuenta: CuentaEmprendedoraWeb }>()
const emit = defineEmits<{ 'consulta-atendida': [] }>()

const consultas = ref<ConsultaEmprendedoraWeb[]>([])
const cursor = ref<QueryDocumentSnapshot<DocumentData> | null>(null)
const hayMas = ref(false)
const cargando = ref(false)
const cargandoMas = ref(false)
const error = ref('')
const aviso = ref('')
const busqueda = ref('')
const filtro = ref<Filtro>('todas')
const seleccionadaId = ref<string | null>(null)
const respuesta = ref('')
const enviando = ref(false)

const filtros: { id: Filtro; nombre: string }[] = [
  { id: 'todas', nombre: 'Todas' },
  { id: 'nueva', nombre: 'Nuevas' },
  { id: 'leida', nombre: 'Leídas' },
  { id: 'respondida', nombre: 'Respondidas' },
]

const seleccionada = computed(() => consultas.value.find((consulta) => consulta.id === seleccionadaId.value) ?? null)
const nuevas = computed(() => consultas.value.filter((consulta) => consulta.estado === 'nueva').length)
const visibles = computed(() => {
  const texto = busqueda.value.trim().toLocaleLowerCase('es-CL')
  return consultas.value.filter((consulta) =>
    (filtro.value === 'todas' || consulta.estado === filtro.value)
    && (!texto || `${consulta.nombre} ${consulta.correo} ${consulta.mensaje} ${consulta.producto}`.toLocaleLowerCase('es-CL').includes(texto)))
})

function servicios() {
  const { $amancay } = useNuxtApp()
  if (!$amancay?.firebase) throw new Error('El acceso no está disponible en este momento.')
  return $amancay as typeof $amancay & {
    firebase: ServiciosCliente
    llamar?: (operacion: Operacion, solicitud: Record<string, unknown>) => Promise<Record<string, unknown>>
  }
}

function mensajeError(fallo: unknown, alternativo: string) {
  const codigo = (fallo as { code?: string; codigo?: string })?.code ?? (fallo as { codigo?: string })?.codigo
  if (codigo === 'permission-denied' || codigo === 'sin-permiso') return 'Tu cuenta no tiene acceso a las consultas de esta tienda.'
  if (codigo === 'no-encontrado') return 'Esta consulta ya no existe. Actualiza la lista.'
  if (codigo === 'unavailable' || codigo === 'conexion-interrumpida' || (fallo as { resultadoIncierto?: boolean })?.resultadoIncierto) return 'No se pudo confirmar con el servidor. Revisa tu conexión e inténtalo de nuevo.'
  return alternativo
}

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    const pagina = await listarPaginaConsultasEmprendedora(servicios().firebase, props.cuenta.tienda.id)
    consultas.value = pagina.consultas
    cursor.value = pagina.cursor
    hayMas.value = pagina.hayMas
    if (seleccionadaId.value && !consultas.value.some((consulta) => consulta.id === seleccionadaId.value)) seleccionadaId.value = null
  } catch (fallo) {
    error.value = mensajeError(fallo, 'No se pudieron cargar las consultas. Inténtalo de nuevo.')
  } finally {
    cargando.value = false
  }
}

async function cargarMas() {
  if (!cursor.value || cargandoMas.value) return
  cargandoMas.value = true
  error.value = ''
  try {
    const pagina = await listarPaginaConsultasEmprendedora(servicios().firebase, props.cuenta.tienda.id, cursor.value)
    const conocidas = new Set(consultas.value.map((consulta) => consulta.id))
    consultas.value = [...consultas.value, ...pagina.consultas.filter((consulta) => !conocidas.has(consulta.id))]
    cursor.value = pagina.cursor ?? cursor.value
    hayMas.value = pagina.hayMas
  } catch (fallo) {
    error.value = mensajeError(fallo, 'No se pudieron cargar más consultas.')
  } finally {
    cargandoMas.value = false
  }
}

function actualizarLocal(id: string, cambios: Partial<ConsultaEmprendedoraWeb>) {
  consultas.value = consultas.value.map((consulta) => consulta.id === id ? { ...consulta, ...cambios } : consulta)
}

async function abrir(consulta: ConsultaEmprendedoraWeb) {
  seleccionadaId.value = consulta.id
  respuesta.value = ''
  aviso.value = ''
  error.value = ''
  if (consulta.estado !== 'nueva') return
  actualizarLocal(consulta.id, { estado: 'leida' })
  try {
    await marcarConsultaLeidaEmprendedora(servicios().firebase, props.cuenta.tienda.id, consulta.id)
    emit('consulta-atendida')
  } catch {
    actualizarLocal(consulta.id, { estado: 'nueva' })
  }
}

function cerrarDetalle() {
  seleccionadaId.value = null
  respuesta.value = ''
  aviso.value = ''
}

async function responder() {
  const consulta = seleccionada.value
  const texto = respuesta.value.trim()
  if (!consulta || enviando.value) return
  if (!texto || texto.length > 2000) {
    error.value = 'Escribe una respuesta de hasta 2.000 caracteres.'
    return
  }
  const cliente = servicios()
  if (typeof cliente.llamar !== 'function') {
    error.value = 'Este sitio está en modo de solo consulta; no se pueden enviar respuestas.'
    return
  }
  enviando.value = true
  error.value = ''
  aviso.value = ''
  try {
    await cliente.llamar('responderConsulta', { tiendaId: props.cuenta.tienda.id, consultaId: consulta.id, respuesta: texto })
    actualizarLocal(consulta.id, { estado: 'respondida', respuesta: texto, respondidaEn: new Date() })
    respuesta.value = ''
    aviso.value = 'Respuesta enviada. La compradora la verá en su app.'
  } catch (fallo) {
    error.value = mensajeError(fallo, 'No se pudo enviar la respuesta. Inténtalo de nuevo.')
  } finally {
    enviando.value = false
  }
}

function fechaLegible(fecha: Date | null) {
  if (!fecha) return ''
  return new Intl.DateTimeFormat('es-CL', { timeZone: 'America/Santiago', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(fecha)
}

function enlaceCorreo(consulta: ConsultaEmprendedoraWeb) {
  const asunto = consulta.producto ? `Sobre ${consulta.producto}` : `Tu consulta a ${props.cuenta.tienda.nombre}`
  return `mailto:${encodeURIComponent(consulta.correo)}?subject=${encodeURIComponent(asunto)}`
}

const etiquetaEstado: Record<EstadoConsulta, string> = { nueva: 'Nueva', leida: 'Leída', respondida: 'Respondida' }

onMounted(cargar)
</script>

<template>
  <section class="consultas" aria-label="Consultas de compradoras">
    <div class="barra">
      <div class="resumen">
        <strong>{{ nuevas }}</strong>
        <span>{{ nuevas === 1 ? 'consulta nueva' : 'consultas nuevas' }}<template v-if="hayMas"> en las más recientes</template></span>
      </div>
      <button class="boton-claro" type="button" :disabled="cargando" @click="cargar">{{ cargando ? 'Actualizando…' : 'Actualizar' }}</button>
    </div>

    <div class="herramientas">
      <label class="buscador">
        <span class="sr-only">Buscar consultas</span>
        <input v-model="busqueda" type="search" placeholder="Buscar por nombre, correo, producto o mensaje" />
      </label>
      <div class="filtros" role="group" aria-label="Filtrar consultas">
        <button v-for="opcion in filtros" :key="opcion.id" type="button" :class="{ activo: filtro === opcion.id }" :aria-pressed="filtro === opcion.id" @click="filtro = opcion.id">{{ opcion.nombre }}</button>
      </div>
    </div>

    <p v-if="error" class="mensaje error" role="alert">{{ error }}</p>

    <div class="columnas" :class="{ 'con-detalle': seleccionada }">
      <div class="lista">
        <p v-if="cargando && !consultas.length" class="estado" role="status">Cargando consultas…</p>
        <div v-else-if="!consultas.length && !error" class="vacio">
          <span aria-hidden="true">✉</span>
          <strong>Aún no tienes consultas</strong>
          <p>Cuando una compradora te escriba desde el formulario de tu tienda, su mensaje aparecerá aquí.</p>
        </div>
        <p v-else-if="consultas.length && !visibles.length" class="estado">No hay consultas con estos filtros.</p>
        <ul v-else>
          <li v-for="consulta in visibles" :key="consulta.id">
            <button type="button" class="tarjeta" :class="{ nueva: consulta.estado === 'nueva', seleccionada: consulta.id === seleccionadaId }" @click="abrir(consulta)">
              <span class="cabeza">
                <strong>{{ consulta.nombre }}</strong>
                <span class="estado-chip" :class="consulta.estado">{{ etiquetaEstado[consulta.estado] }}</span>
              </span>
              <span v-if="consulta.producto" class="producto">Sobre: {{ consulta.producto }}</span>
              <span class="extracto">{{ consulta.mensaje }}</span>
              <small>{{ fechaLegible(consulta.creadaEn) }}</small>
            </button>
          </li>
        </ul>
        <button v-if="hayMas" class="boton-claro cargar-mas" type="button" :disabled="cargandoMas" @click="cargarMas">{{ cargandoMas ? 'Cargando…' : 'Cargar más consultas' }}</button>
      </div>

      <article v-if="seleccionada" class="detalle" aria-live="polite">
        <button class="volver" type="button" @click="cerrarDetalle">← Volver a la lista</button>
        <header>
          <div>
            <h3>{{ seleccionada.nombre }}</h3>
            <a v-if="seleccionada.correo" :href="enlaceCorreo(seleccionada)">{{ seleccionada.correo }}</a>
          </div>
          <span class="estado-chip" :class="seleccionada.estado">{{ etiquetaEstado[seleccionada.estado] }}</span>
        </header>
        <p v-if="seleccionada.producto" class="producto">Producto: <strong>{{ seleccionada.producto }}</strong></p>
        <small class="fecha">Recibida el {{ fechaLegible(seleccionada.creadaEn) }}</small>
        <blockquote>{{ seleccionada.mensaje }}</blockquote>

        <div v-if="seleccionada.respuesta" class="respuesta-enviada">
          <strong>Tu respuesta</strong>
          <p>{{ seleccionada.respuesta }}</p>
          <small v-if="seleccionada.respondidaEn">Enviada el {{ fechaLegible(seleccionada.respondidaEn) }}</small>
        </div>
        <form v-else class="formulario-respuesta" @submit.prevent="responder">
          <label>
            Responder
            <textarea v-model="respuesta" rows="5" maxlength="2000" placeholder="Escribe tu respuesta para la compradora" :disabled="enviando" />
          </label>
          <span class="contador">{{ respuesta.length }}/2000</span>
          <div class="acciones">
            <a v-if="seleccionada.correo" class="boton-claro" :href="enlaceCorreo(seleccionada)">Responder por correo</a>
            <button class="boton-vino" type="submit" :disabled="enviando || !respuesta.trim()">{{ enviando ? 'Enviando…' : 'Enviar respuesta' }}</button>
          </div>
        </form>
        <p v-if="aviso" class="mensaje ok" role="status">{{ aviso }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.consultas{display:grid;gap:16px}
.barra{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.resumen{display:flex;align-items:baseline;gap:8px;color:var(--suave,#7a6468);font-size:14px}
.resumen strong{color:var(--vino,#74244e);font:400 34px/1 'DM Serif Display',Georgia,serif}
.herramientas{display:grid;gap:10px}
.buscador input{width:100%;min-height:46px;padding:0 16px;border:1px solid var(--borde,#ecd8cb);border-radius:14px;background:#fffdf9;color:var(--texto,#3f222c);font:500 14px 'Plus Jakarta Sans',sans-serif}
.buscador input:focus{outline:3px solid rgba(116,36,78,.2);border-color:var(--vino,#74244e)}
.filtros{display:flex;flex-wrap:wrap;gap:8px}
.filtros button{min-height:40px;padding:0 16px;border:1px solid var(--borde,#ecd8cb);border-radius:999px;background:#fff;color:var(--vino,#74244e);font:700 12px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.filtros button.activo{border-color:var(--vino,#74244e);background:var(--vino,#74244e);color:#fff}
.columnas{display:grid;gap:16px}
.columnas.con-detalle{grid-template-columns:minmax(0,1fr) minmax(0,1.2fr)}
.lista ul{display:grid;gap:10px;margin:0;padding:0;list-style:none}
.tarjeta{display:grid;gap:5px;width:100%;padding:14px 16px;border:1px solid var(--borde,#ecd8cb);border-radius:18px;background:#fff;color:var(--texto,#3f222c);text-align:left;cursor:pointer;transition:border-color .15s ease,box-shadow .15s ease}
.tarjeta:hover{border-color:#d8b3a3;box-shadow:0 8px 20px rgba(63,34,44,.06)}
.tarjeta:focus-visible,.filtros button:focus-visible,.boton-claro:focus-visible,.boton-vino:focus-visible,.volver:focus-visible{outline:3px solid rgba(116,36,78,.35);outline-offset:2px}
.tarjeta.nueva{border-left:4px solid var(--naranja,#e9812d)}
.tarjeta.seleccionada{border-color:var(--vino,#74244e);background:#fff7f2}
.cabeza{display:flex;align-items:center;justify-content:space-between;gap:10px}
.cabeza strong{color:var(--vino,#74244e);font-size:15px}
.producto{color:var(--suave,#7a6468);font-size:12.5px}
.extracto{display:-webkit-box;overflow:hidden;color:var(--texto,#3f222c);font-size:13.5px;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}
.tarjeta small,.fecha{color:var(--suave,#7a6468);font-size:12px}
.estado-chip{flex:0 0 auto;padding:4px 10px;border-radius:999px;background:#f1e8e8;color:var(--vino,#74244e);font-size:11px;font-weight:800}
.estado-chip.nueva{background:#fde4d0;color:#9a4a10}
.estado-chip.respondida{background:#e3f1e5;color:#2f6b40}
.detalle{position:sticky;top:16px;display:grid;align-content:start;gap:10px;padding:20px;border:1px solid var(--borde,#ecd8cb);border-radius:22px;background:#fff}
.detalle header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
.detalle h3{margin:0;color:var(--vino,#74244e);font:400 26px/1.1 'DM Serif Display',Georgia,serif}
.detalle header a{color:var(--suave,#7a6468);font-size:13px}
.detalle .producto{margin:0}
blockquote{margin:4px 0;padding:14px 16px;border-radius:16px;background:#fbf4ea;color:var(--texto,#3f222c);font-size:14.5px;line-height:1.55;white-space:pre-line}
.respuesta-enviada{display:grid;gap:4px;padding:14px 16px;border-radius:16px;background:#eef6ef;color:#2f4b36}
.respuesta-enviada p{margin:0;white-space:pre-line}
.formulario-respuesta{display:grid;gap:6px}
.formulario-respuesta label{display:grid;gap:6px;color:var(--texto,#3f222c);font-size:13px;font-weight:800}
.formulario-respuesta textarea{width:100%;min-height:120px;padding:12px 14px;border:1px solid var(--borde,#ecd8cb);border-radius:14px;background:#fffdf9;color:var(--texto,#3f222c);font:500 14px/1.5 'Plus Jakarta Sans',sans-serif;resize:vertical}
.formulario-respuesta textarea:focus{outline:3px solid rgba(116,36,78,.2);border-color:var(--vino,#74244e)}
.contador{justify-self:end;color:var(--suave,#7a6468);font-size:11px}
.acciones{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:10px}
.boton-claro,.boton-vino{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:0 18px;border-radius:13px;font:700 12px 'Plus Jakarta Sans',sans-serif;text-decoration:none;cursor:pointer}
.boton-claro{border:1px solid var(--borde,#ecd8cb);background:#fff;color:var(--vino,#74244e)}
.boton-vino{border:0;background:var(--vino,#74244e);color:#fff}
.boton-claro:disabled,.boton-vino:disabled{opacity:.5;cursor:default}
.cargar-mas{width:100%;margin-top:10px}
.volver{display:none;justify-self:start;padding:6px 0;border:0;background:transparent;color:var(--vino,#74244e);font:700 13px 'Plus Jakarta Sans',sans-serif;cursor:pointer}
.mensaje{margin:0;padding:12px 14px;border-radius:14px;font-size:13px}
.mensaje.error{background:#fff0ee;color:#852e3f}
.mensaje.ok{background:#edf7ed;color:#286344}
.estado{color:var(--suave,#7a6468);font-size:14px}
.vacio{display:grid;justify-items:center;gap:6px;padding:32px 20px;border:1px dashed var(--borde,#ecd8cb);border-radius:20px;text-align:center;color:var(--suave,#7a6468)}
.vacio span{color:var(--vino,#74244e);font-size:34px}
.vacio strong{color:var(--vino,#74244e)}
.vacio p{max-width:380px;margin:0;font-size:13px}
.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}
@media(max-width:860px){
  .columnas.con-detalle{grid-template-columns:minmax(0,1fr)}
  .columnas.con-detalle .lista{display:none}
  .detalle{position:static}
  .volver{display:inline-block}
}
</style>
