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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.R
import com.example.amancay.data.ApiEmprendedoraClient
import com.example.amancay.data.ContactoEmprendedora
import com.example.amancay.data.CuentaEmprendedora
import com.example.amancay.data.CuentaEmprendedoraRepository
import com.example.amancay.data.FirebaseClientServices
import com.example.amancay.data.OpcionCatalogoEmprendedora
import com.example.amancay.data.ProductosEmprendedoraRepository
import com.example.amancay.data.TiendaEmprendedora
import com.example.amancay.ui.Fig
import com.example.amancay.ui.theme.CardPink
import com.example.amancay.ui.theme.Cream
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

private enum class SeccionEmprendedora { INICIO, TIENDA, PRODUCTOS }

@Composable
fun ModoEmprendedoraConectada(firebase: FirebaseClientServices, alVolver: () -> Unit) {
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
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var huellaHabilitada by remember(firebase) { mutableStateOf(huellaRecordada) }
    var huellaValidada by remember(firebase) { mutableStateOf(!huellaRecordada) }
    var errorHuella by remember { mutableStateOf<String?>(null) }
    var seccion by remember { mutableStateOf(SeccionEmprendedora.INICIO) }
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
        PantallaDesbloqueoEmprendedora(
            error = errorHuella,
            alDesbloquear = {
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
            alUsarContrasena = {
                uidRecordado?.let { preferenciasAcceso.edit().remove(claveHuella(it)).apply() }
                repository.cerrarSesion()
                huellaHabilitada = false
                huellaValidada = true
                errorHuella = null
            },
            alVolver = alVolver
        )
        return
    }
    if (sesion == null) {
        PantallaIngresoEmprendedora(
            correo = correo,
            contrasena = contrasena,
            error = error,
            enviando = enviando,
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
            }
        )
        return
    }

    DisposableEffect(productosRepository, sesion.tiendaId) {
        productosRepository.observar(sesion.tiendaId)
        onDispose { productosRepository.cerrar() }
    }

    Scaffold(
        containerColor = Cream,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Fig.LOGO), contentDescription = "Rincón Amancay", modifier = Modifier.size(42.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rincón", fontSize = 12.sp, color = Muted)
                    Text("Amancay", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Wine)
                }
                TextButton(onClick = {
                    preferenciasAcceso.edit().remove(claveHuella(sesion.uid)).apply()
                    repository.cerrarSesion()
                    cuenta = null
                    huellaHabilitada = false
                    huellaValidada = true
                    seccion = SeccionEmprendedora.INICIO
                    aviso = null
                    error = null
                    alVolver()
                }) { Text("Salir", color = Wine, fontWeight = FontWeight.Bold) }
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(SeccionEmprendedora.INICIO to "Inicio", SeccionEmprendedora.TIENDA to "Mi tienda", SeccionEmprendedora.PRODUCTOS to "Productos").forEach { (item, label) ->
                    TextButton(onClick = { seccion = item }) {
                        Text(label, color = if (seccion == item) Wine else Muted, fontWeight = if (seccion == item) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    ) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            aviso?.let { AvisoEmprendedora(it, false) }
            error?.let { AvisoEmprendedora(it, true) }
            when (seccion) {
                SeccionEmprendedora.INICIO -> {
                    Text("MI ESPACIO", fontSize = 12.sp, color = Wine, fontWeight = FontWeight.Bold)
                    Text("Hola, ${sesion.nombreMostrar.ifBlank { "emprendedora" }}", fontSize = 28.sp, color = Dark)
                    Text("Tu tienda se guarda en el mismo Firebase que usa la web.", color = Muted)
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text("Acceso con huella", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Dark)
                            Text(
                                if (huellaHabilitada) "Activado en este dispositivo. Al volver a entrar al modo emprendedora, confirma tu identidad con biometría."
                                else "Puedes usar la biometría de este dispositivo para abrir la sesión que Firebase mantiene guardada.",
                                color = Muted
                            )
                            Spacer(Modifier.height(10.dp))
                            if (huellaHabilitada) {
                                OutlinedButton(
                                    onClick = {
                                        preferenciasAcceso.edit().remove(claveHuella(sesion.uid)).apply()
                                        huellaHabilitada = false
                                        aviso = "Acceso con huella desactivado en este dispositivo."
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("Desactivar huella", color = Wine) }
                            } else {
                                Button(
                                    onClick = {
                                        ejecutarPromptHuella("Confirma tu huella para activar el acceso") {
                                            preferenciasAcceso.edit().putBoolean(claveHuella(sesion.uid), true).apply()
                                            huellaHabilitada = true
                                            aviso = "Acceso con huella activado en este dispositivo."
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Wine),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("Activar huella") }
                            }
                            errorHuella?.let { Text(it, color = Wine, modifier = Modifier.padding(top = 8.dp)) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                        Column(Modifier.padding(18.dp)) {
                            Text(tienda?.nombre ?: sesion.tienda.nombre, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Dark)
                            val ficha = tienda ?: sesion.tienda
                            Text(if (ficha.estadoPublicacion == "publicado") "Tienda publicada" else "Tienda en borrador", color = Muted)
                            Text("Catálogo compartido con la web comprador.", color = Muted)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { seccion = SeccionEmprendedora.TIENDA }, colors = ButtonDefaults.buttonColors(containerColor = Wine), shape = RoundedCornerShape(14.dp)) {
                                Text("Editar ficha de tienda")
                            }
                            Spacer(Modifier.height(8.dp))
                            if (ficha.estadoPublicacion == "publicado") {
                                OutlinedButton(onClick = { confirmarRetiroTienda = true }, enabled = !enviando, shape = RoundedCornerShape(14.dp)) { Text("Retirar tienda", color = Wine) }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            enviando = true; error = null; aviso = null
                                            try {
                                                repository.publicarTienda(sesion, ficha)
                                                aviso = "Tienda publicada en el catálogo comprador."
                                            } catch (e: Exception) { error = e.message ?: "No se pudo publicar la tienda." }
                                            finally { enviando = false }
                                        }
                                    },
                                    enabled = !enviando && ficha.nombre.isNotBlank() && ficha.sectorId.isNotBlank() && ficha.tipoEmprendimientoId.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Wine), shape = RoundedCornerShape(14.dp)
                                ) { Text(if (enviando) "Publicando…" else "Publicar mi tienda") }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    AvisoEmprendedora("La tienda puede publicarse sin imagen por ahora. La carga segura de logo está pendiente de Storage.", false)
                }
                SeccionEmprendedora.TIENDA -> PantallaEdicionTienda(
                    cuenta = sesion,
                    tienda = tienda ?: sesion.tienda,
                    sectores = sectores,
                    tipos = tipos,
                    enviando = enviando,
                    alGuardar = { propuesta ->
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
                    }
                )
                SeccionEmprendedora.PRODUCTOS -> {
                    ProductosEmprendedoraPanel(
                        tiendaId = sesion.tiendaId,
                        tiendaPublicada = (tienda ?: sesion.tienda).estadoPublicacion == "publicado",
                        repository = productosRepository,
                        enviando = enviando,
                        alEnviando = { enviando = it },
                        alError = { error = it },
                        alAviso = { aviso = it }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
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
}

@Composable
private fun PantallaIngresoEmprendedora(
    correo: String,
    contrasena: String,
    error: String?,
    enviando: Boolean,
    alCambiarCorreo: (String) -> Unit,
    alCambiarContrasena: (String) -> Unit,
    alVolver: () -> Unit,
    alIngresar: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Image(painterResource(Fig.LOGO), contentDescription = null, modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text("Rincón Amancay", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Wine, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Ingresa con las credenciales que te entregó la administración.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
        AmancayInput("Correo electrónico", correo, alCambiarCorreo)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = contrasena, onValueChange = alCambiarContrasena, modifier = Modifier.fillMaxWidth(), label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp))
        error?.let { AvisoEmprendedora(it, true) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = alIngresar, enabled = !enviando && correo.isNotBlank() && contrasena.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Wine), shape = RoundedCornerShape(16.dp)) {
            if (enviando) CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp)) else Text("Ingresar", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver al modo comprador", color = Wine) }
    }
}

@Composable
private fun PantallaDesbloqueoEmprendedora(
    error: String?,
    alDesbloquear: () -> Unit,
    alUsarContrasena: () -> Unit,
    alVolver: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(Fig.LOGO), contentDescription = null, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(12.dp))
        Text("Rincón Amancay", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Wine)
        Text("Confirma tu huella para abrir tu sesión guardada en este dispositivo.", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        error?.let { AvisoEmprendedora(it, true) }
        Button(onClick = alDesbloquear, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Wine), shape = RoundedCornerShape(16.dp)) {
            Text("Ingresar con huella", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = alUsarContrasena, modifier = Modifier.fillMaxWidth()) { Text("Usar correo y contraseña", color = Wine) }
        TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth()) { Text("Volver al modo comprador", color = Wine) }
    }
}

private fun claveHuella(uid: String): String = "huella_emprendedora_$uid"

@Composable
private fun PantallaEdicionTienda(
    cuenta: CuentaEmprendedora,
    tienda: TiendaEmprendedora,
    sectores: List<OpcionCatalogoEmprendedora>,
    tipos: List<OpcionCatalogoEmprendedora>,
    enviando: Boolean,
    alGuardar: (TiendaEmprendedora) -> Unit
) {
    var nombre by remember(tienda.versionEsperada) { mutableStateOf(tienda.nombre) }
    var descripcion by remember(tienda.versionEsperada) { mutableStateOf(tienda.descripcion) }
    var sectorId by remember(tienda.versionEsperada) { mutableStateOf(tienda.sectorId) }
    var tipoId by remember(tienda.versionEsperada) { mutableStateOf(tienda.tipoEmprendimientoId) }
    var contactoTipo by remember(tienda.versionEsperada) { mutableStateOf(tienda.contacto?.tipo ?: "whatsapp") }
    var contactoValor by remember(tienda.versionEsperada) { mutableStateOf(tienda.contacto?.valor.orEmpty()) }
    var mostrarContacto by remember(tienda.versionEsperada) { mutableStateOf(tienda.mostrarContacto) }

    Text("MI TIENDA", fontSize = 12.sp, color = Wine, fontWeight = FontWeight.Bold)
    Text("Ficha del emprendimiento", fontSize = 28.sp, color = Dark)
    Text("Los cambios de nombre, sector y tipo se reflejan en la proyección pública si la tienda ya está publicada.", color = Muted)
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("whatsapp" to "WhatsApp", "correo" to "Correo", "web" to "Sitio web").forEach { (id, label) ->
            OutlinedButton(onClick = { contactoTipo = id }, shape = RoundedCornerShape(14.dp)) {
                Text(label, color = if (contactoTipo == id) Wine else Muted)
            }
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
    Button(
        onClick = {
            val contacto = contactoValor.trim().takeIf(String::isNotEmpty)?.let { ContactoEmprendedora(contactoTipo, it) }
            alGuardar(tienda.copy(nombre = nombre.trim(), descripcion = descripcion.trim(), sectorId = sectorId, tipoEmprendimientoId = tipoId, contacto = contacto, mostrarContacto = mostrarContacto))
        },
        enabled = !enviando && nombre.isNotBlank() && sectorId.isNotBlank() && tipoId.isNotBlank() && (!mostrarContacto || contactoValor.isNotBlank()),
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Wine), shape = RoundedCornerShape(16.dp)
    ) { Text(if (enviando) "Guardando…" else "Guardar ficha", fontWeight = FontWeight.Bold) }
    Text("Tienda asignada: ${cuenta.tiendaId}", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AmancayInput(label: String, value: String, onValueChange: (String) -> Unit, maxLength: Int = 254, multiline: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = { onValueChange(it.take(maxLength)) }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, minLines = if (multiline) 3 else 1, singleLine = !multiline, shape = RoundedCornerShape(16.dp))
}

@Composable
private fun SelectorCatalogo(label: String, opciones: List<OpcionCatalogoEmprendedora>, seleccionado: String, onSelected: (String) -> Unit) {
    var expandido by remember { mutableStateOf(false) }
    val nombre = opciones.firstOrNull { it.id == seleccionado }?.nombre ?: "Seleccionar"
    Column {
        Text(label, fontWeight = FontWeight.Bold, color = Dark)
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedButton(onClick = { expandido = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), enabled = opciones.isNotEmpty()) {
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
        colors = CardDefaults.cardColors(containerColor = if (esError) androidx.compose.ui.graphics.Color(0xFFFCE4E4) else CardPink),
        shape = RoundedCornerShape(14.dp)
    ) { Text(texto, modifier = Modifier.padding(12.dp), color = if (esError) androidx.compose.ui.graphics.Color(0xFF8A2635) else Dark, style = MaterialTheme.typography.bodyMedium) }
}
