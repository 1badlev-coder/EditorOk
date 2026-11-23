package site.kentok.lists.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Язык интерфейса приложения — только русский. */
class LocaleStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lang: String
        get() = "ru"
        set(_) {
            prefs.edit().putString(KEY, "ru").apply()
            apply("ru")
        }

    fun applySaved() = apply("ru")

    private fun apply(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    companion object {
        private const val PREFS = "editorok_locale"
        private const val KEY = "lang"
    }
}
