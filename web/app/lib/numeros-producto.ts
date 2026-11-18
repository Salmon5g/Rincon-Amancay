export type ValorNumericoFormulario = string | number | null | undefined

export function textoNumericoFormulario(valor: ValorNumericoFormulario): string {
  return String(valor ?? '').trim()
}

export function enteroNoNegativoFormulario(valor: ValorNumericoFormulario): number | null {
  const texto = textoNumericoFormulario(valor)
  if (!/^\d+$/.test(texto)) return null
  const numero = Number(texto)
  return Number.isSafeInteger(numero) ? numero : null
}
