import { Timestamp, collection, getDocsFromServer, limit, orderBy, query, startAfter, where } from 'firebase/firestore'
import type { DocumentData, QueryDocumentSnapshot } from 'firebase/firestore'
import type { ServiciosCliente } from '~/lib/cliente-base'

export type VentaRegistradaWeb = {
  id: string
  productoId: string
  productoNombre: string
  varianteNombre: string
  precioUnitario: number
  cantidad: number
  total: number
  fecha: Date
  lugarTipo: 'feria' | 'otro'
  feriaId: string
  lugarNombre: string
}

export type PaginaVentasEmprendedora = {
  ventas: VentaRegistradaWeb[]
  cursor: QueryDocumentSnapshot<DocumentData> | null
  hayMas: boolean
}

export const TAMANO_PAGINA_VENTAS = 30

function texto(valor: unknown): string {
  return typeof valor === 'string' ? valor.trim() : ''
}

function entero(valor: unknown): number {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : 0
}

export function mapearVentaEmprendedora(id: string, datos: DocumentData): VentaRegistradaWeb | null {
  const fecha = datos.fecha && typeof datos.fecha.toDate === 'function' ? datos.fecha.toDate() as Date : null
  if (!fecha) return null
  const opciones = datos.opcionesVariante && typeof datos.opcionesVariante === 'object'
    ? Object.values(datos.opcionesVariante as Record<string, unknown>).filter((valor): valor is string => typeof valor === 'string' && valor.trim() !== '')
    : []
  const lugarTipo = datos.lugarTipo === 'feria' ? 'feria' : 'otro'
  const cantidad = entero(datos.cantidad)
  const precioUnitario = entero(datos.precioUnitario)
  return {
    id,
    productoId: texto(datos.productoId),
    productoNombre: texto(datos.productoNombre) || 'Producto',
    varianteNombre: opciones.join(' · '),
    precioUnitario,
    cantidad,
    total: entero(datos.total) || precioUnitario * cantidad,
    fecha,
    lugarTipo,
    feriaId: texto(datos.feriaId),
    lugarNombre: lugarTipo === 'feria'
      ? texto(datos.feriaNombre) || texto(datos.lugarTexto) || 'Feria'
      : texto(datos.lugarTexto) || 'Otro lugar',
  }
}

export async function listarPaginaVentasEmprendedora(
  firebase: ServiciosCliente,
  tiendaId: string,
  desde: Date,
  hasta: Date,
  cursor: QueryDocumentSnapshot<DocumentData> | null = null,
): Promise<PaginaVentasEmprendedora> {
  const filtros = [
    where('fecha', '>=', Timestamp.fromDate(desde)),
    where('fecha', '<', Timestamp.fromDate(hasta)),
    orderBy('fecha', 'desc'),
    ...(cursor ? [startAfter(cursor)] : []),
    limit(TAMANO_PAGINA_VENTAS),
  ]
  const resultado = await getDocsFromServer(query(collection(firebase.db, 'tiendasPrivadas', tiendaId, 'ventas'), ...filtros))
  return {
    ventas: resultado.docs.flatMap((documento) => {
      const venta = mapearVentaEmprendedora(documento.id, documento.data())
      return venta ? [venta] : []
    }),
    cursor: resultado.docs.at(-1) ?? null,
    hayMas: resultado.docs.length === TAMANO_PAGINA_VENTAS,
  }
}
