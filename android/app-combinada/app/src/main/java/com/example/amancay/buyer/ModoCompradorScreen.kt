package com.example.amancay.buyer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.amancay.data.AutenticacionGoogleComprador
import com.example.amancay.data.AutenticacionCorreo
import com.example.amancay.data.CatalogoCompradorRepository
import com.example.amancay.data.ProductoCatalogoComprador
import com.example.amancay.data.ResultadoAccesoGoogle
import com.example.amancay.data.SesionComprador
import com.example.amancay.data.sesionDesdeUsuario
import com.example.amancay.ui.Fig
import com.example.amancay.ui.ProductImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ModoCompradorScreen(
    repositorio: CatalogoCompradorRepository,
    auth: FirebaseAuth,
    webClientId: String,
    alVolver: () -> Unit
) {
    val productos by repositorio.productos.collectAsState()
    val cargando by repositorio.cargando.collectAsState()
    val error by repositorio.error.collectAsState()
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()
    var sesion by remember { mutableStateOf<SesionComprador?>(null) }
    var ingresando by remember { mutableStateOf(false) }
    var mensajeAcceso by remember { mutableStateOf<String?>(null) }
    var mostrarAcceso by remember { mutableStateOf(false) }
    var busqueda by remember { mutableStateOf("") }
    var categoriaSeleccionada by remember { mutableStateOf<String?>(null) }
    var productoSeleccionado by remember { mutableStateOf<ProductoCatalogoComprador?>(null) }

    DisposableEffect(auth) {
        val escucha = FirebaseAuth.AuthStateListener { estado ->
            sesion = estado.currentUser?.let(::sesionDesdeUsuario)
        }
        auth.addAuthStateListener(escucha)
        onDispose { auth.removeAuthStateListener(escucha) }
    }

    // Si la sesión aparece (login con Google), la pantalla de acceso se cierra sola.
    DisposableEffect(sesion, ingresando) {
        if (sesion != null && !ingresando) mostrarAcceso = false
        onDispose { }
    }

    BackHandler(onBack = alVolver)

    val categorias = remember(productos) {
        productos.map { it.categoriaNombre }.filter(String::isNotBlank).distinct().sorted()
    }
    val filtrados = remember(productos, busqueda, categoriaSeleccionada) {
        productos.filter { producto ->
            val coincideTipo = categoriaSeleccionada == null || producto.categoriaNombre == categoriaSeleccionada
            val texto = busqueda.trim()
            val coincideBusqueda = texto.isBlank() || listOfNotNull(
                producto.nombre,
                producto.descripcion,
                producto.categoriaNombre,
                producto.tipoProductoNombre,
                producto.tiendaNombre,
                producto.unidadVenta,
                producto.modalidad,
                *producto.atributosEspecificos.values.toTypedArray()
            ).any { it.contains(texto, ignoreCase = true) }
            coincideTipo && coincideBusqueda
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AmancayFondo,
        topBar = {
            BarraSuperiorAmancay(
                titulo = "Modo comprador",
                alSalir = alVolver,
                contenidoSesion = {
                    val sesionActual = sesion
                    if (sesionActual == null) {
                        TextButton(
                            onClick = {
                                mensajeAcceso = null
                                mostrarAcceso = true
                            },
                            enabled = !ingresando
                        ) {
                            Text(
                                "Iniciar sesión",
                                color = AmancayBordo,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AmancayBordo),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                sesionActual.iniciales,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        TextButton(
                            onClick = {
                                alcance.launch {
                                    AutenticacionGoogleComprador.cerrarSesion(context, auth)
                                }
                            }
                        ) {
                            Text(
                                "Cerrar sesión",
                                color = AmancayTintaSuave,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        }
    ) { inset ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inset).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            EtiquetaSeccion("Catálogo")
            TituloPantalla(
                titulo = "Encuentra tu próximo favorito",
                bajada = "Productos publicados de los emprendimientos Amancay."
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar productos…") },
                singleLine = true,
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AmancaySuperficie,
                    unfocusedContainerColor = AmancaySuperficie,
                    focusedBorderColor = AmancayBordo,
                    unfocusedBorderColor = AmancayBorde
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FiltroProducto("Todos", categoriaSeleccionada == null) { categoriaSeleccionada = null }
                categorias.forEach { categoria ->
                    FiltroProducto(categoria, categoriaSeleccionada == categoria) { categoriaSeleccionada = categoria }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (filtrados.size == 1) "1 producto" else "${filtrados.size} productos", style = MaterialTheme.typography.labelMedium, color = AmancayTintaSuave)
            Spacer(Modifier.height(6.dp))
            if (error != null) {
                MensajeCatalogo(error!!)
                Spacer(Modifier.height(6.dp))
            }
            when {
                cargando && productos.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AmancayBordo)
                    Spacer(Modifier.height(12.dp))
                    Text("Cargando catálogo…", color = AmancayTintaSuave)
                }
                productos.isEmpty() -> MensajeCatalogo("Aún no hay productos publicados para comprar.")
                filtrados.isEmpty() -> MensajeCatalogo("No hay productos que coincidan con tu búsqueda o filtro.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtrados, key = { it.id }) { producto ->
                        TarjetaProductoEmprendedora(producto) { productoSeleccionado = producto }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }

    productoSeleccionado?.let { producto ->
        DialogoDetalleProducto(producto) { productoSeleccionado = null }
    }

    if (mostrarAcceso && (sesion == null || ingresando)) {
        PantallaAccesoComprador(
            auth = auth,
            googleCargando = ingresando,
            mensaje = mensajeAcceso,
            alRefrescarPerfil = {
                sesion = auth.currentUser?.let(::sesionDesdeUsuario)
            },
            onCerrar = { mostrarAcceso = false },
            onGoogle = { correoIngresado, claveIngresada ->
                ingresando = true
                mensajeAcceso = null
                alcance.launch {
                    try {
                        val idClienteWeb = AutenticacionGoogleComprador.idClienteWeb(context, webClientId)
                        val resultado = if (correoIngresado.isNotBlank() && claveIngresada.isNotBlank()) {
                            auth.signInWithEmailAndPassword(correoIngresado.trim(), claveIngresada).await()
                            val vinculacion = AutenticacionGoogleComprador.vincularGoogleConCuentaActual(
                                context, auth, idClienteWeb, correoIngresado
                            )
                            if (vinculacion !is ResultadoAccesoGoogle.Exito) {
                                AutenticacionGoogleComprador.cerrarSesion(context, auth)
                                sesion = null
                            }
                            vinculacion
                        } else {
                            AutenticacionGoogleComprador.iniciarSesionConGoogle(context, auth, idClienteWeb)
                        }
                        when (resultado) {
                            is ResultadoAccesoGoogle.Fallido -> mensajeAcceso = resultado.mensaje
                            ResultadoAccesoGoogle.Cancelado -> {
                                if (correoIngresado.isNotBlank() && claveIngresada.isNotBlank()) {
                                    mensajeAcceso = "No se vinculó Google. Puedes ingresar con tu correo y contraseña."
                                }
                            }
                            ResultadoAccesoGoogle.Exito -> Unit
                        }
                    } catch (e: Exception) {
                        auth.signOut()
                        sesion = null
                        mensajeAcceso = mensajeAccesoCorreo(e)
                    } finally {
                        ingresando = false
                    }
                }
            }
        )
    }
}

private fun mensajeAccesoCorreo(error: Exception): String {
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

private fun mensajeCrearCuenta(error: Exception): String {
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

@Composable
private fun PantallaAccesoComprador(
    auth: FirebaseAuth,
    googleCargando: Boolean,
    mensaje: String?,
    alRefrescarPerfil: () -> Unit,
    onCerrar: () -> Unit,
    onGoogle: (String, String) -> Unit
) {
    val alcance = rememberCoroutineScope()
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var creandoCuenta by remember { mutableStateOf(false) }
    var enviandoCorreo by remember { mutableStateOf(false) }
    var recuperandoContrasena by remember { mutableStateOf(false) }
    var errorCorreo by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize().background(AmancayFondo),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Fig.LOGO),
                contentDescription = "Rincón Amancay",
                modifier = Modifier.size(92.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                if (creandoCuenta) "Crea tu cuenta compradora" else "Inicia sesión para comprar",
                style = MaterialTheme.typography.headlineSmall,
                color = AmancayBordo
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (creandoCuenta)
                    "Regístrate con tu correo para enviar encargos y seguir a tus emprendimientos favoritos."
                else
                    "Con tu cuenta puedes enviar encargos y seguir a tus emprendimientos favoritos.",
                style = MaterialTheme.typography.bodyMedium,
                color = AmancayTintaSuave,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            if (creandoCuenta) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Nombre y apellido") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = AmancayBordo,
                        unfocusedBorderColor = AmancayBorde,
                        focusedTextColor = AmancayTinta,
                        unfocusedTextColor = AmancayTinta
                    )
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = AmancayBordo,
                    unfocusedBorderColor = AmancayBorde,
                    focusedTextColor = AmancayTinta,
                    unfocusedTextColor = AmancayTinta
                )
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = AmancayBordo,
                    unfocusedBorderColor = AmancayBorde,
                    focusedTextColor = AmancayTinta,
                    unfocusedTextColor = AmancayTinta
                )
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
                                // El AuthStateListener cierra esta ventana al ver la sesión.
                            } else {
                                auth.signInWithEmailAndPassword(correo.trim(), contrasena).await()
                                // El AuthStateListener cierra esta ventana al ver la sesión.
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
                    modifier = Modifier.align(Alignment.End)
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
                enabled = !enviandoCorreo && !googleCargando && !recuperandoContrasena
            ) {
                Text(
                    if (creandoCuenta) "¿Ya tienes cuenta? Iniciar sesión" else "¿No tienes cuenta? Crear una",
                    color = AmancayBordo,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = onCerrar, enabled = !enviandoCorreo && !googleCargando && !recuperandoContrasena) {
                Text("Explorar sin iniciar sesión", color = AmancayTintaSuave)
            }
        }
    }
}

@Composable
private fun FiltroProducto(texto: String, seleccionado: Boolean, alSeleccionar: () -> Unit) {
    FilterChip(
        selected = seleccionado,
        onClick = alSeleccionar,
        label = { Text(texto) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmancayBordo,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
            containerColor = AmancaySuperficie,
            labelColor = AmancayTinta
        ),
        shape = RoundedCornerShape(50.dp)
    )
}

@Composable
private fun TarjetaProductoEmprendedora(producto: ProductoCatalogoComprador, alAbrir: () -> Unit) {
    TarjetaAmancay(modifier = Modifier.clickable(onClick = alAbrir)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (producto.imagenUrl != null) {
                ProductImage(fallback = Fig.VASE_THUMB, size = 72.dp, uri = Uri.parse(producto.imagenUrl), hasPhoto = true, corner = 16.dp)
            } else {
                MiniaturaAmancay("🛍️")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                EtiquetaCategoria(producto.categoriaNombre.ifBlank { producto.tipoProductoNombre })
                Text(
                    producto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = AmancayTinta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(producto.tiendaNombre, style = MaterialTheme.typography.bodySmall, color = AmancayTintaSuave)
                Spacer(Modifier.height(4.dp))
                Text(
                    etiquetaDisponibilidad(producto.estadoDisponibilidad),
                    style = MaterialTheme.typography.labelMedium,
                    color = AmancayVerdeTexto
                )
                Spacer(Modifier.height(4.dp))
                Text(precioProducto(producto), style = MaterialTheme.typography.titleSmall, color = AmancayBordo)
            }
        }
    }
}

@Composable
private fun MensajeCatalogo(texto: String) {
    TarjetaAmancay(modifier = Modifier.padding(top = 12.dp)) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = AmancayTinta)
    }
}

@Composable
private fun DialogoDetalleProducto(producto: ProductoCatalogoComprador, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(producto.nombre, fontWeight = FontWeight.Bold, color = AmancayTinta) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (producto.imagenUrl != null) {
                    ProductImage(
                        fallback = Fig.VASE_REVIEW,
                        size = 184.dp,
                        uri = Uri.parse(producto.imagenUrl),
                        hasPhoto = true,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                Text(producto.tiendaNombre, color = AmancayBordo)
                Text(producto.categoriaNombre.ifBlank { producto.tipoProductoNombre }, color = AmancayTintaSuave)
                if (producto.descripcion.isNotBlank()) Text(producto.descripcion)
                producto.atributosEspecificos.forEach { (nombre, valor) -> Text("$nombre: $valor") }
                if (producto.unidadVenta.isNotBlank()) Text("Venta: ${producto.unidadVenta}")
                if (producto.modalidad.isNotBlank()) Text("Modalidad: ${producto.modalidad}")
                Text(etiquetaDisponibilidad(producto.estadoDisponibilidad), color = AmancayVerdeTexto)
                if (producto.variantes.isEmpty() && producto.precioBase != null) {
                    Text(formatoPrecio(producto.precioBase), color = AmancayBordo)
                }
                producto.variantes.forEach { variante ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(variante.opciones.values.joinToString(" · ").ifBlank { "Opción" }, modifier = Modifier.weight(1f))
                        Text(variante.precio?.let(::formatoPrecio) ?: "Consultar", color = AmancayBordo)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        containerColor = AmancaySuperficie
    )
}

private fun etiquetaDisponibilidad(estado: String): String = when (estado) {
    "disponible" -> "Disponible"
    "a_pedido" -> "Disponible a pedido"
    "agotado" -> "Agotado"
    "no_disponible" -> "No disponible"
    else -> "Consultar disponibilidad"
}

private fun precioProducto(producto: ProductoCatalogoComprador): String {
    val precios = producto.variantes.mapNotNull { it.precio }.filter { it > 0 }
    if (precios.isEmpty()) return producto.precioBase?.let(::formatoPrecio) ?: "Consultar precio"
    val minimo = precios.minOrNull() ?: return "Consultar precio"
    return if (precios.distinct().size > 1) "Desde ${formatoPrecio(minimo)}" else formatoPrecio(minimo)
}

private fun formatoPrecio(valor: Long): String {
    val numero = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).format(valor)
    return "\$$numero CLP"
}
