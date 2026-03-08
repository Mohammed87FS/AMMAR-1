package alh.za.ammar.ui.screens.machines

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import alh.za.ammar.R
import alh.za.ammar.model.Machine
import alh.za.ammar.model.cycleCount
import alh.za.ammar.model.cycleDurationMillis
import alh.za.ammar.model.finishedAtMillis
import alh.za.ammar.model.formatClockTime
import alh.za.ammar.model.isFinished
import alh.za.ammar.model.totalDurationMillis
import alh.za.ammar.notification.cancelMachineFinishedAlarm
import alh.za.ammar.notification.cancelMachineNotification
import alh.za.ammar.notification.stopActiveAlarm
import alh.za.ammar.notification.syncMachineStatusNotifications
import alh.za.ammar.ui.theme.Orange
import alh.za.ammar.viewmodel.MachinesViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.floor
import kotlin.math.roundToLong
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

    LaunchedEffect(machines) {
        syncMachineStatusNotifications(context, machines)
        machines.forEach { machine ->
            when {
                machine.isStopped -> cancelMachineFinishedAlarm(context, machine.id)
                machine.isFinished() -> Unit
                else -> alh.za.ammar.notification.scheduleMachineFinishedAlarm(context, machine)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            AddMachineForm(onAddMachine = {
                viewModel.addMachine(it)
            })
        }

        if (machines.isEmpty()) {
            item {
                EmptyMachinesCard()
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.machine_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(machines, key = { it.id }) { machine ->
                MachineItem(
                    machine = machine,
                    onRemove = {
                        stopActiveAlarm(context, machine.id, showCompletionNotification = false)
                        viewModel.removeMachine(machine)
                        cancelMachineFinishedAlarm(context, machine.id)
                        cancelMachineNotification(context, machine.id)
                    },
                    onReactivate = {
                        stopActiveAlarm(context, machine.id, showCompletionNotification = false)
                        viewModel.reactivateMachine(machine)
                    },
                    onStop = {
                        stopActiveAlarm(context, machine.id, showCompletionNotification = false)
                        viewModel.stopMachine(machine)
                        cancelMachineFinishedAlarm(context, machine.id)
                    },
                    onResume = {
                        stopActiveAlarm(context, machine.id, showCompletionNotification = false)
                        viewModel.resumeMachine(machine)
                    }
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
    var showValidationError by remember { mutableStateOf(false) }
    val totalProductsInt = totalProducts.toIntOrNull() ?: 0
    val productsPerDropInt = productsPerDrop.toIntOrNull() ?: 0
    val timePerDropInSecondsDouble = parseLocalizedDecimal(timePerDropInSeconds) ?: 0.0
    val canShowPreview =
        totalProductsInt > 0 && productsPerDropInt > 0 && timePerDropInSecondsDouble > 0.0
    val previewCycles = calculateCycleCount(totalProductsInt, productsPerDropInt)
    val previewTotalTime = previewCycles * (timePerDropInSecondsDouble * 1000).roundToLong()

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.new_machine_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.new_machine_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = machineName,
                onValueChange = {
                    machineName = it
                    showValidationError = false
                },
                label = { Text(stringResource(R.string.machine_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = totalProducts,
                    onValueChange = {
                        totalProducts = it
                        showValidationError = false
                    },
                    label = { Text(stringResource(R.string.total_products)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = productsPerDrop,
                    onValueChange = {
                        productsPerDrop = it
                        showValidationError = false
                    },
                    label = { Text(stringResource(R.string.products_per_drop)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = timePerDropInSeconds,
                onValueChange = {
                    timePerDropInSeconds = sanitizeDecimalInput(it)
                    showValidationError = false
                },
                label = { Text(stringResource(R.string.time_per_drop_in_seconds)) },
                supportingText = { Text(stringResource(R.string.time_per_drop_hint)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            if (canShowPreview) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.preview_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        MachineInfoRow(
                            label = stringResource(R.string.preview_cycles_label),
                            value = previewCycles.toString()
                        )
                        MachineInfoRow(
                            label = stringResource(R.string.preview_total_time_label),
                            value = formatDuration(previewTotalTime)
                        )
                    }
                }
            }

            Button(
                onClick = {
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
                        showValidationError = false
                    } else {
                        showValidationError = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_machine))
            }

            if (showValidationError) {
                Text(
                    text = stringResource(R.string.error_all_fields_positive),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
    val numberOfCycles = machine.cycleCount()
    val timePerCycle = machine.cycleDurationMillis()
    val totalTime = machine.totalDurationMillis()
    val expectedProducts15Min = calculateExpectedProducts(machine.totalProducts, machine.productsPerDrop, machine.timePerDropInSeconds, 15)
    val expectedProducts30Min = calculateExpectedProducts(machine.totalProducts, machine.productsPerDrop, machine.timePerDropInSeconds, 30)
    val expectedProducts60Min = calculateExpectedProducts(machine.totalProducts, machine.productsPerDrop, machine.timePerDropInSeconds, 60)
    val isFinishedNow = machine.isFinished()

    val statusLabel = when {
        isFinishedNow -> stringResource(R.string.status_finished)
        machine.isStopped -> stringResource(R.string.status_paused)
        else -> stringResource(R.string.status_running)
    }
    val statusContainerColor = when {
        isFinishedNow -> MaterialTheme.colorScheme.primaryContainer
        machine.isStopped -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val statusContentColor = when {
        isFinishedNow -> MaterialTheme.colorScheme.onPrimaryContainer
        machine.isStopped -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = machine.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = statusContainerColor,
                    contentColor = statusContentColor,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Text(stringResource(R.string.remove))
                }

                when {
                    isFinishedNow -> {
                        Button(
                            onClick = onReactivate,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text(stringResource(R.string.reactivate))
                        }
                    }

                    machine.isStopped -> {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text(stringResource(R.string.resume))
                        }
                    }

                    else -> {
                        Button(
                            onClick = onStop,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)
                        ) {
                            Text(stringResource(R.string.stop))
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MachineInfoRow(
                        label = stringResource(R.string.total_products_label),
                        value = machine.totalProducts.toString()
                    )
                    MachineInfoRow(
                        label = stringResource(R.string.products_per_drop_label),
                        value = machine.productsPerDrop.toString()
                    )
                    MachineInfoRow(
                        label = stringResource(R.string.time_per_drop_label),
                        value = formatCycleTimeDetails(machine.timePerDropInSeconds)
                    )
                    MachineInfoRow(
                        label = stringResource(R.string.number_of_drops_label),
                        value = numberOfCycles.toString()
                    )
                    MachineInfoRow(
                        label = stringResource(R.string.total_time_label),
                        value = formatDuration(totalTime)
                    )
                    MachineInfoRow(
                        label = stringResource(R.string.end_time_label),
                        value = if (machine.isStopped) {
                            stringResource(R.string.end_time_after_resume)
                        } else {
                            formatClockTime(machine.finishedAtMillis(), Locale.GERMANY)
                        }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.forecast_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    MachineInfoRow(
                        label = stringResource(R.string.forecast_15_min_label),
                        value = stringResource(R.string.expected_products_value, expectedProducts15Min)
                    )
                    MachineInfoRow(
                        label = stringResource(R.string.forecast_30_min_label),
                        value = stringResource(R.string.expected_products_value, expectedProducts30Min)
                    )
                    MachineInfoRow(
                        label = stringResource(R.string.forecast_60_min_label),
                        value = stringResource(R.string.expected_products_value, expectedProducts60Min)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMachinesCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.empty_state_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.empty_state_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MachineInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

private fun formatDuration(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0)
    val hours = TimeUnit.MILLISECONDS.toHours(safeMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMillis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMillis) % 60
    val millisRemainder = safeMillis % 1000

    if (millisRemainder == 0L) {
        return String.format(Locale.GERMANY, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    val fraction = millisRemainder.toString().padStart(3, '0').trimEnd('0')
    return String.format(Locale.GERMANY, "%02d:%02d:%02d,%s", hours, minutes, seconds, fraction)
}

private fun formatLocalizedSeconds(seconds: Double): String {
    val symbols = DecimalFormatSymbols(Locale.GERMANY)
    val formatter = DecimalFormat("0.###", symbols)
    return "${formatter.format(seconds)} Sek."
}

private fun formatCycleTimeDetails(seconds: Double): String {
    val millis = (seconds * 1000).roundToLong()
    return "${formatLocalizedSeconds(seconds)} (${formatDuration(millis)})"
}

private fun sanitizeDecimalInput(input: String): String {
    val sanitized = StringBuilder()
    var hasSeparator = false

    input.forEach { character ->
        when {
            character.isDigit() -> sanitized.append(character)
            (character == ',' || character == '.') && !hasSeparator -> {
                if (sanitized.isEmpty()) {
                    sanitized.append('0')
                }
                sanitized.append(',')
                hasSeparator = true
            }
        }
    }

    return sanitized.toString()
}

private fun parseLocalizedDecimal(input: String): Double? {
    if (input.isBlank()) return null
    return input.replace(',', '.').toDoubleOrNull()
}

private fun calculateCycleCount(totalProducts: Int, productsPerDrop: Int): Int {
    if (totalProducts <= 0 || productsPerDrop <= 0) {
        return 0
    }

    return (totalProducts + productsPerDrop - 1) / productsPerDrop
}

private fun calculateExpectedProducts(
    totalProducts: Int,
    productsPerDrop: Int,
    timePerDropInSeconds: Double,
    durationMinutes: Int
): Int {
    if (totalProducts <= 0 || productsPerDrop <= 0 || timePerDropInSeconds <= 0.0) {
        return 0
    }

    val completedDrops = floor((durationMinutes * 60) / timePerDropInSeconds).toInt()
    return (completedDrops * productsPerDrop).coerceAtMost(totalProducts)
}

@Preview(showBackground = true)
@Composable
fun MachinesScreenPreview() {
    // This preview will not work correctly because it does not have the ViewModel
    // or the permission handling logic that is part of the main screen.
}
