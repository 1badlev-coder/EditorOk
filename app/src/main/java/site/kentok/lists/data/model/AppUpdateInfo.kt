package site.kentok.lists.data.model

data class AppUpdateInfo(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val size: Long,
    val urlPath: String
)
