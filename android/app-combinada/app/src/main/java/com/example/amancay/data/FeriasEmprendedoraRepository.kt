package com.example.amancay.data

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FeriasEmprendedoraRepository(private val api: ApiEmprendedoraClient) {
    private val localeEspanol = Locale.forLanguageTag("es-CL")

    suspend fun listarInvitaciones(): List<FeriaInvitation> {
        val response = api.llamar("listarInvitacionesFeria", emptyMap())
        val items = response.optJSONArray("invitaciones") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.texto("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val estado = when (item.texto("estado")) {
                "invitada" -> "pendiente"
                "confirmada" -> "aceptada"
                "rechazada" -> "rechazada"
                else -> return@mapNotNull null
            }
            val inicio = item.texto("fechaInicio").fechaLocal()
            val fin = item.texto("fechaFin").fechaLocal()
            FeriaInvitation(
                id = id,
                nombre = item.texto("nombre").ifBlank { "Feria" },
                donde = item.texto("donde").ifBlank { "Lugar por confirmar" },
                cuando = inicio?.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", localeEspanol))
                    ?.replaceFirstChar { it.titlecase(localeEspanol) }
                    ?: "Fecha por confirmar",
                horario = if (inicio != null && fin != null) {
                    "${inicio.format(DateTimeFormatter.ofPattern("HH:mm"))} a ${fin.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                } else "Horario por confirmar",
                organiza = item.texto("organiza").ifBlank { "Equipo organizador" },
                estado = estado
            )
        }
    }

    suspend fun responder(feriaId: String, acepta: Boolean) {
        api.llamar(
            "responderInvitacionFeria",
            mapOf("feriaId" to feriaId, "respuesta" to if (acepta) "confirmar" else "rechazar")
        )
    }

    private fun String.fechaLocal() = runCatching {
        Instant.parse(this).atZone(ZoneId.of("America/Santiago"))
    }.getOrNull()

    private fun JSONObject.texto(clave: String): String =
        if (isNull(clave)) "" else optString(clave).trim()
}
