package alh.za.ammar.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import alh.za.ammar.data.MachineRepository
import alh.za.ammar.model.Machine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MachinesViewModel(application: Application) : ViewModel() {

    private val repository = MachineRepository(application)

    val machines: StateFlow<List<Machine>> = repository.machines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMachine(machine: Machine) {
        viewModelScope.launch {
            repository.saveMachines(machines.value + machine)
        }
    }

    fun removeMachine(machine: Machine) {
        viewModelScope.launch {
            repository.saveMachines(machines.value - machine)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MachinesViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MachinesViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
