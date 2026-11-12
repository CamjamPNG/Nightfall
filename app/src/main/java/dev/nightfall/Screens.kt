// SPDX-License-Identifier: MIT
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.nightfall

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nightfall.core.*
import kotlin.math.roundToInt

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.6).sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SoundLibrary(r: NightfallRuntime) {
    val saved by r.saved.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    val sounds =
        Library.all.filter {
            (category == "All" || it.category == category) &&
                (it.name.contains(query, true) || it.category.contains(query, true))
        }
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeading("Sound library", "A little of everything. As many layers as you like.") }
        item {
            OutlinedTextField(
                query,
                { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Find your sound") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier =
                    Modifier.fillMaxWidth().semantics { contentDescription = "Search sounds" },
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All") + Library.all.map { it.category }.distinct()) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) },
                    )
                }
            }
        }
        item {
            SectionLabel(
                if (category == "All") "Explore sounds" else category,
                "${sounds.count {it.available}} available",
            )
        }
        items(sounds, key = { it.id }) { sound ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        soundIcon(sound),
                        null,
                        Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sound.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (sound.kind) {
                                "recording" -> "Field recording · offline"
                                "unavailable" -> "Recording not bundled"
                                "music" -> "Original generative music"
                                else -> sound.description
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val n = saved.current.layers.count { it.soundId == sound.id }
                        if (n > 0)
                            Text(
                                "$n in your mix",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                    }
                    IconButton(onClick = { r.add(sound) }, enabled = sound.available) {
                        Icon(
                            if (sound.available) Icons.Outlined.AddCircleOutline
                            else Icons.Outlined.Info,
                            "Add ${sound.name}",
                        )
                    }
                }
            }
        }
        if (sounds.isEmpty())
            item {
                Text(
                    "No sounds match your search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        item {
            Text(
                "Environmental synthesis is an interpretation of a sound, not a field recording. Gray, green, black and velvet are labeled configurable presets. All music is generated on your device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
fun PresetScreen(
    saved: SavedState,
    r: NightfallRuntime,
    onImport: () -> Unit,
    onExport: (Mix) -> Unit,
    onRename: (Mix) -> Unit,
) {
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeading("Find your night", "Carefully composed. Always yours to change.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport) {
                    Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import JSON")
                }
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("Favorites") },
                    leadingIcon = {
                        Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp))
                    },
                )
            }
        }
        if (saved.presets.isNotEmpty())
            item { SectionLabel("Your mixes", "${saved.presets.size} saved") }
        items(
            saved.presets.filter { !favoritesOnly || it.favorite || it.id in saved.favoriteIds },
            key = { it.id },
        ) { p ->
            PresetCard(p, r, false, onExport, onRename)
        }
        item { SectionLabel("Nightfall collection", "15 soundscapes") }
        items(
            Presets.all.filter { !favoritesOnly || it.id in saved.favoriteIds },
            key = { "builtin-${it.id}" },
        ) { p ->
            PresetCard(p.copy(favorite = p.id in saved.favoriteIds), r, true, onExport, onRename)
        }
    }
}

@Composable
private fun PresetCard(
    p: Mix,
    r: NightfallRuntime,
    builtin: Boolean,
    onExport: (Mix) -> Unit,
    onRename: (Mix) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${p.layers.size} layers",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(
                    onClick = {
                        if (builtin)
                            r.change {
                                it.copy(
                                    favoriteIds =
                                        if (p.id in it.favoriteIds) it.favoriteIds - p.id
                                        else it.favoriteIds + p.id
                                )
                            }
                        else r.updatePreset(p.id) { it.copy(favorite = !it.favorite) }
                    }
                ) {
                    Icon(
                        if (p.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite ${p.name}",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreHoriz, "Options for ${p.name}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                r.savePreset(p.copy(name = "${p.name} copy"))
                                menu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export JSON") },
                            onClick = {
                                onExport(p)
                                menu = false
                            },
                        )
                        if (!builtin) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    onRename(p)
                                    menu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    r.deletePreset(p.id)
                                    menu = false
                                },
                            )
                        }
                    }
                }
            }
            Text(
                p.layers.joinToString("  ·  ") { it.name },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            TextButton(
                onClick = { r.usePreset(p) },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text("Play soundscape")
            }
        }
    }
}

@Composable
fun Studio(
    r: NightfallRuntime,
    layerId: String?,
    onSaved: (String) -> Unit,
    onClearEdit: () -> Unit,
) {
    val saved by r.saved.collectAsStateWithLifecycle()
    val existing = saved.current.layers.firstOrNull { it.id == layerId }
    var encoded by
        rememberSaveable(layerId) {
            mutableStateOf(PresetJson.json.encodeToString(existing?.music ?: MusicConfig()))
        }
    val c = remember(encoded) { PresetJson.json.decodeFromString<MusicConfig>(encoded) }
    fun update(f: (MusicConfig) -> MusicConfig) {
        encoded = PresetJson.json.encodeToString(f(c))
    }
    var soundId by
        rememberSaveable(layerId) { mutableStateOf(existing?.soundId ?: "440-hz-ambient-music") }
    var bass by rememberSaveable(layerId) { mutableFloatStateOf(existing?.bass ?: 0f) }
    var treble by rememberSaveable(layerId) { mutableFloatStateOf(existing?.treble ?: 0f) }
    val sound = Library.byId.getValue(soundId)
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeading(
                "Sound studio",
                if (existing != null) "Shaping ${existing.name}"
                else "A quiet composition, made by you.",
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Original. Evolving. Endless.",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Seeded chords and gentle melodies develop together. Save to your mixer to hear your composition.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Choice(
                "Music type",
                sound.name,
                Library.all.filter { it.music }.map { it.id to it.name },
            ) { id ->
                soundId = id
                update {
                    it.copy(
                        tuning =
                            when (id) {
                                "432-hz-ambient-music" -> 432f
                                "440-hz-ambient-music" -> 440f
                                else -> it.tuning
                            },
                        texture = Library.layer(id).music.texture,
                        percussion = id == "gentle-lo-fi",
                    )
                }
            }
        }
        item { SectionLabel("Musical foundation") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(432f, 440f).forEach { hz ->
                    FilterChip(
                        selected = c.tuning == hz,
                        onClick = {
                            update { it.copy(tuning = hz) }
                            if (
                                soundId == "432-hz-ambient-music" ||
                                    soundId == "440-hz-ambient-music"
                            ) {
                                soundId = "${hz.toInt()}-hz-ambient-music"
                            }
                        },
                        label = { Text("A4 = ${hz.toInt()} Hz") },
                    )
                }
            }
        }
        item {
            Text(
                "Tuning changes musical pitch. Neither tuning is established to improve sleep or produce clinical benefits.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    Choice("Key", keys[c.key], keys.mapIndexed { i, k -> i.toString() to k }) { k ->
                        update { it.copy(key = k.toInt()) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    Choice(
                        "Scale",
                        if (c.minor) "Minor" else "Major",
                        listOf("major" to "Major", "minor" to "Minor"),
                    ) { k ->
                        update { it.copy(minor = k == "minor") }
                    }
                }
            }
        }
        item {
            LevelSlider(
                "Tempo",
                c.tempo,
                { v -> update { it.copy(tempo = v) } },
                20f..90f,
                "${c.tempo.roundToInt()} BPM",
            )
        }
        item { LevelSlider("Note density", c.density, { v -> update { it.copy(density = v) } }) }
        item {
            LevelSlider(
                "Harmonic complexity",
                c.complexity,
                { v -> update { it.copy(complexity = v) } },
            )
        }
        item {
            Choice(
                "Instrument texture",
                textures[c.texture],
                textures.mapIndexed { i, t -> i.toString() to t },
            ) { v ->
                update { it.copy(texture = v.toInt()) }
            }
        }
        item { SectionLabel("Space & character") }
        item { LevelSlider("Bass", bass, { bass = it }, -12f..12f, "${bass.roundToInt()} dB") }
        item {
            LevelSlider("Treble", treble, { treble = it }, -12f..12f, "${treble.roundToInt()} dB")
        }
        item { LevelSlider("Reverb", c.reverb, { v -> update { it.copy(reverb = v) } }) }
        item { LevelSlider("Stereo delay", c.delay, { v -> update { it.copy(delay = v) } }) }
        item {
            LevelSlider(
                "Melodic randomization",
                c.randomness,
                { v -> update { it.copy(randomness = v) } },
            )
        }
        item {
            SettingToggle("Melody", "Let quiet notes wander over the chords.", c.melody) { v ->
                update { it.copy(melody = v) }
            }
        }
        if (soundId in listOf("lo-fi-sleep-music", "gentle-lo-fi"))
            item {
                SettingToggle(
                    "Low-volume percussion",
                    "A restrained pulse for lo-fi compositions.",
                    c.percussion,
                ) { v ->
                    update { it.copy(percussion = v) }
                }
            }
        item {
            OutlinedTextField(
                value = c.seed.toString(),
                onValueChange = { v ->
                    v.toLongOrNull()?.let { n -> update { it.copy(seed = n) } }
                },
                label = { Text("Composition seed") },
                supportingText = { Text("The same seed and settings reproduce the same notes.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TextButton(onClick = { update { it.copy(seed = System.nanoTime()) } }) {
                Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New variation")
            }
        }
        item {
            Button(
                onClick = {
                    if (existing != null)
                        r.mix { m ->
                            m.copy(
                                layers =
                                    m.layers.map { l ->
                                        if (l.id == existing.id)
                                            l.copy(
                                                id = java.util.UUID.randomUUID().toString(),
                                                soundId = soundId,
                                                name = sound.name,
                                                music = c,
                                                bass = bass,
                                                treble = treble,
                                            )
                                        else l
                                    }
                            )
                        }
                    else
                        r.mix {
                            it.copy(
                                layers =
                                    it.layers +
                                        Library.layer(soundId)
                                            .copy(music = c, bass = bass, treble = treble)
                            )
                        }
                    onSaved(
                        if (existing != null) "Composition updated"
                        else "Composition added to your mixer"
                    )
                    onClearEdit()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (existing != null) "Update mixer layer" else "Add composition to mixer")
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    r.savePreset(
                        Mix(
                            name = "Studio · ${sound.name}",
                            layers =
                                listOf(
                                    Library.layer(soundId)
                                        .copy(music = c, bass = bass, treble = treble)
                                ),
                        )
                    )
                    onSaved("Composition saved as a preset")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save composition as a preset")
            }
        }
        item {
            Text(
                "Adjust midrange, filters and independent fade timing in the layer’s Effects controls on Home. Save the whole mix as a preset at any time.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val keys = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private val textures =
    listOf("Soft pad", "Felt piano", "Warm analog", "Bells & bowls", "Soft strings")

@Composable
private fun Choice(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedButton(
                onClick = { open = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(value, Modifier.weight(1f), fontSize = 13.sp)
                Icon(Icons.Outlined.ExpandMore, null)
            }
            DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 380.dp)) {
                choices.forEach { (id, n) ->
                    DropdownMenuItem(
                        text = { Text(n) },
                        onClick = {
                            onChange(id)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp))
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
fun SettingsScreen(
    saved: SavedState,
    status: PlaybackStatus,
    r: NightfallRuntime,
    onExport: () -> Unit,
    onCredits: () -> Unit,
) {
    val s = saved.settings
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageHeading("Make yourself at home", "Quiet by design. Private by default.") }
        item { SectionLabel("Appearance") }
        item {
            SettingToggle("Light theme", "Midnight navy is the default.", s.light) { v ->
                r.change { it.copy(settings = it.settings.copy(light = v)) }
            }
        }
        item {
            SettingToggle("Reduced motion", "Keep the waveform still.", s.reducedMotion) { v ->
                r.change { it.copy(settings = it.settings.copy(reducedMotion = v)) }
            }
        }
        item { SectionLabel("Sound behavior") }
        item {
            SettingToggle(
                "Thunder ducking",
                "Gently lower music during thunder events.",
                s.ducking,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(ducking = v)) }
            }
        }
        item {
            SettingToggle(
                "Dynamic weather",
                "Let weather intensity evolve slowly.",
                s.dynamicWeather,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(dynamicWeather = v)) }
            }
        }
        item {
            SettingToggle(
                "Slow variation",
                "Subtle texture changes over long sessions.",
                s.slowVariation,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(slowVariation = v)) }
            }
        }
        item {
            SettingToggle(
                "Low-distraction mode",
                "Soften thunder and other sudden events.",
                s.lowDistraction,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(lowDistraction = v)) }
            }
        }
        item {
            SettingToggle(
                "Pause on disconnect",
                "Pause when Bluetooth or wired audio disconnects. Turn off to allow the system to reroute audio.",
                s.pauseOnDisconnect,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(pauseOnDisconnect = v)) }
            }
        }
        item {
            LevelSlider(
                "Sleep timer fade",
                s.fadeSeconds,
                { v -> r.change { it.copy(settings = it.settings.copy(fadeSeconds = v)) } },
                5f..120f,
                "${s.fadeSeconds.roundToInt()} seconds",
            )
        }
        item { SectionLabel("On this device") }
        item {
            Text(
                "Output: ${status.output}\nAudio render load: ${(status.load*100).roundToInt()}% of buffer time\nAudioTrack underruns: ${status.underruns}\n48 kHz · stereo · floating-point PCM",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
        item {
            Text(
                "There is no software layer limit. If rendering approaches 65% of the buffer budget or underruns accumulate, Nightfall warns you. Music layers use more processing than noise. Actual battery life and all-night playback depend on Android and device power policies.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SectionLabel("Your data & audio") }
        item {
            Text(
                "Everything runs on your device. No account, advertising, analytics, tracking, purchases or network permission. Mixes and settings are stored locally. Keep listening levels comfortable; percentages describe layer gain, not acoustic loudness.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("Export current mix as JSON")
            }
        }
        item {
            OutlinedButton(onClick = onCredits, modifier = Modifier.fillMaxWidth()) {
                Text("Audio credits & open-source licenses")
            }
        }
        item {
            Text(
                "Nightfall 1.0\nFree forever. A small space for a quieter night.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 20.dp),
                lineHeight = 22.sp,
            )
        }
    }
}
