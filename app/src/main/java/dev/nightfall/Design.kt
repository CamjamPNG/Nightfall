// SPDX-License-Identifier: MIT
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.nightfall

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.nightfall.core.Sound
import kotlin.math.*

val Navy = Color(0xFF0B101C)
val CardNavy = Color(0xFF171F32)
val Lavender = Color(0xFFB8A9F4)
val White = Color(0xFFF7F5FF)
val Muted = Color(0xFFA7B0C4)
val Divider = Color(0xFF354058)

/**
 * The inactive half of a slider's groove. It was `#30384C`, which is 1.62:1 against [Navy] and 1.40:1
 * against [CardNavy] — a control boundary nobody with low vision can find. This is 4.17:1 and 3.60:1
 * against the same two surfaces, so the groove is a groove; the thumb and the filled half stay
 * [Lavender], at 9.07:1, and remain the brightest thing in the row.
 */
val Track = Color(0xFF6A7690)
val Active = Color(0xFFAFA0EE)

/** The light theme's own palette, named so a test can measure the colours the theme really uses. */
val DayBackground = Color(0xFFF4F2F9)
val DaySurface = Color(0xFFEAE6F2)
val DayOnSurface = Color(0xFF202437)
val DayOnSurfaceVariant = Color(0xFF555D73)
val DayOutline = Color(0xFF747A8C)
val DayPrimary = Color(0xFF67519E)

@Composable
fun NightfallTheme(light: Boolean, content: @Composable () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? android.app.Activity
        SideEffect {
            activity?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
    }
    val colors =
        if (light)
            lightColorScheme(
                primary = DayPrimary,
                onPrimary = Color.White,
                background = DayBackground,
                surface = DaySurface,
                onBackground = DayOnSurface,
                onSurface = DayOnSurface,
                onSurfaceVariant = DayOnSurfaceVariant,
                outline = DayOutline,
                secondary = DayPrimary,
            )
        else
            darkColorScheme(
                primary = Lavender,
                onPrimary = Navy,
                background = Navy,
                surface = CardNavy,
                onBackground = White,
                onSurface = White,
                onSurfaceVariant = Muted,
                outline = Divider,
                secondary = Lavender,
                surfaceVariant = Track,
            )
    MaterialTheme(
        colorScheme = colors,
        typography =
            Typography(
                bodyLarge = Typography().bodyLarge.copy(fontSize = 15.sp),
                titleLarge =
                    Typography().titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp)),
        content = content,
    )
}

fun soundIcon(s: Sound?): ImageVector =
    when (s?.kind) {
        "music" ->
            if (s.variant in listOf(3, 4, 14)) Icons.Outlined.Piano else Icons.Outlined.MusicNote
        "noise" -> Icons.Outlined.GraphicEq
        "rain" -> Icons.Outlined.WaterDrop
        "thunder" -> Icons.Outlined.Thunderstorm
        "winter" -> Icons.Outlined.AcUnit
        "wind" -> Icons.Outlined.Air
        "fire" -> Icons.Outlined.LocalFireDepartment
        "water" -> Icons.Outlined.Waves
        "recording" ->
            if (s.id == "quiet-library") Icons.Outlined.LocalLibrary else Icons.Outlined.Forest
        "clock" -> Icons.Outlined.Schedule
        "binaural" -> Icons.Outlined.Headphones
        else -> Icons.Outlined.SurroundSound
    }

@Composable
fun SectionLabel(title: String, aside: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        aside?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A labelled slider. [spokenName] is what TalkBack announces; [display] is announced as the value,
 * so a row of sliders is distinguishable in a long list of layer options.
 */
@Composable
fun LevelSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    display: String = "${(value*100).roundToInt()}%",
    spokenName: String = label,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                display,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        SlimSlider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier =
                Modifier.fillMaxWidth().height(48.dp).semantics {
                    contentDescription = spokenName
                    stateDescription = display
                },
        )
    }
}

/**
 * Speaks [text] when it changes, and draws nothing.
 *
 * Two rules make this worth having. It is polite, so it queues behind whatever is already being read
 * instead of interrupting. And it is only ever given a *setting* — that the timer was just set to
 * thirty minutes — never a value on a meter tick, because a live region that updates twice a second
 * would read the clock aloud all night.
 */
@Composable
fun LiveAnnouncement(text: String) {
    if (text.isEmpty()) return
    Text(
        text,
        Modifier.fillMaxWidth().alpha(0f).semantics { liveRegion = LiveRegionMode.Polite },
        fontSize = 1.sp,
        maxLines = 1,
    )
}

@Composable
fun Waveform(playing: Boolean, reducedMotion: Boolean) {
    val resumed =
        LocalLifecycleOwner.current.lifecycle
            .currentStateAsState()
            .value
            .isAtLeast(Lifecycle.State.RESUMED)
    // Keep the animation as a State and never read it here: reading it during composition would
    // recompose this composable, and the card around it, on every animation frame.
    val phase: State<Float>? =
        if (playing && !reducedMotion && resumed) {
            val transition = rememberInfiniteTransition(label = "Quiet waveform")
            transition.animateFloat(
                0f,
                (2 * PI).toFloat(),
                infiniteRepeatable(tween(9000, easing = LinearEasing)),
                label = "Wave phase",
            )
        } else null
    val color = MaterialTheme.colorScheme.primary
    // Decorative: every fact in this picture — that the mix is running — is already the play
    // control's own state, three inches away. Left in the tree it was a focus stop that announced
    // "Soundscape playing" between the play button and the timer.
    Canvas(Modifier.fillMaxWidth().height(52.dp).clearAndSetSemantics {}) {
        // Read in the draw phase: a phase change invalidates this drawing only.
        val phase = phase?.value ?: 0f
        val bars = 53
        val step = size.width / bars
        repeat(bars) { i ->
            val envelope = sin(PI * (i + .5) / bars).toFloat().pow(.6f)
            val h =
                (.12f + abs(sin(i * .61f + phase) * cos(i * .23f - phase * .4f)) * .88f) *
                    envelope *
                    size.height
            drawRoundRect(
                color.copy(alpha = if (playing) .85f else .4f),
                androidx.compose.ui.geometry.Offset(i * step + step * .3f, (size.height - h) / 2),
                androidx.compose.ui.geometry.Size(step * .3f, h.coerceAtLeast(3f)),
                androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            )
        }
    }
}

@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val colors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor =
                if (MaterialTheme.colorScheme.background == Navy) Active
                else MaterialTheme.colorScheme.primary,
            inactiveTrackColor =
                if (MaterialTheme.colorScheme.background == Navy) Track
                else MaterialTheme.colorScheme.outline,
        )
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        colors = colors,
        thumb = {
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(4.dp),
                colors = colors,
                thumbTrackGapSize = 4.dp,
                drawStopIndicator = null,
            )
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Nightfall dark",
    showBackground = true,
    widthDp = 412,
)
@Composable
private fun DesignPreview() {
    NightfallTheme(false) {
        Surface {
            Column(Modifier.padding(24.dp)) {
                // hardcoded-copy-allowed: preview only, never built into the app.
                Text("Nightfall", style = MaterialTheme.typography.headlineMedium)
                Waveform(false, true)
                // hardcoded-copy-allowed: preview only, never built into the app.
                LevelSlider("Master volume", .5f, {})
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Nightfall light",
    showBackground = true,
    widthDp = 360,
    fontScale = 1.3f,
)
@Composable
private fun LightDesignPreview() {
    NightfallTheme(true) {
        Surface {
            Column(Modifier.padding(24.dp)) {
                // hardcoded-copy-allowed: preview only, never built into the app.
                Text("Nightfall", style = MaterialTheme.typography.headlineMedium)
                // hardcoded-copy-allowed: preview only, never built into the app.
                LevelSlider("Master volume", .5f, {})
            }
        }
    }
}
