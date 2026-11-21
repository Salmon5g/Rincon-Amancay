package com.example.amancay.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class SeguimientoAsociacionesComprador(private val firestore: FirebaseFirestore) {
    suspend fun estaSiguiendo(usuarioId: String, asociacionId: String): Boolean =
        referencia(usuarioId, asociacionId).get(Source.SERVER).await().exists()

    suspend fun seguir(usuarioId: String, asociacionId: String) {
        referencia(usuarioId, asociacionId)
            .set(mapOf("asociacionId" to asociacionId))
            .await()
    }

    suspend fun dejarDeSeguir(usuarioId: String, asociacionId: String) {
        referencia(usuarioId, asociacionId).delete().await()
    }

    private fun referencia(usuarioId: String, asociacionId: String) =
        firestore.collection("usuarios")
            .document(validarId(usuarioId))
            .collection("asociacionesSeguidas")
            .document(validarId(asociacionId))

    private fun validarId(valor: String): String {
        require(ID_SEGURO.matches(valor)) { "El identificador de seguimiento no es válido." }
        return valor
    }

    private companion object {
        val ID_SEGURO = Regex("^[A-Za-z0-9_-]{1,128}$")
    }
}
