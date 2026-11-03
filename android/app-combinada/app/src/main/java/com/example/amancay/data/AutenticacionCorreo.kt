package com.example.amancay.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object AutenticacionCorreo {
    suspend fun enviarEnlaceRestablecimiento(auth: FirebaseAuth, correo: String) {
        val direccion = correo.trim()
        require(direccion.isNotBlank()) { "Ingresa tu correo electrónico." }
        auth.setLanguageCode("es")
        auth.sendPasswordResetEmail(direccion).await()
    }
}
