export type ProductoOrganizable = {
  id: string
  nombre: string
  descripcion: string
  categoriaId: string
  disponible: boolean
  versionEsperada: string
  estado: string
}

export type LoteDisponibilidad = {
  operacionId: string
  items: { productoId: string; versionEsperada: string }[]
}

export const TAMANO_LOTE_DISPONIBILIDAD = 20

export function crearLotesDisponibilidad(productos: ProductoOrganizable[], disponible: boolean, crearId: () => string): LoteDisponibilidad[] {
  const pendientes = [...new Map(productos
    .filter((producto) => producto.disponible !== disponible && producto.versionEsperada)
    .map((producto) => [producto.id, producto])).values()]
  const lotes: LoteDisponibilidad[] = []
  for (let inicio = 0; inicio < pendientes.length; inicio += TAMANO_LOTE_DISPONIBILIDAD) {
    lotes.push({
      operacionId: crearId(),
      items: pendientes.slice(inicio, inicio + TAMANO_LOTE_DISPONIBILIDAD).map((producto) => ({ productoId: producto.id, versionEsperada: producto.versionEsperada })),
    })
  }
  return lotes
}

export type FiltroOrganizar = {
  texto: string
  categoriaId: string
  disponibilidad: 'todas' | 'disponibles' | 'no_disponibles'
}

export function filtrarOrganizables<T extends ProductoOrganizable>(productos: T[], filtro: FiltroOrganizar): T[] {
  const texto = filtro.texto.trim().toLocaleLowerCase('es-CL')
  return productos.filter((producto) =>
    producto.estado !== 'eliminado'
    && (!filtro.categoriaId || producto.categoriaId === filtro.categoriaId)
    && (filtro.disponibilidad === 'todas' || (filtro.disponibilidad === 'disponibles') === producto.disponible)
    && (!texto || `${producto.nombre} ${producto.descripcion}`.toLocaleLowerCase('es-CL').includes(texto)))
}

export function alternarSeleccion(seleccion: Set<string>, ids: string[], marcar: boolean): Set<string> {
  const nueva = new Set(seleccion)
  for (const id of ids) {
    if (marcar) nueva.add(id)
    else nueva.delete(id)
  }
  return nueva
}
