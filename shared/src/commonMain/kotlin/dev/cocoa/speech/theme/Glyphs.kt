package dev.cocoa.speech.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The few line icons the screens need, drawn in a 24x24 box (no icon dependency). Decorative: the button they sit in
// carries the label.
enum class Glyph { Mic, Camera, CameraOff, Close, Check, Pause, Plus, Back, File }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val line = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            Glyph.Mic -> {
                drawRoundRect(color, Offset(9f * u, 3f * u), Size(6f * u, 11f * u), CornerRadius(3f * u), style = line)
                drawPath(path(u, "M 5.5 11 Q 5.5 17.5 12 17.5 Q 18.5 17.5 18.5 11 M 12 17.5 L 12 21"), color, style = line)
            }
            Glyph.Camera -> {
                drawRoundRect(color, Offset(3f * u, 6.5f * u), Size(13f * u, 11f * u), CornerRadius(3f * u), style = line)
                drawPath(path(u, "M 16 10.5 L 21 7.5 L 21 16.5 L 16 13.5"), color, style = line)
            }
            Glyph.CameraOff -> {
                drawRoundRect(color, Offset(3f * u, 6.5f * u), Size(13f * u, 11f * u), CornerRadius(3f * u), style = line)
                drawPath(path(u, "M 16 10.5 L 21 7.5 L 21 16.5 L 16 13.5 M 3 4 L 21 20"), color, style = line)
            }
            Glyph.Close -> drawPath(path(u, "M 6 6 L 18 18 M 18 6 L 6 18"), color, style = line)
            Glyph.Plus -> drawPath(path(u, "M 12 5 L 12 19 M 5 12 L 19 12"), color, style = line)
            Glyph.File -> drawPath(path(u, "M 6 3 L 14 3 L 19 8 L 19 21 L 6 21 L 6 3 M 14 3 L 14 8 L 19 8 M 9 13 L 16 13 M 9 17 L 14 17"), color, style = line)
            Glyph.Back -> drawPath(path(u, "M 15 5 L 8 12 L 15 19"), color, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            Glyph.Check -> drawPath(path(u, "M 5 12.5 L 10 17.5 L 19 7"), color, style = Stroke(2.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            Glyph.Pause -> {
                drawRoundRect(color, Offset(6f * u, 5f * u), Size(4f * u, 14f * u), CornerRadius(1.6f * u))
                drawRoundRect(color, Offset(14f * u, 5f * u), Size(4f * u, 14f * u), CornerRadius(1.6f * u))
            }
        }
    }
}

// Tiny path language: M, L and Q with absolute coordinates in the 24-unit box.
private fun DrawScope.path(u: Float, d: String): Path = Path().apply {
    val tokens = d.split(' ')
    var i = 0
    fun n() = tokens[i++].toFloat() * u
    while (i < tokens.size) {
        when (tokens[i++]) {
            "M" -> moveTo(n(), n())
            "L" -> lineTo(n(), n())
            "Q" -> quadraticTo(n(), n(), n(), n())
        }
    }
}
