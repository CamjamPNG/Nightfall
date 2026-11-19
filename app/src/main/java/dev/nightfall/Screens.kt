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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.nightfall.core.*
import dev.nightfall.diagnostics.DiagnosticsSurface
import dev.nightfall.update.UpdateSettingsSection
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
        Text(
            title,
            Modifier.semantics { heading() },
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-.6).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How many layers in the current mix use this sound, subscribed per row instead of per screen. */
@Composable
private fun LayerCount(r: NightfallRuntime, soundId: String) {
    val n by
        selectSaved(r, r.savedNow().current.layers.count { it.soundId == soundId }) { s ->
            s.current.layers.count { it.soundId == soundId }
        }
    if (n > 0)
        Text(
            stringResource(R.string.library_in_mix, n),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.primary,
        )
}

/** One catalogue section: its name and the entries the current filters leave in it. */
private data class LibrarySection(val name: String, val sounds: List<Sound>)

@Composable
fun SoundLibrary(r: NightfallRuntime) {
    val context = LocalContext.current
    // null is "every category": the chip said "All" and was also the filter sentinel, which is an
    // English word deciding behaviour. The label is a label, the value is a value.
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // Which sections are closed is part of what someone was doing here, so it is saved beside the
    // filters and comes back with them.
    var closed by
        rememberSaveable(stateSaver = listSaver<Set<String>, String>({ it.toList() }, { it.toSet() })) {
            mutableStateOf(emptySet<String>())
        }
    // The catalogue is fixed and its order is editorial — a drizzle before a downpour, a breeze
    // before a gale — so the register of categories is read once and the grouping is built when a
    // filter changes, not on every frame.
    val categories = remember { Library.all.map { it.category }.distinct() }
    val sections =
        remember(query, category) {
            Library.all
                .filter { category == null || it.category == category }
                .filter { it.name.contains(query, true) || it.category.contains(query, true) }
                .groupBy { it.category }
                .map { (name, sounds) -> LibrarySection(name, sounds) }
        }
    val list = rememberLazyListState()
    // A changed filter is a different list and starts at its own top: from four screens down, the
    // filter used to land on the short list's last row rather than on its first. A tab that is
    // restored with the same filter is the same list, so it keeps the row it was left on — which is
    // why this compares against the filter last applied instead of always scrolling.
    var appliedCategory by rememberSaveable { mutableStateOf(category) }
    var appliedQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(category, query) {
        if (category != appliedCategory || query != appliedQuery) {
            appliedCategory = category
            appliedQuery = query
            list.scrollToItem(0)
        }
    }
    Column(Modifier.fillMaxSize()) {
        // The filters sit above the list instead of being its first items. A search field that
        // scrolls away is what turned finding one sound into a scroll through ninety-eight rows.
        Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 22.dp)
                .padding(top = 6.dp, bottom = 10.dp)
        ) {
            OutlinedTextField(
                query,
                { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        contentDescription = context.getString(R.string.library_a11y_search)
                    },
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf<String?>(null) + categories) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c ?: stringResource(R.string.library_category_all)) },
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 22.dp),
        ) {
            item(key = "heading") {
                PageHeading(
                    stringResource(R.string.library_title),
                    stringResource(R.string.library_subtitle),
                )
            }
            if (sections.isEmpty())
                item(key = "empty") {
                    Column {
                        Text(
                            stringResource(R.string.library_no_match),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                query = ""
                                category = null
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            Text(stringResource(R.string.library_no_match_action))
                        }
                    }
                }
            sections.forEach { section ->
                val open = section.name !in closed
                stickyHeader(key = "section-${section.name}", contentType = "section") {
                    LibrarySectionHeader(
                        section.name,
                        section.sounds.count { it.available },
                        open,
                    ) {
                        closed =
                            if (open) closed + section.name else closed - section.name
                    }
                }
                if (open)
                    items(section.sounds, key = { it.id }, contentType = { "sound" }) { sound ->
                        LibraryRow(sound, r)
                    }
            }
            item(key = "disclaimer") {
                Text(
                    stringResource(R.string.library_disclaimer),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * A category's outline row: the name, how many of its entries are available, and whether it is
 * closed. It sticks to the top of the list so the section a row belongs to is never off screen.
 */
@Composable
private fun LibrarySectionHeader(name: String, available: Int, open: Boolean, onToggle: () -> Unit) {
    val state =
        stringResource(
            if (open) R.string.library_section_expanded else R.string.library_section_collapsed
        )
    Row(
        Modifier.fillMaxWidth()
            // Opaque on purpose: rows scroll underneath a sticky header.
            .background(MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                heading()
                stateDescription = state
            }
            .padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.library_available, available, available),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryRow(sound: Sound, r: NightfallRuntime) {
    Card(
        modifier = Modifier.padding(bottom = 10.dp),
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
                        "recording" -> stringResource(R.string.library_kind_recording)
                        "unavailable" -> stringResource(R.string.library_unavailable)
                        "music" -> stringResource(R.string.library_kind_music)
                        else -> sound.description
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LayerCount(r, sound.id)
            }
            IconButton(onClick = { r.add(sound) }, enabled = sound.available) {
                Icon(
                    if (sound.available) Icons.Outlined.AddCircleOutline
                    else Icons.Outlined.Info,
                    stringResource(R.string.library_add, sound.name),
                )
            }
        }
    }
}

@Composable
fun PresetScreen(
    r: NightfallRuntime,
    onImport: () -> Unit,
    onExport: (Mix) -> Unit,
    onRename: (Mix) -> Unit,
) {
    val context = LocalContext.current
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val presets by selectSaved(r, r.savedNow().presets) { it.presets }
    val favoriteIds by selectSaved(r, r.savedNow().favoriteIds) { it.favoriteIds }
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeading(
                stringResource(R.string.presets_title),
                stringResource(R.string.presets_subtitle),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport) {
                    Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.presets_action_import))
                }
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text(stringResource(R.string.presets_action_favorites)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp))
                    },
                )
            }
        }
        if (presets.isNotEmpty())
            item {
                SectionLabel(
                    stringResource(R.string.presets_section_yours),
                    pluralStringResource(R.plurals.presets_saved, presets.size, presets.size),
                )
            }
        items(
            presets.filter { !favoritesOnly || it.favorite || it.id in favoriteIds },
            key = { it.id },
        ) { p ->
            PresetCard(p, r, false, onExport, onRename)
        }
        item {
            SectionLabel(
                stringResource(R.string.presets_section_builtin),
                stringResource(R.string.presets_soundscapes, Presets.all.size),
            )
        }
        items(
            Presets.all.filter { !favoritesOnly || it.id in favoriteIds },
            key = { "builtin-${it.id}" },
        ) { p ->
            PresetCard(p.copy(favorite = p.id in favoriteIds), r, true, onExport, onRename)
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
    val context = LocalContext.current
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
                        pluralStringResource(R.plurals.unit_layers, p.layers.size, p.layers.size),
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
                        stringResource(R.string.presets_a11y_favorite, p.name),
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            stringResource(R.string.presets_a11y_options, p.name),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.presets_action_duplicate)) },
                            onClick = {
                                r.savePreset(
                                    p.copy(name = context.getString(R.string.presets_copy_suffix, p.name))
                                )
                                menu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.presets_action_export)) },
                            onClick = {
                                onExport(p)
                                menu = false
                            },
                        )
                        if (!builtin) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.presets_action_rename)) },
                                onClick = {
                                    onRename(p)
                                    menu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.presets_action_delete)) },
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
                Text(stringResource(R.string.presets_play))
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
    val context = LocalContext.current
    // One read, not a subscription: the studio form is local state and must not recompose with
    // every mixer change.
    val existing = remember(layerId) { r.savedNow().current.layers.firstOrNull { it.id == layerId } }
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
                stringResource(R.string.studio_title),
                if (existing != null) stringResource(R.string.studio_subtitle_editing, existing.name)
                else stringResource(R.string.studio_subtitle_new),
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
                            stringResource(R.string.studio_music_intro),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.studio_music_body),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Choice(
                stringResource(R.string.studio_music_type),
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
        item { SectionLabel(stringResource(R.string.studio_section_foundation)) }
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
                        label = { Text(stringResource(R.string.studio_tuning, hz.toInt())) },
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.studio_tuning_note),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    Choice(
                        stringResource(R.string.studio_key),
                        keys[c.key],
                        keys.mapIndexed { i, k -> i.toString() to k },
                    ) { k ->
                        update { it.copy(key = k.toInt()) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    Choice(
                        stringResource(R.string.studio_scale),
                        if (c.minor) stringResource(R.string.studio_scale_minor)
                        else stringResource(R.string.studio_scale_major),
                        listOf("major" to "Major", "minor" to "Minor"),
                    ) { k ->
                        update { it.copy(minor = k == "minor") }
                    }
                }
            }
        }
        item {
            LevelSlider(
                stringResource(R.string.studio_tempo),
                c.tempo,
                { v -> update { it.copy(tempo = v) } },
                20f..90f,
                stringResource(R.string.studio_tempo_value, c.tempo.roundToInt()),
            )
        }
        item {
            LevelSlider(
                stringResource(R.string.studio_note_density),
                c.density,
                { v -> update { it.copy(density = v) } },
            )
        }
        item {
            LevelSlider(
                stringResource(R.string.studio_harmonic_complexity),
                c.complexity,
                { v -> update { it.copy(complexity = v) } },
            )
        }
        item {
            val textures = stringArrayResource(R.array.studio_instrument_textures).toList()
            Choice(
                stringResource(R.string.studio_instrument_texture),
                textures.getOrElse(c.texture) { textures.first() },
                textures.mapIndexed { i, t -> i.toString() to t },
            ) { v ->
                update { it.copy(texture = v.toInt()) }
            }
        }
        item { SectionLabel(stringResource(R.string.studio_section_space)) }
        item {
            LevelSlider(
                stringResource(R.string.studio_bass),
                bass,
                { bass = it },
                -12f..12f,
                stringResource(R.string.unit_decibels, bass.roundToInt()),
            )
        }
        item {
            LevelSlider(
                stringResource(R.string.studio_treble),
                treble,
                { treble = it },
                -12f..12f,
                stringResource(R.string.unit_decibels, treble.roundToInt()),
            )
        }
        item {
            LevelSlider(
                stringResource(R.string.studio_reverb),
                c.reverb,
                { v -> update { it.copy(reverb = v) } },
            )
        }
        item {
            LevelSlider(
                stringResource(R.string.studio_stereo_delay),
                c.delay,
                { v -> update { it.copy(delay = v) } },
            )
        }
        item {
            LevelSlider(
                stringResource(R.string.studio_randomization),
                c.randomness,
                { v -> update { it.copy(randomness = v) } },
            )
        }
        item {
            SettingToggle(
                stringResource(R.string.studio_melody),
                stringResource(R.string.studio_melody_note),
                c.melody,
            ) { v ->
                update { it.copy(melody = v) }
            }
        }
        if (soundId in listOf("lo-fi-sleep-music", "gentle-lo-fi"))
            item {
                SettingToggle(
                    stringResource(R.string.studio_percussion),
                    stringResource(R.string.studio_percussion_note),
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
                label = { Text(stringResource(R.string.studio_seed)) },
                supportingText = { Text(stringResource(R.string.studio_seed_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TextButton(onClick = { update { it.copy(seed = System.nanoTime()) } }) {
                Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.studio_new_variation))
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
                        if (existing != null) context.getString(R.string.studio_saved_updated)
                        else context.getString(R.string.studio_saved_added)
                    )
                    onClearEdit()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (existing != null) stringResource(R.string.studio_action_update_layer)
                    else stringResource(R.string.studio_action_add_layer)
                )
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    r.savePreset(
                        Mix(
                            name = context.getString(R.string.studio_layer_name, sound.name),
                            layers =
                                listOf(
                                    Library.layer(soundId)
                                        .copy(music = c, bass = bass, treble = treble)
                                ),
                        )
                    )
                    onSaved(context.getString(R.string.studio_saved_as_preset))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.studio_action_save_preset))
            }
        }
        item {
            Text(
                stringResource(R.string.studio_note),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val keys = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
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
    r: NightfallRuntime,
    onExport: () -> Unit,
    onCredits: () -> Unit,
) {
    val s by selectSaved(r, r.savedNow().settings) { it.settings }
    val output by selectStatus(r, OutputRoute()) { it.output }
    val load by selectStatus(r, 0) { (it.load * 100).roundToInt() }
    val underruns by selectStatus(r, 0) { it.underruns }
    val context = LocalContext.current
    // The installed package, not a build constant: a literal here drifted two releases behind.
    val version by
        produceState("") { value = withContext(Dispatchers.IO) { installedVersion(context) } }
    val appTitle =
        if (version.isEmpty()) stringResource(R.string.settings_app_title)
        else stringResource(R.string.settings_app_title_version, version)
    LazyColumn(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeading(
                stringResource(R.string.settings_title),
                stringResource(R.string.settings_subtitle),
            )
        }
        item { SectionLabel(stringResource(R.string.settings_section_appearance)) }
        item {
            SettingToggle(
                stringResource(R.string.settings_light_theme),
                stringResource(R.string.settings_light_theme_note),
                s.light,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(light = v)) }
            }
        }
        item {
            SettingToggle(
                stringResource(R.string.settings_reduced_motion),
                stringResource(R.string.settings_reduced_motion_note),
                s.reducedMotion,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(reducedMotion = v)) }
            }
        }
        item { SectionLabel(stringResource(R.string.settings_section_sound)) }
        item {
            SettingToggle(
                stringResource(R.string.settings_thunder_ducking),
                stringResource(R.string.settings_thunder_ducking_note),
                s.ducking,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(ducking = v)) }
            }
        }
        item {
            SettingToggle(
                stringResource(R.string.settings_dynamic_weather),
                stringResource(R.string.settings_dynamic_weather_note),
                s.dynamicWeather,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(dynamicWeather = v)) }
            }
        }
        item {
            SettingToggle(
                stringResource(R.string.settings_slow_variation),
                stringResource(R.string.settings_slow_variation_note),
                s.slowVariation,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(slowVariation = v)) }
            }
        }
        item {
            SettingToggle(
                stringResource(R.string.settings_low_distraction),
                stringResource(R.string.settings_low_distraction_note),
                s.lowDistraction,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(lowDistraction = v)) }
            }
        }
        item {
            SettingToggle(
                stringResource(R.string.settings_pause_on_disconnect),
                stringResource(R.string.settings_pause_on_disconnect_note),
                s.pauseOnDisconnect,
            ) { v ->
                r.change { it.copy(settings = it.settings.copy(pauseOnDisconnect = v)) }
            }
        }
        item {
            LevelSlider(
                stringResource(R.string.settings_timer_fade),
                s.fadeSeconds,
                { v -> r.change { it.copy(settings = it.settings.copy(fadeSeconds = v)) } },
                5f..120f,
                pluralStringResource(
                    R.plurals.unit_seconds,
                    s.fadeSeconds.roundToInt(),
                    s.fadeSeconds.roundToInt(),
                ),
            )
        }
        item { SectionLabel(stringResource(R.string.settings_section_device)) }
        item {
            Text(
                stringResource(
                    R.string.settings_device_readout,
                    outputLabel(context, output),
                    load,
                    underruns,
                    stringResource(R.string.settings_pcm_spec),
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
        item {
            Text(
                stringResource(R.string.settings_capacity_note),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SectionLabel(stringResource(R.string.settings_section_updates)) }
        item { UpdateSettingsSection() }
        item { SectionLabel(stringResource(R.string.settings_section_data)) }
        item {
            Text(
                stringResource(R.string.settings_privacy_note),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_action_export))
            }
        }
        item {
            OutlinedButton(onClick = onCredits, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_action_credits))
            }
        }
        item {
            val about = stringResource(R.string.settings_about, appTitle)
            Column {
                // The version row. It still reads as the app's name and version; five taps inside
                // three seconds reveal the diagnostics section below it, and nothing hints at that.
                Text(
                    about,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = DiagnosticsSurface.VersionTap(Modifier.padding(vertical = 20.dp)),
                    lineHeight = 22.sp,
                )
                DiagnosticsSurface.Section()
            }
        }
    }
}
