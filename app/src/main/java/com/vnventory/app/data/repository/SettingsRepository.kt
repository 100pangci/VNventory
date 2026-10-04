package com.vnventory.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.ShopChannels
import com.vnventory.app.domain.text.MessageException
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** 应用设置（DataStore，位于应用私有目录） */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "vnventory_settings")

data class SettingsSnapshot(val defaultCurrency: String, val shopChannels: List<String>)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val defaultCurrency: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_DEFAULT_CURRENCY] ?: FALLBACK_CURRENCY }

    val shopChannels: Flow<List<String>> = dataStore.data.map { it.readShopChannels() }

    suspend fun addShopChannel(name: String) {
        val trimmed = name.trim()
        requireMessage(trimmed.isNotBlank()) { message(MessageKey.SHOP_NAME_REQUIRED) }
        dataStore.edit { prefs ->
            val names = prefs.readShopChannels()
            requireMessage(names.none { ShopChannels.sameName(it, trimmed) }) { message(MessageKey.SHOP_DUPLICATE) }
            prefs[KEY_SHOP_CHANNELS] = Json.encodeToString(names + trimmed)
        }
    }

    suspend fun renameShopChannel(original: String, name: String) {
        val trimmed = name.trim()
        requireMessage(trimmed.isNotBlank()) { message(MessageKey.SHOP_NAME_REQUIRED) }
        dataStore.edit { prefs ->
            val names = prefs.readShopChannels().toMutableList()
            val index = names.indexOf(original)
            requireMessage(index >= 0) { message(MessageKey.SHOP_MISSING) }
            requireMessage(names.withIndex().none { it.index != index && ShopChannels.sameName(it.value, trimmed) }) {
                message(MessageKey.SHOP_DUPLICATE)
            }
            names[index] = trimmed
            prefs[KEY_SHOP_CHANNELS] = Json.encodeToString(names)
        }
    }

    suspend fun removeShopChannel(name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOP_CHANNELS] = Json.encodeToString(prefs.readShopChannels().filterNot { it == name })
        }
    }

    /** Both preference fields restore atomically; neither changes if the DataStore write fails. */
    suspend fun restorePreferences(currency: String?, shops: List<String>?) {
        if (currency == null && shops == null) return
        shops?.let(ShopChannels::validate)
        dataStore.edit { prefs ->
            currency?.let { prefs[KEY_DEFAULT_CURRENCY] = Money.normalize(it) }
            shops?.let { prefs[KEY_SHOP_CHANNELS] = Json.encodeToString(it) }
        }
    }

    suspend fun snapshot(): SettingsSnapshot = dataStore.data.first().let {
        SettingsSnapshot(it[KEY_DEFAULT_CURRENCY] ?: FALLBACK_CURRENCY, it.readShopChannels())
    }

    private fun Preferences.readShopChannels(): List<String> {
        val encoded = this[KEY_SHOP_CHANNELS] ?: return emptyList()
        return try {
            Json.decodeFromString<List<String>>(encoded).also(ShopChannels::validate)
        } catch (e: SerializationException) {
            throw MessageException(message(MessageKey.SHOP_DATA_INVALID), e)
        }
    }

    suspend fun setDefaultCurrency(code: String) {
        dataStore.edit { prefs -> prefs[KEY_DEFAULT_CURRENCY] = Money.normalize(code) }
    }

    /** 备份不能把读取失败悄悄当作默认配置；UI 的容错 Flow 仍保持原行为。 */
    suspend fun getDefaultCurrency(): String = dataStore.data.first()[KEY_DEFAULT_CURRENCY] ?: FALLBACK_CURRENCY

    companion object {
        const val FALLBACK_CURRENCY = "CNY"
        private val KEY_DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        private val KEY_SHOP_CHANNELS = stringPreferencesKey("shop_channels")
    }
}
