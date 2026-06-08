package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "git_settings")

class SettingsRepository(private val context: Context) {
    private val USERNAME_KEY = stringPreferencesKey("github_username")
    private val TOKEN_KEY = stringPreferencesKey("github_token")

    val username: Flow<String> = context.dataStore.data.map { it[USERNAME_KEY] ?: "" }
    val token: Flow<String> = context.dataStore.data.map { it[TOKEN_KEY] ?: "" }

    suspend fun saveSettings(user: String, tok: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = user
            prefs[TOKEN_KEY] = tok
        }
    }
}
