package alh.za.ammar.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import alh.za.ammar.model.Machine
import alh.za.ammar.model.finishedAtMillis
import com.google.gson.Gson

fun scheduleMachineFinishedAlarm(context: Context, machine: Machine) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        return
    }
    if (machine.isStopped) {
        return
    }

    val triggerTime = machine.finishedAtMillis()
    if (triggerTime <= System.currentTimeMillis()) {
        return
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(EXTRA_MACHINE_JSON, Gson().toJson(machine))
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        machine.id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
        alarmManager.setAlarmClock(alarmInfo, pendingIntent)
    } else {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }
}

fun cancelMachineFinishedAlarm(context: Context, machineId: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        machineId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}
