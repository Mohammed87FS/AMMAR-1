package alh.za.ammar.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import alh.za.ammar.AlarmAlertActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AlarmEntry(
    val machineId: Int,
    val title: String,
    val message: String
)

class AlarmSoundService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private val stopHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                val machineId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                val showCompletion = intent.getBooleanExtra(EXTRA_SHOW_COMPLETION_NOTIFICATION, true)
                if (machineId == -1) {
                    handleStopAll(showCompletion)
                } else {
                    handleStopSingle(machineId, showCompletion)
                }
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_ALARM_TITLE)
                val message = intent?.getStringExtra(EXTRA_ALARM_MESSAGE)
                val machineId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1

                if (title.isNullOrBlank() || message.isNullOrBlank() || machineId == -1) {
                    stopSelfIfEmpty()
                    return START_NOT_STICKY
                }

                handleStartAlarm(AlarmEntry(machineId, title, message))
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopHandler.removeCallbacksAndMessages(null)
        releasePlayer()
        releaseScreenWakeLock()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        synchronized(_alarmQueue) {
            _alarmQueue.value.forEach { notificationManager.cancel(it.machineId) }
            _alarmQueue.value = emptyList()
            _alarmingMachineIds.value = emptySet()
        }
        _serviceRunning = false
        super.onDestroy()
    }

    private fun handleStartAlarm(entry: AlarmEntry) {
        _serviceRunning = true
        synchronized(_alarmQueue) {
            val current = _alarmQueue.value.toMutableList()
            if (current.none { it.machineId == entry.machineId }) {
                current.add(entry)
                _alarmQueue.value = current
                _alarmingMachineIds.value = current.map { it.machineId }.toSet()
            }
        }

        wakeUpScreen()
        createNotificationChannel(this)
        showAlarmNotification(entry)

        if (mediaPlayer == null || mediaPlayer?.isPlaying != true) {
            startAlarmPlayback()
        }

        resetAutoStopTimer()
        stopHandler.postDelayed({ launchAlarmAlertScreen() }, 300)
    }

    @Suppress("DEPRECATION")
    private fun wakeUpScreen() {
        if (screenWakeLock?.isHeld == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE,
            "alh.za.ammar:AlarmWakeLock"
        ).apply {
            acquire(SCREEN_WAKE_TIMEOUT_MS)
        }
    }

    private fun releaseScreenWakeLock() {
        screenWakeLock?.let {
            if (it.isHeld) {
                runCatching { it.release() }
            }
        }
        screenWakeLock = null
    }

    private fun launchAlarmAlertScreen() {
        runCatching {
            val activityIntent = Intent(this, AlarmAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(activityIntent)
        }
    }

    private fun handleStopAll(showCompletion: Boolean) {
        val all = _alarmQueue.value.toList()
        all.forEach { handleStopSingle(it.machineId, showCompletion) }
    }

    private fun handleStopSingle(machineId: Int, showCompletion: Boolean) {
        val removed: AlarmEntry?
        synchronized(_alarmQueue) {
            val current = _alarmQueue.value.toMutableList()
            removed = current.firstOrNull { it.machineId == machineId }
            if (removed != null) {
                current.remove(removed)
                _alarmQueue.value = current
                _alarmingMachineIds.value = current.map { it.machineId }.toSet()
            }
        }

        if (removed != null) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(machineId)

            if (showCompletion) {
                showCompletionNotification(this, removed.title, removed.message, machineId)
            }
        }

        val remaining = _alarmQueue.value
        if (remaining.isEmpty()) {
            releasePlayer()
            releaseScreenWakeLock()
            stopHandler.removeCallbacksAndMessages(null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateForegroundNotification(remaining.first())
        }
    }

    private fun showAlarmNotification(entry: AlarmEntry) {
        val notification = buildAlarmNotification(this, entry.title, entry.message, entry.machineId)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(entry.machineId, notification)

        val isFirst = _alarmQueue.value.firstOrNull()?.machineId == entry.machineId
        if (isFirst) {
            startAsForegroundService(entry.machineId, notification)
        }
    }

    private fun updateForegroundNotification(entry: AlarmEntry) {
        val notification = buildAlarmNotification(this, entry.title, entry.message, entry.machineId)
        startAsForegroundService(entry.machineId, notification)
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

        runCatching {
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
    }

    private fun resetAutoStopTimer() {
        stopHandler.removeCallbacksAndMessages(null)
        stopHandler.postDelayed({
            val remaining = _alarmQueue.value.toList()
            remaining.forEach { handleStopSingle(it.machineId, showCompletion = true) }
        }, AUTO_STOP_AFTER_MS)
    }

    private fun releasePlayer() {
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    private fun stopSelfIfEmpty() {
        if (_alarmQueue.value.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        private const val AUTO_STOP_AFTER_MS = 120_000L
        private const val SCREEN_WAKE_TIMEOUT_MS = 30_000L

        @Volatile
        private var _serviceRunning = false

        private val _alarmQueue = MutableStateFlow<List<AlarmEntry>>(emptyList())
        val alarmQueue: StateFlow<List<AlarmEntry>> = _alarmQueue

        private val _alarmingMachineIds = MutableStateFlow<Set<Int>>(emptySet())
        val alarmingMachineIds: StateFlow<Set<Int>> = _alarmingMachineIds

        fun clearStaleState() {
            if (!_serviceRunning) {
                _alarmQueue.value = emptyList()
                _alarmingMachineIds.value = emptySet()
            }
        }
    }
}
