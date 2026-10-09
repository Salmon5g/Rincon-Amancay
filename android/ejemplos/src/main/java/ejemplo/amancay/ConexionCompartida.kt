package ejemplo.amancay

import android.content.Context
import java.net.URI
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.storage.FirebaseStorage

// Ejemplo para src/main: apunta al proyecto real y a la API desplegada (HTTPS).
// NO usa emuladores. La configuración se inyecta desde el proyecto del equipo;
// este ejemplo no fija el applicationId y todavía no se compiló.
object ConexionCompartida {
    // firebaseAppId es mobilesdk_app_id de Firebase, NO el applicationId de Gradle.
    data class Configuracion(
        val projectId: String,
        val firebaseAppId: String,
        val apiKey: String,
        val storageBucket: String,
        val apiUrl: String, // URL HTTPS de Cloud Run, sin /api/v1/
    )
    data class Servicios(val auth: FirebaseAuth, val db: FirebaseFirestore, val storage: FirebaseStorage)
    private var servicios: Servicios? = null
    private var configuracion: Configuracion? = null

    fun validarApiUrl(valor: String): String {
        val url = URI(valor)
        require(url.scheme == "https" && !url.host.isNullOrBlank() && url.rawUserInfo == null &&
            url.rawQuery == null && url.rawFragment == null && (url.port == -1 || url.port in 1..65535) &&
            (url.rawPath.isNullOrEmpty() || url.rawPath == "/")) {
            "Usar el origen HTTPS de la API, sin rutas, credenciales, consulta ni fragmento"
        }
        return valor.trimEnd('/')
    }

    @Synchronized
    fun obtener(context: Context, config: Configuracion): Servicios {
        require(config.projectId == "rincon-amancay") { "El adaptador compartido exige el proyecto rincon-amancay" }
        validarApiUrl(config.apiUrl)
        require(config.firebaseAppId.matches(Regex("1:[0-9]+:android:[a-zA-Z0-9]+"))) {
            "firebaseAppId debe ser mobilesdk_app_id de Firebase (1:…:android:…), no el package name"
        }
        require(config.apiKey.isNotBlank()) { "Falta la API key de Firebase" }
        require(config.storageBucket.isNotBlank()) { "Falta el bucket de Storage" }
        servicios?.let {
            require(configuracion == config) { "Firebase ya se inicializó con otra configuración" }
            return it
        }
        val options = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApplicationId(config.firebaseAppId)
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
        return Servicios(auth, db, storage).also { servicios = it; configuracion = config }
    }
}
