package com.blefinder.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val stepLength: Double = 0.7,
    val vibrateByDefault: Boolean = true,
    val gaugeCold: Int = -100,
    val gaugeHot: Int = -40,
) {
    companion object {
        const val MIN_STEP = 0.4
        const val MAX_STEP = 1.0
        const val GAUGE_FLOOR = -110
        const val GAUGE_CEILING = -30
        const val MIN_GAUGE_SPAN = 20
    }
}

private val Context.settingsStore by preferencesDataStore("settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsStore

    val state: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            stepLength = p[STEP] ?: d.stepLength,
            vibrateByDefault = p[VIBRATE] ?: d.vibrateByDefault,
            gaugeCold = p[COLD] ?: d.gaugeCold,
            gaugeHot = p[HOT] ?: d.gaugeHot,
        )
    }

    suspend fun save(s: AppSettings) {
        store.edit { p ->
            p[STEP] = s.stepLength.coerceIn(AppSettings.MIN_STEP, AppSettings.MAX_STEP)
            p[VIBRATE] = s.vibrateByDefault
            p[COLD] = s.gaugeCold
            p[HOT] = s.gaugeHot
        }
    }

    private companion object {
        val STEP = doublePreferencesKey("step_length")
        val VIBRATE = booleanPreferencesKey("vibrate_by_default")
        val COLD = intPreferencesKey("gauge_cold")
        val HOT = intPreferencesKey("gauge_hot")
    }
}
