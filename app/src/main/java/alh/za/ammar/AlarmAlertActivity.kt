package alh.za.ammar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import alh.za.ammar.R
import alh.za.ammar.notification.EXTRA_ALARM_MESSAGE
import alh.za.ammar.notification.EXTRA_ALARM_TITLE
import alh.za.ammar.notification.EXTRA_NOTIFICATION_ID
import alh.za.ammar.notification.stopActiveAlarm
import alh.za.ammar.ui.theme.AMMARTheme

class AlarmAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        enableEdgeToEdge()

        val title = intent.getStringExtra(EXTRA_ALARM_TITLE).orEmpty()
        val message = intent.getStringExtra(EXTRA_ALARM_MESSAGE).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        setContent {
            AMMARTheme {
                AlarmAlertContent(
                    title = title,
                    message = message,
                    onStopAlarm = {
                        stopActiveAlarm(
                            context = applicationContext,
                            notificationId = notificationId,
                            showCompletionNotification = true
                        )
                        finish()
                    },
                    onShowMachines = {
                        startActivity(Intent(this, LockScreenMachinesActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

@Composable
private fun AlarmAlertContent(
    title: String,
    message: String,
    onStopAlarm: () -> Unit,
    onShowMachines: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp)
            )
            Button(
                onClick = onStopAlarm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.stop_alarm))
            }
            OutlinedButton(
                onClick = onShowMachines,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.show_machines))
            }
        }
    }
}
