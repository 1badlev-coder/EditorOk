package site.kentok.lists.data.local

import android.content.Context

/** Несекретные настройки приложения (версия, флаги после обновления). */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lastAppVersionCode: Int
        get() = prefs.getInt(KEY_LAST_VC, 0)
        set(v) = prefs.edit().putInt(KEY_LAST_VC, v).apply()

    /** После установки новой версии — один раз обновить оболочку и документы. */
    var pendingPostUpdateSync: Boolean
        get() = prefs.getBoolean(KEY_POST_SYNC, false)
        set(v) = prefs.edit().putBoolean(KEY_POST_SYNC, v).apply()

    companion object {
        private const val PREFS = "editorok_app"
        private const val KEY_LAST_VC = "last_vc"
        private const val KEY_POST_SYNC = "post_sync"
    }
}
