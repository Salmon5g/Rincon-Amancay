package com.example.amancay.data

object ApiNavegacionComprador {
    suspend fun leerModo(): String {
        val modo = datosApiComprador("/configuracionPublica/navegacion").optString("modo", "permitir")
        return modo.takeIf { it in setOf("permitir", "invitar", "exigir") } ?: "permitir"
    }
}