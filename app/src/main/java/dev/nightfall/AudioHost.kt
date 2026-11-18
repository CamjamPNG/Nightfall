// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.*
import android.media.*
import android.os.*
import dev.nightfall.core.*
import java.nio.channels.FileChannel
import kotlin.math.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the single output stream; never performs DSP on the main thread.
 *
 * The worker thread is the audio thread. Inside its block loop it allocates nothing, takes no lock,
 * logs nothing and catches nothing: pending state is published into volatile fields that a main
 * thread monitor reads, because a `MutableStateFlow` write allocates and notifies collectors.
 */
class AudioHost(private val runtime: NightfallRuntime) {
    private val context = runtime.context
    private val manager = context.getSystemService(AudioManager::class.java)
    private val wake =
        context
            .getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nightfall:audio")
    private val monitor = Object()
    @Volatile private var running = true
    @Volatile private var resetRequested = false
    @Volatile private var playing = false
    @Volatile private var focusGain = 1f
    @Volatile private var resumeAfterFocus = false
    private var focusHeld = false
    @Volatile private var track: AudioTrack? = null
    @Volatile private var meterPeak = 0f
    @Volatile private var meterLoad = 0f
    @Volatile private var meterUnderruns = 0
    @Volatile private var meterRemaining = 0L
    @Volatile private var failure: String? = null
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(
                { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            resumeAfterFocus = false
                            focusHeld = false
                            pause(false)
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            resumeAfterFocus = playing
                            pause(false)
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> focusGain = .25f
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            focusGain = 1f
                            if (resumeAfterFocus) {
                                resumeAfterFocus = false
                                play()
                            }
                        }
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            .build()
    private val noisy =
        object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (runtime.saved.value.settings.pauseOnDisconnect) pause(true)
            }
        }
    private val devices =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                updateRoute()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                updateRoute()
            }
        }

    init {
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            noisy,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        manager.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
        runtime.scope.launch {
            while (isActive && running) {
                delay(250)
                publishMeters()
            }
        }
    }

    /** Main thread: the only place playback status is written. */
    private fun publishMeters() {
        failure?.let {
            failure = null
            pause(true)
            runtime.status.value = runtime.status.value.copy(error = "Audio stopped: $it")
            return
        }
        if (!playing) return
        val now = SystemClock.elapsedRealtime()
        updateRoute()
        runtime.status.value =
            runtime.status.value.copy(
                peak = meterPeak,
                load = meterLoad,
                underruns = meterUnderruns,
                remainingMs = meterRemaining,
                timerActive = runtime.timer.active,
                timerPaused = runtime.timer.paused,
                timerFading = runtime.timer.active && runtime.timer.remainingMs(now) == 0L,
            )
        // Evaluated against the live timer, so a cancel or replacement between the worker's block
        // and this tick can never stop playback on an obsolete deadline.
        if (runtime.timer.expired(now)) runtime.stop()
    }

    fun play() {
        if (playing) return
        try {
            runtime.promotePlayback?.invoke()
        } catch (e: Exception) {
            runtime.status.value =
                runtime.status.value.copy(
                    error = "Background playback could not start: ${e.message}"
                )
            return
        }
        val result = manager.requestAudioFocus(focus)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            runtime.status.value =
                runtime.status.value.copy(
                    error = "Audio focus is unavailable. Try again after the other audio ends."
                )
            return
        }
        focusHeld = true
        focusGain = 1f
        playing = true
        runtime.timer.resume(SystemClock.elapsedRealtime())
        if (!wake.isHeld) wake.acquire()
        runtime.status.value = runtime.status.value.copy(playing = true, error = null)
        synchronized(monitor) { monitor.notifyAll() }
    }

    fun pause(user: Boolean) {
        playing = false
        if (user) {
            resumeAfterFocus = false
            if (focusHeld) {
                manager.abandonAudioFocusRequest(focus)
                focusHeld = false
            }
        }
        runtime.timer.pause(SystemClock.elapsedRealtime())
        if (wake.isHeld) wake.release()
        runtime.status.value = runtime.status.value.copy(playing = false)
    }

    fun stop() {
        pause(true)
        resetRequested = true
    }

    fun updateRoute() {
        val device = track?.routedDevice
        val route =
            when (device?.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET ->
                    OutputRoute(
                        OutputRoute.Kind.BLUETOOTH,
                        device.productName?.toString().orEmpty(),
                    )
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputRoute(OutputRoute.Kind.WIRED)
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputRoute(OutputRoute.Kind.SPEAKER)
                else ->
                    if (device == null) OutputRoute()
                    else
                        OutputRoute(
                            OutputRoute.Kind.DEVICE,
                            device.productName?.toString().orEmpty(),
                        )
            }
        runtime.status.value = runtime.status.value.copy(output = route)
    }

    private val worker =
        Thread(
                {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                    val sampleRate = 48000
                    val frames = 1024
                    val cache = HashMap<String, SampleData>()
                    val mixer =
                        PcmMixer(sampleRate, frames) { asset ->
                            cache[asset] ?: loadRecording(asset)?.also { cache[asset] = it }
                        }
                    var previous: Mix? = null
                    val pcm = FloatArray(frames * 2)
                    try {
                        val minimum =
                            AudioTrack.getMinBufferSize(
                                sampleRate,
                                AudioFormat.CHANNEL_OUT_STEREO,
                                AudioFormat.ENCODING_PCM_FLOAT,
                            )
                        val output =
                            AudioTrack.Builder()
                                .setAudioAttributes(attributes)
                                .setAudioFormat(
                                    AudioFormat.Builder()
                                        .setSampleRate(sampleRate)
                                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                        .build()
                                )
                                .setBufferSizeInBytes(max(minimum, frames * 8 * 4))
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build()
                        track = output
                        var blocks = 0
                        var queuedBlocks = 0
                        var average = 0f
                        while (running) {
                            if (!playing) {
                                if (output.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                    output.pause()
                                    output.flush()
                                    queuedBlocks = 0
                                }
                                synchronized(monitor) { if (!playing && running) monitor.wait() }
                                continue
                            }
                            if (resetRequested) {
                                mixer.reset()
                                previous = null
                                resetRequested = false
                            }
                            val snapshot = runtime.saved.value
                            val start = System.nanoTime()
                            if (snapshot.current != previous) {
                                mixer.sync(snapshot.current)
                                previous = snapshot.current
                                reportDecodeFailure(cache.values)
                            }
                            mixer.settings = snapshot.settings
                            val now = SystemClock.elapsedRealtime()
                            mixer.render(pcm, frames, runtime.timer.gain(now) * focusGain)
                            if (blocks % 8 == 0) reportDecodeFailure(cache.values)
                            val time = (System.nanoTime() - start) / 1e9f
                            average =
                                .98f * average + .02f * (time / (frames.toFloat() / sampleRate))
                            var offset = 0
                            while (offset < pcm.size && running && playing) {
                                val written =
                                    output.write(
                                        pcm,
                                        offset,
                                        pcm.size - offset,
                                        AudioTrack.WRITE_BLOCKING,
                                    )
                                if (written <= 0) {
                                    failure = "AudioTrack write returned $written"
                                    break
                                }
                                offset += written
                            }
                            // A dead track must end the worker, not just this block: only exiting
                            // releases it, and only a released host lets PlaybackService build a
                            // healthy one for the next Play.
                            if (failure != null) break
                            // Prefill 64 ms before starting/resuming to absorb cold DSP/JIT work.
                            if (
                                playing &&
                                    output.playState != AudioTrack.PLAYSTATE_PLAYING &&
                                    ++queuedBlocks >= 3
                            )
                                output.play()
                            // Publish into plain volatiles; the main-thread monitor reads them.
                            blocks++
                            if (blocks % 24 == 0) {
                                meterPeak = mixer.peak
                                meterLoad = average
                                meterUnderruns = output.underrunCount
                                meterRemaining = runtime.timer.remainingMs(now)
                            }
                        }
                    } catch (e: Exception) {
                        if (running) failure = e.message ?: e.javaClass.simpleName
                    } finally {
                        track?.release()
                        track = null
                    }
                },
                "Nightfall PCM",
            )
            .apply { start() }

    val isAlive
        get() = worker.isAlive

    /**
     * One bundled recording, either decoded from its Opus container or memory mapped from PCM. The
     * asset kind is a build flag, so a build either carries the encoded recordings or the originals,
     * never both.
     */
    private fun loadRecording(asset: String): SampleData? =
        if (BuildConfig.OPUS_ASSETS)
            OpusAssets.open(context, asset) { reason -> reportDecodeFailure(reason) }
        else
            context.assets.openFd("audio/$asset.pcm").use { fd ->
                fd.createInputStream().use { input ->
                    Pcm16Data(
                        input.channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            fd.startOffset,
                            fd.length,
                        )
                    )
                }
            }

    /**
     * A recording this device cannot decode must be reported: a layer that contributes silence without
     * saying so is the worst failure a mixer can have.
     */
    private fun reportDecodeFailure(reason: String) {
        val message = "A bundled recording cannot be decoded on this device ($reason)."
        if (runtime.status.value.error != message)
            runtime.status.value = runtime.status.value.copy(error = message)
    }

    /** A decoder that fails after the mix was built still has to reach the user. */
    private fun reportDecodeFailure(recordings: Collection<SampleData>) {
        val reason =
            recordings.filterIsInstance<DecodedSampleData>().firstNotNullOfOrNull { it.unavailable }
                ?: return
        reportDecodeFailure(reason)
    }

    fun release() {
        pause(true)
        running = false
        synchronized(monitor) { monitor.notifyAll() }
        track?.pause()
        track?.flush()
        worker.interrupt()
        worker.join(1500)
        manager.unregisterAudioDeviceCallback(devices)
        context.unregisterReceiver(noisy)
    }
}
