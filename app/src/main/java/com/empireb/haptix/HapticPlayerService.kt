package com.empireb.haptix

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat

class HapticPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private val binder = LocalBinder()

    var playlist: List<HapticFile> = emptyList()
    var currentIndex: Int = -1

    var onTrackChanged: ((Int) -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onProgressUpdate: ((Int, Int) -> Unit)? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    onProgressUpdate?.invoke(it.currentPosition, it.duration)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): HapticPlayerService = this@HapticPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        setupMediaSession()
        createNotificationChannel()
        handler.post(progressRunnable)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "HapticPlayerSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { this@HapticPlayerService.play() }
                override fun onPause() { this@HapticPlayerService.pause() }
                override fun onSkipToNext() { this@HapticPlayerService.playNext() }
                override fun onSkipToPrevious() { this@HapticPlayerService.playPrevious() }
            })
            isActive = true
        }
    }

    fun playFile(index: Int) {
        if (index < 0 || index >= playlist.size) return
        currentIndex = index
        val file = playlist[index]

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@HapticPlayerService, file.uri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setHapticChannelsMuted(false)
                    .build()
            )
            setOnPreparedListener {
                start()
                updateNotification()
                updatePlaybackState(true)
                onTrackChanged?.invoke(currentIndex)
            }
            setOnCompletionListener {
                playNext()
            }
            prepareAsync()
        }
    }

    fun play() {
        mediaPlayer?.start()
        updateNotification()
        updatePlaybackState(true)
    }

    fun pause() {
        mediaPlayer?.pause()
        updateNotification()
        updatePlaybackState(false)
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val nextIndex = (currentIndex + 1) % playlist.size
        playFile(nextIndex)
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        var prevIndex = (currentIndex - 1) % playlist.size
        if (prevIndex < 0) prevIndex = playlist.size - 1
        playFile(prevIndex)
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(pos: Int) { mediaPlayer?.seekTo(pos) }

    private fun updatePlaybackState(playing: Boolean) {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .build()
        )
        onPlaybackStateChanged?.invoke(playing)
    }

    private fun updateNotification() {
        if (currentIndex < 0 || currentIndex >= playlist.size) return
        val file = playlist[currentIndex]

        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.notif_pause), getPendingIntent("PAUSE"))
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.notif_play), getPendingIntent("PLAY"))
        }

        val notification = NotificationCompat.Builder(this, "haptic_player_channel")
            .setContentTitle(file.name)
            .setContentText(getString(R.string.notif_content_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(isPlaying())
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.notif_prev), getPendingIntent("PREV"))
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.notif_next), getPendingIntent("NEXT"))
            .build()

        startForeground(1, notification)
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, HapticPlayerService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> play()
            "PAUSE" -> pause()
            "NEXT" -> playNext()
            "PREV" -> playPrevious()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("haptic_player_channel", getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession?.release()
        handler.removeCallbacks(progressRunnable)
    }
}
