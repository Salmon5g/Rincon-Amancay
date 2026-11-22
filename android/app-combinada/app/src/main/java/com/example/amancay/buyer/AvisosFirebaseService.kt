package com.example.amancay.buyer

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import com.example.amancay.R
import com.example.amancay.MainActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class AvisosFirebaseService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["tipo"] == "participacion_feria") {
            if (puedeMostrarAvisoFeria(message)) {
                mostrarAviso(
                    message.data["titulo"] ?: "Participación confirmada",
                    message.data["mensaje"].orEmpty(),
                    message.messageId ?: message.data.toString(),
                    message.data["feriaId"]
                )
            }
            return
        }
        if (message.data["tipo"] == "consulta") {
            val tiendaId = message.data["tiendaId"]?.takeIf(String::isNotBlank) ?: return
            mostrarAviso(
                message.data["titulo"] ?: "Nueva consulta",
                message.data["mensaje"].orEmpty(),
                message.messageId ?: message.data.toString(),
                tiendaConsultaId = tiendaId
            )
            return
        }
        val aviso = message.notification ?: return
        mostrarAviso(aviso.title ?: "Rincón Amancay", aviso.body.orEmpty(), message.messageId ?: message.data.toString())
    }

    private fun puedeMostrarAvisoFeria(message: RemoteMessage): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        if (message.data["destinatarioUid"] != uid) return false
        return runBlocking {
            runCatching {
                withContext(Dispatchers.IO) {
                    withTimeout(8_000) {
                        FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                            .get(Source.SERVER).await().getBoolean("avisosFerias") == true
                    }
                }
            }.getOrDefault(false)
        }
    }

    private fun mostrarAviso(
        titulo: String,
        mensaje: String,
        clave: String,
        feriaId: String? = null,
        tiendaConsultaId: String? = null
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CANAL) == null) {
            manager.createNotificationChannel(NotificationChannel(CANAL, "Avisos de Amancay", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val id = clave.hashCode()
        val constructor = Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notification_amancay)
            .setLargeIcon(iconoColorAmancay())
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setAutoCancel(true)
        val destino = when {
            feriaId?.isNotBlank() == true -> Intent(this, MainActivity::class.java).apply {
                action = ACCION_ABRIR_FERIA
                data = Uri.parse("amancay://feria/$feriaId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_FERIA_ID, feriaId)
            }
            tiendaConsultaId?.isNotBlank() == true -> Intent(this, MainActivity::class.java).apply {
                action = ACCION_ABRIR_CONSULTAS
                data = Uri.parse("amancay://consultas/$tiendaConsultaId")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_CONSULTAS_TIENDA_ID, tiendaConsultaId)
            }
            else -> null
        }
        destino?.let {
            val apertura = PendingIntent.getActivity(
                this,
                (feriaId ?: tiendaConsultaId).hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            constructor.setContentIntent(apertura)
        }
        val notificacion = constructor
            .build()
        runCatching { manager.notify(id, notificacion) }
    }

    private fun iconoColorAmancay(): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, R.drawable.amancay_logo_color) ?: return null
        val dimension = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width).coerceAtLeast(1)
        return Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, dimension, dimension)
            drawable.draw(Canvas(bitmap))
        }
    }

    private companion object {
        const val CANAL = "amancay_avisos"
        const val ACCION_ABRIR_FERIA = "com.example.amancay.ABRIR_FERIA"
        const val ACCION_ABRIR_CONSULTAS = "com.example.amancay.ABRIR_CONSULTAS"
    }
}
