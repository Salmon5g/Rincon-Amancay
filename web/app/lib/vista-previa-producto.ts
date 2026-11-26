export type VariantePrevia = {
  id: string
  opciones: Record<string, string>
  precio: number
  stock: number | null
  activa: boolean
}

export type ProductoPrevio = {
  estado: 'publicado' | 'borrador' | 'archivado'
  tieneVariantes: boolean
  precioBase: number | null
  stock: number | null
  manejaStock: boolean
  modalidad: string
  disponible: boolean
  piezaUnicaVendida: boolean
  variantes: VariantePrevia[]
}

export type MotivoSinCompartir = 'tienda' | 'producto' | 'disponible' | null

export function urlPublicaProducto(origen: string, tiendaId: string, productoId: string): string {
  const url = new URL('/comprador', origen)
  url.searchParams.set('tienda', tiendaId)
  url.searchParams.set('producto', productoId)
  return url.toString()
}

export function motivoSinCompartir(producto: Pick<ProductoPrevio, 'estado' | 'disponible'>, tiendaPublicada: boolean): MotivoSinCompartir {
  if (!tiendaPublicada) return 'tienda'
  if (producto.estado !== 'publicado') return 'producto'
  if (!producto.disponible) return 'disponible'
  return null
}

function pesos(valor: number) {
  return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(valor)
}

export function precioPrevio(producto: ProductoPrevio, variante?: VariantePrevia | null): string {
  if (variante) return pesos(variante.precio)
  if (!producto.tieneVariantes) return producto.precioBase === null ? 'Precio por definir' : pesos(producto.precioBase)
  const precios = producto.variantes.filter((item) => item.activa).map((item) => item.precio)
  if (!precios.length) return 'Sin variantes activas'
  const minimo = Math.min(...precios)
  return minimo === Math.max(...precios) ? pesos(minimo) : `Desde ${pesos(minimo)}`
}

export function disponibilidadPrevia(producto: ProductoPrevio, variante?: VariantePrevia | null): { texto: string; agotado: boolean } {
  if (!producto.disponible) return { texto: 'No disponible por ahora', agotado: true }
  if (producto.piezaUnicaVendida) return { texto: 'Pieza vendida', agotado: true }
  if (producto.modalidad === 'a_pedido' || producto.modalidad === 'por_encargo') return { texto: 'Disponible para encargo', agotado: false }
  if (!producto.manejaStock) return { texto: 'Disponible', agotado: false }
  const unidades = variante
    ? variante.stock ?? 0
    : producto.tieneVariantes
      ? producto.variantes.filter((item) => item.activa).reduce((suma, item) => suma + (item.stock ?? 0), 0)
      : producto.stock ?? 0
  if (unidades <= 0) return { texto: 'Agotado', agotado: true }
  return { texto: `Disponible · ${unidades} ${unidades === 1 ? 'unidad' : 'unidades'}`, agotado: false }
}

export function nombreVariantePrevia(variante: VariantePrevia): string {
  return Object.values(variante.opciones).filter((valor) => valor.trim()).join(' · ') || 'Variante'
}

export function nombreArchivoQr(nombre: string): string {
  const base = nombre
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60)
  return `qr-${base || 'producto'}.png`
}
