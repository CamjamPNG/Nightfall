// SPDX-License-Identifier: MIT
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.nightfall

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nightfall.core.*
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Subscribes to one value derived from the saved state instead of the whole [SavedState], so a
 * change elsewhere (a layer volume, a status tick) does not recompose the caller.
 *
 * [initial] should be the same value read from the current runtime state, which the caller reads
 * without subscribing: the flow then emits only when the derived value actually changes.
 */
/**
 * One synchronous read of the current snapshot, used only to seed a value that the same composable
 * also subscribes to; the subscription supplies every later value, so the seed just avoids a blank
 * first frame. Lint forbids a raw StateFlow read in a composable body, so the read lives here with
 * the reason attached.
 */
internal fun NightfallRuntime.savedNow(): SavedState = saved.value

internal fun NightfallRuntime.statusNow(): PlaybackStatus = status.value

@Composable
internal fun <T> selectSaved(
    r: NightfallRuntime,
    initial: T,
    selector: (SavedState) -> T,
): State<T> =
    remember(r) { r.saved.map(selector).distinctUntilChanged() }
        .collectAsStateWithLifecycle(initial)

@Composable
internal fun <T> selectStatus(
    r: NightfallRuntime,
    initial: T,
    selector: (PlaybackStatus) -> T,
): State<T> =
    remember(r) { r.status.map(selector).distinctUntilChanged() }
        .collectAsStateWithLifecycle(initial)

private fun SavedState.allMuted(): Boolean = current.layers.let { l -> l.isNotEmpty() && l.all { it.mute } }

private fun SavedState.activeLayers(): Int {
    val layers = current.layers
    val anySolo = layers.any { it.solo && it.enabled && !it.mute }
    return layers.count { it.enabled && !it.mute && (!anySolo || it.solo) }
}

@Composable
fun NightfallScreen(vm: NightfallViewModel) {
    val runtime = vm.runtime
    val light by selectSaved(runtime, runtime.savedNow().settings.light) { it.settings.light }
    val ready by runtime.ready.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val screenState = rememberSaveableStateHolder()
    BackHandler(enabled = tab != 0) { tab = 0 }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    // The encoded snapshot, so recreation while the document picker is open still writes the mix the
    // user asked to export. A missing snapshot is reported rather than replaced with an empty mix.
    var exportJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editingPreset by remember { mutableStateOf<Mix?>(null) }
    var studioLayer by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        val json = exportJson ?: error("The mix to export is no longer available")
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use {
                                it.write(json.toByteArray())
                            } ?: error("Cannot open export file")
                        }
                        snackbar.showSnackbar("Mix exported")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Export failed: ${e.message}")
                    }
                }
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        val mix =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    PresetJson.decodeStream(input)
                                } ?: error("Cannot open preset")
                            }
                        runtime.savePreset(mix)
                        snackbar.showSnackbar("Imported ${mix.name}")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Import failed: ${e.message}")
                    }
                }
        }
    // Consume playback errors without making this level read status, which updates twice a second.
    LaunchedEffect(runtime) {
        runtime.status
            .map { it.error }
            .distinctUntilChanged()
            .collect { error ->
                if (error != null) {
                    snackbar.showSnackbar(error)
                    // A newer failure may have replaced this one while the snackbar was up.
                    if (runtime.status.value.error == error)
                        runtime.status.value = runtime.status.value.copy(error = null)
                }
            }
    }
    NightfallTheme(light) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier =
                        Modifier.border(
                            androidx.compose.foundation.BorderStroke(
                                .5.dp,
                                MaterialTheme.colorScheme.outline,
                            ),
                            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        ),
                ) {
                    val tabs =
                        listOf(
                            "Home" to Icons.Outlined.Home,
                            "Library" to Icons.Outlined.LibraryMusic,
                            "Presets" to Icons.Outlined.Bookmarks,
                            "Studio" to Icons.Outlined.Tune,
                            "Settings" to Icons.Outlined.Settings,
                        )
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icon, null, Modifier.size(22.dp)) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    indicatorColor =
                                        MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                ),
                        )
                    }
                }
            },
        ) { padding ->
            if (!ready)
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            else
                Box(
                    Modifier.padding(padding).fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                        screenState.SaveableStateProvider(tab) {
                            when (tab) {
                                0 ->
                                    Home(
                                        runtime,
                                        onSettings = { tab = 4 },
                                        onAdd = { tab = 1 },
                                        onPresets = { tab = 2 },
                                        onTimer = { dialog = "timer" },
                                        onSave = {
                                            name = runtime.savedNow().current.name
                                            editingPreset = null
                                            dialog = "save"
                                        },
                                        onFade = { dialog = "fade" },
                                        onStudio = {
                                            studioLayer = it
                                            tab = 3
                                        },
                                    )
                                1 -> SoundLibrary(runtime)
                                2 ->
                                    PresetScreen(
                                        runtime,
                                        onImport = {
                                            importer.launch(
                                                arrayOf(
                                                    "application/json",
                                                    "text/plain",
                                                    "application/octet-stream",
                                                )
                                            )
                                        },
                                        onExport = {
                                            exportJson = PresetJson.json.encodeToString(it)
                                            export.launch("${Library.slug(it.name)}.json")
                                        },
                                        onRename = {
                                            editingPreset = it
                                            name = it.name
                                            dialog = "save"
                                        },
                                    )
                                3 ->
                                    Studio(
                                        runtime,
                                        studioLayer,
                                        onSaved = { message ->
                                            scope.launch { snackbar.showSnackbar(message) }
                                        },
                                        onClearEdit = { studioLayer = null },
                                    )
                                4 ->
                                    SettingsScreen(
                                        runtime,
                                        onExport = {
                                            exportJson =
                                                PresetJson.json.encodeToString(
                                                    runtime.savedNow().current
                                                )
                                            export.launch("nightfall-mix.json")
                                        },
                                        onCredits = { dialog = "credits" },
                                    )
                            }
                        }
                    }
                }
        }
        when (dialog) {
            "save" ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = {
                        Text(if (editingPreset == null) "Save your mix" else "Rename preset")
                    },
                    text = {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(200) },
                            label = { Text("Mix name") },
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (editingPreset == null) runtime.saveMix(name)
                                else
                                    runtime.updatePreset(editingPreset!!.id) {
                                        it.copy(name = name.trim().ifEmpty { it.name })
                                    }
                                dialog = null
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
                )
            "timer" -> TimerDialog(runtime, onDismiss = { dialog = null })
            "fade" ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("Gentle transitions") },
                    text = {
                        Column {
                            Text(
                                "New layers and preset changes fade in and out using each layer’s fade setting.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(20.dp))
                            val fadeSeconds by
                                selectSaved(runtime, runtime.savedNow().settings.fadeSeconds) {
                                    it.settings.fadeSeconds
                                }
                            LevelSlider(
                                "Timer fade duration",
                                fadeSeconds,
                                { v ->
                                    runtime.change {
                                        it.copy(settings = it.settings.copy(fadeSeconds = v))
                                    }
                                },
                                5f..120f,
                                "${fadeSeconds.roundToInt()} ${plural(fadeSeconds.roundToInt(), "second", "seconds")}",
                            )
                        }
                    },
                    confirmButton = {
                        val playing by
                            selectStatus(runtime, runtime.statusNow().playing) { it.playing }
                        TextButton(
                            enabled = playing,
                            onClick = {
                                runtime.timer.start(
                                    android.os.SystemClock.elapsedRealtime(),
                                    1,
                                    (runtime.savedNow().settings.fadeSeconds * 1000).toLong(),
                                )
                                dialog = null
                            },
                        ) {
                            Text("Fade out now")
                        }
                    },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
                )
            "credits" ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("Audio credits & licenses") },
                    text = {
                        Column(
                            Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                        ) {
                            val licenses by
                                produceState("") {
                                    value =
                                        withContext(Dispatchers.IO) {
                                            context.assets
                                                .open("AUDIO_LICENSES.md")
                                                .bufferedReader()
                                                .use { it.readText() }
                                        }
                                }
                            Text(licenses, fontSize = 12.sp)
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
                )
        }
    }
}

@Composable
private fun Home(
    r: NightfallRuntime,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onPresets: () -> Unit,
    onTimer: () -> Unit,
    onSave: () -> Unit,
    onFade: () -> Unit,
    onStudio: (String) -> Unit,
) {
    // The mixer asks for layer identity, not layer content: gaining one layer leaves this list
    // unchanged, so the list itself never recomposes while a slider is dragged.
    val layerIds by
        selectSaved(r, r.savedNow().current.layers.map { it.id }) { state ->
            state.current.layers.map { it.id }
        }
    val mixName by selectSaved(r, r.savedNow().current.name) { it.current.name }
    val reducedMotion by
        selectSaved(r, r.savedNow().settings.reducedMotion) { it.settings.reducedMotion }
    val overloaded by
        selectStatus(r, false) { it.load > .65f || it.underruns > 3 }
    LazyColumn(
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.DarkMode,
                    null,
                    Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nightfall",
                        Modifier.semantics { heading() },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-.6).sp,
                    )
                    Text(
                        mixName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPresets) {
                    Icon(
                        Icons.Outlined.Bookmarks,
                        "Presets",
                        Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        "Open settings",
                        Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { NowPlayingCard(r, mixName, reducedMotion, onTimer) }
        item {
            Column(Modifier.padding(top = 6.dp)) {
                val master by
                    selectSaved(r, r.savedNow().current.master) { it.current.master }
                LevelSlider("Master volume", master, { v -> r.mix { it.copy(master = v) } })
                QuickActions(r, onFade, onSave)
            }
        }
        item { PresetStrip(r, onPresets) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Your soundscape",
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${layerIds.size} ${plural(layerIds.size, "layer", "layers")} · hold the grip to reorder",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add sound")
                }
            }
        }
        if (overloaded) item { CapacityWarning(r) }
        items(layerIds, key = { it }, contentType = { "layer" }) { id ->
            LayerRow(r, id, onStudio)
        }
        item {
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add another sound")
            }
        }
        item {
            Text(
                "OFFLINE BY NATURE  ·  FREE FOREVER",
                Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NowPlayingCard(
    r: NightfallRuntime,
    mixName: String,
    reducedMotion: Boolean,
    onTimer: () -> Unit,
) {
    val playing by selectStatus(r, false) { it.playing }
    val output by selectStatus(r, OutputRoute()) { it.output }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (playing) "NOW PLAYING" else "READY WHEN YOU ARE",
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(mixName, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                }
                FilledIconButton(
                    onClick = { if (playing) r.pause() else r.play() },
                    modifier = Modifier.size(66.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (playing) "Pause soundscape" else "Play soundscape",
                        Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Waveform(playing, reducedMotion)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                ActiveLayerLabel(r)
                Spacer(Modifier.width(12.dp))
                Icon(
                    if (output.kind == OutputRoute.Kind.BLUETOOTH) Icons.Outlined.BluetoothAudio
                    else Icons.Outlined.Headphones,
                    null,
                    Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    outputShortLabel(output),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TimerButton(r, onTimer)
            }
        }
    }
}

@Composable
private fun ActiveLayerLabel(r: NightfallRuntime) {
    val active by selectSaved(r, r.savedNow().activeLayers()) { it.activeLayers() }
    Text(
        "$active ${plural(active, "layer", "layers")}",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimerButton(r: NightfallRuntime, onTimer: () -> Unit) {
    val label by
        selectStatus(r, "All night") { s ->
            if (s.timerActive) if (s.timerFading) "Fading" else elapsedLabel(s.remainingMs)
            else "All night"
        }
    TextButton(
        onClick = onTimer,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier =
            Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = "Sleep timer"
                stateDescription = label
            },
    ) {
        Icon(Icons.Outlined.Bedtime, null, Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun QuickActions(r: NightfallRuntime, onFade: () -> Unit, onSave: () -> Unit) {
    val allMuted by selectSaved(r, r.savedNow().allMuted()) { it.allMuted() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TinyAction(
            if (allMuted) "Unmute" else "Mute all",
            Icons.AutoMirrored.Outlined.VolumeOff,
        ) {
            val muted = !r.savedNow().current.layers.all { it.mute }
            r.mix { it.copy(layers = it.layers.map { l -> l.copy(mute = muted) }) }
        }
        TinyAction("Stop all", Icons.Outlined.Stop) { r.stop() }
        TinyAction("Fade", Icons.Outlined.BlurOn, onFade)
        TinyAction("Save mix", Icons.Outlined.BookmarkAdd, onSave)
        TinyAction("Reset", Icons.Outlined.RestartAlt) { r.mix { Presets.all.last() } }
    }
}

@Composable
private fun PresetStrip(r: NightfallRuntime, onPresets: () -> Unit) {
    val userPresets by selectSaved(r, r.savedNow().presets) { it.presets }
    val shown = if (userPresets.isEmpty()) Presets.all else userPresets
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(shown.take(8), key = { it.id }) { p ->
            SuggestionChip(
                onClick = { r.usePreset(p) },
                label = { Text(p.name, fontSize = 12.sp, maxLines = 1) },
            )
        }
        item {
            SuggestionChip(
                onClick = onPresets,
                label = { Text("All presets →", fontSize = 12.sp) },
                modifier = Modifier.semantics { contentDescription = "See all presets" },
            )
        }
    }
}

@Composable
private fun CapacityWarning(r: NightfallRuntime) {
    val stats by selectStatus(r, 0 to 0) { (it.load * 100).roundToInt() to it.underruns }
    Text(
        "This mix is approaching the device’s audio capacity. Reduce music effects or layers if you hear gaps. Render load ${stats.first}% · ${stats.second} ${plural(stats.second, "underrun", "underruns")}",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
    )
}

@Composable
private fun TinyAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 48.dp)
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One layer. The row subscribes to its own layer only, so dragging its volume recomposes this row
 * and nothing else: siblings, the play card and the screen root are untouched.
 */
@Composable
private fun LayerRow(r: NightfallRuntime, id: String, onStudio: (String) -> Unit) {
    val live =
        remember(id) {
            r.saved.map { s -> s.current.layers.firstOrNull { it.id == id } }.distinctUntilChanged()
        }
    val layer by
        live.collectAsStateWithLifecycle(
            remember(id) { r.savedNow().current.layers.firstOrNull { it.id == id } }
        )
    val current = layer ?: return
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val sound = Library.byId[current.soundId]
    val adjusted = adjustedOptions(current)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp)
                        .pointerInput(current.id) {
                            var y = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { y = 0f },
                                onDrag = { change, drag ->
                                    change.consume()
                                    y += drag.y
                                    if (abs(y) > 70.dp.toPx()) {
                                        r.reorder(current.id, if (y > 0) 1 else -1)
                                        y = 0f
                                    }
                                },
                            )
                        }
                        .semantics {
                            contentDescription = "Reorder ${current.name}"
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Move up") {
                                        r.reorder(current.id, -1)
                                        true
                                    },
                                    CustomAccessibilityAction("Move down") {
                                        r.reorder(current.id, 1)
                                        true
                                    },
                                )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        null,
                        Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                    )
                }
                Icon(
                    soundIcon(sound),
                    null,
                    Modifier.size(23.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(
                        current.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        layerSubtitle(sound),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Switch(
                    checked = current.enabled,
                    onCheckedChange = { v -> r.layer(current.id) { it.copy(enabled = v) } },
                    modifier = Modifier.semantics { contentDescription = "Enable ${current.name}" },
                )
            }
            LevelSlider(
                "Volume",
                current.gain,
                { v -> r.layer(current.id) { it.copy(gain = v) } },
                spokenName = "${current.name} volume",
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = current.mute,
                    onClick = { r.layer(current.id) { it.copy(mute = !it.mute) } },
                    label = { Text("Mute", fontSize = 11.sp) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Mute ${current.name}"
                        },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = current.solo,
                    onClick = { r.layer(current.id) { it.copy(solo = !it.solo) } },
                    label = { Text("Solo", fontSize = 11.sp) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Solo ${current.name}"
                        },
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        r.mix { it.copy(layers = it.layers.filterNot { l -> l.id == current.id }) }
                    }
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        "Remove ${current.name}",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = { expanded = !expanded },
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = "Options for ${current.name}"
                        stateDescription =
                            if (expanded) "Expanded"
                            else if (adjusted.isEmpty()) "Collapsed, all defaults"
                            else "Collapsed, ${adjusted.size} adjusted"
                    },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (adjusted.isEmpty()) "Options" else "Options · ${adjusted.size} adjusted",
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    Modifier.size(18.dp),
                )
            }
            if (!expanded && adjusted.isNotEmpty())
                Text(
                    adjusted.joinToString("  ·  "),
                    Modifier.padding(start = 8.dp, bottom = 8.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(
                    sound?.description ?: "",
                    Modifier.padding(top = 12.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OptionGroup("Placement") {
                    LevelSlider(
                        "Pan",
                        current.pan,
                        { v -> r.layer(current.id) { it.copy(pan = v) } },
                        -1f..1f,
                        if (abs(current.pan) < .05) "Center"
                        else "${(abs(current.pan) * 100).roundToInt()}% ${if (current.pan < 0) "left" else "right"}",
                        "${current.name} pan",
                    )
                    LevelSlider(
                        "Stereo width",
                        current.width,
                        { v -> r.layer(current.id) { it.copy(width = v) } },
                        spokenName = "${current.name} stereo width",
                    )
                }
                OptionGroup("Tone") {
                    LevelSlider(
                        "Bass",
                        current.bass,
                        { v -> r.layer(current.id) { it.copy(bass = v) } },
                        -12f..12f,
                        "${current.bass.roundToInt()} dB",
                        "${current.name} bass",
                    )
                    LevelSlider(
                        "Mid",
                        current.mid,
                        { v -> r.layer(current.id) { it.copy(mid = v) } },
                        -12f..12f,
                        "${current.mid.roundToInt()} dB",
                        "${current.name} mid",
                    )
                    LevelSlider(
                        "Treble",
                        current.treble,
                        { v -> r.layer(current.id) { it.copy(treble = v) } },
                        -12f..12f,
                        "${current.treble.roundToInt()} dB",
                        "${current.name} treble",
                    )
                    LevelSlider(
                        "Brightness",
                        current.brightness,
                        { v -> r.layer(current.id) { it.copy(brightness = v) } },
                        spokenName = "${current.name} brightness",
                    )
                    LevelSlider(
                        "Spectral tilt",
                        current.tilt,
                        { v -> r.layer(current.id) { it.copy(tilt = v) } },
                        -1f..1f,
                        "${"%.1f".format(current.tilt)}",
                        "${current.name} spectral tilt",
                    )
                }
                OptionGroup("Filters") {
                    LevelSlider(
                        "High-pass filter",
                        current.highPass,
                        { v -> r.layer(current.id) { it.copy(highPass = v) } },
                        20f..2000f,
                        "${current.highPass.roundToInt()} Hz",
                        "${current.name} high-pass filter",
                    )
                    LevelSlider(
                        "Low-pass filter",
                        current.lowPass,
                        { v -> r.layer(current.id) { it.copy(lowPass = v) } },
                        500f..20000f,
                        "${current.lowPass.roundToInt()} Hz",
                        "${current.name} low-pass filter",
                    )
                }
                if (hasTextureOptions(sound))
                    OptionGroup("Texture") {
                        if (sound?.kind == "binaural")
                            LevelSlider(
                                "Beat frequency",
                                current.intensity,
                                { v -> r.layer(current.id) { it.copy(intensity = v) } },
                                display = "${"%.1f".format(1+current.intensity*19)} Hz",
                                spokenName = "${current.name} beat frequency",
                            )
                        else {
                            LevelSlider(
                                "Intensity",
                                current.intensity,
                                { v -> r.layer(current.id) { it.copy(intensity = v) } },
                                spokenName = "${current.name} intensity",
                            )
                            if (sound?.kind in listOf("rain", "thunder", "winter", "fire"))
                                LevelSlider(
                                    "Event frequency",
                                    current.eventRate,
                                    { v -> r.layer(current.id) { it.copy(eventRate = v) } },
                                    spokenName = "${current.name} event frequency",
                                )
                            LevelSlider(
                                "Slow modulation",
                                current.modulation,
                                { v -> r.layer(current.id) { it.copy(modulation = v) } },
                                spokenName = "${current.name} slow modulation",
                            )
                        }
                    }
                OptionGroup("Timing") {
                    LevelSlider(
                        "Fade in and out over",
                        current.fadeSeconds,
                        { v -> r.layer(current.id) { it.copy(fadeSeconds = v) } },
                        .1f..60f,
                        "${"%.1f".format(current.fadeSeconds)} s",
                        "${current.name} fade duration",
                    )
                    LevelSlider(
                        "Stop this layer after",
                        current.stopAfterMinutes,
                        { v -> r.layer(current.id) { it.copy(stopAfterMinutes = v) } },
                        0f..120f,
                        if (current.stopAfterMinutes < .5f) "All night"
                        else "${current.stopAfterMinutes.roundToInt()} min",
                        "${current.name} stop time",
                    )
                    if (sound?.kind == "thunder")
                        LevelSlider(
                            "Storm becomes rain over",
                            current.weatherToRainMinutes,
                            { v -> r.layer(current.id) { it.copy(weatherToRainMinutes = v) } },
                            0f..120f,
                            if (current.weatherToRainMinutes < .5f) "Off"
                            else "${current.weatherToRainMinutes.roundToInt()} min",
                            "${current.name} storm transition",
                        )
                }
                if (sound?.music == true)
                    TextButton(
                        onClick = { onStudio(current.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit composition in Studio")
                    }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun OptionGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        Text(
            title,
            Modifier.padding(top = 12.dp).semantics { heading() },
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

/** Layer fields the audio model honours for every source kind. */
private fun adjustedOptions(l: Layer): List<String> {
    val d = Layer(id = l.id, soundId = l.soundId, name = l.name)
    fun f(v: Float, ref: Float, name: String) = if (abs(v - ref) > 1e-4f) name else null
    return listOfNotNull(
        f(l.pan, d.pan, "pan"),
        f(l.width, d.width, "stereo width"),
        f(l.highPass, d.highPass, "high-pass"),
        f(l.lowPass, d.lowPass, "low-pass"),
        f(l.bass, d.bass, "bass"),
        f(l.mid, d.mid, "mid"),
        f(l.treble, d.treble, "treble"),
        f(l.brightness, d.brightness, "brightness"),
        f(l.tilt, d.tilt, "tilt"),
        f(l.intensity, d.intensity, "intensity"),
        f(l.eventRate, d.eventRate, "events"),
        f(l.modulation, d.modulation, "modulation"),
        f(l.fadeSeconds, d.fadeSeconds, "fade"),
        f(l.stopAfterMinutes, d.stopAfterMinutes, "stop time"),
        f(l.weatherToRainMinutes, d.weatherToRainMinutes, "storm transition"),
    )
}

/** Whether core reads intensity/modulation for this kind, so the panel never shows an inert control. */
private fun hasTextureOptions(sound: Sound?): Boolean =
    when (sound?.kind) {
        "music", "recording", null -> false
        else -> true
    }

private fun layerSubtitle(sound: Sound?): String =
    when (sound?.kind) {
        "recording" -> "Field recording"
        "music" -> "Generative music"
        "binaural" -> "Binaural tones"
        "noise" -> "Noise texture"
        "clock" -> "Ticking clock"
        "hum" -> "Hum"
        null -> ""
        else -> "${sound.kind.replaceFirstChar { it.uppercase() }} texture"
    }

@Composable
private fun TimerDialog(r: NightfallRuntime, onDismiss: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf("30") }
    val timerActive by selectStatus(r, false) { it.timerActive }
    val timerPaused by selectStatus(r, false) { it.timerPaused }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text(
                    "Audio fades gently after the countdown. Pausing playback pauses the timer.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(listOf(5, 10, 15), listOf(30, 45, 60), listOf(90, 120, 0)).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { m ->
                            OutlinedButton(
                                onClick = {
                                    r.setTimer(m.toLong())
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(2.dp),
                            ) {
                                Text(if (m == 0) "All night" else "$m min", fontSize = 11.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                    label = { Text("Custom minutes (1–1440)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (timerActive)
                    TextButton(
                        onClick = {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (r.timer.paused) r.timer.resume(now) else r.timer.pause(now)
                            r.status.value = r.status.value.copy(timerPaused = r.timer.paused)
                        }
                    ) {
                        Text(if (timerPaused) "Resume timer" else "Pause timer")
                    }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    custom
                        .toLongOrNull()
                        ?.takeIf { it in 1..1440 }
                        ?.let {
                            r.setTimer(it)
                            onDismiss()
                        }
                }
            ) {
                Text("Set timer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
