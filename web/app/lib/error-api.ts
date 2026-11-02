export class ErrorApi extends Error {
  code: string
  status: number
  resultadoIncierto: boolean

  constructor(code: string, mensaje: string, status = 0, resultadoIncierto = false) {
    super(mensaje)
    this.code = code
    this.status = status
    this.resultadoIncierto = resultadoIncierto
  }
}
