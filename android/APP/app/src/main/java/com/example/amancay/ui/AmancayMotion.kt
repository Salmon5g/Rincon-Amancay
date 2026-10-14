package com.example.amancay.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Transición entre pantallas / pasos: desliz lateral + fundido. */
@Composable
fun ScreenTransition(key: Any, content: @Composable () -> Unit) {
    AnimatedContent(
        targetState = key,
        transitionSpec = {
            (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(280)))
        },
        label = "pantalla"
    ) { _ -> content() }
}

/** Entrada con rebote para diálogos e hitos (checks de éxito). */
@Composable
fun PopIn(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = scaleIn(
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            initialScale = 0.75f
        ) + fadeIn(),
        label = "pop"
    ) { content() }
}

/** Entrada deslizante para banners y tarjetas destacadas. */
@Composable
fun SlideDownIn(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = slideInVertically(tween(350)) { -it / 2 } + fadeIn(tween(350)),
        label = "bajada"
    ) { content() }
}

/** Número del stepper con desliz al cambiar. */
@Composable
fun SteppingNumber(value: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically(tween(200)) { if (targetState > initialState) it / 2 else -it / 2 } +
                    fadeIn(tween(200))) togetherWith
                    (slideOutVertically(tween(200)) { if (targetState > initialState) -it / 2 else it / 2 } +
                            fadeOut(tween(200)))
        },
        label = "numero",
        modifier = modifier
    ) { v -> content(v) }
}

/** Centra y anima la primera aparición del contenido. */
@Composable
fun FadeInScreen(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(visible = shown, enter = fadeIn(tween(300)), label = "fade") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { content() }
    }
}
