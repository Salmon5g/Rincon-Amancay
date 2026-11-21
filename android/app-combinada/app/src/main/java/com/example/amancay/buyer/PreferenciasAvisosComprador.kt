package com.example.amancay.buyer

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

internal object PreferenciasAvisosComprador {
    fun topic(uid: String, tipo: String, entidadId: String) = "amancay-$tipo-$entidadId-$uid"

    suspend fun consentimiento(db: FirebaseFirestore, uid: String): Boolean =
        db.collection("usuarios").document(uid).get().await().getBoolean("avisosFerias") == true

    suspend fun cambiar(db: FirebaseFirestore, uid: String, habilitados: Boolean) {
        db.collection("usuarios").document(uid).set(
            mapOf("avisosFerias" to habilitados, "avisosConsentimientoEn" to FieldValue.serverTimestamp()),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
        val tiendas = db.collection("usuarios").document(uid).collection("tiendasSeguidas").limit(500).get().await()
        val asociaciones = db.collection("usuarios").document(uid).collection("asociacionesSeguidas").limit(500).get().await()
        tiendas.documents.forEach { doc -> actualizarTema(uid, "tienda", doc.id, habilitados) }
        asociaciones.documents.forEach { doc -> actualizarTema(uid, "asociacion", doc.id, habilitados) }
    }

    suspend fun desuscribirTodos(db: FirebaseFirestore, uid: String) {
        val tiendas = db.collection("usuarios").document(uid).collection("tiendasSeguidas").limit(500).get().await()
        val asociaciones = db.collection("usuarios").document(uid).collection("asociacionesSeguidas").limit(500).get().await()
        tiendas.documents.forEach { doc -> actualizarTema(uid, "tienda", doc.id, false) }
        asociaciones.documents.forEach { doc -> actualizarTema(uid, "asociacion", doc.id, false) }
    }

    suspend fun actualizarTema(uid: String, tipo: String, id: String, habilitado: Boolean) {
        val topic = topic(uid, tipo, id)
        if (habilitado) FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
        else FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
    }
}
