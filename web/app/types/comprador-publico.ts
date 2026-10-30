// Tipos públicos del comprador web — espejo de android/.../data/Modelos.kt
// y de contratos/02-catalogo-local.md. Solo lectura: tiendasPublicas,
// productos publicados y variantes activas. Sin stock exacto ni propietarioUid.

export interface TiendaPublica {
  id: string;
  nombre: string;
  descripcion: string;
  tipoEmprendimientoId: string;
  sectorId: string;
  mostrarPrecios: boolean;
}

export interface ProductoPublico {
  id: string;
  nombre: string;
  descripcion: string;
  categoriaId: string;
  tipoProductoId: string;
  tieneVariantes: boolean;
  moneda: string;
  unidadVenta: string;
  modalidad: string;
  estadoDisponibilidad: 'disponible' | 'agotado' | 'a_pedido' | string;
  precioBase: number | null;
  atributosEspecificos: Record<string, string>;
  imagenes: string[];
}

export interface VariantePublica {
  id: string;
  opciones: Record<string, string>;
  precio: number | null;
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
    mostrarPrecios?: boolean;
    imagenes?: string[];
    tieneVariantes?: boolean;
    estadoDisponibilidad?: string;
    precioBase?: number | null;
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
