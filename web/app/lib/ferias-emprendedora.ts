export type EstadoInvitacionFeria = 'pendiente' | 'aceptada' | 'rechazada' | 'agregada_admin'

export type InvitacionFeriaWeb = {
  id: string
  nombre: string
  donde: string
  organiza: string
  fechaInicio: Date | null
  fechaFin: Date | null
  estado: EstadoInvitacionFeria
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
