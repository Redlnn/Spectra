package top.redlnn.spectra.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun AboutBackdrop(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val dark = LocalDarkMode.current
    val palette =
        remember(primary) {
            listOf(
                    Color(0xFF1D4ED8),
                    Color(0xFF7C3AED),
                    Color(0xFF008D8A),
                    Color(0xFFD23E7B),
                )
                .map { lerp(it, primary, .1f) }
        }
    val transition = rememberInfiniteTransition(label = "about-halo")
    val phase =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
            label = "halo-orbit",
        )
    Canvas(modifier) {
        // Read animation state while drawing, without recomposing the license list each frame.
        val progress = phase.value
        val angle = progress * (2 * PI).toFloat()
        drawRect(background)
        val radius = size.maxDimension * .54f
        val centers =
            listOf(
                Offset(
                    size.width * (.22f + .25f * sin(angle)),
                    size.height * (.12f + .1f * cos(angle)),
                ),
                Offset(
                    size.width * (.82f + .2f * cos(angle)),
                    size.height * (.46f + .16f * sin(angle)),
                ),
                Offset(
                    size.width * (.42f + .28f * cos(angle)),
                    size.height * (.91f + .1f * sin(angle)),
                ),
            )
        centers.forEachIndexed { index, center ->
            val colorPosition = (progress * palette.size + index * palette.size / 3f) % palette.size
            val colorIndex = colorPosition.toInt()
            val color =
                lerp(
                        palette[colorIndex],
                        palette[(colorIndex + 1) % palette.size],
                        colorPosition - colorIndex,
                    )
                    .copy(alpha = if (dark) .78f else .36f)
            drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center, radius),
                radius = radius,
                center = center,
            )
        }
    }
}
