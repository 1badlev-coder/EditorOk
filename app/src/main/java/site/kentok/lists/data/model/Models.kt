package site.kentok.lists.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DocItem(
    val t: String = "",
    val h: Boolean = true,
    val l: Int = 0,
    val c: Boolean = false,
    val n: Boolean = false,
    val f: String = "",
    val s: Int = 0,
    val col: String = "",
    val img: String = "",
    val iw: Int = 0,
    val ix: Int = 0,
    val iy: Int = 0,
    val ip: String = "below"
)

@Serializable
data class Document(
    val id: String = "",
    val title: String = "Новая страница",
    val cat: String = "",
    val rev: Int = 0,
    val items: List<DocItem> = listOf(DocItem()),
    val updated: Long = 0L,
    /** локальный черновик ещё не отправлен */
    val dirty: Boolean = false,
    /** база ревизии сервера, с которой сравниваем */
    val baseRev: Int = 0
)

@Serializable
data class DocMeta(
    val id: String,
    val title: String = "Без названия",
    val cat: String = "",
    val updated: Long = 0L,
    val rev: Int = 0
)

@Serializable
data class Category(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class LoginResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val id: Int = 0,
    val nick: String = "",
    val email: String = "",
    val role: String = "user",
    @SerialName("pinOn") val pinOn: Boolean = false,
    val view: String = ""
)

@Serializable
data class ViewResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val view: String = ""
)

@Serializable
data class AppVerResponse(
    val ok: Boolean = false,
    val update: Boolean = false,
    val id: String = "",
    val name: String = "",
    val size: Long = 0,
    val t: Long = 0,
    val vc: Int = 0,
    val vn: String = "",
    val url: String = ""
)

@Serializable
data class PingResponse(
    val ok: Boolean = false,
    val active: Boolean? = null
)

@Serializable
data class SaveResponse(
    val ok: Boolean = false,
    val rev: Int = 0,
    val updated: Long = 0L,
    val error: String? = null
)

@Serializable
data class CreateResponse(
    val id: String = "",
    val title: String = "",
    val cat: String = "",
    val rev: Int = 1,
    val updated: Long = 0L,
    val items: List<DocItem> = emptyList(),
    val error: String? = null
)

@Serializable
data class Reminder(
    val type: String = "once",
    val at: String = "",
    val time: String = "",
    val times: List<String> = emptyList(),
    val days: List<Int> = emptyList(),
    val mel: String = "m1",
    val fired: Int = 0,
    val snooze: Int = 0,
    val ack: Int = 0,
    val last: String = ""
)

@Serializable
data class TileTimer(
    val sec: Int = 60,
    val mel: String = "m1",
    val end: Long = 0L,
    val fired: Long = 0L
)

@Serializable
data class BoardTile(
    val doc: String = "",
    val color: String = "",
    val note: String = "",
    val rem: Reminder? = null,
    val timer: TileTimer? = null,
    val saved: Boolean = false
)

@Serializable
data class BoardResponse(
    val tiles: List<BoardTile> = emptyList()
)

@Serializable
data class OkResponse(
    val ok: Boolean = false,
    val error: String? = null,
    @SerialName("pinOn") val pinOn: Boolean? = null,
    val view: String? = null
)

@Serializable
data class UploadResponse(
    val name: String = "",
    val error: String? = null
)

@Serializable
data class RecoverResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

enum class ConflictChoice { REPLACE, RENAME, CANCEL }

/** Главный рабочий экран после входа — как на сайте (редактор). */
enum class AppScreen { LOGIN, REGISTER, RECOVER, PIN, WORK }

enum class WorkPanel { EDITOR, BOARD, GUIDE }
