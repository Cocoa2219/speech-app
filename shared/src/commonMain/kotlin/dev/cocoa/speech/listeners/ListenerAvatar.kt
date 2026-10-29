package dev.cocoa.speech.listeners

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import dev.cocoa.speech.model.Persona
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// The three listeners as approved on the "Listener characters" design canvas (2026-10-29): flat shapes, no outlines,
// no mouth; eyes and a nose. Each sits on its own badge shape (identity by shape, not only color). Raising a hand
// morphs the badge point by point into a 12-point burst, lifts a short arm from behind the ear, adds brows, and widens
// the eyes. Drawn in a 120x120 space, scaled to the composable's size.

// Fixed listener colors: they must mean the same listener everywhere, so they do not follow the theme.
object ListenerColors {
    val Ink = Color(0xFF1E2329)
    val Face = Color(0xFFFFF7EE)
    val Nose = Color(0xFFE8B994)

    fun main(persona: Persona): Color = when (persona) {
        Persona.CLASSMATE -> Color(0xFF2F6FDE)
        Persona.TEACHER -> Color(0xFF136F63)
        Persona.KID -> Color(0xFFE89A1C)
    }

    fun tint(persona: Persona): Color = when (persona) {
        Persona.CLASSMATE -> Color(0xFFD6E4FB)
        Persona.TEACHER -> Color(0xFFCFE7E2)
        Persona.KID -> Color(0xFFFCE5BF)
    }
}

// Spring from the design canvas: stiffness 380, damping 18, i.e. damping ratio ~0.46 (a visible overshoot).
@Composable
fun AnimatedListenerAvatar(persona: Persona, raised: Boolean, modifier: Modifier = Modifier) {
    val raise by animateFloatAsState(
        targetValue = if (raised) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.46f, stiffness = 380f),
        label = "raise",
    )
    ListenerAvatar(persona, raise = { raise }, modifier = modifier)
}

// raise: 0 = resting, 1 = hand up; values past 1 come from spring overshoot. Read in the draw phase only.
@Composable
fun ListenerAvatar(persona: Persona, raise: () -> Float, modifier: Modifier = Modifier) {
    val parts = remember(persona) { ListenerParts.of(persona) }
    Canvas(modifier) {
        val p = raise()
        scale(size.minDimension / VIEW, pivot = Offset.Zero) { drawListener(persona, parts, p) }
    }
}

private const val VIEW = 120f

private fun DrawScope.drawListener(persona: Persona, parts: ListenerParts, p: Float) {
    val q = p.coerceIn(0f, 1f)
    val main = ListenerColors.main(persona)
    val face = ListenerColors.Face
    val ink = ListenerColors.Ink

    drawPath(BadgeShapes.morph(parts.badge, p), ListenerColors.tint(persona))

    // Arm rises from behind the head; drawn before the face so the sleeve tucks behind the ear.
    val armAlpha = (p * 2f).coerceIn(0f, 1f)
    if (armAlpha > 0f) {
        translate(top = (1f - p) * 24f) {
            drawLine(main, parts.armFrom, parts.armTo, strokeWidth = parts.armWidth, cap = StrokeCap.Round, alpha = armAlpha)
            drawOval(face, topLeft = parts.hand.topLeft, size = parts.hand.size, alpha = armAlpha)
        }
    }

    for (ear in parts.ears) drawCircle(face, ear.radius, ear.center)
    drawOval(face, topLeft = parts.head.topLeft, size = parts.head.size)
    drawPath(parts.hair, main)
    parts.sprout?.let { (stem, leaf) ->
        drawPath(stem, main, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
        drawPath(leaf, main)
    }

    val browAlpha = ((p - 0.3f) / 0.4f).coerceIn(0f, 1f)
    if (browAlpha > 0f) {
        for (brow in parts.brows) {
            rotate(brow.degrees, pivot = brow.pivot) {
                drawRoundRect(main, brow.topLeft, brow.size, CornerRadius(brow.size.height / 2), alpha = browAlpha)
            }
        }
    }

    for (cheek in parts.cheeks) drawCircle(Color(0xFFF8D6A6), 4.2f, cheek)
    if (persona == Persona.TEACHER) {
        val glasses = Stroke(width = 2.5f, cap = StrokeCap.Round)
        for (eye in parts.eyes) drawCircle(ink, 8f, eye, style = glasses)
        drawPath(parts.glassesBridge!!, ink, style = glasses)
    }

    val eyeRadius = parts.eyeRadius + parts.eyeGrow * q
    for (eye in parts.eyes) drawCircle(ink, eyeRadius, eye)
    val shine = if (parts.alwaysShine) 1f else ((p - 0.5f) * 2f).coerceIn(0f, 1f)
    if (shine > 0f) {
        for (eye in parts.eyes) drawCircle(Color.White, 1.3f, eye + Offset(1.4f, -1.4f), alpha = shine)
    }
    drawOval(ListenerColors.Nose, topLeft = parts.nose.topLeft, size = parts.nose.size)
}

private class Circle(val center: Offset, val radius: Float)
private class Oval(cx: Float, cy: Float, rx: Float, ry: Float) {
    val topLeft = Offset(cx - rx, cy - ry)
    val size = Size(2 * rx, 2 * ry)
}
private class Brow(x: Float, y: Float, w: Float, h: Float, val degrees: Float) {
    val topLeft = Offset(x, y)
    val size = Size(w, h)
    val pivot = Offset(x + w / 2, y + h / 2)
}

// Per-listener geometry, in the 120x120 design space (copied from the approved SVGs).
private class ListenerParts(
    val badge: BadgeShapes.Shape,
    val ears: List<Circle>,
    val head: Oval,
    val hair: Path,
    val sprout: Pair<Path, Path>? = null,
    val brows: List<Brow>,
    val eyes: List<Offset>,
    val eyeRadius: Float,
    val eyeGrow: Float,
    val alwaysShine: Boolean = false,
    val cheeks: List<Offset> = emptyList(),
    val glassesBridge: Path? = null,
    val nose: Oval,
    val armFrom: Offset,
    val armTo: Offset,
    val armWidth: Float,
    val hand: Oval,
) {
    companion object {
        private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

        fun of(persona: Persona): ListenerParts = when (persona) {
            Persona.CLASSMATE -> ListenerParts(
                badge = BadgeShapes.Shape.COOKIE,
                ears = listOf(Circle(Offset(31f, 69f), 7f), Circle(Offset(89f, 69f), 7f)),
                head = Oval(60f, 67f, 29f, 29f),
                hair = svg("M30 64 C28 41 44 31 60 31 C77 31 92 41 90 64 C82 54 71 50 60 52 C50 49 38 54 30 64 Z"),
                brows = listOf(Brow(44f, 57f, 11f, 3.4f, -8f), Brow(65f, 57f, 11f, 3.4f, 8f)),
                eyes = listOf(Offset(50f, 71f), Offset(70f, 71f)),
                eyeRadius = 3.4f,
                eyeGrow = 0.8f,
                nose = Oval(60f, 80f, 3.2f, 2.4f),
                armFrom = Offset(91f, 82f), armTo = Offset(97f, 59f), armWidth = 9f,
                hand = Oval(98f, 50f, 7f, 8.5f),
            )
            Persona.TEACHER -> ListenerParts(
                badge = BadgeShapes.Shape.SQUARE,
                ears = listOf(Circle(Offset(32f, 69f), 6.5f), Circle(Offset(88f, 69f), 6.5f)),
                head = Oval(60f, 67f, 27f, 30f),
                hair = svg("M32 62 C30 41 44 32 61 32 C78 32 90 42 88 62 C86 54 81 49 74 47 C64 44 50 46 42 52 C37 55 34 58 32 62 Z"),
                brows = listOf(Brow(43f, 55f, 11f, 3.2f, -6f), Brow(66f, 55f, 11f, 3.2f, 6f)),
                eyes = listOf(Offset(49f, 70f), Offset(71f, 70f)),
                eyeRadius = 2.6f,
                eyeGrow = 0.6f,
                glassesBridge = svg("M57 69 Q60 67 63 69"),
                nose = Oval(60f, 82f, 3f, 2.3f),
                armFrom = Offset(90f, 82f), armTo = Offset(97f, 59f), armWidth = 9f,
                hand = Oval(98f, 50f, 7f, 8.5f),
            )
            Persona.KID -> ListenerParts(
                badge = BadgeShapes.Shape.FLOWER,
                ears = listOf(Circle(Offset(35f, 73f), 6f), Circle(Offset(85f, 73f), 6f)),
                head = Oval(60f, 71f, 26f, 26f),
                hair = svg("M34 69 C33 53 45 44 60 44 C75 44 87 53 86 69 C80 61 72 58 64 60 C62 55 58 53 54 55 C46 57 38 61 34 69 Z"),
                sprout = svg("M60 45 C60 38 57 33 52 30") to svg("M59 40 C63 33 70 32 74 34 C71 40 65 42 59 40 Z"),
                brows = listOf(Brow(44f, 62f, 10f, 3.2f, -10f), Brow(66f, 62f, 10f, 3.2f, 10f)),
                eyes = listOf(Offset(50f, 74f), Offset(70f, 74f)),
                eyeRadius = 3.9f,
                eyeGrow = 0.5f,
                alwaysShine = true,
                cheeks = listOf(Offset(44f, 82f), Offset(76f, 82f)),
                nose = Oval(60f, 82f, 2.6f, 2f),
                armFrom = Offset(88f, 86f), armTo = Offset(95f, 64f), armWidth = 8f,
                hand = Oval(96f, 55f, 6.5f, 8f),
            )
        }
    }
}

// Badge outlines sampled as radii at the same 108 angles, so any two morph point by point (no shape library needed).
internal object BadgeShapes {
    enum class Shape { COOKIE, SQUARE, FLOWER }

    private const val N = 108

    private fun sample(f: (Double) -> Double) = FloatArray(N) { i -> f(2 * PI * i / N).toFloat() }

    private val cookie = sample { t -> 53 + 3.0 * cos(9 * t) }
    private val square = sample { t -> 51 / (abs(cos(t)).pow(4.2) + abs(sin(t)).pow(4.2)).pow(1 / 4.2) }
    private val flower = sample { t -> 49.5 + 6.5 * cos(8 * t) }
    private val burst = sample { t -> 50 + 6.5 * cos(12 * t) }

    private fun radii(shape: Shape) = when (shape) {
        Shape.COOKIE -> cookie
        Shape.SQUARE -> square
        Shape.FLOWER -> flower
    }

    // p = 0 is the listener's own shape, 1 the burst; p past 1 (spring overshoot) extrapolates slightly.
    fun morph(shape: Shape, p: Float): Path {
        val from = radii(shape)
        val path = Path()
        for (i in 0 until N) {
            val a = 2 * PI * i / N - PI / 2
            val r = from[i] * (1 - p) + burst[i] * p
            val x = (60 + r * cos(a)).toFloat()
            val y = (60 + r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
