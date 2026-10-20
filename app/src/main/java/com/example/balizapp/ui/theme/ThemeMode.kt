package com.example.balizapp.ui.theme

import com.example.balizapp.R
import androidx.annotation.StringRes
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Apariencia elegida por el usuario: la del sistema (por defecto), siempre claro o siempre oscuro. */
enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.core_theme_system),
    LIGHT(R.string.core_theme_light),
    DARK(R.string.core_theme_dark),
}

/** Guarda la elección en SharedPreferences para recordarla entre ejecuciones. */
class ThemeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
