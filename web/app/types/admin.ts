// Tipos del panel de administración. Alineados con contratos/ y firebase/firestore.rules.
// No sustituyen la validación de reglas ni del servidor.

export type EstadoAcceso = 'activo' | 'desactivado'

export interface Acceso {
  uid: string
  roles: string[]
  estado: EstadoAcceso
  tiendaId?: string
  operacionCuentaPendiente?: string
}

export interface Emprendedora {
  uid: string
  tiendaId: string
  creadaPor?: string
}

export interface EmprendedoraAdmin {
  uid: string
  correo: string
  nombreMostrar: string
  nombreTienda: string
  tiendaId: string
  estado: string
  consistente: boolean
  versionAcceso?: string
  versionPerfil?: string
}

export interface InvitacionAlta {
  nombreMostrar: string
  nombreTienda: string
  descripcion?: string
  tiendaId: string
  sectorId: string
  tipoEmprendimientoId: string
}

export interface Invitacion {
  id: string
  correo: string
  estado: string
  alta: InvitacionAlta
  venceEn: Date | null
}

export interface InvitacionEmprendedoraAdmin {
  invitacionId: string
  correo: string
  nombreTienda: string
  estado: string
}

export interface ElementoCatalogo {
  id: string
  nombre: string
  activo: boolean
  versionEsperada?: string
  versionActual?: number
  campos?: CampoTipoProducto[]
  opcionesVariante?: string[]
}

export interface CampoTipoProducto {
  clave: string
  etiqueta: string
  tipo: 'texto'
  obligatorio: boolean
}

export interface Catalogos {
  sectores: ElementoCatalogo[]
  categorias: ElementoCatalogo[]
  tiposEmprendimiento: ElementoCatalogo[]
  tiposProducto: ElementoCatalogo[]
}

export interface ContactoAsociacion {
  tipo: 'whatsapp' | 'correo' | 'web'
  valor: string
}

export interface Asociacion {
  id: string
  nombre: string
  descripcion: string
  sectorId: string
  contacto: ContactoAsociacion
  logoUrl?: string
  activo: boolean
  versionEsperada?: string
}

export interface IntegranteAsociacion {
  uid: string
  tiendaId: string
  nombreMostrar: string
  nombreTienda: string
  estadoRelacion: 'activa' | 'retirada'
  estadoCuenta: string
  versionEsperada?: string
}

export interface EmprendedoraDisponible {
  uid: string
  tiendaId: string
  nombreMostrar: string
  nombreTienda: string
  versionRelacionEsperada?: string
}

export interface IntegrantesAsociacion {
  asociacion: { id: string; activo: boolean; versionEsperada?: string }
  miembros: IntegranteAsociacion[]
  disponibles: EmprendedoraDisponible[]
}

export interface DatosAdmin {
  accesos: Acceso[]
  emprendedoras: Emprendedora[]
  invitaciones: Invitacion[]
  catalogos: Catalogos
  asociaciones: Asociacion[]
}

export interface AdministradorPlataforma {
  uid: string
  correo: string
  nombreMostrar: string
  estado: 'activo' | 'desactivado'
  roles: string[]
  consistente: boolean
  versionEsperada: string
}

export type ModoNavegacionComprador = 'permitir' | 'invitar' | 'exigir'
export interface ConfiguracionNavegacion {
  modo: ModoNavegacionComprador
  versionEsperada: string
  actualizadoEn: string | null
}

export type EstadoFeria = 'borrador' | 'publicada' | 'cancelada'
export type TipoParticipanteFeria = 'emprendedora' | 'asociacion'
export type EstadoParticipanteFeria = 'invitada' | 'confirmada' | 'rechazada' | 'agregada_admin' | 'retirada'
export type AsistenciaFeria = 'pendiente' | 'asistio' | 'no_asistio'

export interface FeriaAdmin {
  id: string
  nombre: string
  descripcion: string
  organizador: string
  direccion: string
  fechaInicio: string | null
  fechaFin: string | null
  imagenUrl: string
  latitud: number | null
  longitud: number | null
  estado: EstadoFeria
  versionEsperada: string
}

export interface ParticipanteFeria {
  id: string
  tipo: TipoParticipanteFeria
  entidadId: string
  nombre: string
  nombreTienda: string
  estado: EstadoParticipanteFeria
  asistencia: AsistenciaFeria
  versionEsperada: string
}

export interface DisponibleFeria {
  tipo: TipoParticipanteFeria
  entidadId: string
  nombre: string
  nombreTienda?: string
}

export interface ParticipantesFeria {
  feria: { id: string; estado: EstadoFeria; versionEsperada: string }
  participantes: ParticipanteFeria[]
  disponibles: DisponibleFeria[]
}
