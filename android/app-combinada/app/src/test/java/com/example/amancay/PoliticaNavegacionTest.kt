package com.example.amancay

import com.example.amancay.buyer.evaluarPoliticaNavegacion
import org.junit.Assert.assertEquals
import org.junit.Test

class PoliticaNavegacionTest {
    @Test
    fun permitirDejaEntrarSinSesion() {
        assertEquals(
            false to false,
            evaluarPoliticaNavegacion("permitir", false, false, false).let { it.bloqueaCatalogo to it.muestraInvitacion },
        )
    }

    @Test
    fun invitarPermiteOmitirLaInvitacion() {
        assertEquals(
            false to true,
            evaluarPoliticaNavegacion("invitar", false, false, false).let { it.bloqueaCatalogo to it.muestraInvitacion },
        )
        assertEquals(
            false to false,
            evaluarPoliticaNavegacion("invitar", false, false, true).let { it.bloqueaCatalogo to it.muestraInvitacion },
        )
    }

    @Test
    fun exigirGoogleBloqueaSinGoogleAunqueExistaSesionDeCorreo() {
        assertEquals(
            true to false,
            evaluarPoliticaNavegacion("exigir", false, false, false).let { it.bloqueaCatalogo to it.muestraInvitacion },
        )
        assertEquals(
            true to false,
            evaluarPoliticaNavegacion("exigir", true, false, false).let { it.bloqueaCatalogo to it.muestraInvitacion },
        )
        assertEquals(
            false to false,
            evaluarPoliticaNavegacion("exigir", true, true, false).let { it.bloqueaCatalogo to it.muestraInvitacion },
        )
    }

    @Test
    fun esperaLaPoliticaRemotaAntesDeAbrirElCatalogo() {
        assertEquals(
            true to false,
            evaluarPoliticaNavegacion(null, false, false, false).let { it.bloqueaCatalogo to it.muestraInvitacion },
        )
    }
}
