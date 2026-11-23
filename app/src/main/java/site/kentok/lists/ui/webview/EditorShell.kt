package site.kentok.lists.ui.webview

import android.content.Context
import android.util.Base64
import android.webkit.WebResourceResponse
import org.json.JSONObject
import site.kentok.lists.BuildConfig
import site.kentok.lists.data.local.SessionStore
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Автономная HTML-оболочка редактора из assets/editor/shell.html.
 * API и загрузки идут на сайт; документ отдаётся локально (same-origin).
 */
object EditorShell {
    private const val ASSET_SHELL = "editor/shell.html"
    private const val ASSET_DIR = "editor/"

    private val APP_DL_CSS = (
        ".copyrow{display:flex;align-items:center;justify-content:center;gap:8px;padding:10px 12px 14px}" +
            ".copyrow .copy{padding:0}" +
            ".appdl{display:inline-flex;align-items:center;justify-content:center;width:32px;height:32px;padding:0;border:0;background:transparent;color:#64748b;cursor:pointer;border-radius:8px}" +
            ".appdl img{width:22px;height:22px;object-fit:contain;display:block}" +
            ".appdl:hover{background:#e2e8f0;color:#1d4ed8}" +
            ".appov{position:fixed;inset:0;background:#0006;display:flex;align-items:center;justify-content:center;z-index:40}" +
            ".appov[hidden]{display:none}" +
            ".appbox{background:#fff;width:min(92vw,400px);max-height:80vh;overflow:auto;border-radius:16px;padding:16px;box-shadow:0 8px 28px #0004}" +
            ".applist{display:flex;flex-direction:column;gap:8px;margin-top:10px}" +
            ".applist a{display:block;text-decoration:none}" +
            ".applist .empty{color:#64748b;font-size:14px;text-align:center;padding:12px}" +
            ".appov .btn{display:block;width:100%;font:inherit;padding:11px;margin:0;border:1px solid #bbb;border-radius:10px;background:#fafafa;color:#222;cursor:pointer;text-align:center;text-decoration:none}" +
            ".appov .btn.sm{display:inline-block;width:auto;padding:7px 12px;font-size:14px}"
        )

    /** Та же иконка APK, что appDlSvg() на сайте. */
    private val APP_ICON_SVG =
        "<svg viewBox=\"0 0 128 128\" width=\"22\" height=\"22\" xmlns=\"http://www.w3.org/2000/svg\" aria-hidden=\"true\">" +
            "<path d=\"M36 10h44l22 22v86H36V10z\" fill=\"#fff\" stroke=\"#2d2d2d\" stroke-width=\"7\" stroke-linejoin=\"round\"/>" +
            "<path d=\"M80 10v28h28\" fill=\"none\" stroke=\"#2d2d2d\" stroke-width=\"7\" stroke-linejoin=\"round\"/>" +
            "<text x=\"69\" y=\"64\" text-anchor=\"middle\" font-size=\"22\" font-weight=\"700\" font-family=\"Arial,Helvetica,sans-serif\" fill=\"#2d2d2d\">APK</text>" +
            "<path d=\"M69 72v30M54 88l15 16 15-16\" fill=\"none\" stroke=\"#7cb342\" stroke-width=\"8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>" +
            "</svg>"

    private val FOLDER_SVG =
        "<svg viewBox=\"0 0 31.119 31.119\" width=\"22\" height=\"22\" fill=\"currentColor\" xmlns=\"http://www.w3.org/2000/svg\" aria-hidden=\"true\">" +
            "<path d=\"M26.065 14.984c-.1-.666-.671-1.146-1.343-1.146H13.836l-1.977-2.34A2.16 2.16 0 0 0 10.178 10.7H3.105a1.77 1.77 0 0 0-1.309.622c-.325.387-.464.917-.379 1.414l.188 1.106H1.481c-.396 0-.771.161-1.027.461-.26.301-.373.693-.312 1.084l2.196 14.248c.133.857.871 1.488 1.738 1.488h22.797c.396 0 .771-.174 1.027-.471.257-.3.373-.689.314-1.08l-2.149-13.578z\"/>" +
            "<path d=\"M20.645 4.351H12.13c-.587 0-1.063.53-1.063 1.118s.477 1.118 1.063 1.118h8.515c.589 0 1.064-.53 1.064-1.118s-.475-1.118-1.064-1.118z\"/>" +
            "<path d=\"M30.677 12.186c-.245-.285-.604-.464-.981-.464h-2.416l.55 5.095 1.313 8.901 1.838-12.492c.051-.372-.057-.754-.304-1.04z\"/>" +
            "<path d=\"M9.024 2.055h14.864V12.146h2.056V1.307C25.943.591 25.345 0 24.629 0H8.191C7.405 0 6.789.647 6.789 1.433v7.812h2.235V2.055z\"/>" +
            "<path d=\"M20.645 8.46H12.13c-.511 0-.937.404-1.04.899.729.186 1.392.583 1.891 1.168l.155.168h7.509c.589 0 1.064-.53 1.064-1.118 0-.538-.477-1.067-1.064-1.067z\"/>" +
            "</svg>"

    private fun shareIconHtml(context: Context): String {
        return try {
            val bytes = context.assets.open("editor/share-folder.png").use { it.readBytes() }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "<img src=\"data:image/png;base64,$b64\" width=\"22\" height=\"22\" alt=\"\" aria-hidden=\"true\">"
        } catch (_: Exception) {
            "<img src=\"share-folder.png\" width=\"22\" height=\"22\" alt=\"\" aria-hidden=\"true\">"
        }
    }

    fun isEditorDocument(url: String): Boolean {
        val u = url.lowercase()
        if (!u.contains("editor-ok.kent-ok.site")) return false
        if (u.contains("api=") || u.contains("asset=") || u.contains("apps=") || u.contains("apk=")) return false
        return u.contains("index.php") && (u.contains("app=1") || u.contains("v=") || u.contains("e="))
    }

    fun documentResponse(context: Context, session: SessionStore, viewId: String): WebResourceResponse? {
        return try {
            val tpl = context.assets.open(ASSET_SHELL).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val me = JSONObject()
                .put("id", session.userId)
                .put("nick", session.nick)
                .put("email", session.email)
                .put("role", session.role.ifBlank { "user" })
                .put("pinOn", session.pinOn)
                .put("pinAsk", false)
            val html = tpl
                .replace("__LANG__", "ru")
                .replace("__LANG_JSON__", JSONObject.quote("ru"))
                .replace("__FAV_HREF__", "icon-192.png")
                .replace("__APP_DL_CSS__", APP_DL_CSS)
                .replace("__COPY_HTML__", "© Editor-Ok")
                .replace("__APP_ICON_HTML__", APP_ICON_SVG)
                .replace("__APP_VER_HTML__", "v" + BuildConfig.VERSION_NAME)
                .replace("__VIEW_JSON__", JSONObject.quote(viewId))
                .replace("__ME_JSON__", me.toString())
                .replace("__FOLDER_SVG_JSON__", JSONObject.quote(FOLDER_SVG))
                .replace("__SHARE_SVG_JSON__", JSONObject.quote(shareIconHtml(context)))
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            WebResourceResponse(
                "text/html",
                "utf-8",
                ByteArrayInputStream(bytes)
            ).also {
                it.responseHeaders = mapOf(
                    "Cache-Control" to "no-store",
                    "Access-Control-Allow-Origin" to "*"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Локальные статические файлы оболочки (иконки и т.п.). */
    fun assetResponse(context: Context, path: String): WebResourceResponse? {
        val name = path.trimStart('/').substringAfterLast('/')
        if (name.isBlank() || name.contains("..")) return null
        val assetPath = ASSET_DIR + name
        return try {
            val mime = when {
                name.endsWith(".png") -> "image/png"
                name.endsWith(".svg") -> "image/svg+xml"
                name.endsWith(".json") -> "application/json"
                name.endsWith(".js") -> "application/javascript"
                name.endsWith(".webp") -> "image/webp"
                else -> return null
            }
            val stream = context.assets.open(assetPath)
            WebResourceResponse(mime, null, stream)
        } catch (_: Exception) {
            null
        }
    }
}
