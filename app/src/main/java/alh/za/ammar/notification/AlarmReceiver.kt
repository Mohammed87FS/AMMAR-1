package alh.za.ammar.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import alh.za.ammar.R
import alh.za.ammar.model.Machine
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        createNotificationChannel(context)

        val machineJson = intent.getStringExtra("machine_json")
        val currentDrop = intent.getIntExtra("current_drop", 0)

        if (machineJson == null || currentDrop == 0) {
            return
        }

        val gson = Gson()
        val machine = gson.fromJson(machineJson, Machine::class.java)
        val numberOfDrops = if (machine.productsPerDrop > 0) machine.totalProducts / machine.productsPerDrop else 0

        val title = context.getString(R.string.drop_complete_notification_title, machine.name)
        val message = context.getString(R.string.drop_complete_notification_message, currentDrop, numberOfDrops)
        showNotification(context, title, message)

        if (currentDrop < numberOfDrops) {
            scheduleNextAlarm(context, machine, currentDrop + 1)
        } else {
            val finalTitle = context.getString(R.string.machine_finished_notification_title, machine.name)
            val finalMessage = context.getString(R.string.machine_finished_notification_message, numberOfDrops)
            showNotification(context, finalTitle, finalMessage)
        }
    }

    private fun scheduleNextAlarm(context: Context, machine: Machine, nextDrop: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        val timePerDrop = (machine.timePerDropInSeconds * 1000).toLong()
        val triggerTime = System.currentTimeMillis() + timePerDrop

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("machine_json", Gson().toJson(machine))
            putExtra("current_drop", nextDrop)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            machine.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }
}
