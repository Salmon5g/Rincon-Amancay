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

export type FiltrosHistorial = { productoId?: string; lugar: string; texto?: string; dia?: string }

export function claveLugar(venta: VentaRegistradaWeb): string {
  return venta.lugarTipo === 'feria' && venta.feriaId ? `feria:${venta.feriaId}` : `otro:${venta.lugarNombre.toLocaleLowerCase('es-CL')}`
}

export function filtrarVentas(ventas: VentaRegistradaWeb[], filtros: FiltrosHistorial): VentaRegistradaWeb[] {
  return ventas.filter((venta) =>
    (!filtros.productoId || venta.productoId === filtros.productoId)
    && (!filtros.lugar || claveLugar(venta) === filtros.lugar)
    && (!filtros.dia || fechaSantiago(venta.fecha) === filtros.dia)
    && (!filtros.texto?.trim() || normalizar(`${venta.productoNombre} ${venta.varianteNombre}`).includes(normalizar(filtros.texto))))
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

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-CL').trim()
}

function partesDia(dia: string): [number, number, number] {
  const [anio, mes, numero] = dia.split('-').map(Number)
  return [anio!, mes!, numero!]
}

function sumarDias(dia: string, dias: number): string {
  return restarDias(dia, -dias)
}

function diasEntre(desde: string, hasta: string): number {
  const [a1, m1, d1] = partesDia(desde)
  const [a2, m2, d2] = partesDia(hasta)
  return Math.round((Date.UTC(a2, m2 - 1, d2) - Date.UTC(a1, m1 - 1, d1)) / 86_400_000) + 1
}

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']

export type TipoPeriodo = 'hoy' | 'semana' | 'mes' | 'personalizado'

export type PeriodoHistorial = {
  desde: string
  hasta: string
  anterior: { desde: string; hasta: string }
  comparacion: string
  titulo: string
}

export function periodoHistorial(tipo: TipoPeriodo, hoy: string, desde = hoy, hasta = hoy): PeriodoHistorial {
  const [anio, mes, numero] = partesDia(hoy)
  if (tipo === 'hoy') return { desde: hoy, hasta: hoy, anterior: { desde: restarDias(hoy, 1), hasta: restarDias(hoy, 1) }, comparacion: 'que ayer', titulo: 'hoy' }
  if (tipo === 'semana') {
    const diaSemana = new Date(Date.UTC(anio, mes - 1, numero)).getUTCDay()
    const inicio = restarDias(hoy, (diaSemana + 6) % 7)
    return { desde: inicio, hasta: hoy, anterior: { desde: restarDias(inicio, 7), hasta: restarDias(hoy, 7) }, comparacion: 'que la semana pasada', titulo: 'esta semana' }
  }
  if (tipo === 'mes') {
    const inicio = `${anio}-${String(mes).padStart(2, '0')}-01`
    const inicioAnterior = new Date(Date.UTC(anio, mes - 2, 1))
    const ultimoAnterior = new Date(Date.UTC(anio, mes - 1, 0)).getUTCDate()
    const finAnterior = new Date(Date.UTC(anio, mes - 2, Math.min(numero, ultimoAnterior)))
    return {
      desde: inicio,
      hasta: hoy,
      anterior: { desde: inicioAnterior.toISOString().slice(0, 10), hasta: finAnterior.toISOString().slice(0, 10) },
      comparacion: `que ${MESES[inicioAnterior.getUTCMonth()]} a la misma fecha`,
      titulo: MESES[mes - 1]!,
    }
  }
  const dias = diasEntre(desde, hasta)
  return {
    desde,
    hasta,
    anterior: { desde: restarDias(desde, dias), hasta: restarDias(desde, 1) },
    comparacion: dias === 1 ? 'que el día anterior' : `que los ${dias} días anteriores`,
    titulo: dias === 1 ? fechaCorta(desde) : `${fechaCorta(desde)} – ${fechaCorta(hasta)}`,
  }
}

export function fechaCorta(dia: string): string {
  const [, mes, numero] = partesDia(dia)
  return `${numero} ${MESES[mes - 1]!.slice(0, 3)}`
}

export function etiquetaDia(dia: string, hoy: string): string {
  const [anio, mes, numero] = partesDia(dia)
  const nombre = DIAS[new Date(Date.UTC(anio, mes - 1, numero)).getUTCDay()]!
  if (dia === hoy) return `Hoy · ${nombre} ${numero}`
  if (dia === restarDias(hoy, 1)) return `Ayer · ${nombre} ${numero}`
  const texto = `${nombre} ${numero} de ${MESES[mes - 1]}${anio !== partesDia(hoy)[0] ? ` de ${anio}` : ''}`
  return texto.charAt(0).toLocaleUpperCase('es-CL') + texto.slice(1)
}

export type GrupoDia = { dia: string; ventas: VentaRegistradaWeb[]; total: number }

export function agruparPorDia(ventas: VentaRegistradaWeb[]): GrupoDia[] {
  const grupos = new Map<string, GrupoDia>()
  for (const venta of [...ventas].sort((a, b) => b.fecha.getTime() - a.fecha.getTime())) {
    const dia = fechaSantiago(venta.fecha)
    const grupo = grupos.get(dia) ?? { dia, ventas: [], total: 0 }
    grupo.ventas.push(venta)
    grupo.total += venta.total
    grupos.set(dia, grupo)
  }
  return [...grupos.values()]
}

export type BarraDia = { dia: string; total: number; conFeria: boolean; proporcion: number }

export const MAXIMO_DIAS_GRAFICO = 62

export function barrasPorDia(ventas: VentaRegistradaWeb[], desde: string, hasta: string): BarraDia[] {
  const dias = diasEntre(desde, hasta)
  if (dias < 2 || dias > MAXIMO_DIAS_GRAFICO) return []
  const porDia = new Map<string, { total: number; conFeria: boolean }>()
  for (const venta of ventas) {
    const dia = fechaSantiago(venta.fecha)
    const actual = porDia.get(dia) ?? { total: 0, conFeria: false }
    porDia.set(dia, { total: actual.total + venta.total, conFeria: actual.conFeria || venta.lugarTipo === 'feria' })
  }
  const maximo = Math.max(0, ...[...porDia.values()].map((valor) => valor.total))
  return Array.from({ length: dias }, (_, indice) => {
    const dia = sumarDias(desde, indice)
    const valor = porDia.get(dia) ?? { total: 0, conFeria: false }
    return { dia, ...valor, proporcion: maximo ? valor.total / maximo : 0 }
  })
}

export function productoMasVendido(ventas: VentaRegistradaWeb[]): { nombre: string; unidades: number } | null {
  const unidades = new Map<string, { nombre: string; unidades: number }>()
  for (const venta of ventas) {
    const clave = venta.productoId || venta.productoNombre
    const actual = unidades.get(clave) ?? { nombre: venta.productoNombre, unidades: 0 }
    unidades.set(clave, { nombre: actual.nombre, unidades: actual.unidades + venta.cantidad })
  }
  return [...unidades.values()].sort((a, b) => b.unidades - a.unidades || a.nombre.localeCompare(b.nombre, 'es-CL'))[0] ?? null
}

export function variacionTotal(actual: number, anterior: number): { diferencia: number; tendencia: 'sube' | 'baja' | 'igual' } {
  const diferencia = actual - anterior
  return { diferencia: Math.abs(diferencia), tendencia: diferencia > 0 ? 'sube' : diferencia < 0 ? 'baja' : 'igual' }
}

function celdaCsv(valor: string): string {
  const seguro = /^[=+\-@]/.test(valor) ? `'${valor}` : valor
  return /[";\n]/.test(seguro) ? `"${seguro.replace(/"/g, '""')}"` : seguro
}

export function ventasCsv(ventas: VentaRegistradaWeb[]): string {
  const hora = new Intl.DateTimeFormat('es-CL', { timeZone: ZONA, hour: '2-digit', minute: '2-digit', hour12: false })
  const filas = [['Fecha', 'Hora', 'Producto', 'Variante', 'Cantidad', 'Precio unitario', 'Total', 'Lugar']]
  for (const venta of [...ventas].sort((a, b) => b.fecha.getTime() - a.fecha.getTime())) {
    filas.push([
      fechaSantiago(venta.fecha),
      hora.format(venta.fecha),
      venta.productoNombre,
      venta.varianteNombre,
      String(venta.cantidad),
      String(venta.precioUnitario),
      String(venta.total),
      venta.lugarTipo === 'feria' ? `Feria: ${venta.lugarNombre}` : venta.lugarNombre,
    ])
  }
  return `\uFEFF${filas.map((fila) => fila.map(celdaCsv).join(';')).join('\r\n')}\r\n`
}
