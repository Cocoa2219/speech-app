package dev.cocoa.speech.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import speech_app.shared.generated.resources.Res
import speech_app.shared.generated.resources.pretendard_bold
import speech_app.shared.generated.resources.pretendard_medium
import speech_app.shared.generated.resources.pretendard_regular
import speech_app.shared.generated.resources.pretendard_semibold

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
    MaterialExpressiveTheme(
        colorScheme = AppColors,
        motionScheme = MotionScheme.expressive(),
        typography = pretendardTypography(),
        content = content,
    )
}

// Pretendard (v1.3.9, SIL OFL 1.1, composeResources/files/licenses/pretendard-OFL.txt) in four weights, bundled so
// Hangul, Latin, and numbers look the same on every device instead of following the phone's system font.
@Composable
private fun pretendardTypography(): Typography {
    val pretendard = FontFamily(
        Font(Res.font.pretendard_regular, FontWeight.Normal),
        Font(Res.font.pretendard_medium, FontWeight.Medium),
        Font(Res.font.pretendard_semibold, FontWeight.SemiBold),
        Font(Res.font.pretendard_bold, FontWeight.Bold),
    )
    return remember(pretendard) {
        val base = Typography()
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = pretendard),
            displayMedium = base.displayMedium.copy(fontFamily = pretendard),
            displaySmall = base.displaySmall.copy(fontFamily = pretendard),
            headlineLarge = base.headlineLarge.copy(fontFamily = pretendard),
            headlineMedium = base.headlineMedium.copy(fontFamily = pretendard),
            headlineSmall = base.headlineSmall.copy(fontFamily = pretendard),
            titleLarge = base.titleLarge.copy(fontFamily = pretendard),
            titleMedium = base.titleMedium.copy(fontFamily = pretendard),
            titleSmall = base.titleSmall.copy(fontFamily = pretendard),
            bodyLarge = base.bodyLarge.copy(fontFamily = pretendard),
            bodyMedium = base.bodyMedium.copy(fontFamily = pretendard),
            bodySmall = base.bodySmall.copy(fontFamily = pretendard),
            labelLarge = base.labelLarge.copy(fontFamily = pretendard),
            labelMedium = base.labelMedium.copy(fontFamily = pretendard),
            labelSmall = base.labelSmall.copy(fontFamily = pretendard),
        )
    }
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
