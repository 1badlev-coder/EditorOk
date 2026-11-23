package site.kentok.lists.data.local

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import site.kentok.lists.crypto.DocCrypto
import site.kentok.lists.data.model.BoardTile
import site.kentok.lists.data.model.Category
import site.kentok.lists.data.model.Document
import java.io.File
import javax.crypto.spec.SecretKeySpec

/**
 * Зашифрованные документы на устройстве — строго по userId (`docs_<uid>/`).
 * Чужие каталоги не читаем и не подмешиваем.
 */
class EncryptedDocStore(
    context: Context,
    private val session: SessionStore
) {
    private val root = context.filesDir
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var boundUid: Int = 0
    private var dir: File = File(root, "docs_0").also { it.mkdirs() }
    private var boardFile: File = File(root, "board_0.bin")
    private var catsFile: File = File(root, "cats_0.bin")
    /** Кэш PBKDF2 — иначе каждый load/listMeta на UI зависает на секунды. */
    @Volatile
    private var cachedKey: SecretKeySpec? = null
    @Volatile
    private var cachedKeyFp: String = ""

    /** Привязать хранилище к пользователю. Без bind — пустой каталог docs_0. */
    @Synchronized
    fun bindUser(uid: Int) {
        val id = uid.coerceAtLeast(0)
        if (id == boundUid && dir.exists()) return
        boundUid = id
        cachedKey = null
        cachedKeyFp = ""
        dir = File(root, "docs_$id").also { it.mkdirs() }
        boardFile = File(root, "board_$id.bin")
        catsFile = File(root, "cats_$id.bin")
        // разовый перенос старого общего каталога docs/ → docs_<uid>
        if (id > 0) {
            val legacy = File(root, "docs")
            if (legacy.isDirectory) {
                legacy.listFiles()?.forEach { f ->
                    val dest = File(dir, f.name)
                    if (!dest.exists()) f.copyTo(dest, overwrite = false)
                }
                legacy.listFiles()?.forEach { it.delete() }
                legacy.delete()
            }
            val legacyBoard = File(root, "board.bin")
            if (legacyBoard.exists() && !boardFile.exists()) {
                legacyBoard.copyTo(boardFile, overwrite = false)
                legacyBoard.delete()
            }
        }
    }

    fun currentUserId(): Int = boundUid

    private fun key(): SecretKeySpec {
        val login = session.login
        val pass = session.password
        val saltB64 = session.saltB64
        val fp = "$boundUid|$login|$pass|$saltB64"
        cachedKey?.let { if (cachedKeyFp == fp) return it }
        val salt = if (saltB64.isBlank()) {
            val s = DocCrypto.newSalt()
            session.saltB64 = DocCrypto.b64(s)
            s
        } else DocCrypto.fromB64(saltB64)
        val k = DocCrypto.deriveKey(login, pass, salt)
        cachedKey = k
        cachedKeyFp = "$boundUid|$login|$pass|${session.saltB64}"
        return k
    }

    private fun file(id: String) = File(dir, "$id.bin")

    fun listIds(): List<String> =
        dir.listFiles()?.mapNotNull { f ->
            f.name.removeSuffix(".bin").takeIf { f.name.endsWith(".bin") }
        }?.sorted() ?: emptyList()

    fun load(id: String): Document? {
        val f = file(id)
        if (!f.exists()) return null
        return try {
            val plain = DocCrypto.decrypt(f.readBytes(), key())
            json.decodeFromString<Document>(plain.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    fun save(doc: Document) {
        val plain = json.encodeToString(doc).toByteArray(Charsets.UTF_8)
        val blob = DocCrypto.encrypt(plain, key())
        file(doc.id).writeBytes(blob)
    }

    fun delete(id: String) {
        file(id).delete()
    }

    fun listMeta(): List<Document> =
        listIds().mapNotNull { load(it) }.sortedByDescending { it.updated }

    /** Очистить только текущего пользователя. */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
        boardFile.delete()
        catsFile.delete()
    }

    /** Полностью удалить данные пользователя с устройства (смена/выход). */
    @Synchronized
    fun wipeUser(uid: Int) {
        val id = uid.coerceAtLeast(0)
        if (id <= 0) return
        File(root, "docs_$id").deleteRecursively()
        File(root, "board_$id.bin").delete()
        File(root, "cats_$id.bin").delete()
        if (boundUid == id) {
            dir.listFiles()?.forEach { it.delete() }
            boardFile.delete()
            catsFile.delete()
        }
    }

    fun saveBoard(tiles: List<BoardTile>) {
        val plain = json.encodeToString(tiles).toByteArray(Charsets.UTF_8)
        boardFile.writeBytes(DocCrypto.encrypt(plain, key()))
    }

    fun loadBoard(): List<BoardTile> {
        if (!boardFile.exists()) return emptyList()
        return try {
            val plain = DocCrypto.decrypt(boardFile.readBytes(), key())
            json.decodeFromString<List<BoardTile>>(plain.decodeToString())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCats(cats: List<Category>) {
        val plain = json.encodeToString(cats).toByteArray(Charsets.UTF_8)
        catsFile.writeBytes(DocCrypto.encrypt(plain, key()))
    }

    fun loadCats(): List<Category> {
        if (!catsFile.exists()) return emptyList()
        return try {
            val plain = DocCrypto.decrypt(catsFile.readBytes(), key())
            json.decodeFromString<List<Category>>(plain.decodeToString())
        } catch (_: Exception) {
            emptyList()
        }
    }
}
