package app.teletype.sdk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/**
 * Chat launcher glyph (speech bubble + smile), ported from the RN SVG.
 * Viewport is 38×41.
 */
@Composable
fun ChatButtonIcon(
    size: Float,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val width = size.dp
    val height = (size * 41f / 38f).dp
    Canvas(modifier = modifier.size(width, height)) {
        val sx = this.size.width / 38f
        val sy = this.size.height / 41f
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(18.999961f * sx, 0f)
            cubicTo(29.493385f * sx, 0f, 38f * sx, 8.498143f * sy, 38f * sx, 18.981429f * sy)
            cubicTo(38f * sx, 29.464714f * sy, 29.493463f * sx, 37.962941f * sy, 19.000039f * sx, 37.962941f * sy)
            cubicTo(17.105667f * sx, 37.962941f * sy, 15.276586f * sx, 37.684655f * sy, 13.550083f * sx, 37.169152f * sy)
            lineTo(7.080746f * sx, 41f * sy)
            lineTo(7.080746f * sx, 33.762634f * sy)
            cubicTo(2.762889f * sx, 30.283178f * sy, 0f, 24.955005f * sy, 0f, 18.981513f * sy)
            cubicTo(0f, 8.498227f * sy, 8.506537f * sx, 0f, 18.999961f * sx, 0f)
            close()
            moveTo(28.197567f * sx, 22.047052f * sy)
            cubicTo(27.611223f * sx, 21.867467f * sy, 26.994069f * sx, 22.21903f * sy, 26.821216f * sx, 22.810875f * sy)
            cubicTo(25.812692f * sx, 26.308615f * sy, 22.597124f * sx, 28.751667f * sy, 19.00014f * sx, 28.751667f * sy)
            cubicTo(15.403156f * sx, 28.751667f * sy, 12.18506f * sx, 26.308534f * sy, 11.1778f * sx, 22.810875f * sy)
            cubicTo(11.006211f * sx, 22.21903f * sy, 10.396404f * sx, 21.867548f * sy, 9.800185f * sx, 22.047052f * sy)
            cubicTo(9.210128f * sx, 22.221539f * sy, 8.87319f * sx, 22.846242f * sy, 9.044701f * sx, 23.440596f * sy)
            cubicTo(10.326014f * sx, 27.88927f * sy, 14.42173f * sx, 31f * sy, 19.000061f * sx, 31f * sy)
            cubicTo(23.578392f * sx, 31f * sy, 27.671738f * sx, 27.88919f * sy, 28.9555f * sx, 23.440596f * sy)
            cubicTo(29.127011f * sx, 22.846242f * sy, 28.787624f * sx, 22.221539f * sy, 28.197567f * sx, 22.047052f * sy)
            close()
        }
        drawPath(path, SolidColor(color))
    }
}
