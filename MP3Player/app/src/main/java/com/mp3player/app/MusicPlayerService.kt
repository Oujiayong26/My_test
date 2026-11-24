package com.mp3player.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mp3player.app.data.Music
import kotlin.random.Random

/**
 * 后台音乐播放服务：管理播放列表、播放控制和前台通知
 */
class MusicPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var playlist: List<Music> = emptyList()
    private var currentIndex = 0
    private var mediaSession: MediaSessionCompat? = null

    var isShuffle = false
        private set
    var repeatMode = REPEAT_NONE
        private set

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MP3PlayerSession")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playPause()
            ACTION_NEXT -> next()
            ACTION_PREVIOUS -> previous()
            ACTION_STOP -> stopPlayback()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPrepared = false
    }

    // ---------------------------------------------------------------------
    // 播放控制 API
    // ---------------------------------------------------------------------

    fun setPlaylist(list: List<Music>, startIndex: Int = 0) {
        playlist = list
        currentIndex = if (startIndex in list.indices) startIndex else 0
        if (list.isNotEmpty()) playCurrent()
    }

    fun playPause() {
        val mp = mediaPlayer
        if (mp == null || !isPrepared) {
            if (playlist.isNotEmpty()) playCurrent()
            return
        }
        try {
            if (mp.isPlaying) mp.pause() else mp.start()
        } catch (e: Exception) {
            Log.e(TAG, "播放控制失败: ${e.message}")
            playCurrent()
            return
        }
        broadcast(ACTION_STATE_CHANGED)
        updateNotification()
    }

    fun next() {
        if (playlist.isEmpty()) return
        currentIndex = if (isShuffle) {
            if (playlist.size <= 1) 0 else Random.nextInt(playlist.size)
        } else {
            (currentIndex + 1) % playlist.size
        }
        playCurrent()
    }

    fun previous() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playCurrent()
    }

    fun seekTo(positionMs: Int) {
        if (!isPrepared) return
        try {
            val max = getDuration()
            mediaPlayer?.seekTo(if (max > 0) positionMs.coerceIn(0, max) else positionMs)
        } catch (_: Exception) {
        }
        broadcast(ACTION_PROGRESS_CHANGED)
    }

    fun getCurrentPosition(): Int = try {
        if (isPrepared) mediaPlayer?.currentPosition ?: 0 else 0
    } catch (_: Exception) {
        0
    }

    fun getDuration(): Int = try {
        if (isPrepared) mediaPlayer?.duration ?: 0 else 0
    } catch (_: Exception) {
        0
    }

    fun isPlaying(): Boolean = try {
        isPrepared && mediaPlayer?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    fun getCurrentMusic(): Music? = playlist.getOrNull(currentIndex)

    fun getPlaylist(): List<Music> = playlist

    fun toggleShuffle() {
        isShuffle = !isShuffle
        broadcast(ACTION_STATE_CHANGED)
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            REPEAT_NONE -> REPEAT_ALL
            REPEAT_ALL -> REPEAT_ONE
            else -> REPEAT_NONE
        }
        broadcast(ACTION_STATE_CHANGED)
    }

    fun stopPlayback() {
        try {
            if (isPrepared) mediaPlayer?.stop()
            mediaPlayer?.reset()
        } catch (_: Exception) {
        }
        isPrepared = false
        broadcast(ACTION_STATE_CHANGED)
        updateNotification()
    }

    private fun playCurrent() {
        val music = playlist.getOrNull(currentIndex) ?: return

        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPrepared = false

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            val source = if (music.isOnline) music.playableUrl else music.path
            mp.setDataSource(applicationContext, Uri.parse(source))
            mp.prepare()
            isPrepared = true

            mp.setOnCompletionListener { player ->
                when (repeatMode) {
                    REPEAT_ONE -> {
                        try {
                            player.seekTo(0)
                            player.start()
                        } catch (_: Exception) {
                            playCurrent()
                        }
                    }
                    REPEAT_ALL -> next()
                    else -> {
                        if (currentIndex < playlist.size - 1) {
                            next()
                        } else {
                            isPrepared = false
                            broadcast(ACTION_STATE_CHANGED)
                            updateNotification()
                        }
                    }
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer 错误: what=$what extra=$extra")
                isPrepared = false
                false
            }

            mp.start()
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e(TAG, "播放失败: ${e.message}")
            try {
                mp.release()
            } catch (_: Exception) {
            }
            mediaPlayer = null
            isPrepared = false
        }

        broadcast(ACTION_TRACK_CHANGED)
        updateNotification()
        ensureForeground()
    }

    // ---------------------------------------------------------------------
    // 通知
    // ---------------------------------------------------------------------

    private fun ensureForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val music = getCurrentMusic()
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionIntent(action: String): PendingIntent {
            val intent = Intent(this, MusicPlayerService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val playIcon = if (isPlaying()) R.drawable.ic_pause else R.drawable.ic_play
        val playLabel = if (isPlaying()) getString(R.string.pause) else getString(R.string.play)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(music?.title ?: getString(R.string.app_name))
            .setContentText(music?.artist ?: "未播放")
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying())
            .addAction(R.drawable.ic_previous, getString(R.string.previous), actionIntent(ACTION_PREVIOUS))
            .addAction(playIcon, playLabel, actionIntent(ACTION_PLAY_PAUSE))
            .addAction(R.drawable.ic_next, getString(R.string.next), actionIntent(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun broadcast(action: String) {
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    companion object {
        const val TAG = "MusicPlayerService"
        const val CHANNEL_ID = "mp3_player_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.mp3player.app.PLAY_PAUSE"
        const val ACTION_NEXT = "com.mp3player.app.NEXT"
        const val ACTION_PREVIOUS = "com.mp3player.app.PREVIOUS"
        const val ACTION_STOP = "com.mp3player.app.STOP"

        const val ACTION_STATE_CHANGED = "com.mp3player.app.STATE_CHANGED"
        const val ACTION_TRACK_CHANGED = "com.mp3player.app.TRACK_CHANGED"
        const val ACTION_PROGRESS_CHANGED = "com.mp3player.app.PROGRESS_CHANGED"

        const val REPEAT_NONE = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }
}
