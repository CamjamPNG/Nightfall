// SPDX-License-Identifier: MIT
package dev.nightfall

import android.content.Intent
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var runtime: NightfallRuntime
    private lateinit var player: MixerPlayer
    private var session: MediaSession? = null
    private var observer: Job? = null

    override fun onCreate() {
        super.onCreate()
        runtime = (application as NightfallApp).runtime
        runtime.promotePlayback = ::promoteToForeground
        runtime.host = AudioHost(runtime)
        player = MixerPlayer(runtime)
        session =
            MediaSession.Builder(this, player)
                .setSessionActivity(
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(1001)
                .setChannelId("nightfall")
                .build()
                .apply { setSmallIcon(R.drawable.ic_moon) }
        )
        observer =
            runtime.scope.launch {
                combine(runtime.status, runtime.saved) { a, b -> a to b }
                    .collect { player.refresh() }
            }
    }

    private fun promoteToForeground() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                "nightfall",
                "Nightfall playback",
                android.app.NotificationManager.IMPORTANCE_LOW,
            )
        )
        val notification =
            androidx.core.app.NotificationCompat.Builder(this, "nightfall")
                .setSmallIcon(R.drawable.ic_moon)
                .setContentTitle(runtime.saved.value.current.name)
                .setContentText("Preparing your soundscape")
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .setOngoing(true)
                .setContentIntent(
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .build()
        if (android.os.Build.VERSION.SDK_INT >= 29)
            startForeground(
                1001,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        else startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == "play") {
            if (runtime.host?.isAlive != true) {
                runtime.host?.release()
                runtime.host = AudioHost(runtime)
            }
            runtime.host?.play()
            player.refresh()
            if (!runtime.status.value.playing) stopSelf()
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        observer?.cancel()
        runtime.host?.release()
        runtime.host = null
        runtime.promotePlayback = null
        session?.release()
        player.release()
        super.onDestroy()
    }

    // Playback deliberately survives task removal while playing.
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!runtime.status.value.playing) stopSelf()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class MixerPlayer(private val runtime: NightfallRuntime) :
    SimpleBasePlayer(Looper.getMainLooper()) {
    fun refresh() = invalidateState()

    override fun getState(): State {
        val s = runtime.status.value
        val mix = runtime.saved.value.current
        val anySolo = mix.layers.any { it.solo && it.enabled && !it.mute }
        val count = mix.layers.count { it.enabled && !it.mute && (!anySolo || it.solo) }
        val timer =
            if (s.timerActive)
                if (s.timerFading) " · Fading out"
                else
                    " · ${s.remainingMs/60000}:${((s.remainingMs/1000)%60).toString().padStart(2,'0')} remaining"
            else " · All night"
        val item =
            MediaItem.Builder()
                .setMediaId("nightfall-mix")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(mix.name)
                        .setArtist("$count layers$timer")
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_PREPARE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_RELEASE,
                    )
                    .build()
            )
            .setPlayWhenReady(s.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(
                listOf(
                    MediaItemData.Builder("nightfall-mix")
                        .setMediaItem(item)
                        .setIsSeekable(false)
                        .setIsDynamic(true)
                        .build()
                )
            )
            .setCurrentMediaItemIndex(0)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) runtime.host?.play() else runtime.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        runtime.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
