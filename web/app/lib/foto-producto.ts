export type OpcionesFotoProducto = {
  recorteCuadrado: boolean
  mejorarLuz: boolean
}

export const OPCIONES_FOTO_INICIALES: OpcionesFotoProducto = { recorteCuadrado: true, mejorarLuz: false }

const LIMITE_BYTES = 5 * 1024 * 1024
const LADO_MAXIMO = 4000

export function areaRecorte(ancho: number, alto: number, recorteCuadrado: boolean) {
  if (!recorteCuadrado) return { x: 0, y: 0, ancho, alto }
  const lado = Math.min(ancho, alto)
  return { x: Math.floor((ancho - lado) / 2), y: Math.floor((alto - lado) / 2), ancho: lado, alto: lado }
}

export function ajustarLuzSuave(pixeles: Uint8ClampedArray) {
  for (let i = 0; i < pixeles.length; i += 4) {
    pixeles[i] = pixeles[i]! * 1.04 + 8
    pixeles[i + 1] = pixeles[i + 1]! * 1.04 + 8
    pixeles[i + 2] = pixeles[i + 2]! * 1.04 + 8
  }
}

function lienzoABlob(lienzo: HTMLCanvasElement, tipo: string, calidad: number): Promise<Blob | null> {
  return new Promise((resolve) => lienzo.toBlob(resolve, tipo, calidad))
}

export async function procesarFotoProducto(archivo: File, opciones: OpcionesFotoProducto): Promise<File> {
  if (!opciones.recorteCuadrado && !opciones.mejorarLuz) return archivo
  const imagen = await createImageBitmap(archivo)
  try {
    const area = areaRecorte(imagen.width, imagen.height, opciones.recorteCuadrado)
    let escala = Math.min(1, LADO_MAXIMO / Math.max(area.ancho, area.alto))
    const tipo = archivo.type === 'image/webp' ? 'image/webp' : 'image/jpeg'
    for (let intento = 0; intento < 4; intento += 1) {
      const lienzo = document.createElement('canvas')
      lienzo.width = Math.max(1, Math.round(area.ancho * escala))
      lienzo.height = Math.max(1, Math.round(area.alto * escala))
      const contexto = lienzo.getContext('2d')
      if (!contexto) throw new Error('El navegador no permite ajustar la foto.')
      contexto.drawImage(imagen, area.x, area.y, area.ancho, area.alto, 0, 0, lienzo.width, lienzo.height)
      if (opciones.mejorarLuz) {
        const datos = contexto.getImageData(0, 0, lienzo.width, lienzo.height)
        ajustarLuzSuave(datos.data)
        contexto.putImageData(datos, 0, 0)
      }
      const blob = await lienzoABlob(lienzo, tipo, intento === 0 ? 0.92 : 0.86)
      if (blob && blob.size > 0 && blob.size <= LIMITE_BYTES) {
        const nombreBase = archivo.name.replace(/\.[^.]+$/, '') || 'foto-producto'
        return new File([blob], `${nombreBase}-ajustada.${tipo === 'image/webp' ? 'webp' : 'jpg'}`, { type: tipo, lastModified: Date.now() })
      }
      escala *= 0.8
    }
    throw new Error('La foto ajustada supera 5 MiB. Prueba con una imagen más pequeña.')
  } finally {
    imagen.close()
  }
}
