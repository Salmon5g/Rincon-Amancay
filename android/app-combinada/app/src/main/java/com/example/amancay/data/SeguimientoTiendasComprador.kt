package com.example.amancay.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class SeguimientoTiendasComprador(private val firestore: FirebaseFirestore) {
    suspend fun estaSiguiendo(usuarioId: String, tiendaId: String): Boolean =
        referencia(usuarioId, tiendaId).get(Source.SERVER).await().exists()

    suspend fun seguir(usuarioId: String, tiendaId: String) {
        referencia(usuarioId, tiendaId)
            .set(mapOf("tiendaId" to tiendaId))
            .await()
    }

    suspend fun dejarDeSeguir(usuarioId: String, tiendaId: String) {
        referencia(usuarioId, tiendaId).delete().await()
    }

    private fun referencia(usuarioId: String, tiendaId: String) =
        firestore.collection("usuarios")
            .document(validarId(usuarioId))
            .collection("tiendasSeguidas")
            .document(validarId(tiendaId))

    private fun validarId(valor: String): String {
        require(ID_SEGURO.matches(valor)) { "El identificador de seguimiento no es válido." }
        return valor
    }

    private companion object {
        val ID_SEGURO = Regex("^[A-Za-z0-9_-]{1,128}$")
    }
}
