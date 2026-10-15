package com.example.amancay

import android.content.Context
import android.os.Bundle
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.data.AmancayRepo
import com.example.amancay.data.Product
import com.example.amancay.ui.*
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

enum class Tab(val label: String, val icon: Int) {
    INICIO("Inicio", Fig.NAV_INICIO),
    PRODUCTOS("Productos", Fig.NAV_PRODUCTOS),
    AGREGAR("Agregar", Fig.NAV_VENDER),
    MAS("Más", Fig.NAV_MAS)
}

sealed interface Screen {
    data object Home : Screen
    data object Products : Screen
    data object Sell : Screen
    data object Sale : Screen
    data object History : Screen
    data class Preview(val productId: String?) : Screen
    data object Settings : Screen
    data object Ferias : Screen
    data class FeriaDetalle(val invitacionId: String?) : Screen
    data object Camara : Screen
}

@Composable
fun AmancayApp() {
    var tab by remember { mutableStateOf(Tab.INICIO) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var sellStep by remember { mutableIntStateOf(1) }
    var draftBump by remember { mutableIntStateOf(0) }
    val bump: () -> Unit = { draftBump += 1 }
    @Suppress("UNUSED_EXPRESSION") draftBump

    val productos by AmancayRepo.productos.collectAsState()
    val ventas by AmancayRepo.ventas.collectAsState()
    val settings by AmancayRepo.settings.collectAsState()
    val borradorGuardado by AmancayRepo.borradorGuardado.collectAsState()
    val compartido by AmancayRepo.enlaceCompartido.collectAsState()
    val invitaciones by AmancayRepo.invitaciones.collectAsState()
    val eventos by AmancayRepo.eventosCatalogo.collectAsState()

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
            AmancayRepo.updateSettings(AmancayRepo.settings.value.copy(huella = true))
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
            is Screen.Products, is Screen.Preview -> Tab.PRODUCTOS
            is Screen.Sell, is Screen.Sale, is Screen.History, is Screen.Camara -> tab
            is Screen.Settings -> Tab.MAS
            is Screen.Ferias -> Tab.INICIO
            is Screen.FeriaDetalle -> Tab.INICIO
        }
    }

    fun startNew() {
        AmancayRepo.draft.reset(null)
        val cfg = settings
        val ultimo = productos.lastOrNull()
        if (cfg.valoresPorDefecto && ultimo != null) {
            AmancayRepo.draft.material = ultimo.material
            AmancayRepo.draft.tamano = ultimo.tamano
            AmancayRepo.draft.cuidados = ultimo.cuidados
        }
        AmancayRepo.draft.disponibleEncargo = cfg.aceptarPedidos
        sellStep = 1; bump()
        go(Screen.Sell, keepTab = true)
    }
    fun continueDraft() {
        val b = borradorGuardado
        if (b != null) {
            AmancayRepo.draft.reset(b)
            sellStep = 1; bump()
            go(Screen.Sell, keepTab = true)
        }
    }
    fun editProduct(p: Product) {
        AmancayRepo.draft.reset(p)
        sellStep = 1; bump()
        go(Screen.Sell, keepTab = true)
    }
    fun saveExit() {
        val p = AmancayRepo.draft.toProduct()
        AmancayRepo.guardarBorrador(p)
        AmancayRepo.draft.reset(null); bump()
        go(Screen.Home)
        scope.launch { snackbar.showSnackbar("Borrador guardado. Puedes volver después") }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            AmancayRepo.draft.photoUri = uri
            AmancayRepo.draft.photoBitmap = null
            AmancayRepo.draft.fotoLista = true
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
        topBar = { if (screen !is Screen.Camara) AmancayTopBar(onHelp = { showHelp = true }) },
        bottomBar = {
            if (screen !is Screen.Camara) {
            Column {
                HorizontalDivider(color = Line, thickness = 1.dp)
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
                        AmancayRepo.eliminarProducto(it.id)
                        scope.launch { snackbar.showSnackbar("Producto eliminado") }
                    },
                    onCreate = { startNew() },
                    onPreview = { go(Screen.Preview(it.id)) },
                    settings = settings,
                    onSettings = { AmancayRepo.updateSettings(it) }
                )
                is Screen.Preview -> {
                    val p = s.productId?.let { AmancayRepo.obtener(it) } ?: productos.firstOrNull { it.publicado }
                    PreviewCatalogScreen(
                        producto = p,
                        onShare = {
                            clipboard.setText(AnnotatedString("https://rincon-amancay.tienda"))
                            AmancayRepo.marcarEnlaceCompartido()
                            scope.launch { snackbar.showSnackbar("Enlace de tu tienda copiado") }
                        },
                        onContact = { scope.launch { snackbar.showSnackbar("Te contactarán por WhatsApp") } }
                    )
                }
                is Screen.Sale -> RegisterSaleScreen(
                    productos = productos,
                    onConfirm = { prod, vari, qty ->
                        AmancayRepo.registrarVenta(prod, vari, qty)
                        showSaleDialog = true
                    },
                    onVerHistorial = { go(Screen.History) }
                )
                is Screen.History -> SalesHistoryScreen(ventas, eventos)
                is Screen.Camara -> CameraScreen(
                    onFoto = { bmp ->
                        AmancayRepo.draft.photoBitmap = bmp
                        AmancayRepo.draft.photoUri = null
                        AmancayRepo.draft.fotoLista = true
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
                                AmancayRepo.updateSettings(nuevo)
                                prefs.edit().putBoolean("huella", true).apply()
                                scope.launch { snackbar.showSnackbar("Huella activada") }
                            } else {
                                scope.launch { snackbar.showSnackbar("Este equipo no tiene huella disponible") }
                            }
                        } else {
                            AmancayRepo.updateSettings(nuevo)
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
                        AmancayRepo.responderFeria(it.id, true)
                        scope.launch { snackbar.showSnackbar("Inscripción confirmada") }
                    },
                    onRechazar = {
                        AmancayRepo.responderFeria(it.id, false)
                        scope.launch { snackbar.showSnackbar("Invitación rechazada") }
                    },
                    onVolver = { go(Screen.Ferias) }
                )
                is Screen.Sell -> {
                    val d = AmancayRepo.draft
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
                                AmancayRepo.guardarProducto(p)
                                AmancayRepo.draft.reset(null); bump()
                                showPublishedDialog = true
                            },
                            onBack = { sellStep = 4 }, onSaveExit = { saveExit() })
                    }
                }
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
