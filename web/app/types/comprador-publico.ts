// Tipos públicos del comprador web — espejo de android/.../data/Modelos.kt
// y de contratos/02-catalogo-local.md. Solo lectura: tiendasPublicas,
// productos publicados y variantes activas. El stock publicado es intencionalmente
// visible al comprador; nunca se exponen propietarioUid ni políticas privadas.

export interface TiendaPublica {
  id: string;
  nombre: string;
  descripcion: string;
  tipoEmprendimientoId: string;
  sectorId: string;
  direccion?: string;
  mostrarPrecios: boolean;
  /** Rutas WebP de Supabase; el cliente local aún requiere adaptador lector. */
  logo?: string;
  logoMiniatura?: string;
  contacto?: ContactoPublico;
  canalesContacto?: CanalesContactoPublico;
  /** Punto de venta o retiro marcado por la emprendedora (Entrega 6, A18). */
  puntoRetiro?: PuntoRetiroPublico;
}

export interface PuntoRetiroPublico {
  latitud: number;
  longitud: number;
  descripcion?: string;
}

export interface IntegranteAsociacionPublica {
  tiendaId: string;
  nombre: string;
  sectorId: string;
  tipoEmprendimientoId: string;
  logoMiniatura?: string;
}

export interface AsociacionPublica {
  id: string;
  nombre: string;
  descripcion: string;
  sectorId: string;
  integrantes: IntegranteAsociacionPublica[];
}

export interface MuestraCatalogoFeria {
  nombreTienda: string;
  productos: string[];
}

export interface ParticipantePublicoFeria {
  tipo: 'emprendedora' | 'asociacion' | string;
  nombre: string;
  /** Tiendas públicas usadas para preparar la muestra del catálogo. */
  tiendaIds?: string[];
  /** Solo asociaciones: ficha pública para visitar su directorio. */
  asociacionId?: string;
  catalogo: MuestraCatalogoFeria[];
}

export interface FeriaPublica {
  id: string;
  nombre: string;
  descripcion: string;
  organizador: string;
  direccion: string;
  fechaInicio: Date | null;
  fechaFin: Date | null;
  imagenUrl: string;
  latitud: number | null;
  longitud: number | null;
  /** Proyección pública mantenida por la API; undefined si aún no existe. */
  participantes?: ParticipantePublicoFeria[];
}

export interface ContactoPublico {
  tipo: 'whatsapp' | 'correo' | 'web';
  valor: string;
}

export interface CanalesContactoPublico {
  whatsapp?: {
    habilitado: boolean;
    numero: string;
  };
  formulario?: {
    formularioId: string;
    habilitado: boolean;
  };
  correo?: {
    habilitado: boolean;
    direccion: string;
  };
}

export interface ProductoPublico {
  id: string;
  nombre: string;
  descripcion: string;
  categoriaId: string;
  tipoProductoId: string;
  versionTipoProducto?: number;
  tieneVariantes: boolean;
  manejaStock?: boolean;
  moneda: string;
  unidadVenta: string;
  modalidad: string;
  estadoDisponibilidad: 'disponible' | 'agotado' | 'a_pedido' | string;
  precioBase: number | null;
  stock?: number | null;
  atributosEspecificos: Record<string, string>;
  imagenes: string[];
}

export interface VariantePublica {
  id: string;
  opciones: Record<string, string>;
  imagen?: string;
  precio: number | null;
  stock?: number | null;
  estadoDisponibilidad: string;
  activa: boolean;
}

export interface FichaComprador {
  id: string;
  datos: {
    nombre: string;
    descripcion: string;
    tipoEmprendimientoId?: string;
    sectorId?: string;
    direccion?: string;
    puntoRetiro?: PuntoRetiroPublico;
    categoriaId?: string;
    tipoProductoId?: string;
    versionTipoProducto?: number;
    mostrarPrecios?: boolean;
    contacto?: ContactoPublico;
    canalesContacto?: CanalesContactoPublico;
    logo?: string;
    imagen?: string;
    imagenes?: string[];
    tieneVariantes?: boolean;
    manejaStock?: boolean;
    unidadVenta?: string;
    modalidad?: string;
    estadoDisponibilidad?: string;
    precioBase?: number | null;
    stock?: number | null;
    precio?: number | null;
    atributosEspecificos?: Record<string, string>;
    opciones?: Record<string, string>;
  };
}

export function etiquetaDisponibilidad(valor: string): string {
  switch (valor) {
    case 'disponible': return 'Disponible';
    case 'a_pedido': return 'A pedido';
    case 'agotado': return 'Agotado';
    case 'no_disponible': return 'No disponible';
    default: return valor;
  }
}

export function nombreVariante(opciones: Record<string, string>): string {
  const primero = Object.values(opciones)[0];
  return primero && primero.trim() !== '' ? primero : 'Única';
}
