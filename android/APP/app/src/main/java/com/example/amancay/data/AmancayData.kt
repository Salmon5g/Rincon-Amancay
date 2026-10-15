package com.example.amancay.data

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

// ---------- Modelos ----------

enum class ProductType(val label: String) {
    DECORACION("Decoración"),
    BIENESTAR("Bienestar"),
    ALIMENTOS("Alimentos"),
    PAPELERIA("Papelería"),
    TEXTIL("Textil")
}

class Variant(
    val id: String = UUID.randomUUID().toString(),
    nombre: String = "",
    precio: String = "",
    stock: String = ""
) {
    var nombre: String by mutableStateOf(nombre)
    var precio: String by mutableStateOf(precio)
    var stock: String by mutableStateOf(stock)

    fun precioLong(): Long = precio.filter { it.isDigit() }.toLongOrNull() ?: 0L
    fun stockInt(): Int = stock.filter { it.isDigit() }.toIntOrNull() ?: 0

    fun copy(
        id: String = this.id,
        nombre: String = this.nombre,
        precio: String = this.precio,
        stock: String = this.stock
    ): Variant = Variant(id, nombre, precio, stock)
}

data class Product(
    val id: String = UUID.randomUUID().toString(),
    var nombre: String = "",
    var tipo: ProductType? = null,
    var material: String = "",
    var tamano: String = "",
    var cuidados: String = "",
    var variantes: MutableList<Variant> = mutableListOf(Variant(nombre = "Variante 1")),
    var disponibleEncargo: Boolean = true,
    var publicado: Boolean = false,
    var esBorrador: Boolean = true,
    var photoUri: Uri? = null,
    var photoBitmap: Bitmap? = null,
    var tieneFoto: Boolean = false
) {
    fun precioPrincipal(): Long = variantes.firstOrNull()?.precioLong() ?: 0L
    fun stockTotal(): Int = variantes.sumOf { it.stockInt() }
    fun esValidoParaPublicar(): Boolean = nombre.isNotBlank()
}

data class Sale(
    val id: String = UUID.randomUUID().toString(),
    val productoId: String,
    val productoNombre: String,
    val varianteNombre: String,
    val cantidad: Int,
    val total: Long,
    val canal: String = "Feria",
    val fecha: Long = System.currentTimeMillis()
)

data class ShopSettings(
    var visibilidadMaxima: Boolean = true,
    var valoresPorDefecto: Boolean = false,
    var rangoHistorial: String = "Último día",
    var manejoStock: String = "Piezas físicas",
    var aceptarPedidos: Boolean = true,
    var ocultarAutomatico: Boolean = false,
    var historialCatalogo: Boolean = true,
    var huella: Boolean = false
)

data class CatalogEvent(
    val id: String = UUID.randomUUID().toString(),
    val texto: String,
    val fecha: Long = System.currentTimeMillis()
)

data class FeriaInvitation(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val donde: String,
    val cuando: String,
    val horario: String,
    val organiza: String,
    val estado: String = "pendiente" // pendiente | aceptada | rechazada
)

fun formatPrice(value: Long): String {
    val nf = NumberFormat.getNumberInstance(Locale("es", "CL"))
    nf.maximumFractionDigits = 0
    return "$" + nf.format(value)
}

fun formatFecha(ts: Long): String {
    return try {
        SimpleDateFormat("dd MMM", Locale("es")).format(java.util.Date(ts))
    } catch (_: Exception) {
        ""
    }
}

fun fechaHoy(): String {
    return try {
        "HOY, " + SimpleDateFormat("d 'DE' MMMM", Locale("es"))
            .format(java.util.Date()).uppercase(Locale("es"))
    } catch (_: Exception) {
        "HOY"
    }
}

// ---------- Draft (borrador en curso) ----------

class DraftState {
    var editingId: String? by mutableStateOf(null) // null = nuevo, sino id editado
    var fotoLista: Boolean by mutableStateOf(false)
    var photoUri: Uri? by mutableStateOf(null)
    var photoBitmap: Bitmap? by mutableStateOf(null)
    var nombre: String by mutableStateOf("")
    var tipo: ProductType? by mutableStateOf(null)
    var material: String by mutableStateOf("")
    var tamano: String by mutableStateOf("")
    var cuidados: String by mutableStateOf("")
    var disponibleEncargo: Boolean by mutableStateOf(true)
    var publicarAhora: Boolean by mutableStateOf(true)

    val variantes: SnapshotStateList<Variant> = mutableStateListOf(Variant(nombre = "Color arena"))
    fun toProduct(): Product = Product(
        id = editingId ?: UUID.randomUUID().toString(),
        nombre = nombre.ifBlank { "Sin título" },
        tipo = tipo,
        material = material,
        tamano = tamano,
        cuidados = cuidados,
        variantes = variantes.map { it.copy() }.toMutableList(),
        disponibleEncargo = disponibleEncargo,
        publicado = publicarAhora,
        esBorrador = !publicarAhora,
        photoUri = photoUri,
        photoBitmap = photoBitmap,
        tieneFoto = fotoLista
    )
    fun isEmpty(): Boolean =
        nombre.isBlank() && material.isBlank() && tamano.isBlank() && !fotoLista && tipo == null &&
                (variantes.size <= 1 && variantes.firstOrNull()?.precio?.isBlank() == true)

    fun reset(editing: Product? = null) {
        if (editing == null) {
            editingId = null; fotoLista = false; photoUri = null; photoBitmap = null
            nombre = ""; tipo = null; material = ""; tamano = ""; cuidados = ""
            variantes.clear(); variantes.add(Variant(nombre = "Color arena"))
            disponibleEncargo = true; publicarAhora = true
        } else {
            editingId = editing.id; fotoLista = editing.tieneFoto
            photoUri = editing.photoUri; photoBitmap = editing.photoBitmap
            nombre = editing.nombre; tipo = editing.tipo
            material = editing.material; tamano = editing.tamano; cuidados = editing.cuidados
            variantes.clear()
            variantes.addAll(editing.variantes.map { it.copy() }.ifEmpty { listOf(Variant(nombre = "Color arena")) })
            disponibleEncargo = editing.disponibleEncargo; publicarAhora = editing.publicado
        }
    }
}

// ---------- Repositorio en memoria ----------

object AmancayRepo {
    private val _productos = MutableStateFlow<List<Product>>(emptyList())
    val productos: StateFlow<List<Product>> = _productos.asStateFlow()

    private val _ventas = MutableStateFlow<List<Sale>>(emptyList())
    val ventas: StateFlow<List<Sale>> = _ventas.asStateFlow()

    private val _settings = MutableStateFlow(ShopSettings())
    val settings: StateFlow<ShopSettings> = _settings.asStateFlow()

    private val _borradorGuardado = MutableStateFlow<Product?>(null)
    val borradorGuardado: StateFlow<Product?> = _borradorGuardado.asStateFlow()

    private val _enlaceCompartido = MutableStateFlow(false)
    val enlaceCompartido: StateFlow<Boolean> = _enlaceCompartido.asStateFlow()

    private val _invitaciones = MutableStateFlow(
        listOf(
            FeriaInvitation(
                nombre = "Feria Emprende Alerce",
                donde = "Alerce, Puerto Montt",
                cuando = "Sábado 17 de octubre",
                horario = "10:00 a 17:00",
                organiza = "Asociación de Alerce"
            ),
            FeriaInvitation(
                nombre = "Feria Plaza Ñuñoa",
                donde = "Plaza Ñuñoa, Stand 12",
                cuando = "Domingo 19 de octubre",
                horario = "11:00 a 20:00",
                organiza = "Junta de Vecinos Ñuñoa"
            )
        )
    )
    val invitaciones: StateFlow<List<FeriaInvitation>> = _invitaciones.asStateFlow()

    private val _eventosCatalogo = MutableStateFlow<List<CatalogEvent>>(emptyList())
    val eventosCatalogo: StateFlow<List<CatalogEvent>> = _eventosCatalogo.asStateFlow()

    val draft = DraftState()

    init {
        // Datos de ejemplo como en Figma
        _productos.value = listOf(
            Product(
                nombre = "Jarrón Tierra",
                tipo = ProductType.DECORACION,
                material = "Gres, lavanda",
                tamano = "250 g, 320 ml",
                cuidados = "Guardar en lugar fresco.",
                variantes = mutableListOf(Variant(nombre = "Color arena", precio = "28500", stock = "6")),
                disponibleEncargo = true, publicado = true, esBorrador = false, tieneFoto = true
            ),
            Product(
                nombre = "Bruma de Caléndula",
                tipo = ProductType.BIENESTAR,
                material = "Caléndula, agua destilada",
                tamano = "100 ml",
                cuidados = "Agitar antes de usar.",
                variantes = mutableListOf(Variant(nombre = "Única", precio = "12900", stock = "12")),
                disponibleEncargo = true, publicado = true, esBorrador = false, tieneFoto = false
            ),
            Product(
                nombre = "Pañuelo Raíces",
                tipo = ProductType.TEXTIL,
                material = "Algodón teñido natural",
                tamano = "70 x 70 cm",
                cuidados = "Lavar a mano.",
                variantes = mutableListOf(Variant(nombre = "Color coral", precio = "22000", stock = "7")),
                disponibleEncargo = false, publicado = true, esBorrador = false, tieneFoto = false
            ),
            Product(
                nombre = "Miel de Ulmo 500 g",
                tipo = ProductType.ALIMENTOS,
                material = "Miel de ulmo, envase de vidrio",
                tamano = "500 g",
                cuidados = "Guardar en lugar fresco y seco.",
                variantes = mutableListOf(Variant(nombre = "Frasco vidrio", precio = "9500", stock = "20")),
                disponibleEncargo = true, publicado = true, esBorrador = false, tieneFoto = false
            ),
            Product(
                nombre = "Mermelada de Mora 250 g",
                tipo = ProductType.ALIMENTOS,
                material = "Mora, azúcar, limón",
                tamano = "250 g",
                cuidados = "Refrigerar una vez abierta.",
                variantes = mutableListOf(Variant(nombre = "Frasco 250 g", precio = "6800", stock = "15")),
                disponibleEncargo = true, publicado = true, esBorrador = false, tieneFoto = false
            ),
            Product(
                nombre = "Jabón de Matico",
                tipo = ProductType.BIENESTAR,
                material = "Matico, aceite de oliva, karité",
                tamano = "100 g",
                cuidados = "Mantener seco entre usos.",
                variantes = mutableListOf(Variant(nombre = "Barra 100 g", precio = "4500", stock = "30")),
                disponibleEncargo = true, publicado = true, esBorrador = false, tieneFoto = false
            ),
            Product(
                nombre = "Cuaderno Semilla",
                tipo = ProductType.PAPELERIA,
                material = "Papel reciclado",
                tamano = "A5, 80 hojas",
                cuidados = "-",
                variantes = mutableListOf(Variant(nombre = "Tapa kraft", precio = "8900", stock = "0")),
                disponibleEncargo = true, publicado = false, esBorrador = true, tieneFoto = false
            )
        )
        _ventas.value = listOf(
            Sale(productoId = "1", productoNombre = "Jarrón Tierra - 2 unidades", varianteNombre = "Color arena", cantidad = 2, total = 57000, canal = "Feria"),
            Sale(productoId = "2", productoNombre = "Bruma de Caléndula - 1", varianteNombre = "Única", cantidad = 1, total = 12900, canal = "Último día"),
            Sale(productoId = "3", productoNombre = "Pañuelo Raíces - 1", varianteNombre = "Color coral", cantidad = 1, total = 22000, canal = "Producto")
        )
    }

    fun guardarProducto(p: Product) {
        val existe = _productos.value.any { it.id == p.id }
        _productos.update { list ->
            val idx = list.indexOfFirst { it.id == p.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = p } else list + p
        }
        // si era el borrador guardado, limpiarlo
        if (_borradorGuardado.value?.id == p.id && !p.esBorrador) _borradorGuardado.value = null
        eventoCatalogo(
            if (existe) "Producto editado: ${p.nombre}"
            else if (p.publicado) "Producto publicado: ${p.nombre}"
            else "Producto creado: ${p.nombre}"
        )
    }

    fun guardarBorrador(p: Product) {
        val b = p.copy(esBorrador = true, publicado = false)
        _borradorGuardado.value = b
        val existe = _productos.value.any { it.id == b.id }
        _productos.update { list ->
            val idx = list.indexOfFirst { it.id == b.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = b } else list + b
        }
        if (!existe) eventoCatalogo("Borrador guardado: ${b.nombre}")
    }

    fun eliminarBorrador() { _borradorGuardado.value = null }

    fun eliminarProducto(id: String) {
        val nombre = _productos.value.firstOrNull { it.id == id }?.nombre ?: "producto"
        _productos.update { list -> list.filterNot { it.id == id } }
        if (_borradorGuardado.value?.id == id) _borradorGuardado.value = null
        eventoCatalogo("Producto eliminado: $nombre")
    }

    fun eventoCatalogo(texto: String) {
        if (!_settings.value.historialCatalogo) return
        _eventosCatalogo.update { listOf(CatalogEvent(texto = texto)) + it }
    }

    fun marcarEnlaceCompartido() { _enlaceCompartido.value = true }

    fun responderFeria(id: String, acepta: Boolean) {
        _invitaciones.update { list ->
            list.map {
                // Solo se puede responder una vez: si ya respondió, no cambia
                if (it.id == id && it.estado == "pendiente") it.copy(estado = if (acepta) "aceptada" else "rechazada")
                else it
            }
        }
    }

    fun obtener(id: String): Product? = _productos.value.firstOrNull { it.id == id }

    fun registrarVenta(producto: Product, variante: Variant, cantidad: Int) {
        val total = variante.precioLong() * cantidad
        val sale = Sale(
            productoId = producto.id,
            productoNombre = "${producto.nombre} - $cantidad ${if (cantidad == 1) "unidad" else "unidades"}",
            varianteNombre = variante.nombre,
            cantidad = cantidad,
            total = total,
            canal = "Feria"
        )
        _ventas.update { listOf(sale) + it }
        // descontar stock
        _productos.update { list ->
            list.map {
                if (it.id == producto.id) {
                    val nuevas = it.variantes.map { v ->
                        if (v.id == variante.id) v.copy(stock = ((v.stockInt() - cantidad).coerceAtLeast(0)).toString())
                        else v
                    }.toMutableList()
                    it.copy(variantes = nuevas)
                } else it
            }
        }
    }

    fun updateSettings(s: ShopSettings) { _settings.value = s }
}
