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

export interface DatosAdmin {
  accesos: Acceso[]
  emprendedoras: Emprendedora[]
  invitaciones: Invitacion[]
  catalogos: Catalogos
}
