package com.example.amancay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import com.example.amancay.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.amancay.buyer.ModoCompradorScreen
import com.example.amancay.emprendedora.ModoEmprendedoraConectada
import com.example.amancay.emprendedora.BandejaConsultasScreen
import com.example.amancay.data.AmancayRepository
import com.example.amancay.data.DraftState
import com.example.amancay.data.FirebaseClientServicesProvider
import com.example.amancay.data.CuentaEmprendedora
import com.example.amancay.data.OpcionCatalogoEmprendedora
import com.example.amancay.data.ProductoEmprendedora
import com.example.amancay.data.ProductosEmprendedoraRepository
import com.example.amancay.data.FeriaInvitation
import com.example.amancay.data.FeriasEmprendedoraRepository
import com.example.amancay.data.VentaRegistrada
import com.example.amancay.data.FeriaParaVenta
import com.example.amancay.data.PreferenciasCatalogoEmprendedora
import com.example.amancay.data.ColaOfflineCatalogo
import com.example.amancay.data.TiendaEmprendedora
import com.example.amancay.data.ImagenProductoPreparada
import com.example.amancay.emprendedora.leerLogoLocalTienda
import com.example.amancay.data.Product
import com.example.amancay.data.ProductType
import com.example.amancay.data.ShopSettings
import com.example.amancay.data.Variant
import com.example.amancay.data.FirebaseClientServices
import com.example.amancay.data.CatalogoCompradorRepository
import com.example.amancay.data.crearCatalogoCompradorFirebase
import com.example.amancay.data.crearRepositorioDemostracion
import com.example.amancay.data.decodificarVistaPreviaProducto
import com.example.amancay.emprendedora.PantallaEdicionTienda
import com.example.amancay.emprendedora.ProductosEmprendedoraPanel
import com.example.amancay.emprendedora.OrganizarCatalogoScreen
import com.example.amancay.emprendedora.AvisoEmprendedora
import com.example.amancay.emprendedora.DialogoAjusteStock
import com.example.amancay.emprendedora.mensajeProducto
import com.example.amancay.emprendedora.VentasEmprendedoraScreen
import com.example.amancay.emprendedora.ConfiguracionVistaPreviaScreen
import com.example.amancay.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.amancay.ui.theme.AmancayTheme
import com.example.amancay.ui.theme.Cream
import com.example.amancay.ui.theme.CardPink
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import kotlinx.coroutines.launch

private data class MiniaturaProductoCache(val firmaImagenes: String, val bitmap: Bitmap)

private data class PasoGuiaEmprendedora(
    val idObjetivo: String,
    val nombreObjetivo: String,
    val titulo: String,
    val explicacion: String
)

private fun firmaImagenes(producto: ProductoEmprendedora) = producto.imagenes.joinToString(",")

private fun mensajeCargaVentas(error: Exception, recurso: String): String {
    val firestoreError = generateSequence(error as Throwable?) { it.cause }
        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
        .firstOrNull()
    return when (firestoreError?.code) {
        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firebase no autorizó la lectura de $recurso. Revisa los permisos de esta cuenta y vuelve a intentarlo."
        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
            "No hay conexión con Firebase para cargar $recurso. Comprueba la conexión y vuelve a intentarlo."
        else -> "No se pudo cargar $recurso. Vuelve a intentarlo."
    }
}

class MainActivity : FragmentActivity() {
    private var feriaPushPendiente by mutableStateOf<String?>(null)
    private var consultasPushTiendaPendiente by mutableStateOf<String?>(null)
    private var abrirInvitacionesFeriaPushPendiente by mutableStateOf(false)
    private var feriaInvitacionPushPendiente by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recibirDestinoPush(intent)
        enableEdgeToEdge()
        setContent {
            AmancayTheme(darkTheme = false) {
                val feriaId = feriaPushPendiente
                val tiendaConsultaId = consultasPushTiendaPendiente
                val abrirInvitacionesFeria = abrirInvitacionesFeriaPushPendiente
                val feriaInvitacionId = feriaInvitacionPushPendiente
                AmancayApp(
                    feriaIdPush = feriaId,
                    consultasTiendaIdPush = tiendaConsultaId,
                    abrirInvitacionesFeriaPush = abrirInvitacionesFeria,
                    feriaInvitacionIdPush = feriaInvitacionId,
                    alConsumirFeriaPush = {
                        if (feriaPushPendiente == feriaId) feriaPushPendiente = null
                    },
                    alConsumirConsultasPush = {
                        if (consultasPushTiendaPendiente == tiendaConsultaId) consultasPushTiendaPendiente = null
                    },
                    alConsumirInvitacionesFeriaPush = {
                        if (abrirInvitacionesFeriaPushPendiente == abrirInvitacionesFeria) {
                            abrirInvitacionesFeriaPushPendiente = false
                        }
                        if (feriaInvitacionPushPendiente == feriaInvitacionId) {
                            feriaInvitacionPushPendiente = null
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recibirDestinoPush(intent)
    }

    private fun recibirDestinoPush(intent: Intent) {
        feriaPushPendiente = intent.getStringExtra(EXTRA_FERIA_ID)?.takeIf(String::isNotBlank)
        consultasPushTiendaPendiente = intent.getStringExtra(EXTRA_CONSULTAS_TIENDA_ID)?.takeIf(String::isNotBlank)
            ?: intent.getStringExtra("tiendaId")?.takeIf { intent.getStringExtra("tipo") == "consulta" }
        val tipoAviso = intent.getStringExtra("tipo")
        abrirInvitacionesFeriaPushPendiente = intent.getBooleanExtra(EXTRA_ABRIR_INVITACIONES_FERIA, false)
            || tipoAviso == "invitacion_feria"
            || tipoAviso == "recordatorio_invitacion_feria"
            || tipoAviso == "aviso_confirmacion_feria"
        feriaInvitacionPushPendiente = intent.getStringExtra(EXTRA_FERIA_INVITACION_ID)?.takeIf(String::isNotBlank)
            ?: if (abrirInvitacionesFeriaPushPendiente) intent.getStringExtra(EXTRA_FERIA_ID)?.takeIf(String::isNotBlank) else null
    }

    companion object {
        const val EXTRA_FERIA_ID = "com.example.amancay.EXTRA_FERIA_ID"
        const val EXTRA_CONSULTAS_TIENDA_ID = "com.example.amancay.EXTRA_CONSULTAS_TIENDA_ID"
        const val EXTRA_ABRIR_INVITACIONES_FERIA = "com.example.amancay.EXTRA_ABRIR_INVITACIONES_FERIA"
        const val EXTRA_FERIA_INVITACION_ID = "com.example.amancay.EXTRA_FERIA_INVITACION_ID"
    }
}

@Composable
fun AmancayApp(
    feriaIdPush: String? = null,
    consultasTiendaIdPush: String? = null,
    abrirInvitacionesFeriaPush: Boolean = false,
    feriaInvitacionIdPush: String? = null,
    alConsumirFeriaPush: () -> Unit = {},
    alConsumirConsultasPush: () -> Unit = {},
    alConsumirInvitacionesFeriaPush: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefsTexto = remember(context) { context.getSharedPreferences("amancay_preferencias_visuales", Context.MODE_PRIVATE) }
    var escalaTexto by remember { mutableFloatStateOf(prefsTexto.getFloat("escala_texto", 1f)) }
    val firebaseServices = remember(context) { FirebaseClientServicesProvider.initialize(context) }
    val catalogoComprador = remember(firebaseServices) { crearCatalogoCompradorFirebase(firebaseServices.firestore) }
    DisposableEffect(catalogoComprador) {
        onDispose { catalogoComprador.cerrar() }
    }
    var espacioEmprendedora by remember { mutableStateOf(false) }
    LaunchedEffect(feriaIdPush, consultasTiendaIdPush, abrirInvitacionesFeriaPush, feriaInvitacionIdPush) {
        when {
            !feriaIdPush.isNullOrBlank() -> espacioEmprendedora = false
            abrirInvitacionesFeriaPush || !feriaInvitacionIdPush.isNullOrBlank() -> espacioEmprendedora = true
            !consultasTiendaIdPush.isNullOrBlank() -> espacioEmprendedora = true
        }
    }
    val densidad = LocalDensity.current
    val cambiarEscalaTexto: (Float) -> Unit = { escala ->
        escalaTexto = escala
        prefsTexto.edit().putFloat("escala_texto", escala).apply()
    }
    CompositionLocalProvider(LocalDensity provides Density(densidad.density, densidad.fontScale * escalaTexto)) {
        ContenidoAmancay(
            espacioEmprendedora = espacioEmprendedora,
            firebaseServices = firebaseServices,
            catalogoComprador = catalogoComprador,
            escalaTexto = escalaTexto,
            alCambiarEscalaTexto = cambiarEscalaTexto,
            alCambiarEspacio = { espacioEmprendedora = it },
            feriaIdPush = feriaIdPush,
            alConsumirFeriaPush = alConsumirFeriaPush,
            consultasTiendaIdPush = consultasTiendaIdPush,
            alConsumirConsultasPush = alConsumirConsultasPush,
            abrirInvitacionesFeriaPush = abrirInvitacionesFeriaPush,
            feriaInvitacionIdPush = feriaInvitacionIdPush,
            alConsumirInvitacionesFeriaPush = alConsumirInvitacionesFeriaPush
        )
    }
}

@Composable
private fun ContenidoAmancay(
    espacioEmprendedora: Boolean,
    firebaseServices: FirebaseClientServices,
    catalogoComprador: CatalogoCompradorRepository,
    escalaTexto: Float,
    alCambiarEscalaTexto: (Float) -> Unit,
    alCambiarEspacio: (Boolean) -> Unit,
    feriaIdPush: String?,
    alConsumirFeriaPush: () -> Unit,
    consultasTiendaIdPush: String?,
    alConsumirConsultasPush: () -> Unit,
    abrirInvitacionesFeriaPush: Boolean,
    feriaInvitacionIdPush: String?,
    alConsumirInvitacionesFeriaPush: () -> Unit
) {
    var uidConEleccionDeModo by remember { mutableStateOf<String?>(null) }
    DisposableEffect(firebaseServices.auth) {
        val escuchaSesion = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser == null) uidConEleccionDeModo = null
        }
        firebaseServices.auth.addAuthStateListener(escuchaSesion)
        onDispose { firebaseServices.auth.removeAuthStateListener(escuchaSesion) }
    }

    if (espacioEmprendedora) {
        ModoEmprendedoraConectada(
            firebase = firebaseServices,
            webClientId = BuildConfig.WEB_CLIENT_ID,
            escalaTexto = escalaTexto,
            alCambiarEscalaTexto = alCambiarEscalaTexto,
            alVolver = { alCambiarEspacio(false) },
            consultasTiendaIdPush = consultasTiendaIdPush,
            alConsumirConsultasPush = alConsumirConsultasPush,
            abrirInvitacionesFeriaPush = abrirInvitacionesFeriaPush,
            feriaInvitacionIdPush = feriaInvitacionIdPush,
            alConsumirInvitacionesFeriaPush = alConsumirInvitacionesFeriaPush
        )
    } else {
        ModoCompradorScreen(
            repositorio = catalogoComprador,
            auth = firebaseServices.auth,
            firestore = firebaseServices.firestore,
            webClientId = BuildConfig.WEB_CLIENT_ID,
            uidConEleccionDeModo = uidConEleccionDeModo,
            alRegistrarEleccionDeModo = { uidConEleccionDeModo = it },
            alAbrirEspacioEmprendedora = { alCambiarEspacio(true) },
            feriaIdPush = feriaIdPush,
            alConsumirFeriaPush = alConsumirFeriaPush
        )
    }
}

enum class Tab(val label: String, val icon: Int) {
    INICIO("Inicio", Fig.NAV_INICIO),
    PRODUCTOS("Productos", Fig.NAV_PRODUCTOS),
    AGREGAR("Agregar", Fig.NAV_VENDER),
    MAS("Más", Fig.NAV_MAS)
}

sealed interface Screen {
    data object Home : Screen
    data object Products : Screen
    data object OrganizarCatalogo : Screen
    data object Sell : Screen
    data object Sale : Screen
    data object History : Screen
    data class Preview(val productId: String?) : Screen
    data object Settings : Screen
    data object ConfiguracionCatalogo : Screen
    data object Ventas : Screen
    data object Consultas : Screen
    data object Ferias : Screen
    data class FeriaDetalle(val invitacionId: String?) : Screen
    data object Camara : Screen
}

@Composable
private fun EmprendedoraApp(repositorio: AmancayRepository, onExit: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.INICIO) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var sellStep by remember { mutableIntStateOf(1) }
    var draftBump by remember { mutableIntStateOf(0) }
    val draft = remember { DraftState() }
    val bump: () -> Unit = { draftBump += 1 }
    @Suppress("UNUSED_EXPRESSION") draftBump

    val productos by repositorio.productos.collectAsState()
    val ventas by repositorio.ventas.collectAsState()
    val settings by repositorio.settings.collectAsState()
    val borradorGuardado by repositorio.borradorGuardado.collectAsState()
    val compartido by repositorio.enlaceCompartido.collectAsState()
    val invitaciones by repositorio.invitaciones.collectAsState()
    val eventos by repositorio.eventosCatalogo.collectAsState()

    var showPublishedDialog by remember { mutableStateOf(false) }
    var showSaleDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val activity = LocalContext.current as FragmentActivity
    val prefs = remember { activity.getSharedPreferences("amancay_prefs", Context.MODE_PRIVATE) }

    val puedeHuella = remember {
        BiometricManager.from(activity)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var bloqueado by remember {
        mutableStateOf(prefs.getBoolean("huella", false) && puedeHuella)
    }
    var bloqueoError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (prefs.getBoolean("huella", false)) {
            repositorio.updateSettings(repositorio.settings.value.copy(huella = true))
        }
    }

    fun pedirHuella() {
        bloqueoError = null
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    bloqueado = false
                }
                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        bloqueoError = err.toString()
                    }
                }
                override fun onAuthenticationFailed() {
                    bloqueoError = "Huella no reconocida, intenta de nuevo"
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Rincón Amancay")
            .setSubtitle("Confirma tu huella para entrar")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    fun go(s: Screen, t: Tab? = null, keepTab: Boolean = false) {
        screen = s
        if (keepTab) return
        if (t != null) tab = t
        else tab = when (s) {
            is Screen.Home -> Tab.INICIO
            is Screen.Products, is Screen.OrganizarCatalogo, is Screen.Preview -> Tab.PRODUCTOS
            is Screen.Sell, is Screen.Sale, is Screen.History, is Screen.Camara -> tab
            is Screen.Ventas -> Tab.INICIO
            is Screen.Consultas -> Tab.INICIO
            is Screen.Settings -> Tab.MAS
            is Screen.ConfiguracionCatalogo -> Tab.MAS
            is Screen.Ferias -> Tab.INICIO
            is Screen.FeriaDetalle -> Tab.INICIO
        }
    }

    fun startNew() {
        draft.reset(null)
        val cfg = settings
        val ultimo = productos.lastOrNull()
        if (cfg.valoresPorDefecto && ultimo != null) {
            draft.material = ultimo.material
            draft.tamano = ultimo.tamano
            draft.cuidados = ultimo.cuidados
        }
        draft.disponibleEncargo = cfg.aceptarPedidos
        sellStep = 1; bump()
        go(Screen.Sell, keepTab = true)
    }
    fun continueDraft() {
        val b = borradorGuardado
        if (b != null) {
            draft.reset(b)
            sellStep = 1; bump()
            go(Screen.Sell, keepTab = true)
        }
    }
    fun editProduct(p: Product) {
        draft.reset(p)
        sellStep = 1; bump()
        go(Screen.Sell, keepTab = true)
    }
    fun saveExit() {
        val p = draft.toProduct()
        repositorio.guardarBorrador(p)
        draft.reset(null); bump()
        go(Screen.Home)
        scope.launch { snackbar.showSnackbar("Borrador guardado. Puedes volver después") }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            draft.photoUri = uri
            draft.photoBitmap = null
            draft.fotoLista = true
            bump()
            if (screen == Screen.Camara) {
                sellStep = 1
                go(Screen.Sell, keepTab = true)
            }
        }
    }

    if (bloqueado) {
        BloqueoHuellaScreen(
            error = bloqueoError,
            onHuella = { pedirHuella() },
            onSinHuella = { bloqueado = false }
        )
    } else {
    Scaffold(
        containerColor = Cream,
        topBar = { if (screen !is Screen.Camara && screen !is Screen.Sell) AmancayTopBar(onHelp = { showHelp = true }, onExit = onExit) },
        bottomBar = {
            if (screen !is Screen.Camara && screen !is Screen.Sell) {
            Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = Color.White, shadowElevation = 8.dp) {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val selected = tab == t
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            when (t) {
                                Tab.INICIO -> go(Screen.Home, t)
                                Tab.PRODUCTOS -> go(Screen.Products, t)
                                Tab.AGREGAR -> startNew()
                                Tab.MAS -> go(Screen.Settings, t)
                            }
                        },
                        icon = {
                            Image(
                                painter = painterResource(t.icon),
                                contentDescription = t.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                t.label, fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Wine else Muted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = CardPink)
                    )
                }
            }
            }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Wine, contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = screen
            val key: Any = if (current is Screen.Sell) "vender/$sellStep" else current.toString()
            ScreenTransition(key = key) {
            when (val s = current) {
                is Screen.Home -> HomeScreen(
                    borrador = borradorGuardado,
                    publicados = productos.count { it.publicado },
                    borradores = productos.count { it.esBorrador },
                    ventasCount = ventas.size,
                    linkCompartido = compartido,
                    invitacionesPendientes = invitaciones.count { it.estado == "pendiente" },
                    onAgregar = { startNew() },
                    onContinuarBorrador = { continueDraft() },
                    onMisProductos = { go(Screen.Products) },
                    onVerCatalogo = { go(Screen.Preview(productos.firstOrNull { it.publicado }?.id)) },
                    onVerFerias = { go(Screen.Ferias) }
                )
                is Screen.Products -> ProductsScreen(
                    productos = productos,
                    onEdit = { editProduct(it) },
                    onDelete = {
                        repositorio.eliminarProducto(it.id)
                        scope.launch { snackbar.showSnackbar("Producto eliminado") }
                    },
                    onCreate = { startNew() },
                    onPreview = { go(Screen.Preview(it.id)) },
                    settings = settings,
                    onSettings = { repositorio.updateSettings(it) }
                )
                is Screen.OrganizarCatalogo -> Text("Organizar catálogo está disponible en el espacio conectado de emprendedora.", modifier = Modifier.padding(24.dp), color = Muted)
                is Screen.ConfiguracionCatalogo -> Text("La configuración del catálogo está disponible con la sesión conectada.", modifier = Modifier.padding(24.dp), color = Muted)
                is Screen.Preview -> {
                    val p = s.productId?.let { repositorio.obtener(it) } ?: productos.firstOrNull { it.publicado }
                    PreviewCatalogScreen(
                        producto = p,
                        onShare = {
                            clipboard.setText(AnnotatedString(EnlacesPublicos.inicio()))
                            repositorio.marcarEnlaceCompartido()
                            scope.launch { snackbar.showSnackbar("Enlace de tu tienda copiado") }
                        },
                        onContact = { scope.launch { snackbar.showSnackbar("Te contactarán por WhatsApp") } },
                        onBack = { go(Screen.Products) },
                        nombreTienda = "Tu tienda"
                    )
                }
                is Screen.Sale -> RegisterSaleScreen(
                    productos = productos,
                    onConfirm = { prod, vari, qty ->
                        repositorio.registrarVenta(prod, vari, qty)
                        showSaleDialog = true
                    },
                    onVerHistorial = { go(Screen.History) }
                )
                is Screen.History -> SalesHistoryScreen(ventas, eventos)
                is Screen.Camara -> CameraScreen(
                    onFoto = { bmp ->
                        draft.photoBitmap = bmp
                        draft.photoUri = null
                        draft.fotoLista = true
                        sellStep = 1
                        go(Screen.Sell, keepTab = true)
                    },
                    onCancelar = { go(Screen.Sell, keepTab = true) },
                    onGaleria = { galleryLauncher.launch("image/*") }
                )
                is Screen.Settings -> SettingsScreen(
                    settings,
                    onChange = { nuevo ->
                        if (nuevo.huella && !settings.huella) {
                            if (puedeHuella) {
                                repositorio.updateSettings(nuevo)
                                prefs.edit().putBoolean("huella", true).apply()
                                scope.launch { snackbar.showSnackbar("Huella activada") }
                            } else {
                                scope.launch { snackbar.showSnackbar("Este equipo no tiene huella disponible") }
                            }
                        } else {
                            repositorio.updateSettings(nuevo)
                            prefs.edit().putBoolean("huella", nuevo.huella).apply()
                        }
                    },
                    onGuardar = { scope.launch { snackbar.showSnackbar("Valores por defecto guardados") } }
                )
                is Screen.Ferias -> FeriasScreen(
                    invitaciones = invitaciones,
                    onVerDetalle = { go(Screen.FeriaDetalle(it.id)) },
                    onVolver = { go(Screen.Home) }
                )
                is Screen.FeriaDetalle -> FeriaDetalleScreen(
                    invitacion = s.invitacionId?.let { id -> invitaciones.firstOrNull { it.id == id } },
                    onAceptar = {
                        repositorio.responderFeria(it.id, true)
                        scope.launch { snackbar.showSnackbar("Inscripción confirmada") }
                    },
                    onRechazar = {
                        repositorio.responderFeria(it.id, false)
                        scope.launch { snackbar.showSnackbar("Invitación rechazada") }
                    },
                    onVolver = { go(Screen.Ferias) }
                )
                is Screen.Sell -> {
                    val d = draft
                    when (sellStep) {
                        1 -> PhotoStep(d, bump,
                            onTakePhoto = { go(Screen.Camara, keepTab = true) },
                            onPickPhoto = { galleryLauncher.launch("image/*") },
                            onNext = { sellStep = 2 }, onBack = { go(Screen.Home) }, onSaveExit = { saveExit() })
                        2 -> BasicsStep(d, bump,
                            onNext = { sellStep = 3 }, onBack = { sellStep = 1 }, onSaveExit = { saveExit() })
                        3 -> DetailsStep(d, bump,
                            onNext = { sellStep = 4 }, onBack = { sellStep = 2 }, onSaveExit = { saveExit() })
                        4 -> PriceStep(d, bump,
                            onNext = { sellStep = 5 }, onBack = { sellStep = 3 }, onSaveExit = { saveExit() })
                        else -> ReviewStep(d, bump,
                            onPublish = {
                                val p = d.toProduct()
                                repositorio.guardarProducto(p)
                                draft.reset(null); bump()
                                showPublishedDialog = true
                            },
                            onBack = { sellStep = 4 }, onSaveExit = { saveExit() })
                    }
                }
                is Screen.Ventas -> AvisoEmprendedora("Las ventas conectadas están disponibles dentro de la sesión de emprendedora.", false)
                is Screen.Consultas -> AvisoEmprendedora("La bandeja está disponible dentro de la sesión de emprendedora.", false)
            }
            }
        }
    }

    if (showPublishedDialog) {
        AlertDialog(
            onDismissRequest = { showPublishedDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            text = {
                PopIn {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    SuccessCheck()
                    Spacer(Modifier.height(14.dp))
                    Text("Producto publicado", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Dark)
                    Text("Tu producto ya está a la venta.", fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showPublishedDialog = false; go(Screen.Products) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Wine),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) { Text("Ver mis productos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                }
            },
            confirmButton = {}
        )
    }

    if (showSaleDialog) {
        AlertDialog(
            onDismissRequest = { showSaleDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            text = {
                PopIn {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    SuccessCheck()
                    Spacer(Modifier.height(14.dp))
                    Text("Venta registrada", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Dark)
                    Text("El stock fue actualizado.", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showSaleDialog = false; go(Screen.History) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Wine),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) { Text("Ver historial de ventas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                }
            },
            confirmButton = {}
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            text = {
                PopIn {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Wine,
                        modifier = Modifier.size(56.dp).background(Cream, CircleShape).wrapContentSize(Alignment.Center))
                    Spacer(Modifier.height(12.dp))
                    Text("¿Necesitas ayuda?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Dark)
                    Text("Te acompañamos en cada paso de tu tienda.", fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showHelp = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Wine),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) { Text("Entendido", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                }
            },
            confirmButton = {}
        )
    }
    }
}

@Composable
internal fun EmprendedoraAppConectada(
    sesion: CuentaEmprendedora,
    tienda: TiendaEmprendedora,
    sectores: List<OpcionCatalogoEmprendedora>,
    tipos: List<OpcionCatalogoEmprendedora>,
    productosRepository: ProductosEmprendedoraRepository,
    feriasRepository: FeriasEmprendedoraRepository,
    consultasTiendaIdPush: String? = null,
    alConsumirConsultasPush: () -> Unit = {},
    abrirInvitacionesFeriaPush: Boolean = false,
    feriaInvitacionIdPush: String? = null,
    alConsumirInvitacionesFeriaPush: () -> Unit = {},
    huellaHabilitada: Boolean,
    aviso: String?,
    error: String?,
    errorHuella: String?,
    enviando: Boolean,
    alCambiarHuella: (Boolean) -> Unit,
    alEditarTienda: (TiendaEmprendedora) -> Unit,
    alSubirLogo: suspend (archivoId: String, imagen: ImagenProductoPreparada) -> Unit,
    alPublicarTienda: () -> Unit,
    alRetirarTienda: () -> Unit,
    alSalir: () -> Unit,
    escalaTexto: Float,
    alCambiarEscalaTexto: (Float) -> Unit
) {
    val context = LocalContext.current
    val productosBase by productosRepository.productos.collectAsState()
    var revisionColaOffline by remember(sesion.tiendaId) { mutableIntStateOf(0) }
    var cambiosPendientesOffline by remember(sesion.tiendaId) {
        mutableIntStateOf(ColaOfflineCatalogo.cantidadPendiente(context, sesion.tiendaId))
    }
    val productos = remember(productosBase, revisionColaOffline, sesion.tiendaId) {
        ColaOfflineCatalogo.aplicarCambiosLocales(context, sesion.tiendaId, productosBase)
    }
    val tiposProducto by productosRepository.tipos.collectAsState()
    val categoriasProducto by productosRepository.categorias.collectAsState()
    var invitaciones by remember(sesion.uid) { mutableStateOf<List<FeriaInvitation>>(emptyList()) }
    var cargandoInvitaciones by remember(sesion.uid) { mutableStateOf(false) }
    var errorInvitaciones by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var ultimaCargaInvitaciones by remember(sesion.uid) { mutableLongStateOf(0L) }
    var respondiendoInvitacion by remember(sesion.uid) { mutableStateOf(false) }
    var feriaDestacadaId by remember(sesion.uid) { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    suspend fun cargarInvitacionesFeria(forzar: Boolean = false) {
        val ahora = System.currentTimeMillis()
        if (cargandoInvitaciones) return
        if (!forzar && errorInvitaciones == null && ahora - ultimaCargaInvitaciones < 30_000) return
        cargandoInvitaciones = true
        errorInvitaciones = null
        try {
            invitaciones = feriasRepository.listarInvitaciones()
            ultimaCargaInvitaciones = System.currentTimeMillis()
        } catch (e: Exception) {
            errorInvitaciones = e.message ?: "No se pudieron consultar las invitaciones a ferias."
        } finally {
            cargandoInvitaciones = false
        }
    }
    LaunchedEffect(sesion.uid, feriasRepository) { cargarInvitacionesFeria(forzar = true) }
    var tab by remember(sesion.uid) { mutableStateOf(Tab.INICIO) }
    var screen by remember(sesion.uid) { mutableStateOf<Screen>(Screen.Home) }
    var editandoTienda by remember(sesion.uid) { mutableStateOf(false) }
    var editandoProducto by remember(sesion.uid) { mutableStateOf(false) }
    var productoInicialId by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    val preferenciasGuia = remember(context) { context.getSharedPreferences("amancay_guias", Context.MODE_PRIVATE) }
    var guiaActiva by remember(sesion.uid) { mutableStateOf(false) }
    var pasoGuia by remember(sesion.uid) { mutableIntStateOf(0) }
    var guiaSoloPaso by remember(sesion.uid) { mutableStateOf(false) }
    val objetivosGuia = remember(sesion.uid) { mutableStateMapOf<Tab, Rect>() }
    val objetivosInicioGuia = remember(sesion.uid) { mutableStateMapOf<String, Rect>() }
    var objetivoConsultasGuia by remember(sesion.uid) { mutableStateOf<Rect?>(null) }
    var objetivoAyudaGuia by remember(sesion.uid) { mutableStateOf<Rect?>(null) }
    var objetivoTextoGrande by remember(sesion.uid) { mutableStateOf<Rect?>(null) }
    val pasosGuia = remember(sesion.uid) {
        listOf(
            PasoGuiaEmprendedora("inicio", "Inicio", "Este es tu espacio", "En Inicio tienes un resumen de tu tienda y accesos directos a las tareas más comunes."),
            PasoGuiaEmprendedora("invitaciones", "Invitaciones a ferias", "Ferias e invitaciones", "Desde esta tarjeta abres Ferias. Allí puedes revisar cada invitación, conocer los datos de la feria y aceptar o rechazar la participación."),
            PasoGuiaEmprendedora("consultas", "Consultas", "Responde a tus clientas", "Toca el timbre para abrir Consultas. Aquí puedes leer las preguntas que llegan por tus productos o tienda y enviar una respuesta."),
            PasoGuiaEmprendedora("productos", "Productos", "Administra tu catálogo", "En Productos puedes revisar lo publicado, editar información y disponibilidad, y organizar tu catálogo."),
            PasoGuiaEmprendedora("encargos", "Encargos", "Revisa ventas y encargos", "Desde esta tarjeta consultas tus ventas y el historial de encargos. También puedes registrar una venta."),
            PasoGuiaEmprendedora("mi_tienda", "Mi tienda", "Mantén tu tienda al día", "Aquí puedes revisar y personalizar la información de tu tienda, como su nombre, descripción e imagen."),
            PasoGuiaEmprendedora("texto_grande", "Grande", "Haz más cómoda la lectura", "Toca Grande para aumentar el tamaño del texto y elegir una lectura más cómoda."),
            PasoGuiaEmprendedora("ayuda", "?", "Aquí encuentras más ayuda", "Toca este signo cuando lo necesites: podrás elegir un tutorial específico o volver a recorrer la guía completa."),
            PasoGuiaEmprendedora("agregar", "Agregar", "Agrega un producto", "Este botón inicia el formulario para cargar un producto paso por paso.")
        )
    }
    LaunchedEffect(sesion.uid) {
        pasoGuia = 0
        guiaSoloPaso = false
        guiaActiva = !preferenciasGuia.getBoolean("completa:emprendedora_${sesion.uid}", false)
    }
    var linkCompartido by remember(sesion.uid) { mutableStateOf(false) }
    var productoVista by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var fotoVistaProducto by remember(sesion.uid) { mutableStateOf<Bitmap?>(null) }
    var cargandoFotoVista by remember(sesion.uid) { mutableStateOf(false) }
    var errorFotoVista by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var reintentoFotoVista by remember(sesion.uid) { mutableIntStateOf(0) }
    var miniaturasProductos by remember(sesion.uid) { mutableStateOf<Map<String, MiniaturaProductoCache>>(emptyMap()) }
    val firmasImagenesActuales = productos.associate { it.id to firmaImagenes(it) }
    val firmasImagenesAlTerminarCarga by rememberUpdatedState(firmasImagenesActuales)
    val miniaturasProductosVisibles = remember(productos, miniaturasProductos) {
        productos.mapNotNull { producto ->
            miniaturasProductos[producto.id]
                ?.takeIf { it.firmaImagenes == firmaImagenes(producto) }
                ?.let { producto.id to it.bitmap }
        }.toMap()
    }
    val firmaImagenesProductos = productos.joinToString("|") { "${it.id}:${it.imagenes.joinToString(",")}" }
    var enviandoProducto by remember(sesion.uid) { mutableStateOf(false) }
    var avisoProducto by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var errorProducto by remember(sesion.uid) { mutableStateOf<String?>(null) }
    LaunchedEffect(consultasTiendaIdPush, sesion.tiendaId) {
        val tiendaId = consultasTiendaIdPush?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        if (tiendaId == sesion.tiendaId) {
            tab = Tab.INICIO
            screen = Screen.Consultas
        } else {
            avisoProducto = "Esta notificación corresponde a otra tienda y no se abrió su bandeja de consultas."
        }
        alConsumirConsultasPush()
    }
    LaunchedEffect(abrirInvitacionesFeriaPush, feriaInvitacionIdPush, sesion.uid) {
        if (!abrirInvitacionesFeriaPush && feriaInvitacionIdPush.isNullOrBlank()) return@LaunchedEffect
        tab = Tab.INICIO
        screen = Screen.Ferias
        feriaDestacadaId = feriaInvitacionIdPush
        cargarInvitacionesFeria(forzar = true)
        alConsumirInvitacionesFeriaPush()
    }
    var productoAjustando by remember(sesion.uid) { mutableStateOf<ProductoEmprendedora?>(null) }
    var ventasGuardadas by remember(sesion.uid) { mutableStateOf<List<VentaRegistrada>>(emptyList()) }
    var feriasVenta by remember(sesion.uid) { mutableStateOf<List<FeriaParaVenta>>(emptyList()) }
    var historialVentasActivo by remember(sesion.uid) { mutableStateOf(false) }
    var preferenciasInteraccion by remember(sesion.tiendaId) { mutableStateOf(com.example.amancay.data.PreferenciasInteraccionTienda()) }
    var abrirHistorialVentas by remember(sesion.uid) { mutableStateOf(false) }
    var valoresDefecto by remember(sesion.tiendaId) {
        mutableStateOf(PreferenciasCatalogoEmprendedora.leerValoresDefecto(context, sesion.tiendaId))
    }
    var valoresDefectoListos by remember(sesion.tiendaId) { mutableStateOf(false) }
    var mostrarPrecios by remember(sesion.tiendaId) {
        mutableStateOf(PreferenciasCatalogoEmprendedora.leerMostrarPrecios(context, sesion.tiendaId))
    }
    var preciosListos by remember(sesion.tiendaId) { mutableStateOf(false) }
    var altaNuevaPendiente by remember(sesion.tiendaId) { mutableStateOf(false) }
    var catalogoSinConexion by remember(sesion.tiendaId) {
        mutableStateOf(PreferenciasCatalogoEmprendedora.catalogoSinConexion(context, sesion.tiendaId))
    }
    var cargandoVentas by remember(sesion.uid) { mutableStateOf(false) }
    var errorVentas by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var errorCargaVentas by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var cargandoHistorialVentas by remember(sesion.uid) { mutableStateOf(false) }
    var errorHistorialVentas by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var cargandoFeriasVenta by remember(sesion.uid) { mutableStateOf(false) }
    var errorFeriasVenta by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var historialConfiguracionCargada by remember(sesion.uid) { mutableStateOf(false) }
    var reintentoCargaVentas by remember(sesion.uid) { mutableIntStateOf(0) }
    var reintentoHistorialVentas by remember(sesion.uid) { mutableIntStateOf(0) }

    LaunchedEffect(sesion.tiendaId) {
        valoresDefectoListos = false
        try {
            valoresDefecto = productosRepository.leerValoresDefecto(sesion.tiendaId)
            PreferenciasCatalogoEmprendedora.guardarValoresDefecto(context, sesion.tiendaId, valoresDefecto)
            valoresDefectoListos = true
        } catch (e: Exception) {
            if (PreferenciasCatalogoEmprendedora.hayValoresDefectoSincronizados(context, sesion.tiendaId)) {
                valoresDefecto = PreferenciasCatalogoEmprendedora.leerValoresDefecto(context, sesion.tiendaId)
                valoresDefectoListos = true
            } else {
                errorProducto = e.message ?: "Conéctate para cargar los valores por defecto antes de crear un producto."
            }
        }
        if (ColaOfflineCatalogo.hayRed(context)) {
            val resultado = ColaOfflineCatalogo.sincronizar(context, productosRepository, sesion.tiendaId)
            revisionColaOffline++
            cambiosPendientesOffline = ColaOfflineCatalogo.cantidadPendiente(context, sesion.tiendaId)
            if (resultado.aplicadas > 0) avisoProducto = "Se sincronizaron ${resultado.aplicadas} cambios del catálogo."
            if (resultado.pendientes > 0) {
                val detalle = ColaOfflineCatalogo.primerErrorPendiente(context, sesion.tiendaId)
                errorProducto = detalle?.let { "Quedan ${resultado.pendientes} cambios pendientes: $it" }
                    ?: "Quedan ${resultado.pendientes} cambios del catálogo pendientes."
            }
        } else {
            cambiosPendientesOffline = ColaOfflineCatalogo.cantidadPendiente(context, sesion.tiendaId)
            if (cambiosPendientesOffline > 0) avisoProducto = "Hay $cambiosPendientesOffline cambios guardados en este dispositivo; se sincronizarán al volver internet."
        }
    }

    DisposableEffect(context, productosRepository, sesion.tiendaId) {
        val detenerObservador = ColaOfflineCatalogo.observarReconexiones(context) {
            scope.launch {
                try {
                    valoresDefecto = productosRepository.leerValoresDefecto(sesion.tiendaId)
                    PreferenciasCatalogoEmprendedora.guardarValoresDefecto(context, sesion.tiendaId, valoresDefecto)
                    valoresDefectoListos = true
                } catch (e: Exception) {
                    if (!PreferenciasCatalogoEmprendedora.hayValoresDefectoSincronizados(context, sesion.tiendaId)) {
                        errorProducto = e.message ?: "No se pudieron cargar los valores por defecto."
                    }
                }
                val resultado = ColaOfflineCatalogo.sincronizar(context, productosRepository, sesion.tiendaId)
                revisionColaOffline++
                cambiosPendientesOffline = ColaOfflineCatalogo.cantidadPendiente(context, sesion.tiendaId)
                if (resultado.aplicadas > 0) {
                    avisoProducto = "Se sincronizaron ${resultado.aplicadas} cambios del catálogo."
                    errorProducto = null
                }
                if (resultado.omitidas > 0) {
                    errorProducto = "${resultado.omitidas} cambios no se aplicaron porque esos productos ya no existen."
                } else if (resultado.pendientes > 0) {
                    val detalle = ColaOfflineCatalogo.primerErrorPendiente(context, sesion.tiendaId)
                    errorProducto = detalle?.let { "Quedan ${resultado.pendientes} cambios pendientes: $it" }
                        ?: "Quedan ${resultado.pendientes} cambios del catálogo pendientes. Se conservaron para reintentar."
                }
            }
        }
        onDispose { detenerObservador() }
    }

    LaunchedEffect(screen, sesion.tiendaId, reintentoCargaVentas) {
        if (screen == Screen.ConfiguracionCatalogo) {
            historialConfiguracionCargada = false
            preciosListos = false
            errorVentas = null
            try {
                mostrarPrecios = productosRepository.leerMostrarPrecios(sesion.tiendaId)
                PreferenciasCatalogoEmprendedora.guardarMostrarPrecios(context, sesion.tiendaId, mostrarPrecios)
                preciosListos = true
            } catch (e: Exception) {
                errorVentas = e.message ?: "No se pudo consultar la visibilidad de precios de la tienda."
            }
            try {
                historialVentasActivo = productosRepository.leerHistorialActivo(sesion.tiendaId)
                preferenciasInteraccion = productosRepository.leerPreferenciasInteraccion(sesion.tiendaId)
                valoresDefecto = productosRepository.leerValoresDefecto(sesion.tiendaId)
                PreferenciasCatalogoEmprendedora.guardarValoresDefecto(context, sesion.tiendaId, valoresDefecto)
                historialConfiguracionCargada = true
            } catch (e: Exception) {
                if (errorVentas == null) errorVentas = e.message ?: "No se pudo leer la configuración del catálogo."
            }
            return@LaunchedEffect
        }
        if (screen != Screen.Ventas) return@LaunchedEffect
        cargandoVentas = true
        errorVentas = null
        errorCargaVentas = null
        errorFeriasVenta = null
        historialConfiguracionCargada = false
        try {
            historialVentasActivo = productosRepository.leerHistorialActivo(sesion.tiendaId)
            historialConfiguracionCargada = true
            if (historialVentasActivo) {
                cargandoFeriasVenta = true
                try {
                    feriasVenta = productosRepository.listarFeriasParaVenta(sesion.tiendaId)
                } catch (e: Exception) {
                    feriasVenta = emptyList()
                    errorFeriasVenta = mensajeCargaVentas(e, "las ferias")
                } finally {
                    cargandoFeriasVenta = false
                }
            } else {
                feriasVenta = emptyList()
            }
        } catch (e: Exception) {
            errorCargaVentas = mensajeCargaVentas(e, "la configuración de ventas")
        } finally { cargandoVentas = false }
    }

    LaunchedEffect(screen, sesion.tiendaId, abrirHistorialVentas, historialConfiguracionCargada, reintentoHistorialVentas) {
        if (screen != Screen.Ventas || !abrirHistorialVentas || !historialConfiguracionCargada) return@LaunchedEffect
        cargandoHistorialVentas = true
        errorHistorialVentas = null
        try {
            ventasGuardadas = productosRepository.listarVentas(sesion.tiendaId)
        } catch (e: Exception) {
            ventasGuardadas = emptyList()
            errorHistorialVentas = mensajeCargaVentas(e, "el historial de ventas")
        } finally {
            cargandoHistorialVentas = false
        }
    }

    LaunchedEffect(screen, productoVista, reintentoFotoVista, firmaImagenesProductos) {
        val preview = screen as? Screen.Preview ?: return@LaunchedEffect
        val id = preview.productId ?: productoVista ?: return@LaunchedEffect
        val producto = productos.firstOrNull { it.id == id } ?: return@LaunchedEffect
        fotoVistaProducto = null
        errorFotoVista = null
        if (producto.imagenes.isEmpty()) return@LaunchedEffect
        cargandoFotoVista = true
        try {
            fotoVistaProducto = decodificarVistaPreviaProducto(
                productosRepository.leerImagenPrivada(sesion.tiendaId, producto.id)
            )
            if (fotoVistaProducto == null) errorFotoVista = "La imagen guardada no se pudo abrir."
        } catch (e: Exception) {
            errorFotoVista = e.message ?: "No se pudo cargar la imagen privada."
        } finally {
            cargandoFotoVista = false
        }
    }

    LaunchedEffect(screen, editandoProducto, sesion.tiendaId, firmaImagenesProductos) {
        if (screen != Screen.Products || editandoProducto) return@LaunchedEffect
        productos.filter { producto ->
            producto.imagenes.isNotEmpty() && miniaturasProductos[producto.id]?.firmaImagenes != firmaImagenes(producto)
        }.forEach { producto ->
            val firma = firmaImagenes(producto)
            try {
                val bitmap = decodificarVistaPreviaProducto(
                    productosRepository.leerImagenPrivada(sesion.tiendaId, producto.id)
                )
                if (bitmap != null && firmasImagenesAlTerminarCarga[producto.id] == firma) {
                    miniaturasProductos = miniaturasProductos + (producto.id to MiniaturaProductoCache(firma, bitmap))
                }
            } catch (_: Exception) {
                // La miniatura no impide abrir el detalle: allí se puede reintentar la carga privada.
            }
        }
    }

    fun abrirAltaProducto() {
        if (!valoresDefectoListos) {
            altaNuevaPendiente = true
            scope.launch { snackbar.showSnackbar("Estamos cargando los valores por defecto del catálogo.") }
            scope.launch {
                try {
                    valoresDefecto = productosRepository.leerValoresDefecto(sesion.tiendaId)
                    PreferenciasCatalogoEmprendedora.guardarValoresDefecto(context, sesion.tiendaId, valoresDefecto)
                    valoresDefectoListos = true
                    errorProducto = null
                } catch (e: Exception) {
                    errorProducto = e.message ?: "Conéctate para cargar los valores por defecto antes de crear un producto."
                }
            }
            return
        }
        altaNuevaPendiente = false
        tab = Tab.AGREGAR
        screen = Screen.Products
        editandoProducto = true
        productoInicialId = null
        editandoTienda = false
    }

    suspend fun eliminarDelCatalogo(producto: ProductoEmprendedora) {
        enviandoProducto = true
        errorProducto = null
        avisoProducto = null
        try {
            if (ColaOfflineCatalogo.hayRed(context)) {
                val sync = ColaOfflineCatalogo.sincronizar(context, productosRepository, sesion.tiendaId)
                revisionColaOffline++
                productosRepository.eliminar(sesion.tiendaId, producto)
                avisoProducto = "Producto eliminado del catálogo."
                if (sync.pendientes > 0) errorProducto = "Quedan ${sync.pendientes} cambios anteriores pendientes de revisión."
            } else if (!catalogoSinConexion) {
                errorProducto = "Activa «Guardar catálogo sin conexión» para conservar esta eliminación y sincronizarla después."
            } else {
                val agregada = ColaOfflineCatalogo.encolarEliminacion(context, sesion.tiendaId, producto)
                revisionColaOffline++
                cambiosPendientesOffline = ColaOfflineCatalogo.cantidadPendiente(context, sesion.tiendaId)
                avisoProducto = if (agregada > 0) "Sin conexión: el producto se quitó de la lista y la eliminación se sincronizará al volver internet." else "La eliminación ya está guardada para sincronizarse."
            }
        } catch (e: Exception) {
            errorProducto = mensajeProducto(e)
        } finally {
            enviandoProducto = false
        }
    }

    fun abrirTab(nuevoTab: Tab) {
        if (nuevoTab == Tab.AGREGAR) {
            abrirAltaProducto()
            return
        }
        tab = nuevoTab
        screen = when (nuevoTab) {
            Tab.INICIO -> Screen.Home
            Tab.PRODUCTOS -> Screen.Products
            Tab.MAS -> Screen.ConfiguracionCatalogo
            Tab.AGREGAR -> Screen.Products
        }
        editandoProducto = false
        productoInicialId = null
        if (nuevoTab != Tab.MAS) editandoTienda = false
    }

    fun terminarGuiaInicial(completarRecorrido: Boolean) {
        guiaActiva = false
        if (completarRecorrido) {
            preferenciasGuia.edit().putBoolean("completa:emprendedora_${sesion.uid}", true).apply()
        }
        guiaSoloPaso = false
    }

    fun iniciarTutorialEmprendedora(idObjetivo: String?) {
        val indice = if (idObjetivo == null) 0 else pasosGuia.indexOfFirst { it.idObjetivo == idObjetivo }
        if (indice < 0) return
        pasoGuia = indice
        guiaSoloPaso = idObjetivo != null
        guiaActiva = true
        showHelp = false
        editandoTienda = false
        editandoProducto = false
        when (idObjetivo) {
            "texto_grande" -> {
                tab = Tab.MAS
                screen = Screen.Settings
            }
            else -> {
                tab = Tab.INICIO
                screen = Screen.Home
            }
        }
    }

    fun avanzarGuiaInicial() {
        val paso = pasosGuia.getOrNull(pasoGuia) ?: return
        when (paso.idObjetivo) {
            "inicio" -> abrirTab(Tab.INICIO)
            "invitaciones" -> {
                tab = Tab.INICIO
                screen = Screen.Ferias
                scope.launch { cargarInvitacionesFeria() }
            }
            "consultas" -> {
                tab = Tab.INICIO
                screen = Screen.Consultas
            }
            "productos" -> abrirTab(Tab.PRODUCTOS)
            "ayuda" -> Unit
            "encargos" -> {
                tab = Tab.INICIO
                screen = Screen.Ventas
            }
            "mi_tienda" -> {
                tab = Tab.MAS
                screen = Screen.Settings
                editandoTienda = false
            }
            "texto_grande" -> alCambiarEscalaTexto(1.1f)
            "agregar" -> {
                abrirTab(Tab.AGREGAR)
                terminarGuiaInicial(completarRecorrido = !guiaSoloPaso)
                return
            }
        }
        if (guiaSoloPaso) {
            terminarGuiaInicial(completarRecorrido = false)
            return
        }
        val siguiente = pasoGuia + 1
        if (siguiente > pasosGuia.lastIndex) {
            terminarGuiaInicial(completarRecorrido = true)
        } else {
            pasoGuia = siguiente
            when (pasosGuia[siguiente].idObjetivo) {
                "encargos", "mi_tienda" -> {
                    tab = Tab.INICIO
                    screen = Screen.Home
                }
            }
        }
    }

    LaunchedEffect(valoresDefectoListos, altaNuevaPendiente) {
        if (valoresDefectoListos && altaNuevaPendiente) {
            altaNuevaPendiente = false
            tab = Tab.AGREGAR
            screen = Screen.Products
            editandoProducto = true
            productoInicialId = null
            editandoTienda = false
            snackbar.showSnackbar("Configuración cargada. Ya puedes completar el producto.")
        }
    }

    fun productoLegado(producto: ProductoEmprendedora, miniatura: Bitmap? = null): Product {
        val tipoNombre = tiposProducto.firstOrNull { it.id == producto.tipoProductoId }?.nombre.orEmpty()
            .lowercase()
        val tipo = when {
            "alimento" in tipoNombre || "reposter" in tipoNombre -> ProductType.ALIMENTOS
            "bienestar" in tipoNombre || "cosm" in tipoNombre || "cuidado" in tipoNombre -> ProductType.BIENESTAR
            "papel" in tipoNombre -> ProductType.PAPELERIA
            "textil" in tipoNombre || "vestuario" in tipoNombre || "tej" in tipoNombre -> ProductType.TEXTIL
            else -> ProductType.DECORACION
        }
        val variantes = if (producto.tieneVariantes) {
            producto.variantes.filter { it.activa }.map { variante ->
                Variant(
                    id = variante.id,
                    nombre = variante.opciones.values.joinToString(" · ").ifBlank { "Variante" },
                    precio = variante.precio.toString(),
                    stock = (variante.stock ?: 0).toString()
                )
            }.toMutableList()
        } else {
            mutableListOf(
                Variant(
                    nombre = "Único",
                    precio = (producto.precioBase ?: 0L).toString(),
                    stock = (producto.stock ?: 0).toString()
                )
            )
        }
        return Product(
            id = producto.id,
            nombre = producto.nombre,
            tipo = tipo,
            material = producto.atributosEspecificos.values.joinToString(" · ").ifBlank { producto.descripcion },
            variantes = variantes.ifEmpty { mutableListOf(Variant(nombre = "Variante")) },
            disponibleEncargo = producto.modalidad == "a_pedido" || producto.modalidad == "regular",
            publicado = producto.estadoPublicacion == "publicado",
            esBorrador = producto.estadoPublicacion != "publicado",
            estadoCatalogo = when (producto.estadoPublicacion) {
                "publicado" -> if (producto.disponible) "Visible" else "No disponible"
                "archivado" -> "Retirado"
                else -> "Borrador"
            },
            photoBitmap = miniatura,
            tieneFoto = producto.imagenes.isNotEmpty()
        )
    }

    val productoDeVista = productos.firstOrNull { it.id == productoVista }?.let(::productoLegado)
    val nuevasConsultas = productosRepository.consultas.collectAsState().value.count { it.estado == "nueva" && it.notificable }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Cream,
        contentWindowInsets = if (editandoProducto) WindowInsets.safeDrawing.only(WindowInsetsSides.Top) else ScaffoldDefaults.contentWindowInsets,
        topBar = { if (!editandoProducto && screen != Screen.ConfiguracionCatalogo && screen != Screen.Consultas && screen !is Screen.Preview) AmancayTopBar(onHelp = if (editandoTienda) null else ({ showHelp = true }), onExit = alSalir, etiquetaEspacio = true, onNotifications = { tab = Tab.INICIO; screen = Screen.Consultas }, notificationsCount = nuevasConsultas, onNotificationsBounds = { objetivoConsultasGuia = it }, onHelpBounds = { objetivoAyudaGuia = it }) },
        bottomBar = {
            if (!editandoProducto && screen != Screen.ConfiguracionCatalogo && screen !is Screen.Preview) {
            Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = Color.White, shadowElevation = 8.dp) {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    Tab.entries.forEach { item ->
                        val selected = tab == item
                        NavigationBarItem(
                            modifier = Modifier.onGloballyPositioned { coordenadas ->
                                val limite = coordenadas.boundsInRoot()
                                if (objetivosGuia[item] != limite) objetivosGuia[item] = limite
                            },
                            selected = selected,
                            onClick = { abrirTab(item) },
                            icon = {
                                Image(
                                    painter = painterResource(item.icon),
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Wine else Muted
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = CardPink)
                        )
                    }
                }
            }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(snackbarData = data, containerColor = Wine, contentColor = Color.White, shape = RoundedCornerShape(16.dp))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            aviso?.let { AvisoEmprendedora(it, false) }
            error?.let { AvisoEmprendedora(it, true) }
            avisoProducto?.let { AvisoEmprendedora(it, false) }
            errorProducto?.let { AvisoEmprendedora(it, true) }
            Box(Modifier.weight(1f)) {
                ScreenTransition(key = screen.toString()) {
                    val pantalla = screen
                    when (pantalla) {
                    Screen.Home -> HomeScreen(
                        borrador = null,
                        publicados = productos.count { it.estadoPublicacion == "publicado" },
                        borradores = productos.count { it.estadoPublicacion == "borrador" },
                        ventasCount = ventasGuardadas.size,
                        linkCompartido = linkCompartido,
                        invitacionesPendientes = invitaciones.count { it.estado == "pendiente" },
                        invitacionesDisponibles = errorInvitaciones == null,
                        invitacionesCargando = cargandoInvitaciones,
                        onAgregar = { abrirTab(Tab.AGREGAR) },
                        onContinuarBorrador = { abrirTab(Tab.PRODUCTOS) },
                        onMisProductos = { abrirTab(Tab.PRODUCTOS) },
                        onVerCatalogo = {
                            productoVista = productos.firstOrNull { it.estadoPublicacion == "publicado" }?.id
                            screen = Screen.Preview(productoVista)
                        },
                        onVerVentas = { screen = Screen.Ventas },
                        onVerMiTienda = { tab = Tab.MAS; screen = Screen.Settings; editandoTienda = false },
                        onVerFerias = {
                            screen = Screen.Ferias
                            scope.launch { cargarInvitacionesFeria() }
                        },
                        tiendaVisible = tienda.estadoPublicacion == "publicado",
                        nombreEmprendedora = sesion.nombreMostrar,
                        productosRecientes = productos.take(3).map { productoLegado(it, miniaturasProductosVisibles[it.id]) },
                        objetivoGuiaActivo = if (guiaActiva) pasosGuia.getOrNull(pasoGuia)?.idObjetivo else null,
                        alUbicarObjetivoGuia = { id, rect -> objetivosInicioGuia[id] = rect },
                        contenidoAdicional = {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    val logoInicio = remember(sesion.uid, tienda.logo) { leerLogoLocalTienda(context, sesion.uid, tienda.logo != null) }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (logoInicio != null) {
                                            Image(
                                                logoInicio.asImageBitmap(),
                                                contentDescription = "Logo de la tienda",
                                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Column {
                                            Text(tienda.nombre, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Dark)
                                            Text(if (tienda.estadoPublicacion == "publicado") "Tienda publicada" else "Tienda en borrador", fontSize = 14.sp, color = Muted)
                                        }
                                    }
                                    if (tienda.descripcion.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(tienda.descripcion, fontSize = 14.sp, color = Muted, maxLines = 2)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    if (tienda.estadoPublicacion == "publicado") {
                                        OutlineButton("Retirar tienda", alRetirarTienda)
                                    } else {
                                        PrimaryButton(
                                            "Publicar mi tienda",
                                            alPublicarTienda,
                                            enabled = !enviando && tienda.nombre.isNotBlank() && tienda.sectorId.isNotBlank() && tienda.tipoEmprendimientoId.isNotBlank()
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlineButton("Registrar venta · Historial de encargos", { screen = Screen.Ventas })
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    )
                    Screen.OrganizarCatalogo -> OrganizarCatalogoScreen(
                        productos = productos,
                        categorias = categoriasProducto,
                        miniaturas = miniaturasProductosVisibles,
                        enviando = enviandoProducto,
                        alEditar = { producto ->
                            productoInicialId = producto.id
                            editandoProducto = true
                            screen = Screen.Products
                        },
                        alCambiarDisponibilidad = { seleccion, disponible ->
                            scope.launch {
                                enviandoProducto = true
                                errorProducto = null
                                avisoProducto = null
                                try {
                                    if (ColaOfflineCatalogo.hayRed(context)) {
                                        val sync = ColaOfflineCatalogo.sincronizar(context, productosRepository, sesion.tiendaId)
                                        revisionColaOffline++
                                        productosRepository.cambiarDisponibilidad(sesion.tiendaId, seleccion, disponible)
                                        avisoProducto = if (disponible) "Productos marcados como disponibles." else "Productos marcados como no disponibles. Siguen en tu catálogo."
                                        if (sync.pendientes > 0) errorProducto = "El cambio nuevo se guardó, pero quedan ${sync.pendientes} cambios anteriores pendientes de revisión."
                                    } else {
                                        if (!catalogoSinConexion) {
                                            errorProducto = "Activa «Guardar catálogo sin conexión» para guardar este cambio y sincronizarlo después."
                                        } else {
                                            val grupos = ColaOfflineCatalogo.encolarDisponibilidad(
                                                context, sesion.tiendaId,
                                                seleccion.map { it.id },
                                                seleccion.map { it.versionEsperada },
                                                disponible
                                            )
                                            revisionColaOffline++
                                            cambiosPendientesOffline = ColaOfflineCatalogo.cantidadPendiente(context, sesion.tiendaId)
                                            avisoProducto = "Sin conexión: el cambio quedó guardado en este dispositivo y se sincronizará al volver internet."
                                            if (grupos > 1) avisoProducto += " Se guardaron $grupos grupos de productos."
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorProducto = mensajeProducto(e)
                                } finally { enviandoProducto = false }
                            }
                        },
                        alEliminar = { producto ->
                            scope.launch { eliminarDelCatalogo(producto) }
                        },
                    )
                    Screen.Products -> if (editandoProducto) {
                        ProductosEmprendedoraPanel(
                            tiendaId = sesion.tiendaId,
                            tiendaPublicada = tienda.estadoPublicacion == "publicado",
                            repository = productosRepository,
                            enviando = enviando || enviandoProducto,
                            alEnviando = { enviandoProducto = it },
                            alError = { errorProducto = it },
                            alAviso = {
                                avisoProducto = it
                                revisionColaOffline++
                                cambiosPendientesOffline = ColaOfflineCatalogo.cantidadPendiente(context, sesion.tiendaId)
                            },
                            abrirNuevoAlIniciar = productoInicialId == null,
                            soloEditor = true,
                            productoInicialId = productoInicialId,
                            alFinalizarEdicion = {
                                editandoProducto = false
                                productoInicialId = null
                                tab = Tab.PRODUCTOS
                            }
                        )
                    } else {
                        ProductsScreen(
                            productos = productos.map { productoLegado(it, miniaturasProductosVisibles[it.id]) },
                            onEdit = {
                                productoInicialId = it.id
                                editandoProducto = true
                                tab = Tab.AGREGAR
                            },
                            onDelete = { legado ->
                                val remoto = productos.firstOrNull { it.id == legado.id }
                                if (remoto != null) scope.launch {
                                    eliminarDelCatalogo(remoto)
                                }
                            },
                            onCreate = {
                                abrirAltaProducto()
                            },
                            onPreview = {
                                productoVista = it.id
                                screen = Screen.Preview(it.id)
                            },
                            settings = ShopSettings(),
                            onSettings = {},
                            mostrarEliminar = true,
                            mensajeEliminar = { nombre ->
                                "\"$nombre\" dejará de aparecer en tu lista y en el catálogo público. Se conservarán el historial de stock y la imagen original como respaldo."
                            },
                            onAccionPublicacion = { legado ->
                                val remoto = productos.firstOrNull { it.id == legado.id }
                                if (remoto != null) scope.launch {
                                    enviandoProducto = true
                                    errorProducto = null
                                    avisoProducto = null
                                    try {
                                        if (remoto.estadoPublicacion == "publicado") {
                                            productosRepository.retirar(sesion.tiendaId, remoto)
                                            avisoProducto = "Producto retirado del catálogo público."
                                        } else {
                                            productosRepository.publicar(sesion.tiendaId, remoto)
                                            avisoProducto = "Producto publicado en el catálogo comprador."
                                        }
                                    } catch (e: Exception) {
                                        errorProducto = mensajeProducto(e)
                                    } finally {
                                        enviandoProducto = false
                                    }
                                }
                            },
                            onAjustarStock = { legado ->
                                productoAjustando = productos.firstOrNull { it.id == legado.id }
                            },
                            onOrganizeCatalog = { screen = Screen.OrganizarCatalogo }
                        )
                    }
                    Screen.ConfiguracionCatalogo -> ConfiguracionVistaPreviaScreen(
                        productos = productos,
                        miniaturas = miniaturasProductosVisibles,
                        mostrarPrecios = mostrarPrecios,
                        preciosListos = preciosListos,
                        historialActivo = historialVentasActivo,
                        preferenciasInteraccion = preferenciasInteraccion,
                        valoresDefecto = valoresDefecto,
                        catalogoSinConexion = catalogoSinConexion,
                        enviando = enviandoProducto,
                        error = errorVentas,
                        alCambiarMostrarPrecios = { mostrar ->
                            scope.launch {
                                enviandoProducto = true
                                errorVentas = null
                                try {
                                    productosRepository.configurarMostrarPrecios(sesion.tiendaId, mostrar)
                                    PreferenciasCatalogoEmprendedora.guardarMostrarPrecios(context, sesion.tiendaId, mostrar)
                                    mostrarPrecios = mostrar
                                    avisoProducto = if (mostrar) "Los precios ya están visibles en el catálogo comprador." else "El catálogo muestra «Consultar precio» y no expone los montos."
                                    preciosListos = true
                                } catch (e: Exception) {
                                    errorVentas = e.message ?: "No se pudo cambiar la visibilidad de precios."
                                } finally {
                                    enviandoProducto = false
                                }
                            }
                        },
                        alGuardarValoresDefecto = { valores ->
                            scope.launch {
                                enviandoProducto = true
                                errorVentas = null
                                try {
                                    productosRepository.configurarValoresDefecto(sesion.tiendaId, valores)
                                    PreferenciasCatalogoEmprendedora.guardarValoresDefecto(context, sesion.tiendaId, valores)
                                    valoresDefecto = valores
                                    avisoProducto = "Valores por defecto guardados para los próximos productos."
                                } catch (e: Exception) {
                                    errorVentas = e.message ?: "No se pudo guardar los valores por defecto."
                                } finally {
                                    enviandoProducto = false
                                }
                            }
                        },
                        alCambiarHistorial = { activo ->
                            scope.launch {
                                enviandoProducto = true; errorVentas = null
                                try {
                                    productosRepository.configurarHistorialVentas(sesion.tiendaId, activo)
                                    historialVentasActivo = activo
                                    historialConfiguracionCargada = true
                                    avisoProducto = if (activo) "Historial de ventas activado." else "Historial de ventas desactivado. El stock seguirá actualizándose al registrar ventas."
                                } catch (e: Exception) { errorVentas = e.message ?: "No se pudo guardar la configuración del historial." }
                                finally { enviandoProducto = false }
                            }
                        },
                        alGuardarAvisosContacto = { activo, inicio, fin ->
                            scope.launch {
                                try {
                                    productosRepository.configurarAvisosContacto(sesion.tiendaId, activo, inicio, fin)
                                    preferenciasInteraccion = preferenciasInteraccion.copy(avisosContacto = activo, horaInicioAvisos = inicio, horaFinAvisos = fin)
                                    avisoProducto = if (activo) "Avisos de contacto activos entre $inicio y $fin." else "Avisos de contacto desactivados; las consultas seguirán guardándose."
                                    errorProducto = null
                                } catch (e: Exception) { errorProducto = e.message ?: "No se pudo guardar el horario de avisos." }
                            }
                        },
                        alCambiarCalificaciones = { activo ->
                            scope.launch {
                                try {
                                    productosRepository.configurarCalificaciones(sesion.tiendaId, activo)
                                    preferenciasInteraccion = preferenciasInteraccion.copy(calificacionesHabilitadas = activo)
                                    avisoProducto = if (activo) "Calificaciones activadas." else "Calificaciones desactivadas."
                                    errorProducto = null
                                } catch (e: Exception) { errorProducto = e.message ?: "No se pudo actualizar la preferencia de calificaciones." }
                            }
                        },
                        alCambiarCatalogoSinConexion = { activo ->
                            PreferenciasCatalogoEmprendedora.guardarCatalogoSinConexion(context, sesion.tiendaId, activo)
                            catalogoSinConexion = activo
                        },
                        alVerHistorial = {
                            abrirHistorialVentas = true
                            screen = Screen.Ventas
                        },
                        alVerTiendaPublica = {
                            productoVista = productos.firstOrNull { it.estadoPublicacion == "publicado" && it.disponible }?.id
                            screen = Screen.Preview(productoVista)
                        },
                        alVolver = { screen = Screen.Home; tab = Tab.INICIO }
                    )
                    Screen.Settings -> if (editandoTienda) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                            Spacer(Modifier.height(8.dp))
                            PantallaEdicionTienda(
                                cuenta = sesion,
                                tienda = tienda,
                                sectores = sectores,
                                tipos = tipos,
                                enviando = enviando,
                                alSubirLogo = alSubirLogo,
                                alGuardar = { alEditarTienda(it); editandoTienda = false },
                                alCancelar = { editandoTienda = false }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    } else {
                        SettingsScreen(
                            settings = ShopSettings(huella = huellaHabilitada),
                            onChange = { alCambiarHuella(it.huella) },
                            onGuardar = {},
                            mostrarPreferenciasProducto = false,
                            nombreTienda = tienda.nombre,
                            estadoTienda = if (tienda.estadoPublicacion == "publicado") "Visible para compradores" else "Aún no aparece en el catálogo comprador",
                            descripcionTienda = tienda.descripcion,
                            logoTienda = remember(sesion.uid, tienda.logo) { leerLogoLocalTienda(context, sesion.uid, tienda.logo != null) },
                            onEditarTienda = { editandoTienda = true },
                            errorAcceso = errorHuella,
                            onCerrarSesion = alSalir,
                            escalaTexto = escalaTexto,
                            onCambiarEscalaTexto = alCambiarEscalaTexto,
                            alUbicarOpcionTextoGrande = { rect -> if (objetivoTextoGrande != rect) objetivoTextoGrande = rect }
                        )
                    }
                    Screen.Ventas -> VentasEmprendedoraScreen(
                        productos = productos,
                        ventas = ventasGuardadas,
                        ferias = feriasVenta,
                        historialActivo = historialVentasActivo,
                        historialConfiguracionCargada = historialConfiguracionCargada,
                        abrirHistorial = abrirHistorialVentas,
                        cargando = cargandoVentas,
                        error = errorVentas,
                        errorCarga = errorCargaVentas,
                        enviando = enviandoProducto,
                        cargandoHistorial = cargandoHistorialVentas,
                        errorHistorial = errorHistorialVentas,
                        cargandoFerias = cargandoFeriasVenta,
                        errorFerias = errorFeriasVenta,
                        alCambiarSeccionHistorial = { abrir -> abrirHistorialVentas = abrir },
                        alReintentarHistorial = { reintentoHistorialVentas++ },
                        alRegistrar = { producto, varianteId, cantidad, lugarTipo, feriaId, lugarTexto, operacionId, fecha, alExito ->
                            scope.launch {
                                enviandoProducto = true; errorVentas = null
                                try {
                                    val historialRegistrado = productosRepository.registrarVenta(sesion.tiendaId, producto.id, varianteId, cantidad, lugarTipo, feriaId, lugarTexto, fecha=fecha, operacionId=operacionId)
                                    avisoProducto = if (historialRegistrado) "Venta guardada y stock actualizado." else "Stock actualizado. El historial está apagado, así que la venta no quedó guardada en él."
                                    alExito()
                                    errorCargaVentas = null
                                    if (abrirHistorialVentas && historialRegistrado) {
                                        try {
                                            ventasGuardadas = productosRepository.listarVentas(sesion.tiendaId)
                                            errorHistorialVentas = null
                                        } catch (e: Exception) {
                                            errorHistorialVentas = mensajeCargaVentas(e, "el historial de ventas")
                                        }
                                    }
                                } catch (e: Exception) { errorVentas = e.message ?: "No se pudo registrar la venta." }
                                finally { enviandoProducto = false }
                            }
                        },
                        alReintentarCarga = { reintentoCargaVentas++ },
                        alVolver = { abrirHistorialVentas = false; screen = Screen.Home; tab = Tab.INICIO }
                    )
                    Screen.Consultas -> BandejaConsultasScreen(
                        consultas = productosRepository.consultas.collectAsState().value,
                        error = productosRepository.error.collectAsState().value,
                        alMarcarLeida = { consulta ->
                            scope.launch {
                                runCatching { productosRepository.marcarConsultaLeida(sesion.tiendaId, consulta.id) }
                                    .onFailure { errorProducto = "No pudimos actualizar esta consulta. Comprueba tu conexión e inténtalo de nuevo." }
                            }
                        },
                        alResponder = { consulta, respuesta ->
                            scope.launch {
                                runCatching { productosRepository.responderConsulta(sesion.tiendaId, consulta.id, respuesta) }
                                    .onFailure { errorProducto = it.message ?: "No se pudo enviar la respuesta." }
                            }
                        },
                        alVolver = { screen = Screen.Home; tab = Tab.INICIO },
                        alAyuda = { showHelp = true }
                    )
                    is Screen.Preview -> PreviewCatalogScreen(
                        producto = productoDeVista,
                        privateImageBitmap = fotoVistaProducto,
                        privateImageLoading = cargandoFotoVista,
                        privateImageError = errorFotoVista,
                        onRetryPrivateImage = { reintentoFotoVista++ },
                        esVistaPrivada = productoDeVista?.esBorrador == true,
                        nombreTienda = tienda.nombre,
                        calificacionesActivas = preferenciasInteraccion.calificacionesHabilitadas,
                        onBack = { screen = Screen.Products; tab = Tab.PRODUCTOS },
                        link = productoDeVista?.let { EnlacesPublicos.producto(sesion.tiendaId, it.id) } ?: EnlacesPublicos.tienda(sesion.tiendaId),
                        onShare = {
                            val enlace = productoDeVista?.let { EnlacesPublicos.producto(sesion.tiendaId, it.id) } ?: EnlacesPublicos.tienda(sesion.tiendaId)
                            clipboard.setText(AnnotatedString(enlace))
                            linkCompartido = true
                            scope.launch { snackbar.showSnackbar("Enlace del producto copiado") }
                        },
                        onContact = { scope.launch { snackbar.showSnackbar("Consulta el contacto disponible en la ficha de la tienda") } }
                    )
                    Screen.Ferias -> FeriasScreen(
                        invitaciones = invitaciones,
                        onVerDetalle = { screen = Screen.FeriaDetalle(it.id); feriaDestacadaId = null },
                        onVolver = { screen = Screen.Home; tab = Tab.INICIO },
                        cargando = cargandoInvitaciones,
                        error = errorInvitaciones,
                        feriaDestacadaId = feriaDestacadaId,
                        onActualizar = { scope.launch { cargarInvitacionesFeria(forzar = true) } }
                    )
                    is Screen.FeriaDetalle -> FeriaDetalleScreen(
                        invitacion = pantalla.invitacionId?.let { id -> invitaciones.firstOrNull { it.id == id } },
                        onAceptar = {
                            scope.launch {
                                if (respondiendoInvitacion) return@launch
                                respondiendoInvitacion = true
                                try {
                                    feriasRepository.responder(it.id, true)
                                    invitaciones = invitaciones.map { actual -> if (actual.id == it.id) actual.copy(estado = "aceptada") else actual }
                                    snackbar.showSnackbar("Inscripción confirmada")
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(e.message ?: "No se pudo confirmar la invitación.")
                                } finally {
                                    respondiendoInvitacion = false
                                }
                            }
                        },
                        onRechazar = {
                            scope.launch {
                                if (respondiendoInvitacion) return@launch
                                respondiendoInvitacion = true
                                try {
                                    feriasRepository.responder(it.id, false)
                                    invitaciones = invitaciones.map { actual -> if (actual.id == it.id) actual.copy(estado = "rechazada") else actual }
                                    snackbar.showSnackbar("Invitación rechazada")
                                } catch (e: Exception) {
                                    snackbar.showSnackbar(e.message ?: "No se pudo rechazar la invitación.")
                                } finally {
                                    respondiendoInvitacion = false
                                }
                            }
                        },
                        onVolver = { screen = Screen.Ferias },
                        enviando = respondiendoInvitacion
                    )
                    else -> AvisoEmprendedora("Esta sección todavía no está conectada a la cuenta de Firebase.", false)
                    }
                }
            }
        }
    }

    if (guiaActiva && !editandoTienda && !editandoProducto && screen !is Screen.Sell && screen !is Screen.Camara) {
        val paso = pasosGuia.getOrNull(pasoGuia)
        val objetivo = when (paso?.idObjetivo) {
            "invitaciones", "encargos", "mi_tienda" -> objetivosInicioGuia[paso.idObjetivo]
            "consultas" -> objetivoConsultasGuia
            "ayuda" -> objetivoAyudaGuia
            "texto_grande" -> objetivoTextoGrande
            "inicio" -> objetivosGuia[Tab.INICIO]
            "productos" -> objetivosGuia[Tab.PRODUCTOS]
            "agregar" -> objetivosGuia[Tab.AGREGAR]
            else -> null
        }
        if (paso != null && objetivo != null) {
            GuiaInterfazSpotlight(
                objetivo = objetivo,
                nombreObjetivo = paso.nombreObjetivo,
                paso = if (guiaSoloPaso) 1 else pasoGuia + 1,
                total = if (guiaSoloPaso) 1 else pasosGuia.size,
                titulo = paso.titulo,
                explicacion = paso.explicacion,
                alTocarObjetivo = ::avanzarGuiaInicial,
                alSaltar = { terminarGuiaInicial(completarRecorrido = !guiaSoloPaso) }
            )
        }
    }
    }

    productoAjustando?.let { producto ->
        DialogoAjusteStock(
            producto = producto,
            enviando = enviandoProducto,
            onDismiss = { if (!enviandoProducto) productoAjustando = null },
            onConfirm = { varianteId, cantidad, motivo, nota, operacionId ->
                scope.launch {
                    enviandoProducto = true
                    errorProducto = null
                    avisoProducto = null
                    try {
                        if (ColaOfflineCatalogo.hayRed(context)) {
                            val sync = ColaOfflineCatalogo.sincronizar(context, productosRepository, sesion.tiendaId)
                            revisionColaOffline++
                            productosRepository.ajustarStock(sesion.tiendaId, producto, varianteId, cantidad, motivo, nota, operacionId)
                            productoAjustando = null
                            avisoProducto = "Ajuste de stock guardado con su historial."
                            if (sync.pendientes > 0) errorProducto = "Quedan ${sync.pendientes} cambios anteriores pendientes de revisión."
                        } else if (!catalogoSinConexion) {
                            errorProducto = "Activa «Guardar catálogo sin conexión» para conservar este ajuste y sincronizarlo después."
                        } else {
                            ColaOfflineCatalogo.encolarAjusteStock(
                                context, sesion.tiendaId, producto, varianteId, cantidad, motivo, nota, operacionId
                            )
                            revisionColaOffline++
                            cambiosPendientesOffline = ColaOfflineCatalogo.cantidadPendiente(context, sesion.tiendaId)
                            productoAjustando = null
                            avisoProducto = "Sin conexión: el ajuste quedó guardado en este dispositivo y se sincronizará al volver internet."
                        }
                    } catch (e: Exception) {
                        errorProducto = mensajeProducto(e)
                    } finally {
                        enviandoProducto = false
                    }
                }
            }
        )
    }

    if (showHelp) {
        val opcionesTutorial = listOf(
            OpcionTutorialAyuda("inicio", "Inicio y portada", Icons.Outlined.Home),
            OpcionTutorialAyuda("invitaciones", "Ferias e invitaciones", Icons.Outlined.Event),
            OpcionTutorialAyuda("consultas", "Responder consultas", Icons.Outlined.ChatBubbleOutline),
            OpcionTutorialAyuda("productos", "Productos", Icons.Filled.ShoppingBag),
            OpcionTutorialAyuda("encargos", "Encargos y ventas", Icons.Filled.ReceiptLong),
            OpcionTutorialAyuda("mi_tienda", "Mi tienda", Icons.Filled.Storefront),
            OpcionTutorialAyuda("texto_grande", "Tamaño de texto", Icons.Outlined.Settings),
            OpcionTutorialAyuda("agregar", "Agregar un producto", Icons.Filled.Add)
        )
        val seccionAyuda = when (screen) {
            is Screen.Home -> "Inicio"
            is Screen.Products, is Screen.OrganizarCatalogo, is Screen.Preview -> "Productos"
            is Screen.Sell, is Screen.Sale, is Screen.Camara -> "Agregar"
            is Screen.History, is Screen.Ventas -> "Ventas"
            is Screen.Consultas -> "Consultas"
            is Screen.Ferias, is Screen.FeriaDetalle -> "Ferias"
            is Screen.Settings, is Screen.ConfiguracionCatalogo -> "Más"
        }
        val descripcionAyuda = when (screen) {
            is Screen.Home -> "En Inicio tienes accesos directos a tus productos, encargos, tienda e invitaciones a ferias. El timbre de arriba abre Consultas."
            is Screen.Products, is Screen.OrganizarCatalogo, is Screen.Preview -> "En Productos puedes revisar, editar y organizar los artículos de tu tienda."
            is Screen.Sell, is Screen.Sale, is Screen.Camara -> "Sigue los pasos en pantalla para agregar un producto o registrar una venta."
            is Screen.History, is Screen.Ventas -> "Aquí puedes revisar tus ventas y buscar por fecha o feria."
            is Screen.Consultas -> "Abre una consulta para leer el mensaje y responder a la persona. El estado te ayuda a distinguir las nuevas de las atendidas."
            is Screen.Ferias -> "Aquí puedes consultar fechas y estados, abrir cada invitación y responder si participarás. Usa el calendario para ubicar las ferias por día."
            is Screen.FeriaDetalle -> "Revisa la fecha, el lugar y las condiciones de la feria. Desde esta ficha puedes aceptar o rechazar la invitación."
            is Screen.Settings -> "En esta configuración puedes cambiar el tamaño de letra y editar la información de tu tienda."
            is Screen.ConfiguracionCatalogo -> "Aquí puedes ajustar las opciones que se aplican a los productos nuevos."
        }
        DialogoAyudaTutoriales(
            titulo = "Ayuda de $seccionAyuda",
            descripcion = descripcionAyuda,
            opciones = opcionesTutorial,
            alElegir = { iniciarTutorialEmprendedora(it) },
            alRepetirTodo = { iniciarTutorialEmprendedora(null) },
            alCerrar = { showHelp = false }
        )
    }
}
