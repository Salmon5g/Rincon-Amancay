package com.example.amancay.buyer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.amancay.data.AutenticacionCorreo
import com.example.amancay.data.AutenticacionGoogleComprador
import com.example.amancay.data.SesionComprador
import com.example.amancay.ui.Fig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Pestaña «Mi sesión»: acceso con correo/contraseña o Google, recuperación
 * de contraseña, creación de cuenta y cierre de sesión. Es el mismo flujo
 * que antes era una pantalla superpuesta, ahora vive en su propia pestaña.
 */

@Composable
fun PantallaCuentaComprador(
    sesion: SesionComprador?,
    auth: FirebaseAuth,
    googleCargando: Boolean,
    mensaje: String?,
    alRefrescarPerfil: () -> Unit,
    alExplorar: () -> Unit,
    alCerrarSesion: () -> Unit,
    onGoogle: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sesion == null) {
        PantallaAccesoComprador(
            auth = auth,
            googleCargando = googleCargando,
            mensaje = mensaje,
            alRefrescarPerfil = alRefrescarPerfil,
            alExplorar = alExplorar,
            onGoogle = onGoogle,
            modifier = modifier
        )
    } else {
        PantallaSesionComprador(
            sesion = sesion,
            cerrando = googleCargando,
            alCerrarSesion = alCerrarSesion,
            alExplorar = alExplorar,
            modifier = modifier
        )
    }
}

@Composable
private fun PantallaSesionComprador(
    sesion: SesionComprador,
    cerrando: Boolean,
    alCerrarSesion: () -> Unit,
    alExplorar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmancayFondo)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Fig.LOGO),
            contentDescription = "Rincón Amancay",
            modifier = Modifier.size(84.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Hola, ${sesion.nombre.ifBlank { sesion.correo }}",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            color = AmancayBordo,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Esta es tu cuenta de compradora en Rincón Amancay.",
            style = MaterialTheme.typography.bodyMedium,
            color = AmancayTintaSuave,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        TarjetaAmancay {
            Text("Cuenta", style = MaterialTheme.typography.labelSmall, color = AmancayBordo)
            Spacer(Modifier.height(6.dp))
            Text(
                sesion.nombre.ifBlank { "Sin nombre registrado" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AmancayTinta
            )
            Spacer(Modifier.height(4.dp))
            Text(
                sesion.correo.ifBlank { "Sin correo registrado" },
                style = MaterialTheme.typography.bodyMedium,
                color = AmancayTintaSuave
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = alExplorar,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AmancayBordo),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmancayBordo)
        ) {
            Text("Volver a explorar productos", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = alCerrarSesion,
            enabled = !cerrando,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AmancayBordo,
                contentColor = Color.White
            )
        ) {
            if (cerrando) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text("Cerrar sesión", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PantallaAccesoComprador(
    auth: FirebaseAuth,
    googleCargando: Boolean,
    mensaje: String?,
    alRefrescarPerfil: () -> Unit,
    alExplorar: () -> Unit,
    onGoogle: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val alcance = rememberCoroutineScope()
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var creandoCuenta by remember { mutableStateOf(false) }
    var enviandoCorreo by remember { mutableStateOf(false) }
    var recuperandoContrasena by remember { mutableStateOf(false) }
    var errorCorreo by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmancayFondo)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Fig.LOGO),
            contentDescription = "Rincón Amancay",
            modifier = Modifier.size(76.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (creandoCuenta) "Crea tu cuenta compradora" else "Inicia sesión para comprar",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            color = AmancayBordo,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (creandoCuenta)
                "Regístrate con tu correo para enviar encargos y seguir a tus emprendimientos."
            else
                "Con tu cuenta puedes enviar encargos y seguir a tus emprendimientos.",
            style = MaterialTheme.typography.bodyMedium,
            color = AmancayTintaSuave,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        if (creandoCuenta) {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nombre y apellido") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = campoAcceso()
            )
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Correo electrónico") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = campoAcceso()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Contraseña") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            colors = campoAcceso()
        )
        Spacer(Modifier.height(12.dp))
        val aviso = errorCorreo ?: mensaje
        aviso?.let {
            TarjetaAmancay {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AmancayTinta)
            }
            Spacer(Modifier.height(14.dp))
        }
        Button(
            onClick = {
                if (enviandoCorreo || googleCargando) return@Button
                enviandoCorreo = true
                errorCorreo = null
                alcance.launch {
                    try {
                        if (creandoCuenta) {
                            auth.createUserWithEmailAndPassword(correo.trim(), contrasena).await()
                            val usuario = auth.currentUser
                            if (usuario != null) {
                                usuario.updateProfile(
                                    userProfileChangeRequest { displayName = nombre.trim() }
                                ).await()
                                alRefrescarPerfil()
                            }
                            // El AuthStateListener refresca la sesión de la pestaña.
                        } else {
                            auth.signInWithEmailAndPassword(correo.trim(), contrasena).await()
                        }
                    } catch (e: Exception) {
                        errorCorreo = if (creandoCuenta) mensajeCrearCuenta(e) else mensajeAccesoCorreo(e)
                    } finally {
                        enviandoCorreo = false
                    }
                }
            },
            enabled = !enviandoCorreo && !googleCargando && correo.isNotBlank() && contrasena.isNotBlank() &&
                !recuperandoContrasena &&
                (!creandoCuenta || nombre.isNotBlank()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AmancayBordo,
                contentColor = Color.White
            )
        ) {
            if (enviandoCorreo) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text(
                    if (creandoCuenta) "Crear cuenta" else "Ingresar",
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (!creandoCuenta) {
            TextButton(
                onClick = {
                    if (recuperandoContrasena || enviandoCorreo || googleCargando) return@TextButton
                    recuperandoContrasena = true
                    errorCorreo = null
                    alcance.launch {
                        try {
                            AutenticacionCorreo.enviarEnlaceRestablecimiento(auth, correo)
                            errorCorreo = "Si existe una cuenta con ese correo, enviaremos un enlace para restablecer la contraseña. Revisa también el correo no deseado."
                        } catch (e: Exception) {
                            errorCorreo = when (e) {
                                is IllegalArgumentException -> "Ingresa un correo electrónico válido para enviar el enlace."
                                is FirebaseAuthInvalidCredentialsException -> "Revisa el formato del correo electrónico."
                                is FirebaseAuthInvalidUserException -> "Si existe una cuenta con ese correo, enviaremos un enlace para restablecer la contraseña. Revisa también el correo no deseado."
                                is FirebaseNetworkException -> "No hay conexión. Revisa internet e inténtalo nuevamente."
                                else -> "No se pudo enviar el enlace. Revisa el correo e inténtalo nuevamente."
                            }
                        } finally {
                            recuperandoContrasena = false
                        }
                    }
                },
                enabled = !recuperandoContrasena && !enviandoCorreo && !googleCargando,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
            ) {
                Text(
                    if (recuperandoContrasena) "Enviando enlace…" else "¿Olvidaste tu contraseña?",
                    color = AmancayBordo
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onGoogle(correo, contrasena) },
                enabled = !googleCargando && !enviandoCorreo && !recuperandoContrasena,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AmancayBordo),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmancayBordo)
            ) {
                if (googleCargando) {
                    CircularProgressIndicator(color = AmancayBordo, modifier = Modifier.size(22.dp))
                } else {
                    Text("Iniciar sesión con Google", fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Si administración creó tu cuenta, ingresa primero esas credenciales para vincular Google.",
                style = MaterialTheme.typography.bodySmall,
                color = AmancayTintaSuave,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = {
                creandoCuenta = !creandoCuenta
                errorCorreo = null
            },
            enabled = !enviandoCorreo && !googleCargando && !recuperandoContrasena,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(
                if (creandoCuenta) "¿Ya tienes cuenta? Iniciar sesión" else "¿No tienes cuenta? Crear una",
                color = AmancayBordo,
                fontWeight = FontWeight.SemiBold
            )
        }
        TextButton(
            onClick = alExplorar,
            enabled = !enviandoCorreo && !googleCargando && !recuperandoContrasena,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Explorar sin iniciar sesión", color = AmancayTintaSuave)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun campoAcceso() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = AmancayBordo,
    unfocusedBorderColor = AmancayBorde,
    focusedTextColor = AmancayTinta,
    unfocusedTextColor = AmancayTinta,
    cursorColor = AmancayBordo
)

fun mensajeAccesoCorreo(error: Exception): String {
    val texto = error.message.orEmpty()
    return when (error) {
        is FirebaseAuthInvalidUserException ->
            "No existe una cuenta con ese correo. Pide tu acceso a la administración o usa «Iniciar sesión con Google»."
        is FirebaseAuthInvalidCredentialsException ->
            if ("invalid-email" in texto || "ERROR_INVALID_EMAIL" in texto)
                "El correo no tiene un formato válido."
            else
                "El correo o la contraseña no son correctos."
        is FirebaseNetworkException, is java.io.IOException ->
            "No hay conexión. Revisa tu internet e inténtalo otra vez."
        else -> when {
            "invalid-email" in texto || "ERROR_INVALID_EMAIL" in texto ->
                "El correo no tiene un formato válido."
            "user-not-found" in texto || "ERROR_USER_NOT_FOUND" in texto ->
                "No existe una cuenta con ese correo. Pide tu acceso a la administración o usa «Iniciar sesión con Google»."
            "too many requests" in texto.lowercase() || "too-many-requests" in texto ->
                "Demasiados intentos seguidos. Espera unos minutos e inténtalo otra vez."
            "network" in texto ->
                "No hay conexión. Revisa tu internet e inténtalo otra vez."
            else -> "No se pudo iniciar sesión: ${texto.ifBlank { "motivo desconocido" }}"
        }
    }
}

fun mensajeCrearCuenta(error: Exception): String {
    val texto = error.message.orEmpty()
    return when (error) {
        is FirebaseAuthUserCollisionException ->
            "Ese correo ya tiene una cuenta. Inicia sesión con ella o usa «Iniciar sesión con Google»."
        is FirebaseAuthWeakPasswordException ->
            "La contraseña es muy corta: usa al menos 6 caracteres."
        is FirebaseAuthInvalidCredentialsException ->
            if ("invalid-email" in texto || "ERROR_INVALID_EMAIL" in texto)
                "El correo no tiene un formato válido."
            else
                "Los datos no son válidos."
        is FirebaseNetworkException, is java.io.IOException ->
            "No hay conexión. Revisa tu internet e inténtalo otra vez."
        else -> when {
            "email-already-in-use" in texto || "ERROR_EMAIL_ALREADY_IN_USE" in texto ->
                "Ese correo ya tiene una cuenta. Inicia sesión con ella o usa «Iniciar sesión con Google»."
            "weak-password" in texto ->
                "La contraseña es muy corta: usa al menos 6 caracteres."
            "invalid-email" in texto || "ERROR_INVALID_EMAIL" in texto ->
                "El correo no tiene un formato válido."
            "network" in texto ->
                "No hay conexión. Revisa tu internet e inténtalo otra vez."
            else -> "No se pudo crear la cuenta: ${texto.ifBlank { "motivo desconocido" }}"
        }
    }
}
