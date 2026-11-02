package com.example.amancay.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

/**
 * Acceso del comprador con su cuenta de Google, reutilizando la app de
 * Firebase creada en FirebaseClientServicesProvider (google-services.json).
 *
 * El flujo usa Credential Manager (AndroidX) con un ID de cliente web
 * (serverClientId). Ese valor llega por BuildConfig.WEB_CLIENT_ID (propiedad
 * `amancay.google.webClientId` en local.properties) o, como respaldo, del
 * recurso `default_web_client_id` que el plugin de Google Services genera
 * cuando el google-services.json ya trae el cliente web.
 */
data class SesionComprador(
    val correo: String,
    val nombre: String,
    val iniciales: String,
    val fotoUrl: String?
)

fun sesionDesdeUsuario(usuario: FirebaseUser): SesionComprador {
    val nombre = usuario.displayName.orEmpty().ifBlank { usuario.email.orEmpty() }
    val partes = nombre.trim().split(" ").filter { it.isNotBlank() }
    val iniciales = run {
        val primera = partes.firstOrNull()?.firstOrNull()?.uppercase() ?: "?"
        val segunda = partes.getOrNull(1)?.firstOrNull()?.uppercase() ?: ""
        val junto = "$primera$segunda".take(2)
        junto.ifBlank { "?" }
    }
    return SesionComprador(
        correo = usuario.email.orEmpty(),
        nombre = nombre,
        iniciales = iniciales,
        fotoUrl = usuario.photoUrl?.toString()
    )
}

/** Mensajes de error de credenciales en el tono de la app. */
private fun mensajeErrorAcceso(error: Exception): String = when (error) {
    is GetCredentialCancellationException, is CancellationException ->
        // El comprador cerró el panel de Google; no es un fallo que avisar.
        ""
    is NoCredentialException ->
        "No hay una cuenta de Google activa en este dispositivo. Agrega una en Ajustes del emulador (o del teléfono) e inténtalo otra vez."
    is GetCredentialException ->
        "Google no pudo entregar tus credenciales. Si acabas de registrar la huella en la consola de Firebase, espera unos minutos e inténtalo otra vez."
    else ->
        "No se pudo iniciar sesión con Google: ${error.message ?: "motivo desconocido"}"
}

sealed class ResultadoAccesoGoogle {
    /** La sesión quedó creada en Firebase. */
    data object Exito : ResultadoAccesoGoogle()

    /** El comprador cerró el panel de Google sin elegir cuenta. */
    data object Cancelado : ResultadoAccesoGoogle()

    /** Falló y se le puede mostrar el mensaje al comprador. */
    data class Fallido(val mensaje: String) : ResultadoAccesoGoogle()
}

object AutenticacionGoogleComprador {

    /**
     * ID de cliente web para el flujo de Google: prioriza el valor declarado
     * en local.properties (llegado por BuildConfig) y, si no está, usa el
     * recurso `default_web_client_id` que genera el plugin de Google Services
     * cuando el google-services.json ya trae el cliente web registrado.
     */
    fun idClienteWeb(context: Context, deBuildConfig: String): String {
        if (deBuildConfig.isNotBlank()) return deBuildConfig
        val idRecurso = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (idRecurso != 0) context.getString(idRecurso) else ""
    }

    /**
     * Abre el panel de Google, canjea el ID token por una credencial de
     * Firebase Auth y deja la sesión visible para el AuthStateListener.
     */
    suspend fun iniciarSesionConGoogle(
        context: Context,
        auth: FirebaseAuth,
        webClientIdDeBuild: String
    ): ResultadoAccesoGoogle {
        if (webClientIdDeBuild.isBlank()) {
            return ResultadoAccesoGoogle.Fallido(
                "Falta configurar el acceso con Google: registra la huella del equipo en la consola de Firebase para com.example.amancay y coloca el ID de cliente web en local.properties (amancay.google.webClientId)."
            )
        }
        val credentialManager = CredentialManager.create(context)
        return try {
            val opcionGoogle = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientIdDeBuild)
                .build()
            val pedido = GetCredentialRequest.Builder()
                .addCredentialOption(opcionGoogle)
                .build()
            val respuesta = credentialManager.getCredential(context, pedido)
            val credencial = respuesta.credential
            if (credencial is CustomCredential &&
                credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val tokenGoogle = GoogleIdTokenCredential.createFrom(credencial.data)
                val credencialFirebase = GoogleAuthProvider.getCredential(tokenGoogle.idToken, null)
                val usuario = auth.signInWithCredential(credencialFirebase).await().user
                if (usuario == null) {
                    ResultadoAccesoGoogle.Fallido(
                        "Google entregó una sesión vacía. Verifica que Authentication esté habilitado en el proyecto Firebase."
                    )
                } else {
                    ResultadoAccesoGoogle.Exito
                }
            } else {
                ResultadoAccesoGoogle.Fallido("Credential Manager devolvió un tipo de credencial inesperado.")
            }
        } catch (error: GetCredentialException) {
            val mensaje = mensajeErrorAcceso(error)
            if (mensaje.isBlank()) ResultadoAccesoGoogle.Cancelado
            else ResultadoAccesoGoogle.Fallido(mensaje)
        } catch (error: Exception) {
            val mensaje = mensajeErrorAcceso(error)
            if (mensaje.isBlank()) ResultadoAccesoGoogle.Cancelado
            else ResultadoAccesoGoogle.Fallido(mensaje)
        }
    }

    /** Cierra la sesión de Firebase y limpia las credenciales de Google. */
    suspend fun cerrarSesion(context: Context, auth: FirebaseAuth) {
        auth.signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Sin Google Play Services el borrado de credenciales no existe;
            // la sesión de Firebase ya quedó cerrada, que es lo principal.
        }
    }
}
