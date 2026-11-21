package com.example.amancay.buyer

import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal class AvisosFirebaseService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CANAL) == null) {
            manager.createNotificationChannel(NotificationChannel(CANAL, "Avisos de Amancay", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val aviso = message.notification ?: return
        val id = (message.messageId ?: message.data.toString()).hashCode()
        val notificacion = Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(aviso.title ?: "Rincón Amancay")
            .setContentText(aviso.body.orEmpty())
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id, notificacion) }
    }

    private companion object { const val CANAL = "amancay_avisos" }
}
