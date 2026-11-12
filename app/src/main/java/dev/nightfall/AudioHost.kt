// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.*
import android.media.*
import android.os.*
import dev.nightfall.core.*
import java.nio.channels.FileChannel
import kotlin.math.*
import kotlinx.coroutines.launch

/** Owns the single output stream; never performs DSP on the main thread. */
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
        val label =
            when (device?.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth · ${device.productName}"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
                else -> device?.productName?.toString() ?: "System audio output"
            }
        runtime.status.value = runtime.status.value.copy(output = label)
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
                            cache.getOrPut(asset) {
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
                            }
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
                            }
                            mixer.settings = snapshot.settings
                            val now = SystemClock.elapsedRealtime()
                            mixer.render(pcm, frames, runtime.timer.gain(now) * focusGain)
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
                                check(written > 0) { "AudioTrack write failed: $written" }
                                offset += written
                            }
                            // Prefill 64 ms before starting/resuming to absorb cold DSP/JIT work.
                            if (
                                playing &&
                                    output.playState != AudioTrack.PLAYSTATE_PLAYING &&
                                    ++queuedBlocks >= 3
                            )
                                output.play()
                            if (++blocks % 24 == 0)
                                runtime.scope.launch {
                                    updateRoute()
                                    runtime.status.value =
                                        runtime.status.value.copy(
                                            peak = mixer.peak,
                                            load = average,
                                            underruns = output.underrunCount,
                                            remainingMs = runtime.timer.remainingMs(now),
                                            timerActive = runtime.timer.active,
                                            timerPaused = runtime.timer.paused,
                                            timerFading =
                                                runtime.timer.active &&
                                                    runtime.timer.remainingMs(now) == 0L,
                                        )
                                    if (runtime.timer.expired(SystemClock.elapsedRealtime()))
                                        runtime.stop()
                                }
                        }
                    } catch (e: Exception) {
                        if (running)
                            runtime.scope.launch {
                                pause(true)
                                runtime.status.value =
                                    runtime.status.value.copy(error = "Audio stopped: ${e.message}")
                            }
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
