package app.teletype.sdk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import app.teletype.sdk.types.ChatBackground
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChatBackgroundLayer(background: ChatBackground) {
    when (background) {
        ChatBackground.White -> Box(Modifier.fillMaxSize().background(Color.White))
        ChatBackground.Doodle -> PatternCanvas { doodle(it) }
        ChatBackground.Circles -> PatternCanvas { circles(it) }
        ChatBackground.Scribbles -> PatternCanvas { scribbles(it) }
    }
}

@Composable
private fun PatternCanvas(draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val ink = Color(0xFF9AA3AB).copy(alpha = 0.18f)
    Canvas(Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
        draw(ink)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.doodle(color: Color) {
    val step = 48f
    var y = 12f
    while (y < size.height) {
        var x = 8f
        var flip = false
        while (x < size.width) {
            val path = Path()
            path.moveTo(x, y)
            path.quadraticTo(x + 10f, y + if (flip) -10f else 10f, x + 20f, y)
            drawPath(path, color, style = Stroke(width = 1.4f))
            x += step
            flip = !flip
        }
        y += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.circles(color: Color) {
    val step = 42f
    var y = 16f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 16f else 36f
        while (x < size.width) {
            drawCircle(color, radius = 7f, center = Offset(x, y), style = Stroke(width = 1.2f))
            x += step
        }
        y += step
        row++
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.scribbles(color: Color) {
    val step = 36f
    var y = 10f
    while (y < size.height) {
        var x = 6f
        while (x < size.width) {
            val path = Path()
            path.moveTo(x, y)
            for (i in 1..6) {
                val t = i / 6f
                path.lineTo(
                    x + 22f * t,
                    y + sin(t * PI * 2).toFloat() * 5f,
                )
            }
            drawPath(path, color, style = Stroke(width = 1.2f))
            x += step
        }
        y += step
    }
}

@Suppress("unused")
private fun wave(i: Int): Float = (cos(i * 0.4) * 4).toFloat()
