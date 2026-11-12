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
val Track = Color(0xFF30384C)
val Active = Color(0xFFAFA0EE)

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
                primary = Color(0xFF67519E),
                onPrimary = Color.White,
                background = Color(0xFFF4F2F9),
                surface = Color(0xFFEAE6F2),
                onBackground = Color(0xFF202437),
                onSurface = Color(0xFF202437),
                onSurfaceVariant = Color(0xFF555D73),
                outline = Color(0xFF747A8C),
                secondary = Color(0xFF67519E),
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
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        aside?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LevelSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    display: String = "${(value*100).roundToInt()}%",
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label },
        )
    }
}

@Composable
fun Waveform(playing: Boolean, reducedMotion: Boolean) {
    val resumed =
        LocalLifecycleOwner.current.lifecycle
            .currentStateAsState()
            .value
            .isAtLeast(Lifecycle.State.RESUMED)
    val phase =
        if (playing && !reducedMotion && resumed) {
            val transition = rememberInfiniteTransition(label = "Quiet waveform")
            transition
                .animateFloat(
                    0f,
                    (2 * PI).toFloat(),
                    infiniteRepeatable(tween(9000, easing = LinearEasing)),
                    label = "Wave phase",
                )
                .value
        } else 0f
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().height(52.dp).semantics {
            contentDescription = if (playing) "Soundscape playing" else "Soundscape paused"
        }
    ) {
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
                else MaterialTheme.colorScheme.outline.copy(alpha = .3f),
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
                Text("Nightfall", style = MaterialTheme.typography.headlineMedium)
                Waveform(false, true)
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
                Text("Nightfall", style = MaterialTheme.typography.headlineMedium)
                LevelSlider("Master volume", .5f, {})
            }
        }
    }
}
