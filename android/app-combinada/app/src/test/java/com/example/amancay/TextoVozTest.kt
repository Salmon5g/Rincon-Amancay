package com.example.amancay

import com.example.amancay.buyer.textoParaEscucharProducto
import com.example.amancay.data.ProductoCatalogoComprador
import com.example.amancay.data.VarianteCatalogoComprador
import com.example.amancay.ui.dividirEnFrases
import com.example.amancay.ui.horarioHablado
import com.example.amancay.ui.textoParaEscucharPasoGuia
import com.example.amancay.buyer.textoParaEscucharTienda
import com.example.amancay.data.TiendaCatalogoComprador
import com.example.amancay.ui.partirPorLongitud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoVozTest {
    private val producto = ProductoCatalogoComprador(
        id = "p1",
        tiendaId = "t1",
        tiendaNombre = "Lanas del Sur",
        nombre = "Gorro de lana",
        descripcion = "Tejido a mano con lana natural.",
        categoriaId = "c1",
        categoriaNombre = "Textiles",
        tipoProductoId = "tp1",
        tipoProductoNombre = "Vestuario",
        moneda = "CLP",
        unidadVenta = "unidad",
        modalidad = "a_pedido",
        estadoDisponibilidad = "disponible",
        precioBase = 12000,
        tieneVariantes = false,
        atributosEspecificos = mapOf("materialPrincipal" to "Lana", "vacio" to " ")
    )

    @Test
    fun leePrecioEnPesosSinSimbolos() {
        val texto = textoParaEscucharProducto(producto, null)
        assertTrue(texto.contains("Precio: 12000 pesos."))
        assertFalse(texto.contains("$"))
        assertFalse(texto.contains("CLP"))
    }

    @Test
    fun incluyeDatosPrincipalesYOmiteVacios() {
        val texto = textoParaEscucharProducto(producto, null)
        assertTrue(texto.startsWith("Gorro de lana. De la tienda Lanas del Sur. Disponible."))
        assertTrue(texto.contains("Material Principal: Lana."))
        assertTrue(texto.contains("Modalidad: A pedido."))
        assertFalse(texto.contains("Vacio"))
    }

    @Test
    fun preciosOcultosSeLeenComoConsulta() {
        val texto = textoParaEscucharProducto(producto.copy(precioBase = null), null)
        assertTrue(texto.contains("Precio a consultar con la emprendedora."))
    }

    @Test
    fun usaPrecioYEstadoDeLaVarianteElegida() {
        val variantes = listOf(
            VarianteCatalogoComprador("v1", mapOf("color" to "Rojo"), 9000, "agotado"),
            VarianteCatalogoComprador("v2", mapOf("color" to "Azul"), 10000, "disponible")
        )
        val texto = textoParaEscucharProducto(producto.copy(variantes = variantes, tieneVariantes = true), variantes[0])
        assertTrue(texto.contains("Agotado."))
        assertTrue(texto.contains("Precio: 9000 pesos."))
        assertTrue(texto.contains("Opción elegida: Rojo."))
        assertTrue(texto.contains("Hay 2 opciones disponibles para elegir."))
    }

    @Test
    fun divideFrasesYDescartaVacias() {
        assertEquals(
            listOf("Hola.", "¿Qué tal?", "Fin"),
            dividirEnFrases("Hola.   ¿Qué tal?\n\n . Fin")
        )
    }

    @Test
    fun parteFrasesLargasSinCortarPalabras() {
        val partes = partirPorLongitud("uno dos tres cuatro", 9)
        assertEquals(listOf("uno dos", "tres", "cuatro"), partes)
        assertTrue(partes.all { it.length <= 9 })
    }

    @Test
    fun horarioSeDiceEnPalabras() {
        assertEquals("de 10 a 17 y 30 horas", horarioHablado("10:00 a 17:30"))
        assertEquals("Horario por confirmar", horarioHablado("Horario por confirmar"))
    }

    @Test
    fun tiendaLeeDescripcionContactoYPreciosOcultos() {
        val tienda = TiendaCatalogoComprador(
            id = "t1", nombre = "Lanas del Sur", tipoId = "x", sectorId = "y",
            mostrarPrecios = false, whatsappHabilitado = true, formularioHabilitado = true,
            descripcion = "Tejidos de Chiloé"
        )
        val texto = textoParaEscucharTienda(tienda, "Textil", "Alerce", 3)
        assertTrue(texto.startsWith("Tienda Lanas del Sur. Tipo de emprendimiento: Textil. Ubicación: sector Alerce. Tejidos de Chiloé."))
        assertTrue(texto.contains("Tiene 3 productos publicados."))
        assertTrue(texto.contains("Los precios se consultan directamente con la emprendedora."))
        assertTrue(texto.contains("Puedes contactarla por WhatsApp y formulario."))
    }

    @Test
    fun pasoDeGuiaNombraElSignoDePregunta() {
        assertEquals(
            "Paso 6 de 6. La ayuda está aquí. Toca para elegir un tutorial. Para continuar, toca el signo de pregunta.",
            textoParaEscucharPasoGuia(6, 6, "La ayuda está aquí", "Toca para elegir un tutorial", "?")
        )
    }
}
