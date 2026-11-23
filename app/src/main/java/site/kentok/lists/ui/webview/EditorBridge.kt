package site.kentok.lists.ui.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import site.kentok.lists.EditorOkApp
import site.kentok.lists.data.model.BoardTile
import site.kentok.lists.data.model.Category
import site.kentok.lists.data.model.DocItem
import site.kentok.lists.data.model.Document

/**
 * Мост JS ↔ Kotlin: офлайн list/load/save/cats и кэш ответов сервера в EncryptedDocStore.
 */
class EditorBridge(
    private val app: EditorOkApp,
    private val onKicked: () -> Unit,
    private val onLogout: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @JavascriptInterface
    fun isOnline(): String {
        return if (app.connectivity.online.value || app.connectivity.hasSystemNetwork()) "1" else "0"
    }

    /** Синхронный ответ для window.EditorBridge.api(...) из JS. */
    @JavascriptInterface
    fun api(action: String, bodyJson: String?, query: String?): String = runBlocking {
        try {
            handleLocal(action, bodyJson, query)
        } catch (e: Exception) {
            """{"error":${json.encodeToString(e.message ?: "bridge")}}"""
        }
    }

    /** Зеркалирование успешных сетевых ответов в локальное хранилище. */
    @JavascriptInterface
    fun cache(action: String, bodyJson: String?, query: String?, responseJson: String) {
        try {
            when (action) {
                "list" -> cacheList(responseJson)
                "load" -> cacheLoad(responseJson)
                "save" -> cacheSave(bodyJson, responseJson)
                "create" -> cacheCreate(responseJson)
                "cats" -> cacheCats(responseJson)
                "catsave" -> {
                    val body = parseObj(bodyJson)
                    val catsEl = body?.get("cats")
                    if (catsEl != null) {
                        val cats = json.decodeFromString(ListSerializer(Category.serializer()), catsEl.toString())
                        app.store.saveCats(cats)
                    }
                }
                "board" -> cacheBoard(responseJson)
                "boardsave" -> {
                    val body = parseObj(bodyJson)
                    val tilesEl = body?.get("tiles")
                    if (tilesEl != null) {
                        val tiles = json.decodeFromString(ListSerializer(BoardTile.serializer()), tilesEl.toString())
                        app.store.saveBoard(tiles)
                    }
                }
                "delete" -> {
                    val id = parseObj(bodyJson)?.get("id")?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                    if (id.isNotBlank()) app.store.delete(id)
                }
                "setcat" -> {
                    val o = parseObj(bodyJson) ?: return
                    val id = (o["id"] as? JsonPrimitive)?.content.orEmpty()
                    val cat = (o["cat"] as? JsonPrimitive)?.content.orEmpty()
                    if (id.isNotBlank()) {
                        val local = app.store.load(id)?.copy(cat = cat) ?: return
                        app.store.save(local)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onKicked() {
        main.post { onKicked.invoke() }
    }

    @JavascriptInterface
    fun onLogout() {
        main.post { onLogout.invoke() }
    }

    private fun handleLocal(action: String, bodyJson: String?, query: String?): String {
        if (!app.editorUnlocked()) return """{"error":"auth"}"""
        return when (action) {
            "ping" -> """{"ok":true,"active":true}"""
            "list" -> {
                val arr = app.store.listMeta().map { d ->
                    buildJsonObject {
                        put("id", d.id)
                        put("title", d.title)
                        put("cat", d.cat)
                        put("updated", d.updated)
                        put("rev", d.rev)
                    }
                }
                json.encodeToString(JsonArray(arr))
            }
            "load" -> {
                val id = queryId(query) ?: parseObj(bodyJson)?.get("id")?.let { (it as? JsonPrimitive)?.content }
                if (id.isNullOrBlank()) return """{"error":"id"}"""
                val doc = app.store.load(id) ?: return """{"error":"not found"}"""
                docToApiJson(doc)
            }
            "save" -> {
                val o = parseObj(bodyJson) ?: return """{"error":"body"}"""
                val id = (o["id"] as? JsonPrimitive)?.content.orEmpty()
                if (id.isBlank()) return """{"error":"id"}"""
                val title = (o["title"] as? JsonPrimitive)?.content ?: "Без названия"
                val cat = (o["cat"] as? JsonPrimitive)?.content ?: ""
                val rev = (o["rev"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val items = json.decodeFromString(
                    ListSerializer(DocItem.serializer()),
                    (o["items"] ?: JsonArray(emptyList())).toString()
                )
                val now = System.currentTimeMillis() / 1000
                val prev = app.store.load(id)
                val nextRev = (prev?.rev ?: rev).coerceAtLeast(rev) + 1
                val doc = Document(
                    id = id,
                    title = title,
                    cat = cat,
                    rev = nextRev,
                    baseRev = nextRev,
                    items = items.ifEmpty { listOf(DocItem()) },
                    updated = now,
                    dirty = true
                )
                app.store.save(doc)
                """{"ok":true,"rev":$nextRev}"""
            }
            "create" -> {
                val now = System.currentTimeMillis() / 1000
                val id = "local_${now}_${(1000..9999).random()}"
                val doc = Document(
                    id = id,
                    title = "Новая страница",
                    items = listOf(DocItem()),
                    updated = now,
                    dirty = true,
                    rev = 0,
                    baseRev = 0
                )
                app.store.save(doc)
                buildJsonObject {
                    put("id", id)
                    put("title", doc.title)
                    put("cat", "")
                    put("rev", 0)
                    put("items", json.parseToJsonElement(json.encodeToString(doc.items)))
                }.toString()
            }
            "delete" -> {
                val id = parseObj(bodyJson)?.get("id")?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                if (id.isNotBlank()) app.store.delete(id)
                """{"ok":true}"""
            }
            "cats" -> json.encodeToString(ListSerializer(Category.serializer()), app.store.loadCats())
            "catsave" -> {
                val catsEl = parseObj(bodyJson)?.get("cats") ?: return """{"error":"cats"}"""
                val cats = json.decodeFromString(ListSerializer(Category.serializer()), catsEl.toString())
                app.store.saveCats(cats)
                """{"ok":true}"""
            }
            "setcat" -> {
                val o = parseObj(bodyJson) ?: return """{"error":"body"}"""
                val id = (o["id"] as? JsonPrimitive)?.content.orEmpty()
                val cat = (o["cat"] as? JsonPrimitive)?.content.orEmpty()
                val local = app.store.load(id)?.copy(cat = cat, dirty = true)
                if (local != null) app.store.save(local)
                """{"ok":true}"""
            }
            "board" -> {
                val tiles = app.store.loadBoard()
                buildJsonObject {
                    put("tiles", json.parseToJsonElement(json.encodeToString(tiles)))
                }.toString()
            }
            "boardsave" -> {
                val tilesEl = parseObj(bodyJson)?.get("tiles") ?: return """{"error":"tiles"}"""
                val tiles = json.decodeFromString(ListSerializer(BoardTile.serializer()), tilesEl.toString())
                app.store.saveBoard(tiles)
                """{"ok":true}"""
            }
            "release", "logout" -> """{"ok":true}"""
            else -> """{"error":"unsupported"}"""
        }
    }

    private fun cacheList(responseJson: String) {
        val arr = json.parseToJsonElement(responseJson) as? JsonArray ?: return
        val remoteIds = mutableSetOf<String>()
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            val id = (o["id"] as? JsonPrimitive)?.content ?: continue
            remoteIds.add(id)
            val title = (o["title"] as? JsonPrimitive)?.content ?: "Без названия"
            val cat = (o["cat"] as? JsonPrimitive)?.content ?: ""
            val updated = (o["updated"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            val rev = (o["rev"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            val local = app.store.load(id)
            if (local == null) {
                // мета без тела — подтянет sync/load
                app.store.save(
                    Document(id = id, title = title, cat = cat, updated = updated, rev = rev, baseRev = rev, dirty = false)
                )
            } else if (!local.dirty && (updated > local.updated || rev > local.rev)) {
                app.store.save(local.copy(title = title, cat = cat, updated = updated, rev = rev, baseRev = rev))
            } else if (local.title != title || local.cat != cat) {
                app.store.save(local.copy(title = title, cat = cat))
            }
        }
    }

    private fun cacheLoad(responseJson: String) {
        val o = json.parseToJsonElement(responseJson) as? JsonObject ?: return
        if (o.containsKey("error")) return
        val id = (o["id"] as? JsonPrimitive)?.content ?: return
        val title = (o["title"] as? JsonPrimitive)?.content ?: "Без названия"
        val cat = (o["cat"] as? JsonPrimitive)?.content ?: ""
        val rev = (o["rev"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val items = json.decodeFromString(
            ListSerializer(DocItem.serializer()),
            (o["items"] ?: JsonArray(emptyList())).toString()
        )
        val prev = app.store.load(id)
        if (prev?.dirty == true) return
        app.store.save(
            Document(
                id = id,
                title = title,
                cat = cat,
                rev = rev,
                baseRev = rev,
                items = items.ifEmpty { listOf(DocItem()) },
                updated = System.currentTimeMillis() / 1000,
                dirty = false
            )
        )
    }

    private fun cacheSave(bodyJson: String?, responseJson: String) {
        val resp = parseObj(responseJson) ?: return
        if ((resp["ok"] as? JsonPrimitive)?.content != "true" && resp["ok"] != JsonPrimitive(true)) return
        val rev = (resp["rev"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return
        val body = parseObj(bodyJson) ?: return
        val id = (body["id"] as? JsonPrimitive)?.content ?: return
        val title = (body["title"] as? JsonPrimitive)?.content ?: "Без названия"
        val cat = (body["cat"] as? JsonPrimitive)?.content ?: ""
        val items = json.decodeFromString(
            ListSerializer(DocItem.serializer()),
            (body["items"] ?: JsonArray(emptyList())).toString()
        )
        app.store.save(
            Document(
                id = id,
                title = title,
                cat = cat,
                rev = rev,
                baseRev = rev,
                items = items.ifEmpty { listOf(DocItem()) },
                updated = System.currentTimeMillis() / 1000,
                dirty = false
            )
        )
    }

    private fun cacheCreate(responseJson: String) {
        val o = parseObj(responseJson) ?: return
        val id = (o["id"] as? JsonPrimitive)?.content ?: return
        val title = (o["title"] as? JsonPrimitive)?.content ?: "Новая страница"
        val cat = (o["cat"] as? JsonPrimitive)?.content ?: ""
        val rev = (o["rev"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
        val items = json.decodeFromString(
            ListSerializer(DocItem.serializer()),
            (o["items"] ?: JsonArray(listOf(json.parseToJsonElement("{}")))).toString()
        )
        app.store.save(
            Document(
                id = id,
                title = title,
                cat = cat,
                rev = rev,
                baseRev = rev,
                items = items.ifEmpty { listOf(DocItem()) },
                updated = System.currentTimeMillis() / 1000,
                dirty = false
            )
        )
    }

    private fun cacheCats(responseJson: String) {
        val cats = json.decodeFromString(ListSerializer(Category.serializer()), responseJson)
        app.store.saveCats(cats)
    }

    private fun cacheBoard(responseJson: String) {
        val o = parseObj(responseJson) ?: return
        val tilesEl = o["tiles"] ?: return
        val tiles = json.decodeFromString(ListSerializer(BoardTile.serializer()), tilesEl.toString())
        app.store.saveBoard(tiles)
    }

    private fun docToApiJson(doc: Document): String = buildJsonObject {
        put("id", doc.id)
        put("title", doc.title)
        put("cat", doc.cat)
        put("rev", doc.rev)
        put("items", json.parseToJsonElement(json.encodeToString(doc.items)))
    }.toString()

    private fun parseObj(s: String?): JsonObject? {
        if (s.isNullOrBlank() || s == "null") return null
        return try {
            json.parseToJsonElement(s) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun queryId(q: String?): String? {
        if (q.isNullOrBlank()) return null
        val m = Regex("""[?&]?id=([^&]+)""").find(q.trim().removePrefix("&").removePrefix("?"))
            ?: Regex("""id=([^&]+)""").find(q)
        return m?.groupValues?.getOrNull(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    }
}
