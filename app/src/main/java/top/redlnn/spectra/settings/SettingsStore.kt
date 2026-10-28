package top.redlnn.spectra.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.analyzerStore by preferencesDataStore("analyzer_settings")

class SettingsStore(context: Context) {
    private val store = context.analyzerStore
    private val current = stringPreferencesKey("current_v1")

    suspend fun load(): AnalyzerSettings =
        runCatching { store.data.first()[current]?.let(AnalyzerSettings::fromJson) }.getOrNull()
            ?: AnalyzerSettings()

    suspend fun save(settings: AnalyzerSettings) {
        store.edit { it[current] = settings.toJson() }
    }
}
