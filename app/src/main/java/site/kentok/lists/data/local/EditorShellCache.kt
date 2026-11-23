package site.kentok.lists.data.local

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.io.File

/**
 * Кэш файлов редактора (HTML/CSS/JS сайта в WebView).
 * Не обновляется при синхронизации документов.
 * Перекачивается только если кэш пуст (память очищена) или сменился пользователь.
 */
class EditorShellCache(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isReadyFor(userId: Int): Boolean {
        if (userId <= 0) return false
        return prefs.getBoolean(KEY_READY, false) && prefs.getInt(KEY_UID, 0) == userId
    }

    fun markReady(userId: Int) {
        if (userId <= 0) return
        prefs.edit()
            .putBoolean(KEY_READY, true)
            .putInt(KEY_UID, userId)
            .apply()
    }

    /** Сброс маркера (смена пользователя / очистка данных приложения). */
    fun invalidate() {
        prefs.edit().clear().apply()
    }

    /**
     * Удалить кэш WebView и служебные файлы оболочки.
     * Документы пользователя сюда не входят.
     */
    fun clearDeviceShell(context: Context = this.context) {
        invalidate()
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {}
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
        try {
            WebView(context).apply {
                clearCache(true)
                clearHistory()
                clearFormData()
                destroy()
            }
        } catch (_: Exception) {}
        // каталог app_webview / кэш
        try {
            File(context.cacheDir, "WebView").deleteRecursively()
            File(context.filesDir, "editor_shell").deleteRecursively()
            context.getDir("webview", Context.MODE_PRIVATE)?.deleteRecursively()
            if (Build.VERSION.SDK_INT >= 24) {
                File(context.dataDir, "app_webview").deleteRecursively()
                File(context.dataDir, "cache/WebView").deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val PREFS = "editorok_shell"
        private const val KEY_READY = "ready"
        private const val KEY_UID = "uid"
    }
}
