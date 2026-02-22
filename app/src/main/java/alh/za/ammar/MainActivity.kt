package alh.za.ammar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import alh.za.ammar.ui.screens.machines.MachinesScreen
import alh.za.ammar.ui.theme.AMMARTheme
import alh.za.ammar.viewmodel.MachinesViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AMMARTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel: MachinesViewModel = viewModel(
                        factory = MachinesViewModel.Factory(application)
                    )
                    MachinesScreen(viewModel, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
