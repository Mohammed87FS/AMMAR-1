package alh.za.ammar.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import alh.za.ammar.data.MachineRepository
import alh.za.ammar.model.isFinished
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MachineActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val machineId = intent.getIntExtra(EXTRA_MACHINE_ID, -1)
        if (machineId == -1) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = MachineRepository(appContext)
                val currentMachines = repository.machines.first()
                val now = System.currentTimeMillis()

                val updatedMachines = when (intent.action) {
                    ACTION_PAUSE_MACHINE -> currentMachines.map { machine ->
                        if (machine.id == machineId && !machine.isStopped && !machine.isFinished(now)) {
                            machine.copy(isStopped = true, stoppedAt = now)
                        } else {
                            machine
                        }
                    }

                    ACTION_RESUME_MACHINE -> currentMachines.map { machine ->
                        if (machine.id == machineId && machine.isStopped && machine.stoppedAt != null) {
                            val stoppedDuration = now - machine.stoppedAt
                            machine.copy(
                                isStopped = false,
                                stoppedAt = null,
                                createdAt = machine.createdAt + stoppedDuration
                            )
                        } else {
                            machine
                        }
                    }

                    else -> currentMachines
                }

                repository.saveMachines(updatedMachines)

                val updatedMachine = updatedMachines.firstOrNull { it.id == machineId }
                when (intent.action) {
                    ACTION_PAUSE_MACHINE -> {
                        cancelMachineFinishedAlarm(appContext, machineId)
                    }

                    ACTION_RESUME_MACHINE -> {
                        if (updatedMachine != null) {
                            scheduleMachineFinishedAlarm(appContext, updatedMachine)
                        }
                    }
                }

                syncMachineStatusNotifications(appContext, updatedMachines)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
