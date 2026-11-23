package site.kentok.lists.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import site.kentok.lists.BuildConfig
import site.kentok.lists.EditorOkApp
import android.content.ClipData
import android.content.ClipboardManager
import site.kentok.lists.data.model.AppScreen
import site.kentok.lists.data.model.BoardTile
import site.kentok.lists.data.model.Category
import site.kentok.lists.data.model.ConflictChoice
import site.kentok.lists.data.model.DocItem
import site.kentok.lists.data.model.Document
import site.kentok.lists.data.model.WorkPanel
import site.kentok.lists.data.remote.ApiException
import site.kentok.lists.data.sync.SyncResult
import site.kentok.lists.util.WordExport
import java.io.File
import java.io.FileOutputStream

data class ConflictState(val doc: Document, val serverRev: Int)

data class UiState(
    val screen: AppScreen = AppScreen.LOGIN,
    val nick: String = "",
    val docs: List<Document> = emptyList(),
    val cats: List<Category> = emptyList(),
    val foldShut: Map<String, Boolean> = emptyMap(),
    val openDocs: List<Document> = emptyList(),
    val current: Document? = null,
    val tiles: List<BoardTile> = emptyList(),
    val loading: Boolean = false,
    /** 0–100 while network/WebView busy; null when idle. */
    val loadingPercent: Int? = null,
    val loadingLabel: String? = null,
    /** WebView page load 0–99; null when idle / finished. */
    val webPercent: Int? = null,
    val message: String? = null,
    val info: String? = null,
    val conflict: ConflictState? = null,
    val syncing: Boolean = false,
    val pinError: String? = null,
    val showMenu: Boolean = false,
    val showDocPicker: Boolean = false,
    val showCatPicker: Boolean = false,
    val panel: WorkPanel = WorkPanel.EDITOR,
    val autoSaveOn: Boolean = false,
    val autoPinOutOn: Boolean = false,
    val pinOn: Boolean = false,
    /** Активный view для WebView (?v=). */
    val viewId: String = "",
    /** Смена эпохи пересоздаёт WebView (после ПИН / нового входа). */
    val webEpoch: Int = 0,
    /**
     * true — брать HTML/CSS/JS редактора из кэша устройства (не качать заново).
     * false — первая загрузка оболочки после очистки памяти или смены пользователя.
     */
    val preferCachedShell: Boolean = false,
    val lang: String = "ru",
    /**
     * Фоновая синхронизация документов: редактор уже открыт, полный оверлей не нужен.
     * null — не идёт; 0–100 — процент в правом верхнем углу.
     */
    val bgSyncPercent: Int? = null,
    val bgSyncLabel: String? = null,
    /** Старт текущего этапа загрузки (для «Время: N с»). 0 — не идёт. */
    val loadStartedAtMs: Long = 0L,
    /**
     * false — экран WORK уже показан после ПИН, но WebView ещё не грузим
     * (ждём cookie ed_s / claim). Иначе страница без сессии «висит» или кикает.
     */
    val webReady: Boolean = false,
    /** Предложение установить новую версию с сайта. */
    val updateOffer: site.kentok.lists.data.model.AppUpdateInfo? = null,
    val updateDownloading: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as EditorOkApp
    private val _ui = MutableStateFlow(safeInitialUi())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private val _loadingPercent = MutableStateFlow<Int?>(null)
    val loadingPercent: StateFlow<Int?> = _loadingPercent.asStateFlow()
    private val _loadingLabel = MutableStateFlow<String?>(null)
    val loadingLabel: StateFlow<String?> = _loadingLabel.asStateFlow()
    val online = try {
        a.connectivity.online.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    } catch (_: Exception) {
        MutableStateFlow(false).asStateFlow()
    }
    val justCameOnline = try {
        a.connectivity.justCameOnline.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    } catch (_: Exception) {
        MutableStateFlow(false).asStateFlow()
    }
    val imageBaseUrl: String get() = BuildConfig.BASE_URL
    private var viewWatchJob: Job? = null
    private var syncDebounceJob: Job? = null
    private var editorSessionJob: Job? = null
    @Volatile private var ignoreKickUntilMs: Long = 0L
    private var netDepth = 0

    init {
        try {
            a.api.downloadProgressListener = site.kentok.lists.data.remote.DownloadProgressListener { bytes, len, done ->
                if (netDepth <= 0 && !_ui.value.syncing && _ui.value.webPercent == null) return@DownloadProgressListener
                val pct = site.kentok.lists.data.remote.ProgressResponseBody.percentOf(bytes, len, done)
                _loadingPercent.value = pct
                val cur = _ui.value
                if (cur.loading || cur.syncing || cur.webPercent != null) {
                    _ui.value = cur.copy(loadingPercent = pct)
                }
            }
            viewModelScope.launch { a.connectivity.refresh() }
            checkForAppUpdate()
            // До разблокировки ПИН — как на сайте: не грузим редактор и не синхронизируем
            if (a.editorUnlocked()) {
                startEditorSession()
            }
            viewModelScope.launch {
                a.connectivity.online.collect { on ->
                    // E: debounce синка при дребезге сети
                    if (on && a.editorUnlocked()) scheduleSync()
                }
            }
        } catch (e: Exception) {
            _ui.value = UiState(
                screen = AppScreen.LOGIN,
                message = a.bootError ?: e.message ?: "Ошибка запуска"
            )
        }
    }

    /** Отложенный sync (A+E): не стартовать сразу при каждом online=true. */
    private fun scheduleSync(delayMs: Long = 800L) {
        syncDebounceJob?.cancel()
        syncDebounceJob = viewModelScope.launch {
            delay(delayMs)
            if (a.editorUnlocked() && online.value) runSync()
        }
    }

    override fun onCleared() {
        a.api.downloadProgressListener = null
        super.onCleared()
    }

    /** Begin visible network wait (nestable). */
    private fun beginNet(label: String, syncing: Boolean = false) {
        netDepth++
        _loadingLabel.value = label
        if (_loadingPercent.value == null) _loadingPercent.value = 0
        _ui.value = _ui.value.copy(
            loading = !syncing || _ui.value.loading,
            syncing = syncing || _ui.value.syncing,
            loadingLabel = label,
            loadingPercent = _loadingPercent.value ?: 0,
            message = if (!syncing) null else _ui.value.message
        )
    }

    private fun setNetProgress(label: String? = null, percent: Int) {
        val p = percent.coerceIn(0, 100)
        _loadingPercent.value = p
        if (label != null) _loadingLabel.value = label
        _ui.value = _ui.value.copy(
            loadingPercent = p,
            loadingLabel = label ?: _ui.value.loadingLabel
        )
    }

    private fun endNet(clearSyncing: Boolean = true) {
        netDepth = (netDepth - 1).coerceAtLeast(0)
        if (netDepth > 0) return
        if (_ui.value.webPercent != null) {
            // page still loading — keep percent from WebView
            _ui.value = _ui.value.copy(
                loading = false,
                syncing = if (clearSyncing) false else _ui.value.syncing,
                loadingPercent = _ui.value.webPercent,
                loadingLabel = "Загрузка страницы…"
            )
            _loadingPercent.value = _ui.value.webPercent
            _loadingLabel.value = "Загрузка страницы…"
            return
        }
        _loadingPercent.value = null
        _loadingLabel.value = null
        _ui.value = _ui.value.copy(
            loading = false,
            syncing = if (clearSyncing) false else _ui.value.syncing,
            loadingPercent = null,
            loadingLabel = null
        )
    }

    fun onWebProgress(percent: Int) {
        val p = percent.coerceIn(0, 100)
        // WebView часто зависает на 99% до субресурсов — считаем готовностью ≥95
        if (p >= 95) {
            _ui.value = _ui.value.copy(webPercent = null)
            if (netDepth <= 0 && !_ui.value.syncing) {
                _loadingPercent.value = null
                _loadingLabel.value = null
                _ui.value = _ui.value.copy(
                    loadingPercent = null,
                    loadingLabel = null,
                    loading = false,
                    syncing = false
                )
            } else {
                _ui.value = _ui.value.copy(webPercent = null)
            }
            return
        }
        _loadingPercent.value = p
        _loadingLabel.value = "Загрузка страницы…"
        _ui.value = _ui.value.copy(
            webPercent = p,
            loadingPercent = p,
            loadingLabel = "Загрузка страницы…"
        )
    }

    val showLoadingOverlay: Boolean
        get() {
            val s = _ui.value
            return s.loading || s.syncing || (s.webPercent != null && s.webPercent < 100) ||
                (s.loadingPercent != null && netDepth > 0)
        }

    private fun safeInitialUi(): UiState = try {
        when {
            a.bootError != null ->
                UiState(screen = AppScreen.LOGIN, message = a.bootError)
            !a.session.isLoggedIn ->
                UiState(screen = AppScreen.LOGIN)
            a.session.pinOn && !a.session.pinUnlocked ->
                UiState(
                    screen = AppScreen.PIN,
                    nick = a.session.nick,
                    pinOn = true,
                    current = null,
                    openDocs = emptyList()
                )
            else ->
                UiState(
                    screen = AppScreen.WORK,
                    nick = a.session.nick,
                    pinOn = a.session.pinOn,
                    panel = WorkPanel.EDITOR,
                    current = null,
                    openDocs = emptyList()
                )
        }
    } catch (e: Exception) {
        UiState(screen = AppScreen.LOGIN, message = e.message ?: "Ошибка запуска")
    }

    /**
     * После ПИН — мгновенно WORK + WebView из кэша.
     * Нельзя читать EncryptedDocStore / сеть на Main: PBKDF2 120k и OkHttp блокируют UI.
     */
    private fun startEditorSession() {
        bindStoreToSession()
        viewWatchJob?.cancel()
        editorSessionJob?.cancel()
        while (netDepth > 0) endNet()
        if (!a.editorUnlocked()) return

        val online = a.connectivity.online.value || a.connectivity.hasSystemNetwork()
        restoreViewForCache(online)
        var v = a.session.viewId.trim().let { if (it == "offline" || it == "boot") "" else it }
        if (v.isBlank()) {
            v = a.session.lastViewId.trim()
            if (v.isNotBlank()) {
                a.session.viewId = v
                a.api.setViewId(v)
            }
        }
        val shellOk = a.shellCache.isReadyFor(a.session.userId) || v.isNotBlank()

        // Нет сохранённого окна и офлайн — сразу ошибка на ПИН (без ожидания)
        if (v.isBlank() && !online) {
            failBackToPin("Нужен интернет при первом открытии редактора на этом устройстве")
            return
        }

        // Сначала WORK без WebView: оверлей «Вход…» + время, затем claim и только потом оболочка
        ignoreKickUntilMs = System.currentTimeMillis() + 20_000L
        val bootAt = System.currentTimeMillis()
        _ui.value = _ui.value.copy(
            screen = AppScreen.WORK,
            panel = WorkPanel.EDITOR,
            pinError = null,
            message = null,
            loading = true,
            syncing = false,
            webReady = false,
            viewId = if (v.isNotBlank()) v else "boot",
            webPercent = null,
            preferCachedShell = shellOk,
            bgSyncPercent = 0,
            bgSyncLabel = "Вход…",
            loadStartedAtMs = bootAt,
            showMenu = false,
            showDocPicker = false,
            showCatPicker = false,
            nick = a.session.nick,
            pinOn = a.session.pinOn
        )
        beginNet("Вход…")

        editorSessionJob = viewModelScope.launch {
            try {
                if (!a.editorUnlocked()) return@launch

                // Тяжёлое чтение стора — только IO (PBKDF2)
                val local = withContext(Dispatchers.IO) {
                    Triple(
                        try { a.store.loadCats() } catch (_: Exception) { emptyList() },
                        try { a.store.loadBoard() } catch (_: Exception) { emptyList() },
                        try { a.store.listMeta() } catch (_: Exception) { emptyList() }
                    )
                }
                if (_ui.value.screen == AppScreen.WORK) {
                    _ui.value = _ui.value.copy(
                        cats = local.first,
                        tiles = local.second,
                        docs = local.third
                    )
                }

                if (online) {
                    setNetProgress("Проверка сессии…", 10)
                    _ui.value = _ui.value.copy(bgSyncPercent = 10, bgSyncLabel = "Проверка сессии…")
                    try {
                        withContext(Dispatchers.IO) {
                            try { ensureServerSession() } catch (_: Exception) {}
                        }
                        setNetProgress("Открытие окна…", 35)
                        _ui.value = _ui.value.copy(bgSyncPercent = 35, bgSyncLabel = "Открытие окна…")
                        withContext(Dispatchers.IO) {
                            try {
                                claimEditorView()
                                rememberViewId()
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        if (e is ApiException && (e.code == 409 || e.message == "superseded")) {
                            endNet()
                            failBackToPin("Сессия окна устарела. Введите ПИН ещё раз.")
                            return@launch
                        }
                    }
                }

                var view = a.session.viewId.trim().let { if (it == "offline" || it == "boot") "" else it }
                if (view.isBlank()) view = a.session.lastViewId.trim()
                if (view.isBlank()) {
                    endNet()
                    failBackToPin("Не удалось открыть окно редактора")
                    return@launch
                }
                a.session.viewId = view
                a.api.setViewId(view)
                rememberViewId()

                val hasSess = a.api.webCookies().any { it.name.equals("ed_s", ignoreCase = true) }
                ignoreKickUntilMs = System.currentTimeMillis() + 15_000L
                setNetProgress("Загрузка редактора…", 55)
                _ui.value = _ui.value.copy(
                    screen = AppScreen.WORK,
                    viewId = view,
                    webReady = true,
                    webEpoch = _ui.value.webEpoch + 1,
                    preferCachedShell = shellOk || !hasSess,
                    loading = true,
                    syncing = false,
                    bgSyncPercent = if (hasSess && online) 55 else null,
                    bgSyncLabel = if (hasSess && online) "Загрузка редактора…" else null,
                    loadStartedAtMs = bootAt
                )
                startViewWatch()
                if (hasSess && online && isActive && a.editorUnlocked()) {
                    if (a.prefs.pendingPostUpdateSync) runPostUpdateSync()
                    else runSync()
                } else {
                    endNet()
                    _ui.value = _ui.value.copy(
                        loading = false,
                        bgSyncPercent = null,
                        bgSyncLabel = null,
                        loadStartedAtMs = 0L
                    )
                }
            } catch (e: Exception) {
                endNet()
                if (e is ApiException && (e.code == 409 || e.message == "superseded")) {
                    lockToPin("Редактор открыт на другом устройстве")
                } else if (!_ui.value.webReady) {
                    failBackToPin(authError(e, "Ошибка входа в редактор"))
                } else {
                    _ui.value = _ui.value.copy(
                        loading = false,
                        bgSyncPercent = null,
                        bgSyncLabel = null,
                        loadStartedAtMs = 0L
                    )
                }
            }
        }
    }

    private fun failBackToPin(msg: String) {
        a.session.pinUnlocked = false
        a.session.viewId = ""
        a.api.clearViewId()
        _ui.value = _ui.value.copy(
            screen = if (a.session.pinOn) AppScreen.PIN else AppScreen.LOGIN,
            pinError = msg,
            message = if (!a.session.pinOn) msg else null,
            viewId = "",
            webReady = false,
            webPercent = null,
            loading = false,
            syncing = false,
            bgSyncPercent = null,
            bgSyncLabel = null
        )
    }

    /** Подставить lastViewId для HTTP-кэша WebView (офлайн и быстрый старт). */
    private fun restoreViewForCache(online: Boolean) {
        val cur = a.session.viewId.trim()
        if (cur.isNotBlank() && cur != "offline") {
            a.api.setViewId(cur)
            return
        }
        val last = a.session.lastViewId.trim()
        if (last.isNotBlank() && last != "offline") {
            a.session.viewId = last
            a.api.setViewId(last)
            return
        }
        if (!online) {
            a.session.viewId = "offline"
            a.api.setViewId("offline")
        }
    }

    private fun rememberViewId() {
        val v = a.session.viewId.trim()
        if (v.isNotBlank() && v != "offline") a.session.lastViewId = v
    }

    private fun openWorkFromCache(shellReady: Boolean, bumpWeb: Boolean) {
        ignoreKickUntilMs = System.currentTimeMillis() + 12_000L
        rememberViewId()
        _ui.value = _ui.value.copy(
            screen = AppScreen.WORK,
            panel = WorkPanel.EDITOR,
            nick = a.session.nick,
            pinOn = a.session.pinOn,
            viewId = a.session.viewId,
            webEpoch = if (bumpWeb) _ui.value.webEpoch + 1 else _ui.value.webEpoch,
            webPercent = null,
            webReady = true,
            preferCachedShell = shellReady,
            loading = false,
            syncing = false,
            bgSyncPercent = null,
            cats = try { a.store.loadCats() } catch (_: Exception) { emptyList() },
            tiles = try { a.store.loadBoard() } catch (_: Exception) { emptyList() },
            docs = try { a.store.listMeta() } catch (_: Exception) { emptyList() }
        )
    }

    private fun bindStoreToSession() {
        val uid = a.session.userId
        if (uid > 0) a.store.bindUser(uid)
    }

    fun webCookies() = a.api.webCookies()

    fun onWebKicked() {
        // Игнор ложных киков сразу после claim (гонка ping / старый WebView)
        if (System.currentTimeMillis() < ignoreKickUntilMs) return
        lockToPin("Редактор открыт на другом устройстве")
    }

    private suspend fun claimEditorView() {
        val existing = a.session.viewId.trim().let { if (it == "offline" || it == "boot") "" else it }
            .ifBlank { a.session.lastViewId.trim() }
        // Окно уже есть — реактивируем; не создаём второе (самокик)
        if (existing.isNotBlank()) {
            a.api.setViewId(existing)
            val ping = try { a.api.pingActive() } catch (_: Exception) { null }
            if (ping == true || ping == null) {
                a.session.viewId = existing
                rememberViewId()
                return
            }
            val reclaimed = try { a.api.openView(existing) } catch (_: Exception) { "" }
            if (reclaimed.isNotBlank()) {
                a.session.viewId = reclaimed
                a.api.setViewId(reclaimed)
                rememberViewId()
                return
            }
        }
        val v = a.api.openView()
        a.session.viewId = v
        a.api.setViewId(v)
        rememberViewId()
    }

    private fun startViewWatch() {
        viewWatchJob?.cancel()
        viewWatchJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (!a.editorUnlocked()) continue
                if (!a.connectivity.online.value && !a.connectivity.hasSystemNetwork()) continue
                // Офлайн — считаем, что авторизация только на этом устройстве
                try {
                    val active = withContext(Dispatchers.IO) { a.api.pingActive() }
                    if (active == false) {
                        lockToPin("Редактор открыт на другом устройстве")
                        break
                    }
                } catch (e: ApiException) {
                    if (e.code == 409 || e.message == "superseded") {
                        lockToPin("Редактор открыт на другом устройстве")
                        break
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun lockToPin(msg: String? = null) {
        viewWatchJob?.cancel()
        editorSessionJob?.cancel()
        syncDebounceJob?.cancel()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val cur = _ui.value.current
                    if (cur?.dirty == true) {
                        try { a.api.save(cur) } catch (_: Exception) {}
                    }
                    if (a.connectivity.online.value || a.connectivity.hasSystemNetwork()) {
                        try { a.api.releaseView() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
            a.session.viewId = ""
            a.session.pinUnlocked = false
            a.api.clearViewId()
            // Не чистим cookie ed_s — иначе «заклинивает» авторизация до повторного login
            netDepth = 0
            _loadingPercent.value = null
            _loadingLabel.value = null
            _ui.value = _ui.value.copy(
                screen = if (a.session.pinOn) AppScreen.PIN else AppScreen.LOGIN,
                pinError = msg,
                message = if (!a.session.pinOn) msg else null,
                current = null,
                openDocs = emptyList(),
                showMenu = false,
                showDocPicker = false,
                loading = false,
                syncing = false,
                loadingPercent = null,
                loadingLabel = null,
                webPercent = null,
                webReady = false,
                bgSyncPercent = null,
                bgSyncLabel = null,
                viewId = "",
                webEpoch = _ui.value.webEpoch + 1
            )
        }
    }

    private fun openLastDocument() {
        val docs = _ui.value.docs
        if (docs.isEmpty()) {
            _ui.value = _ui.value.copy(current = null, openDocs = emptyList())
            return
        }
        val last = a.session.lastDocId
        val prefer = docs.firstOrNull { it.id == last }
            ?: docs.maxByOrNull { it.updated }
        if (prefer != null) openDoc(prefer.id)
    }

    fun openRegister() {
        _ui.value = _ui.value.copy(screen = AppScreen.REGISTER, message = null, info = null, loading = false)
    }

    fun openRecover() {
        _ui.value = _ui.value.copy(screen = AppScreen.RECOVER, message = null, info = null, loading = false)
    }

    fun backToLogin() {
        _ui.value = _ui.value.copy(screen = AppScreen.LOGIN, message = null, loading = false)
    }

    fun login(login: String, password: String) {
        viewModelScope.launch {
            beginNet("Вход…")
            _ui.value = _ui.value.copy(message = null, info = null)
            try {
                // Не ждём короткий ping: сразу пробуем вход (длинный таймаут authHttp).
                // Offline только если система явно без сети.
                if (!a.connectivity.hasSystemNetwork() && !a.connectivity.online.value) {
                    if (a.session.login == login && a.session.password == password && a.session.isLoggedIn) {
                        endNet()
                        enterAfterAuth()
                        return@launch
                    }
                    endNet()
                    _ui.value = _ui.value.copy(
                        message = "Нет сети. Первый вход нужен при подключении к интернету."
                    )
                    return@launch
                }
                val r = withContext(Dispatchers.IO) { a.api.login(login.trim(), password) }
                applyAuth(login.trim(), password, r)
                endNet()
                enterAfterAuth()
                viewModelScope.launch { a.connectivity.refresh() }
                // Синк только после разблокировки (без ПИН или после ПИН) — как на сайте
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = authError(e, "Ошибка входа"))
            }
        }
    }

    fun register(nick: String, email: String, password: String) {
        viewModelScope.launch {
            beginNet("Регистрация…")
            _ui.value = _ui.value.copy(message = null, info = null)
            try {
                if (!a.connectivity.hasSystemNetwork() && !a.connectivity.online.value) {
                    endNet()
                    _ui.value = _ui.value.copy(message = "Нужен интернет для регистрации")
                    return@launch
                }
                val r = withContext(Dispatchers.IO) { a.api.register(nick, email, password) }
                applyAuth(nick, password, r)
                endNet()
                enterAfterAuth()
                viewModelScope.launch { a.connectivity.refresh() }
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = authError(e, "Ошибка регистрации"))
            }
        }
    }

    fun recover(login: String) {
        viewModelScope.launch {
            beginNet("Восстановление…")
            _ui.value = _ui.value.copy(message = null, info = null)
            try {
                if (!a.connectivity.hasSystemNetwork() && !a.connectivity.online.value) {
                    endNet()
                    _ui.value = _ui.value.copy(message = "Нужен интернет для восстановления")
                    return@launch
                }
                val r = withContext(Dispatchers.IO) { a.api.recover(login) }
                endNet()
                _ui.value = _ui.value.copy(
                    screen = AppScreen.LOGIN,
                    message = null,
                    info = r.message ?: "Данные для входа отправлены на почту"
                )
                viewModelScope.launch { a.connectivity.refresh() }
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = authError(e, "Ошибка восстановления"))
            }
        }
    }

    private fun authError(e: Throwable, fallback: String): String {
        val m = e.message?.trim().orEmpty()
        if (m.isEmpty()) return fallback
        // не показываем стек / HTML / сырой JSON
        if (m.startsWith("{") || m.contains('<') || m.length > 220) return fallback
        return m
    }

    private fun applyAuth(loginOrNick: String, password: String, r: site.kentok.lists.data.model.LoginResponse) {
        val prevUid = a.session.userId
        val nextUid = r.id.coerceAtLeast(1)
        // смена пользователя: удалить его данные с устройства и сбросить кэш редактора
        if (prevUid > 0 && prevUid != nextUid) {
            try { a.store.wipeUser(prevUid) } catch (_: Exception) {}
            a.shellCache.clearDeviceShell(getApplication())
            a.session.saltB64 = ""
            a.session.lastDocId = ""
            a.session.pinCode = ""
        } else if (prevUid <= 0 && !a.shellCache.isReadyFor(nextUid)) {
            // первый вход / после очистки памяти — оболочку качаем заново
            a.shellCache.invalidate()
        }
        a.session.login = loginOrNick
        a.session.password = password
        a.session.nick = r.nick.ifBlank { loginOrNick }
        a.session.email = r.email
        a.session.role = r.role.ifBlank { "user" }
        a.session.userId = nextUid
        a.session.pinOn = r.pinOn
        a.store.bindUser(nextUid)
        // Окно редактора только через mobile_pin / mobile_view — не из login (иначе кик при refresh)
        a.session.viewId = ""
        a.api.clearViewId()
        if (!r.pinOn) {
            a.session.pinCode = ""
            a.session.pinUnlocked = true
        } else {
            a.session.pinUnlocked = false
        }
    }

    /** WebView впервые догрузил оболочку — дальше берём из кэша устройства. */
    fun onEditorShellCached() {
        val uid = a.session.userId
        if (uid > 0) {
            a.shellCache.markReady(uid)
            _ui.value = _ui.value.copy(preferCachedShell = true)
        }
    }

    private fun enterAfterAuth() {
        if (a.session.pinOn) {
            a.session.pinUnlocked = false
            // Как на сайте и на любом устройстве: после пароля — экран ПИН
            _ui.value = _ui.value.copy(
                nick = a.session.nick,
                screen = AppScreen.PIN,
                pinOn = true,
                pinError = null,
                current = null,
                openDocs = emptyList(),
                showMenu = false,
                showDocPicker = false
            )
        } else {
            a.session.pinUnlocked = true
            startEditorSession()
        }
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(pinError = null)
            try {
                val online = a.connectivity.online.value || a.connectivity.hasSystemNetwork()
                val local = a.session.pinCode
                // Локальный ПИН — мгновенный вход без ожидания сети и без оверлея
                if (local.isNotBlank()) {
                    if (pin != local) throw Exception("Неверный ПИН-код")
                    a.session.pinUnlocked = true
                    a.session.pinOn = true
                    startEditorSession()
                    return@launch
                }
                if (!online) {
                    throw Exception("Нужен интернет для проверки ПИН на новом устройстве")
                }
                beginNet("Проверка ПИН…")
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(12_000) {
                        ensureServerSession()
                        setNetProgress("Проверка ПИН…", 50)
                        val v = a.api.verifyPin(pin)
                        if (v.isNotBlank()) {
                            a.session.viewId = v
                            a.api.setViewId(v)
                            a.session.lastViewId = v
                        } else {
                            claimEditorView()
                            rememberViewId()
                        }
                    } ?: throw Exception("Сервер не ответил")
                }
                a.session.pinCode = pin
                a.session.pinUnlocked = true
                a.session.pinOn = true
                endNet()
                startEditorSession()
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(
                    pinError = authError(e, "Неверный ПИН-код"),
                    loading = false
                )
            }
        }
    }

    /**
     * Восстановить cookie ed_s по логину/паролю.
     * Не трогаем view: mobile_login больше не создаёт окно (иначе ensureServerSession кикал WebView).
     */
    private suspend fun ensureServerSession() {
        val login = a.session.login
        val pass = a.session.password
        if (login.isBlank() || pass.isBlank()) throw Exception("Нет сохранённого входа")
        val r = a.api.login(login, pass)
        a.session.nick = r.nick.ifBlank { a.session.nick }
        a.session.email = r.email.ifBlank { a.session.email }
        a.session.role = r.role.ifBlank { a.session.role }
        a.session.userId = r.id.coerceAtLeast(a.session.userId)
        a.session.pinOn = r.pinOn
        // игнорируем r.view — окно только mobile_pin / mobile_view / claimEditorView
    }

    fun openMenu() { _ui.value = _ui.value.copy(showMenu = true) }
    fun closeMenu() { _ui.value = _ui.value.copy(showMenu = false) }
    fun openDocPicker() {
        viewModelScope.launch {
            refreshCats()
            refreshDocs()
            _ui.value = _ui.value.copy(showDocPicker = true, showMenu = false)
        }
    }
    fun closeDocPicker() { _ui.value = _ui.value.copy(showDocPicker = false) }
    fun toggleFolder(catId: String) {
        val key = catId.ifBlank { "_" }
        val m = _ui.value.foldShut.toMutableMap()
        // как на сайте: по умолчанию свёрнуто (shut=true), false = раскрыто
        val shut = m[key] ?: true
        m[key] = !shut
        _ui.value = _ui.value.copy(foldShut = m)
    }
    fun openCatPicker() { _ui.value = _ui.value.copy(showCatPicker = true, showMenu = false) }
    fun closeCatPicker() { _ui.value = _ui.value.copy(showCatPicker = false) }

    fun addCategory(name: String, assignToCurrent: Boolean = false) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            beginNet("Сохранение папки…")
            try {
                val id = "c" + System.currentTimeMillis().toString(36)
                val next = _ui.value.cats + Category(id = id, name = n)
                withContext(Dispatchers.IO) { a.api.catsSave(next) }
                _ui.value = _ui.value.copy(cats = next)
                endNet()
                if (assignToCurrent) {
                    val docId = _ui.value.current?.id
                    if (docId != null) setDocCategory(docId, id)
                }
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = authError(e, "Не удалось создать папку"))
            }
        }
    }

    fun renameCategory(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty() || id.isBlank()) return
        viewModelScope.launch {
            beginNet("Сохранение папки…")
            try {
                val next = _ui.value.cats.map { if (it.id == id) it.copy(name = n) else it }
                withContext(Dispatchers.IO) { a.api.catsSave(next) }
                endNet()
                _ui.value = _ui.value.copy(cats = next)
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = authError(e, "Не удалось переименовать"))
            }
        }
    }

    fun deleteCategory(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            beginNet("Удаление папки…")
            try {
                val next = _ui.value.cats.filter { it.id != id }
                withContext(Dispatchers.IO) { a.api.catsSave(next) }
                val docs = _ui.value.docs.map { if (it.cat == id) it.copy(cat = "") else it }
                docs.filter { it.cat != a.store.load(it.id)?.cat }.forEach { d ->
                    try { a.store.save(d) } catch (_: Exception) {}
                }
                val cur = _ui.value.current
                endNet()
                _ui.value = _ui.value.copy(
                    cats = next,
                    docs = docs,
                    current = if (cur?.cat == id) cur.copy(cat = "") else cur
                )
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = authError(e, "Не удалось удалить папку"))
            }
        }
    }

    fun setDocCategory(docId: String, cat: String) {
        viewModelScope.launch {
            if (online.value) beginNet("Смена папки…")
            try {
                if (online.value) withContext(Dispatchers.IO) { a.api.setCat(docId, cat) }
                val local = a.store.load(docId)?.copy(cat = cat, dirty = !online.value)
                if (local != null) a.store.save(local)
                val docs = _ui.value.docs.map { if (it.id == docId) it.copy(cat = cat) else it }
                val open = _ui.value.openDocs.map { if (it.id == docId) it.copy(cat = cat) else it }
                val cur = _ui.value.current
                if (online.value) endNet()
                _ui.value = _ui.value.copy(
                    docs = docs,
                    openDocs = open,
                    current = if (cur?.id == docId) cur.copy(cat = cat) else cur,
                    showCatPicker = false
                )
            } catch (e: Exception) {
                if (online.value) endNet()
                _ui.value = _ui.value.copy(message = authError(e, "Не удалось сменить папку"))
            }
        }
    }

    fun deleteRemoteDoc(id: String) {
        viewModelScope.launch {
            if (online.value) beginNet("Удаление…")
            try {
                if (online.value) withContext(Dispatchers.IO) { a.api.delete(id) }
                a.store.delete(id)
                closeDocTab(id)
                refreshDocs()
                if (online.value) endNet()
            } catch (e: Exception) {
                if (online.value) endNet()
                _ui.value = _ui.value.copy(message = authError(e, "Не удалось удалить"))
            }
        }
    }

    fun refreshCats() {
        viewModelScope.launch {
            if (!online.value && !a.connectivity.hasSystemNetwork()) {
                _ui.value = _ui.value.copy(cats = a.store.loadCats())
                return@launch
            }
            beginNet("Загрузка папок…")
            try {
                val cats = withContext(Dispatchers.IO) { a.api.cats() }
                a.store.saveCats(cats)
                endNet()
                _ui.value = _ui.value.copy(cats = cats)
            } catch (_: Exception) {
                endNet()
                _ui.value = _ui.value.copy(cats = a.store.loadCats())
            }
        }
    }

    fun openGuide() {
        _ui.value = _ui.value.copy(panel = WorkPanel.GUIDE, showMenu = false, screen = AppScreen.WORK)
    }

    fun toggleAutoSave() {
        _ui.value = _ui.value.copy(autoSaveOn = !_ui.value.autoSaveOn)
    }

    fun toggleAutoPinOut() {
        _ui.value = _ui.value.copy(autoPinOutOn = !_ui.value.autoPinOutOn)
    }

    fun togglePinOn() {
        viewModelScope.launch {
            beginNet("Настройка ПИН…")
            try {
                val next = !_ui.value.pinOn
                withContext(Dispatchers.IO) { a.api.setPinOn(next) }
                a.session.pinOn = next
                if (!next) a.session.pinCode = ""
                endNet()
                _ui.value = _ui.value.copy(
                    pinOn = next,
                    autoPinOutOn = if (next) _ui.value.autoPinOutOn else false,
                    message = if (next) "Вход по ПИН-коду включён" else "Вход по ПИН-коду выключен"
                )
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = e.message ?: "Не удалось изменить ПИН")
            }
        }
    }

    fun changePin(newPin: String) {
        if (newPin.length !in 3..8 || !newPin.all { it.isDigit() }) {
            _ui.value = _ui.value.copy(message = "ПИН-код — от 3 до 8 цифр")
            return
        }
        a.session.pinCode = newPin
        a.session.pinOn = true
        _ui.value = _ui.value.copy(pinOn = true, message = "ПИН-код сохранён на устройстве")
    }

    fun exportTxt(context: Context) {
        val doc = _ui.value.current ?: return
        try {
            val text = buildString {
                append(doc.title.ifBlank { "Список" }).append("\n\n")
                val counters = IntArray(16)
                doc.items.forEach { item ->
                    val lvl = item.l.coerceIn(0, 8)
                    val prefix = if (item.n) "" else {
                        counters[lvl]++
                        for (j in lvl + 1 until counters.size) counters[j] = 0
                        (0..lvl).joinToString(".") { counters[it].toString() } + ". "
                    }
                    append(prefix).append(item.t.replace(Regex("</?(b|i|u|s|strong|em)>", RegexOption.IGNORE_CASE), "")).append('\n')
                }
            }
            val file = File(context.cacheDir, safeFile(doc.title) + ".txt")
            file.writeText("\uFEFF$text", Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Скачать .txt"))
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(message = e.message ?: "Ошибка txt")
        }
    }

    fun copyText(context: Context) {
        val doc = _ui.value.current ?: return
        val text = buildString {
            append(doc.title.ifBlank { "Список" }).append("\n\n")
            doc.items.forEach { append(it.t.replace(Regex("</?(b|i|u|s|strong|em)>", RegexOption.IGNORE_CASE), "")).append('\n') }
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Editor-Ok", text))
        _ui.value = _ui.value.copy(info = "Текст скопирован", message = null)
    }

    private fun safeFile(t: String) =
        t.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "document" }

    private fun rememberOpen(doc: Document) {
        val rest = _ui.value.openDocs.filter { it.id != doc.id }
        a.session.lastDocId = doc.id
        _ui.value = _ui.value.copy(openDocs = listOf(doc) + rest)
    }

    fun logout() {
        viewWatchJob?.cancel()
        viewModelScope.launch {
            beginNet("Выход…")
            val uid = a.session.userId
            try { a.api.releaseView() } catch (_: Exception) {}
            try { a.api.logout() } catch (_: Exception) {}
            // Данные пользователя с устройства. Оболочку редактора не трогаем —
            // она сбрасывается только при смене пользователя или очистке памяти.
            try { if (uid > 0) a.store.wipeUser(uid) else a.store.clearAll() } catch (_: Exception) {}
            a.session.clear()
            a.api.clearViewId()
            a.api.clearCookies()
            try { site.kentok.lists.ui.webview.clearWebCookies() } catch (_: Exception) {}
            netDepth = 0
            _loadingPercent.value = null
            _loadingLabel.value = null
            _ui.value = UiState(screen = AppScreen.LOGIN)
        }
    }

    fun refreshDocs() {
        _ui.value = _ui.value.copy(docs = a.store.listMeta())
    }

    /** Проверка новой сборки на сайте при каждом запуске. */
    fun checkForAppUpdate() {
        viewModelScope.launch {
            try {
                val online = a.connectivity.online.value || a.connectivity.hasSystemNetwork()
                if (!online) return@launch
                val latest = withContext(Dispatchers.IO) { a.api.appLatest() } ?: return@launch
                if (latest.versionCode <= BuildConfig.VERSION_CODE) return@launch
                _ui.value = _ui.value.copy(updateOffer = latest)
            } catch (_: Exception) {
                // тихо — нет сети / ещё нет appver на сервере
            }
        }
    }

    fun dismissUpdate() {
        _ui.value = _ui.value.copy(updateOffer = null, updateDownloading = false)
    }

    fun acceptUpdate() {
        val offer = _ui.value.updateOffer ?: return
        viewModelScope.launch {
            beginNet("Скачивание обновления…")
            _ui.value = _ui.value.copy(updateDownloading = true)
            try {
                if (!a.updater.canInstall()) {
                    endNet()
                    _ui.value = _ui.value.copy(updateDownloading = false)
                    a.updater.openInstallPermissionSettings()
                    _ui.value = _ui.value.copy(
                        info = "Разрешите установку из этого источника и нажмите «Обновить» снова"
                    )
                    return@launch
                }
                val file = withContext(Dispatchers.IO) {
                    a.updater.download(offer.urlPath) { pct ->
                        setNetProgress("Скачивание обновления…", pct)
                    }
                }
                setNetProgress("Установка…", 100)
                // Установку запускаем на Main; offer не сбрасываем до старта установщика
                withContext(Dispatchers.Main) {
                    a.updater.install(file)
                }
                endNet()
                _ui.value = _ui.value.copy(
                    updateDownloading = false,
                    updateOffer = null,
                    info = "Подтвердите установку в системном окне"
                )
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(
                    updateDownloading = false,
                    // оставляем offer — можно нажать «Обновить» снова
                    message = authError(e, "Не удалось установить обновление")
                )
            }
        }
    }

    /**
     * После обновления APK: синк документов в углу, без сброса оболочки и без полного оверлея.
     */
    private fun runPostUpdateSync() {
        // Обычный фоновый синк (угол); оболочку WebView не трогаем
        a.prefs.pendingPostUpdateSync = false
        runSync()
    }

    fun runSync() {
        if (!a.editorUnlocked()) return
        viewModelScope.launch {
            // На WORK после ПИН — угол + подпись/время; полный оверлей уже мог быть с beginNet
            val corner = _ui.value.screen == AppScreen.WORK &&
                _ui.value.viewId.isNotBlank() &&
                _ui.value.viewId != "boot"
            val started = _ui.value.loadStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            if (corner) {
                _ui.value = _ui.value.copy(
                    bgSyncPercent = 60,
                    bgSyncLabel = "Синхронизация…",
                    syncing = true,
                    loading = true,
                    loadStartedAtMs = started
                )
                setNetProgress("Синхронизация…", 60)
            } else {
                beginNet("Синхронизация данных…", syncing = true)
            }
            fun progress(label: String, pct: Int) {
                val mapped = if (corner) (60 + (pct.coerceIn(0, 100) * 39) / 100) else pct
                _ui.value = _ui.value.copy(
                    bgSyncPercent = mapped.coerceIn(0, 99),
                    bgSyncLabel = label,
                    loadingLabel = label,
                    loadingPercent = mapped.coerceIn(0, 99)
                )
                setNetProgress(label, mapped)
            }
            try {
                when (val r = a.sync.syncAll(online.value) { label, pct ->
                    progress(label, pct)
                }) {
                    is SyncResult.Conflict ->
                        _ui.value = _ui.value.copy(conflict = ConflictState(r.local, r.serverRev))
                    is SyncResult.Error -> {
                        if (r.message.contains("superseded") || r.message.contains("409")) {
                            endNet()
                            _ui.value = _ui.value.copy(
                                bgSyncPercent = null,
                                bgSyncLabel = null,
                                loadStartedAtMs = 0L
                            )
                            lockToPin("Редактор открыт на другом устройстве")
                            return@launch
                        }
                        _ui.value = _ui.value.copy(message = r.message)
                    }
                    else -> {}
                }
                if (online.value) {
                    try {
                        progress("Загрузка доски…", 92)
                        val tiles = a.api.board()
                        a.store.saveBoard(tiles)
                        _ui.value = _ui.value.copy(tiles = tiles)
                    } catch (e: ApiException) {
                        if (e.code == 409) {
                            endNet()
                            _ui.value = _ui.value.copy(
                                bgSyncPercent = null,
                                bgSyncLabel = null,
                                loadStartedAtMs = 0L
                            )
                            lockToPin("Редактор открыт на другом устройстве")
                            return@launch
                        }
                        _ui.value = _ui.value.copy(tiles = a.store.loadBoard())
                    } catch (_: Exception) {
                        _ui.value = _ui.value.copy(tiles = a.store.loadBoard())
                    }
                    refreshCats()
                } else {
                    _ui.value = _ui.value.copy(tiles = a.store.loadBoard())
                }
                refreshDocs()
                if (corner) {
                    _ui.value = _ui.value.copy(bgSyncPercent = 100, bgSyncLabel = "Готово")
                    setNetProgress("Готово", 100)
                    delay(350)
                }
            } catch (e: ApiException) {
                if (e.code == 409 || e.message == "superseded") {
                    endNet()
                    _ui.value = _ui.value.copy(
                        bgSyncPercent = null,
                        bgSyncLabel = null,
                        loadStartedAtMs = 0L
                    )
                    lockToPin("Редактор открыт на другом устройстве")
                    return@launch
                }
                _ui.value = _ui.value.copy(message = e.message)
            } finally {
                while (netDepth > 0) endNet()
                _ui.value = _ui.value.copy(
                    bgSyncPercent = null,
                    bgSyncLabel = null,
                    loading = false,
                    syncing = false,
                    loadStartedAtMs = 0L
                )
            }
        }
    }

    fun openDoc(id: String) {
        viewModelScope.launch {
            var doc = a.store.load(id)
            if (doc == null && online.value) {
                beginNet("Загрузка документа…")
                try { doc = a.sync.pullOne(id) } catch (_: Exception) {}
                endNet()
            }
            if (doc == null) {
                _ui.value = _ui.value.copy(message = "Документ не найден", showDocPicker = false)
                return@launch
            }
            rememberOpen(doc)
            _ui.value = _ui.value.copy(
                current = doc,
                screen = AppScreen.WORK,
                panel = WorkPanel.EDITOR,
                showDocPicker = false,
                showCatPicker = false,
                showMenu = false,
                message = null
            )
        }
    }

    fun closeDocTab(id: String) {
        val open = _ui.value.openDocs.filter { it.id != id }
        val cur = _ui.value.current
        val nextCur = when {
            cur?.id != id -> cur
            open.isNotEmpty() -> a.store.load(open.first().id)
            else -> null
        }
        _ui.value = _ui.value.copy(
            openDocs = open,
            current = nextCur,
            panel = WorkPanel.EDITOR
        )
        refreshDocs()
    }

    fun closeDoc() {
        val id = _ui.value.current?.id
        if (id != null) closeDocTab(id)
        else _ui.value = _ui.value.copy(current = null, panel = WorkPanel.EDITOR)
    }

    fun openBoard() {
        viewModelScope.launch {
            val tiles = if (online.value) {
                beginNet("Загрузка доски…")
                try {
                    a.api.board().also { a.store.saveBoard(it) }
                } catch (_: Exception) {
                    a.store.loadBoard()
                }.also { endNet() }
            } else a.store.loadBoard()
            _ui.value = _ui.value.copy(
                tiles = tiles,
                screen = AppScreen.WORK,
                panel = if (_ui.value.panel == WorkPanel.BOARD) WorkPanel.EDITOR else WorkPanel.BOARD,
                showMenu = false
            )
        }
    }

    fun closeBoard() {
        _ui.value = _ui.value.copy(panel = WorkPanel.EDITOR)
    }

    fun updateTiles(tiles: List<BoardTile>) {
        _ui.value = _ui.value.copy(tiles = tiles)
        a.store.saveBoard(tiles)
    }

    fun saveBoard() {
        viewModelScope.launch {
            a.store.saveBoard(_ui.value.tiles)
            if (!online.value) {
                _ui.value = _ui.value.copy(message = "Доска сохранена на устройстве")
                return@launch
            }
            beginNet("Сохранение доски…")
            try {
                a.api.boardSave(_ui.value.tiles)
                endNet()
                _ui.value = _ui.value.copy(message = "Доска сохранена")
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = e.message ?: "Ошибка сохранения доски")
            }
        }
    }

    fun newDoc() {
        viewModelScope.launch {
            val now = System.currentTimeMillis() / 1000
            if (online.value) {
                beginNet("Создание документа…")
                try {
                    val c = a.api.create()
                    val doc = Document(
                        id = c.id,
                        title = c.title.ifBlank { "Новая страница" },
                        rev = c.rev,
                        baseRev = c.rev,
                        items = c.items.ifEmpty { listOf(DocItem()) },
                        updated = now,
                        dirty = false
                    )
                    a.store.save(doc)
                    rememberOpen(doc)
                    endNet()
                    _ui.value = _ui.value.copy(
                        current = doc,
                        screen = AppScreen.WORK,
                        panel = WorkPanel.EDITOR,
                        showMenu = false,
                        showDocPicker = false
                    )
                    refreshDocs()
                    return@launch
                } catch (_: Exception) {
                    endNet()
                }
            }
            val id = "local_${now}_${(1000..9999).random()}"
            val doc = Document(
                id = id,
                title = "Новая страница",
                items = listOf(DocItem()),
                updated = now,
                dirty = true
            )
            a.store.save(doc)
            rememberOpen(doc)
            _ui.value = _ui.value.copy(
                current = doc,
                screen = AppScreen.WORK,
                panel = WorkPanel.EDITOR,
                showMenu = false,
                showDocPicker = false
            )
            refreshDocs()
        }
    }

    fun updateCurrent(doc: Document) {
        val d = doc.copy(dirty = true)
        val open = _ui.value.openDocs.map { if (it.id == d.id) d else it }
        _ui.value = _ui.value.copy(current = d, openDocs = open)
    }

    fun saveCurrent() {
        val doc = _ui.value.current ?: return
        viewModelScope.launch {
            beginNet("Сохранение…")
            _ui.value = _ui.value.copy(message = null)
            when (val r = a.sync.saveDocument(doc, online.value)) {
                is SyncResult.Conflict -> {
                    endNet()
                    _ui.value = _ui.value.copy(conflict = ConflictState(r.local, r.serverRev))
                }
                is SyncResult.Error -> {
                    endNet()
                    _ui.value = _ui.value.copy(message = r.message)
                }
                is SyncResult.Offline -> {
                    refreshDocs()
                    endNet()
                    _ui.value = _ui.value.copy(
                        current = a.store.load(doc.id),
                        message = "Сохранено на устройстве (офлайн)"
                    )
                }
                SyncResult.Ok -> {
                    refreshDocs()
                    endNet()
                    _ui.value = _ui.value.copy(
                        current = a.store.load(doc.id),
                        message = "Сохранено"
                    )
                }
            }
        }
    }

    fun resolveConflict(choice: ConflictChoice) {
        val c = _ui.value.conflict ?: return
        viewModelScope.launch {
            beginNet("Сохранение…")
            _ui.value = _ui.value.copy(conflict = null)
            val r = a.sync.saveDocument(c.doc, online.value, choice, c.serverRev)
            refreshDocs()
            endNet()
            _ui.value = _ui.value.copy(
                current = a.store.load(c.doc.id) ?: _ui.value.current,
                message = when (r) {
                    SyncResult.Ok -> "Сохранено"
                    is SyncResult.Offline -> "Сохранено на устройстве"
                    is SyncResult.Error -> r.message
                    else -> null
                }
            )
        }
    }

    fun attachPhoto(index: Int, uri: Uri) {
        val doc = _ui.value.current ?: return
        viewModelScope.launch {
            beginNet("Загрузка фото…")
            try {
                val name = withContext(Dispatchers.IO) {
                    val tmp = copyUriToCache(getApplication(), uri)
                    if (online.value) {
                        try {
                            a.api.uploadImage(tmp)
                        } catch (_: Exception) {
                            // офлайн / ошибка — локальный путь
                            tmp.absolutePath
                        }
                    } else tmp.absolutePath
                }
                val items = doc.items.toMutableList()
                if (index !in items.indices) {
                    endNet()
                    return@launch
                }
                items[index] = items[index].copy(img = name, ip = "below", iw = 400)
                val next = doc.copy(items = items, dirty = true)
                a.store.save(next)
                endNet()
                _ui.value = _ui.value.copy(current = next, message = "Фото добавлено")
            } catch (e: Exception) {
                endNet()
                _ui.value = _ui.value.copy(message = e.message ?: "Не удалось добавить фото")
            }
        }
    }

    fun exportWord(context: Context) {
        val doc = _ui.value.current ?: return
        try {
            WordExport.share(context, doc)
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(message = e.message ?: "Ошибка Word")
        }
    }

    fun dismissMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun dismissInfo() {
        _ui.value = _ui.value.copy(info = null)
    }

    private fun copyUriToCache(ctx: Context, uri: Uri): File {
        val dir = File(ctx.cacheDir, "up").also { it.mkdirs() }
        val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть файл" }
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }
}
