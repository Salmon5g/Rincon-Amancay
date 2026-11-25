const DIGITOS_HABLADOS: Record<string, string> = {
  cero: '0',
  un: '1', uno: '1', una: '1',
  dos: '2', tres: '3', cuatro: '4', cinco: '5',
  seis: '6', siete: '7', ocho: '8', nueve: '9',
}

export function combinarDictado(actual: string, dictado: string, numerico: boolean): string | null {
  const limpio = dictado.trim().replace(/\s+/g, ' ')
  if (numerico) {
    const digitos = limpio.replace(/\D/g, '')
    if (digitos) return digitos
    const palabras = limpio
      .toLocaleLowerCase('es-CL')
      .replace(/[.,;:!?¡¿]/g, '')
      .split(' ')
      .filter(Boolean)
    const convertidas = palabras.map((palabra) => DIGITOS_HABLADOS[palabra])
    if (!convertidas.length || convertidas.some((digito) => digito === undefined)) return null
    return convertidas.join('')
  }
  if (!limpio) return null
  const conMayuscula = limpio.charAt(0).toLocaleUpperCase('es-CL') + limpio.slice(1)
  const base = actual.trimEnd()
  if (!base) return conMayuscula
  return `${base} ${/[.!?]$/.test(base) ? conMayuscula : limpio}`
}
