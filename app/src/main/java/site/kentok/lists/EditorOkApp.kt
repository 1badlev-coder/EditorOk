package site.kentok.lists

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import site.kentok.lists.BuildConfig
import site.kentok.lists.data.local.AppPrefs
import site.kentok.lists.data.local.EditorShellCache
import site.kentok.lists.data.local.EncryptedDocStore
import site.kentok.lists.data.local.LocaleStore
import site.kentok.lists.data.local.SessionStore
import site.kentok.lists.data.remote.ApiClient
import site.kentok.lists.data.sync.ConnectivityMonitor
import site.kentok.lists.data.sync.SyncEngine
import site.kentok.lists.data.update.AppUpdateInstaller

class EditorOkApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var session: SessionStore
        private set
    lateinit var store: EncryptedDocStore
        private set
    lateinit var shellCache: EditorShellCache
        private set
    lateinit var prefs: AppPrefs
        private set
    lateinit var locale: LocaleStore
        private set
    lateinit var api: ApiClient
        private set
    lateinit var sync: SyncEngine
        private set
    lateinit var connectivity: ConnectivityMonitor
        private set
    lateinit var updater: AppUpdateInstaller
        private set
    var bootError: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            session = SessionStore(this)
            // Как на сайте: при каждом запуске снова нужен ПИН (если включён)
            session.pinUnlocked = false
            locale = LocaleStore(this)
            locale.applySaved()
            prefs = AppPrefs(this)
            store = EncryptedDocStore(this, session)
            shellCache = EditorShellCache(this)
            updater = AppUpdateInstaller(this)
            // После обновления APK — только синк документов; оболочку редактора не сбрасываем
            // (иначе после ПИН зависание на полной перекачке сайта).
            val prevVc = prefs.lastAppVersionCode
            val curVc = BuildConfig.VERSION_CODE
            if (prevVc > 0 && prevVc < curVc) {
                prefs.pendingPostUpdateSync = true
            }
            prefs.lastAppVersionCode = curVc
            if (session.userId > 0) store.bindUser(session.userId)
            api = ApiClient()
            sync = SyncEngine(api, store)
            connectivity = ConnectivityMonitor(this, api, appScope)
            connectivity.start()
        } catch (e: Exception) {
            bootError = e.message ?: e.javaClass.simpleName
            // Минимальный каркас, чтобы Activity не падала в чёрный экран
            if (!::session.isInitialized) {
                try { session = SessionStore(this) } catch (_: Exception) {}
            }
            if (!::api.isInitialized) api = ApiClient()
            if (!::connectivity.isInitialized) {
                try {
                    connectivity = ConnectivityMonitor(this, api, appScope)
                    connectivity.start()
                } catch (_: Exception) {}
            }
        }
    }

    fun editorUnlocked(): Boolean {
        if (!::session.isInitialized || !session.isLoggedIn) return false
        if (!session.pinOn) return true
        return session.pinUnlocked
    }
}
