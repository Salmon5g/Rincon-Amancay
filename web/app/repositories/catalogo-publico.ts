import type { AsociacionPublica, FeriaPublica, ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico'

export interface PaginaRepositorio<T> {
  elementos: T[]
  cursorSiguiente: string | null
}

export interface ElementoEtiqueta {
  id: string
  nombre: string
  tipoProductoId?: string
}

export interface CatalogosPublicos {
  sectores: ElementoEtiqueta[]
  tiposEmprendimiento: ElementoEtiqueta[]
  categorias: ElementoEtiqueta[]
}

const TIPOS_PRODUCTO_CON_CATEGORIAS_VIGENTES = new Set([
  'alimento_reposteria', 'artesania_manualidad', 'plantas', 'producto_agricola',
  'vestuario', 'accesorio', 'cosmetica_natural',
])

export function categoriasProductoVigentes(categorias: ElementoEtiqueta[]): ElementoEtiqueta[] {
  return categorias
    .filter((categoria) => TIPOS_PRODUCTO_CON_CATEGORIAS_VIGENTES.has(categoria.tipoProductoId ?? ''))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-CL'))
}

export type PoliticaNavegacion = 'permitir' | 'invitar' | 'exigir'

export interface CatalogoPublicoRepository {
  listarTiendas(cursor?: string | null): Promise<PaginaRepositorio<TiendaPublica>>
  listarProductos(tiendaId: string, cursor?: string | null, tamanoPagina?: number): Promise<PaginaRepositorio<ProductoPublico>>
  leerTienda(tiendaId: string): Promise<TiendaPublica>
  observarTienda(tiendaId: string, alCambiar: (tienda: TiendaPublica) => void): () => void
  leerProducto(tiendaId: string, productoId: string): Promise<ProductoPublico>
  observarProducto(tiendaId: string, productoId: string, alCambiar: (producto: ProductoPublico | null) => void, alFallar?: (error: Error) => void): () => void
  listarVariantes(tiendaId: string, productoId: string): Promise<VariantePublica[]>
  listarCatalogosPublicos(): Promise<CatalogosPublicos>
  listarAsociacionesPublicas(): Promise<AsociacionPublica[]>
  listarFeriasPublicas(): Promise<FeriaPublica[]>
  leerFeria(feriaId: string): Promise<FeriaPublica>
  leerEtiquetasTipoProducto(tipoProductoId: string, version: number): Promise<Record<string, string>>
  leerPoliticaNavegacion(): Promise<PoliticaNavegacion>
  resolverUrlImagen(ruta: string): string | null
  leerImagen(ruta: string): Promise<Blob>
}
