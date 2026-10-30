<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { Operacion } from '~/lib/cliente-base'
import { leerParticipantesFeria, listarFeriasAdmin } from '~/repositories/admin'
import type { FeriaAdmin, ParticipanteFeria, ParticipantesFeria } from '~/types/admin'

const { $amancay } = useNuxtApp()
const modoFirebase = useRuntimeConfig().public.firebaseModo
const habilitado = ['local', 'real-api'].includes(modoFirebase)
const ferias = ref<FeriaAdmin[]>([])
const selectedId = ref('')
const detalle = ref<ParticipantesFeria | null>(null)
const cargando = ref(false)
const cargandoDetalle = ref(false)
const enviando = ref(false)
const error = ref('')
const aviso = ref('')
const formulario = ref(false)
const feriaEditando = ref<FeriaAdmin | null>(null)
const busquedaFeria = ref('')
const filtroEstadoFeria = ref('todas')
const candidata = ref('')
const busquedaParticipante = ref('')
const filtroEstadoParticipante = ref('todos')
const filtroTipoParticipante = ref('todos')
const tipoGestion = ref<'invitarParticipanteFeria' | 'agregarParticipanteFeria'>('invitarParticipanteFeria')
const retiro = ref<ParticipanteFeria | null>(null)
const pendiente = ref<{ operacion: Operacion; solicitud: Record<string, unknown> } | null>(null)
const borrador = ref({ nombre: '', descripcion: '', organizador: '', direccion: '', fechaInicio: '', fechaFin: '', imagenUrl: '', latitud: '', longitud: '' })

const seleccionDisponible = computed(() => {
  if (!detalle.value || !candidata.value) return null
  const [tipo, ...rest] = candidata.value.split(':')
  const entidadId = rest.join(':')
  return detalle.value.disponibles.find((item) => item.tipo === tipo && item.entidadId === entidadId) ?? null
})
const feriasVisibles = computed(() => {
  const query = busquedaFeria.value.trim().toLocaleLowerCase('es-CL')
  return ferias.value.filter((item) => (filtroEstadoFeria.value === 'todas' || item.estado === filtroEstadoFeria.value)
    && (!query || `${item.nombre} ${item.direccion} ${item.organizador}`.toLocaleLowerCase('es-CL').includes(query)))
})
const participantesVisibles = computed(() => {
  const query = busquedaParticipante.value.trim().toLocaleLowerCase('es-CL')
  return (detalle.value?.participantes ?? []).filter((item) => (filtroEstadoParticipante.value === 'todos' || item.estado === filtroEstadoParticipante.value)
    && (filtroTipoParticipante.value === 'todos' || item.tipo === filtroTipoParticipante.value)
    && (!query || `${item.nombre} ${item.nombreTienda} ${item.entidadId}`.toLocaleLowerCase('es-CL').includes(query)))
})

function opId() { return crypto.randomUUID().replaceAll('-', '') }
function mensajeError(cause: unknown) {
  const code = (cause as { code?: string })?.code
  if (code === 'sin-permiso') return 'La API no reconoce una cuenta administradora activa.'
  if (code === 'datos-invalidos') return 'Revisa los campos: las fechas deben ser válidas, el término posterior al inicio y la imagen debe usar HTTPS.'
  if (code === 'conflicto') return 'La feria o el participante cambió, o la acción ya no corresponde a su estado. Actualiza el módulo.'
  if (code === 'conexion-interrumpida' || (cause as { resultadoIncierto?: boolean })?.resultadoIncierto) return 'No se confirmó la respuesta. Reintenta la misma solicitud sin cambiar sus datos.'
  if (code === 'no-encontrado') return 'La feria, asociación o cuenta ya no existe. Actualiza el listado.'
  return 'No se pudo completar la operación. Revisa la conexión y vuelve a cargar el módulo.'
}

async function cargar() {
  if (!habilitado) return
  cargando.value = true; error.value = ''
  try {
    ferias.value = await listarFeriasAdmin($amancay)
    if (selectedId.value && ferias.value.some((item) => item.id === selectedId.value)) await cargarParticipantes(selectedId.value)
    else { selectedId.value = ''; detalle.value = null }
  } catch (cause) { error.value = mensajeError(cause) }
  finally { cargando.value = false }
}

async function cargarParticipantes(id: string) {
  selectedId.value = id; cargandoDetalle.value = true; error.value = ''
  try { detalle.value = await leerParticipantesFeria($amancay, id); candidata.value = '' }
  catch (cause) { detalle.value = null; error.value = mensajeError(cause) }
  finally { cargandoDetalle.value = false }
}

function nuevo() {
  feriaEditando.value = null
  borrador.value = { nombre: '', descripcion: '', organizador: '', direccion: '', fechaInicio: '', fechaFin: '', imagenUrl: '', latitud: '', longitud: '' }
  formulario.value = true; error.value = ''; aviso.value = ''
}

function fechaLocal(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 16)
}

function editar(item: FeriaAdmin) {
  feriaEditando.value = item
  borrador.value = {
    nombre: item.nombre, descripcion: item.descripcion, organizador: item.organizador, direccion: item.direccion,
    fechaInicio: fechaLocal(item.fechaInicio), fechaFin: fechaLocal(item.fechaFin), imagenUrl: item.imagenUrl, latitud: item.latitud === null ? '' : String(item.latitud), longitud: item.longitud === null ? '' : String(item.longitud),
  }
  formulario.value = true; error.value = ''; aviso.value = ''
}

function payloadFeria() {
  const datos: Record<string, unknown> = {
    nombre: borrador.value.nombre.trim(), descripcion: borrador.value.descripcion.trim(), organizador: borrador.value.organizador.trim(),
    direccion: borrador.value.direccion.trim(), fechaInicio: new Date(borrador.value.fechaInicio).toISOString(), fechaFin: new Date(borrador.value.fechaFin).toISOString(),
    imagenUrl: borrador.value.imagenUrl.trim(),
  }
  if (borrador.value.latitud.trim() || borrador.value.longitud.trim()) {
    datos.latitud = Number(borrador.value.latitud); datos.longitud = Number(borrador.value.longitud)
  }
  return datos
}

async function procesarPendiente() {
  if (!pendiente.value || enviando.value) return
  enviando.value = true; error.value = ''; aviso.value = ''
  const current = pendiente.value
  try {
    await $amancay.llamar(current.operacion, current.solicitud)
    pendiente.value = null; formulario.value = false; retiro.value = null
    aviso.value = 'Los cambios de la feria se guardaron en Firebase.'
    await cargar()
  } catch (cause) {
    error.value = mensajeError(cause)
    if (!(cause as { resultadoIncierto?: boolean })?.resultadoIncierto) pendiente.value = null
  } finally { enviando.value = false }
}

function guardarFeria() {
  if (!habilitado || pendiente.value || !borrador.value.fechaInicio || !borrador.value.fechaFin) return
  const op = opId(); const datos = payloadFeria()
  if (feriaEditando.value) {
    pendiente.value = { operacion: 'editarFeria', solicitud: { ...datos, operacionId: op, feriaId: feriaEditando.value.id, versionEsperada: feriaEditando.value.versionEsperada } }
  } else {
    pendiente.value = { operacion: 'crearFeria', solicitud: { ...datos, operacionId: op } }
  }
  void procesarPendiente()
}

function cambiarEstado(item: FeriaAdmin, estado: 'publicada' | 'cancelada') {
  if (!item.versionEsperada || pendiente.value) return
  const pregunta = estado === 'publicada' ? `¿Publicar “${item.nombre}” en Firebase?` : `¿Cancelar “${item.nombre}”? Esta acción no se puede revertir.`
  if (!window.confirm(pregunta)) return
  pendiente.value = { operacion: 'cambiarEstadoFeria', solicitud: { operacionId: opId(), feriaId: item.id, versionEsperada: item.versionEsperada, estado } }
  void procesarPendiente()
}

function gestionarParticipantes(item: FeriaAdmin) {
  retiro.value = null
  void cargarParticipantes(item.id)
}

function agregarCandidata() {
  if (!detalle.value || !seleccionDisponible.value || pendiente.value) return
  const candidato = seleccionDisponible.value
  pendiente.value = {
    operacion: tipoGestion.value,
    solicitud: { operacionId: opId(), feriaId: detalle.value.feria.id, versionFeriaEsperada: detalle.value.feria.versionEsperada, tipo: candidato.tipo, entidadId: candidato.entidadId },
  }
  void procesarPendiente()
}

function retirar(item: ParticipanteFeria) {
  if (!detalle.value || pendiente.value) return
  retiro.value = item
}

function confirmarRetiro() {
  if (!detalle.value || !retiro.value || pendiente.value) return
  pendiente.value = { operacion: 'retirarParticipanteFeria', solicitud: { operacionId: opId(), feriaId: detalle.value.feria.id, versionFeriaEsperada: detalle.value.feria.versionEsperada, participanteId: retiro.value.id } }
  void procesarPendiente()
}

function marcarAsistencia(item: ParticipanteFeria, asistencia: 'asistio' | 'no_asistio') {
  if (!detalle.value || pendiente.value) return
  pendiente.value = { operacion: 'registrarAsistenciaFeria', solicitud: { operacionId: opId(), feriaId: detalle.value.feria.id, versionFeriaEsperada: detalle.value.feria.versionEsperada, participanteId: item.id, asistencia } }
  void procesarPendiente()
}

function fechaBonita(value: string | null) {
  if (!value) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-CL', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

onMounted(() => { void cargar() })
</script>

<template>
  <section class="ferias-admin">
    <div class="nota"><span aria-hidden="true">🎪</span><p>Administra fechas, lugar y participantes. Las invitaciones se registran en Firebase; esta versión todavía no envía correos ni notificaciones. Para imágenes se acepta una URL HTTPS pública; aún no incluye carga a Storage ni selector de mapa.</p></div>
    <p v-if="!habilitado" class="nota" role="status">Este entorno es de solo lectura. Conecta la API autenticada para administrar ferias.</p>
    <div class="barra"><div><h2>Ferias y eventos</h2><p>{{ ferias.length }} registros</p></div><div class="acciones"><button class="secundario" type="button" :disabled="cargando || enviando" @click="cargar">{{ cargando ? 'Actualizando…' : 'Actualizar' }}</button><button class="boton" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="nuevo">+ Crear feria</button></div></div>
    <p v-if="cargando" role="status">Leyendo ferias desde Firebase…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="aviso" class="exito" role="status">{{ aviso }}</p>
    <div v-if="pendiente" class="nota pendiente" role="status"><p>La respuesta de la API no se confirmó. Conservé el mismo identificador para evitar duplicar la operación.</p><button class="boton" type="button" :disabled="enviando" @click="procesarPendiente">{{ enviando ? 'Procesando…' : 'Reintentar operación' }}</button></div>

    <section v-if="formulario" class="formulario">
      <div class="formulario-cabecera"><div><p class="sobre">{{ feriaEditando ? 'Editar información' : 'Nueva actividad' }}</p><h3>{{ feriaEditando ? feriaEditando.nombre : 'Crear feria' }}</h3></div><button class="secundario" type="button" :disabled="enviando || !!pendiente" @click="formulario = false">Cerrar</button></div>
      <form class="campos" @submit.prevent="guardarFeria">
        <label>Nombre de la feria<input v-model="borrador.nombre" maxlength="120" required :disabled="enviando || !!pendiente" /></label>
        <label>Organizador o responsable<input v-model="borrador.organizador" maxlength="120" required :disabled="enviando || !!pendiente" /></label>
        <label class="completo">Descripción<textarea v-model="borrador.descripcion" maxlength="2000" rows="3" :disabled="enviando || !!pendiente" /></label>
        <label class="completo">Dirección o lugar<input v-model="borrador.direccion" maxlength="300" required placeholder="Ej.: Sede de la Asociación, Alerce" :disabled="enviando || !!pendiente" /></label>
        <label>Inicio<input v-model="borrador.fechaInicio" type="datetime-local" required :disabled="enviando || !!pendiente" /></label>
        <label>Término<input v-model="borrador.fechaFin" type="datetime-local" required :disabled="enviando || !!pendiente" /></label>
        <label>Latitud (opcional)<input v-model="borrador.latitud" type="number" min="-90" max="90" step="any" :disabled="enviando || !!pendiente" /></label>
        <label>Longitud (opcional)<input v-model="borrador.longitud" type="number" min="-180" max="180" step="any" :disabled="enviando || !!pendiente" /></label>
        <label class="completo">URL de imagen HTTPS (opcional)<input v-model="borrador.imagenUrl" type="url" maxlength="2048" placeholder="https://…" :disabled="enviando || !!pendiente" /></label>
        <div class="acciones completo"><button class="boton" type="submit" :disabled="!habilitado || enviando || !!pendiente || !borrador.nombre.trim() || !borrador.organizador.trim() || !borrador.direccion.trim()">{{ enviando ? 'Guardando…' : feriaEditando ? 'Guardar cambios' : 'Crear borrador' }}</button><button class="secundario" type="button" :disabled="enviando || !!pendiente" @click="formulario = false">Cancelar</button></div>
      </form>
    </section>

    <div v-if="ferias.length" class="filtros">
      <label>Buscar<input v-model="busquedaFeria" type="search" placeholder="Feria, lugar u organizador" /></label>
      <label>Estado<select v-model="filtroEstadoFeria"><option value="todas">Todos</option><option value="borrador">Borrador</option><option value="publicada">Publicada</option><option value="cancelada">Cancelada</option></select></label>
    </div>
    <ul v-if="feriasVisibles.length" class="lista">
      <li v-for="item in feriasVisibles" :key="item.id" class="feria">
        <div class="feria-cabecera"><div><span class="estado" :class="item.estado">{{ item.estado === 'borrador' ? 'Borrador' : item.estado === 'publicada' ? 'Publicada' : 'Cancelada' }}</span><h3>{{ item.nombre }}</h3><p>{{ fechaBonita(item.fechaInicio) }} — {{ fechaBonita(item.fechaFin) }}</p><small>{{ item.direccion }} · Responsable: {{ item.organizador }}</small></div><img v-if="item.imagenUrl" :src="item.imagenUrl" alt="" loading="lazy" /></div>
        <p v-if="item.descripcion">{{ item.descripcion }}</p>
        <div class="acciones">
          <button class="secundario" type="button" :disabled="cargandoDetalle || enviando || !!pendiente" @click="gestionarParticipantes(item)">{{ selectedId === item.id ? 'Actualizar participantes' : 'Participantes' }}</button>
          <button v-if="item.estado !== 'cancelada' && new Date(item.fechaInicio || 0) > new Date()" class="secundario" type="button" :disabled="enviando || !!pendiente" @click="editar(item)">Editar</button>
          <button v-if="item.estado === 'borrador'" class="boton" type="button" :disabled="enviando || !!pendiente" @click="cambiarEstado(item, 'publicada')">Publicar</button>
          <button v-if="item.estado !== 'cancelada'" class="peligro" type="button" :disabled="enviando || !!pendiente" @click="cambiarEstado(item, 'cancelada')">Cancelar feria</button>
        </div>
      </li>
    </ul>
    <p v-else-if="!cargando" class="vacio">{{ ferias.length ? 'No hay ferias que coincidan con el filtro.' : 'Todavía no hay ferias registradas. Crea un borrador para comenzar.' }}</p>

    <section v-if="selectedId" class="participantes">
      <div class="formulario-cabecera"><div><p class="sobre">Lista y control</p><h3>Participantes</h3></div><button class="secundario" type="button" :disabled="cargandoDetalle || enviando" @click="cargarParticipantes(selectedId)">Actualizar</button></div>
      <p v-if="cargandoDetalle" role="status">Consultando participantes…</p>
      <template v-if="detalle">
        <div class="asignar">
          <label>Persona emprendedora o asociación<select v-model="candidata" :disabled="!habilitado || enviando || !!pendiente || !detalle.disponibles.length"><option value="">{{ detalle.disponibles.length ? 'Seleccionar participante' : 'No hay candidatos disponibles' }}</option><option v-for="(opcion, index) in detalle.disponibles" :key="`${opcion.tipo}:${opcion.entidadId}:${index}`" :value="`${opcion.tipo}:${opcion.entidadId}`">{{ opcion.tipo === 'emprendedora' ? `${opcion.nombreTienda || 'Tienda'} · ${opcion.nombre}` : `${opcion.nombre} · Asociación` }}</option></select></label>
          <label>Acción<select v-model="tipoGestion" :disabled="enviando || !!pendiente"><option value="invitarParticipanteFeria">Enviar invitación (queda pendiente)</option><option value="agregarParticipanteFeria">Agregar directamente</option></select></label>
          <button class="boton" type="button" :disabled="!habilitado || !seleccionDisponible || enviando || !!pendiente" @click="agregarCandidata">{{ tipoGestion === 'invitarParticipanteFeria' ? 'Registrar invitación' : 'Agregar a la feria' }}</button>
        </div>
        <div v-if="detalle.participantes.length" class="filtros participantes-filtros">
          <label>Buscar<input v-model="busquedaParticipante" type="search" placeholder="Nombre o tienda" /></label>
          <label>Tipo<select v-model="filtroTipoParticipante"><option value="todos">Todos</option><option value="emprendedora">Emprendedoras</option><option value="asociacion">Asociaciones</option></select></label>
          <label>Estado<select v-model="filtroEstadoParticipante"><option value="todos">Todos</option><option value="invitada">Invitadas</option><option value="confirmada">Confirmadas</option><option value="rechazada">Rechazadas</option><option value="agregada_admin">Agregadas por administración</option><option value="retirada">Retiradas</option></select></label>
        </div>
        <ul v-if="participantesVisibles.length" class="lista participantes-lista">
          <li v-for="persona in participantesVisibles" :key="persona.id" class="participante">
            <div class="persona"><strong>{{ persona.nombreTienda || persona.nombre }}</strong><small>{{ persona.tipo === 'asociacion' ? 'Asociación' : persona.nombre }} · {{ persona.estado.replace('_', ' ') }}</small></div>
            <span class="estado asistencia">{{ persona.asistencia === 'pendiente' ? 'Asistencia pendiente' : persona.asistencia === 'asistio' ? 'Asistió' : 'No asistió' }}</span>
            <div class="acciones" v-if="['confirmada', 'agregada_admin'].includes(persona.estado)"><button class="secundario" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="marcarAsistencia(persona, 'asistio')">Asistió</button><button class="secundario" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="marcarAsistencia(persona, 'no_asistio')">No asistió</button></div>
            <button v-if="persona.estado !== 'retirada'" class="peligro" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="retirar(persona)">Retirar</button>
          </li>
        </ul>
        <p v-else class="vacio">{{ detalle.participantes.length ? 'No hay participantes que coincidan con el filtro.' : 'No hay participantes asociados a esta feria.' }}</p>
      </template>
    </section>
    <div v-if="retiro" class="confirmacion" role="group" aria-label="Confirmar retiro">
      <p>¿Retirar a <strong>{{ retiro.nombreTienda || retiro.nombre }}</strong>? Se conservará el historial.</p>
      <button class="peligro" type="button" :disabled="enviando || !!pendiente" @click="confirmarRetiro">Confirmar retiro</button>
      <button class="secundario" type="button" :disabled="enviando || !!pendiente" @click="retiro = null">Volver</button>
    </div>
  </section>
</template>

<style scoped>
.ferias-admin{--vino:#75264f;--vino-oscuro:#5a1c3c;--crema:#fff8ef;--borde:#ead9cc;--texto:#3a1228;color:var(--texto);display:grid;gap:18px}.nota,.formulario,.feria,.participantes,.confirmacion{background:#fff;border:1px solid var(--borde);border-radius:20px;padding:18px}.nota{display:flex;gap:12px;background:var(--crema);color:#6d5663;line-height:1.55}.nota p{margin:0}.barra,.formulario-cabecera{display:flex;align-items:center;justify-content:space-between;gap:12px}.barra h2,.formulario h3,.participantes h3,.feria h3{font:400 1.55rem 'DM Serif Display',serif;color:var(--vino);margin:4px 0}.barra p,.feria p,.formulario p{margin:4px 0;color:#775f6c}.acciones{display:flex;gap:9px;align-items:center;flex-wrap:wrap}.boton,.secundario,.peligro{border:0;border-radius:999px;padding:10px 15px;font:inherit;font-weight:700;cursor:pointer}.boton{background:var(--vino);color:#fff}.secundario{background:#f4e7ed;color:var(--vino)}.peligro{background:#fff0e8;color:#9c3a28}button:disabled{opacity:.55;cursor:not-allowed}.formulario{display:grid;gap:16px}.sobre{font-size:.74rem!important;text-transform:uppercase;letter-spacing:.12em;color:var(--vino)!important;font-weight:700}.campos{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.campos label,.asignar label,.filtros label{display:grid;gap:7px;font-weight:600}.filtros{display:grid;grid-template-columns:2fr 1fr;gap:12px}.participantes-filtros{grid-template-columns:2fr 1fr 1fr}input,textarea,select{width:100%;border:1px solid var(--borde);border-radius:12px;padding:11px;font:inherit;color:var(--texto);background:#fff}.completo{grid-column:1/-1}.lista{list-style:none;margin:0;padding:0;display:grid;gap:12px}.feria{display:grid;gap:12px}.feria-cabecera{display:flex;justify-content:space-between;gap:15px}.feria-cabecera>div{min-width:0}.feria-cabecera img{width:128px;height:100px;object-fit:cover;border-radius:14px}.feria small,.participante small{color:#775f6c}.estado{display:inline-block;border-radius:999px;padding:5px 10px;font-size:.78rem;font-weight:700}.estado.borrador{background:#f9edda;color:#755319}.estado.publicada{background:#e8f4df;color:#365b2f}.estado.cancelada{background:#f8e1e1;color:#8e2b38}.asistencia{background:#f4e7ed;color:var(--vino)}.participantes{display:grid;gap:15px}.asignar{display:grid;grid-template-columns:2fr 1.5fr auto;gap:12px;align-items:end}.participantes-lista{border-top:1px solid var(--borde);padding-top:14px}.participante{display:flex;align-items:center;gap:12px;padding:12px;border:1px solid var(--borde);border-radius:15px;flex-wrap:wrap}.persona{display:grid;gap:4px;flex:1;min-width:150px}.confirmacion{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.confirmacion p{flex:1}.vacio{padding:24px;text-align:center;color:#775f6c;border:1px dashed var(--borde);border-radius:16px}.error{color:#a32335}.exito{color:#27603b}.pendiente{display:grid}.barra>div:first-child{flex:1}@media(max-width:760px){.barra,.formulario-cabecera{align-items:flex-start;flex-direction:column}.campos,.asignar,.filtros,.participantes-filtros{grid-template-columns:1fr}.completo{grid-column:auto}.feria-cabecera img{width:88px;height:78px}.participante{align-items:flex-start}.feria-cabecera{align-items:flex-start}}
</style>
