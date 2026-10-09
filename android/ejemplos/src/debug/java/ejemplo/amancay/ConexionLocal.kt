package ejemplo.amancay

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.storage.FirebaseStorage

// Ejemplo para src/debug. Este package no fija el applicationId de la app.
object ConexionLocal {
    const val HOST = "10.0.2.2" // Android Emulator -> computador anfitrión
    const val API = "http://$HOST:8787/api/v1/"
    data class Servicios(val auth: FirebaseAuth, val db: FirebaseFirestore, val storage: FirebaseStorage)
    private var servicios: Servicios? = null

    @Synchronized
    fun obtener(context: Context): Servicios {
        check((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            "El adaptador local solo puede usarse en debug"
        }
        servicios?.let { return it }
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-rincon-amancay")
            .setApplicationId("1:1234567890:android:0123456789abcdef") // ID ficticio local
            .setApiKey("demo-key")
            .setStorageBucket("demo-rincon-amancay.appspot.com")
            .build()
        val app = FirebaseApp.initializeApp(context.applicationContext, options, "amancay-local")
        val auth = FirebaseAuth.getInstance(app)
        val db = FirebaseFirestore.getInstance(app)
        val storage = FirebaseStorage.getInstance(app)
        // Antes de iniciar sesión o consultar; nunca usar FirebaseApp por defecto.
        auth.useEmulator(HOST, 9099)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        db.useEmulator(HOST, 8080)
        storage.useEmulator(HOST, 9199)
        return Servicios(auth, db, storage).also { servicios = it }
    }
}
