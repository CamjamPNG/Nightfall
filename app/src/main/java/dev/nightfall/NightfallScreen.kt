// SPDX-License-Identifier: MIT
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.nightfall

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
    // Where the first-run card's middle row sends a person: the phrase box on Home.
    val phraseFocus = remember { FocusRequester() }
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
                        snackbar.showSnackbar(context.getString(R.string.snackbar_mix_exported))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(context.getString(R.string.snackbar_export_failed, e.message))
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
                        snackbar.showSnackbar(context.getString(R.string.snackbar_imported, mix.name))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(context.getString(R.string.snackbar_import_failed, e.message))
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
        val firstRunDone by runtime.firstRunDone.collectAsStateWithLifecycle()
        val noSavedMixes by
            selectSaved(runtime, runtime.savedNow().presets.isEmpty()) { it.presets.isEmpty() }
        val playingNow by selectStatus(runtime, runtime.statusNow().playing) { it.playing }
        // Three things have to be true at once, and each of them alone retires the card: nothing was
        // ever dismissed, nothing was ever saved, nothing is playing.
        val showFirstRun = !firstRunDone && noSavedMixes && !playingNow
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
                            stringResource(R.string.nav_home) to Icons.Outlined.Home,
                            stringResource(R.string.nav_library) to Icons.Outlined.LibraryMusic,
                            stringResource(R.string.nav_presets) to Icons.Outlined.Bookmarks,
                            stringResource(R.string.nav_studio) to Icons.Outlined.Tune,
                            stringResource(R.string.nav_settings) to Icons.Outlined.Settings,
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
                                        showFirstRun = showFirstRun,
                                        phraseFocus = phraseFocus,
                                        onRetireFirstRun = { runtime.retireFirstRun() },
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
                        Text(
                            stringResource(
                                if (editingPreset == null) R.string.dialog_save_title
                                else R.string.dialog_rename_title
                            )
                        )
                    },
                    text = {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(200) },
                            label = { Text(stringResource(R.string.dialog_mix_name)) },
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
                            Text(stringResource(R.string.common_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            "timer" -> TimerDialog(runtime, onDismiss = { dialog = null })
            "fade" ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(stringResource(R.string.dialog_fade_title)) },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.dialog_fade_body),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(20.dp))
                            val fadeSeconds by
                                selectSaved(runtime, runtime.savedNow().settings.fadeSeconds) {
                                    it.settings.fadeSeconds
                                }
                            LevelSlider(
                                stringResource(R.string.dialog_fade_duration),
                                fadeSeconds,
                                { v ->
                                    runtime.change {
                                        it.copy(settings = it.settings.copy(fadeSeconds = v))
                                    }
                                },
                                5f..120f,
                                pluralStringResource(
                                    R.plurals.unit_seconds,
                                    fadeSeconds.roundToInt(),
                                    fadeSeconds.roundToInt(),
                                ),
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
                            Text(stringResource(R.string.dialog_fade_now))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) {
                            Text(stringResource(R.string.common_done))
                        }
                    },
                )
            "credits" ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(stringResource(R.string.dialog_credits_title)) },
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
                    confirmButton = {
                        TextButton(onClick = { dialog = null }) {
                            Text(stringResource(R.string.common_done))
                        }
                    },
                )
        }
    }
}

@Composable
private fun Home(
    r: NightfallRuntime,
    showFirstRun: Boolean,
    phraseFocus: FocusRequester,
    onRetireFirstRun: () -> Unit,
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
    // The card's middle row retires the card and then hands the person the phrase box. The landing
    // runs after this composition has dropped the card, because that is what moves the box from the
    // list's fourth item to its third.
    val list = rememberLazyListState()
    var landing by remember { mutableStateOf(false) }
    LaunchedEffect(landing, showFirstRun) {
        if (landing && !showFirstRun) {
            list.animateScrollToItem(2)
            phraseFocus.requestFocus()
            landing = false
        }
    }
    LazyColumn(
        state = list,
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
                        stringResource(R.string.home_title),
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
                        stringResource(R.string.home_action_open_presets),
                        Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        stringResource(R.string.home_action_open_settings),
                        Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { NowPlayingCard(r, mixName, reducedMotion, onTimer) }
        // Under the transport, over the phrase box it points at: the first-run card never covers a
        // control that starts a night.
        if (showFirstRun)
            item {
                FirstRunCard(
                    onPresets = {
                        onRetireFirstRun()
                        onPresets()
                    },
                    onDescribe = {
                        onRetireFirstRun()
                        landing = true
                    },
                    onLibrary = {
                        onRetireFirstRun()
                        onAdd()
                    },
                    onDismiss = onRetireFirstRun,
                )
            }
        item { DescribeMix(r, onAdd, phraseFocus) }
        item {
            Column(Modifier.padding(top = 6.dp)) {
                val master by
                    selectSaved(r, r.savedNow().current.master) { it.current.master }
                LevelSlider(
                    stringResource(R.string.home_master_volume),
                    master,
                    { v -> r.mix { it.copy(master = v) } },
                )
                QuickActions(r, onFade, onSave)
            }
        }
        item {
            val endless by
                selectSaved(r, r.savedNow().current.endless.enabled) { it.current.endless.enabled }
            SettingToggle(
                stringResource(R.string.home_endless_title),
                stringResource(R.string.home_endless_description),
                endless,
            ) { on ->
                r.mix { it.copy(endless = it.endless.copy(enabled = on)) }
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
                        stringResource(R.string.home_section_your_soundscape),
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.home_layer_count,
                            layerIds.size,
                            layerIds.size,
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_action_add_sound))
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_action_add_another_sound))
            }
        }
        item {
            Text(
                stringResource(R.string.home_tagline),
                Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowPlayingCard(
    r: NightfallRuntime,
    mixName: String,
    reducedMotion: Boolean,
    onTimer: () -> Unit,
) {
    val context = LocalContext.current
    val playing by selectStatus(r, false) { it.playing }
    val output by selectStatus(r, OutputRoute()) { it.output }
    // Resolved in composition: a semantics block is not composable.
    val playingState = stringResource(R.string.transport_state_playing)
    val pausedState = stringResource(R.string.transport_state_paused)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (playing) stringResource(R.string.home_status_playing)
                        else stringResource(R.string.home_status_ready),
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(mixName, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                }
                FilledIconButton(
                    onClick = { if (playing) r.pause() else r.play() },
                    modifier =
                        Modifier.size(66.dp).semantics {
                            // "Play soundscape, Paused" is a button that only says the half of it.
                            stateDescription = if (playing) playingState else pausedState
                        },
                    shape = CircleShape,
                ) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (playing) stringResource(R.string.home_action_pause)
                        else stringResource(R.string.home_action_play),
                        Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Waveform(playing, reducedMotion)
            TimerAnnouncement(r)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The transport line flows instead of ending in an ellipsis. At 200% text the route
                // is wider than what the row's other items leave it, and "System audio outp…" is a
                // line nobody can finish reading; here it moves to a second line and takes the width
                // it needs. The spacers are the row's own gaps, so at 100% this lays out on one line
                // where it always did.
                FlowRow(
                    modifier = Modifier.weight(1f),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
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
                        outputShortLabel(context, output),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TimerButton(r, onTimer)
            }
        }
    }
}

@Composable
private fun ActiveLayerLabel(r: NightfallRuntime) {
    val active by selectSaved(r, r.savedNow().activeLayers()) { it.activeLayers() }
    Text(
        pluralStringResource(R.plurals.unit_layers, active, active),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Speaks the timer once, when it is set, and says nothing while it runs.
 *
 * The countdown itself is a value that changes every second; a live region on it would read the
 * clock aloud all night. So the announcement is the *setting*, which changes once, and it is polite:
 * it queues behind whatever the reader was already saying instead of cutting in.
 */
@Composable
private fun TimerAnnouncement(r: NightfallRuntime) {
    val active by selectStatus(r, false) { it.timerActive }
    val minutes by selectStatus(r, 0) { s ->
        if (s.timerActive) ((s.remainingMs + 59_999) / 60_000).toInt() else 0
    }
    // The countdown crossing a minute is not news. The announcement is captured when the timer
    // *starts* and then held: keying this on the remaining minutes would say the clock aloud, once a
    // minute, all night. Nothing is said for "All night", which is where the card already starts.
    val set = if (minutes > 0) pluralStringResource(R.plurals.a11y_timer_set, minutes, minutes) else ""
    var announced by remember { mutableStateOf("") }
    LaunchedEffect(active) { announced = if (active) set else "" }
    LiveAnnouncement(announced)
}

@Composable
private fun TimerButton(r: NightfallRuntime, onTimer: () -> Unit) {
    val allNight = stringResource(R.string.common_all_night)
    val fading = stringResource(R.string.common_fading)
    val sleepTimer = stringResource(R.string.home_a11y_sleep_timer)
    val label by
        selectStatus(r, allNight) { s ->
            if (s.timerActive) if (s.timerFading) fading else elapsedLabel(s.remainingMs)
            else allNight
        }
    TextButton(
        onClick = onTimer,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier =
            Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = sleepTimer
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
            if (allMuted) stringResource(R.string.home_action_unmute)
            else stringResource(R.string.home_action_mute_all),
            Icons.AutoMirrored.Outlined.VolumeOff,
            Modifier.weight(1f),
        ) {
            val muted = !r.savedNow().current.layers.all { it.mute }
            r.mix { it.copy(layers = it.layers.map { l -> l.copy(mute = muted) }) }
        }
        TinyAction(
            stringResource(R.string.home_action_stop_all),
            Icons.Outlined.Stop,
            Modifier.weight(1f),
        ) { r.stop() }
        TinyAction(
            stringResource(R.string.home_action_fade),
            Icons.Outlined.BlurOn,
            Modifier.weight(1f),
            onFade,
        )
        TinyAction(
            stringResource(R.string.home_action_save_mix),
            Icons.Outlined.BookmarkAdd,
            Modifier.weight(1f),
            onSave,
        )
        TinyAction(
            stringResource(R.string.home_action_reset),
            Icons.Outlined.RestartAlt,
            Modifier.weight(1f),
        ) { r.mix { Presets.all.last() } }
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
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        item {
            val seeAll = stringResource(R.string.home_a11y_all_presets)
            SuggestionChip(
                onClick = onPresets,
                label = { Text(stringResource(R.string.home_action_all_presets), fontSize = 12.sp) },
                modifier =
                    Modifier.heightIn(min = 48.dp).semantics { contentDescription = seeAll },
            )
        }
    }
}

@Composable
private fun CapacityWarning(r: NightfallRuntime) {
    val stats by selectStatus(r, 0 to 0) { (it.load * 100).roundToInt() to it.underruns }
    Text(
        pluralStringResource(
            R.plurals.home_capacity_warning,
            stats.second,
            stats.first,
            stats.second,
        ),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
    )
}

@Composable
private fun TinyAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
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
    val context = LocalContext.current
    val adjusted = adjustedOptions(context, current)
    val reorderLabel = stringResource(R.string.layer_a11y_reorder, current.name)
    val moveUp = stringResource(R.string.layer_a11y_move_up)
    val moveDown = stringResource(R.string.layer_a11y_move_down)
    val enableLabel = stringResource(R.string.layer_a11y_enable, current.name)
    val muteA11y = stringResource(R.string.layer_a11y_mute, current.name)
    val soloA11y = stringResource(R.string.layer_a11y_solo, current.name)
    val optionsA11y = stringResource(R.string.layer_a11y_options, current.name)
    val mutableStateOn = stringResource(R.string.layer_state_muted)
    val mutableStateOff = stringResource(R.string.layer_state_not_muted)
    val soloStateOn = stringResource(R.string.layer_state_solo_on)
    val soloStateOff = stringResource(R.string.layer_state_solo_off)
    val stateOpen = stringResource(R.string.layer_state_expanded)
    val stateDefaults = stringResource(R.string.layer_state_collapsed_defaults)
    val stateAdjusted = stringResource(R.string.layer_state_collapsed_adjusted, adjusted.size)
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
                            contentDescription = reorderLabel
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(moveUp) {
                                        r.reorder(current.id, -1)
                                        true
                                    },
                                    CustomAccessibilityAction(moveDown) {
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
                        layerSubtitle(context, sound),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Switch(
                    checked = current.enabled,
                    onCheckedChange = { v -> r.layer(current.id) { it.copy(enabled = v) } },
                    modifier =
                        Modifier.semantics {
                            contentDescription = enableLabel
                        },
                )
            }
            LevelSlider(
                stringResource(R.string.layer_volume),
                current.gain,
                { v -> r.layer(current.id) { it.copy(gain = v) } },
                spokenName = stringResource(R.string.layer_spoken_volume, current.name),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = current.mute,
                    onClick = { r.layer(current.id) { it.copy(mute = !it.mute) } },
                    label = { Text(stringResource(R.string.layer_action_mute), fontSize = 11.sp) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = muteA11y
                            stateDescription =
                                if (current.mute) mutableStateOn else mutableStateOff
                        },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = current.solo,
                    onClick = { r.layer(current.id) { it.copy(solo = !it.solo) } },
                    label = { Text(stringResource(R.string.layer_action_solo), fontSize = 11.sp) },
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = soloA11y
                            stateDescription = if (current.solo) soloStateOn else soloStateOff
                        },
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        r.mix { it.copy(layers = it.layers.filterNot { l -> l.id == current.id }) }
                    },
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        stringResource(R.string.layer_action_remove, current.name),
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = { expanded = !expanded },
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = optionsA11y
                        stateDescription =
                            if (expanded) stateOpen
                            else if (adjusted.isEmpty()) stateDefaults
                            else stateAdjusted
                    },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (adjusted.isEmpty()) stringResource(R.string.layer_options)
                    else stringResource(R.string.layer_options_adjusted, adjusted.size),
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
                OptionGroup(stringResource(R.string.option_group_placement)) {
                    LevelSlider(
                        stringResource(R.string.option_pan),
                        current.pan,
                        { v -> r.layer(current.id) { it.copy(pan = v) } },
                        -1f..1f,
                        if (abs(current.pan) < .05) stringResource(R.string.option_pan_center)
                        else
                            stringResource(
                                R.string.option_pan_value,
                                (abs(current.pan) * 100).roundToInt(),
                                stringResource(
                                    if (current.pan < 0) R.string.option_pan_left
                                    else R.string.option_pan_right
                                ),
                            ),
                        stringResource(R.string.option_spoken_pan, current.name),
                    )
                    LevelSlider(
                        stringResource(R.string.option_stereo_width),
                        current.width,
                        { v -> r.layer(current.id) { it.copy(width = v) } },
                        spokenName = stringResource(R.string.option_spoken_stereo_width, current.name),
                    )
                }
                OptionGroup(stringResource(R.string.option_group_tone)) {
                    LevelSlider(
                        stringResource(R.string.option_bass),
                        current.bass,
                        { v -> r.layer(current.id) { it.copy(bass = v) } },
                        -12f..12f,
                        stringResource(R.string.unit_decibels, current.bass.roundToInt()),
                        stringResource(R.string.option_spoken_bass, current.name),
                    )
                    LevelSlider(
                        stringResource(R.string.option_mid),
                        current.mid,
                        { v -> r.layer(current.id) { it.copy(mid = v) } },
                        -12f..12f,
                        stringResource(R.string.unit_decibels, current.mid.roundToInt()),
                        stringResource(R.string.option_spoken_mid, current.name),
                    )
                    LevelSlider(
                        stringResource(R.string.option_treble),
                        current.treble,
                        { v -> r.layer(current.id) { it.copy(treble = v) } },
                        -12f..12f,
                        stringResource(R.string.unit_decibels, current.treble.roundToInt()),
                        stringResource(R.string.option_spoken_treble, current.name),
                    )
                    LevelSlider(
                        stringResource(R.string.option_brightness),
                        current.brightness,
                        { v -> r.layer(current.id) { it.copy(brightness = v) } },
                        spokenName =
                            stringResource(R.string.option_spoken_brightness, current.name),
                    )
                    LevelSlider(
                        stringResource(R.string.option_spectral_tilt),
                        current.tilt,
                        { v -> r.layer(current.id) { it.copy(tilt = v) } },
                        -1f..1f,
                        "%.1f".format(current.tilt),
                        stringResource(R.string.option_spoken_tilt, current.name),
                    )
                }
                OptionGroup(stringResource(R.string.option_group_filters)) {
                    LevelSlider(
                        stringResource(R.string.option_high_pass),
                        current.highPass,
                        { v -> r.layer(current.id) { it.copy(highPass = v) } },
                        20f..2000f,
                        stringResource(R.string.unit_hertz, current.highPass.roundToInt()),
                        stringResource(R.string.option_spoken_high_pass, current.name),
                    )
                    LevelSlider(
                        stringResource(R.string.option_low_pass),
                        current.lowPass,
                        { v -> r.layer(current.id) { it.copy(lowPass = v) } },
                        500f..20000f,
                        stringResource(R.string.unit_hertz, current.lowPass.roundToInt()),
                        stringResource(R.string.option_spoken_low_pass, current.name),
                    )
                }
                if (hasTextureOptions(sound))
                    OptionGroup(stringResource(R.string.option_group_texture)) {
                        if (sound?.kind == "binaural")
                            LevelSlider(
                                stringResource(R.string.option_beat_frequency),
                                current.intensity,
                                { v -> r.layer(current.id) { it.copy(intensity = v) } },
                                display =
                                    stringResource(
                                        R.string.unit_hertz_decimal,
                                        "%.1f".format(1 + current.intensity * 19),
                                    ),
                                spokenName =
                                    stringResource(
                                        R.string.option_spoken_beat_frequency,
                                        current.name,
                                    ),
                            )
                        else {
                            LevelSlider(
                                stringResource(R.string.option_intensity),
                                current.intensity,
                                { v -> r.layer(current.id) { it.copy(intensity = v) } },
                                spokenName =
                                    stringResource(R.string.option_spoken_intensity, current.name),
                            )
                            if (sound?.kind in listOf("rain", "thunder", "winter", "fire"))
                                LevelSlider(
                                    stringResource(R.string.option_event_frequency),
                                    current.eventRate,
                                    { v -> r.layer(current.id) { it.copy(eventRate = v) } },
                                    spokenName =
                                        stringResource(
                                            R.string.option_spoken_event_frequency,
                                            current.name,
                                        ),
                                )
                            LevelSlider(
                                stringResource(R.string.option_slow_modulation),
                                current.modulation,
                                { v -> r.layer(current.id) { it.copy(modulation = v) } },
                                spokenName =
                                    stringResource(
                                        R.string.option_spoken_modulation,
                                        current.name,
                                    ),
                            )
                        }
                    }
                OptionGroup(stringResource(R.string.option_group_timing)) {
                    LevelSlider(
                        stringResource(R.string.option_fade_over),
                        current.fadeSeconds,
                        { v -> r.layer(current.id) { it.copy(fadeSeconds = v) } },
                        .1f..60f,
                        stringResource(
                            R.string.unit_seconds_decimal,
                            "%.1f".format(current.fadeSeconds),
                        ),
                        stringResource(R.string.option_spoken_fade, current.name),
                    )
                    LevelSlider(
                        stringResource(R.string.option_stop_after),
                        current.stopAfterMinutes,
                        { v -> r.layer(current.id) { it.copy(stopAfterMinutes = v) } },
                        0f..120f,
                        if (current.stopAfterMinutes < .5f)
                            stringResource(R.string.common_all_night)
                        else
                            stringResource(
                                R.string.unit_minutes,
                                current.stopAfterMinutes.roundToInt(),
                            ),
                        stringResource(R.string.option_spoken_stop_time, current.name),
                    )
                    if (sound?.kind == "thunder")
                        LevelSlider(
                            stringResource(R.string.option_storm_to_rain),
                            current.weatherToRainMinutes,
                            { v -> r.layer(current.id) { it.copy(weatherToRainMinutes = v) } },
                            0f..120f,
                            if (current.weatherToRainMinutes < .5f)
                                stringResource(R.string.common_off)
                            else
                                stringResource(
                                    R.string.unit_minutes,
                                    current.weatherToRainMinutes.roundToInt(),
                                ),
                            stringResource(
                                R.string.option_spoken_storm_transition,
                                current.name,
                            ),
                        )
                }
                if (sound?.music == true)
                    TextButton(
                        onClick = { onStudio(current.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.layer_action_edit_in_studio))
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
private fun adjustedOptions(context: Context, l: Layer): List<String> {
    val d = Layer(id = l.id, soundId = l.soundId, name = l.name)
    fun f(v: Float, ref: Float, name: Int) =
        if (abs(v - ref) > 1e-4f) context.getString(name) else null
    return listOfNotNull(
        f(l.pan, d.pan, R.string.layer_adjust_pan),
        f(l.width, d.width, R.string.layer_adjust_stereo_width),
        f(l.highPass, d.highPass, R.string.layer_adjust_high_pass),
        f(l.lowPass, d.lowPass, R.string.layer_adjust_low_pass),
        f(l.bass, d.bass, R.string.layer_adjust_bass),
        f(l.mid, d.mid, R.string.layer_adjust_mid),
        f(l.treble, d.treble, R.string.layer_adjust_treble),
        f(l.brightness, d.brightness, R.string.layer_adjust_brightness),
        f(l.tilt, d.tilt, R.string.layer_adjust_tilt),
        f(l.intensity, d.intensity, R.string.layer_adjust_intensity),
        f(l.eventRate, d.eventRate, R.string.layer_adjust_events),
        f(l.modulation, d.modulation, R.string.layer_adjust_modulation),
        f(l.fadeSeconds, d.fadeSeconds, R.string.layer_adjust_fade),
        f(l.stopAfterMinutes, d.stopAfterMinutes, R.string.layer_adjust_stop_time),
        f(l.weatherToRainMinutes, d.weatherToRainMinutes, R.string.layer_adjust_storm_transition),
    )
}

/** Whether core reads intensity/modulation for this kind, so the panel never shows an inert control. */
private fun hasTextureOptions(sound: Sound?): Boolean =
    when (sound?.kind) {
        "music", "recording", null -> false
        else -> true
    }

private fun layerSubtitle(context: Context, sound: Sound?): String =
    when (sound?.kind) {
        "recording" -> context.getString(R.string.kind_field_recording)
        "music" -> context.getString(R.string.kind_generative_music)
        "binaural" -> context.getString(R.string.kind_binaural)
        "noise" -> context.getString(R.string.kind_noise)
        "clock" -> context.getString(R.string.kind_clock)
        "hum" -> context.getString(R.string.kind_hum)
        null -> ""
        else ->
            context.getString(
                R.string.kind_other,
                sound.kind.replaceFirstChar { it.uppercase() },
            )
    }

@Composable
private fun TimerDialog(r: NightfallRuntime, onDismiss: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf("30") }
    val timerActive by selectStatus(r, false) { it.timerActive }
    val timerPaused by selectStatus(r, false) { it.timerPaused }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_timer_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.dialog_timer_body),
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
                                Text(
                                    if (m == 0) stringResource(R.string.common_all_night)
                                    else stringResource(R.string.unit_minutes, m),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.dialog_timer_custom)) },
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
                        Text(
                            if (timerPaused) stringResource(R.string.dialog_timer_resume)
                            else stringResource(R.string.dialog_timer_pause)
                        )
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
                Text(stringResource(R.string.dialog_timer_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}
