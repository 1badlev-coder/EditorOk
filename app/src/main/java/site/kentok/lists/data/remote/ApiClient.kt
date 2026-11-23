package site.kentok.lists.data.remote

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import site.kentok.lists.BuildConfig
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import site.kentok.lists.data.model.AppUpdateInfo
import site.kentok.lists.data.model.AppVerResponse
import site.kentok.lists.data.model.BoardResponse
import site.kentok.lists.data.model.BoardTile
import site.kentok.lists.data.model.Category
import site.kentok.lists.data.model.CreateResponse
import site.kentok.lists.data.model.DocItem
import site.kentok.lists.data.model.DocMeta
import site.kentok.lists.data.model.Document
import site.kentok.lists.data.model.LoginResponse
import site.kentok.lists.data.model.OkResponse
import site.kentok.lists.data.model.PingResponse
import site.kentok.lists.data.model.RecoverResponse
import site.kentok.lists.data.model.SaveResponse
import site.kentok.lists.data.model.UploadResponse
import site.kentok.lists.data.model.ViewResponse
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ApiClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val media = "application/json; charset=utf-8".toMediaType()
    private val form = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
    private val viewId = AtomicReference("")

    /** Download progress for active OkHttp responses (UI may ignore when not loading). */
    @Volatile
    var downloadProgressListener: DownloadProgressListener? = null

    fun setViewId(id: String) { viewId.set(id.trim()) }
    fun clearViewId() { viewId.set("") }
    fun currentViewId(): String = viewId.get()

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()
    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            val prev = cookieStore[url.host].orEmpty().associateBy { it.name }.toMutableMap()
            cookies.forEach { prev[it.name] = it }
            cookieStore[url.host] = prev.values.toList()
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()
    }

    /** Cookie ed_s (и др.) для WebView CookieManager. */
    fun webCookies(host: String = "editor-ok.kent-ok.site"): List<Cookie> =
        cookieStore[host]?.toList().orEmpty()

    fun clearCookies() {
        cookieStore.clear()
    }

    private fun OkHttpClient.Builder.withProgress(): OkHttpClient.Builder =
        addNetworkInterceptor(progressInterceptor { downloadProgressListener })

    private val http = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .withProgress()
        .build()

    /** Auth/view/ping — жёсткий callTimeout (единственный способ оборвать blocking execute). */
    private val authHttp = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    private val pingHttp = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun url(path: String): String {
        val base = BuildConfig.BASE_URL.trimEnd('/')
        return "$base/$path"
    }

    private fun Request.Builder.withView(): Request.Builder {
        val v = viewId.get()
        if (v.isNotBlank()) header("X-View", v)
        return this
    }

    private fun get(path: String): String {
        val req = Request.Builder().url(url(path)).withView().get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 409) throw ApiException(409, "superseded")
            if (!resp.isSuccessful) throw ApiException(resp.code, apiErrorText(body, "Ошибка запроса (${resp.code})"))
            return body
        }
    }

    private fun postJson(path: String, body: String, client: OkHttpClient = http): Pair<Int, String> {
        val req = Request.Builder()
            .url(url(path))
            .withView()
            .post(body.toRequestBody(media))
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    /** Достаёт человекочитаемый текст из JSON {error}/{message}. */
    private fun apiErrorText(body: String, fallback: String): String {
        val t = body.trim()
        if (t.isEmpty()) return fallback
        if (t.startsWith("{")) {
            try {
                val obj = json.parseToJsonElement(t) as? JsonObject
                val err = (obj?.get("error") as? JsonPrimitive)?.content?.trim().orEmpty()
                if (err.isNotEmpty() && err != "account" && err != "auth") return err
                val msg = (obj?.get("message") as? JsonPrimitive)?.content?.trim().orEmpty()
                if (msg.isNotEmpty()) return msg
                when (err) {
                    "account", "auth" -> return "Нет доступа. Проверьте логин и пароль или обновите сервер (update_20)."
                }
            } catch (_: Exception) {}
        }
        // HTML / мусор не показываем
        if (t.contains('<') || t.length > 180) return fallback
        return t
    }

    fun ping(): Boolean = try {
        val req = Request.Builder().url(url("index.php?api=ping")).withView().get().build()
        pingHttp.newCall(req).execute().use { resp ->
            resp.code in 200..499
        }
    } catch (_: Exception) {
        false
    }

    /** Публичная проверка последней сборки APK на сайте. */
    fun appLatest(): AppUpdateInfo? {
        val req = Request.Builder().url(url("index.php?appver=1")).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            val r = try {
                json.decodeFromString<AppVerResponse>(body)
            } catch (_: Exception) {
                return null
            }
            if (!r.ok || !r.update || r.id.isBlank()) return null
            return AppUpdateInfo(
                id = r.id,
                name = r.name,
                versionCode = r.vc,
                versionName = r.vn,
                size = r.size,
                urlPath = if (r.url.isNotBlank()) r.url else "index.php?apk=${enc(r.id)}"
            )
        }
    }

    /** true = это окно активно; false = открыт другой редактор; null = нет ответа. */
    fun pingActive(): Boolean? = try {
        val req = Request.Builder().url(url("index.php?api=ping")).withView().get().build()
        pingHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            val r = try { json.decodeFromString<PingResponse>(body) } catch (_: Exception) { null }
            r?.active
        }
    } catch (_: Exception) {
        null
    }

    fun openView(preferView: String = ""): String {
        val payload = if (preferView.isNotBlank()) {
            buildJsonObject { put("view", preferView) }.toString()
        } else {
            "{}"
        }
        val (code, body) = postJson("index.php?api=mobile_view", payload, authHttp)
        val r = try { json.decodeFromString<ViewResponse>(body) } catch (_: Exception) { null }
        if (r != null && r.ok && r.view.isNotBlank() && code in 200..299) {
            setViewId(r.view)
            return r.view
        }
        throw ApiException(code, r?.error ?: apiErrorText(body, "Не удалось открыть окно редактора"))
    }

    fun releaseView() {
        try {
            postJson("index.php?api=release", "{}")
        } catch (_: Exception) {}
        clearViewId()
    }

    fun cats(): List<Category> {
        val body = get("index.php?api=cats")
        return json.decodeFromString(ListSerializer(Category.serializer()), body)
    }

    fun catsSave(list: List<Category>) {
        val payload = buildJsonObject {
            put("cats", json.parseToJsonElement(json.encodeToString(list)))
        }.toString()
        val (code, body) = postJson("index.php?api=catsave", payload)
        if (code !in 200..299) throw ApiException(code, apiErrorText(body, "Не удалось сохранить папки"))
    }

    fun setCat(id: String, cat: String) {
        val payload = buildJsonObject {
            put("id", id)
            put("cat", cat)
        }.toString()
        val (code, body) = postJson("index.php?api=setcat", payload)
        if (code !in 200..299) throw ApiException(code, apiErrorText(body, "Не удалось сменить папку"))
    }

    fun login(login: String, password: String): LoginResponse {
        try {
            // sessOnly: только cookie-сессия, без viewNew (единый путь окна — mobile_pin / mobile_view)
            val payload = buildJsonObject {
                put("login", login)
                put("password", password)
                put("sessOnly", true)
            }.toString()
            val (code, body) = postJson("index.php?api=mobile_login", payload, authHttp)
            if (body.trim().startsWith("{")) {
                val r = try { json.decodeFromString<LoginResponse>(body) } catch (_: Exception) { null }
                if (r != null) {
                    if (r.ok && code in 200..299) return r
                    throw ApiException(
                        code,
                        r.error?.takeIf { it.isNotBlank() }
                            ?: apiErrorText(body, if (code == 401 || code == 403) "Неверный ник/почта или пароль" else "Ошибка входа")
                    )
                }
            }
            if (code == 401 || code == 403) {
                throw ApiException(code, apiErrorText(body, "Неверный ник/почта или пароль"))
            }
            if (code !in 200..299) {
                throw ApiException(code, apiErrorText(body, "Ошибка входа (код $code)"))
            }
            // редкий fallback: form-логин сайта
            val formBody = "act=login&login=${enc(login)}&password=${enc(password)}"
            val req = Request.Builder()
                .url(url("index.php"))
                .post(formBody.toRequestBody(form))
                .build()
            authHttp.newCall(req).execute().use { resp ->
                if (resp.code !in 200..399) {
                    throw ApiException(resp.code, "Неверный ник/почта или пароль")
                }
                return try {
                    get("index.php?api=list")
                    LoginResponse(ok = true, nick = login, email = login)
                } catch (_: ApiException) {
                    throw ApiException(401, "Неверный ник/почта или пароль")
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException(0, "Сервер не ответил вовремя. Проверьте интернет и повторите вход.")
        } catch (e: java.io.IOException) {
            throw ApiException(0, "Нет связи с сервером. Проверьте интернет и повторите.")
        }
    }

    fun list(): List<DocMeta> {
        val body = get("index.php?api=list")
        return json.decodeFromString(ListSerializer(DocMeta.serializer()), body)
    }

    fun load(id: String): Document {
        val body = get("index.php?api=load&id=${enc(id)}")
        val el = json.parseToJsonElement(body)
        val obj = el as JsonObject
        val rev = (obj["rev"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val updated = (obj["updated"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: 0L
        return Document(
            id = (obj["id"] as? JsonPrimitive)?.content ?: id,
            title = (obj["title"] as? JsonPrimitive)?.content ?: "Без названия",
            cat = (obj["cat"] as? JsonPrimitive)?.content ?: "",
            rev = rev,
            items = json.decodeFromString(
                ListSerializer(DocItem.serializer()),
                (obj["items"] ?: JsonArray(emptyList())).toString()
            ),
            // время последнего изменения с сервера — не «сейчас», иначе каждый load ломает diff-синк
            updated = updated,
            dirty = false,
            baseRev = rev
        )
    }

    fun create(): CreateResponse {
        val (code, body) = postJson("index.php?api=create", "{}")
        if (code !in 200..299) throw ApiException(code, body)
        return json.decodeFromString(body)
    }

    fun save(doc: Document, forceRev: Int? = null): SaveResponse {
        val rev = forceRev ?: doc.baseRev
        val payload = buildJsonObject {
            put("id", doc.id)
            put("title", doc.title)
            put("cat", doc.cat)
            put("rev", rev)
            put("items", json.parseToJsonElement(json.encodeToString(doc.items)))
        }
        val (code, body) = postJson("index.php?api=save", payload.toString())
        if (code == 412) {
            val err = try { json.decodeFromString<SaveResponse>(body) } catch (_: Exception) {
                SaveResponse(ok = false, error = "conflict")
            }
            return err.copy(error = "conflict")
        }
        if (code !in 200..299) throw ApiException(code, body)
        return json.decodeFromString(body)
    }

    fun delete(id: String) {
        val payload = buildJsonObject { put("id", id) }
        val (code, body) = postJson("index.php?api=delete", payload.toString())
        if (code !in 200..299) throw ApiException(code, body)
    }

    fun logout() {
        try { postJson("index.php?api=logout", "{}") } catch (_: Exception) {}
        clearCookies()
        clearViewId()
    }

    fun board(): List<BoardTile> {
        val body = get("index.php?api=board")
        return json.decodeFromString<BoardResponse>(body).tiles
    }

    fun boardSave(tiles: List<BoardTile>): Boolean {
        val payload = buildJsonObject {
            put("tiles", json.parseToJsonElement(json.encodeToString(tiles)))
        }
        val (code, body) = postJson("index.php?api=boardsave", payload.toString())
        if (code !in 200..299) throw ApiException(code, body)
        return json.decodeFromString<OkResponse>(body).ok
    }

    fun uploadImage(file: File): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "f",
                file.name,
                file.asRequestBody("image/*".toMediaType())
            )
            .build()
        val req = Request.Builder().url(url("index.php?api=upload")).post(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, text)
            val r = json.decodeFromString<UploadResponse>(text)
            if (r.name.isBlank()) throw ApiException(resp.code, r.error ?: "upload")
            return r.name
        }
    }

    fun register(nick: String, email: String, password: String): LoginResponse {
        try {
            val payload = buildJsonObject {
                put("nick", nick)
                put("email", email)
                put("password", password)
            }.toString()
            val (code, body) = postJson("index.php?api=mobile_register", payload, authHttp)
            val r = try { json.decodeFromString<LoginResponse>(body) } catch (_: Exception) { null }
            if (r != null) {
                if (r.ok && code in 200..299) return r
                throw ApiException(
                    code,
                    r.error?.takeIf { it.isNotBlank() }
                        ?: apiErrorText(body, "Ошибка регистрации")
                )
            }
            throw ApiException(code, apiErrorText(body, "Ошибка регистрации (код $code)"))
        } catch (e: ApiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException(0, "Сервер не ответил вовремя. Повторите регистрацию.")
        } catch (e: java.io.IOException) {
            throw ApiException(0, "Нет связи с сервером. Проверьте интернет.")
        }
    }

    fun setPinOn(on: Boolean): Boolean {
        val payload = buildJsonObject { put("on", on) }.toString()
        val (code, body) = postJson("index.php?api=pinon", payload, authHttp)
        if (code !in 200..299) throw ApiException(code, apiErrorText(body, "Не удалось изменить ПИН"))
        return json.decodeFromString<OkResponse>(body).pinOn ?: on
    }

    /** Проверка ПИН на сервере; при успехе активирует окно редактора (view). */
    fun verifyPin(pin: String): String {
        try {
            val payload = buildJsonObject { put("pin", pin) }.toString()
            val (code, body) = postJson("index.php?api=mobile_pin", payload, authHttp)
            if (body.trim().startsWith("{")) {
                val r = try { json.decodeFromString<OkResponse>(body) } catch (_: Exception) { null }
                if (r != null) {
                    if (r.ok && code in 200..299) {
                        val v = r.view.orEmpty()
                        if (v.isNotBlank()) setViewId(v)
                        return v
                    }
                    throw ApiException(code, r.error ?: "Неверный ПИН-код")
                }
            }
            if (code in 200..299) return ""
            throw ApiException(code, apiErrorText(body, "Неверный ПИН-код"))
        } catch (e: ApiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException(0, "Сервер не ответил. Повторите ввод ПИН.")
        } catch (e: java.io.IOException) {
            throw ApiException(0, "Нет связи с сервером")
        }
    }

    fun recover(login: String): RecoverResponse {
        try {
            val payload = buildJsonObject { put("login", login) }.toString()
            val (code, body) = postJson("index.php?api=mobile_recover", payload, authHttp)
            val r = try { json.decodeFromString<RecoverResponse>(body) } catch (_: Exception) { null }
            if (r != null) {
                if (r.ok && code in 200..299) return r
                throw ApiException(
                    code,
                    r.error?.takeIf { it.isNotBlank() }
                        ?: r.message?.takeIf { it.isNotBlank() }
                        ?: apiErrorText(body, "Не удалось восстановить пароль")
                )
            }
            throw ApiException(
                code,
                apiErrorText(
                    body,
                    if (code == 404) "Пользователь с таким ником или почтой не найден"
                    else "Ошибка восстановления (код $code)"
                )
            )
        } catch (e: ApiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException(0, "Сервер не ответил вовремя. Повторите восстановление.")
        } catch (e: java.io.IOException) {
            throw ApiException(0, "Нет связи с сервером. Проверьте интернет.")
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

class ApiException(val code: Int, message: String) : Exception(message)
