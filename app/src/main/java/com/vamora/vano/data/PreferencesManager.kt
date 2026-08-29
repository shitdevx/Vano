package com.vamora.vano.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vano_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model_file")
        private val KEY_ACTIVE_CHAT = stringPreferencesKey("active_chat_id")
    }

    val selectedModelFile: Flow<String?> = context.dataStore.data.map { it[KEY_SELECTED_MODEL] }

    suspend fun setSelectedModel(fileName: String?) {
        context.dataStore.edit { prefs ->
            if (fileName == null) prefs.remove(KEY_SELECTED_MODEL)
            else prefs[KEY_SELECTED_MODEL] = fileName
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
