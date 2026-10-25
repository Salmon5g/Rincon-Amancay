package cl.equipo.amancay.comprador

import java.text.NumberFormat
import java.util.Locale

/**
 * Utilidades de formato en español para la interfaz del comprador.
 * Se usan para no repetir esta lógica en cada pantalla.
 */

private val formatoNumero: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL"))

/** Da formato a un precio, por ejemplo `4500` -> `"$4.500 CLP"`. Si es null, "Consultar precio". */
fun precioConMoneda(precio: Long?, moneda: String): String {
    if (precio == null) return "Consultar precio"
    val numero = formatoNumero.format(precio)
    return if (moneda.isBlank()) "\$$numero" else "\$$numero $moneda"
}

/** Traduce el estado de disponibilidad de los datos (`a_pedido`) a texto legible ("A pedido"). */
fun etiquetaDisponibilidad(valor: String): String = when (valor) {
    "disponible" -> "Disponible"
    "a_pedido" -> "A pedido"
    "agotado" -> "Agotado"
    "no_disponible" -> "No disponible"
    "" -> ""
    else -> valor
}

/** Traduce la modalidad de venta para mostrarla con un texto más claro. */
fun etiquetaModalidad(valor: String): String = when (valor) {
    "regular" -> "Venta regular"
    "a_pedido" -> "A pedido"
    "" -> ""
    else -> valor
}
