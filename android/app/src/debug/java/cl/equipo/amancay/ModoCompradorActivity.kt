package cl.equipo.amancay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cl.equipo.amancay.comprador.DetalleProductoScreen
import cl.equipo.amancay.comprador.DetalleProductoViewModel
import cl.equipo.amancay.comprador.DetalleTiendaScreen
import cl.equipo.amancay.comprador.DetalleTiendaViewModel
import cl.equipo.amancay.comprador.DirectorioScreen
import cl.equipo.amancay.comprador.DirectorioViewModel
import cl.equipo.amancay.data.RepositorioCatalogo
import cl.equipo.amancay.ui.theme.RinconAmancayTheme

/**
 * Punto de entrada del modo comprador (variante debug).
 *
 * Crea el repositorio y el ViewModel del directorio una sola vez y delega la interfaz a
 * [NavegacionComprador]. El manifiesto de `debug` apunta aquí como actividad de inicio.
 */
class ModoCompradorActivity : ComponentActivity() {
    private val repositorio by lazy { RepositorioCatalogo(ConexionLocal.obtener(applicationContext)) }
    private val directorioViewModel by lazy { DirectorioViewModel(repositorio) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RinconAmancayTheme {
                NavegacionComprador(directorioViewModel, repositorio)
            }
        }
    }
}

/**
 * Pantallas del modo comprador. La navegación es simple y sin librerías externas:
 * se guarda la pantalla actual en [ruta] y se cambia según las acciones del usuario.
 */
private sealed interface RutaComprador {
    data object Directorio : RutaComprador
    data class Tienda(val tiendaId: String) : RutaComprador
    data class Producto(val tiendaId: String, val productoId: String) : RutaComprador
}

/**
 * Dibuja la pantalla correspondiente a la ruta actual.
 *
 * - Directorio -> abre directamente la tienda para mostrar todos los productos.
 * - Detalle de tienda -> tocar un producto abre su detalle; "Volver" regresa al directorio.
 * - Detalle de producto -> "Volver" regresa al detalle de tienda.
 *
 * [BackHandler] hace que el bot��n atrǭs del sistema haga lo mismo que "Volver".
 */
@Composable
private fun NavegacionComprador(
    directorioViewModel: DirectorioViewModel,
    repositorio: RepositorioCatalogo
) {
    var ruta by remember { mutableStateOf<RutaComprador>(RutaComprador.Directorio) }
    when (val actual = ruta) {
        is RutaComprador.Directorio -> {
            val modeloTienda = remember("t_demo_01") {
                DetalleTiendaViewModel(repositorio, "t_demo_01")
            }
            DetalleTiendaScreen(
                viewModel = modeloTienda,
                alVolver = { },
                alAbrirProducto = { productoId -> ruta = RutaComprador.Producto("t_demo_01", productoId) }
            )
        }

        is RutaComprador.Tienda -> {
            val modelo = remember(actual.tiendaId) {
                DetalleTiendaViewModel(repositorio, actual.tiendaId)
            }
            DetalleTiendaScreen(
                viewModel = modelo,
                alVolver = { ruta = RutaComprador.Directorio },
                alAbrirProducto = { productoId -> ruta = RutaComprador.Producto(actual.tiendaId, productoId) }
            )
            BackHandler { ruta = RutaComprador.Directorio }
        }

        is RutaComprador.Producto -> {
            val modelo = remember(actual.tiendaId, actual.productoId) {
                DetalleProductoViewModel(repositorio, actual.tiendaId, actual.productoId)
            }
            DetalleProductoScreen(
                viewModel = modelo,
                alVolver = { ruta = RutaComprador.Tienda(actual.tiendaId) }
            )
            BackHandler { ruta = RutaComprador.Tienda(actual.tiendaId) }
        }
    }
}
