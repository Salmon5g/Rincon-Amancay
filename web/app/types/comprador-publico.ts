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
  mostrarPrecios: boolean;
  /** Rutas WebP de Supabase; el cliente local aún requiere adaptador lector. */
  logo?: string;
  logoMiniatura?: string;
  contacto?: ContactoPublico;
}

export interface ContactoPublico {
  tipo: 'whatsapp' | 'correo' | 'web';
  valor: string;
}

export interface ProductoPublico {
  id: string;
  nombre: string;
  descripcion: string;
  categoriaId: string;
  tipoProductoId: string;
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
    categoriaId?: string;
    mostrarPrecios?: boolean;
    contacto?: ContactoPublico;
    logo?: string;
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
