package dev.cocoa.speech.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// M3 Expressive: springy motion everywhere, and the neutral palette from the results mockups (warm off-white ground,
// white cards, near-black ink). Listener colors are separate and fixed (listeners/ListenerColors).
private val AppColors = lightColorScheme(
    primary = Color(0xFF1E2329),
    onPrimary = Color.White,
    background = Color(0xFFF4F5F2),
    onBackground = Color(0xFF1E2329),
    surface = Color(0xFFF4F5F2),
    onSurface = Color(0xFF1E2329),
    onSurfaceVariant = Color(0xFF5A6170),
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = Color(0xFFECEEF0),
    outlineVariant = Color(0xFFD9DCE1),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(colorScheme = AppColors, motionScheme = MotionScheme.expressive(), content = content)
}

// Shorthands for the theme's motion springs, so screens animate with the expressive scheme.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> MaterialTheme.motionSchemeFast(): FiniteAnimationSpec<T> = motionScheme.fastSpatialSpec()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> MaterialTheme.motionSchemeSpatial(): FiniteAnimationSpec<T> = motionScheme.defaultSpatialSpec()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> MaterialTheme.motionSchemeEffects(): FiniteAnimationSpec<T> = motionScheme.defaultEffectsSpec()
