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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** 应用设置（DataStore，位于应用私有目录） */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vnventory_settings")

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val defaultCurrency: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_DEFAULT_CURRENCY] ?: FALLBACK_CURRENCY }

    suspend fun setDefaultCurrency(code: String) {
        dataStore.edit { prefs -> prefs[KEY_DEFAULT_CURRENCY] = Money.normalize(code) }
    }

    /** 备份不能把读取失败悄悄当作默认配置；UI 的容错 Flow 仍保持原行为。 */
    suspend fun getDefaultCurrency(): String = dataStore.data.first()[KEY_DEFAULT_CURRENCY] ?: FALLBACK_CURRENCY

    companion object {
        const val FALLBACK_CURRENCY = "CNY"
        private val KEY_DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
    }
}
