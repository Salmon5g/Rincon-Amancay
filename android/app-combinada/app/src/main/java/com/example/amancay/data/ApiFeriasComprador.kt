package com.example.amancay.data

import android.net.Uri
import com.example.amancay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Instant
import java.util.Date

data class CatalogoFeriaPublico(
    val nombreTienda: String,
    val productos: List<String>
)

data class ParticipanteFeriaPublico(
    val tipo: String,
    val nombre: String,
    val catalogo: List<CatalogoFeriaPublico>
)

data class FeriaPublicaApi(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val organizador: String,
    val direccion: String,
    val fechaInicio: Date?,
    val fechaFin: Date?,
    val latitud: Double?,
    val longitud: Double?
)

internal suspend fun datosApiComprador(ruta: String): JSONObject = withContext(Dispatchers.IO) {
    val conexion = URL(BuildConfig.API_BASE_URL.trimEnd('/') + ruta).openConnection() as HttpURLConnection
    try {
        conexion.requestMethod = "GET"
        conexion.connectTimeout = 10_000
        conexion.readTimeout = 20_000
        conexion.setRequestProperty("Accept", "application/json")
        val status = conexion.responseCode
        val contenido = (if (status in 200..299) conexion.inputStream else conexion.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val mensaje = runCatching {
                JSONObject(contenido).getJSONObject("error").getString("mensaje")
            }.getOrNull()
            throw IllegalStateException(mensaje ?: "La API no pudo completar la lectura pública.")
        }
        JSONObject(contenido).optJSONObject("datos")
            ?: throw IllegalStateException("La API devolvió una respuesta incompleta.")
    } catch (error: SocketTimeoutException) {
        throw IllegalStateException("La API del proyecto tardó demasiado en responder. Reintenta.", error)
    } catch (error: ConnectException) {
        throw IllegalStateException("No se pudo conectar con la API local del proyecto.", error)
    } catch (error: IllegalStateException) {
        throw error
    } catch (error: Exception) {
        throw IllegalStateException("No se pudo consultar la API pública del proyecto.", error)
    } finally {
        conexion.disconnect()
    }
}

object ApiFeriasComprador {
    suspend fun leerFerias(): List<FeriaPublicaApi> {
        val datos = datosApiComprador("/feriasPublicas")
        val lista = datos.optJSONArray("ferias") ?: return emptyList()
        return buildList {
            for (indice in 0 until lista.length()) {
                val feria = lista.optJSONObject(indice) ?: continue
                val id = feria.optString("id").trim()
                val nombre = feria.optString("nombre").trim()
                if (id.isBlank() || nombre.isBlank()) continue
                add(
                    FeriaPublicaApi(
                        id = id,
                        nombre = nombre,
                        descripcion = feria.optString("descripcion"),
                        organizador = feria.optString("organizador"),
                        direccion = feria.optString("direccion"),
                        fechaInicio = fecha(feria.optString("fechaInicio")),
                        fechaFin = fecha(feria.optString("fechaFin")),
                        latitud = feria.optDouble("latitud").takeIf { !feria.isNull("latitud") && it.isFinite() },
                        longitud = feria.optDouble("longitud").takeIf { !feria.isNull("longitud") && it.isFinite() }
                    )
                )
            }
        }
    }

    suspend fun leerParticipantes(feriaId: String): List<ParticipanteFeriaPublico> {
        val datos = datosApiComprador("/feriasPublicas/" + Uri.encode(feriaId))
        val lista = datos.optJSONArray("participantes") ?: return emptyList()
        return buildList {
            for (indice in 0 until lista.length()) {
                val participante = lista.optJSONObject(indice) ?: continue
                val nombre = participante.optString("nombre").trim()
                if (nombre.isBlank()) continue
                val tiendasJson = participante.optJSONArray("catalogo")
                val tiendas = buildList {
                    if (tiendasJson != null) {
                        for (indiceTienda in 0 until tiendasJson.length()) {
                            val tienda = tiendasJson.optJSONObject(indiceTienda) ?: continue
                            val nombreTienda = tienda.optString("nombreTienda").trim()
                            if (nombreTienda.isBlank()) continue
                            val productosJson = tienda.optJSONArray("productos")
                            val productos = buildList {
                                if (productosJson != null) {
                                    for (indiceProducto in 0 until productosJson.length()) {
                                        val producto = productosJson.optString(indiceProducto).trim()
                                        if (producto.isNotBlank()) add(producto)
                                    }
                                }
                            }
                            add(CatalogoFeriaPublico(nombreTienda, productos))
                        }
                    }
                }
                add(
                    ParticipanteFeriaPublico(
                        tipo = participante.optString("tipo", "emprendedora"),
                        nombre = nombre,
                        catalogo = tiendas
                    )
                )
            }
        }
    }

    private fun fecha(valor: String): Date? = runCatching {
        Date.from(Instant.parse(valor))
    }.getOrNull()
}