export type ResultadoCompartir = 'compartido' | 'copiado' | 'cancelado' | 'fallo'

export interface DatosEnlaceCompartido {
  titulo: string
  texto: string
  url: string
}

async function copiarEnlace(url: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(url)
      return true
    } catch {
    }
  }

  const campo = document.createElement('textarea')
  campo.value = url
  campo.setAttribute('readonly', '')
  campo.style.position = 'fixed'
  campo.style.opacity = '0'
  try {
    document.body.append(campo)
    campo.select()
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    campo.remove()
  }
}

export async function compartirEnlacePublico(datos: DatosEnlaceCompartido): Promise<ResultadoCompartir> {
  if (!import.meta.client) return 'fallo'

  if (navigator.share) {
    try {
      await navigator.share({ title: datos.titulo, text: datos.texto, url: datos.url })
      return 'compartido'
    } catch (error) {
      if ((error as { name?: string }).name === 'AbortError') return 'cancelado'
    }
  }

  return await copiarEnlace(datos.url) ? 'copiado' : 'fallo'
}
