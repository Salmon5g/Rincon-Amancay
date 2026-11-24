export type TamanoTextoAccesible = 'normal' | 'grande' | 'muy-grande'

export const opcionesTextoAccesible: { id: TamanoTextoAccesible; etiqueta: string; muestra: string; escala: number }[] = [
  { id: 'normal', etiqueta: 'Normal', muestra: 'A', escala: 1 },
  { id: 'grande', etiqueta: 'Grande', muestra: 'A+', escala: 1.125 },
  { id: 'muy-grande', etiqueta: 'Más grande', muestra: 'A++', escala: 1.25 },
]

const CLAVE_TAMANO_TEXTO = 'amancay:tamano-texto'

export function useTextoAccesible() {
  const tamano = useState<TamanoTextoAccesible>('amancay-tamano-texto', () => 'normal')
  const inicializado = useState<boolean>('amancay-tamano-texto-inicializado', () => false)

  function aplicar(valor: TamanoTextoAccesible) {
    if (!import.meta.client) return
    const escala = opcionesTextoAccesible.find(opcion => opcion.id === valor)?.escala ?? 1
    // `zoom` amplía también los textos definidos en píxeles (como el panel
    // emprendedora) y permite que el diseño adaptable vuelva a distribuirse.
    document.documentElement.style.zoom = String(escala)
    document.documentElement.style.setProperty('--amancay-escala-texto', String(escala))
  }

  onMounted(() => {
    if (!inicializado.value) {
      try {
        const guardado = localStorage.getItem(CLAVE_TAMANO_TEXTO)
        if (opcionesTextoAccesible.some(opcion => opcion.id === guardado)) tamano.value = guardado as TamanoTextoAccesible
      } catch { /* El tamaño se conserva mientras esta página permanezca abierta. */ }
      inicializado.value = true
    }
    aplicar(tamano.value)
  })

  watch(tamano, valor => {
    aplicar(valor)
    if (!import.meta.client) return
    try { localStorage.setItem(CLAVE_TAMANO_TEXTO, valor) } catch { /* La preferencia queda activa en esta visita. */ }
  })

  return { tamano, cambiarTamano: (valor: TamanoTextoAccesible) => { tamano.value = valor } }
}
