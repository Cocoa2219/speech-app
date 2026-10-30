package dev.cocoa.speech.listeners

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.cocoa.speech.model.Persona

// A listener who bobs gently while listening (recording, analyzing). Each listener starts a little later than the one
// before, so the three never move in lockstep. The bob is read in the draw phase only.
@Composable
fun ListeningAvatar(persona: Persona, listening: Boolean, modifier: Modifier = Modifier, raised: Boolean = false) {
    val bob = rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(persona.ordinal * 300),
        ),
        label = "bob",
    )
    AnimatedListenerAvatar(
        persona,
        raised,
        modifier.graphicsLayer { translationY = if (listening) -5.dp.toPx() * bob.value else 0f },
    )
}
