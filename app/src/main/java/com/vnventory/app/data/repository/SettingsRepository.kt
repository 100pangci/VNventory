package com.vnventory.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vnventory.app.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** 应用设置（DataStore，位于应用私有目录） */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vnventory_settings")

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val defaultCurrency: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) } // 读失败时回退默认值，不崩溃
        .map { prefs -> prefs[KEY_DEFAULT_CURRENCY] ?: FALLBACK_CURRENCY }

    suspend fun setDefaultCurrency(code: String) {
        dataStore.edit { prefs -> prefs[KEY_DEFAULT_CURRENCY] = Money.normalize(code) }
    }

    companion object {
        const val FALLBACK_CURRENCY = "CNY"
        private val KEY_DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }
}
