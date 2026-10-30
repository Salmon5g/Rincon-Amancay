import type { ProductoPublico, TiendaPublica, VariantePublica } from '~/types/comprador-publico'

export interface PaginaRepositorio<T> {
  elementos: T[]
  cursorSiguiente: string | null
}

export interface CatalogoPublicoRepository {
  listarTiendas(cursor?: string | null): Promise<PaginaRepositorio<TiendaPublica>>
  listarProductos(tiendaId: string, cursor?: string | null): Promise<PaginaRepositorio<ProductoPublico>>
  listarVariantes(tiendaId: string, productoId: string): Promise<VariantePublica[]>
  leerImagen(ruta: string): Promise<Blob>
}
