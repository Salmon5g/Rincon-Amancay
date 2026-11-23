package com.example.amancay.buyer

import android.net.Uri
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Storefront
import com.example.amancay.data.AutenticacionGoogleComprador
import com.example.amancay.data.ApiConsultasComprador
import com.example.amancay.data.TiendaCatalogoComprador
import com.example.amancay.data.VarianteCatalogoComprador
import com.example.amancay.data.CatalogoCompradorRepository
import com.example.amancay.data.ProductoCatalogoComprador
import com.example.amancay.data.ResultadoAccesoGoogle
import com.example.amancay.data.SesionComprador
import com.example.amancay.data.SeguimientoAsociacionesComprador
import com.example.amancay.data.SeguimientoTiendasComprador
import com.example.amancay.data.sesionDesdeUsuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first

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
    uidConEleccionDeModo: String?,
    alRegistrarEleccionDeModo: (String) -> Unit,
    alAbrirEspacioEmprendedora: () -> Unit,
    feriaIdPush: String? = null,
    alConsumirFeriaPush: () -> Unit = {}
) {
    val productos by repositorio.productos.collectAsState()
    val cargando by repositorio.cargando.collectAsState()
    val error by repositorio.error.collectAsState()
    val directorio by repositorio.directorio.collectAsState()
    val asociaciones by repositorio.asociaciones.collectAsState()
    val tiposEmprendimiento by repositorio.tiposEmprendimiento.collectAsState()
    val sectores by repositorio.sectores.collectAsState()
    val modoNavegacion by repositorio.modoNavegacion.collectAsState()
    var mostrarDirectorio by remember { mutableStateOf(false) }
    var asociacionesPrimero by remember { mutableStateOf(false) }
    var asociacionInicialId by remember { mutableStateOf<String?>(null) }
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
    var uidEmprendedoraParaElegir by remember { mutableStateOf<String?>(null) }
    var pestana by remember { mutableStateOf(PestanaComprador.INICIO) }
    var seguimientoPendienteTipo by rememberSaveable { mutableStateOf<String?>(null) }
    var seguimientoPendienteId by rememberSaveable { mutableStateOf<String?>(null) }
    var seguimientoPendienteNombre by rememberSaveable { mutableStateOf<String?>(null) }
    var pestanaAntesDeSeguimiento by rememberSaveable { mutableStateOf(PestanaComprador.INICIO.name) }
    var reintentoSeguimientoPendiente by rememberSaveable { mutableIntStateOf(0) }
    var errorSeguimientoPendiente by remember { mutableStateOf<String?>(null) }
    var busqueda by remember { mutableStateOf("") }
    var categoriaSeleccionada by remember { mutableStateOf<String?>(null) }
    var productoSeleccionado by remember { mutableStateOf<ProductoCatalogoComprador?>(null) }
    var feriaSeleccionada by remember { mutableStateOf<FeriaComprador?>(null) }
    var feriaIdPendientePush by remember { mutableStateOf<String?>(null) }
    var mapaFeriasVisible by remember { mutableStateOf(false) }
    var formularioContacto by remember { mutableStateOf<Triple<TiendaCatalogoComprador, String?, VarianteCatalogoComprador?>?>(null) }
    var avisoContacto by remember { mutableStateOf<String?>(null) }
    var misConsultas by remember { mutableStateOf(leerConsultasGuardadas(context)) }
    var avisosFerias by remember(auth.currentUser?.uid) { mutableStateOf(false) }
    var cargandoAvisos by remember(auth.currentUser?.uid) { mutableStateOf(false) }
    val consultasActualizadas by androidx.compose.runtime.rememberUpdatedState(misConsultas)
    var cargandoRespuestas by remember { mutableStateOf(false) }
    var errorRespuestas by remember { mutableStateOf<String?>(null) }

    suspend fun actualizarRespuestas(consultas: List<ConsultaSeguida>, maximo: Int? = null): List<ConsultaSeguida> {
        val pendientes = consultas.filter { it.respuesta == null }.let { if (maximo == null) it else it.take(maximo) }
        if (pendientes.isEmpty()) return consultas
        val resultados = coroutineScope {
            pendientes.map { consulta -> async {
                consulta.operacionId to ApiConsultasComprador.leerRespuesta(consulta.tiendaId, consulta.operacionId)
            } }.awaitAll().toMap()
        }
        return consultas.map { consulta ->
            val respuesta = resultados[consulta.operacionId]
            if (!respuesta.isNullOrBlank() && respuesta != consulta.respuesta) consulta.copy(respuesta = respuesta, respuestaVista = false)
            else consulta
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val actuales = consultasActualizadas
            if (actuales.isNotEmpty()) {
                runCatching { actualizarRespuestas(actuales, maximo = 25) }
                    .onSuccess { nuevas ->
                        if (nuevas != actuales) {
                            misConsultas = nuevas
                            guardarConsultas(context, nuevas)
                        }
                    }
            }
            delay(60_000)
        }
    }

    val ferias = rememberRepositorioFerias()
    val listaFerias by ferias.ferias.collectAsState()
    val feriasCargando by ferias.cargando.collectAsState()
    val feriasError by ferias.error.collectAsState()

    LaunchedEffect(feriaIdPush) {
        feriaIdPush?.takeIf(String::isNotBlank)?.let { id ->
            pestana = PestanaComprador.FERIAS
            mapaFeriasVisible = false
            feriaSeleccionada = null
            feriaIdPendientePush = id
        }
    }

    LaunchedEffect(feriaIdPendientePush) {
        val id = feriaIdPendientePush ?: return@LaunchedEffect
        val (feriasActuales, _) = snapshotFlow { listaFerias to feriasCargando }
            .first { (_, cargando) -> !cargando }
        val feriaEnLista = feriasActuales.firstOrNull { it.id == id }
        if (feriaEnLista != null) {
            feriaSeleccionada = feriaEnLista
            feriaIdPendientePush = null
            alConsumirFeriaPush()
            return@LaunchedEffect
        }
        if (!feriasCargando) {
            runCatching {
                val documento = firestore.collection("ferias").document(id).get().await()
                if (!documento.exists() || documento.getString("estado") != "publicada") null
                else FeriaComprador(
                    id = documento.id,
                    nombre = documento.getString("nombre")?.takeIf(String::isNotBlank) ?: "Feria",
                    descripcion = documento.getString("descripcion").orEmpty(),
                    organizador = documento.getString("organizador").orEmpty(),
                    direccion = documento.getString("direccion").orEmpty(),
                    fechaInicio = documento.getTimestamp("fechaInicio")?.toDate(),
                    fechaFin = documento.getTimestamp("fechaFin")?.toDate(),
                    latitud = documento.getDouble("latitud"),
                    longitud = documento.getDouble("longitud")
                )
            }
                .onSuccess { feria ->
                    if (feria != null) feriaSeleccionada = feria
                    else mensajeAcceso = "Esta feria ya no está publicada o no se pudo encontrar."
                    feriaIdPendientePush = null
                    alConsumirFeriaPush()
                }
                .onFailure {
                    mensajeAcceso = "No se pudo abrir el detalle de la feria. Comprueba tu conexión e inténtalo de nuevo."
                    feriaIdPendientePush = null
                    alConsumirFeriaPush()
                }
        }
    }

    DisposableEffect(auth) {
        val escucha = FirebaseAuth.AuthStateListener { estado ->
            uidSesion = estado.currentUser?.uid
            sesion = estado.currentUser?.let(::sesionDesdeUsuario)
        }
        auth.addAuthStateListener(escucha)
        onDispose { auth.removeAuthStateListener(escucha) }
    }

    val uid = uidSesion
    LaunchedEffect(uid) {
        if (uid.isNullOrBlank()) {
            avisosFerias = false
            cargandoAvisos = false
            return@LaunchedEffect
        }
        cargandoAvisos = true
        mensajeAcceso = null
        runCatching {
            val habilitados = PreferenciasAvisosComprador.consentimiento(firestore, uid)
            val temasSincronizados = !habilitados || PreferenciasAvisosComprador.sincronizarTemas(firestore, uid, true)
            habilitados to temasSincronizados
        }
            .onSuccess { (habilitados, temasSincronizados) ->
                avisosFerias = habilitados
                if (habilitados && !temasSincronizados) {
                    mensajeAcceso = "Tus avisos están activados, pero no pudimos sincronizar todas tus tiendas y asociaciones. Revisa tu conexión e inténtalo de nuevo."
                }
            }
            .onFailure {
                avisosFerias = false
                mensajeAcceso = "No pudimos comprobar tus preferencias de avisos. Revisa tu conexión e inténtalo de nuevo."
            }
        cargandoAvisos = false
    }
    val guardarAvisos: (Boolean) -> Unit = { habilitados ->
        val usuarioId = uid
        if (usuarioId != null && !cargandoAvisos) {
            cargandoAvisos = true
            alcance.launch {
                runCatching { PreferenciasAvisosComprador.cambiar(firestore, usuarioId, habilitados) }
                    .onSuccess { temasSincronizados ->
                        avisosFerias = habilitados
                        if (!temasSincronizados) mensajeAcceso = "Guardamos tu preferencia, pero no pudimos sincronizar todos los avisos. Revisa tu conexión e inténtalo de nuevo."
                    }
                    .onFailure { mensajeAcceso = "No pudimos actualizar tus preferencias de avisos. Revisa tu conexión e inténtalo de nuevo." }
                cargandoAvisos = false
            }
        }
    }
    val permisoAvisosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) guardarAvisos(true)
        else mensajeAcceso = "Android no autorizó las notificaciones. Puedes habilitarlas en los ajustes del teléfono."
    }
    val solicitarAvisos: (Boolean) -> Unit = { habilitados ->
        if (!habilitados) guardarAvisos(false)
        else if (Build.VERSION.SDK_INT >= 33) permisoAvisosLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else guardarAvisos(true)
    }
    LaunchedEffect(uid, seguimientoPendienteTipo, seguimientoPendienteId, reintentoSeguimientoPendiente) {
        val usuarioId = uid ?: return@LaunchedEffect
        val tipo = seguimientoPendienteTipo ?: return@LaunchedEffect
        val id = seguimientoPendienteId ?: return@LaunchedEffect
        val nombre = seguimientoPendienteNombre.orEmpty()

        errorSeguimientoPendiente = null
        try {
            when (tipo) {
                "tienda" -> SeguimientoTiendasComprador(firestore).seguir(usuarioId, id)
                "asociacion" -> SeguimientoAsociacionesComprador(firestore).seguir(usuarioId, id)
                else -> error("El tipo de seguimiento pendiente no es válido.")
            }
            if (PreferenciasAvisosComprador.consentimiento(firestore, usuarioId)) {
                PreferenciasAvisosComprador.actualizarTema(usuarioId, tipo, id, true)
            }

            mensajeAcceso = "Ahora sigues a $nombre."
            pestana = runCatching { PestanaComprador.valueOf(pestanaAntesDeSeguimiento) }
                .getOrDefault(PestanaComprador.INICIO)
            if (tipo == "tienda") {
                tiendaSeleccionada = id
                mostrarDirectorio = false
            } else {
                asociacionesPrimero = true
                asociacionInicialId = id
                mostrarDirectorio = true
            }
            seguimientoPendienteTipo = null
            seguimientoPendienteId = null
            seguimientoPendienteNombre = null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            errorSeguimientoPendiente =
                "Iniciaste sesión, pero no se pudo completar el seguimiento de $nombre. Comprueba la conexión y las reglas de Firestore; puedes reintentar."
        }
    }

    LaunchedEffect(auth, firestore, uid, ingresando, intentoRol, seguimientoPendienteTipo, uidConEleccionDeModo) {
        errorRol = false
        if (uid == null || ingresando || seguimientoPendienteTipo != null || uid == uidConEleccionDeModo) {
            if (uid == null) uidEmprendedoraParaElegir = null
            return@LaunchedEffect
        }
        try {
            val acceso = firestore.collection("accesos").document(uid).get().await()
            if (auth.currentUser?.uid == uid && acceso.getString("estado") == "activo" &&
                "emprendedora" in (acceso.get("roles") as? List<*>).orEmpty()
            ) {
                uidEmprendedoraParaElegir = uid
            } else if (uidEmprendedoraParaElegir == uid) {
                uidEmprendedoraParaElegir = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            errorRol = true
        }
    }

    BackHandler(enabled = pestana != PestanaComprador.INICIO || mapaFeriasVisible) {
        if (mapaFeriasVisible) mapaFeriasVisible = false
        else pestana = PestanaComprador.INICIO
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

    fun pedirInicioSesionParaSeguir(tipo: String, id: String, nombre: String) {
        pestanaAntesDeSeguimiento = pestana.name
        seguimientoPendienteTipo = tipo
        seguimientoPendienteId = id
        seguimientoPendienteNombre = nombre
        errorSeguimientoPendiente = null
        mensajeAcceso = "Inicia sesión con Google y volverás siguiendo a $nombre."
        tiendaSeleccionada = null
        mostrarDirectorio = false
        asociacionInicialId = null
        pestana = PestanaComprador.CUENTA
    }

    fun responderEleccionEspacioEmprendedora(abrirEspacio: Boolean) {
        val uidElegido = uidEmprendedoraParaElegir ?: return
        if (auth.currentUser?.uid != uidElegido) {
            uidEmprendedoraParaElegir = null
            return
        }
        alRegistrarEleccionDeModo(uidElegido)
        uidEmprendedoraParaElegir = null
        if (abrirEspacio) alAbrirEspacioEmprendedora()
    }

    fun abrirEnlace(uri: Uri) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            avisoContacto = "No hay una aplicación disponible para abrir este medio de contacto."
        }
    }

    fun abrirWhatsApp(tienda: TiendaCatalogoComprador, mensaje: String) {
        val telefono = (tienda.whatsappNumero.ifBlank { tienda.contacto?.takeIf { it.tipo == "whatsapp" }?.valor.orEmpty() }).filter(Char::isDigit)
        if (telefono.isBlank()) {
            avisoContacto = "Esta tienda no tiene un número de WhatsApp público configurado."
            return
        }
        val uri = Uri.parse("https://wa.me/$telefono?text=${Uri.encode(mensaje)}")
        abrirEnlace(uri)
    }

    fun abrirFormularioContacto(tienda: TiendaCatalogoComprador, producto: String? = null, variante: VarianteCatalogoComprador? = null) {
        formularioContacto = Triple(tienda, producto, variante)
    }

    fun abrirCorreo(tienda: TiendaCatalogoComprador, producto: String?) {
        val direccion = tienda.correoDireccion.ifBlank { tienda.contacto?.takeIf { it.tipo == "correo" }?.valor.orEmpty() }
        if (direccion.isBlank()) {
            avisoContacto = "Esta tienda no tiene un correo público configurado."
            return
        }
        val asunto = producto?.let { "Consulta sobre $it" } ?: "Consulta sobre tu tienda"
        val enlace = Uri.fromParts("mailto", direccion, null).buildUpon()
            .appendQueryParameter("subject", asunto)
            .build()
        abrirEnlace(enlace)
        formularioContacto = null
    }

    fun contactar(tienda: TiendaCatalogoComprador, producto: String? = null, variante: VarianteCatalogoComprador? = null) {
        if (tienda.whatsappHabilitado || tienda.formularioHabilitado || tienda.correoHabilitado) {
            abrirFormularioContacto(tienda, producto, variante)
        } else {
            avisoContacto = "Esta tienda aún no configuró una vía de contacto disponible."
        }
    }

    fun enviarFormularioContacto(tienda: TiendaCatalogoComprador, producto: String?, nombre: String, correo: String, mensaje: String) {
        formularioContacto = null
        alcance.launch {
            try {
                val operacionId = ApiConsultasComprador.enviar(tienda.id, producto, nombre, correo, mensaje)
                misConsultas = (listOf(ConsultaSeguida(tienda.id, tienda.nombre, operacionId, correo, producto, mensaje)) + misConsultas).distinctBy { it.operacionId }
                guardarConsultas(context, misConsultas)
                avisoContacto = "Tu consulta se envió a la bandeja de la emprendedora."
            } catch (e: Exception) {
                avisoContacto = e.message ?: "No se pudo enviar la consulta. Inténtalo de nuevo."
            }
        }
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

    val estadoPolitica = evaluarPoliticaNavegacion(
        modo = modoNavegacion,
        haySesion = sesion != null,
        sesionGoogle = auth.currentUser?.providerData.orEmpty().any { it.providerId == "google.com" },
        omitioInvitacion = omitioInvitacion,
    )
    val exigeGoogle = modoNavegacion == "exigir" && estadoPolitica.bloqueaCatalogo
    val invitaGoogle = estadoPolitica.muestraInvitacion
    val catalogoRestringido = estadoPolitica.bloqueaCatalogo || invitaGoogle
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
                if (modoNavegacion != null) TextButton(
                    onClick = { if (invitaGoogle) omitioInvitacion = true else abrirAcceso() }
                ) {
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
                alVolver = if (pestana != PestanaComprador.INICIO) ({
                if (mapaFeriasVisible) mapaFeriasVisible = false
                else pestana = PestanaComprador.INICIO
            }) else null,
                respuestasNuevas = misConsultas.count { it.respuesta != null && !it.respuestaVista },
                alVerRespuestas = { pestana = PestanaComprador.RESPUESTAS }
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
                alVerDirectorio = { asociacionesPrimero = false; asociacionInicialId = null; mostrarDirectorio = true },
                alVerAsociaciones = { asociacionesPrimero = true; asociacionInicialId = null; mostrarDirectorio = true },
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

            PestanaComprador.FERIAS -> {
                if (mapaFeriasVisible) {
                    PantallaMapaFerias(
                        ferias = listaFerias,
                        cargando = feriasCargando,
                        error = feriasError,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inset)
                            .padding(horizontal = 16.dp)
                    )
                } else {
                    PantallaFeriasComprador(
                        ferias = listaFerias,
                        cargando = feriasCargando,
                        error = feriasError,
                        alDetalle = { feriaSeleccionada = it },
                        alVerMapa = { mapaFeriasVisible = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inset)
                            .padding(horizontal = 16.dp)
                    )
                }
            }

            PestanaComprador.RESPUESTAS -> RespuestasCompradorScreen(
                consultas = misConsultas,
                cargando = cargandoRespuestas,
                error = errorRespuestas,
                alActualizar = {
                    alcance.launch {
                        cargandoRespuestas = true
                        errorRespuestas = null
                        try {
                            misConsultas = actualizarRespuestas(misConsultas)
                            guardarConsultas(context, misConsultas)
                        } catch (e: Exception) {
                            errorRespuestas = e.message ?: "No se pudieron actualizar tus respuestas."
                        } finally {
                            cargandoRespuestas = false
                        }
                    }
                },
                alMarcarVistas = {
                    val actualizadas = misConsultas.map { consulta ->
                        if (consulta.respuesta != null) consulta.copy(respuestaVista = true) else consulta
                    }
                    if (actualizadas != misConsultas) {
                        misConsultas = actualizadas
                        guardarConsultas(context, actualizadas)
                    }
                }
            )

            PestanaComprador.CUENTA -> PantallaCuentaComprador(
                sesion = sesion,
                auth = auth,
                avisosFerias = avisosFerias,
                cargandoAvisos = cargandoAvisos,
                alCambiarAvisos = solicitarAvisos,
                googleCargando = ingresando,
                mensaje = mensajeAcceso,
                alRefrescarPerfil = {
                    sesion = auth.currentUser?.let(::sesionDesdeUsuario)
                },
                alExplorar = { pestana = PestanaComprador.INICIO },
                alCerrarSesion = {
                    alcance.launch {
                        uid?.let { runCatching { PreferenciasAvisosComprador.desuscribirTodos(firestore, it) } }
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

    if (uidEmprendedoraParaElegir != null && uidEmprendedoraParaElegir == uid) {
        AlertDialog(
            onDismissRequest = { responderEleccionEspacioEmprendedora(abrirEspacio = false) },
            title = { Text("¿Quieres ir a tu espacio?") },
            text = {
                Text("Esta cuenta también tiene un emprendimiento. Puedes administrarlo ahora o seguir explorando como compradora.")
            },
            confirmButton = {
                TextButton(onClick = { responderEleccionEspacioEmprendedora(abrirEspacio = true) }) {
                    Text("Ir a mi espacio")
                }
            },
            dismissButton = {
                TextButton(onClick = { responderEleccionEspacioEmprendedora(abrirEspacio = false) }) {
                    Text("Seguir como compradora")
                }
            }
        )
    }

    errorSeguimientoPendiente?.let { mensaje ->
        AlertDialog(
            onDismissRequest = { errorSeguimientoPendiente = null },
            title = { Text("No se completó el seguimiento") },
            text = { Text(mensaje) },
            confirmButton = {
                TextButton(onClick = {
                    errorSeguimientoPendiente = null
                    reintentoSeguimientoPendiente += 1
                }) { Text("Reintentar") }
            },
            dismissButton = {
                TextButton(onClick = { errorSeguimientoPendiente = null }) { Text("Ahora no") }
            }
        )
    }

    if (mostrarDirectorio) {
        DirectorioComprador(
            tiendas = directorio,
            asociaciones = asociaciones,
            tipos = tiposEmprendimiento,
            sectores = sectores,
            cargando = cargando,
            error = error,
            asociacionesIniciales = asociacionesPrimero,
            asociacionInicialId = asociacionInicialId,
            usuarioId = uid,
            firestore = firestore,
            alCerrar = { mostrarDirectorio = false; asociacionInicialId = null },
            alAbrir = { tiendaSeleccionada = it; mostrarDirectorio = false; asociacionInicialId = null },
            alPedirInicioSesion = { asociacionId, nombre ->
                pedirInicioSesionParaSeguir("asociacion", asociacionId, nombre)
            }
        )
    }
    directorio.firstOrNull { it.id == tiendaSeleccionada }?.let { tienda ->
        if (productoSeleccionado == null) FichaTiendaComprador(
            tienda = tienda,
            tipo = tiposEmprendimiento[tienda.tipoId].orEmpty(),
            sector = sectores[tienda.sectorId].orEmpty(),
            productos = productos.filter { it.tiendaId == tienda.id },
            usuarioId = uid,
            firestore = firestore,
            alPedirInicioSesion = { pedirInicioSesionParaSeguir("tienda", tienda.id, tienda.nombre) },
            alConsultar = { contactar(tienda) },
            alCompartir = {
                com.example.amancay.ui.BotonCompartirEnlace("Compartir tienda", tienda.nombre, com.example.amancay.ui.EnlacesPublicos.tienda(tienda.id))
            },
            alMostrarQr = {
                com.example.amancay.ui.BotonCodigoQr("Código QR", tienda.nombre, com.example.amancay.ui.EnlacesPublicos.tienda(tienda.id))
            },
            alComoLlegar = {
                tienda.puntoRetiro?.let { punto ->
                    val destino = FeriaComprador(
                        id = tienda.id, nombre = tienda.nombre, descripcion = "", organizador = "",
                        direccion = punto.descripcion, fechaInicio = null, fechaFin = null,
                        latitud = punto.latitud, longitud = punto.longitud
                    )
                    if (!abrirComoLlegar(context, destino)) avisoContacto = "No se pudo abrir la aplicación de mapas."
                }
            },
            alAbrirProducto = { productoSeleccionado = it },
            alVolver = { tiendaSeleccionada = null; mostrarDirectorio = true }
        )
    }
    productoSeleccionado?.let { seleccionado ->
        val producto = productos.firstOrNull { it.id == seleccionado.id && it.tiendaId == seleccionado.tiendaId } ?: seleccionado
        DialogoDetalleProducto(
            producto = producto,
            calificacionesActivas = directorio.firstOrNull { it.id == producto.tiendaId }?.calificacionesHabilitadas == true,
            usuarioId = uid,
            firestore = firestore,
            alCerrar = { productoSeleccionado = null },
            alEncargar = { varianteElegida ->
                productoSeleccionado = null
                val tienda = directorio.firstOrNull { it.id == producto.tiendaId }
                if (tienda == null) avisoContacto = "No encontramos los datos públicos de contacto de esta tienda."
                else contactar(tienda, producto.nombre, varianteElegida)
            },
            alVerProductoCompleto = {
                productoSeleccionado = null
                abrirEnlace(Uri.parse(com.example.amancay.ui.EnlacesPublicos.producto(producto.tiendaId, producto.id)))
            }
        )
    }
    formularioContacto?.let { (tienda, nombreProducto, varianteElegida) ->
        val fichaProducto = productos.firstOrNull { it.tiendaId == tienda.id && it.nombre == nombreProducto }
        FormularioContactoComprador(
            tienda = tienda,
            producto = nombreProducto,
            fichaProducto = fichaProducto,
            variante = varianteElegida,
            correoSesion = sesion?.correo?.takeIf(String::isNotBlank),
            nombreSesion = sesion?.nombre?.takeIf(String::isNotBlank),
            alCerrar = { formularioContacto = null },
            alWhatsApp = {
                formularioContacto = null
                abrirWhatsApp(tienda, "Hola, me interesa ${nombreProducto ?: "conocer tu emprendimiento"}${varianteElegida?.opciones?.values?.joinToString(" · ")?.let { " ($it)" }.orEmpty()}.")
            },
            alCorreo = { abrirCorreo(tienda, nombreProducto) },
            alEnviar = { nombre, correo, mensaje ->
                enviarFormularioContacto(tienda, nombreProducto, nombre, correo, mensaje)
            }
        )
    }
    avisoContacto?.let { mensaje ->
        AlertDialog(
            onDismissRequest = { avisoContacto = null },
            title = { Text("Contacto") },
            text = { Text(mensaje) },
            confirmButton = { TextButton(onClick = { avisoContacto = null }) { Text("Entendido") } },
            containerColor = AmancaySuperficie
        )
    }
    feriaSeleccionada?.let { feria ->
        DialogoDetalleFeria(
            feria = feria,
            alCerrar = { feriaSeleccionada = null }
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
    alVerAsociaciones: () -> Unit,
    alVerFerias: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HeroBienvenida(alExplorar = alVerTodo) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Explora nuestra comunidad", style = MaterialTheme.typography.titleLarge, color = AmancayBordo, fontWeight = FontWeight.Bold)
                Text("Conecta con emprendimientos y asociaciones locales.", color = AmancayTintaSuave)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(onClick = alVerDirectorio, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AmancayChipRosa)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = AmancayBordo, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Emprendimientos", color = AmancayBordo, fontWeight = FontWeight.Bold)
                            Text("Descubre productos únicos creados por nuestra comunidad.", color = AmancayTintaSuave, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(onClick = alVerAsociaciones, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AmancayChipDurazno)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Icon(Icons.Outlined.Groups, contentDescription = null, tint = AmancayBordo, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Asociaciones", color = AmancayBordo, fontWeight = FontWeight.Bold)
                            Text("Conoce sus organizaciones e integrantes.", color = AmancayTintaSuave, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { BuscadorAmancay(valor = busqueda, alCambiar = alBuscar) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Categorías populares", style = MaterialTheme.typography.titleLarge, color = AmancayBordo, fontWeight = FontWeight.Bold)
                TextButton(onClick = alVerTodo) { Text("Ver todas →", color = AmancayBordo) }
            }
        }
        item {
            FilaCategorias(
                categorias = categorias,
                seleccionada = categoriaSeleccionada,
                alSeleccionar = alSeleccionarCategoria
            )
        }
        item { TarjetaFraseComunidad() }
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

@Composable
private fun TarjetaFraseComunidad() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = AmancayChipDurazno)) {
        Row(Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(com.example.amancay.R.drawable.flor_inicio_comprador),
                contentDescription = null,
                modifier = Modifier.size(66.dp),
                contentScale = ContentScale.Fit
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hecho con cariño, cerca de ti", color = AmancayBordo, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text("♡  NUESTRA COMUNIDAD FLORECE", color = AmancayBordo, style = MaterialTheme.typography.labelSmall)
            }
            Image(
                painter = painterResource(com.example.amancay.R.drawable.flor_inicio_comprador),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                contentScale = ContentScale.Fit
            )
        }
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
