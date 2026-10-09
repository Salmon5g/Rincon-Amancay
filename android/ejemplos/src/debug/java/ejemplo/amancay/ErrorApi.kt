package ejemplo.amancay

// Error común de la API. Debe vivir en el código compartido visible por ambos
// variantes. Al integrar, copiar este archivo junto con ClienteCompartido.kt a
// src/main/ (no a src/debug/), porque src/main no puede ver src/debug.
class ErrorApi(val code: String, message: String, val status: Int = 0, val resultadoIncierto: Boolean = false) : Exception(message)
