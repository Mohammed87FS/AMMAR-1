package alh.za.ammar.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class AlarmSoundService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stopAlarm() }

    private var activeTitle: String? = null
    private var activeMessage: String? = null
    private var activeNotificationId: Int = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            activeNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, activeNotificationId)
            val showCompletionNotification =
                intent.getBooleanExtra(EXTRA_SHOW_COMPLETION_NOTIFICATION, true)
            stopAlarm(showCompletionNotification)
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_ALARM_TITLE)
        val message = intent?.getStringExtra(EXTRA_ALARM_MESSAGE)
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, activeNotificationId)
            ?: activeNotificationId

        if (title.isNullOrBlank() || message.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        activeTitle = title
        activeMessage = message
        activeNotificationId = notificationId

        createNotificationChannel(this)

        val notification = buildAlarmNotification(
            context = this,
            title = title,
            message = message,
            notificationId = notificationId
        )

        startAsForegroundService(notificationId, notification)
        runCatching {
            startAlarmPlayback()
        }.onFailure {
            showCompletionNotification(this, title, message, notificationId)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        stopHandler.removeCallbacks(autoStopRunnable)
        stopHandler.postDelayed(autoStopRunnable, AUTO_STOP_AFTER_MS)

        return START_STICKY
    }

    override fun onDestroy() {
        stopHandler.removeCallbacks(autoStopRunnable)
        releasePlayer()
        super.onDestroy()
    }

    private fun startAsForegroundService(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun startAlarmPlayback() {
        releasePlayer()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setDataSource(this@AlarmSoundService, getAlarmSoundUri(this@AlarmSoundService))
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopAlarm(showCompletionNotification: Boolean = true) {
        stopHandler.removeCallbacks(autoStopRunnable)
        releasePlayer()

        val title = activeTitle
        val message = activeMessage
        if (showCompletionNotification && !title.isNullOrBlank() && !message.isNullOrBlank()) {
            showCompletionNotification(this, title, message, activeNotificationId)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(activeNotificationId)
        stopSelf()
    }

    private fun releasePlayer() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    companion object {
        private const val AUTO_STOP_AFTER_MS = 60_000L
    }
}
