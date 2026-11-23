package site.kentok.lists.ui.webview

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import okhttp3.Cookie
import site.kentok.lists.BuildConfig
import site.kentok.lists.EditorOkApp

/**
 * Внутренний WebView: HTML-оболочка из assets, API — с сайта.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EditorWebView(
    viewId: String,
    sessionEpoch: Int,
    online: Boolean,
    preferCachedShell: Boolean = true,
    cookies: List<Cookie>,
    onKicked: () -> Unit,
    onLogout: () -> Unit,
    onProgress: (Int) -> Unit = {},
    onShellCached: () -> Unit = {},
    onFileChooser: ((ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as EditorOkApp
    val base = BuildConfig.BASE_URL.trimEnd('/') + "/"
    val bridge = remember(sessionEpoch) {
        EditorBridge(app, onKicked, onLogout)
    }

    LaunchedEffect(sessionEpoch, viewId) {
        delay(4_000)
        onProgress(100)
    }

    key(sessionEpoch, viewId) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.userAgentString =
                        settings.userAgentString + " EditorOkApp/${BuildConfig.VERSION_NAME}"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    }
                    addJavascriptInterface(bridge, "EditorBridge")
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            onProgress(newProgress.coerceIn(0, 100))
                        }
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (onFileChooser != null) {
                                return onFileChooser.invoke(filePathCallback, fileChooserParams)
                            }
                            return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val url = request.url?.toString().orEmpty()
                            if (request.isForMainFrame && EditorShell.isEditorDocument(url)) {
                                EditorShell.documentResponse(ctx, app.session, viewId)?.let { return it }
                            }
                            val path = request.url?.path.orEmpty()
                            if (path.endsWith(".png") || path.endsWith(".svg") ||
                                path.endsWith("manifest.json") || path.endsWith("sw.js")
                            ) {
                                EditorShell.assetResponse(ctx, path)?.let { return it }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            return handleNav(url, onKicked, onLogout)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return handleNav(url.orEmpty(), onKicked, onLogout)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            onProgress(30)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            onProgress(100)
                            onShellCached()
                        }
                    }
                    injectCookies(base, cookies)
                    onProgress(10)
                    val v = viewId.trim()
                    val target = if (v.isNotBlank()) {
                        "${base}index.php?app=1&v=${java.net.URLEncoder.encode(v, "UTF-8")}"
                    } else {
                        "${base}index.php?app=1"
                    }
                    loadUrl(target)
                }
            },
            update = { }
        )
    }
}

private fun handleNav(url: String, onKicked: () -> Unit, onLogout: () -> Unit): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    if (lower.contains("k=1")) {
        onKicked()
        return true
    }
    if (lower.contains("index.php") &&
        !lower.contains("app=1") &&
        !lower.contains("v=") &&
        !lower.contains("e=") &&
        !lower.contains("api=") &&
        !lower.contains("k=")
    ) {
        onLogout()
        return true
    }
    return false
}

fun injectCookies(siteUrl: String, cookies: List<Cookie>) {
    val cm = CookieManager.getInstance()
    cm.setAcceptCookie(true)
    val origin = siteUrl.trimEnd('/')
    cookies.forEach { c ->
        val parts = mutableListOf("${c.name}=${c.value}", "Path=${c.path.ifBlank { "/" }}")
        if (c.secure) parts.add("Secure")
        cm.setCookie(origin, parts.joinToString("; "))
    }
    cm.flush()
}

fun clearWebCookies(siteUrl: String = BuildConfig.BASE_URL) {
    val cm = CookieManager.getInstance()
    val origin = siteUrl.trimEnd('/')
    val existing = cm.getCookie(origin).orEmpty()
    if (existing.isNotBlank()) {
        existing.split(";").forEach { part ->
            val name = part.substringBefore("=").trim()
            if (name.isNotBlank()) {
                cm.setCookie(origin, "$name=; Max-Age=0; Path=/")
            }
        }
    }
    cm.removeAllCookies(null)
    cm.flush()
}
