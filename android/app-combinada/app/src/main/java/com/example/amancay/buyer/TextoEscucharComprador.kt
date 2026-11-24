package com.example.amancay.buyer

import com.example.amancay.data.ProductoCatalogoComprador
import com.example.amancay.data.VarianteCatalogoComprador
import com.example.amancay.ui.precioHablado
import com.example.amancay.ui.unirFrases

/** Texto que se lee al pulsar «Escuchar ficha» en el detalle de un producto. */
fun textoParaEscucharProducto(
    producto: ProductoCatalogoComprador,
    variante: VarianteCatalogoComprador?
): String {
    val precio = if (producto.variantes.isNotEmpty()) variante?.precio else producto.precioBase
    val estado = variante?.estadoDisponibilidad?.takeIf(String::isNotBlank) ?: producto.estadoDisponibilidad
    val opciones = variante?.opciones?.values?.filter(String::isNotBlank).orEmpty()
    val categoria = producto.categoriaNombre.ifBlank { producto.tipoProductoNombre }
    val modalidad = etiquetaModalidad(producto.modalidad)
    val detalles = producto.atributosEspecificos.entries
        .filter { it.value.isNotBlank() }
        .map { "${etiquetaAtributoProducto(it.key)}: ${it.value.trim()}" }
    return unirFrases(
        producto.nombre,
        producto.tiendaNombre.takeIf(String::isNotBlank)?.let { "De la tienda $it" },
        etiquetaDisponibilidad(estado),
        precio?.takeIf { it > 0 }?.let { "Precio: ${precioHablado(it)}" } ?: "Precio a consultar con la emprendedora",
        opciones.takeIf { it.isNotEmpty() }?.let { "Opción elegida: ${it.joinToString(", ")}" },
        producto.variantes.size.takeIf { it > 1 }?.let { "Hay $it opciones disponibles para elegir" },
        categoria.takeIf(String::isNotBlank)?.let { "Categoría: $it" },
        producto.descripcion,
        *detalles.toTypedArray(),
        producto.unidadVenta.takeIf(String::isNotBlank)?.let { "Se vende por $it" },
        modalidad.takeIf(String::isNotBlank)?.let { "Modalidad: $it" }
    )
}

/** Texto que se lee al pulsar «Escuchar ficha» en la ficha de una tienda. */
fun textoParaEscucharTienda(
    tienda: com.example.amancay.data.TiendaCatalogoComprador,
    tipo: String,
    sector: String,
    cantidadProductos: Int
): String {
    val vias = listOfNotNull(
        "WhatsApp".takeIf { tienda.whatsappHabilitado },
        "formulario".takeIf { tienda.formularioHabilitado },
        "correo".takeIf { tienda.correoHabilitado }
    )
    return unirFrases(
        "Tienda ${tienda.nombre}",
        tipo.takeIf(String::isNotBlank)?.let { "Tipo de emprendimiento: $it" },
        sector.takeIf(String::isNotBlank)?.let { "Ubicación: sector $it" },
        tienda.descripcion,
        when (cantidadProductos) {
            0 -> "Por ahora no tiene productos publicados"
            1 -> "Tiene 1 producto publicado"
            else -> "Tiene $cantidadProductos productos publicados"
        },
        if (tienda.mostrarPrecios) null else "Los precios se consultan directamente con la emprendedora",
        vias.takeIf { it.isNotEmpty() }?.let { "Puedes contactarla por ${unirConY(it)}" },
        tienda.puntoRetiro?.let { punto ->
            punto.descripcion.takeIf(String::isNotBlank)?.let { "Punto de venta o retiro: $it" }
                ?: "Tiene un punto de venta o retiro marcado en el mapa"
        }
    )
}

/** Texto que se lee al pulsar «Escuchar ficha» en el detalle de una feria. */
fun textoParaEscucharFeria(feria: FeriaComprador, participantes: List<String>?): String = unirFrases(
    "Feria ${feria.nombre}",
    com.example.amancay.ui.fechaHablada(feria.fechaInicio).takeIf(String::isNotBlank)?.let { "Comienza el $it" },
    com.example.amancay.ui.fechaHablada(feria.fechaFin).takeIf(String::isNotBlank)?.let { "Termina el $it" },
    feria.direccion.takeIf(String::isNotBlank)?.let { "Lugar: $it" },
    feria.organizador.takeIf(String::isNotBlank)?.let { "Organiza: $it" },
    feria.descripcion,
    participantes?.filter(String::isNotBlank)?.takeIf { it.isNotEmpty() }?.let { "Participan ${unirConY(it)}" }
)

/** Texto que se lee en la ficha de una invitación a feria de la emprendedora. */
fun textoParaEscucharInvitacionFeria(invitacion: com.example.amancay.data.FeriaInvitation): String = unirFrases(
    "Feria ${invitacion.nombre}",
    when (invitacion.estado) {
        "pendiente" -> "Te invitaron a participar. Puedes responder si participas o no"
        "agregada_admin" -> "La administración registró tu participación directamente"
        "aceptada" -> "Tu participación está confirmada"
        "rechazada" -> "Respondiste que no participas"
        else -> null
    },
    invitacion.donde.takeIf(String::isNotBlank)?.let { "Lugar: $it" },
    invitacion.cuando.takeIf(String::isNotBlank)?.let { "Fecha: $it" },
    invitacion.horario.takeIf(String::isNotBlank)?.let { "Horario: ${com.example.amancay.ui.horarioHablado(it)}" },
    invitacion.organiza.takeIf(String::isNotBlank)?.let { "Organiza: $it" }
)

private fun unirConY(partes: List<String>): String = when (partes.size) {
    0 -> ""
    1 -> partes[0]
    else -> partes.dropLast(1).joinToString(", ") + " y " + partes.last()
}
