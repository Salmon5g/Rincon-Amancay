import type { VentaRegistradaWeb } from '~/repositories/firebase/ventas-emprendedora'

const ZONA = 'America/Santiago'

export function fechaSantiago(fecha: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' }).format(fecha)
}

function desfaseSantiago(instante: number): number {
  const partes = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).formatToParts(new Date(instante)).map((parte) => [parte.type, parte.value]))
  const local = Date.UTC(Number(partes.year), Number(partes.month) - 1, Number(partes.day), Number(partes.hour) % 24, Number(partes.minute), Number(partes.second))
  return local - instante
}

export function inicioDiaSantiago(dia: string): Date {
  const [anio, mes, numero] = dia.split('-').map(Number)
  if (!anio || !mes || !numero) throw new Error('Fecha inválida.')
  const base = Date.UTC(anio, mes - 1, numero)
  const primera = base - desfaseSantiago(base)
  return new Date(base - desfaseSantiago(primera))
}

export function rangoHistorial(desde: string, hasta: string): { desde: Date; hasta: Date } {
  const inicio = inicioDiaSantiago(desde)
  const fin = inicioDiaSantiago(hasta)
  if (fin.getTime() < inicio.getTime()) throw new Error('La fecha final debe ser igual o posterior a la inicial.')
  const [anio, mes, dia] = hasta.split('-').map(Number)
  const siguiente = new Date(Date.UTC(anio!, mes! - 1, dia! + 1))
  return { desde: inicio, hasta: inicioDiaSantiago(siguiente.toISOString().slice(0, 10)) }
}

export function restarDias(dia: string, dias: number): string {
  const [anio, mes, numero] = dia.split('-').map(Number)
  return new Date(Date.UTC(anio!, mes! - 1, numero! - dias)).toISOString().slice(0, 10)
}

export type FiltrosHistorial = { productoId: string; lugar: string }

export function claveLugar(venta: VentaRegistradaWeb): string {
  return venta.lugarTipo === 'feria' && venta.feriaId ? `feria:${venta.feriaId}` : `otro:${venta.lugarNombre.toLocaleLowerCase('es-CL')}`
}

export function filtrarVentas(ventas: VentaRegistradaWeb[], filtros: FiltrosHistorial): VentaRegistradaWeb[] {
  return ventas.filter((venta) =>
    (!filtros.productoId || venta.productoId === filtros.productoId)
    && (!filtros.lugar || claveLugar(venta) === filtros.lugar))
}

export function resumirVentas(ventas: VentaRegistradaWeb[]): { unidades: number; total: number; ventas: number } {
  return ventas.reduce((acumulado, venta) => ({
    unidades: acumulado.unidades + venta.cantidad,
    total: acumulado.total + venta.total,
    ventas: acumulado.ventas + 1,
  }), { unidades: 0, total: 0, ventas: 0 })
}

export function opcionesFiltro(ventas: VentaRegistradaWeb[]) {
  const productos = new Map<string, string>()
  const lugares = new Map<string, string>()
  for (const venta of ventas) {
    if (venta.productoId) productos.set(venta.productoId, venta.productoNombre)
    lugares.set(claveLugar(venta), venta.lugarTipo === 'feria' ? `Feria: ${venta.lugarNombre}` : venta.lugarNombre)
  }
  const ordenar = (mapa: Map<string, string>) => [...mapa].map(([id, nombre]) => ({ id, nombre })).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es-CL'))
  return { productos: ordenar(productos), lugares: ordenar(lugares) }
}
