package site.kentok.lists.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.kentok.lists.data.local.EncryptedDocStore
import site.kentok.lists.data.model.ConflictChoice
import site.kentok.lists.data.model.Document
import site.kentok.lists.data.remote.ApiClient
import site.kentok.lists.data.remote.ApiException

sealed class SyncResult {
    data object Ok : SyncResult()
    data class Conflict(val local: Document, val serverRev: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
    data object Offline : SyncResult()
}

fun interface SyncProgress {
    fun onProgress(label: String, percent: Int)
}

/**
 * Синхронизация ТОЛЬКО документов / доски / папок пользователя.
 * Файлы редактора (оболочка сайта в WebView) сюда не входят и не обновляются.
 */
class SyncEngine(
    private val api: ApiClient,
    private val store: EncryptedDocStore
) {
    suspend fun syncAll(
        online: Boolean,
        onProgress: SyncProgress? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!online) return@withContext SyncResult.Offline
        try {
            fun report(label: String, done: Int, total: Int, part: Int = 0) {
                val base = if (total <= 0) 0 else (done * 100) / total
                val slice = if (total <= 0) part else (part / total).coerceIn(0, 99 / total.coerceAtLeast(1))
                onProgress?.onProgress(label, (base + slice).coerceIn(0, 99))
            }

            onProgress?.onProgress("Список документов…", 0)
            val remote = api.list()

            // Только изменившиеся: нет локально / новее на сервере / локальный черновик
            val toPull = remote.filter { meta ->
                val local = store.load(meta.id)
                when {
                    local == null -> true
                    local.dirty -> false
                    meta.rev > local.rev -> true
                    meta.updated > 0L && local.updated > 0L && meta.updated > local.updated -> true
                    meta.updated > 0L && local.updated <= 0L -> true
                    else -> false
                }
            }
            val toPush = store.listIds().mapNotNull { id ->
                store.load(id)?.takeIf { it.dirty }
            }
            if (toPull.isEmpty() && toPush.isEmpty()) {
                onProgress?.onProgress("Нет изменений", 100)
                return@withContext SyncResult.Ok
            }
            val totalSteps = 1 + toPull.size + toPush.size + 1 // list + pulls + pushes + finish
            var done = 1
            report("Список документов…", done, totalSteps, 0)

            for ((i, meta) in toPull.withIndex()) {
                report("Загрузка «${meta.title.ifBlank { meta.id }}»…", done, totalSteps, 0)
                val loaded = api.load(meta.id)
                val doc = loaded.copy(
                    updated = when {
                        loaded.updated > 0L -> loaded.updated
                        meta.updated > 0L -> meta.updated
                        else -> System.currentTimeMillis() / 1000
                    },
                    dirty = false,
                    rev = loaded.rev.takeIf { it > 0 } ?: meta.rev,
                    baseRev = (loaded.rev.takeIf { it > 0 } ?: meta.rev).coerceAtLeast(0)
                )
                store.save(doc)
                done++
                report("Загрузка документов…", done, totalSteps, ((i + 1) * 100) / toPull.size.coerceAtLeast(1))
            }

            // A: перед push сверяем rev с сервером — меньше ложных конфликтов после list
            val remoteById = remote.associateBy { it.id }
            for (local in toPush) {
                report("Отправка «${local.title.ifBlank { local.id }}»…", done, totalSteps, 0)
                val meta = remoteById[local.id]
                if (meta != null && meta.rev > local.baseRev && !local.dirty) {
                    done++
                    continue
                }
                if (meta != null && meta.rev > local.baseRev && local.dirty) {
                    // сервер ушёл вперёд при локальном dirty — конфликт сразу
                    onProgress?.onProgress("Конфликт", 100)
                    return@withContext SyncResult.Conflict(local, meta.rev)
                }
                val save = api.save(local)
                if (save.error == "conflict") {
                    onProgress?.onProgress("Конфликт", 100)
                    return@withContext SyncResult.Conflict(local, save.rev)
                }
                if (save.ok) {
                    val now = if (save.updated > 0L) save.updated else System.currentTimeMillis() / 1000
                    store.save(
                        local.copy(
                            rev = save.rev,
                            baseRev = save.rev,
                            dirty = false,
                            updated = now
                        )
                    )
                }
                done++
                report("Отправка изменений…", done, totalSteps, 0)
            }

            onProgress?.onProgress("Готово", 100)
            SyncResult.Ok
        } catch (e: ApiException) {
            SyncResult.Error(e.message ?: "Ошибка синхронизации")
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Ошибка синхронизации")
        }
    }

    suspend fun pullOne(id: String): Document = withContext(Dispatchers.IO) {
        val doc = api.load(id)
        val saved = doc.copy(dirty = false, baseRev = doc.rev)
        store.save(saved)
        saved
    }

    suspend fun saveDocument(
        doc: Document,
        online: Boolean,
        conflictChoice: ConflictChoice? = null,
        serverRev: Int = 0
    ): SyncResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        when (conflictChoice) {
            ConflictChoice.REPLACE -> {
                if (!online) {
                    store.save(doc.copy(dirty = true, updated = now))
                    return@withContext SyncResult.Offline
                }
                val save = api.save(doc, forceRev = serverRev)
                if (!save.ok) return@withContext SyncResult.Error(save.error ?: "Ошибка")
                val done = doc.copy(rev = save.rev, baseRev = save.rev, dirty = false, updated = now)
                store.save(done)
                SyncResult.Ok
            }
            ConflictChoice.RENAME -> {
                if (!online) {
                    val newId = "local_${now}_${(1000..9999).random()}"
                    val renamed = doc.copy(
                        id = newId,
                        title = "${doc.title} (копия)",
                        rev = 0,
                        baseRev = 0,
                        dirty = true,
                        updated = now
                    )
                    store.save(renamed)
                    return@withContext SyncResult.Ok
                }
                val created = api.create()
                val renamed = doc.copy(
                    id = created.id,
                    title = "${doc.title} (копия)",
                    rev = 0,
                    baseRev = created.rev,
                    dirty = false,
                    updated = now
                )
                val save = api.save(renamed.copy(baseRev = created.rev))
                if (!save.ok && save.error == "conflict") {
                    return@withContext SyncResult.Conflict(renamed, save.rev)
                }
                val done = renamed.copy(rev = save.rev, baseRev = save.rev, dirty = false)
                store.save(done)
                SyncResult.Ok
            }
            ConflictChoice.CANCEL, null -> {
                store.save(doc.copy(dirty = true, updated = now))
                if (!online) return@withContext SyncResult.Offline
                val save = api.save(doc)
                if (save.error == "conflict") {
                    return@withContext SyncResult.Conflict(doc, save.rev)
                }
                if (!save.ok) return@withContext SyncResult.Error(save.error ?: "Ошибка")
                store.save(doc.copy(rev = save.rev, baseRev = save.rev, dirty = false, updated = now))
                SyncResult.Ok
            }
        }
    }
}
