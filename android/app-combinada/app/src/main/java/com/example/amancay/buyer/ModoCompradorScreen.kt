package com.example.amancay.buyer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.amancay.data.AutenticacionGoogleComprador
import com.example.amancay.data.CatalogoCompradorRepository
import com.example.amancay.data.ProductoCatalogoComprador
import com.example.amancay.data.ResultadoAccesoGoogle
import com.example.amancay.data.SesionComprador
import com.example.amancay.data.sesionDesdeUsuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Modo comprador con cuatro pestañas: Inicio (hero + selección + feria),
 * Explorar (catálogo completo), Ferias (ferias publicadas) y Mi sesión
 * (acceso con correo o Google). La lógica de autenticación y de lectura
 * del catálogo no cambia: solo se reorganiza la navegación y la estética.
 */
@Composable
fun ModoCompradorScreen(
    repositorio: CatalogoCompradorRepository,
    auth: FirebaseAuth,
    firestore: FirebaseFirestore,
    webClientId: String,
    alAbrirEspacioEmprendedora: () -> Unit
) {
    val productos by repositorio.productos.collectAsState()
    val cargando by repositorio.cargando.collectAsState()
    val error by repositorio.error.collectAsState()
    val directorio by repositorio.directorio.collectAsState()
    val tiposEmprendimiento by repositorio.tiposEmprendimiento.collectAsState()
    val sectores by repositorio.sectores.collectAsState()
    val modoNavegacion by repositorio.modoNavegacion.collectAsState()
    var mostrarDirectorio by remember { mutableStateOf(false) }
    var tiendaSeleccionada by remember { mutableStateOf<String?>(null) }
    var omitioInvitacion by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()
    var sesion by remember(auth) { mutableStateOf(auth.currentUser?.let(::sesionDesdeUsuario)) }
    var uidSesion by remember(auth) { mutableStateOf(auth.currentUser?.uid) }
    var ingresando by remember { mutableStateOf(false) }
    var mensajeAcceso by remember { mutableStateOf<String?>(null) }
    var errorRol by remember { mutableStateOf(false) }
    var intentoRol by remember { mutableStateOf(0) }
    var pestana by remember { mutableStateOf(PestanaComprador.INICIO) }
    var busqueda by remember { mutableStateOf("") }
    var categoriaSeleccionada by remember { mutableStateOf<String?>(null) }
    var productoSeleccionado by remember { mutableStateOf<ProductoCatalogoComprador?>(null) }
    var feriaSeleccionada by remember { mutableStateOf<FeriaComprador?>(null) }
    var avisoEncargoSinSesion by remember { mutableStateOf(false) }
    var encargoProximamenteDe by remember { mutableStateOf<String?>(null) }

    val ferias = rememberRepositorioFerias()
    val listaFerias by ferias.ferias.collectAsState()
    val feriasCargando by ferias.cargando.collectAsState()
    val feriasError by ferias.error.collectAsState()

    DisposableEffect(auth) {
        val escucha = FirebaseAuth.AuthStateListener { estado ->
            uidSesion = estado.currentUser?.uid
            sesion = estado.currentUser?.let(::sesionDesdeUsuario)
        }
        auth.addAuthStateListener(escucha)
        onDispose { auth.removeAuthStateListener(escucha) }
    }

    val uid = uidSesion
    LaunchedEffect(auth, firestore, uid, ingresando, intentoRol) {
        errorRol = false
        if (uid == null || ingresando) return@LaunchedEffect
        try {
            val acceso = firestore.collection("accesos").document(uid).get().await()
            if (auth.currentUser?.uid == uid && acceso.getString("estado") == "activo" &&
                "emprendedora" in (acceso.get("roles") as? List<*>).orEmpty()
            ) {
                alAbrirEspacioEmprendedora()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            errorRol = true
        }
    }

    BackHandler(enabled = pestana != PestanaComprador.INICIO) {
        pestana = PestanaComprador.INICIO
    }

    val categorias = remember(productos) {
        productos.map { it.categoriaNombre }.filter(String::isNotBlank).distinct().sorted()
    }
    val filtrados = remember(productos, busqueda, categoriaSeleccionada) {
        productos.filter { producto ->
            val coincideTipo = categoriaSeleccionada == null || producto.categoriaNombre == categoriaSeleccionada
            val texto = busqueda.trim()
            // Busca solo en lo que el comprador identifica: nombre, tienda,
            // categoría y tipo. Descripción y atributos quedan fuera porque
            // suelen contener datos de prueba que ensucian el filtrado.
            val coincideBusqueda = texto.isBlank() || listOfNotNull(
                producto.nombre,
                producto.tiendaNombre,
                producto.categoriaNombre,
                producto.tipoProductoNombre
            ).any { it.contains(texto, ignoreCase = true) }
            coincideTipo && coincideBusqueda
        }
    }

    fun abrirAcceso() {
        mensajeAcceso = null
        pestana = PestanaComprador.CUENTA
    }

    fun alIngresarGoogle(correoIngresado: String, claveIngresada: String) {
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

    val exigeGoogle = modoNavegacion == "exigir" && auth.currentUser?.providerData.orEmpty().none { it.providerId == "google.com" }
    val invitaGoogle = modoNavegacion == "invitar" && sesion == null && !omitioInvitacion
    val catalogoRestringido = modoNavegacion == null || exigeGoogle || invitaGoogle
    if (catalogoRestringido && pestana != PestanaComprador.CUENTA) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (modoNavegacion == null) "Cargando acceso al catálogo" else "Continúa con Google") },
            text = {
                Text(when {
                    modoNavegacion == null -> error ?: "Estamos consultando la configuración de acceso."
                    exigeGoogle -> mensajeAcceso ?: "La plataforma requiere iniciar sesión con Google."
                    else -> mensajeAcceso ?: "Puedes ingresar con Google u omitir este paso."
                })
            },
            confirmButton = {
                if (modoNavegacion != null) TextButton(
                    onClick = { alIngresarGoogle("", "") },
                    enabled = !ingresando
                ) { Text("Ingresar con Google") }
            },
            dismissButton = {
                TextButton(onClick = { if (invitaGoogle) omitioInvitacion = true else abrirAcceso() }) {
                    Text(if (invitaGoogle) "Omitir" else "Usar correo")
                }
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AmancayFondo,
        topBar = {
            BarraSuperiorAmancay(
                alVolver = if (pestana != PestanaComprador.INICIO) ({ pestana = PestanaComprador.INICIO }) else null
            ) {
                val sesionActual = sesion
                if (sesionActual == null) {
                    BotonIniciarSesionAmancay(
                        alClick = ::abrirAcceso,
                        habilitado = !ingresando
                    )
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
        },
        bottomBar = {
            if (!catalogoRestringido) BarraInferiorComprador(actual = pestana) { pestana = it }
        }
    ) { inset ->
        when (pestana) {
            PestanaComprador.INICIO -> PantallaInicio(
                productos = productos,
                filtrados = filtrados,
                cargando = cargando,
                error = error,
                busqueda = busqueda,
                alBuscar = { busqueda = it },
                categorias = categorias,
                categoriaSeleccionada = categoriaSeleccionada,
                alSeleccionarCategoria = { categoriaSeleccionada = it },
                alAbrirProducto = { productoSeleccionado = it },
                proximaFeria = proximaFeria(listaFerias),
                alVerTodo = { pestana = PestanaComprador.EXPLORAR },
                alVerDirectorio = { mostrarDirectorio = true },
                alVerFerias = { pestana = PestanaComprador.FERIAS },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset)
                    .padding(horizontal = 16.dp)
            )

            PestanaComprador.EXPLORAR -> PantallaExplorar(
                productos = productos,
                filtrados = filtrados,
                cargando = cargando,
                error = error,
                busqueda = busqueda,
                alBuscar = { busqueda = it },
                categorias = categorias,
                categoriaSeleccionada = categoriaSeleccionada,
                alSeleccionarCategoria = { categoriaSeleccionada = it },
                alAbrirProducto = { productoSeleccionado = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset)
                    .padding(horizontal = 16.dp)
            )

            PestanaComprador.FERIAS -> PantallaFeriasComprador(
                ferias = listaFerias,
                cargando = feriasCargando,
                error = feriasError,
                alDetalle = { feriaSeleccionada = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset)
                    .padding(horizontal = 16.dp)
            )

            PestanaComprador.CUENTA -> PantallaCuentaComprador(
                sesion = sesion,
                auth = auth,
                googleCargando = ingresando,
                mensaje = mensajeAcceso,
                alRefrescarPerfil = {
                    sesion = auth.currentUser?.let(::sesionDesdeUsuario)
                },
                alExplorar = { pestana = PestanaComprador.INICIO },
                alCerrarSesion = {
                    alcance.launch {
                        AutenticacionGoogleComprador.cerrarSesion(context, auth)
                    }
                },
                onGoogle = ::alIngresarGoogle,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset)
            )
        }
    }

    if (errorRol) {
        AlertDialog(
            onDismissRequest = { errorRol = false },
            title = { Text("No pudimos comprobar tu perfil") },
            text = { Text("Comprueba tu conexión para acceder al espacio asociado a tu cuenta.") },
            confirmButton = { TextButton(onClick = { intentoRol++ }) { Text("Reintentar") } },
            dismissButton = { TextButton(onClick = { errorRol = false }) { Text("Cerrar") } }
        )
    }

    if (mostrarDirectorio) {
        DirectorioComprador(directorio, tiposEmprendimiento, sectores, cargando, error,
            alCerrar = { mostrarDirectorio = false },
            alAbrir = { tiendaSeleccionada = it; mostrarDirectorio = false })
    }
    directorio.firstOrNull { it.id == tiendaSeleccionada }?.let { tienda ->
        if (productoSeleccionado == null) AlertDialog(
            onDismissRequest = { tiendaSeleccionada = null },
            title = { Text(tienda.nombre) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val catalogo = productos.filter { it.tiendaId == tienda.id }
                    if (catalogo.isEmpty()) Text("No hay productos públicos disponibles para mostrar.")
                    catalogo.forEach { producto ->
                        TextButton(onClick = { productoSeleccionado = producto }) {
                            Text("${producto.nombre} · ${precioProducto(producto)}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tiendaSeleccionada = null; mostrarDirectorio = true }) { Text("Volver al directorio") } }
        )
    }
    productoSeleccionado?.let { seleccionado ->
        val producto = productos.firstOrNull { it.id == seleccionado.id && it.tiendaId == seleccionado.tiendaId } ?: seleccionado
        DialogoDetalleProducto(
            producto = producto,
            alCerrar = { productoSeleccionado = null },
            alEncargar = {
                productoSeleccionado = null
                if (sesion == null) {
                    avisoEncargoSinSesion = true
                } else {
                    encargoProximamenteDe = producto.nombre
                }
            }
        )
    }
    feriaSeleccionada?.let { feria ->
        DialogoDetalleFeria(feria) { feriaSeleccionada = null }
    }
    if (avisoEncargoSinSesion) {
        AlertDialog(
            onDismissRequest = { avisoEncargoSinSesion = false },
            title = {
                Text(
                    "Inicia sesión para encargar",
                    fontWeight = FontWeight.Bold,
                    color = AmancayTinta
                )
            },
            text = {
                Text(
                    "Con tu cuenta de compradora puedes enviar encargos a las emprendedoras y seguir el estado de tus pedidos.",
                    color = AmancayTinta
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    avisoEncargoSinSesion = false
                    pestana = PestanaComprador.CUENTA
                }) {
                    Text("Ir a Mi sesión", color = AmancayBordo, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { avisoEncargoSinSesion = false }) {
                    Text("Cancelar", color = AmancayTintaSuave)
                }
            },
            containerColor = AmancaySuperficie
        )
    }
    encargoProximamenteDe?.let { nombreProducto ->
        AlertDialog(
            onDismissRequest = { encargoProximamenteDe = null },
            title = {
                Text(
                    "Encargo muy pronto",
                    fontWeight = FontWeight.Bold,
                    color = AmancayTinta
                )
            },
            text = {
                Text(
                    "Estamos preparando el formulario de encargos. Muy pronto podrás encargar «$nombreProducto» directamente desde la app.",
                    color = AmancayTinta
                )
            },
            confirmButton = {
                TextButton(onClick = { encargoProximamenteDe = null }) {
                    Text("Entendido", color = AmancayBordo, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = AmancaySuperficie
        )
    }
}

// ---------------------------------------------------------------------------
// Inicio
// ---------------------------------------------------------------------------

@Composable
private fun PantallaInicio(
    productos: List<ProductoCatalogoComprador>,
    filtrados: List<ProductoCatalogoComprador>,
    cargando: Boolean,
    error: String?,
    busqueda: String,
    alBuscar: (String) -> Unit,
    categorias: List<String>,
    categoriaSeleccionada: String?,
    alSeleccionarCategoria: (String?) -> Unit,
    alAbrirProducto: (ProductoCatalogoComprador) -> Unit,
    proximaFeria: FeriaComprador?,
    alVerTodo: () -> Unit,
    alVerDirectorio: () -> Unit,
    alVerFerias: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HeroBienvenida(alExplorar = alVerTodo) }
        item { BuscadorAmancay(valor = busqueda, alCambiar = alBuscar) }
        item {
            TextButton(onClick = alVerDirectorio) {
                Text("Directorio de emprendimientos", color = AmancayBordo, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            FilaCategorias(
                categorias = categorias,
                seleccionada = categoriaSeleccionada,
                alSeleccionar = alSeleccionarCategoria
            )
        }
        if (error != null && productos.isEmpty()) {
            item { MensajeCatalogo(error) }
        }
        when {
            cargando && productos.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmancayBordo)
                }
            }

            productos.isEmpty() -> item {
                MensajeCatalogo("Aún no hay productos publicados para comprar.")
            }

            filtrados.isEmpty() -> item {
                MensajeCatalogo("No hay productos que coincidan con tu búsqueda o filtro.")
            }

            else -> {
                item {
                    Column {
                        EncabezadoSeccion("Productos destacados", alVerTodo = alVerTodo)
                        Spacer(Modifier.height(2.dp))
                    }
                }
                grillaProductos(
                    productos = filtrados.take(6),
                    alAbrir = alAbrirProducto
                )
            }
        }
        if (proximaFeria != null && !cargando) {
            item {
                TarjetaFeriaProxima(
                    nombre = proximaFeria.nombre,
                    lugar = proximaFeria.direccion.ifBlank { proximaFeria.organizador },
                    alAbrir = alVerFerias
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Explorar
// ---------------------------------------------------------------------------

@Composable
private fun PantallaExplorar(
    productos: List<ProductoCatalogoComprador>,
    filtrados: List<ProductoCatalogoComprador>,
    cargando: Boolean,
    error: String?,
    busqueda: String,
    alBuscar: (String) -> Unit,
    categorias: List<String>,
    categoriaSeleccionada: String?,
    alSeleccionarCategoria: (String?) -> Unit,
    alAbrirProducto: (ProductoCatalogoComprador) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TituloPantalla(
                titulo = "Explorar",
                bajada = "Todos los productos publicados de los emprendimientos Amancay."
            )
        }
        item { BuscadorAmancay(valor = busqueda, alCambiar = alBuscar) }
        item {
            FilaCategorias(
                categorias = categorias,
                seleccionada = categoriaSeleccionada,
                alSeleccionar = alSeleccionarCategoria
            )
        }
        item {
            Text(
                if (filtrados.size == 1) "1 producto"
                else "${filtrados.size} productos",
                style = MaterialTheme.typography.labelMedium,
                color = AmancayTintaSuave
            )
        }
        if (error != null) {
            item { MensajeCatalogo(error) }
        }
        when {
            cargando && productos.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmancayBordo)
                }
            }

            productos.isEmpty() -> item {
                MensajeCatalogo("Aún no hay productos publicados para comprar.")
            }

            filtrados.isEmpty() -> item {
                MensajeCatalogo("No hay productos que coincidan con tu búsqueda o filtro.")
            }

            else -> grillaProductos(
                productos = filtrados,
                alAbrir = alAbrirProducto
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Fila de categorías
// ---------------------------------------------------------------------------

@Composable
private fun FilaCategorias(
    categorias: List<String>,
    seleccionada: String?,
    alSeleccionar: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChipCategoriaAmancay(
            texto = "Todos",
            icono = Icons.Outlined.GridView,
            seleccionado = seleccionada == null
        ) { alSeleccionar(null) }
        categorias.forEach { categoria ->
            ChipCategoriaAmancay(
                texto = categoria,
                icono = iconoCategoriaAmancay(categoria),
                seleccionado = seleccionada == categoria
            ) { alSeleccionar(categoria) }
        }
    }
}
