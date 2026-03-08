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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import alh.za.ammar.R
import alh.za.ammar.model.Machine
import alh.za.ammar.model.finishedAtMillis
import alh.za.ammar.model.formatClockTime
import alh.za.ammar.model.isFinished
import alh.za.ammar.notification.cancelMachineFinishedAlarm
import alh.za.ammar.notification.stopActiveAlarm
import alh.za.ammar.notification.syncMachineStatusNotifications
import alh.za.ammar.ui.theme.AMMARTheme
import alh.za.ammar.viewmodel.MachinesViewModel
import java.util.Locale

class LockScreenMachinesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        enableEdgeToEdge()

        setContent {
            AMMARTheme {
                val viewModel: MachinesViewModel = viewModel(
                    factory = MachinesViewModel.Factory(application)
                )
                LockScreenMachinesContent(
                    viewModel = viewModel,
                    appContext = applicationContext,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onClose = { finish() }
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
private fun LockScreenMachinesContent(
    viewModel: MachinesViewModel,
    appContext: android.content.Context,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    val machines by viewModel.machines.collectAsState()
    val activeMachines = machines.filterNot { it.isFinished() }

    LaunchedEffect(machines) {
        syncMachineStatusNotifications(appContext, machines)
        machines.forEach { machine ->
            when {
                machine.isStopped -> cancelMachineFinishedAlarm(appContext, machine.id)
                machine.isFinished() -> Unit
                else -> alh.za.ammar.notification.scheduleMachineFinishedAlarm(appContext, machine)
            }
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.lock_screen_overview_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.lock_screen_overview_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (activeMachines.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.lock_screen_empty_state),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeMachines, key = { it.id }) { machine ->
                        LockScreenMachineCard(
                            machine = machine,
                            onPause = {
                                stopActiveAlarm(appContext, machine.id, false)
                                viewModel.stopMachine(machine)
                                cancelMachineFinishedAlarm(appContext, machine.id)
                            },
                            onResume = {
                                stopActiveAlarm(appContext, machine.id, false)
                                viewModel.resumeMachine(machine)
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.close))
                }
                Button(
                    onClick = onOpenApp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.open_app))
                }
            }
        }
    }
}

@Composable
private fun LockScreenMachineCard(
    machine: Machine,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = machine.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (machine.isStopped) stringResource(R.string.status_paused) else stringResource(R.string.status_running),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (machine.isStopped) {
                    stringResource(R.string.end_time_after_resume)
                } else {
                    "${stringResource(R.string.end_time_label)} ${formatClockTime(machine.finishedAtMillis(), Locale.GERMANY)}"
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (machine.isStopped) {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.resume))
                }
            } else {
                OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pause_action))
                }
            }
        }
    }
}
