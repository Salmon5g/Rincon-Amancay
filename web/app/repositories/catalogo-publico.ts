import type { AsociacionPublica, FeriaPublica, ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico'

export interface PaginaRepositorio<T> {
  elementos: T[]
  cursorSiguiente: string | null
}

export interface ElementoEtiqueta {
  id: string
  nombre: string
}

export interface CatalogosPublicos {
  sectores: ElementoEtiqueta[]
  tiposEmprendimiento: ElementoEtiqueta[]
  categorias: ElementoEtiqueta[]
}

export interface CatalogoPublicoRepository {
  listarTiendas(cursor?: string | null): Promise<PaginaRepositorio<TiendaPublica>>
  listarProductos(tiendaId: string, cursor?: string | null): Promise<PaginaRepositorio<ProductoPublico>>
  leerTienda(tiendaId: string): Promise<TiendaPublica>
  leerProducto(tiendaId: string, productoId: string): Promise<ProductoPublico>
  observarProducto(tiendaId: string, productoId: string, alCambiar: (producto: ProductoPublico | null) => void, alFallar?: (error: Error) => void): () => void
  listarVariantes(tiendaId: string, productoId: string): Promise<VariantePublica[]>
  listarCatalogosPublicos(): Promise<CatalogosPublicos>
  listarAsociacionesPublicas(): Promise<AsociacionPublica[]>
  listarFeriasPublicas(): Promise<FeriaPublica[]>
  leerFeria(feriaId: string): Promise<FeriaPublica>
  resolverUrlImagen(ruta: string): string | null
  leerImagen(ruta: string): Promise<Blob>
}
