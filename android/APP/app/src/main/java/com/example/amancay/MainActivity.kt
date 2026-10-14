package com.example.amancay

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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
    VENDER("Vender", Fig.NAV_VENDER),
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

    var showPublishedDialog by remember { mutableStateOf(false) }
    var showSaleDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun go(s: Screen, t: Tab? = null) {
        screen = s
        if (t != null) tab = t
        else tab = when (s) {
            is Screen.Home -> Tab.INICIO
            is Screen.Products, is Screen.Preview -> Tab.PRODUCTOS
            is Screen.Sell -> Tab.VENDER
            is Screen.Sale, is Screen.History -> Tab.VENDER
            is Screen.Settings -> Tab.MAS
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
        go(Screen.Sell)
    }
    fun continueDraft() {
        val b = borradorGuardado
        if (b != null) {
            AmancayRepo.draft.reset(b)
            sellStep = 1; bump()
            go(Screen.Sell)
        }
    }
    fun editProduct(p: Product) {
        AmancayRepo.draft.reset(p)
        sellStep = 1; bump()
        go(Screen.Sell)
    }
    fun saveExit() {
        val p = AmancayRepo.draft.toProduct()
        AmancayRepo.guardarBorrador(p)
        AmancayRepo.draft.reset(null); bump()
        go(Screen.Home)
        scope.launch { snackbar.showSnackbar("Borrador guardado. Puedes volver después") }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) {
            AmancayRepo.draft.photoBitmap = bmp
            AmancayRepo.draft.photoUri = null
            AmancayRepo.draft.fotoLista = true
            bump()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            AmancayRepo.draft.photoUri = uri
            AmancayRepo.draft.photoBitmap = null
            AmancayRepo.draft.fotoLista = true
            bump()
        }
    }

    Scaffold(
        containerColor = Cream,
        topBar = { AmancayTopBar(onHelp = { showHelp = true }) },
        bottomBar = {
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
                                Tab.VENDER -> startNew()
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
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                    )
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
                    ventasTotal = ventas.sumOf { it.total },
                    ventasCount = ventas.size,
                    linkCompartido = compartido,
                    onAgregar = { startNew() },
                    onContinuarBorrador = { continueDraft() },
                    onMisProductos = { go(Screen.Products) },
                    onRegistrarVenta = { go(Screen.Sale) },
                    onVerCatalogo = { go(Screen.Preview(productos.firstOrNull { it.publicado }?.id)) }
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
                            clipboard.setText(AnnotatedString("https://amancay.tienda/ana"))
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
                is Screen.History -> SalesHistoryScreen(ventas)
                is Screen.Settings -> SettingsScreen(settings, { AmancayRepo.updateSettings(it) })
                is Screen.Sell -> {
                    val d = AmancayRepo.draft
                    when (sellStep) {
                        1 -> PhotoStep(d, bump,
                            onTakePhoto = { cameraLauncher.launch(null) },
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
