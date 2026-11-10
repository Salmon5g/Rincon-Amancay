package com.example.amancay

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import com.example.amancay.BuildConfig
import com.example.amancay.ui.theme.Peach
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
import com.example.amancay.buyer.ModoCompradorScreen
import com.example.amancay.emprendedora.ModoEmprendedoraConectada
import com.example.amancay.data.AmancayRepository
import com.example.amancay.data.DraftState
import com.example.amancay.data.FirebaseClientServicesProvider
import com.example.amancay.data.CuentaEmprendedora
import com.example.amancay.data.OpcionCatalogoEmprendedora
import com.example.amancay.data.ProductoEmprendedora
import com.example.amancay.data.ProductosEmprendedoraRepository
import com.example.amancay.data.VentaRegistrada
import com.example.amancay.data.FeriaParaVenta
import com.example.amancay.data.PreferenciasCatalogoEmprendedora
import com.example.amancay.data.TiendaEmprendedora
import com.example.amancay.data.ImagenProductoPreparada
import com.example.amancay.emprendedora.leerLogoLocalTienda
import com.example.amancay.data.Product
import com.example.amancay.data.ProductType
import com.example.amancay.data.ShopSettings
import com.example.amancay.data.Variant
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

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmancayTheme(darkTheme = false) {
                AmancayApp()
            }
        }
    }
}

@Composable
fun AmancayApp() {
    val context = LocalContext.current
    val prefsTexto = remember(context) { context.getSharedPreferences("amancay_preferencias_visuales", Context.MODE_PRIVATE) }
    var escalaTexto by remember { mutableFloatStateOf(prefsTexto.getFloat("escala_texto", 1f)) }
    val firebaseServices = remember(context) { FirebaseClientServicesProvider.initialize(context) }
    val catalogoComprador = remember(firebaseServices) { crearCatalogoCompradorFirebase(firebaseServices.firestore) }
    DisposableEffect(catalogoComprador) {
        onDispose { catalogoComprador.cerrar() }
    }
    var modo by remember { mutableStateOf<String?>(null) }
    val densidad = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(densidad.density, densidad.fontScale * escalaTexto)) {
    when (modo) {
        "comprador" -> ModoCompradorScreen(
            repositorio = catalogoComprador,
            auth = firebaseServices.auth,
            webClientId = BuildConfig.WEB_CLIENT_ID
        ) { modo = null }
        "emprendedora" -> ModoEmprendedoraConectada(firebaseServices, BuildConfig.WEB_CLIENT_ID, escalaTexto, { escala ->
            escalaTexto = escala
            prefsTexto.edit().putFloat("escala_texto", escala).apply()
        }) { modo = null }
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Cream)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            Image(
                painter = painterResource(Fig.LOGO),
                contentDescription = "Rincón Amancay",
                modifier = Modifier.size(92.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Rincón Amancay",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Wine,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "¿Cómo quieres usar la aplicación?",
                fontSize = 15.sp,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            TarjetaModo(
                titulo = "Modo comprador",
                descripcion = "Encuentra productos de los emprendimientos Amancay.",
                emoji = "🛍️"
            ) { modo = "comprador" }
            Spacer(Modifier.height(14.dp))
            TarjetaModo(
                titulo = "Modo emprendedora",
                descripcion = "Administra tu catálogo, tus precios y tus ventas.",
                emoji = "✿"
            ) { modo = "emprendedora" }
        }
    }
    }
}

@Composable
private fun TarjetaModo(
    titulo: String,
    descripcion: String,
    emoji: String,
    alEntrar: () -> Unit
) {
    Card(
        onClick = alEntrar,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(56.dp).background(Peach, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 26.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Dark)
                Spacer(Modifier.height(4.dp))
                Text(descripcion, fontSize = 13.sp, color = Muted, maxLines = 2)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Wine.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text("›", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Wine)
            }
        }
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
                            clipboard.setText(AnnotatedString("https://rincon-amancay.tienda"))
                            repositorio.marcarEnlaceCompartido()
                            scope.launch { snackbar.showSnackbar("Enlace de tu tienda copiado") }
                        },
                        onContact = { scope.launch { snackbar.showSnackbar("Te contactarán por WhatsApp") } }
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
    val productos by productosRepository.productos.collectAsState()
    val tiposProducto by productosRepository.tipos.collectAsState()
    val categoriasProducto by productosRepository.categorias.collectAsState()
    // Las invitaciones de feria siguen siendo un flujo legado de demostración.
    val local = remember(sesion.uid) { crearRepositorioDemostracion() }
    val invitaciones by local.invitaciones.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember(sesion.uid) { mutableStateOf(Tab.INICIO) }
    var screen by remember(sesion.uid) { mutableStateOf<Screen>(Screen.Home) }
    var editandoTienda by remember(sesion.uid) { mutableStateOf(false) }
    var editandoProducto by remember(sesion.uid) { mutableStateOf(false) }
    var productoInicialId by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var linkCompartido by remember(sesion.uid) { mutableStateOf(false) }
    var productoVista by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var fotoVistaProducto by remember(sesion.uid) { mutableStateOf<Bitmap?>(null) }
    var cargandoFotoVista by remember(sesion.uid) { mutableStateOf(false) }
    var errorFotoVista by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var reintentoFotoVista by remember(sesion.uid) { mutableIntStateOf(0) }
    var miniaturasProductos by remember(sesion.uid) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    val firmaImagenesProductos = productos.joinToString("|") { "${it.id}:${it.imagenes.joinToString(",")}" }
    var enviandoProducto by remember(sesion.uid) { mutableStateOf(false) }
    var avisoProducto by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var errorProducto by remember(sesion.uid) { mutableStateOf<String?>(null) }
    var productoAjustando by remember(sesion.uid) { mutableStateOf<ProductoEmprendedora?>(null) }
    var ventasGuardadas by remember(sesion.uid) { mutableStateOf<List<VentaRegistrada>>(emptyList()) }
    var feriasVenta by remember(sesion.uid) { mutableStateOf<List<FeriaParaVenta>>(emptyList()) }
    var historialVentasActivo by remember(sesion.uid) { mutableStateOf(false) }
    var abrirHistorialVentas by remember(sesion.uid) { mutableStateOf(false) }
    var valoresDefecto by remember(sesion.tiendaId) {
        mutableStateOf(PreferenciasCatalogoEmprendedora.leerValoresDefecto(context, sesion.tiendaId))
    }
    var catalogoSinConexion by remember(sesion.tiendaId) {
        mutableStateOf(PreferenciasCatalogoEmprendedora.catalogoSinConexion(context, sesion.tiendaId))
    }
    var cargandoVentas by remember(sesion.uid) { mutableStateOf(false) }
    var errorVentas by remember(sesion.uid) { mutableStateOf<String?>(null) }

    LaunchedEffect(screen, sesion.tiendaId) {
        if (screen == Screen.ConfiguracionCatalogo) {
            try {
                historialVentasActivo = productosRepository.leerHistorialActivo(sesion.tiendaId)
            } catch (e: Exception) {
                errorVentas = e.message ?: "No se pudo leer la configuración del historial."
            }
            return@LaunchedEffect
        }
        if (screen != Screen.Ventas) return@LaunchedEffect
        cargandoVentas = true
        errorVentas = null
        try {
            historialVentasActivo = productosRepository.leerHistorialActivo(sesion.tiendaId)
            ventasGuardadas = productosRepository.listarVentas(sesion.tiendaId)
            feriasVenta = productosRepository.listarFeriasPublicadas()
        } catch (e: Exception) {
            errorVentas = e.message ?: "No se pudo cargar el historial de ventas."
        } finally { cargandoVentas = false }
    }

    LaunchedEffect(screen, productoVista, reintentoFotoVista) {
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
        productos.filter { it.imagenes.isNotEmpty() && it.id !in miniaturasProductos }.forEach { producto ->
            try {
                val bitmap = decodificarVistaPreviaProducto(
                    productosRepository.leerImagenPrivada(sesion.tiendaId, producto.id)
                )
                if (bitmap != null) miniaturasProductos = miniaturasProductos + (producto.id to bitmap)
            } catch (_: Exception) {
                // La miniatura no impide abrir el detalle: allí se puede reintentar la carga privada.
            }
        }
    }

    fun abrirTab(nuevoTab: Tab) {
        tab = nuevoTab
        screen = when (nuevoTab) {
            Tab.INICIO -> Screen.Home
            Tab.PRODUCTOS, Tab.AGREGAR -> Screen.Products
            Tab.MAS -> Screen.ConfiguracionCatalogo
        }
        editandoProducto = nuevoTab == Tab.AGREGAR
        productoInicialId = null
        if (nuevoTab != Tab.MAS) editandoTienda = false
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

    Scaffold(
        containerColor = Cream,
        topBar = { if (!editandoProducto && screen != Screen.ConfiguracionCatalogo) AmancayTopBar(onHelp = { showHelp = true }, onExit = alSalir, etiquetaEspacio = true) },
        bottomBar = {
            if (!editandoProducto && screen != Screen.ConfiguracionCatalogo) {
            Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = Color.White, shadowElevation = 8.dp) {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    Tab.entries.forEach { item ->
                        val selected = tab == item
                        NavigationBarItem(
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
                        onAgregar = { abrirTab(Tab.AGREGAR) },
                        onContinuarBorrador = { abrirTab(Tab.PRODUCTOS) },
                        onMisProductos = { abrirTab(Tab.PRODUCTOS) },
                        onVerCatalogo = {
                            productoVista = productos.firstOrNull { it.estadoPublicacion == "publicado" }?.id
                            screen = Screen.Preview(productoVista)
                        },
                        onVerVentas = { screen = Screen.Ventas },
                        onVerMiTienda = { tab = Tab.MAS; screen = Screen.Settings; editandoTienda = false },
                        onVerFerias = { screen = Screen.Ferias },
                        tiendaVisible = tienda.estadoPublicacion == "publicado",
                        nombreEmprendedora = sesion.nombreMostrar,
                        productosRecientes = productos.take(3).map { productoLegado(it, miniaturasProductos[it.id]) },
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
                                }
                            }
                        }
                    )
                    Screen.OrganizarCatalogo -> OrganizarCatalogoScreen(
                        productos = productos,
                        categorias = categoriasProducto,
                        miniaturas = miniaturasProductos,
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
                                    productosRepository.cambiarDisponibilidad(sesion.tiendaId, seleccion, disponible)
                                    avisoProducto = if (disponible) "Productos marcados como disponibles." else "Productos marcados como no disponibles. Siguen en tu catálogo."
                                } catch (e: Exception) {
                                    errorProducto = mensajeProducto(e)
                                } finally { enviandoProducto = false }
                            }
                        },
                        alEliminar = { producto ->
                            scope.launch {
                                enviandoProducto = true
                                errorProducto = null
                                avisoProducto = null
                                try {
                                    productosRepository.eliminar(sesion.tiendaId, producto)
                                    avisoProducto = "Producto eliminado del catálogo."
                                } catch (e: Exception) {
                                    errorProducto = mensajeProducto(e)
                                } finally { enviandoProducto = false }
                            }
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
                            alAviso = { avisoProducto = it },
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
                            productos = productos.map { productoLegado(it, miniaturasProductos[it.id]) },
                            onEdit = {
                                productoInicialId = it.id
                                editandoProducto = true
                                tab = Tab.AGREGAR
                            },
                            onDelete = { legado ->
                                val remoto = productos.firstOrNull { it.id == legado.id }
                                if (remoto != null) scope.launch {
                                    enviandoProducto = true
                                    errorProducto = null
                                    avisoProducto = null
                                    try {
                                        productosRepository.eliminar(sesion.tiendaId, remoto)
                                        avisoProducto = "Producto quitado de tu lista y del catálogo. Se conservó su historial."
                                    } catch (e: Exception) {
                                        errorProducto = mensajeProducto(e)
                                    } finally {
                                        enviandoProducto = false
                                    }
                                }
                            },
                            onCreate = {
                                productoInicialId = null
                                editandoProducto = true
                                tab = Tab.AGREGAR
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
                        miniaturas = miniaturasProductos,
                        historialActivo = historialVentasActivo,
                        valoresDefecto = valoresDefecto,
                        catalogoSinConexion = catalogoSinConexion,
                        enviando = enviandoProducto,
                        alGuardarValoresDefecto = { valores ->
                            PreferenciasCatalogoEmprendedora.guardarValoresDefecto(context, sesion.tiendaId, valores)
                            valoresDefecto = valores
                            avisoProducto = "Valores por defecto guardados para los próximos productos."
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
                            onCambiarEscalaTexto = alCambiarEscalaTexto
                        )
                    }
                    Screen.Ventas -> VentasEmprendedoraScreen(
                        productos = productos,
                        ventas = ventasGuardadas,
                        ferias = feriasVenta,
                        historialActivo = historialVentasActivo,
                        abrirHistorial = abrirHistorialVentas,
                        cargando = cargandoVentas,
                        error = errorVentas,
                        enviando = enviandoProducto,
                        alCambiarHistorial = { activo ->
                            scope.launch {
                                enviandoProducto = true; errorVentas = null
                                try {
                                    productosRepository.configurarHistorialVentas(sesion.tiendaId, activo)
                                    historialVentasActivo = activo
                                } catch (e: Exception) { errorVentas = e.message ?: "No se pudo guardar la configuración del historial." }
                                finally { enviandoProducto = false }
                            }
                        },
                        alRegistrar = { producto, varianteId, cantidad, lugarTipo, feriaId, lugarTexto, operacionId, fecha, alExito ->
                            scope.launch {
                                enviandoProducto = true; errorVentas = null
                                try {
                                    productosRepository.registrarVenta(sesion.tiendaId, producto.id, varianteId, cantidad, lugarTipo, feriaId, lugarTexto, fecha=fecha, operacionId=operacionId)
                                    ventasGuardadas = productosRepository.listarVentas(sesion.tiendaId)
                                    avisoProducto = "Venta guardada y stock actualizado."
                                    alExito()
                                } catch (e: Exception) { errorVentas = e.message ?: "No se pudo registrar la venta." }
                                finally { enviandoProducto = false }
                            }
                        },
                        alVolver = { abrirHistorialVentas = false; screen = Screen.Home; tab = Tab.INICIO }
                    )
                    is Screen.Preview -> PreviewCatalogScreen(
                        producto = productoDeVista,
                        privateImageBitmap = fotoVistaProducto,
                        privateImageLoading = cargandoFotoVista,
                        privateImageError = errorFotoVista,
                        onRetryPrivateImage = { reintentoFotoVista++ },
                        esVistaPrivada = productoDeVista?.esBorrador == true,
                        onShare = {
                            clipboard.setText(AnnotatedString("https://rincon-amancay.tienda"))
                            linkCompartido = true
                            scope.launch { snackbar.showSnackbar("Enlace de tu tienda copiado") }
                        },
                        onContact = { scope.launch { snackbar.showSnackbar("Consulta el contacto disponible en la ficha de la tienda") } }
                    )
                    Screen.Ferias -> FeriasScreen(
                        invitaciones = invitaciones,
                        onVerDetalle = { screen = Screen.FeriaDetalle(it.id) },
                        onVolver = { screen = Screen.Home; tab = Tab.INICIO }
                    )
                    is Screen.FeriaDetalle -> FeriaDetalleScreen(
                        invitacion = pantalla.invitacionId?.let { id -> invitaciones.firstOrNull { it.id == id } },
                        onAceptar = {
                            local.responderFeria(it.id, true)
                            scope.launch { snackbar.showSnackbar("Inscripción confirmada") }
                        },
                        onRechazar = {
                            local.responderFeria(it.id, false)
                            scope.launch { snackbar.showSnackbar("Invitación rechazada") }
                        },
                        onVolver = { screen = Screen.Ferias }
                    )
                    else -> AvisoEmprendedora("Esta sección todavía no está conectada a la cuenta de Firebase.", false)
                    }
                }
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
                        productosRepository.ajustarStock(sesion.tiendaId, producto, varianteId, cantidad, motivo, nota, operacionId)
                        productoAjustando = null
                        avisoProducto = "Ajuste de stock guardado con su historial."
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
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("¿Necesitas ayuda?") },
            text = { Text("Desde Inicio puedes abrir tu tienda y catálogo. En Más encontrarás la ficha y el acceso con huella.") },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Entendido", color = Wine) } }
        )
    }
}
