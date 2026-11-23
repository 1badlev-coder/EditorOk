package site.kentok.lists.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import site.kentok.lists.R

/**
 * Белый splash: логотип растёт с 10% до максимального размера,
 * который ещё помещается на экран (с небольшим полем).
 */
@Composable
fun AnimatedSplash(
    onFinished: () -> Unit
) {
    val scale = remember { Animatable(0.1f) }
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Максимум ~86% меньшей стороны экрана — не выходим за края
            val maxSide = minOf(maxWidth, maxHeight) * 0.86f
            val sidePx = with(density) { maxSide.toPx() }

            Image(
                painter = painterResource(R.drawable.splash_icon),
                contentDescription = "Editor-Ok",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(maxSide)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        // якорь масштаба — центр
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                    }
            )

            LaunchedEffect(sidePx) {
                scale.snapTo(0.1f)
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
                )
                delay(280)
                onFinished()
            }
        }
    }
}
