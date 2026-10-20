package com.example.balizapp.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/** Idiomas de la app. El primero es el de siempre (y el de los textos por defecto en `values/`). */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ES("es-AR", "Español"),
    EN("en", "English"),
    IT("it", "Italiano"),
    PT("pt", "Português"),
    DE("de", "Deutsch");

    val locale: Locale get() = Locale.forLanguageTag(tag)
}

/**
 * Idioma elegido en Perfil. Se guarda en SharedPreferences y se aplica envolviendo el contexto
 * ([wrap]) en la Application y en la Activity, y cambiando el locale por defecto de la JVM
 * (que usan los formateadores de fecha y número).
 *
 * Fuera de las pantallas (ViewModels, repositorios, notificaciones) se usa [str] / [plural]; en
 * Compose, `stringResource`, que toma el contexto ya envuelto de la Activity.
 */
object AppLocale {
    private const val PREFS = "settings"
    private const val KEY_LANGUAGE = "language"

    private lateinit var appContext: Context
    private var cached: Pair<AppLanguage, Context>? = null

    /** Llamar una vez desde `Application.attachBaseContext`/`onCreate`. */
    fun init(context: Context) {
        appContext = context.applicationContext ?: context
        Locale.setDefault(language(appContext).locale)
    }

    fun language(context: Context): AppLanguage {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        return AppLanguage.entries.firstOrNull { it.name == saved } ?: AppLanguage.ES
    }

    fun current(): AppLanguage = language(appContext)

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language.name).apply()
        Locale.setDefault(language.locale)
        cached = null
    }

    /** [context] con el idioma elegido; para `attachBaseContext`. */
    fun wrap(context: Context): Context {
        val locale = language(context).locale
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(config)
    }

    private fun localized(): Context {
        val language = current()
        cached?.takeIf { it.first == language }?.let { return it.second }
        return wrap(appContext).also { cached = language to it }
    }

    fun str(@StringRes id: Int, vararg args: Any): String = localized().getString(id, *args)

    fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        localized().resources.getQuantityString(id, quantity, *args)
}
