<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import type { Operacion } from '~/lib/cliente-base'
import SelectorUbicacionFeria from './MapaUbicacionFeria.client.vue'
import { leerParticipantesFeria, listarFeriasAdmin } from '~/repositories/admin'
import type { FeriaAdmin, ParticipanteFeria, ParticipantesFeria, TipoParticipanteFeria } from '~/types/admin'

const { $amancay } = useNuxtApp()
const modoFirebase = useRuntimeConfig().public.firebaseModo
const habilitado = ['local', 'real-api'].includes(modoFirebase)
const ferias = ref<FeriaAdmin[]>([])
const selectedId = ref('')
const detalle = ref<ParticipantesFeria | null>(null)
const cargando = ref(false)
const cargandoDetalle = ref(false)
const enviando = ref(false)
const confirmacionEstado = ref<{ item: FeriaAdmin; estado: 'publicada' | 'cancelada' } | null>(null)
const dialogoConfirmacionRef = ref<HTMLElement | null>(null)
const volverConfirmacionRef = ref<HTMLButtonElement | null>(null)
const ejecutarConfirmacionRef = ref<HTMLButtonElement | null>(null)
let focoAnteriorConfirmacion: HTMLElement | null = null
const error = ref('')
const aviso = ref('')
const formulario = ref(false)
const formularioRef = ref<HTMLElement | null>(null)
const participantesRef = ref<HTMLElement | null>(null)
const feriaEditando = ref<FeriaAdmin | null>(null)
const borradorInicial = ref('')
const busquedaFeria = ref('')
const filtroEstadoFeria = ref('todas')
const candidata = ref('')
const tipoCandidata = ref<TipoParticipanteFeria>('emprendedora')
const busquedaCandidata = ref('')
const busquedaParticipante = ref('')
const filtroEstadoParticipante = ref('todos')
const filtroTipoParticipante = ref('todos')
const filtroTipoEmprendimiento = ref('todos')
const filtroAsociacion = ref('todas')
const tipoGestion = ref<'invitarParticipanteFeria' | 'agregarParticipanteFeria'>('invitarParticipanteFeria')
const retiro = ref<ParticipanteFeria | null>(null)
const pendiente = ref<{ operacion: Operacion; solicitud: Record<string, unknown> } | null>(null)
const borrador = ref({ nombre: '', descripcion: '', organizador: '', direccion: '', fecha: '', horaInicio: '', horaFin: '', imagenUrl: '', latitud: '', longitud: '' })
const archivoImagen = ref<File | null>(null)
const archivoIdImagen = ref('')
const vistaImagen = ref('')
const subiendoImagen = ref(false)
const ubicacionMarcada = computed(() => {
  const latitud = Number(borrador.value.latitud)
  const longitud = Number(borrador.value.longitud)
  return borrador.value.latitud.trim() !== '' && borrador.value.longitud.trim() !== ''
    && Number.isFinite(latitud) && latitud >= -90 && latitud <= 90
    && Number.isFinite(longitud) && longitud >= -180 && longitud <= 180
})
const fechaHora = computed(() => {
  const { fecha, horaInicio, horaFin } = borrador.value
  if (!fecha || !horaInicio || !horaFin) return null
  const inicio = new Date(`${fecha}T${horaInicio}`)
  const fin = new Date(`${fecha}T${horaFin}`)
  if (!Number.isFinite(inicio.getTime()) || !Number.isFinite(fin.getTime())) return null
  return { inicio, fin, valido: fin > inicio }
})
const imagenLista = computed(() => Boolean(borrador.value.imagenUrl || archivoImagen.value))
const inicioPasado = computed(() => Boolean(fechaHora.value?.valido && fechaHora.value.inicio.getTime() <= Date.now()))
const edicionModificada = computed(() => Boolean(feriaEditando.value)
  && (Boolean(archivoImagen.value) || JSON.stringify(borrador.value) !== borradorInicial.value))
const guardadoFeriaDeshabilitado = computed(() => {
  if (!habilitado || enviando.value || pendiente.value) return true
  // En edición, cualquier cambio permite intentar guardar para que guardarFeria
  // muestre la validación concreta (por ejemplo, un horario inválido) al usuario.
  if (feriaEditando.value) return !edicionModificada.value
  return !ubicacionMarcada.value || !fechaHora.value?.valido || inicioPasado.value || !imagenLista.value
    || !borrador.value.nombre.trim() || !borrador.value.descripcion.trim()
    || !borrador.value.organizador.trim() || !borrador.value.direccion.trim()
})

const seleccionDisponible = computed(() => {
  if (!detalle.value || !candidata.value) return null
  const [tipo, ...rest] = candidata.value.split(':')
  const entidadId = rest.join(':')
  return detalle.value.disponibles.find((item) => item.tipo === tipo && item.tipo === tipoCandidata.value && item.entidadId === entidadId) ?? null
})
const disponiblesPorTipo = computed(() => detalle.value?.disponibles.filter((item) => item.tipo === tipoCandidata.value) ?? [])
const candidatasFiltradas = computed(() => {
  const query = busquedaCandidata.value.trim().toLocaleLowerCase('es-CL')
  return disponiblesPorTipo.value.filter((item) => !query
    || `${item.nombreTienda ?? ''} ${item.nombre} ${item.entidadId}`.toLocaleLowerCase('es-CL').includes(query))
})
const totalEmprendedorasDisponibles = computed(() => detalle.value?.disponibles.filter((item) => item.tipo === 'emprendedora').length ?? 0)
const totalAsociacionesDisponibles = computed(() => detalle.value?.disponibles.filter((item) => item.tipo === 'asociacion').length ?? 0)
const feriasVisibles = computed(() => {
  const query = busquedaFeria.value.trim().toLocaleLowerCase('es-CL')
  return ferias.value.filter((item) => (filtroEstadoFeria.value === 'todas' || item.estado === filtroEstadoFeria.value)
    && (!query || `${item.nombre} ${item.direccion} ${item.organizador}`.toLocaleLowerCase('es-CL').includes(query)))
})
const estadosParticipante = [
  { valor: 'invitada', etiqueta: 'Invitada' },
  { valor: 'confirmada', etiqueta: 'Confirmada' },
  { valor: 'rechazada', etiqueta: 'Rechazada' },
  { valor: 'agregada_admin', etiqueta: 'Agregada por el administrador' },
  { valor: 'retirada', etiqueta: 'Retirada' },
] as const
function opcionesUnicas(lista: { id: string; nombre: string }[]) {
  return [...new Map(lista.map((item) => [item.id, item])).values()].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-CL'))
}
const tiposEmprendimientoParticipantes = computed(() => opcionesUnicas((detalle.value?.participantes ?? []).flatMap((item) => item.tipoEmprendimiento ? [item.tipoEmprendimiento] : [])))
const asociacionesParticipantes = computed(() => opcionesUnicas((detalle.value?.participantes ?? []).flatMap((item) => item.asociaciones ?? [])))
function cantidadPorEstado(estado: string) {
  return (detalle.value?.participantes ?? []).filter((item) => item.estado === estado).length
}
const participantesVisibles = computed(() => {
  const query = busquedaParticipante.value.trim().toLocaleLowerCase('es-CL')
  return (detalle.value?.participantes ?? []).filter((item) => (filtroEstadoParticipante.value === 'todos' || item.estado === filtroEstadoParticipante.value)
    && (filtroTipoParticipante.value === 'todos' || item.tipo === filtroTipoParticipante.value)
    && (filtroTipoEmprendimiento.value === 'todos' || item.tipoEmprendimiento?.id === filtroTipoEmprendimiento.value)
    && (filtroAsociacion.value === 'todas' || (item.asociaciones ?? []).some((asociacion) => asociacion.id === filtroAsociacion.value))
    && (!query || `${item.nombre} ${item.nombreTienda} ${item.entidadId} ${item.tipoEmprendimiento?.nombre ?? ''} ${(item.asociaciones ?? []).map((asociacion) => asociacion.nombre).join(' ')}`.toLocaleLowerCase('es-CL').includes(query)))
})

function opId() { return crypto.randomUUID().replaceAll('-', '') }
function mensajeError(cause: unknown) {
  const code = (cause as { code?: string })?.code
  const detalle = cause instanceof Error ? cause.message.trim() : ''
  if (code === 'sin-permiso') return 'La API no reconoce una cuenta administradora activa.'
  if (code === 'datos-invalidos') return detalle || 'Revisa los campos: nombre, descripción, lugar, fecha, horario e imagen son obligatorios; el término debe ser posterior al inicio.'
  if (code === 'pendiente' || code === 'cuota-firestore' || code === 'imagen-no-autorizada') return detalle || 'La API no pudo completar la carga. Conserva los datos y reintenta.'
  if (code === 'conflicto') return detalle || 'La feria o el participante cambió, o la acción ya no corresponde a su estado. Actualiza el módulo.'
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
  const cambioFeria = selectedId.value !== id
  selectedId.value = id; cargandoDetalle.value = true; error.value = ''
  if (cambioFeria) {
    detalle.value = null
    candidata.value = ''
    tipoCandidata.value = 'emprendedora'
    busquedaCandidata.value = ''
  }
  try { detalle.value = await leerParticipantesFeria($amancay, id); candidata.value = ''; busquedaCandidata.value = '' }
  catch (cause) { detalle.value = null; error.value = mensajeError(cause) }
  finally { cargandoDetalle.value = false }
}

function seleccionarTipoCandidata(tipo: TipoParticipanteFeria) {
  if (tipoCandidata.value === tipo) return
  tipoCandidata.value = tipo
  candidata.value = ''
  busquedaCandidata.value = ''
}

function nuevo() {
  feriaEditando.value = null
  borrador.value = { nombre: '', descripcion: '', organizador: '', direccion: '', fecha: '', horaInicio: '', horaFin: '', imagenUrl: '', latitud: '', longitud: '' }
  borradorInicial.value = ''
  limpiarArchivoImagen()
  formulario.value = true; error.value = ''; aviso.value = ''
}

function cerrarFormulario() {
  if (enviando.value || pendiente.value) return
  limpiarArchivoImagen()
  formulario.value = false
}

function dosDigitos(valor: number) { return String(valor).padStart(2, '0') }
function fechaLocal(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return ''
  return `${date.getFullYear()}-${dosDigitos(date.getMonth() + 1)}-${dosDigitos(date.getDate())}`
}
function horaLocal(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return ''
  return `${dosDigitos(date.getHours())}:${dosDigitos(date.getMinutes())}`
}

function limpiarArchivoImagen() {
  archivoImagen.value = null
  archivoIdImagen.value = ''
  if (vistaImagen.value.startsWith('blob:')) URL.revokeObjectURL(vistaImagen.value)
  vistaImagen.value = ''
}

function seleccionarImagen(event: Event) {
  const input = event.target as HTMLInputElement
  const archivo = input.files?.[0] ?? null
  if (!archivo) return
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(archivo.type) || archivo.size <= 0 || archivo.size > 5 * 1024 * 1024) {
    error.value = 'Usa una imagen JPEG, PNG o WebP de hasta 5 MiB.'
    input.value = ''
    return
  }
  if (vistaImagen.value.startsWith('blob:')) URL.revokeObjectURL(vistaImagen.value)
  error.value = ''
  archivoImagen.value = archivo
  archivoIdImagen.value = crypto.randomUUID()
  vistaImagen.value = URL.createObjectURL(archivo)
}

function actualizarUbicacion(ubicacion: { latitud: number; longitud: number; direccion: string }) {
  borrador.value = {
    ...borrador.value,
    direccion: ubicacion.direccion.slice(0, 300),
    latitud: String(ubicacion.latitud),
    longitud: String(ubicacion.longitud),
  }
}

function desplazarAlApartado(elemento: HTMLElement | null) {
  if (!elemento) return
  const reducirMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  elemento.scrollIntoView({ behavior: reducirMovimiento ? 'auto' : 'smooth', block: 'start' })
}

async function editar(item: FeriaAdmin) {
  feriaEditando.value = item
  limpiarArchivoImagen()
  borrador.value = {
    nombre: item.nombre, descripcion: item.descripcion, organizador: item.organizador, direccion: item.direccion,
    fecha: fechaLocal(item.fechaInicio), horaInicio: horaLocal(item.fechaInicio), horaFin: horaLocal(item.fechaFin),
    imagenUrl: item.imagenUrl, latitud: item.latitud === null ? '' : String(item.latitud), longitud: item.longitud === null ? '' : String(item.longitud),
  }
  borradorInicial.value = JSON.stringify(borrador.value)
  formulario.value = true; error.value = ''; aviso.value = ''
  await nextTick()
  desplazarAlApartado(formularioRef.value)
}

function payloadFeria(imagenUrl: string) {
  const tramo = fechaHora.value
  if (!tramo) throw new Error('Completa la fecha y el horario de la feria.')
  const datos: Record<string, unknown> = {
    nombre: borrador.value.nombre.trim(), descripcion: borrador.value.descripcion.trim(), organizador: borrador.value.organizador.trim(),
    direccion: borrador.value.direccion.trim(), fechaInicio: tramo.inicio.toISOString(), fechaFin: tramo.fin.toISOString(),
    imagenUrl,
  }
  const latitud = String(borrador.value.latitud ?? '').trim()
  const longitud = String(borrador.value.longitud ?? '').trim()
  datos.latitud = Number(latitud)
  datos.longitud = Number(longitud)
  return datos
}

async function procesarPendiente() {
  if (!pendiente.value || enviando.value) return
  enviando.value = true; error.value = ''; aviso.value = ''
  const current = pendiente.value
  try {
    await $amancay.llamar(current.operacion, current.solicitud)
    if (current.operacion === 'invitarParticipanteFeria' || current.operacion === 'agregarParticipanteFeria') candidata.value = ''
    pendiente.value = null; formulario.value = false; retiro.value = null
    limpiarArchivoImagen()
    aviso.value = current.operacion === 'enviarRecordatorioFeria' ? 'El proveedor aceptó el recordatorio. Esto todavía no confirma su entrega en el buzón' : current.operacion === 'enviarAvisoConfirmadasFeria' ? 'El proveedor aceptó el aviso de participación confirmada. La entrega todavía no está confirmada.' : 'Los cambios de la feria se guardaron en Firebase.'
    await cargar()
  } catch (cause) {
    error.value = mensajeError(cause)
    if (!(cause as { resultadoIncierto?: boolean })?.resultadoIncierto) pendiente.value = null
  } finally { enviando.value = false }
}

async function guardarFeria() {
  if (!habilitado || enviando.value || pendiente.value) return
  if (!borrador.value.nombre.trim() || !borrador.value.descripcion.trim() || !borrador.value.organizador.trim() || !borrador.value.direccion.trim()) {
    error.value = 'Completa nombre, descripción, organizador y lugar.'
    return
  }
  if (!fechaHora.value || !fechaHora.value.valido) {
    error.value = 'Completa la fecha y un horario con término posterior al inicio.'
    return
  }
  if (fechaHora.value.inicio.getTime() <= Date.now()) {
    error.value = 'Elige una fecha y hora de inicio posteriores a este momento.'
    return
  }
  if (!ubicacionMarcada.value) {
    error.value = 'Marca la ubicación de la feria en el mapa o busca su dirección.'
    return
  }
  if (!imagenLista.value) {
    error.value = 'Selecciona una imagen para la feria.'
    return
  }
  enviando.value = true; error.value = ''; aviso.value = ''
  try {
    let imagenUrl = borrador.value.imagenUrl.trim()
    if (archivoImagen.value) {
      subiendoImagen.value = true
      imagenUrl = await $amancay.subirImagenFeria(archivoImagen.value, archivoIdImagen.value)
      borrador.value.imagenUrl = imagenUrl
    }
    const datos = payloadFeria(imagenUrl)
    pendiente.value = feriaEditando.value
      ? { operacion: 'editarFeria', solicitud: { ...datos, operacionId: opId(), feriaId: feriaEditando.value.id, versionEsperada: feriaEditando.value.versionEsperada } }
      : { operacion: 'crearFeria', solicitud: { ...datos, operacionId: opId() } }
  } catch (cause) {
    subiendoImagen.value = false; enviando.value = false
    error.value = mensajeError(cause)
    return
  }
  subiendoImagen.value = false; enviando.value = false
  await procesarPendiente()
}

async function cambiarEstado(item: FeriaAdmin, estado: 'publicada' | 'cancelada') {
  if (!item.versionEsperada || pendiente.value) return
  focoAnteriorConfirmacion = document.activeElement instanceof HTMLElement ? document.activeElement : null
  confirmacionEstado.value = { item, estado }
  await nextTick()
  volverConfirmacionRef.value?.focus()
}

function cerrarConfirmacionEstado() {
  confirmacionEstado.value = null
  void nextTick(() => {
    focoAnteriorConfirmacion?.focus()
    focoAnteriorConfirmacion = null
  })
}

function confirmarCambioEstado() {
  const confirmacion = confirmacionEstado.value
  if (!confirmacion) return
  confirmacionEstado.value = null
  focoAnteriorConfirmacion = null
  pendiente.value = { operacion: 'cambiarEstadoFeria', solicitud: { operacionId: opId(), feriaId: confirmacion.item.id, versionEsperada: confirmacion.item.versionEsperada, estado: confirmacion.estado } }
  void procesarPendiente()
}

function mantenerFocoConfirmacion(event: KeyboardEvent) {
  if (event.key !== 'Tab') return
  const volver = volverConfirmacionRef.value
  const ejecutar = ejecutarConfirmacionRef.value
  if (!volver || !ejecutar) return
  if (event.shiftKey && document.activeElement === volver) {
    event.preventDefault()
    ejecutar.focus()
  } else if (!event.shiftKey && document.activeElement === ejecutar) {
    event.preventDefault()
    volver.focus()
  }
}

async function gestionarParticipantes(item: FeriaAdmin) {
  retiro.value = null
  const carga = cargarParticipantes(item.id)
  await nextTick()
  desplazarAlApartado(participantesRef.value)
  await carga
}

function cerrarParticipantes() {
  selectedId.value = ''
  detalle.value = null
  candidata.value = ''
  tipoCandidata.value = 'emprendedora'
  busquedaCandidata.value = ''
  retiro.value = null
  busquedaParticipante.value = ''
  filtroEstadoParticipante.value = 'todos'
  filtroTipoParticipante.value = 'todos'
  filtroTipoEmprendimiento.value = 'todos'
  filtroAsociacion.value = 'todas'
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
  const operacion = retiro.value.estado === 'invitada' ? 'cancelarInvitacionFeria' : 'retirarParticipanteFeria'
  pendiente.value = { operacion, solicitud: { operacionId: opId(), feriaId: detalle.value.feria.id, versionFeriaEsperada: detalle.value.feria.versionEsperada, participanteId: retiro.value.id } }
  void procesarPendiente()
}

function etiquetaEstadoParticipante(persona: ParticipanteFeria) {
  return estadosParticipante.find((estado) => estado.valor === persona.estado)?.etiqueta ?? persona.estado
}

function etiquetaAsistencia(asistencia: ParticipanteFeria['asistencia']) {
  if (asistencia === 'asistio') return 'Asistió'
  if (asistencia === 'no_asistio') return 'No asistió'
  return 'Asistencia pendiente'
}


function prepararRecordatorioFeria(persona: ParticipanteFeria) {
  if (!detalle.value || pendiente.value || enviando.value) return

  if (
    detalle.value.feria.estado !== 'publicada' ||
    persona.estado !== 'invitada'
  ) {
    error.value =
      'Solo puedes enviar recordatorios a invitaciones pendientes de ferias publicadas.'
    return
  }

  if (
    !window.confirm(
      `¿Enviar un recordatorio real a ${persona.nombreTienda || persona.nombre}?`
    )
  ) {
    return
  }

  pendiente.value = {
    operacion: 'enviarRecordatorioFeria',
    solicitud: {
      operacionId: opId(),
      feriaId: detalle.value.feria.id,
      participanteId: persona.id,
    },
  }

  void procesarPendiente()
}

function enviarAvisoConfirmadas(persona: ParticipanteFeria) {
  if (!detalle.value || pendiente.value || enviando.value || !habilitado) {return}

  if (
    detalle.value.feria.estado !== 'publicada' ||
    persona.estado !== 'confirmada'
  ) {
    error.value =
      'Solo puedes enviar avisos a participantes confirmadas de ferias publicadas.'
    return
  }

  const nombre = persona.nombreTienda || persona.nombre

  if (!window.confirm(
    `¿Enviar un aviso real de participación confirmada a ${nombre}?`
  )) {
    return
  }

  pendiente.value = {
    operacion: 'enviarAvisoConfirmadasFeria',
    solicitud: {
      operacionId: opId(),
      feriaId: detalle.value.feria.id,
      participanteId: persona.id
    }
  }
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
    <div class="nota"><span aria-hidden="true">🎪</span><p>Administra fechas, lugar, imagen y participantes. La imagen se sube al almacenamiento y queda asociada a la feria. Las invitaciones se registran en Firebase. Los recordatorios para invitaciones pendientes se envían desde el servidor y muestran cuándo el proveedor aceptó el correo (no su entrega final).</p></div>
    <p v-if="!habilitado" class="nota" role="status">Este entorno es de solo lectura. Conecta la API autenticada para administrar ferias.</p>
    <div class="barra"><div><h2>Ferias y eventos</h2><p>{{ ferias.length }} registros</p></div><div class="acciones"><button class="secundario" type="button" :disabled="cargando || enviando" @click="cargar">{{ cargando ? 'Actualizando…' : 'Actualizar' }}</button><button class="boton" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="nuevo">+ Crear feria</button></div></div>
    <p v-if="cargando" role="status">Leyendo ferias desde Firebase…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="aviso" class="exito" role="status">{{ aviso }}</p>
    <div v-if="pendiente" class="nota pendiente" role="status" tabindex="-1"><p>La respuesta de la API no se confirmó. Conservé el mismo identificador para evitar duplicar la operación.</p><button class="boton" type="button" :disabled="enviando" @click="procesarPendiente">{{ enviando ? 'Procesando…' : 'Reintentar operación' }}</button></div>

    <section v-if="formulario" ref="formularioRef" class="formulario apartado-desplegable">
      <div class="formulario-cabecera"><div><p class="sobre">{{ feriaEditando ? 'Editar información' : 'Nueva actividad' }}</p><h3>{{ feriaEditando ? feriaEditando.nombre : 'Crear feria' }}</h3></div><button class="secundario" type="button" :disabled="enviando || !!pendiente" @click="cerrarFormulario">Cerrar</button></div>
      <form class="campos" @submit.prevent="guardarFeria">
        <label>Nombre de la feria<input v-model="borrador.nombre" maxlength="120" required :disabled="enviando || !!pendiente" /></label>
        <label>Organizador o responsable<input v-model="borrador.organizador" maxlength="120" required :disabled="enviando || !!pendiente" /></label>
        <label class="completo">Descripción<textarea v-model="borrador.descripcion" maxlength="2000" rows="3" required :disabled="enviando || !!pendiente" /></label>
        <label class="completo">Lugar<input v-model="borrador.direccion" maxlength="300" required placeholder="Ej.: Sede de la Asociación, Alerce" :disabled="enviando || !!pendiente" /></label>
        <SelectorUbicacionFeria
          class="completo"
          :latitud="borrador.latitud.trim() ? Number(borrador.latitud) : null"
          :longitud="borrador.longitud.trim() ? Number(borrador.longitud) : null"
          :direccion="borrador.direccion"
          :disabled="enviando || !!pendiente"
          @ubicacion-seleccionada="actualizarUbicacion"
        />
        <p v-if="!ubicacionMarcada" class="aviso-ubicacion completo" role="status">Marca un punto en el mapa o busca una dirección para guardar la ubicación de la feria.</p>
        <div class="completo horario">
          <label>Fecha<input v-model="borrador.fecha" type="date" required :disabled="enviando || !!pendiente" /></label>
          <label>Hora de inicio<input v-model="borrador.horaInicio" type="time" required :disabled="enviando || !!pendiente" /></label>
          <label>Hora de término<input v-model="borrador.horaFin" type="time" required :disabled="enviando || !!pendiente" /></label>
        </div>
        <p v-if="borrador.fecha && borrador.horaInicio && borrador.horaFin && !fechaHora?.valido" class="aviso-ubicacion completo" role="alert">La hora de término debe ser posterior a la de inicio.</p>
        <p v-else-if="inicioPasado" class="aviso-ubicacion completo" role="alert">La fecha y hora de inicio deben ser futuras.</p>
        <div class="completo imagen-campo">
          <p class="etiqueta">Imagen de la feria</p>
          <input type="file" accept="image/jpeg,image/png,image/webp" :disabled="enviando || !!pendiente" @change="seleccionarImagen" />
          <small>Se sube al almacenamiento y se guarda su URL en la feria. JPEG, PNG o WebP de hasta 5 MiB.</small>
          <img v-if="vistaImagen || borrador.imagenUrl" :src="vistaImagen || borrador.imagenUrl" alt="Vista previa de la imagen de la feria" />
        </div>
        <div class="acciones completo"><button class="boton" type="submit" :disabled="guardadoFeriaDeshabilitado">{{ subiendoImagen ? 'Subiendo imagen…' : enviando ? 'Guardando…' : feriaEditando ? 'Guardar cambios' : 'Crear borrador' }}</button><button class="secundario" type="button" :disabled="enviando || !!pendiente" @click="cerrarFormulario">Cancelar</button></div>
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

    <section v-if="selectedId" ref="participantesRef" class="participantes apartado-desplegable">
      <div class="formulario-cabecera"><div><p class="sobre">Lista y control</p><h3>Participantes</h3></div><div class="acciones"><button class="secundario" type="button" :disabled="cargandoDetalle || enviando || !!pendiente" @click="cargarParticipantes(selectedId)">Actualizar</button><button class="secundario" type="button" :disabled="enviando || !!pendiente" @click="cerrarParticipantes">Cerrar</button></div></div>
      <p v-if="cargandoDetalle" role="status">Consultando participantes…</p>
      <template v-if="detalle">
        <div class="asignar">
          <div class="tipo-participante">
            <p class="etiqueta-candidata">Tipo de participante</p>
            <div class="selector-tipo" role="group" aria-label="Tipo de participante">
              <button
                class="tipo-candidata"
                :class="{ activa: tipoCandidata === 'emprendedora' }"
                type="button"
                :aria-pressed="tipoCandidata === 'emprendedora'"
                :disabled="enviando || !!pendiente"
                @click="seleccionarTipoCandidata('emprendedora')"
              >
                Emprendedoras <span>{{ totalEmprendedorasDisponibles }}</span>
              </button>
              <button
                class="tipo-candidata"
                :class="{ activa: tipoCandidata === 'asociacion' }"
                type="button"
                :aria-pressed="tipoCandidata === 'asociacion'"
                :disabled="enviando || !!pendiente"
                @click="seleccionarTipoCandidata('asociacion')"
              >
                Asociaciones <span>{{ totalAsociacionesDisponibles }}</span>
              </button>
            </div>
          </div>

          <section class="disponibles" :aria-labelledby="'titulo-disponibles-' + selectedId">
            <div class="disponibles-cabecera">
              <div>
                <h4 :id="'titulo-disponibles-' + selectedId">{{ tipoCandidata === 'emprendedora' ? 'Emprendedoras' : 'Asociaciones' }} disponibles</h4>
                <p>Selecciona una persona o entidad para continuar.</p>
              </div>
              <span class="cantidad-disponible">{{ disponiblesPorTipo.length }} disponibles</span>
            </div>
            <label class="busqueda-candidata">
              <span>Buscar por tienda o nombre</span>
              <input
                v-model="busquedaCandidata"
                type="search"
                :placeholder="tipoCandidata === 'emprendedora' ? 'Buscar emprendedora…' : 'Buscar asociación…'"
                :disabled="cargandoDetalle || enviando || !!pendiente"
              />
            </label>
            <div
              v-if="candidatasFiltradas.length"
              class="opciones-disponibles"
              role="radiogroup"
              :aria-label="tipoCandidata === 'emprendedora' ? 'Emprendedoras disponibles' : 'Asociaciones disponibles'"
            >
              <label
                v-for="(opcion, index) in candidatasFiltradas"
                :key="opcion.tipo + ':' + opcion.entidadId + ':' + index"
                class="opcion-disponible"
                :class="{ seleccionada: candidata === opcion.tipo + ':' + opcion.entidadId }"
              >
                <input
                  v-model="candidata"
                  type="radio"
                  :name="'participante-feria-' + selectedId"
                  :value="opcion.tipo + ':' + opcion.entidadId"
                  :disabled="!habilitado || enviando || !!pendiente"
                />
                <span class="radio-visual" aria-hidden="true"></span>
                <span class="opcion-texto">
                  <strong>{{ tipoCandidata === 'emprendedora' ? (opcion.nombreTienda || 'Tienda') : opcion.nombre }}</strong>
                  <small>{{ tipoCandidata === 'emprendedora' ? opcion.nombre : 'Asociación' }}</small>
                </span>
              </label>
            </div>
            <p v-else-if="disponiblesPorTipo.length" class="sin-disponibles" role="status">
              No encontramos opciones que coincidan con “{{ busquedaCandidata }}”.
            </p>
            <p v-else class="sin-disponibles" role="status">
              {{ tipoCandidata === 'emprendedora' ? 'No hay emprendedoras disponibles para esta feria.' : 'No hay asociaciones disponibles para esta feria.' }}
            </p>
          </section>

          <div class="accion-participante">
            <label>Acción
              <select v-model="tipoGestion" :disabled="enviando || !!pendiente">
                <option value="invitarParticipanteFeria">Enviar invitación (queda pendiente)</option>
                <option value="agregarParticipanteFeria">Agregar directamente</option>
              </select>
            </label>
            <button class="boton" type="button" :disabled="!habilitado || !seleccionDisponible || enviando || !!pendiente" @click="agregarCandidata">{{ tipoGestion === 'invitarParticipanteFeria' ? 'Registrar invitación' : 'Agregar a la feria' }}</button>
          </div>
        </div>
        <div v-if="detalle.participantes.length" class="filtros participantes-filtros">
          <label>Buscar<input v-model="busquedaParticipante" type="search" placeholder="Nombre, tienda o asociación" /></label>
          <label>Estado<select v-model="filtroEstadoParticipante"><option value="todos">Todos ({{ detalle.participantes.length }})</option><option v-for="estado in estadosParticipante" :key="estado.valor" :value="estado.valor">{{ estado.etiqueta }} ({{ cantidadPorEstado(estado.valor) }})</option></select></label>
          <label>Participante<select v-model="filtroTipoParticipante"><option value="todos">Todos</option><option value="emprendedora">Emprendedoras</option><option value="asociacion">Asociaciones</option></select></label>
          <label>Tipo de emprendimiento<select v-model="filtroTipoEmprendimiento"><option value="todos">Todos</option><option v-for="tipo in tiposEmprendimientoParticipantes" :key="tipo.id" :value="tipo.id">{{ tipo.nombre }}</option></select></label>
          <label>Asociación<select v-model="filtroAsociacion"><option value="todas">Todas</option><option v-for="asociacion in asociacionesParticipantes" :key="asociacion.id" :value="asociacion.id">{{ asociacion.nombre }}</option></select></label>
        </div>
        <ul v-if="participantesVisibles.length" class="lista participantes-lista">
          <li v-for="persona in participantesVisibles" :key="persona.id" class="participante">
            <div class="persona">
              <strong>{{ persona.nombreTienda || persona.nombre }}</strong>
              <small>{{ persona.tipo === 'asociacion' ? 'Asociación' : persona.nombre }}</small>
              <small v-if="persona.tipo === 'emprendedora' && (persona.tipoEmprendimiento || persona.asociaciones?.length)">{{ [persona.tipoEmprendimiento?.nombre, ...(persona.asociaciones ?? []).map((asociacion) => asociacion.nombre)].filter(Boolean).join(' · ') }}</small>
            </div>
            <div class="estados-participante">
              <span class="estado estado-participante" :class="`participacion-${persona.estado}`">{{ etiquetaEstadoParticipante(persona) }}</span>
              <span v-if="persona.asistencia" class="estado asistencia">{{ etiquetaAsistencia(persona.asistencia) }}</span>
            </div>
            <div class="acciones" v-if="['confirmada', 'agregada_admin'].includes(persona.estado)"><button class="secundario" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="marcarAsistencia(persona, 'asistio')">Asistió</button><button class="secundario" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="marcarAsistencia(persona, 'no_asistio')">No asistió</button></div>
            <button v-if="persona.estado === 'confirmada' && detalle.feria.estado === 'publicada'" class="secundario" type="button" :disabled="!habilitado || enviando || !!pendiente || persona.avisoConfirmacion?.estado === 'reservado'" @click="enviarAvisoConfirmadas(persona)">Enviar aviso</button>
            <small v-if="persona.avisoConfirmacion?.estado === 'aceptado'">Último aviso aceptado: {{ persona.avisoConfirmacion.enviadoEn ? fechaBonita(persona.avisoConfirmacion.enviadoEn) : 'fecha pendiente' }}</small>
            <small v-else-if="persona.avisoConfirmacion?.estado === 'reservado'">Aviso en proceso o pendiente de verificación</small>
            <button v-if="persona.estado === 'invitada' && detalle.feria.estado === 'publicada'" class="secundario" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="prepararRecordatorioFeria(persona)"> Enviar recordatorio </button>
            <small v-if="persona.recordatorio?.estado === 'aceptado'"> Último recordatorio aceptado: {{ persona.recordatorio.enviadoEn ? fechaBonita(persona.recordatorio.enviadoEn) : 'fecha pendiente' }} </small>
            <small v-else-if="persona.recordatorio?.estado === 'reservado'"> Recordatorio en proceso o resultado por verificar</small>
            <button v-if="persona.estado === 'invitada'" class="peligro" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="retirar(persona)">Cancelar invitación</button>
            <button v-else-if="['confirmada', 'agregada_admin'].includes(persona.estado)" class="peligro" type="button" :disabled="!habilitado || enviando || !!pendiente" @click="retirar(persona)">Retirar</button>
          </li>
        </ul>
        <p v-else class="vacio">{{ detalle.participantes.length ? 'No hay participantes que coincidan con el filtro.' : 'No hay participantes asociados a esta feria.' }}</p>
      </template>
    </section>
    <div v-if="retiro" class="confirmacion" role="group" :aria-label="retiro.estado === 'invitada' ? 'Confirmar cancelación de invitación' : 'Confirmar retiro'">
      <p v-if="retiro.estado === 'invitada'">¿Cancelar la invitación pendiente para <strong>{{ retiro.nombreTienda || retiro.nombre }}</strong>? Se quitará de esta feria y podrás invitarle de nuevo más adelante.</p>
      <p v-else>¿Retirar a <strong>{{ retiro.nombreTienda || retiro.nombre }}</strong>? Se conservará el historial.</p>
      <button class="peligro" type="button" :disabled="enviando || !!pendiente" @click="confirmarRetiro">{{ retiro.estado === 'invitada' ? 'Confirmar cancelación' : 'Confirmar retiro' }}</button>
      <button class="secundario" type="button" :disabled="enviando || !!pendiente" @click="retiro = null">Volver</button>
    </div>

    <Transition name="confirmacion-modal">
      <div
        v-if="confirmacionEstado"
        class="confirmacion-fondo"
        @click.self="cerrarConfirmacionEstado"
        @keydown.esc.stop.prevent="cerrarConfirmacionEstado"
      >
        <section
          ref="dialogoConfirmacionRef"
          class="confirmacion-dialogo"
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirmacion-feria-titulo"
          aria-describedby="confirmacion-feria-descripcion"
          tabindex="-1"
          @keydown="mantenerFocoConfirmacion"
        >
          <div class="confirmacion-icono" :class="{ cancelacion: confirmacionEstado.estado === 'cancelada' }" aria-hidden="true">
            <svg v-if="confirmacionEstado.estado === 'publicada'" viewBox="0 0 24 24" fill="none">
              <path d="M5 12.5 9.2 17 19 7" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none">
              <path d="M7 7l10 10M17 7 7 17" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
            </svg>
          </div>
          <p class="confirmacion-sobre">{{ confirmacionEstado.estado === 'publicada' ? 'Publicar feria' : 'Cancelar feria' }}</p>
          <h3 id="confirmacion-feria-titulo">{{ confirmacionEstado.estado === 'publicada' ? '¿Publicar esta feria?' : '¿Cancelar esta feria?' }}</h3>
          <p id="confirmacion-feria-descripcion" class="confirmacion-descripcion">
            <strong>“{{ confirmacionEstado.item.nombre }}”</strong>
            <template v-if="confirmacionEstado.estado === 'publicada'"> quedará publicada y podrá aparecer en el catálogo público.</template>
            <template v-else> dejará de estar activa. Esta acción no se puede revertir desde este módulo.</template>
          </p>
          <div class="confirmacion-acciones">
            <button ref="volverConfirmacionRef" class="secundario" type="button" @click="cerrarConfirmacionEstado">Volver</button>
            <button
              ref="ejecutarConfirmacionRef"
              :class="confirmacionEstado.estado === 'publicada' ? 'boton' : 'peligro'"
              type="button"
              @click="confirmarCambioEstado"
            >
              {{ confirmacionEstado.estado === 'publicada' ? 'Publicar feria' : 'Confirmar cancelación' }}
            </button>
          </div>
        </section>
      </div>
    </Transition>
  </section>
</template>

<style scoped>
.ferias-admin{--vino:#75264f;--vino-oscuro:#5a1c3c;--crema:#fff8ef;--borde:#ead9cc;--texto:#3a1228;color:var(--texto);display:grid;gap:18px}.nota,.formulario,.feria,.participantes,.confirmacion{background:#fff;border:1px solid var(--borde);border-radius:20px;padding:18px}.nota{display:flex;gap:12px;background:var(--crema);color:#6d5663;line-height:1.55}.nota p{margin:0}.barra,.formulario-cabecera{display:flex;align-items:center;justify-content:space-between;gap:12px}.barra h2,.formulario h3,.participantes h3,.feria h3{font:400 1.55rem 'DM Serif Display',serif;color:var(--vino);margin:4px 0}.barra p,.feria p,.formulario p{margin:4px 0;color:#775f6c}.acciones{display:flex;gap:9px;align-items:center;flex-wrap:wrap}.boton,.secundario,.peligro{border:0;border-radius:999px;padding:10px 15px;font:inherit;font-weight:700;cursor:pointer;transition:background-color .16s,border-color .16s,box-shadow .16s,transform .16s}.boton{background:var(--vino);color:#fff}.secundario{background:#f4e7ed;color:var(--vino)}.peligro{background:#fff0e8;color:#9c3a28}.boton:not(:disabled):hover,.secundario:not(:disabled):hover,.peligro:not(:disabled):hover{transform:translateY(-2px);box-shadow:0 8px 18px rgba(90,28,60,.18)}.boton:not(:disabled):hover{background:var(--vino-oscuro)}.secundario:not(:disabled):hover{background:#ead4df}.peligro:not(:disabled):hover{background:#ffdfd0}button:disabled{opacity:.55;cursor:not-allowed}.boton:focus-visible,.secundario:focus-visible,.peligro:focus-visible{outline:3px solid #d8a46c;outline-offset:3px}.formulario{display:grid;gap:16px}.apartado-desplegable{scroll-margin-top:1rem}.sobre{font-size:.74rem!important;text-transform:uppercase;letter-spacing:.12em;color:var(--vino)!important;font-weight:700}.campos{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.campos label,.asignar label,.filtros label{display:grid;gap:7px;font-weight:600}.filtros{display:grid;grid-template-columns:2fr 1fr;gap:12px}.participantes-filtros{grid-template-columns:2fr repeat(4,minmax(0,1fr))}input,textarea,select{width:100%;border:1px solid var(--borde);border-radius:12px;padding:11px;font:inherit;color:var(--texto);background:#fff}.completo{grid-column:1/-1}.lista{list-style:none;margin:0;padding:0;display:grid;gap:12px}.feria{display:grid;gap:12px;transition:border-color .16s,box-shadow .16s,transform .16s}.feria:hover{border-color:var(--vino);box-shadow:0 14px 30px rgba(117,38,79,.12);transform:translateY(-2px)}.feria-cabecera{display:flex;justify-content:space-between;gap:15px}.feria-cabecera>div{min-width:0}.feria-cabecera img{width:128px;height:100px;object-fit:cover;border-radius:14px}.feria small,.participante small{color:#775f6c}.estado{display:inline-block;border-radius:999px;padding:5px 10px;font-size:.78rem;font-weight:700}.estado.borrador{background:#f9edda;color:#755319}.estado.publicada{background:#e8f4df;color:#365b2f}.estado.cancelada{background:#f8e1e1;color:#8e2b38}.asistencia{background:#f4e7ed;color:var(--vino)}.participantes{display:grid;gap:15px}.asignar{display:grid;grid-template-columns:2fr 1.5fr auto;gap:12px;align-items:end}.participantes-lista{border-top:1px solid var(--borde);padding-top:14px}.participante{display:flex;align-items:center;gap:12px;padding:12px;border:1px solid var(--borde);border-radius:15px;flex-wrap:wrap;transition:border-color .16s,box-shadow .16s,transform .16s}.participante:hover{border-color:var(--durazno);box-shadow:0 8px 20px rgba(117,38,79,.1);transform:translateY(-1px)}.persona{display:grid;gap:4px;flex:1;min-width:150px}.confirmacion{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.confirmacion p{flex:1}.vacio{padding:24px;text-align:center;color:#775f6c;border:1px dashed var(--borde);border-radius:16px}.error{color:#a32335}.exito{color:#27603b}.pendiente{display:grid}.barra>div:first-child{flex:1}@media(max-width:760px){.barra,.formulario-cabecera{align-items:flex-start;flex-direction:column}.campos,.asignar,.filtros,.participantes-filtros{grid-template-columns:1fr}.completo{grid-column:auto}.feria-cabecera img{width:88px;height:78px}.participante{align-items:flex-start}.feria-cabecera{align-items:flex-start}}
.horario{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.horario label{display:grid;gap:7px;font-weight:600}.imagen-campo{display:grid;gap:8px}.imagen-campo .etiqueta{font-weight:600;color:var(--texto);margin:0}.imagen-campo small{color:#775f6c}.imagen-campo input[type=file]{padding:9px;background:var(--crema)}.imagen-campo img{max-width:260px;max-height:180px;border-radius:14px;object-fit:cover;border:1px solid var(--borde)}@media(max-width:680px){.horario{grid-template-columns:1fr}}
@media(prefers-reduced-motion:reduce){.boton,.secundario,.peligro,.feria,.participante{transition:none}.boton:not(:disabled):hover,.secundario:not(:disabled):hover,.peligro:not(:disabled):hover,.feria:hover,.participante:hover{transform:none}}
.confirmacion-fondo{position:fixed;inset:0;z-index:10000;display:grid;place-items:center;padding:20px;background:rgba(42,18,33,.46);backdrop-filter:blur(4px)}.confirmacion-dialogo{width:min(100%,480px);padding:28px;border:1px solid var(--borde);border-radius:24px;background:#fffdf9;box-shadow:0 24px 70px rgba(42,18,33,.26);color:var(--texto)}.confirmacion-icono{display:grid;place-items:center;width:48px;height:48px;margin-bottom:18px;border:1px solid #ead9cc;border-radius:16px;background:var(--crema);color:var(--vino)}.confirmacion-icono.cancelacion{border-color:#f1d6ca;background:#fff0e8;color:#9c3a28}.confirmacion-icono svg{width:25px;height:25px}.confirmacion-sobre{margin:0 0 5px!important;color:var(--vino)!important;font-size:.75rem;font-weight:800;letter-spacing:.12em;text-transform:uppercase}.confirmacion-dialogo h3{margin:0;color:var(--vino);font:400 1.8rem 'DM Serif Display',serif}.confirmacion-descripcion{margin:12px 0 24px!important;color:#775f6c;line-height:1.6}.confirmacion-descripcion strong{color:var(--texto)}.confirmacion-acciones{display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap}.confirmacion-acciones button{min-height:44px}.confirmacion-modal-enter-active,.confirmacion-modal-leave-active{transition:opacity .18s ease}.confirmacion-modal-enter-active .confirmacion-dialogo,.confirmacion-modal-leave-active .confirmacion-dialogo{transition:transform .18s ease,opacity .18s ease}.confirmacion-modal-enter-from,.confirmacion-modal-leave-to{opacity:0}.confirmacion-modal-enter-from .confirmacion-dialogo,.confirmacion-modal-leave-to .confirmacion-dialogo{transform:translateY(8px) scale(.985);opacity:0}@media(max-width:520px){.confirmacion-dialogo{padding:22px;border-radius:20px}.confirmacion-acciones{flex-direction:column-reverse}.confirmacion-acciones button{width:100%}}@media(prefers-reduced-motion:reduce){.confirmacion-modal-enter-active,.confirmacion-modal-leave-active,.confirmacion-modal-enter-active .confirmacion-dialogo,.confirmacion-modal-leave-active .confirmacion-dialogo{transition:none}}
.asignar{grid-template-columns:minmax(0,1fr);align-items:stretch;gap:16px}
.etiqueta-candidata{margin:0 0 8px;color:var(--texto);font-weight:700}
.selector-tipo{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;padding:6px;border:1px solid var(--borde);border-radius:16px;background:var(--crema)}
.tipo-candidata{display:flex;justify-content:center;align-items:center;gap:9px;min-height:46px;padding:9px 14px;border:1px solid transparent;border-radius:12px;background:transparent;color:var(--vino);font:inherit;font-weight:700;cursor:pointer;transition:background-color .16s,border-color .16s,box-shadow .16s,transform .16s}
.tipo-candidata span{display:grid;place-items:center;min-width:25px;height:25px;padding:0 7px;border-radius:999px;background:#f4e7ed;font-size:.82rem}
.tipo-candidata:hover:not(:disabled){transform:translateY(-1px);background:#f4e7ed}
.tipo-candidata.activa{border-color:#e6cad8;background:#fff;color:var(--vino-oscuro);box-shadow:0 3px 10px rgba(90,28,60,.09)}
.tipo-candidata:focus-visible{outline:3px solid #d8a46c;outline-offset:2px}
.disponibles{display:grid;gap:12px;padding:16px;border:1px solid var(--borde);border-radius:18px;background:#fffdf9}
.disponibles-cabecera{display:flex;justify-content:space-between;align-items:center;gap:12px}
.disponibles-cabecera h4{margin:0;color:var(--vino);font:400 1.2rem 'DM Serif Display',serif}
.disponibles-cabecera p{margin:4px 0 0;color:#775f6c}
.cantidad-disponible{flex:none;padding:6px 10px;border-radius:999px;background:#f4e7ed;color:var(--vino);font-size:.84rem;font-weight:700}
.opciones-disponibles{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,220px),1fr));gap:10px}
.opcion-disponible{position:relative;display:grid!important;grid-template-columns:20px minmax(0,1fr);align-items:center;gap:11px!important;min-height:70px;padding:12px;border:1px solid var(--borde);border-radius:14px;background:#fff;cursor:pointer;transition:border-color .16s,box-shadow .16s,transform .16s}
.opcion-disponible:hover{transform:translateY(-2px);border-color:#c995ad;box-shadow:0 8px 18px rgba(90,28,60,.1)}
.opcion-disponible.seleccionada{border-color:var(--vino);background:#fff9fc;box-shadow:0 0 0 2px rgba(117,38,79,.1)}
.opcion-disponible input[type=radio]{position:absolute;width:1px;height:1px;padding:0;border:0;opacity:0;clip-path:inset(50%);overflow:hidden;white-space:nowrap}
.radio-visual{display:grid;place-items:center;width:19px;height:19px;border:2px solid #b69aa8;border-radius:50%;background:#fff}
.opcion-disponible.seleccionada .radio-visual{border-color:var(--vino)}
.opcion-disponible.seleccionada .radio-visual::after{width:9px;height:9px;border-radius:50%;background:var(--vino);content:''}
.opcion-disponible input:focus-visible+.radio-visual{outline:3px solid #d8a46c;outline-offset:3px}
.opcion-texto{display:grid;gap:3px;min-width:0}
.opcion-texto strong,.opcion-texto small{overflow-wrap:anywhere}
.opcion-texto strong{color:var(--texto)}
.opcion-texto small{color:#775f6c;font-weight:400}
.sin-disponibles{margin:0;padding:14px;border:1px dashed var(--borde);border-radius:14px;background:var(--crema);color:#775f6c}
.accion-participante{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:end;gap:12px}
.accion-participante .boton{min-height:46px}
.estados-participante{display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.estado-participante{background:#f4e7ed;color:var(--vino)}
.estado-participante.participacion-invitada{background:#f9edda;color:#755319}
.estado-participante.participacion-confirmada{background:#e8f4df;color:#365b2f}
.estado-participante.participacion-rechazada{background:#f8e1e1;color:#8e2b38}
.estado-participante.participacion-retirada{background:#eee9ec;color:#6d5663}
@media(max-width:1100px) and (min-width:761px){.participantes-filtros{grid-template-columns:repeat(3,minmax(0,1fr))}.participantes-filtros>label:first-child{grid-column:1/-1}}
@media(max-width:760px){.asignar{grid-template-columns:1fr}.accion-participante{grid-template-columns:1fr}.accion-participante .boton{width:100%}.disponibles-cabecera{align-items:flex-start}}
@media(max-width:520px){.selector-tipo{grid-template-columns:1fr}.disponibles{padding:12px}.disponibles-cabecera{flex-direction:column}.cantidad-disponible{align-self:flex-start}}
@media(prefers-reduced-motion:reduce){.tipo-candidata,.opcion-disponible{transition:none}.tipo-candidata:hover:not(:disabled),.opcion-disponible:hover{transform:none}}
.busqueda-candidata{display:grid;gap:6px;color:#775f6c;font-size:.88rem;font-weight:600}
.busqueda-candidata input{min-height:42px;font-size:1rem}
.opciones-disponibles{grid-template-columns:repeat(2,minmax(0,1fr));align-content:start;gap:8px;max-height:256px;overflow-y:auto;overscroll-behavior:contain;padding:2px 5px 2px 2px;scrollbar-width:thin}
.opcion-disponible{grid-template-columns:18px minmax(0,1fr);gap:9px!important;min-height:52px;padding:8px 10px;border-radius:12px}
.opcion-disponible:hover{transform:translateY(-1px);box-shadow:0 5px 12px rgba(90,28,60,.08)}
.radio-visual{width:17px;height:17px}
.opcion-disponible.seleccionada .radio-visual::after{width:7px;height:7px}
.opcion-texto strong{font-size:.9rem}
.opcion-texto small{font-size:.8rem}
@media(max-width:560px){.opciones-disponibles{grid-template-columns:1fr;max-height:288px}}
@media(max-width:520px){.selector-tipo{grid-template-columns:repeat(2,minmax(0,1fr));gap:4px;padding:4px}.tipo-candidata{gap:4px;padding:7px 3px;font-size:.78rem}}
@media(prefers-reduced-motion:reduce){.opcion-disponible:hover{transform:none}}
</style>
