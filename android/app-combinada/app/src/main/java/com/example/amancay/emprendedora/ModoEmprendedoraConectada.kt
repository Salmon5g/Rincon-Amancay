package com.example.amancay.emprendedora

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.R
import com.example.amancay.EmprendedoraAppConectada
import com.example.amancay.data.ApiEmprendedoraClient
import com.example.amancay.data.AutenticacionCorreo
import com.example.amancay.data.AutenticacionGoogleComprador
import com.example.amancay.data.ContactoEmprendedora
import com.example.amancay.data.CuentaEmprendedora
import com.example.amancay.data.CuentaEmprendedoraRepository
import com.example.amancay.data.FirebaseClientServices
import com.example.amancay.data.OpcionCatalogoEmprendedora
import com.example.amancay.data.ProductosEmprendedoraRepository
import com.example.amancay.data.TiendaEmprendedora
import com.example.amancay.ui.Fig
import com.example.amancay.ui.BotanicalBackdrop
import com.example.amancay.ui.OutlineButton
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.example.amancay.ui.PrimaryButton
import com.example.amancay.ui.SelectableChip
import com.example.amancay.ui.theme.CardPink
import com.example.amancay.ui.theme.AccentRed
import com.example.amancay.ui.theme.Cream
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import com.example.amancay.data.ImagenProductoPreparada
import com.example.amancay.data.decodificarVistaPreviaProducto
import com.example.amancay.data.prepararImagenProducto
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ModoEmprendedoraConectada(firebase: FirebaseClientServices, webClientId: String, escalaTexto: Float, alCambiarEscalaTexto: (Float) -> Unit, alVolver: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val preferenciasAcceso = remember(context) {
        context.getSharedPreferences("amancay_emprendedora_acceso", Context.MODE_PRIVATE)
    }
    val uidRecordado = remember(firebase) { firebase.auth.currentUser?.uid }
    val huellaRecordada = remember(firebase) {
        uidRecordado?.let { preferenciasAcceso.getBoolean(claveHuella(it), false) } == true
    }
    val repository = remember(firebase) { CuentaEmprendedoraRepository(firebase.auth, firebase.firestore, ApiEmprendedoraClient(firebase.auth)) }
    val productosRepository = remember(firebase) { ProductosEmprendedoraRepository(firebase.firestore, ApiEmprendedoraClient(firebase.auth)) }
    val tienda by repository.tienda.collectAsState()
    val sectores by repository.sectores.collectAsState()
    val tipos by repository.tiposEmprendimiento.collectAsState()
    val scope = rememberCoroutineScope()
    var cuenta by remember { mutableStateOf<CuentaEmprendedora?>(null) }
    var cargandoCuenta by remember { mutableStateOf(true) }
    var enviando by remember { mutableStateOf(false) }
    var enviandoGoogle by remember { mutableStateOf(false) }
    var enviandoRecuperacion by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var huellaHabilitada by remember(firebase) { mutableStateOf(huellaRecordada) }
    var huellaValidada by remember(firebase) { mutableStateOf(!huellaRecordada) }
    var errorHuella by remember { mutableStateOf<String?>(null) }
    var confirmarRetiroTienda by remember { mutableStateOf(false) }

    DisposableEffect(repository) {
        onDispose { repository.cerrar() }
    }

    LaunchedEffect(repository, huellaValidada) {
        if (!huellaValidada) {
            cargandoCuenta = false
            return@LaunchedEffect
        }
        cargandoCuenta = true
        try {
            cuenta = repository.validarSesion()
            cuenta?.let { repository.observarTienda(it.tiendaId) }
        } catch (e: Exception) {
            error = e.message ?: "No se pudo comprobar el acceso."
        } finally {
            cargandoCuenta = false
        }
    }

    if (cargandoCuenta) {
        Box(Modifier.fillMaxSize().background(Cream), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Wine)
        }
        return
    }

    fun ejecutarPromptHuella(motivo: String, alConfirmar: () -> Unit) {
        val host = activity
        if (host == null) {
            errorHuella = "No se pudo abrir la verificación biométrica en este dispositivo."
            return
        }
        val disponibilidad = BiometricManager.from(host).canAuthenticate(BIOMETRIC_STRONG)
        if (disponibilidad != BiometricManager.BIOMETRIC_SUCCESS) {
            errorHuella = when (disponibilidad) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Este dispositivo no tiene un sensor biométrico compatible."
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Primero registra una huella o biometría en los ajustes del dispositivo."
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "El sensor biométrico no está disponible ahora."
                else -> "No se puede usar la biometría en este dispositivo."
            }
            return
        }
        errorHuella = null
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    errorHuella = null
                    alConfirmar()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        errorHuella = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    errorHuella = "No se reconoció la huella. Inténtalo nuevamente."
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Rincón Amancay")
            .setSubtitle(motivo)
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    val sesion = cuenta
    if (sesion == null && huellaRecordada && !huellaValidada) {
        com.example.amancay.ui.BloqueoHuellaScreen(
            error = errorHuella,
            onHuella = {
                ejecutarPromptHuella("Confirma tu huella para abrir tu sesión") {
                    if (firebase.auth.currentUser?.uid == uidRecordado) {
                        huellaValidada = true
                    } else {
                        preferenciasAcceso.edit().remove(claveHuella(uidRecordado.orEmpty())).apply()
                        repository.cerrarSesion()
                        huellaHabilitada = false
                        huellaValidada = true
                        errorHuella = null
                    }
                }
            },
            onSinHuella = {
                uidRecordado?.let { preferenciasAcceso.edit().remove(claveHuella(it)).apply() }
                repository.cerrarSesion()
                huellaHabilitada = false
                huellaValidada = true
                errorHuella = null
            }
        )
        return
    }
    if (sesion == null) {
        PantallaIngresoEmprendedora(
            correo = correo,
            contrasena = contrasena,
            error = error,
            aviso = aviso,
            enviando = enviando,
            googleCargando = enviandoGoogle,
            recuperacionCargando = enviandoRecuperacion,
            alCambiarCorreo = { correo = it },
            alCambiarContrasena = { contrasena = it },
            alRecuperarContrasena = {
                scope.launch {
                    enviandoRecuperacion = true
                    error = null
                    aviso = null
                    try {
                        AutenticacionCorreo.enviarEnlaceRestablecimiento(firebase.auth, correo)
                        aviso = "Si existe una cuenta con ese correo, enviaremos un enlace para restablecer la contraseña. Revisa también el correo no deseado."
                    } catch (e: Exception) {
                        error = when (e) {
                            is IllegalArgumentException, is FirebaseAuthInvalidCredentialsException ->
                                "Ingresa un correo electrónico válido para enviar el enlace."
                            is FirebaseAuthInvalidUserException ->
                                "Si existe una cuenta con ese correo, enviaremos un enlace para restablecer la contraseña. Revisa también el correo no deseado."
                            is FirebaseNetworkException -> "No hay conexión. Revisa internet e inténtalo nuevamente."
                            else -> "No se pudo enviar el enlace. Revisa el correo e inténtalo nuevamente."
                        }
                    } finally {
                        enviandoRecuperacion = false
                    }
                }
            },
            alVolver = alVolver,
            alIngresar = {
                scope.launch {
                    enviando = true
                    error = null
                    try {
                        val nueva = repository.iniciarSesion(correo, contrasena)
                        cuenta = nueva
                        huellaHabilitada = preferenciasAcceso.getBoolean(claveHuella(nueva.uid), false)
                        huellaValidada = true
                        repository.observarTienda(nueva.tiendaId)
                        contrasena = ""
                    } catch (e: Exception) {
                        error = e.message ?: "No se pudo iniciar sesión."
                    } finally {
                        enviando = false
                    }
                }
            },
            alGoogle = {
                enviandoGoogle = true
                error = null
                scope.launch {
                    try {
                        val idClienteWeb = AutenticacionGoogleComprador.idClienteWeb(context, webClientId)
                        val resultado = if (correo.isNotBlank() && contrasena.isNotBlank()) {
                            val cuentaConCredenciales = repository.iniciarSesion(correo, contrasena)
                            val vinculacion = AutenticacionGoogleComprador.vincularGoogleConCuentaActual(
                                context, firebase.auth, idClienteWeb, correo
                            )
                            if (vinculacion is com.example.amancay.data.ResultadoAccesoGoogle.Exito) {
                                vinculacion
                            } else {
                                cuenta = cuentaConCredenciales
                                huellaHabilitada = preferenciasAcceso.getBoolean(claveHuella(cuentaConCredenciales.uid), false)
                                huellaValidada = true
                                repository.observarTienda(cuentaConCredenciales.tiendaId)
                                aviso = when (vinculacion) {
                                    is com.example.amancay.data.ResultadoAccesoGoogle.Fallido ->
                                        "Ingresaste con tu correo y contraseña, pero Google no quedó vinculado: ${vinculacion.mensaje}"
                                    else -> "Ingresaste con tu correo y contraseña. No se vinculó Google; puedes seguir usando este acceso."
                                }
                                vinculacion
                            }
                        } else {
                            AutenticacionGoogleComprador.iniciarSesionConGoogle(context, firebase.auth, idClienteWeb)
                        }
                        when (resultado) {
                            is com.example.amancay.data.ResultadoAccesoGoogle.Exito -> {
                                val nueva = repository.validarSesion()
                                if (nueva == null) {
                                    error = "Tu cuenta de Google no tiene un emprendimiento registrado en Amancay. Usa las credenciales que te entregó la administración."
                                } else {
                                    cuenta = nueva
                                    huellaHabilitada = preferenciasAcceso.getBoolean(claveHuella(nueva.uid), false)
                                    huellaValidada = true
                                    repository.observarTienda(nueva.tiendaId)
                                }
                            }
                            is com.example.amancay.data.ResultadoAccesoGoogle.Fallido ->
                                if (cuenta == null) error = resultado.mensaje
                            else -> { /* el comprador cerró el panel de Google */ }
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "No se pudo iniciar sesión con Google."
                    } finally {
                        enviandoGoogle = false
                    }
                }
            }
        )
        return
    }

    DisposableEffect(productosRepository, sesion.tiendaId) {
        productosRepository.observar(sesion.tiendaId)
        onDispose { productosRepository.cerrar() }
    }

    if (confirmarRetiroTienda) {
        AlertDialog(
            onDismissRequest = { if (!enviando) confirmarRetiroTienda = false },
            title = { Text("¿Retirar la tienda?") },
            text = { Text("La tienda y sus productos dejarán de aparecer para compradores. Tus fichas privadas se conservarán.") },
            confirmButton = {
                Button(enabled = !enviando, onClick = {
                    scope.launch {
                        enviando = true; error = null; aviso = null
                        try {
                            repository.retirarTienda(sesion, tienda ?: sesion.tienda)
                            aviso = "Tienda retirada del catálogo público."
                            confirmarRetiroTienda = false
                        } catch (e: Exception) { error = e.message ?: "No se pudo retirar la tienda." }
                        finally { enviando = false }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Wine)) { Text("Retirar") }
            },
            dismissButton = { TextButton(onClick = { confirmarRetiroTienda = false }, enabled = !enviando, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", color = Wine) } }
        )
    }

    EmprendedoraAppConectada(
        sesion = sesion,
        tienda = tienda ?: sesion.tienda,
        sectores = sectores,
        tipos = tipos,
        productosRepository = productosRepository,
        huellaHabilitada = huellaHabilitada,
        aviso = aviso,
        error = error,
        errorHuella = errorHuella,
        enviando = enviando,
        alCambiarHuella = { activar ->
            if (!activar) {
                preferenciasAcceso.edit().remove(claveHuella(sesion.uid)).apply()
                huellaHabilitada = false
                aviso = "Acceso con huella desactivado en este dispositivo."
            } else {
                ejecutarPromptHuella("Confirma tu huella para activar el acceso") {
                    preferenciasAcceso.edit().putBoolean(claveHuella(sesion.uid), true).apply()
                    huellaHabilitada = true
                    aviso = "Acceso con huella activado en este dispositivo."
                }
            }
        },
        alEditarTienda = { propuesta ->
            scope.launch {
                enviando = true
                error = null
                aviso = null
                try {
                    repository.guardarTienda(sesion, propuesta)
                    limpiarBorradorFicha(context, sesion.uid)
                    aviso = "Ficha guardada. Si estaba publicada, la web ya recibió los cambios públicos."
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo guardar la ficha."
                } finally {
                    enviando = false
                }
            }
        },
        alSubirLogo = { archivoId, imagen ->
            repository.subirLogo(sesion, archivoId, imagen)
        },
        alPublicarTienda = {
            scope.launch {
                enviando = true
                error = null
                aviso = null
                try {
                    repository.publicarTienda(sesion, tienda ?: sesion.tienda)
                    aviso = "Tienda publicada en el catálogo comprador."
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo publicar la tienda."
                } finally {
                    enviando = false
                }
            }
        },
        alRetirarTienda = { confirmarRetiroTienda = true },
        alSalir = {
            preferenciasAcceso.edit().remove(claveHuella(sesion.uid)).apply()
            huellaHabilitada = false
            repository.cerrarSesion()
            cuenta = null
            huellaValidada = true
            aviso = null
            error = null
            alVolver()
        },
        escalaTexto = escalaTexto,
        alCambiarEscalaTexto = alCambiarEscalaTexto
    )
}

@Composable
private fun PantallaIngresoEmprendedora(
    correo: String,
    contrasena: String,
    error: String?,
    aviso: String?,
    enviando: Boolean,
    googleCargando: Boolean,
    recuperacionCargando: Boolean,
    alCambiarCorreo: (String) -> Unit,
    alCambiarContrasena: (String) -> Unit,
    alRecuperarContrasena: () -> Unit,
    alVolver: () -> Unit,
    alIngresar: () -> Unit,
    alGoogle: () -> Unit
) {
    var mostrarContrasena by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        BotanicalBackdrop()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
                TextButton(onClick = alVolver, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("←  Volver", color = Wine, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Image(painterResource(Fig.LOGO), contentDescription = "Rincón Amancay", modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Rincón", fontSize = 14.sp, color = Muted)
                    Text("Amancay", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Wine, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(64.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("ESPACIO DE EMPRENDIMIENTO", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentRed, letterSpacing = 2.2.sp)
            Text("Ingresa a\ntu espacio", fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, color = Wine, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Text("Administra tu tienda desde aquí.", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    AmancayInput("Correo electrónico", correo, alCambiarCorreo)
                    Spacer(Modifier.height(12.dp))
                    Text("Contraseña", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark, modifier = Modifier.padding(bottom = 6.dp))
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = alCambiarContrasena,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Tu contraseña", color = Muted) },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = Wine) },
                        trailingIcon = {
                            IconButton(onClick = { mostrarContrasena = !mostrarContrasena }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                                Icon(if (mostrarContrasena) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (mostrarContrasena) "Ocultar contraseña" else "Mostrar contraseña", tint = Wine)
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (mostrarContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(18.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Wine,
                            unfocusedBorderColor = Line,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            cursorColor = Wine,
                            focusedTextColor = Dark,
                            unfocusedTextColor = Dark
                        )
                    )
                    TextButton(
                        onClick = alRecuperarContrasena,
                        enabled = !enviando && !googleCargando && !recuperacionCargando,
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    ) {
                        Text(if (recuperacionCargando) "Enviando enlace…" else "¿Olvidaste tu contraseña?", color = Wine)
                    }
                    aviso?.let { AvisoEmprendedora(it, false) }
                    error?.let { AvisoEmprendedora(it, true) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = alIngresar,
                        enabled = !enviando && !googleCargando && !recuperacionCargando && correo.isNotBlank() && contrasena.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Wine),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp)
                    ) {
                        if (enviando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            Text("Ingresar", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlineButton(
                        "Iniciar sesión con Google",
                        alGoogle,
                        enabled = !googleCargando && !enviando && !recuperacionCargando
                    )
                }
            }
            Text(
                "Si administración creó tu cuenta, ingresa primero esas credenciales para vincular Google.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            Text("HECHO CON CARIÑO, CERCA DE TI", fontSize = 11.sp, color = Wine, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp, bottom = 12.dp))
        }
    }
}

private fun claveHuella(uid: String): String = "huella_emprendedora_$uid"

internal data class BorradorFicha(
    val nombre: String,
    val descripcion: String,
    val sectorId: String,
    val tipoId: String,
    val direccion: String,
    val contactoTipo: String,
    val contactoValor: String,
    val mostrarContacto: Boolean
)

private const val PREFS_BORRADOR_FICHA = "amancay_borrador_tienda"
private const val PREFS_LOGO_TIENDA = "amancay_logo_tienda"
private const val CLAVE_BORRADOR_FICHA = "borrador_ficha_"
private const val CLAVE_LOGO_TIENDA = "logo_ficha_"

internal fun guardarBorradorFicha(context: Context, uid: String, borrador: BorradorFicha) {
    if (uid.isBlank()) return
    val json = JSONObject()
        .put("nombre", borrador.nombre)
        .put("descripcion", borrador.descripcion)
        .put("sectorId", borrador.sectorId)
        .put("tipoId", borrador.tipoId)
        .put("direccion", borrador.direccion)
        .put("contactoTipo", borrador.contactoTipo)
        .put("contactoValor", borrador.contactoValor)
        .put("mostrarContacto", borrador.mostrarContacto)
    context.getSharedPreferences(PREFS_BORRADOR_FICHA, Context.MODE_PRIVATE)
        .edit().putString("$CLAVE_BORRADOR_FICHA$uid", json.toString()).apply()
}

internal fun leerBorradorFicha(context: Context, uid: String): BorradorFicha? {
    if (uid.isBlank()) return null
    val texto = context.getSharedPreferences(PREFS_BORRADOR_FICHA, Context.MODE_PRIVATE)
        .getString("$CLAVE_BORRADOR_FICHA$uid", null) ?: return null
    val json = runCatching { JSONObject(texto) }.getOrNull() ?: return null
    return BorradorFicha(
        nombre = json.optString("nombre"),
        descripcion = json.optString("descripcion"),
        sectorId = json.optString("sectorId"),
        tipoId = json.optString("tipoId"),
        direccion = json.optString("direccion"),
        contactoTipo = json.optString("contactoTipo").ifBlank { "whatsapp" },
        contactoValor = json.optString("contactoValor"),
        mostrarContacto = json.optBoolean("mostrarContacto", false)
    ).takeIf { it.nombre.isNotBlank() || it.descripcion.isNotBlank() || it.direccion.isNotBlank() || it.contactoValor.isNotBlank() }
}

internal fun limpiarBorradorFicha(context: Context, uid: String) {
    if (uid.isBlank()) return
    context.getSharedPreferences(PREFS_BORRADOR_FICHA, Context.MODE_PRIVATE)
        .edit().remove("$CLAVE_BORRADOR_FICHA$uid").apply()
}

/** Copia local comprimida del logo para poder mostrarlo en próximas sesiones (el original queda privado en Storage). */
internal fun guardarLogoLocal(context: Context, uid: String, bitmap: Bitmap) {
    if (uid.isBlank()) return
    val escala = minOf(1f, 512f / max(bitmap.width, bitmap.height))
    val copia = if (escala < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * escala).roundToInt().coerceAtLeast(1), (bitmap.height * escala).roundToInt().coerceAtLeast(1), true)
    } else bitmap
    val salida = ByteArrayOutputStream()
    if (!copia.compress(Bitmap.CompressFormat.JPEG, 85, salida)) return
    val base64 = Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
    context.getSharedPreferences(PREFS_LOGO_TIENDA, Context.MODE_PRIVATE)
        .edit().putString("$CLAVE_LOGO_TIENDA$uid", base64).apply()
}

/** Devuelve la copia local solo si la ficha todavía conserva el logo guardado. */
internal fun leerLogoLocalTienda(context: Context, uid: String, logoGuardado: Boolean): Bitmap? {
    if (uid.isBlank() || !logoGuardado) return null
    val base64 = context.getSharedPreferences(PREFS_LOGO_TIENDA, Context.MODE_PRIVATE)
        .getString("$CLAVE_LOGO_TIENDA$uid", null) ?: return null
    return runCatching {
        Base64.decode(base64, Base64.NO_WRAP).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }.getOrNull()
}

internal fun crearUriFotoTemporalTienda(context: Context): Uri {
    val directorio = File(context.cacheDir, "tienda-fotos")
    check(directorio.exists() || directorio.mkdirs()) { "No se pudo preparar el almacenamiento temporal de la cámara." }
    val archivo = File.createTempFile("amancay-tienda-", ".jpg", directorio)
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    } catch (error: Exception) {
        archivo.delete()
        throw error
    }
}

internal fun eliminarFotoTemporalTienda(context: Context, uri: Uri?) {
    if (uri?.authority != "${context.packageName}.fileprovider") return
    val nombre = uri.lastPathSegment ?: return
    if (!nombre.matches(Regex("amancay-tienda-[A-Za-z0-9_-]+\\.jpg"))) return
    File(File(context.cacheDir, "tienda-fotos"), nombre).delete()
}

internal suspend fun decodificarLogoDesdeUri(contentResolver: android.content.ContentResolver, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                val escala = minOf(1f, 1600f / max(info.size.width, info.size.height))
                if (escala < 1f) {
                    decoder.setTargetSize(
                        (info.size.width * escala).roundToInt().coerceAtLeast(1),
                        (info.size.height * escala).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        }.getOrNull()
    }

/** Repite las validaciones del backend para avisar antes errores comunes de contacto. */
internal fun validarContactoTienda(tipo: String, valor: String): String? {
    val texto = valor.trim()
    if (texto.isEmpty()) return null
    return when (tipo) {
        "whatsapp" -> if (Regex("^\\+?[0-9][0-9\\s()-]{6,22}$").matches(texto)) null
        else "WhatsApp debe incluir un número válido con código de país."
        "correo" -> if (Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(texto)) null
        else "Correo de contacto inválido."
        "web" -> if (runCatching { java.net.URL(texto) }.getOrNull()?.protocol == "https" && texto.startsWith("https://")) null
        else "El sitio de contacto debe ser HTTPS."
        else -> null
    }
}

@Composable
internal fun PantallaEdicionTienda(
    cuenta: CuentaEmprendedora,
    tienda: TiendaEmprendedora,
    sectores: List<OpcionCatalogoEmprendedora>,
    tipos: List<OpcionCatalogoEmprendedora>,
    enviando: Boolean,
    alSubirLogo: suspend (archivoId: String, imagen: ImagenProductoPreparada) -> Unit,
    alGuardar: (TiendaEmprendedora) -> Unit,
    alCancelar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val borradorRestaurado = remember(cuenta.uid, tienda.versionEsperada) { leerBorradorFicha(context, cuenta.uid) }
    var nombre by remember(cuenta.uid, tienda.versionEsperada) { mutableStateOf(borradorRestaurado?.nombre ?: tienda.nombre) }
    var descripcion by remember(cuenta.uid, tienda.versionEsperada) { mutableStateOf(borradorRestaurado?.descripcion ?: tienda.descripcion) }
    var sectorId by remember(cuenta.uid, tienda.versionEsperada) { mutableStateOf(borradorRestaurado?.sectorId ?: tienda.sectorId) }
    var tipoId by remember(cuenta.uid, tienda.versionEsperada) { mutableStateOf(borradorRestaurado?.tipoId ?: tienda.tipoEmprendimientoId) }
    var direccion by remember(cuenta.uid, tienda.versionEsperada) { mutableStateOf(borradorRestaurado?.direccion ?: tienda.direccion) }
    var contactoTipo by remember(cuenta.uid, tienda.versionEsperada) { mutableStateOf(borradorRestaurado?.contactoTipo ?: tienda.contacto?.tipo ?: "whatsapp") }
    var contactoValor by remember(cuenta.uid, tienda.versionEsperada) { mutableStateOf(borradorRestaurado?.contactoValor ?: tienda.contacto?.valor.orEmpty()) }
    var mostrarContacto by remember(cuenta.uid, tienda.versionEsperada) { mutableStateOf(borradorRestaurado?.mostrarContacto ?: tienda.mostrarContacto) }

    // Foto o logo: previsualización nueva, copia local guardada y envío a la API.
    var logoEnviando by remember { mutableStateOf(false) }
    var logoLocal by remember(cuenta.uid, tienda.logo) { mutableStateOf(leerLogoLocalTienda(context, cuenta.uid, tienda.logo != null)) }
    var logoNuevoUri by remember { mutableStateOf<Uri?>(null) }
    var logoNuevo by remember { mutableStateOf<Bitmap?>(null) }
    var archivoIdLogo by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var avisoLogo by remember { mutableStateOf<String?>(null) }
    var errorLogo by remember { mutableStateOf<String?>(null) }
    var capturaPendienteLogo by remember { mutableStateOf<String?>(null) }

    val selectorGaleriaLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            eliminarFotoTemporalTienda(context, logoNuevoUri)
            logoNuevoUri = uri
            archivoIdLogo = java.util.UUID.randomUUID().toString()
            logoNuevo = null
            avisoLogo = null
            errorLogo = null
            scope.launch { logoNuevo = decodificarLogoDesdeUri(context.contentResolver, uri) }
        }
    }
    val selectorCamaraLogo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { guardada ->
        val uri = capturaPendienteLogo?.let(Uri::parse)
        capturaPendienteLogo = null
        if (guardada && uri != null) {
            eliminarFotoTemporalTienda(context, logoNuevoUri)
            logoNuevoUri = uri
            archivoIdLogo = java.util.UUID.randomUUID().toString()
            logoNuevo = null
            avisoLogo = null
            errorLogo = null
            scope.launch { logoNuevo = decodificarLogoDesdeUri(context.contentResolver, uri) }
        } else {
            eliminarFotoTemporalTienda(context, uri)
        }
    }
    fun subirLogoAhora() {
        val uri = logoNuevoUri ?: return
        scope.launch {
            logoEnviando = true
            errorLogo = null
            avisoLogo = null
            try {
                val preparada = prepararImagenProducto(context.contentResolver, uri, null)
                alSubirLogo(archivoIdLogo, preparada)
                val miniatura = decodificarVistaPreviaProducto(preparada.bytes) ?: logoNuevo
                miniatura?.let { guardarLogoLocal(context, cuenta.uid, it) }
                logoLocal = miniatura ?: logoLocal
                logoNuevo = null
                logoNuevoUri = null
                eliminarFotoTemporalTienda(context, uri)
                avisoLogo = if (tienda.estadoPublicacion == "publicado") "Logo guardado: la web ya recibió la copia pública." else "Logo guardado en tu ficha. La copia pública se genera al publicar la tienda."
            } catch (e: Exception) {
                errorLogo = e.message ?: "No se pudo subir el logo. Inténtalo nuevamente."
            } finally {
                logoEnviando = false
            }
        }
    }

    // Conservar borrador al cerrar la app: se guarda con cada cambio y se limpia cuando vuelve a coincidir con la ficha guardada.
    LaunchedEffect(nombre, descripcion, sectorId, tipoId, direccion, contactoTipo, contactoValor, mostrarContacto) {
        val difiere = nombre != tienda.nombre ||
            descripcion != tienda.descripcion ||
            sectorId != tienda.sectorId ||
            tipoId != tienda.tipoEmprendimientoId ||
            direccion != tienda.direccion ||
            contactoTipo != (tienda.contacto?.tipo ?: "whatsapp") ||
            contactoValor != tienda.contacto?.valor.orEmpty() ||
            mostrarContacto != tienda.mostrarContacto
        if (difiere) {
            guardarBorradorFicha(context, cuenta.uid, BorradorFicha(nombre, descripcion, sectorId, tipoId, direccion, contactoTipo, contactoValor, mostrarContacto))
        } else {
            limpiarBorradorFicha(context, cuenta.uid)
        }
    }

    val errorContacto = validarContactoTienda(contactoTipo, contactoValor)

    Text("MI TIENDA", fontSize = 12.sp, color = Wine, fontWeight = FontWeight.Bold)
    Text("Mi tienda", fontSize = 32.sp, color = Dark, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
    Text("Los cambios de nombre, descripción, sector, tipo y contacto se reflejan en la proyección pública si la tienda ya está publicada.", fontSize = 15.sp, color = Muted)
    Spacer(Modifier.height(16.dp))
    if (borradorRestaurado != null) {
        AvisoEmprendedora("Se conservó el borrador de tu ficha al cerrar la app. Continúa donde lo dejaste o guarda los cambios.", false)
    }
    Spacer(Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
    ) {
    Column(Modifier.padding(14.dp)) {
    Text("Foto o logo de la tienda", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Dark)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val logoMostrar = logoNuevo ?: logoLocal
        if (logoMostrar != null) {
            Image(
                logoMostrar.asImageBitmap(),
                contentDescription = "Logo de la tienda",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardPink),
                contentAlignment = Alignment.Center
            ) {
                Text("Sin logo", fontSize = 12.sp, color = Muted)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (tienda.logo != null) "Tu logo está guardado en la ficha y protegido mientras la tienda no se publica."
                else "Puedes usar la foto o el logo del emprendimiento: se guarda en privado hasta publicar la tienda.",
                fontSize = 13.sp, color = Muted
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Galería", { selectorGaleriaLogo.launch("image/*") })
                OutlineButton("Cámara", {
                    runCatching { crearUriFotoTemporalTienda(context) }.fold(
                        onSuccess = { uri ->
                            capturaPendienteLogo = uri.toString()
                            selectorCamaraLogo.launch(uri)
                        },
                        onFailure = { errorLogo = "No se pudo abrir la cámara. Inténtalo nuevamente." }
                    )
                })
            }
            if (logoNuevoUri != null) {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = if (logoEnviando) "Subiendo logo…" else "Guardar logo",
                    onClick = ::subirLogoAhora,
                    enabled = !logoEnviando && !enviando
                )
            }
            avisoLogo?.let { AvisoEmprendedora(it, false) }
            errorLogo?.let { AvisoEmprendedora(it, true) }
            if (tienda.logo != null && logoLocal == null && logoNuevo == null) {
                AvisoEmprendedora("Tu logo está guardado en la ficha. Vuelve a subirlo para verlo aquí en este dispositivo.", false)
            }
        }
    }
    }
    }
    Spacer(Modifier.height(16.dp))

    AmancayInput("Nombre de la tienda", nombre, { nombre = it }, 120)
    Spacer(Modifier.height(12.dp))
    AmancayInput("Descripción", descripcion, { descripcion = it }, 2000, multiline = true)
    Spacer(Modifier.height(12.dp))
    SelectorCatalogo("Tipo de emprendimiento", tipos, tipoId, { tipoId = it })
    Spacer(Modifier.height(12.dp))
    AmancayInput("Dirección de la tienda", direccion, { direccion = it }, 200)
    Spacer(Modifier.height(16.dp))
    // Propuesta para el equipo (zona + mapa): bloque aislado y destacado para poder
    // presentarla y quitarla completo si se descarta. Lo que se usa hoy es la dirección.
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(2.dp, Wine, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        SelectorCatalogo(
            "Zona de la tienda (sector)",
            listOf(OpcionCatalogoEmprendedora("", "Ninguno")) + sectores,
            sectorId,
            { sectorId = it }
        )
        MapaZonaVisual(sectorId.takeIf(String::isNotBlank))
        if (sectorId.isBlank()) {
            Text(
                "Por ahora, para guardar la ficha necesitas elegir una zona de la lista. La opción \"Ninguno\" estará disponible más adelante.",
                fontSize = 13.sp,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Idea en evaluación con el equipo: ubicar la tienda por zona con este mapa de referencia. Mientras se decide, la tienda muestra la dirección escrita arriba.",
            fontSize = 12.sp,
            color = Muted,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
    Spacer(Modifier.height(12.dp))
    Text("Contacto", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Dark)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        listOf("whatsapp" to "WhatsApp", "correo" to "Correo", "web" to "Sitio web").forEach { (id, label) ->
            SelectableChip(text = label, selected = contactoTipo == id) { contactoTipo = id }
        }
    }
    Spacer(Modifier.height(8.dp))
    AmancayInput("Dato de contacto", contactoValor, { contactoValor = it }, 254)
    errorContacto?.let {
        Text(it, color = AccentRed, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = mostrarContacto,
            onCheckedChange = { mostrarContacto = it },
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        )
        Text("Mostrar este contacto en la tienda pública", color = Dark)
    }
    Spacer(Modifier.height(8.dp))
    OutlineButton("Volver a configuración", alCancelar)
    Spacer(Modifier.height(8.dp))
    PrimaryButton(
        text = if (enviando) "Guardando…" else "Guardar ficha",
        onClick = {
            val contacto = contactoValor.trim().takeIf(String::isNotEmpty)?.let { ContactoEmprendedora(contactoTipo, it) }
            alGuardar(tienda.copy(nombre = nombre.trim(), descripcion = descripcion.trim(), sectorId = sectorId, tipoEmprendimientoId = tipoId, direccion = direccion.trim(), contacto = contacto, mostrarContacto = mostrarContacto))
        },
        enabled = !enviando && !logoEnviando && nombre.isNotBlank() && sectorId.isNotBlank() && tipoId.isNotBlank() && direccion.isNotBlank() &&
            (!mostrarContacto || contactoValor.isNotBlank()) && errorContacto == null,
        fontSize = 16.sp
    )
    Text("Tienda asignada: ${cuenta.tiendaId}", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AmancayInput(label: String, value: String, onValueChange: (String) -> Unit, maxLength: Int = 254, multiline: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = if (multiline) 3 else 1,
        singleLine = !multiline,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Wine,
            unfocusedBorderColor = Line,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            cursorColor = Wine,
            focusedTextColor = Dark,
            unfocusedTextColor = Dark
        )
    )
}

@Composable
private fun SelectorCatalogo(label: String, opciones: List<OpcionCatalogoEmprendedora>, seleccionado: String, onSelected: (String) -> Unit) {
    var expandido by remember { mutableStateOf(false) }
    val nombre = opciones.firstOrNull { it.id == seleccionado }?.nombre ?: "Seleccionar"
    Column {
        Text(label, fontWeight = FontWeight.Bold, color = Dark)
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedButton(
                onClick = { expandido = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = opciones.isNotEmpty(),
                border = androidx.compose.foundation.BorderStroke(2.dp, Wine),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Wine),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
            ) {
                Text(if (opciones.isEmpty()) "No hay opciones activas" else nombre, color = Wine)
            }
            DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                opciones.forEach { opcion ->
                    DropdownMenuItem(text = { Text(opcion.nombre) }, onClick = { onSelected(opcion.id); expandido = false })
                }
            }
        }
    }

}

@Composable
internal fun AvisoEmprendedora(texto: String, esError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = if (esError) AccentRed.copy(alpha = 0.14f) else CardPink),
        shape = RoundedCornerShape(16.dp)
    ) { Text(texto, modifier = Modifier.padding(12.dp), color = if (esError) AccentRed else Dark, style = MaterialTheme.typography.bodyMedium) }
}
