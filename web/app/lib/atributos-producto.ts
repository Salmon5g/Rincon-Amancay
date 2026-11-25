const PALABRAS_CON_TILDE: Record<string, string> = {
  alergenos: 'alérgenos',
  elaboracion: 'elaboración',
  fabricacion: 'fabricación',
  composicion: 'composición',
  descripcion: 'descripción',
  duracion: 'duración',
  presentacion: 'presentación',
  conservacion: 'conservación',
  tecnica: 'técnica',
  dimension: 'dimensión',
  dimensiones: 'dimensiones',
  origen: 'origen',
  energia: 'energía',
  vegano: 'vegano',
  organico: 'orgánico',
  organica: 'orgánica',
  caracteristicas: 'características',
  cuidado: 'cuidado',
  riego: 'riego',
  ubicacion: 'ubicación',
  estacion: 'estación',
  coleccion: 'colección',
  diametro: 'diámetro',
  unidad: 'unidad',
  peso: 'peso',
}

export function etiquetaLegibleAtributo(clave: string): string {
  const palabras = clave
    .replace(/([a-záéíóúñ0-9])([A-ZÁÉÍÓÚÑ])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLocaleLowerCase('es-CL')
    .split(/\s+/)
    .filter(Boolean)
    .map((palabra) => PALABRAS_CON_TILDE[palabra] ?? palabra)
  const conectadas = palabras.length === 2 && palabras[0] === 'fecha'
    ? ['fecha', 'de', palabras[1]!]
    : palabras
  const texto = conectadas.join(' ')
  return texto ? texto.charAt(0).toLocaleUpperCase('es-CL') + texto.slice(1) : clave
}

export function atributosConEtiqueta(
  atributos: Record<string, string>,
  etiquetas: Record<string, string>,
): { clave: string; etiqueta: string; valor: string }[] {
  return Object.entries(atributos)
    .filter(([, valor]) => typeof valor === 'string' && valor.trim() !== '')
    .map(([clave, valor]) => ({
      clave,
      etiqueta: etiquetas[clave]?.trim() || etiquetaLegibleAtributo(clave),
      valor: valor.trim(),
    }))
}
