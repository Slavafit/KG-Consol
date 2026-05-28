package com.kgconsol.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppLanguage(val code: String) {
    RUSSIAN("ru"),
    ENGLISH("en"),
    SPANISH("es");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class AppSettings(
    val printerIp: String = "192.168.1.168",
    val printerPort: Int = 9100,
    val keepScreenOn: Boolean = true,
    val language: AppLanguage = AppLanguage.RUSSIAN
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PRINTER_IP = stringPreferencesKey("printer_ip")
        val PRINTER_PORT = intPreferencesKey("printer_port")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            AppSettings(
                printerIp = prefs[Keys.PRINTER_IP] ?: "192.168.1.168",
                printerPort = prefs[Keys.PRINTER_PORT] ?: 9100,
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
                language = AppLanguage.fromCode(prefs[Keys.LANGUAGE] ?: "ru")
            )
        }

    suspend fun setPrinterIp(ip: String) {
        context.dataStore.edit { it[Keys.PRINTER_IP] = ip }
    }

    suspend fun setPrinterPort(port: Int) {
        context.dataStore.edit { it[Keys.PRINTER_PORT] = port }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setLanguage(lang: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang.code }
    }
}
