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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NightfallScreen(vm: NightfallViewModel) {
    val runtime = vm.runtime
    val saved by vm.saved.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val ready by runtime.ready.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val screenState = rememberSaveableStateHolder()
    BackHandler(enabled = tab != 0) { tab = 0 }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var exportMix by remember { mutableStateOf(saved.current) }
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
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use {
                                it.write(PresetJson.encode(exportMix).toByteArray())
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
    LaunchedEffect(status.error) {
        status.error?.let {
            snackbar.showSnackbar(it)
            runtime.status.value = runtime.status.value.copy(error = null)
        }
    }
    NightfallTheme(saved.settings.light) {
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
                                        saved,
                                        status,
                                        runtime,
                                        onSettings = { tab = 4 },
                                        onAdd = { tab = 1 },
                                        onTimer = { dialog = "timer" },
                                        onSave = {
                                            name = saved.current.name
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
                                        saved,
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
                                            exportMix = it
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
                                        saved,
                                        status,
                                        runtime,
                                        onExport = {
                                            exportMix = saved.current
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
            "timer" -> TimerDialog(runtime, status, onDismiss = { dialog = null })
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
                            LevelSlider(
                                "Timer fade duration",
                                saved.settings.fadeSeconds,
                                { v ->
                                    runtime.change {
                                        it.copy(settings = it.settings.copy(fadeSeconds = v))
                                    }
                                },
                                5f..120f,
                                "${saved.settings.fadeSeconds.roundToInt()} seconds",
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = status.playing,
                            onClick = {
                                runtime.timer.start(
                                    android.os.SystemClock.elapsedRealtime(),
                                    1,
                                    (saved.settings.fadeSeconds * 1000).toLong(),
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
                            Text(
                                remember {
                                    context.assets.open("AUDIO_LICENSES.md").bufferedReader().use {
                                        it.readText()
                                    }
                                },
                                fontSize = 12.sp,
                            )
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
                )
        }
    }
}

@Composable
private fun Home(
    saved: SavedState,
    status: PlaybackStatus,
    r: NightfallRuntime,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onTimer: () -> Unit,
    onSave: () -> Unit,
    onFade: () -> Unit,
    onStudio: (String) -> Unit,
) {
    val mix = saved.current
    val active =
        mix.layers.count {
            it.enabled &&
                !it.mute &&
                (mix.layers.none { l -> l.solo && l.enabled && !l.mute } || it.solo)
        }
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
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-.6).sp,
                    )
                    Text(
                        mix.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        item {
            Card(
                colors =
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (status.playing) "NOW PLAYING" else "READY WHEN YOU ARE",
                                fontSize = 10.sp,
                                letterSpacing = 1.6.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                mix.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                            )
                        }
                        FilledIconButton(
                            onClick = { if (status.playing) r.pause() else r.play() },
                            modifier = Modifier.size(66.dp),
                            shape = CircleShape,
                        ) {
                            Icon(
                                if (status.playing) Icons.Outlined.Pause
                                else Icons.Outlined.PlayArrow,
                                if (status.playing) "Pause soundscape" else "Play soundscape",
                                Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Waveform(status.playing, saved.settings.reducedMotion)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(5.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$active layers",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            if (status.output.startsWith("Bluetooth")) Icons.Outlined.BluetoothAudio
                            else Icons.Outlined.Headphones,
                            null,
                            Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            status.output.substringBefore(" · "),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onTimer, contentPadding = PaddingValues(4.dp)) {
                            Icon(Icons.Outlined.Bedtime, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (status.timerActive)
                                    if (status.timerFading) "Fading"
                                    else formatTime(status.remainingMs)
                                else "All night",
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 6.dp)) {
                LevelSlider("Master volume", mix.master, { v -> r.mix { it.copy(master = v) } })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TinyAction(
                        if (mix.layers.all { it.mute }) "Unmute" else "Mute all",
                        Icons.AutoMirrored.Outlined.VolumeOff,
                    ) {
                        val muted = !mix.layers.all { it.mute }
                        r.mix { it.copy(layers = it.layers.map { l -> l.copy(mute = muted) }) }
                    }
                    TinyAction("Stop all", Icons.Outlined.Stop) { r.stop() }
                    TinyAction("Fade", Icons.Outlined.BlurOn, onFade)
                    TinyAction("Save mix", Icons.Outlined.BookmarkAdd, onSave)
                    TinyAction("Reset", Icons.Outlined.RestartAlt) { r.mix { Presets.all.last() } }
                }
            }
        }
        item { SectionLabel("Your soundscape", "${mix.layers.size} layers") }
        if (status.load > .65f || status.underruns > 3)
            item {
                Text(
                    "This mix is approaching the device’s audio capacity. Reduce music effects or layers if you hear gaps. Render load ${(status.load*100).roundToInt()}% · ${status.underruns} underruns",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
        items(mix.layers, key = { it.id }) { layer -> LayerCard(layer, r, onStudio) }
        item {
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add sound")
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
private fun TinyAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 48.dp)
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

@Composable
private fun LayerCard(layer: Layer, r: NightfallRuntime, onStudio: (String) -> Unit) {
    var expanded by rememberSaveable(layer.id) { mutableStateOf(false) }
    val sound = Library.byId[layer.soundId]
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    "Hold to reorder ${layer.name}",
                    Modifier.size(48.dp)
                        .pointerInput(layer.id) {
                            var y = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { y = 0f },
                                onDrag = { change, drag ->
                                    change.consume()
                                    y += drag.y
                                    if (abs(y) > 70.dp.toPx()) {
                                        r.reorder(layer.id, if (y > 0) 1 else -1)
                                        y = 0f
                                    }
                                },
                            )
                        }
                        .semantics {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Move up") {
                                        r.reorder(layer.id, -1)
                                        true
                                    },
                                    CustomAccessibilityAction("Move down") {
                                        r.reorder(layer.id, 1)
                                        true
                                    },
                                )
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
                )
                Icon(
                    soundIcon(sound),
                    null,
                    Modifier.size(23.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    layer.name,
                    Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
                Text(
                    "${(layer.gain*100).roundToInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Switch(
                    checked = layer.enabled,
                    onCheckedChange = { v -> r.layer(layer.id) { it.copy(enabled = v) } },
                    modifier = Modifier.semantics { contentDescription = "Enable ${layer.name}" },
                )
            }
            SlimSlider(
                value = layer.gain,
                onValueChange = { v -> r.layer(layer.id) { it.copy(gain = v) } },
                modifier =
                    Modifier.fillMaxWidth().height(48.dp).semantics {
                        contentDescription = "${layer.name} volume"
                    },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = layer.mute,
                    onClick = { r.layer(layer.id) { it.copy(mute = !it.mute) } },
                    label = { Text("Mute", fontSize = 11.sp) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Mute ${layer.name}"
                        },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = layer.solo,
                    onClick = { r.layer(layer.id) { it.copy(solo = !it.solo) } },
                    label = { Text("Solo", fontSize = 11.sp) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Solo ${layer.name}"
                        },
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text("Effects", fontSize = 11.sp)
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        null,
                        Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = {
                        r.mix { it.copy(layers = it.layers.filterNot { l -> l.id == layer.id }) }
                    }
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        "Remove ${layer.name}",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text(
                    sound?.description ?: "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sound?.kind == "binaural")
                    LevelSlider(
                        "Frequency difference",
                        layer.intensity,
                        { v -> r.layer(layer.id) { it.copy(intensity = v) } },
                        display = "${"%.1f".format(1+layer.intensity*19)} Hz",
                    )
                LevelSlider(
                    "Pan",
                    layer.pan,
                    { v -> r.layer(layer.id) { it.copy(pan = v) } },
                    -1f..1f,
                    if (abs(layer.pan) < .05) "Center" else if (layer.pan < 0) "Left" else "Right",
                )
                LevelSlider(
                    "Stereo width",
                    layer.width,
                    { v -> r.layer(layer.id) { it.copy(width = v) } },
                )
                LevelSlider(
                    "High-pass filter",
                    layer.highPass,
                    { v -> r.layer(layer.id) { it.copy(highPass = v) } },
                    20f..2000f,
                    "${layer.highPass.roundToInt()} Hz",
                )
                LevelSlider(
                    "Low-pass filter",
                    layer.lowPass,
                    { v -> r.layer(layer.id) { it.copy(lowPass = v) } },
                    500f..20000f,
                    "${layer.lowPass.roundToInt()} Hz",
                )
                LevelSlider(
                    "Bass",
                    layer.bass,
                    { v -> r.layer(layer.id) { it.copy(bass = v) } },
                    -12f..12f,
                    "${layer.bass.roundToInt()} dB",
                )
                LevelSlider(
                    "Mid",
                    layer.mid,
                    { v -> r.layer(layer.id) { it.copy(mid = v) } },
                    -12f..12f,
                    "${layer.mid.roundToInt()} dB",
                )
                LevelSlider(
                    "Treble",
                    layer.treble,
                    { v -> r.layer(layer.id) { it.copy(treble = v) } },
                    -12f..12f,
                    "${layer.treble.roundToInt()} dB",
                )
                LevelSlider(
                    "Brightness",
                    layer.brightness,
                    { v -> r.layer(layer.id) { it.copy(brightness = v) } },
                )
                LevelSlider(
                    "Spectral tilt",
                    layer.tilt,
                    { v -> r.layer(layer.id) { it.copy(tilt = v) } },
                    -1f..1f,
                    "${"%.1f".format(layer.tilt)}",
                )
                if (
                    sound?.music != true && sound?.kind != "recording" && sound?.kind != "binaural"
                ) {
                    LevelSlider(
                        "Intensity",
                        layer.intensity,
                        { v -> r.layer(layer.id) { it.copy(intensity = v) } },
                    )
                    if (sound?.kind in listOf("rain", "thunder", "winter", "fire")) {
                        LevelSlider(
                            "Event frequency",
                            layer.eventRate,
                            { v -> r.layer(layer.id) { it.copy(eventRate = v) } },
                        )
                    }
                    LevelSlider(
                        "Slow modulation",
                        layer.modulation,
                        { v -> r.layer(layer.id) { it.copy(modulation = v) } },
                    )
                }
                LevelSlider(
                    "Layer fade",
                    layer.fadeSeconds,
                    { v -> r.layer(layer.id) { it.copy(fadeSeconds = v) } },
                    .1f..60f,
                    "${"%.1f".format(layer.fadeSeconds)} s",
                )
                LevelSlider(
                    "Stop this layer after",
                    layer.stopAfterMinutes,
                    { v -> r.layer(layer.id) { it.copy(stopAfterMinutes = v) } },
                    0f..120f,
                    if (layer.stopAfterMinutes < .5f) "All night"
                    else "${layer.stopAfterMinutes.roundToInt()} min",
                )
                if (sound?.kind == "thunder")
                    LevelSlider(
                        "Storm becomes rain over",
                        layer.weatherToRainMinutes,
                        { v -> r.layer(layer.id) { it.copy(weatherToRainMinutes = v) } },
                        0f..120f,
                        if (layer.weatherToRainMinutes < .5f) "Off"
                        else "${layer.weatherToRainMinutes.roundToInt()} min",
                    )
                if (sound?.music == true)
                    TextButton(onClick = { onStudio(layer.id) }) {
                        Icon(Icons.Outlined.Tune, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit composition in Studio")
                    }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "${s/60}:${(s%60).toString().padStart(2,'0')}"
}

@Composable
private fun TimerDialog(r: NightfallRuntime, status: PlaybackStatus, onDismiss: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf("30") }
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
                                modifier = Modifier.weight(1f),
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
                if (status.timerActive)
                    TextButton(
                        onClick = {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (r.timer.paused) r.timer.resume(now) else r.timer.pause(now)
                            r.status.value = r.status.value.copy(timerPaused = r.timer.paused)
                        }
                    ) {
                        Text(if (status.timerPaused) "Resume timer" else "Pause timer")
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
