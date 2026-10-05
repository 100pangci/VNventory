package com.vnventory.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.ShopChannels
import com.vnventory.app.domain.model.AppearancePreferences
import com.vnventory.app.domain.model.ThemeMode
import com.vnventory.app.domain.model.TitleDisplayMode
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

data class SettingsSnapshot(
    val defaultCurrency: String,
    val shopChannels: List<String>,
    val showShelfPrices: Boolean = false,
    val showPriceStats: Boolean = false,
    val appearance: AppearancePreferences = AppearancePreferences(),
    val showShelfReleaseNames: Boolean = false,
    val titleDisplayMode: TitleDisplayMode = TitleDisplayMode.ORIGINAL,
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val titleDisplayMode: Flow<TitleDisplayMode> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.readTitleDisplayMode() }

    suspend fun setTitleDisplayMode(mode: TitleDisplayMode) { dataStore.edit { it[KEY_TITLE_DISPLAY_MODE] = mode.name } }

    val defaultCurrency: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_DEFAULT_CURRENCY] ?: FALLBACK_CURRENCY }

    val shopChannels: Flow<List<String>> = dataStore.data.map { it.readShopChannels() }

    val showShelfPrices: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SHELF_PRICES] ?: false }
    val showPriceStats: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PRICE_STATS] ?: false }
    val showShelfReleaseNames: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SHELF_RELEASE_NAMES] ?: false }

    suspend fun setShowShelfPrices(value: Boolean) { dataStore.edit { it[KEY_SHELF_PRICES] = value } }
    suspend fun setShowPriceStats(value: Boolean) { dataStore.edit { it[KEY_PRICE_STATS] = value } }
    suspend fun setShowShelfReleaseNames(value: Boolean) { dataStore.edit { it[KEY_SHELF_RELEASE_NAMES] = value } }

    val appearance: Flow<AppearancePreferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.readAppearance() }

    suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[KEY_THEME_MODE] = mode.name } }
    suspend fun setDynamicColor(value: Boolean) { dataStore.edit { it[KEY_DYNAMIC_COLOR] = value } }

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

    /** All supplied preferences restore atomically; absent legacy appearance settings stay unchanged. */
    suspend fun restorePreferences(currency: String?, shops: List<String>?, shelfPrices: Boolean? = null, priceStats: Boolean? = null, appearance: AppearancePreferences? = null, shelfReleaseNames: Boolean? = null, titleDisplayMode: TitleDisplayMode? = null) {
        if (currency == null && shops == null && shelfPrices == null && priceStats == null && appearance == null && shelfReleaseNames == null && titleDisplayMode == null) return
        shops?.let(ShopChannels::validate)
        dataStore.edit { prefs ->
            currency?.let { prefs[KEY_DEFAULT_CURRENCY] = Money.normalize(it) }
            shops?.let { prefs[KEY_SHOP_CHANNELS] = Json.encodeToString(it) }
            shelfPrices?.let { prefs[KEY_SHELF_PRICES] = it }
            priceStats?.let { prefs[KEY_PRICE_STATS] = it }
            shelfReleaseNames?.let { prefs[KEY_SHELF_RELEASE_NAMES] = it }
            titleDisplayMode?.let { prefs[KEY_TITLE_DISPLAY_MODE] = it.name }
            appearance?.let {
                prefs[KEY_THEME_MODE] = it.themeMode.name
                prefs[KEY_DYNAMIC_COLOR] = it.dynamicColor
            }
        }
    }

    suspend fun snapshot(): SettingsSnapshot = dataStore.data.first().let {
        SettingsSnapshot(it[KEY_DEFAULT_CURRENCY] ?: FALLBACK_CURRENCY, it.readShopChannels(), it[KEY_SHELF_PRICES] ?: false, it[KEY_PRICE_STATS] ?: false, it.readAppearance(), it[KEY_SHELF_RELEASE_NAMES] ?: false, it.readTitleDisplayMode())
    }

    private fun Preferences.readTitleDisplayMode() = TitleDisplayMode.entries.firstOrNull { it.name == this[KEY_TITLE_DISPLAY_MODE] } ?: TitleDisplayMode.ORIGINAL

    private fun Preferences.readAppearance() = AppearancePreferences(
        themeMode = ThemeMode.entries.firstOrNull { it.name == this[KEY_THEME_MODE] } ?: ThemeMode.SYSTEM,
        dynamicColor = this[KEY_DYNAMIC_COLOR] ?: false,
    )

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
        private val KEY_TITLE_DISPLAY_MODE = stringPreferencesKey("title_display_mode")
        private val KEY_SHOP_CHANNELS = stringPreferencesKey("shop_channels")
        private val KEY_SHELF_PRICES = booleanPreferencesKey("show_shelf_prices")
        private val KEY_PRICE_STATS = booleanPreferencesKey("show_price_stats")
        private val KEY_SHELF_RELEASE_NAMES = booleanPreferencesKey("show_shelf_release_names")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
