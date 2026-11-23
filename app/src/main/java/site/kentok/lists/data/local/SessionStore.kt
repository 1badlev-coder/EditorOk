package site.kentok.lists.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Сессия и материал для ключа шифрования документов (логин+пароль). */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "editorok_session",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // На части устройств EncryptedSharedPreferences падает — иначе чёрный экран при старте
        context.getSharedPreferences("editorok_session_plain", Context.MODE_PRIVATE)
    }

    var login: String
        get() = prefs.getString(KEY_LOGIN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LOGIN, v).apply()

    var password: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PASS, v).apply()

    var nick: String
        get() = prefs.getString(KEY_NICK, "") ?: ""
        set(v) = prefs.edit().putString(KEY_NICK, v).apply()

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_EMAIL, v).apply()

    var role: String
        get() = prefs.getString(KEY_ROLE, "user") ?: "user"
        set(v) = prefs.edit().putString(KEY_ROLE, v).apply()

    var userId: Int
        get() = prefs.getInt(KEY_UID, 0)
        set(v) = prefs.edit().putInt(KEY_UID, v).apply()

    var saltB64: String
        get() = prefs.getString(KEY_SALT, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SALT, v).apply()

    var pinOn: Boolean
        get() = prefs.getBoolean(KEY_PIN_ON, false)
        set(v) = prefs.edit().putBoolean(KEY_PIN_ON, v).apply()

    /** Локальный кэш ПИН для офлайн-проверки на этом устройстве. */
    var pinCode: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PIN, v).apply()

    /**
     * Как cookie-сессия сайта: не сохраняем на диск.
     * После каждого запуска приложения снова нужен ПИН.
     */
    @Volatile
    var pinUnlocked: Boolean = false

    /** Идентификатор окна редактора (как view на сайте). Только в памяти. */
    @Volatile
    var viewId: String = ""

    /**
     * Последнее успешное окно — для офлайн/кэша WebView (тот же URL попадал в HTTP-кэш).
     * ПИН хранится локально ([pinCode]) для входа без интернета.
     */
    var lastViewId: String
        get() = prefs.getString(KEY_LAST_VIEW, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_VIEW, v).apply()

    /** Последний открытый документ на этом устройстве. */
    var lastDocId: String
        get() = prefs.getString(KEY_LAST_DOC, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LAST_DOC, v).apply()

    val isLoggedIn: Boolean
        get() = login.isNotBlank() && password.isNotBlank() && userId > 0

    fun clear() {
        pinUnlocked = false
        viewId = ""
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LOGIN = "login"
        private const val KEY_PASS = "password"
        private const val KEY_NICK = "nick"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_UID = "uid"
        private const val KEY_SALT = "salt"
        private const val KEY_PIN_ON = "pin_on"
        private const val KEY_PIN = "pin_code"
        private const val KEY_LAST_DOC = "last_doc"
        private const val KEY_LAST_VIEW = "last_view"
    }
}
