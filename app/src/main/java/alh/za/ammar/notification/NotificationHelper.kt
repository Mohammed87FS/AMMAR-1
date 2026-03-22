package alh.za.ammar.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import alh.za.ammar.AlarmAlertActivity
import alh.za.ammar.LockScreenMachinesActivity
import alh.za.ammar.MainActivity
import alh.za.ammar.R
import alh.za.ammar.model.Machine
import alh.za.ammar.model.finishedAtMillis
import alh.za.ammar.model.formatClockTime
import alh.za.ammar.model.isFinished
import java.util.Locale

const val STATUS_CHANNEL_ID = "machine_status_channel_v1"
const val ALARM_CHANNEL_ID = "machine_alarm_channel_v2"
const val ACTION_START_ALARM = "alh.za.ammar.notification.action.START_ALARM"
const val ACTION_STOP_ALARM = "alh.za.ammar.notification.action.STOP_ALARM"
const val ACTION_PAUSE_MACHINE = "alh.za.ammar.notification.action.PAUSE_MACHINE"
const val ACTION_RESUME_MACHINE = "alh.za.ammar.notification.action.RESUME_MACHINE"
const val EXTRA_MACHINE_JSON = "machine_json"
const val EXTRA_MACHINE_ID = "machine_id"
const val EXTRA_ALARM_TITLE = "alarm_title"
const val EXTRA_ALARM_MESSAGE = "alarm_message"
const val EXTRA_NOTIFICATION_ID = "alarm_notification_id"
const val EXTRA_SHOW_COMPLETION_NOTIFICATION = "show_completion_notification"
const val EXTRA_MACHINE_NAME = "machine_name"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            context.getString(R.string.status_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.status_notification_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(statusChannel)
        notificationManager.createNotificationChannel(alarmChannel)
    }
}

fun syncMachineStatusNotifications(context: Context, machines: List<Machine>) {
    createNotificationChannel(context)
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val activeMachineIds = machines.filterNot { it.isFinished() }.map { it.id }.toSet()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        notificationManager.activeNotifications
            .filter { it.notification.channelId == STATUS_CHANNEL_ID && it.id !in activeMachineIds }
            .forEach { notificationManager.cancel(it.id) }
    }

    machines.forEach { machine ->
        if (!machine.isFinished()) {
            showMachineStatusNotification(context, machine)
        }
    }
}

fun cancelMachineNotification(context: Context, machineId: Int) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(machineId)
}

fun buildAlarmNotification(
    context: Context,
    title: String,
    message: String,
    notificationId: Int
): Notification {
    val alarmAlertPendingIntent =
        createAlarmAlertPendingIntent(context, title, message, notificationId)

    return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setColor(0xFFD32F2F.toInt())
        .setColorized(true)
        .setContentTitle("🔴 $title 🔴")
        .setContentText("⚠️ ALARM — Maschine ist fertig!")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(alarmAlertPendingIntent)
        .setFullScreenIntent(alarmAlertPendingIntent, true)
        .addAction(
            0,
            context.getString(R.string.stop_alarm),
            createStopAlarmPendingIntent(context, notificationId)
        )
        .setOngoing(true)
        .setAutoCancel(false)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()
}

fun showCompletionNotification(context: Context, title: String, message: String, notificationId: Int) {
    val builder = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setColor(0xFFD32F2F.toInt())
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(createLaunchAppPendingIntent(context))
        .setAutoCancel(true)
        .setOngoing(false)
        .setOnlyAlertOnce(true)
        .setSilent(true)

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(notificationId, builder.build())
}

fun stopActiveAlarm(
    context: Context,
    notificationId: Int? = null,
    showCompletionNotification: Boolean = true
) {
    val intent = Intent(context, AlarmSoundService::class.java).apply {
        action = ACTION_STOP_ALARM
        notificationId?.let { putExtra(EXTRA_NOTIFICATION_ID, it) }
        putExtra(EXTRA_SHOW_COMPLETION_NOTIFICATION, showCompletionNotification)
    }

    runCatching { context.startService(intent) }
}

fun getAlarmSoundUri(context: Context): Uri {
    val rawAlarmId = context.resources.getIdentifier("alarm", "raw", context.packageName)
    if (rawAlarmId != 0) {
        return Uri.parse("android.resource://${context.packageName}/$rawAlarmId")
    }

    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ?: Uri.parse("content://settings/system/alarm_alert")
}

private fun showMachineStatusNotification(context: Context, machine: Machine) {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(machine.id, buildMachineStatusNotification(context, machine))
}

private fun buildMachineStatusNotification(context: Context, machine: Machine): Notification {
    val contentText = if (machine.isStopped) {
        context.getString(R.string.machine_paused_notification_message)
    } else {
        context.getString(
            R.string.machine_running_notification_message,
            formatClockTime(machine.finishedAtMillis(), Locale.GERMANY)
        )
    }

    val builder = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setColor(0xFF4CAF50.toInt())
        .setContentTitle(machine.name)
        .setContentText(contentText)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(createLockScreenOverviewPendingIntent(context))
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setAutoCancel(false)
        .setSilent(true)

    if (machine.isStopped) {
        builder.addAction(
            0,
            context.getString(R.string.resume),
            createMachineActionPendingIntent(context, ACTION_RESUME_MACHINE, machine.id)
        )
    } else {
        builder.addAction(
            0,
            context.getString(R.string.pause_action),
            createMachineActionPendingIntent(context, ACTION_PAUSE_MACHINE, machine.id)
        )
    }

    return builder.build()
}

private fun createLaunchAppPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun createLockScreenOverviewPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, LockScreenMachinesActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
    }

    return PendingIntent.getActivity(
        context,
        10_001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun createAlarmAlertPendingIntent(
    context: Context,
    title: String,
    message: String,
    notificationId: Int
): PendingIntent {
    val intent = Intent(context, AlarmAlertActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    return PendingIntent.getActivity(
        context,
        20_000,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun createMachineActionPendingIntent(
    context: Context,
    action: String,
    machineId: Int
): PendingIntent {
    val intent = Intent(context, MachineActionReceiver::class.java).apply {
        this.action = action
        putExtra(EXTRA_MACHINE_ID, machineId)
    }

    return PendingIntent.getBroadcast(
        context,
        machineId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun createStopAlarmPendingIntent(context: Context, notificationId: Int): PendingIntent {
    val intent = Intent(context, AlarmSoundService::class.java).apply {
        action = ACTION_STOP_ALARM
        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        putExtra(EXTRA_SHOW_COMPLETION_NOTIFICATION, true)
    }

    return PendingIntent.getService(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
