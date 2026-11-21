
/*Envío de W09 exclusivamente desde servidor. Nunca importar desde web. */
export type SolicitudCorreoFeria = {
  para: string
  asunto: string
  texto: string
  claveIdempotencia: string
}

export function crearEmisorRecordatorios(env: NodeJS.ProcessEnv) {
  const key = env.AMANCAY_MAIL_API_KEY?.trim()
  const from = env.AMANCAY_MAIL_FROM?.trim()

  if (!key || !from) return undefined

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(from)) {
    throw new Error('AMANCAY_MAIL_FROM debe ser una dirección de correo verificada.')
  }

  return async ({
    para,
    asunto,
    texto,
    claveIdempotencia,
  }: SolicitudCorreoFeria): Promise<string> => {
    const respuesta = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': claveIdempotencia,
      },
      body: JSON.stringify({
        from,
        to: [para],
        subject: asunto,
        text: texto,
      }),
      signal: AbortSignal.timeout(15000),
    })

    if (!respuesta.ok) {
      throw new Error(`Proveedor de correo rechazó el envío (${respuesta.status}).`)
    }

    const resultado: unknown = await respuesta.json()
    const identificador = (resultado as { id?: unknown } | null)?.id

    if (typeof identificador !== 'string' || !identificador) {
      throw new Error('El proveedor no confirmó el identificador del mensaje.')
    }

    return identificador
  }
}