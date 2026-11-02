package com.example.amancay.emprendedora

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.R
import com.example.amancay.EmprendedoraAppConectada
import com.example.amancay.data.ApiEmprendedoraClient
import com.example.amancay.data.AutenticacionGoogleComprador
import com.example.amancay.data.ContactoEmprendedora
import com.example.amancay.data.CuentaEmprendedora
import com.example.amancay.data.CuentaEmprendedoraRepository
import com.example.amancay.data.FirebaseClientServices
import com.example.amancay.data.OpcionCatalogoEmprendedora
import com.example.amancay.data.ProductosEmprendedoraRepository
import com.example.amancay.data.TiendaEmprendedora
import com.example.amancay.ui.Fig
import com.example.amancay.ui.OutlineButton
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

@Composable
fun ModoEmprendedoraConectada(firebase: FirebaseClientServices, webClientId: String, alVolver: () -> Unit) {
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
            enviando = enviando,
            googleCargando = enviandoGoogle,
            alCambiarCorreo = { correo = it },
            alCambiarContrasena = { contrasena = it },
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
                        val resultado = AutenticacionGoogleComprador.iniciarSesionConGoogle(
                            context, firebase.auth, AutenticacionGoogleComprador.idClienteWeb(context, webClientId)
                        )
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
                                error = resultado.mensaje
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
            dismissButton = { TextButton(onClick = { confirmarRetiroTienda = false }, enabled = !enviando) { Text("Cancelar", color = Wine) } }
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
                    aviso = "Ficha guardada. Si estaba publicada, la web ya recibió los cambios públicos."
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo guardar la ficha."
                } finally {
                    enviando = false
                }
            }
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
        }
    )
}

@Composable
private fun PantallaIngresoEmprendedora(
    correo: String,
    contrasena: String,
    error: String?,
    enviando: Boolean,
    googleCargando: Boolean,
    alCambiarCorreo: (String) -> Unit,
    alCambiarContrasena: (String) -> Unit,
    alVolver: () -> Unit,
    alIngresar: () -> Unit,
    alGoogle: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Image(painterResource(Fig.LOGO), contentDescription = null, modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text("Rincón Amancay", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Wine, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Ingresa con las credenciales que te entregó la administración.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
        AmancayInput("Correo electrónico", correo, alCambiarCorreo)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = contrasena,
            onValueChange = alCambiarContrasena,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
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
        error?.let { AvisoEmprendedora(it, true) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = alIngresar,
            enabled = !enviando && correo.isNotBlank() && contrasena.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Wine),
            shape = RoundedCornerShape(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp)
        ) {
            if (enviando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp)) else Text("Ingresar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlineButton(
            "Iniciar sesión con Google",
            alGoogle,
            enabled = !googleCargando && !enviando
        )
        TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver al modo comprador", color = Wine) }
    }
}

private fun claveHuella(uid: String): String = "huella_emprendedora_$uid"

@Composable
internal fun PantallaEdicionTienda(
    cuenta: CuentaEmprendedora,
    tienda: TiendaEmprendedora,
    sectores: List<OpcionCatalogoEmprendedora>,
    tipos: List<OpcionCatalogoEmprendedora>,
    enviando: Boolean,
    alGuardar: (TiendaEmprendedora) -> Unit,
    alCancelar: () -> Unit
) {
    var nombre by remember(tienda.versionEsperada) { mutableStateOf(tienda.nombre) }
    var descripcion by remember(tienda.versionEsperada) { mutableStateOf(tienda.descripcion) }
    var sectorId by remember(tienda.versionEsperada) { mutableStateOf(tienda.sectorId) }
    var tipoId by remember(tienda.versionEsperada) { mutableStateOf(tienda.tipoEmprendimientoId) }
    var contactoTipo by remember(tienda.versionEsperada) { mutableStateOf(tienda.contacto?.tipo ?: "whatsapp") }
    var contactoValor by remember(tienda.versionEsperada) { mutableStateOf(tienda.contacto?.valor.orEmpty()) }
    var mostrarContacto by remember(tienda.versionEsperada) { mutableStateOf(tienda.mostrarContacto) }

    Text("MI TIENDA", fontSize = 12.sp, color = Wine, fontWeight = FontWeight.Bold)
    Text("Ficha del emprendimiento", fontSize = 30.sp, color = Dark)
    Text("Los cambios de nombre, sector y tipo se reflejan en la proyección pública si la tienda ya está publicada.", fontSize = 15.sp, color = Muted)
    Spacer(Modifier.height(16.dp))
    AmancayInput("Nombre de la tienda", nombre, { nombre = it }, 120)
    Spacer(Modifier.height(12.dp))
    AmancayInput("Descripción", descripcion, { descripcion = it }, 2000, multiline = true)
    Spacer(Modifier.height(12.dp))
    SelectorCatalogo("Tipo de emprendimiento", tipos, tipoId, { tipoId = it })
    Spacer(Modifier.height(12.dp))
    SelectorCatalogo("Sector", sectores, sectorId, { sectorId = it })
    Spacer(Modifier.height(16.dp))
    Text("Contacto", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Dark)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        listOf("whatsapp" to "WhatsApp", "correo" to "Correo", "web" to "Sitio web").forEach { (id, label) ->
            SelectableChip(text = label, selected = contactoTipo == id) { contactoTipo = id }
        }
    }
    Spacer(Modifier.height(8.dp))
    AmancayInput("Dato de contacto", contactoValor, { contactoValor = it }, 254)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = mostrarContacto, onCheckedChange = { mostrarContacto = it })
        Text("Mostrar este contacto en la tienda pública", color = Dark)
    }
    Spacer(Modifier.height(8.dp))
    AvisoEmprendedora("La carga de foto/logo se habilitará cuando el grupo defina el acceso seguro a Supabase Storage.", false)
    Spacer(Modifier.height(10.dp))
    OutlineButton("Volver a configuración", alCancelar)
    Spacer(Modifier.height(8.dp))
    PrimaryButton(
        text = if (enviando) "Guardando…" else "Guardar ficha",
        onClick = {
            val contacto = contactoValor.trim().takeIf(String::isNotEmpty)?.let { ContactoEmprendedora(contactoTipo, it) }
            alGuardar(tienda.copy(nombre = nombre.trim(), descripcion = descripcion.trim(), sectorId = sectorId, tipoEmprendimientoId = tipoId, contacto = contacto, mostrarContacto = mostrarContacto))
        },
        enabled = !enviando && nombre.isNotBlank() && sectorId.isNotBlank() && tipoId.isNotBlank() && (!mostrarContacto || contactoValor.isNotBlank()),
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
                modifier = Modifier.fillMaxWidth(),
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
