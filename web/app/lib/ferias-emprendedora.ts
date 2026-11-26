export type EstadoInvitacionFeria = 'pendiente' | 'aceptada' | 'rechazada' | 'agregada_admin'

export type InvitacionFeriaWeb = {
  id: string
  nombre: string
  donde: string
  organiza: string
  fechaInicio: Date | null
  fechaFin: Date | null
  estado: EstadoInvitacionFeria
  descripcion: string
  imagenUrl: string
  latitud: number | null
  longitud: number | null
  invitadaEn: Date | null
}

const ESTADOS: Record<string, EstadoInvitacionFeria> = {
  invitada: 'pendiente',
  confirmada: 'aceptada',
  rechazada: 'rechazada',
  agregada_admin: 'agregada_admin',
}

function fechaIso(valor: unknown): Date | null {
  if (typeof valor !== 'string' || !valor) return null
  const fecha = new Date(valor)
  return Number.isNaN(fecha.getTime()) ? null : fecha
}

function coordenada(valor: unknown, limite: number): number | null {
  return typeof valor === 'number' && Number.isFinite(valor) && Math.abs(valor) <= limite ? valor : null
}

function texto(valor: unknown): string {
  return typeof valor === 'string' ? valor.trim() : ''
}

export function mapearInvitacionesFeria(datos: Record<string, unknown>): InvitacionFeriaWeb[] {
  if (!Array.isArray(datos.invitaciones)) throw new Error('No se pudieron cargar las ferias.')
  return datos.invitaciones.flatMap((valor) => {
    if (!valor || typeof valor !== 'object' || Array.isArray(valor)) return []
    const item = valor as Record<string, unknown>
    const id = texto(item.id)
    const estado = ESTADOS[texto(item.estado)]
    if (!id || !estado) return []
    return [{
      id,
      nombre: texto(item.nombre) || 'Feria',
      donde: texto(item.donde) || 'Lugar por confirmar',
      organiza: texto(item.organiza) || 'Equipo organizador',
      fechaInicio: fechaIso(item.fechaInicio),
      fechaFin: fechaIso(item.fechaFin),
      estado,
      descripcion: texto(item.descripcion),
      imagenUrl: /^https:\/\//.test(texto(item.imagenUrl)) ? texto(item.imagenUrl) : '',
      latitud: coordenada(item.latitud, 90),
      longitud: coordenada(item.longitud, 180),
      invitadaEn: fechaIso(item.invitadaEn),
    }]
  })
}

const zona = 'America/Santiago'

export function diaFeria(fecha: Date | null): string {
  if (!fecha) return 'Fecha por confirmar'
  const texto = new Intl.DateTimeFormat('es-CL', { timeZone: zona, weekday: 'long', day: 'numeric', month: 'long' }).format(fecha)
  return texto.charAt(0).toLocaleUpperCase('es-CL') + texto.slice(1)
}

export function horarioFeria(inicio: Date | null, fin: Date | null): string {
  if (!inicio || !fin) return 'Horario por confirmar'
  const hora = new Intl.DateTimeFormat('es-CL', { timeZone: zona, hour: '2-digit', minute: '2-digit', hour12: false })
  return `${hora.format(inicio)} a ${hora.format(fin)}`
}

export function claveDia(fecha: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: zona, year: 'numeric', month: '2-digit', day: '2-digit' }).format(fecha)
}

export function agruparPorDia(invitaciones: InvitacionFeriaWeb[]): Map<string, InvitacionFeriaWeb[]> {
  const grupos = new Map<string, InvitacionFeriaWeb[]>()
  for (const invitacion of invitaciones) {
    if (!invitacion.fechaInicio || invitacion.estado === 'rechazada') continue
    const clave = claveDia(invitacion.fechaInicio)
    grupos.set(clave, [...(grupos.get(clave) ?? []), invitacion])
  }
  return grupos
}

type LlamarApi = (operacion: 'listarInvitacionesFeria', solicitud: Record<string, unknown>) => Promise<Record<string, unknown>>

const DURACION_CACHE = 30_000
const cacheInvitaciones = new Map<string, { valor: InvitacionFeriaWeb[]; vence: number }>()
const lecturasPendientes = new Map<string, Promise<InvitacionFeriaWeb[]>>()

export function invitacionesEnCache(uid: string): InvitacionFeriaWeb[] | null {
  const guardadas = cacheInvitaciones.get(uid)
  return guardadas && guardadas.vence > Date.now() ? guardadas.valor : null
}

export function actualizarInvitacionEnCache(uid: string, feriaId: string, estado: EstadoInvitacionFeria) {
  const guardadas = cacheInvitaciones.get(uid)
  if (!guardadas) return
  guardadas.valor = guardadas.valor.map((invitacion) => invitacion.id === feriaId ? { ...invitacion, estado } : invitacion)
}

export function olvidarInvitaciones(uid?: string) {
  if (uid) cacheInvitaciones.delete(uid)
  else cacheInvitaciones.clear()
}

export function obtenerInvitacionesFeria(uid: string, llamar: LlamarApi, forzar = false): Promise<InvitacionFeriaWeb[]> {
  const enCache = forzar ? null : invitacionesEnCache(uid)
  if (enCache) return Promise.resolve(enCache)
  const pendiente = lecturasPendientes.get(uid)
  if (pendiente) return pendiente
  const lectura = llamar('listarInvitacionesFeria', {})
    .then((datos) => {
      const invitaciones = mapearInvitacionesFeria(datos)
      cacheInvitaciones.set(uid, { valor: invitaciones, vence: Date.now() + DURACION_CACHE })
      return invitaciones
    })
    .finally(() => {
      if (lecturasPendientes.get(uid) === lectura) lecturasPendientes.delete(uid)
    })
  lecturasPendientes.set(uid, lectura)
  return lectura
}

const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sept', 'oct', 'nov', 'dic']
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

function participa(invitacion: InvitacionFeriaWeb) {
  return invitacion.estado === 'aceptada' || invitacion.estado === 'agregada_admin'
}

function terminada(invitacion: InvitacionFeriaWeb, ahora: Date) {
  const fin = invitacion.fechaFin ?? invitacion.fechaInicio
  return Boolean(fin && fin.getTime() <= ahora.getTime())
}

function porFecha(a: InvitacionFeriaWeb, b: InvitacionFeriaWeb) {
  return (a.fechaInicio?.getTime() ?? Number.MAX_SAFE_INTEGER) - (b.fechaInicio?.getTime() ?? Number.MAX_SAFE_INTEGER)
}

export type MesAgenda = { clave: string; titulo: string; ferias: InvitacionFeriaWeb[] }

export type SeccionesFerias = {
  proxima: InvitacionFeriaWeb | null
  pendientes: InvitacionFeriaWeb[]
  agenda: MesAgenda[]
  pasadas: InvitacionFeriaWeb[]
}

export function seccionesFerias(invitaciones: InvitacionFeriaWeb[], ahora = new Date()): SeccionesFerias {
  const vigentes = invitaciones.filter((invitacion) => invitacion.estado !== 'rechazada' && !terminada(invitacion, ahora))
  const pendientes = vigentes.filter((invitacion) => invitacion.estado === 'pendiente').sort(porFecha)
  const confirmadas = vigentes.filter(participa).sort(porFecha)
  const meses = new Map<string, MesAgenda>()
  for (const feria of confirmadas) {
    const clave = feria.fechaInicio ? claveDia(feria.fechaInicio).slice(0, 7) : 'sin-fecha'
    if (!meses.has(clave)) {
      const [anio, mes] = clave.split('-').map(Number)
      const titulo = clave === 'sin-fecha'
        ? 'Fecha por confirmar'
        : `${MESES[mes! - 1]!.charAt(0).toLocaleUpperCase('es-CL')}${MESES[mes! - 1]!.slice(1)}${anio !== Number(claveDia(ahora).slice(0, 4)) ? ` ${anio}` : ''}`
      meses.set(clave, { clave, titulo, ferias: [] })
    }
    meses.get(clave)!.ferias.push(feria)
  }
  const pasadas = invitaciones
    .filter((invitacion) => invitacion.estado === 'rechazada' || terminada(invitacion, ahora))
    .sort((a, b) => porFecha(b, a))
  return { proxima: confirmadas[0] ?? null, pendientes, agenda: [...meses.values()], pasadas }
}

function diasCalendario(desde: Date, hasta: Date) {
  const [a1, m1, d1] = claveDia(desde).split('-').map(Number)
  const [a2, m2, d2] = claveDia(hasta).split('-').map(Number)
  return Math.round((Date.UTC(a2!, m2! - 1, d2!) - Date.UTC(a1!, m1! - 1, d1!)) / 86_400_000)
}

export function cuantoFalta(invitacion: InvitacionFeriaWeb, ahora = new Date()): string {
  if (!invitacion.fechaInicio) return 'Fecha por confirmar'
  if (invitacion.fechaInicio.getTime() <= ahora.getTime() && !terminada(invitacion, ahora)) return 'Ahora mismo'
  const dias = diasCalendario(ahora, invitacion.fechaInicio)
  if (dias <= 0) return 'Hoy'
  if (dias === 1) return 'Mañana'
  if (dias < 7) return `En ${dias} días`
  if (dias < 14) return 'En una semana'
  return `En ${Math.round(dias / 7)} semanas`
}

export function haceCuanto(fecha: Date | null, ahora = new Date()): string {
  if (!fecha) return ''
  const dias = diasCalendario(fecha, ahora)
  if (dias <= 0) return 'Te invitaron hoy'
  if (dias === 1) return 'Te invitaron ayer'
  return `Te invitaron hace ${dias} días`
}

export function bloqueFecha(fecha: Date | null): { dia: string; mes: string } {
  if (!fecha) return { dia: '?', mes: 'por conf.' }
  const [, mes, dia] = claveDia(fecha).split('-').map(Number)
  return { dia: String(dia), mes: MESES_CORTOS[mes! - 1]! }
}

export function enlaceComoLlegar(invitacion: Pick<InvitacionFeriaWeb, 'latitud' | 'longitud' | 'donde'>): string {
  const url = new URL('https://www.google.com/maps/dir/')
  url.searchParams.set('api', '1')
  const destino = invitacion.latitud !== null && invitacion.longitud !== null
    ? `${invitacion.latitud},${invitacion.longitud}`
    : invitacion.donde
  url.searchParams.set('destination', destino)
  return url.toString()
}
