package cl.equipo.amancay

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.storage.FirebaseStorage

/**
 * Punto único que entrega los servicios de Firebase al modo comprador (variante debug).
 *
 * El destino se elige con [DestinoConfig.USAR_EMULADORES]:
 * - Emuladores locales: usa [HOST] = 127.0.0.1 junto con un túnel `adb reverse` que redirige
 *   los puertos del teléfono/emulador hacia el PC. Se eligió 127.0.0.1 porque el alias
 *   `10.0.2.2` no completa la sesión de Firestore en esta computadora.
 * - Nube: usa la aplicación por defecto creada por `google-services.json` (sin emuladores).
 *
 * Solo puede usarse en compilaciones debug.
 */
object ConexionLocal {
    const val HOST = "127.0.0.1" // adb reverse tcp:8080/9099/9199/8787 -> emuladores del PC
    const val API = "http://$HOST:8787/api/v1/"
    data class Servicios(val auth: FirebaseAuth, val db: FirebaseFirestore, val storage: FirebaseStorage)
    private var servicios: Servicios? = null

    @Synchronized
    fun obtener(context: Context): Servicios {
        check((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            "El adaptador local solo puede usarse en debug"
        }
        servicios?.let { return it }
        val resultado = if (DestinoConfig.USAR_EMULADORES) {
            conectarEmuladores(context)
        } else {
            conectarNube(context)
        }
        servicios = resultado
        return resultado
    }

    /** Emuladores locales: app nombrada con datos ficticios y hooks a los emuladores. */
    private fun conectarEmuladores(context: Context): Servicios {
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
        auth.useEmulator(HOST, 9099)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setHost("$HOST:8080")
            .setSslEnabled(false)
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        storage.useEmulator(HOST, 9199)
        return Servicios(auth, db, storage)
    }

    /** Nube: aplicación por defecto de `google-services.json`, sin hooks de emulador. */
    private fun conectarNube(context: Context): Servicios {
        val app = FirebaseApp.initializeApp(context.applicationContext)
            ?: error(
                "Modo nube sin google-services.json: colócalo en android/app/ o vuelve a " +
                    "DestinoConfig.USAR_EMULADORES = true. Ver docs/firebase-nube.md"
            )
        return Servicios(
            auth = FirebaseAuth.getInstance(app),
            db = FirebaseFirestore.getInstance(app),
            storage = FirebaseStorage.getInstance(app)
        )
    }
}
