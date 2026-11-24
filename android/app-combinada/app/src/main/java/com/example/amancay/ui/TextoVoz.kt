package com.example.amancay.ui

/** Divide un texto en frases cortas para poder pausar y reanudar la lectura entre ellas. */
fun dividirEnFrases(texto: String): List<String> = texto
    .split(Regex("(?<=[.!?;:])\\s+|\\n+"))
    .map { it.trim().replace(Regex("\\s+"), " ") }
    .filter { parte -> parte.any(Char::isLetterOrDigit) }

/** Corta una frase demasiado larga por espacios, sin partir palabras. */
fun partirPorLongitud(frase: String, limite: Int): List<String> {
    if (frase.length <= limite) return listOf(frase)
    val partes = mutableListOf<String>()
    var resto = frase
    while (resto.length > limite) {
        val corte = resto.lastIndexOf(' ', limite).takeIf { it > 0 } ?: limite
        partes += resto.substring(0, corte).trim()
        resto = resto.substring(corte).trim()
    }
    if (resto.isNotEmpty()) partes += resto
    return partes
}

/** Precio en pesos dicho en palabras simples, sin símbolos que el motor lea letra por letra. */
fun precioHablado(valor: Long): String = if (valor == 1L) "1 peso" else "$valor pesos"

/** Arma un párrafo con frases terminadas en punto, omitiendo las vacías. */
fun unirFrases(vararg frases: String?): String = frases
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .joinToString(" ") { if (it.last() in ".!?") it else "$it." }

private val DIGITOS_HABLADOS_ES = mapOf(
    "cero" to "0",
    "un" to "1", "uno" to "1", "una" to "1",
    "dos" to "2", "tres" to "3", "cuatro" to "4", "cinco" to "5",
    "seis" to "6", "siete" to "7", "ocho" to "8", "nueve" to "9"
)

/** Fecha y hora dichas en palabras: «sábado 30 de noviembre, a las 10 y 30». */
fun fechaHablada(fecha: java.util.Date?): String {
    fecha ?: return ""
    val calendario = java.util.Calendar.getInstance().apply { time = fecha }
    val dia = java.text.SimpleDateFormat("EEEE d 'de' MMMM", java.util.Locale.forLanguageTag("es")).format(fecha)
    val hora = calendario.get(java.util.Calendar.HOUR_OF_DAY)
    val minutos = calendario.get(java.util.Calendar.MINUTE)
    val articulo = if (hora == 1) "a la" else "a las"
    val textoHora = if (minutos == 0) "$articulo $hora" else "$articulo $hora y $minutos"
    return "$dia, $textoHora"
}

/** Convierte «10:00 a 17:30» en «de 10 a 17 y 30 horas»; deja igual los textos sin horas. */
fun horarioHablado(horario: String): String {
    val hora = Regex("""\b(\d{1,2}):(\d{2})\b""")
    if (!hora.containsMatchIn(horario)) return horario
    val convertido = hora.replace(horario) { coincidencia ->
        val h = coincidencia.groupValues[1].toInt()
        val m = coincidencia.groupValues[2].toInt()
        if (m == 0) "$h" else "$h y $m"
    }.replace(Regex("""\s*[–-]\s*"""), " a ")
    return "de $convertido horas"
}

/**
 * Integra lo dictado en un campo. En campos numéricos reemplaza el valor por los dígitos
 * reconocidos («6.990» → «6990») y también convierte cifras habladas («uno» → «1», «uno dos»
 * → «12»). Devuelve null si no se reconoció un número; en campos de texto agrega lo dictado
 * al final, con mayúscula inicial si el campo estaba vacío.
 */
fun combinarDictado(actual: String, dictado: String, numerico: Boolean): String? {
    val limpio = dictado.trim().replace(Regex("""\s+"""), " ")
    if (numerico) {
        val digitos = limpio.filter(Char::isDigit)
        if (digitos.isNotEmpty()) return digitos

        val palabras = limpio
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("""[.,;:!?]"""), "")
            .split(' ')
            .filter(String::isNotBlank)
        return palabras
            .mapNotNull { DIGITOS_HABLADOS_ES[it] }
            .takeIf { it.size == palabras.size && it.isNotEmpty() }
            ?.joinToString("")
    }
    if (limpio.isEmpty()) return null
    val base = actual.trimEnd()
    if (base.isEmpty()) return limpio.replaceFirstChar { it.uppercase() }
    val siguiente = if (base.last() in ".!?") limpio.replaceFirstChar { it.uppercase() } else limpio
    return "$base $siguiente"
}
