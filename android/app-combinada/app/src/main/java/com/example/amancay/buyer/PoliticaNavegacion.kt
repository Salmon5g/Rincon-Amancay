package com.example.amancay.buyer

internal data class EstadoPoliticaNavegacion(
    val bloqueaCatalogo: Boolean,
    val muestraInvitacion: Boolean,
)

internal fun evaluarPoliticaNavegacion(
    modo: String?,
    haySesion: Boolean,
    sesionGoogle: Boolean,
    omitioInvitacion: Boolean,
): EstadoPoliticaNavegacion = when (modo) {
    "permitir" -> EstadoPoliticaNavegacion(bloqueaCatalogo = false, muestraInvitacion = false)
    "invitar" -> EstadoPoliticaNavegacion(
        bloqueaCatalogo = false,
        muestraInvitacion = !haySesion && !omitioInvitacion,
    )
    "exigir" -> EstadoPoliticaNavegacion(
        bloqueaCatalogo = !sesionGoogle,
        muestraInvitacion = false,
    )
    // No se habilita el catálogo hasta confirmar en servidor la política vigente.
    else -> EstadoPoliticaNavegacion(bloqueaCatalogo = true, muestraInvitacion = false)
}
