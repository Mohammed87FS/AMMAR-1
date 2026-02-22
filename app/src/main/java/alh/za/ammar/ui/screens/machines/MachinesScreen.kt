package alh.za.ammar.ui.screens.machines

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import alh.za.ammar.R
import alh.za.ammar.model.Machine
import alh.za.ammar.notification.AlarmReceiver
import alh.za.ammar.ui.theme.Orange
import alh.za.ammar.viewmodel.MachinesViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.gson.Gson
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun MachinesScreen(viewModel: MachinesViewModel, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        MachinesScreenApi33AndAbove(viewModel, modifier)
    } else {
        MachinesScreenBelowApi33(viewModel, modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MachinesScreenApi33AndAbove(viewModel: MachinesViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val notificationPermissionState = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)

    var canScheduleExactAlarms by remember {
        mutableStateOf(alarmManager.canScheduleExactAlarms())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !notificationPermissionState.status.isGranted -> {
            PermissionRequestScreen(
                modifier = modifier,
                onGrantClick = { notificationPermissionState.launchPermissionRequest() }
            )
        }

        !canScheduleExactAlarms -> {
            ExactAlarmPermissionRequestScreen(modifier, context)
        }

        else -> {
            MainContent(viewModel, modifier)
        }
    }
}

@Composable
private fun MachinesScreenBelowApi33(viewModel: MachinesViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    var canScheduleExactAlarms by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (canScheduleExactAlarms) {
        MainContent(viewModel, modifier)
    } else {
        ExactAlarmPermissionRequestScreen(modifier, context)
    }
}

@Composable
private fun PermissionRequestScreen(modifier: Modifier = Modifier, onGrantClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.notification_permission_request),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onGrantClick) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
private fun ExactAlarmPermissionRequestScreen(modifier: Modifier = Modifier, context: Context) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.exact_alarm_permission_request),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                    it.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(it)
                }
            }
        }) {
            Text(stringResource(R.string.open_settings))
        }
    }
}

@Composable
private fun MainContent(viewModel: MachinesViewModel, modifier: Modifier = Modifier) {
    val machines by viewModel.machines.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AddMachineForm(onAddMachine = {
            viewModel.addMachine(it)
            scheduleFinalAlarm(context, it)
        })
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(machines) { machine ->
                MachineItem(
                    machine = machine, 
                    onRemove = {
                        viewModel.removeMachine(machine)
                        cancelAlarm(context, machine)
                    },
                    onReactivate = {
                        viewModel.reactivateMachine(machine)
                        scheduleFinalAlarm(context, machine)
                    },
                    onStop = { viewModel.stopMachine(machine) },
                    onResume = { viewModel.resumeMachine(machine) }
                )
            }
        }
    }
}

@Composable
private fun AddMachineForm(onAddMachine: (Machine) -> Unit) {
    var machineName by remember { mutableStateOf("") }
    var totalProducts by remember { mutableStateOf("") }
    var productsPerDrop by remember { mutableStateOf("") }
    var timePerDropInSeconds by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = machineName,
            onValueChange = { machineName = it },
            label = { Text(stringResource(R.string.machine_name)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = totalProducts,
            onValueChange = { totalProducts = it },
            label = { Text(stringResource(R.string.total_products)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = productsPerDrop,
            onValueChange = { productsPerDrop = it },
            label = { Text(stringResource(R.string.products_per_drop)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = timePerDropInSeconds,
            onValueChange = { timePerDropInSeconds = it },
            label = { Text(stringResource(R.string.time_per_drop_in_seconds)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Button(
            onClick = {
                val totalProductsInt = totalProducts.toIntOrNull() ?: 0
                val productsPerDropInt = productsPerDrop.toIntOrNull() ?: 0
                val timePerDropInSecondsDouble = timePerDropInSeconds.toDoubleOrNull() ?: 0.0

                if (machineName.isNotBlank() && totalProductsInt > 0 && productsPerDropInt > 0 && timePerDropInSecondsDouble > 0) {
                    onAddMachine(
                        Machine(
                            name = machineName,
                            totalProducts = totalProductsInt,
                            productsPerDrop = productsPerDropInt,
                            timePerDropInSeconds = timePerDropInSecondsDouble
                        )
                    )
                    machineName = ""
                    totalProducts = ""
                    productsPerDrop = ""
                    timePerDropInSeconds = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_machine))
        }
    }
}

@Composable
private fun MachineItem(
    machine: Machine,
    onRemove: () -> Unit,
    onReactivate: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit
) {
    val numberOfDrops = if (machine.productsPerDrop > 0) machine.totalProducts / machine.productsPerDrop else 0
    val timePerDrop = (machine.timePerDropInSeconds * 1000).toLong()
    val totalTime = numberOfDrops * timePerDrop

    var timeRemaining by remember(machine) { mutableLongStateOf(totalTime) }

    LaunchedEffect(key1 = machine) {
        if (!machine.isStopped) {
            val elapsedTime = if (machine.stoppedAt != null) {
                machine.stoppedAt - machine.createdAt
            } else {
                System.currentTimeMillis() - machine.createdAt
            }
            timeRemaining = (totalTime - elapsedTime).coerceAtLeast(0)
            while (timeRemaining > 0) {
                delay(1000)
                val newElapsedTime = if (machine.stoppedAt != null) {
                    machine.stoppedAt - machine.createdAt
                } else {
                    System.currentTimeMillis() - machine.createdAt
                }
                timeRemaining = (totalTime - newElapsedTime).coerceAtLeast(0)
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(machine.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    Button(
                        onClick = onRemove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text(stringResource(R.string.remove))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (timeRemaining <= 0) {
                        Button(
                            onClick = onReactivate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                        ) {
                            Text(stringResource(R.string.reactivate))
                        }
                    } else if (machine.isStopped) {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                        ) {
                            Text(stringResource(R.string.resume))
                        }
                    } else {
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)
                        ) {
                            Text(stringResource(R.string.stop))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.total_products_label))
                Text(machine.totalProducts.toString())
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.products_per_drop_label))
                Text(machine.productsPerDrop.toString())
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.time_per_drop_label))
                Text(formatMillis(timePerDrop))
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.number_of_drops_label))
                Text(numberOfDrops.toString())
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.total_time_label))
                Text(formatMillis(totalTime))
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.time_remaining_label))
                Text(if (timeRemaining > 0) formatMillis(timeRemaining) else stringResource(R.string.finished))
            }
        }
    }
}

private fun scheduleFinalAlarm(context: Context, machine: Machine) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        return
    }

    val numberOfDrops = if (machine.productsPerDrop > 0) machine.totalProducts / machine.productsPerDrop else 0
    val timePerDrop = (machine.timePerDropInSeconds * 1000).toLong()
    val totalTime = numberOfDrops * timePerDrop
    val triggerTime = System.currentTimeMillis() + totalTime

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("machine_json", Gson().toJson(machine))
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        machine.id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
}

private fun cancelAlarm(context: Context, machine: Machine) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        machine.id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}

private fun formatMillis(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun MachinesScreenPreview() {
    // This preview will not work correctly because it does not have the ViewModel
    // or the permission handling logic that is part of the main screen.
}
