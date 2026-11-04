import type { ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico'

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
  listarVariantes(tiendaId: string, productoId: string): Promise<VariantePublica[]>
  listarCatalogosPublicos(): Promise<CatalogosPublicos>
  resolverUrlImagen(ruta: string): string | null
  leerImagen(ruta: string): Promise<Blob>
}
