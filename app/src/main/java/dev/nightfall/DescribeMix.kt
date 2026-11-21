// SPDX-License-Identifier: MIT
package dev.nightfall

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nightfall.core.Library
import dev.nightfall.core.LocalPhraseInterpreter
import dev.nightfall.core.PhraseInterpreter
import dev.nightfall.core.PhraseLayer
import dev.nightfall.core.PhrasePlan
import kotlin.math.roundToInt

/**
 * "Type what you want and it makes the mix."
 *
 * The interpretation is shown before anything plays — each layer as a chip carrying its level, with
 * a close target on it — so a wrong reading is visible and correctable in one tap rather than
 * hidden. The card sits under the transport on Home because that is where a mix starts and where
 * the play control already is; the Library stays the browsing path.
 *
 * A phrase that matches nothing, or that contradicts itself ("loud rain, silent"), says so and
 * offers the Library instead of inventing a mix.
 */
@Composable
internal fun DescribeMix(
    r: NightfallRuntime,
    onBrowse: () -> Unit,
    // The field's handle for whoever wants to put a cursor in it: Home hands this to the first-run
    // card's "type what you want" row.
    fieldFocus: FocusRequester = remember { FocusRequester() },
    // The seam: the phrase box talks to the interface, and the offline interpreter is the only
    // implementation. A test or a future implementation passes its own; nothing here reaches a
    // network or holds a key.
    interpreter: PhraseInterpreter = LocalPhraseInterpreter,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    var asked by rememberSaveable { mutableStateOf("") }
    var removed by rememberSaveable(asked) { mutableStateOf(ArrayList<String>()) }
    var timerRemoved by rememberSaveable(asked) { mutableStateOf(false) }
    val plan = remember(asked) { if (asked.isBlank()) null else interpreter.interpret(asked) }
    val focus = LocalFocusManager.current
    val fieldLabel = stringResource(R.string.describe_a11y_field)

    fun submit() {
        asked = typed.trim()
        removed = ArrayList()
        timerRemoved = false
        focus.clearFocus()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // One row while it is only an invitation: the transport and the mix stay in view, and the
            // card grows only when there is something to explain.
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(200) },
                    modifier =
                        Modifier.weight(1f)
                            .focusRequester(fieldFocus)
                            .semantics { contentDescription = fieldLabel },
                    label = { Text(stringResource(R.string.describe_title), fontSize = 12.sp) },
                    placeholder = { Text(stringResource(R.string.describe_hint), fontSize = 12.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { submit() },
                    enabled = typed.isNotBlank(),
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Text(stringResource(R.string.describe_action_make), fontSize = 12.sp)
                }
            }
            plan?.let { understood ->
                val kept = understood.layers.filterNot { it.soundId in removed }
                val conflict = understood.conflict
                val timer = understood.timerMinutes
                when {
                    conflict != null ->
                        Problem(
                            stringResource(R.string.describe_conflict, conflict.first, conflict.second),
                            onBrowse,
                        )
                    kept.isEmpty() ->
                        Problem(stringResource(R.string.describe_empty), onBrowse)
                    else ->
                        Result(
                            plan = understood,
                            kept = kept,
                            timerRemoved = timerRemoved,
                            onRemove = { removed = ArrayList(removed).apply { add(it) } },
                            onRemoveTimer = { timerRemoved = true },
                            onPlay = {
                                val mix = understood.toMix(kept)
                                r.mix { mix }
                                if (timer != null && !timerRemoved) r.setTimer(timer.toLong())
                                if (!r.statusNow().playing) r.play()
                            },
                        )
                }
            }
        }
    }
}

@Composable
private fun Problem(text: String, onBrowse: () -> Unit) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = onBrowse, modifier = Modifier.heightIn(min = 44.dp)) {
        Text(stringResource(R.string.describe_action_browse), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Result(
    plan: PhrasePlan,
    kept: List<PhraseLayer>,
    timerRemoved: Boolean,
    onRemove: (String) -> Unit,
    onRemoveTimer: () -> Unit,
    onPlay: () -> Unit,
) {
    Text(
        stringResource(R.string.describe_understood),
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (layer in kept) {
            val name = Library.byId[layer.soundId]?.name ?: layer.soundId
            InputChip(
                selected = false,
                onClick = { onRemove(layer.soundId) },
                label = {
                    Text(
                        stringResource(
                            R.string.describe_chip,
                            name,
                            (layer.gain * 100).roundToInt(),
                        ),
                        fontSize = 12.sp,
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(R.string.describe_a11y_remove, name),
                        Modifier.size(14.dp),
                    )
                },
            )
        }
        val timer = plan.timerMinutes
        if (timer != null && !timerRemoved) {
            InputChip(
                selected = false,
                onClick = onRemoveTimer,
                label = { Text(stringResource(R.string.describe_timer, timer), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Outlined.Bedtime, null, Modifier.size(14.dp)) },
                trailingIcon = {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(R.string.describe_a11y_remove_timer),
                        Modifier.size(14.dp),
                    )
                },
            )
        }
    }
    if (plan.unrecognized.isNotEmpty())
        Text(
            stringResource(R.string.describe_unrecognized, plan.unrecognized.joinToString(", ")),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    for (substitution in plan.substitutions) {
        val name = Library.byId[substitution.soundId]?.name ?: substitution.soundId
        Text(
            stringResource(R.string.describe_substituted, substitution.asked, name),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FilledTonalButton(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.describe_action_play), fontSize = 13.sp)
    }
}
