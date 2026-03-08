package alh.za.ammar.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import alh.za.ammar.R
import alh.za.ammar.model.Machine
import com.google.gson.Gson

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        createNotificationChannel(context)

        val machineJson = intent.getStringExtra(EXTRA_MACHINE_JSON) ?: return

        val gson = Gson()
        val machine = gson.fromJson(machineJson, Machine::class.java)
        val numberOfCycles = if (machine.productsPerDrop > 0) {
            (machine.totalProducts + machine.productsPerDrop - 1) / machine.productsPerDrop
        } else {
            0
        }

        val finalTitle = context.getString(R.string.machine_finished_notification_title, machine.name)
        val finalMessage = context.getString(R.string.machine_finished_notification_message, numberOfCycles)

        val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
            action = ACTION_START_ALARM
            putExtra(EXTRA_ALARM_TITLE, finalTitle)
            putExtra(EXTRA_ALARM_MESSAGE, finalMessage)
            putExtra(EXTRA_NOTIFICATION_ID, machine.id)
        }

        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }.onFailure {
            showCompletionNotification(context, finalTitle, finalMessage, machine.id)
        }
    }
}
