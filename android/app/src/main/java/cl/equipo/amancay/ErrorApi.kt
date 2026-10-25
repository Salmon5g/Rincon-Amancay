package cl.equipo.amancay

// Error común de la API. Vive en src/main para ser visible por ambos variantes.
class ErrorApi(val code: String, message: String, val status: Int = 0, val resultadoIncierto: Boolean = false) : Exception(message)