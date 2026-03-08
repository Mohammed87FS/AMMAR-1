package alh.za.ammar.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import alh.za.ammar.R
import alh.za.ammar.model.Machine
import com.google.gson.Gson

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        createNotificationChannel(context)

        val machineJson = intent.getStringExtra("machine_json") ?: return

        val gson = Gson()
        val machine = gson.fromJson(machineJson, Machine::class.java)
        val numberOfDrops = if (machine.productsPerDrop > 0) machine.totalProducts / machine.productsPerDrop else 0

        val finalTitle = context.getString(R.string.machine_finished_notification_title, machine.name)
        val finalMessage = context.getString(R.string.machine_finished_notification_message, numberOfDrops)
        
        // Pass the machine's unique ID to allow multiple distinct notifications
        showNotification(context, machine.id, finalTitle, finalMessage)
    }
}
