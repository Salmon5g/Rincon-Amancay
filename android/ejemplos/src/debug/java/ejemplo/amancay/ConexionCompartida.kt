package ejemplo.amancay

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.storage.FirebaseStorage

// Adaptador compartido: apunta al proyecto real y a la API desplegada (HTTPS).
// NO usa emuladores. La configuración se inyecta desde el proyecto del equipo;
// este ejemplo no fija el applicationId y todavía no se compiló.
object ConexionCompartida {
    // applicationId real pendiente hasta que el equipo cree su proyecto Gradle.
    data class Configuracion(
        val projectId: String,
        val applicationId: String,
        val apiKey: String,
        val storageBucket: String,
        val apiUrl: String, // URL HTTPS de Cloud Run, sin /api/v1/
    )
    data class Servicios(val auth: FirebaseAuth, val db: FirebaseFirestore, val storage: FirebaseStorage)
    private var servicios: Servicios? = null

    @Synchronized
    fun obtener(context: Context, config: Configuracion): Servicios {
        require(config.projectId == "rincon-amancay") { "El adaptador compartido exige el proyecto rincon-amancay" }
        require(config.apiUrl.startsWith("https://")) { "La API compartida debe usar HTTPS" }
        require(config.storageBucket.isNotBlank()) { "Falta el bucket de Storage" }
        servicios?.let { return it }
        val options = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApplicationId(config.applicationId)
            .setApiKey(config.apiKey)
            .setStorageBucket(config.storageBucket)
            .build()
        val app = FirebaseApp.initializeApp(context.applicationContext, options, "amancay-compartido")
        val auth = FirebaseAuth.getInstance(app)
        val db = FirebaseFirestore.getInstance(app)
        val storage = FirebaseStorage.getInstance(app)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        // Sin useEmulator: conexión real al proyecto y publicación en la nube.
        return Servicios(auth, db, storage).also { servicios = it }
    }
}
