package site.kentok.lists

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import java.util.concurrent.atomic.AtomicBoolean
import site.kentok.lists.data.model.AppScreen
import site.kentok.lists.data.model.ConflictChoice
import site.kentok.lists.ui.AppViewModel
import site.kentok.lists.ui.components.SyncCornerBadge
import site.kentok.lists.ui.components.SyncOverlay
import androidx.compose.ui.Alignment
import site.kentok.lists.ui.login.LoginScreen
import site.kentok.lists.ui.login.RecoverScreen
import site.kentok.lists.ui.login.RegisterScreen
import site.kentok.lists.ui.pin.PinScreen
import site.kentok.lists.ui.splash.AnimatedSplash
import site.kentok.lists.ui.theme.EditorOkTheme
import site.kentok.lists.ui.theme.SiteBg
import site.kentok.lists.ui.theme.SiteOk
import site.kentok.lists.ui.theme.SiteOkBg
import site.kentok.lists.ui.webview.EditorWebView

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val keepSystemSplash = AtomicBoolean(true)
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = filePathCallback
        filePathCallback = null
        if (uri != null) cb?.onReceiveValue(arrayOf(uri)) else cb?.onReceiveValue(null)
    }

    private val requestMediaPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickImage.launch("image/*")
        else {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun openImageChooser(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback
        val need = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val ok = ContextCompat.checkSelfPermission(this, need) == PackageManager.PERMISSION_GRANTED
        if (ok) pickImage.launch("image/*") else requestMediaPerm.launch(need)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSystemSplash.get() }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Тёмная статус-шторка, светлые иконки/время (контраст на телефоне)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.statusBarColor = android.graphics.Color.parseColor("#0F172A")
        window.navigationBarColor = android.graphics.Color.parseColor("#0F172A")
        setContent {
            var showAnimSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { keepSystemSplash.set(false) }

            if (showAnimSplash) {
                AnimatedSplash(onFinished = { showAnimSplash = false })
                return@setContent
            }

            EditorOkTheme {
                val state by vm.ui.collectAsState()
                val online by vm.online.collectAsState()
                val justOnline by vm.justCameOnline.collectAsState()
                val loadPct by vm.loadingPercent.collectAsState()
                val loadLabel by vm.loadingLabel.collectAsState()
                val snack = remember { SnackbarHostState() }
                val bootErr = (application as? EditorOkApp)?.bootError

                val editorReady = state.screen == AppScreen.WORK && state.webReady &&
                    state.viewId.isNotBlank() && state.viewId != "boot"
                // После ПИН: полный оверлей до готовности WebView; дальше — угол с % и временем.
                val bootOverlay = state.screen == AppScreen.WORK && (!editorReady || state.loading)
                val overlayVisible =
                    bootOverlay || (state.screen != AppScreen.WORK && (state.loading || state.syncing))
                val overlayPct =
                    if (overlayVisible) {
                        (state.bgSyncPercent ?: loadPct ?: state.loadingPercent)?.coerceIn(0, 99)
                    } else null
                val overlayLabel =
                    state.bgSyncLabel ?: loadLabel ?: state.loadingLabel ?: "Загрузка…"
                val overlayStartedAt = state.loadStartedAtMs

                LaunchedEffect(state.message) {
                    val m = state.message ?: return@LaunchedEffect
                    if (state.screen == AppScreen.LOGIN ||
                        state.screen == AppScreen.REGISTER ||
                        state.screen == AppScreen.RECOVER
                    ) return@LaunchedEffect
                    snack.showSnackbar(m)
                    vm.dismissMessage()
                }
                LaunchedEffect(state.info) {
                    val m = state.info ?: return@LaunchedEffect
                    if (state.screen == AppScreen.WORK) {
                        snack.showSnackbar(m)
                        vm.dismissInfo()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SiteBg
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SiteBg)
                        // status/nav + IME: контент (и WebView) сжимается, поля остаются над клавиатурой
                        .windowInsetsPadding(
                            WindowInsets.statusBars.union(WindowInsets.navigationBars)
                        )
                        .imePadding()
                ) {
                    if (bootErr != null) {
                        Text(
                            "Ошибка запуска: $bootErr",
                            color = Color(0xFFB91C1C),
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        when {
                            !online -> Text(
                                text = stringResource(R.string.offline_banner),
                                color = Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFEBEE))
                                    .padding(10.dp)
                            )
                            justOnline -> Text(
                                text = stringResource(R.string.online_banner),
                                color = SiteOk,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SiteOkBg)
                                    .padding(10.dp)
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (state.screen) {
                                AppScreen.LOGIN -> LoginScreen(
                                    loading = state.loading,
                                    error = state.message,
                                    success = state.info,
                                    offline = !online,
                                    onLogin = vm::login,
                                    onRegister = vm::openRegister,
                                    onRecover = vm::openRecover
                                )
                                AppScreen.REGISTER -> RegisterScreen(
                                    loading = state.loading,
                                    error = state.message,
                                    offline = !online,
                                    onRegister = vm::register,
                                    onBack = vm::backToLogin
                                )
                                AppScreen.RECOVER -> RecoverScreen(
                                    loading = state.loading,
                                    error = state.message,
                                    offline = !online,
                                    onRecover = vm::recover,
                                    onBack = vm::backToLogin
                                )
                                AppScreen.PIN -> PinScreen(
                                    nick = state.nick,
                                    error = state.pinError,
                                    loading = state.loading,
                                    expectedPinLength = (application as? EditorOkApp)?.session?.pinCode?.length ?: 0,
                                    onSubmit = vm::submitPin,
                                    onLogout = vm::logout
                                )
                                AppScreen.WORK -> {
                                    if (editorReady) {
                                        EditorWebView(
                                            viewId = state.viewId,
                                            sessionEpoch = state.webEpoch,
                                            online = online,
                                            preferCachedShell = true,
                                            cookies = vm.webCookies(),
                                            onKicked = vm::onWebKicked,
                                            onLogout = vm::logout,
                                            onProgress = vm::onWebProgress,
                                            onShellCached = vm::onEditorShellCached,
                                            onFileChooser = { cb, params -> openImageChooser(cb, params) }
                                        )
                                    }
                                }
                            }
                            SyncOverlay(
                                visible = overlayVisible,
                                percent = overlayPct,
                                label = overlayLabel,
                                startedAtMs = overlayStartedAt
                            )
                            // Угол: % и время после появления редактора
                            if (state.screen == AppScreen.WORK && editorReady &&
                                !overlayVisible && state.bgSyncPercent != null
                            ) {
                                SyncCornerBadge(
                                    percent = state.bgSyncPercent,
                                    label = state.bgSyncLabel,
                                    startedAtMs = state.loadStartedAtMs,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                    SnackbarHost(hostState = snack, modifier = Modifier.padding(16.dp))
                }
                }

                state.conflict?.let {
                    AlertDialog(
                        onDismissRequest = { vm.resolveConflict(ConflictChoice.CANCEL) },
                        title = { Text(stringResource(R.string.conflict_title)) },
                        text = { Text(stringResource(R.string.conflict_message)) },
                        confirmButton = {
                            TextButton(onClick = { vm.resolveConflict(ConflictChoice.REPLACE) }) {
                                Text(stringResource(R.string.conflict_replace))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { vm.resolveConflict(ConflictChoice.RENAME) }) {
                                Text(stringResource(R.string.conflict_rename))
                            }
                        }
                    )
                }

                state.updateOffer?.let { offer ->
                    if (!state.updateDownloading) {
                        val sizeMb = if (offer.size >= 1_048_576) {
                            String.format("%.1f МБ", offer.size / 1_048_576.0)
                        } else {
                            "${(offer.size / 1024).coerceAtLeast(1)} КБ"
                        }
                        val ver = offer.versionName.ifBlank { offer.versionCode.toString() }
                        AlertDialog(
                            onDismissRequest = vm::dismissUpdate,
                            title = { Text("Доступно обновление") },
                            text = {
                                Text(
                                    "Версия $ver\n${offer.name}\n$sizeMb\n\nУстановить обновление?"
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = vm::acceptUpdate) {
                                    Text("Обновить")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = vm::dismissUpdate) {
                                    Text("Позже")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
