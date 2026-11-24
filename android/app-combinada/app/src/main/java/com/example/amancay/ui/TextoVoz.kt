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
    val hora = Regex("\b(\d{1,2}):(\d{2})\b")
    if (!hora.containsMatchIn(horario)) return horario
    val convertido = hora.replace(horario) { coincidencia ->
        val h = coincidencia.groupValues[1].toInt()
        val m = coincidencia.groupValues[2].toInt()
        if (m == 0) "$h" else "$h y $m"
    }.replace(Regex("\s*[–-]\s*"), " a ")
    return "de $convertido horas"
}
