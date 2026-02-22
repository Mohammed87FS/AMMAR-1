package alh.za.ammar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import alh.za.ammar.model.Machine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "machines")

class MachineRepository(private val context: Context) {

    private val gson = Gson()
    private val machinesKey = stringPreferencesKey("machines")

    val machines: Flow<List<Machine>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[machinesKey] ?: "[]"
            val type = object : TypeToken<List<Machine>>() {}.type
            gson.fromJson(json, type)
        }

    suspend fun saveMachines(machines: List<Machine>) {
        context.dataStore.edit {
            it[machinesKey] = gson.toJson(machines)
        }
    }
}
