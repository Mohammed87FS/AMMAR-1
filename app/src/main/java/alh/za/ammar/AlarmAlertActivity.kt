package alh.za.ammar

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import alh.za.ammar.notification.AlarmEntry
import alh.za.ammar.notification.AlarmSoundService
import alh.za.ammar.notification.stopActiveAlarm
import alh.za.ammar.ui.theme.AMMARTheme

class AlarmAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        enableEdgeToEdge()

        setContent {
            AMMARTheme {
                val queue by AlarmSoundService.alarmQueue.collectAsState()

                if (queue.isEmpty()) {
                    LaunchedEffect(Unit) {
                        finish()
                    }
                    return@AMMARTheme
                }

                val currentAlarm = queue.first()
                val remainingCount = queue.size

                AlarmAlertContent(
                    machineName = extractMachineName(currentAlarm.title),
                    subtitle = currentAlarm.message,
                    remainingCount = remainingCount,
                    onStopAlarm = {
                        stopActiveAlarm(
                            context = applicationContext,
                            notificationId = currentAlarm.machineId,
                            showCompletionNotification = true
                        )
                    },
                    onShowMachines = {
                        startActivity(Intent(this@AlarmAlertActivity, LockScreenMachinesActivity::class.java))
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        }
    }

    private fun extractMachineName(title: String): String {
        val suffix = getString(R.string.machine_finished_suffix)
        return if (title.endsWith(suffix)) {
            title.removeSuffix(suffix).trim()
        } else {
            title
        }
    }
}

@Composable
private fun AlarmAlertContent(
    machineName: String,
    subtitle: String,
    remainingCount: Int,
    onStopAlarm: () -> Unit,
    onShowMachines: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFD32F2F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            Text(
                text = "ALARM",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.weight(0.35f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = machineName,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 58.sp
                )
            }

            Text(
                text = subtitle,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            if (remainingCount > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.more_alarms_pending, remainingCount - 1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(0.15f))

            Button(
                onClick = onStopAlarm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFFD32F2F)
                )
            ) {
                Text(
                    text = stringResource(R.string.stop_alarm),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onShowMachines,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(
                    text = stringResource(R.string.show_machines),
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.weight(0.1f))
        }
    }
}
