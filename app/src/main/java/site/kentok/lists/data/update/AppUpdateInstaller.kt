package site.kentok.lists.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import site.kentok.lists.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

class AppUpdateInstaller(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var installReceiver: BroadcastReceiver? = null

    fun canInstall(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Скачать APK во внутреннее хранилище (не cache — надёжнее для установщика).
     * @param onProgress 0..100
     */
    fun download(urlPath: String, onProgress: (Int) -> Unit): File {
        val base = BuildConfig.BASE_URL.trimEnd('/') + "/"
        val url = if (urlPath.startsWith("http")) urlPath else base + urlPath.trimStart('/')
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Не удалось скачать обновление (${resp.code})")
            val body = resp.body ?: throw Exception("Пустой ответ сервера")
            val total = body.contentLength()
            val dir = File(context.filesDir, "updates").also { it.mkdirs() }
            val out = File(dir, "editor-ok-update.apk")
            if (out.exists()) out.delete()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else 50
                        onProgress(pct)
                    }
                    output.flush()
                }
            }
            onProgress(100)
            if (out.length() < 10_000L) throw Exception("Файл обновления повреждён")
            // ZIP/APK magic
            out.inputStream().use { ins ->
                val magic = ByteArray(4)
                if (ins.read(magic) != 4 || magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte()) {
                    out.delete()
                    throw Exception("Скачан не APK-файл. Повторите обновление.")
                }
            }
            return out
        }
    }

    /**
     * Установка через PackageInstaller (показывает системный экран подтверждения).
     * Fallback — ACTION_VIEW + FileProvider с выдачей URI установщику.
     */
    fun install(apk: File) {
        if (!apk.exists() || apk.length() < 10_000L) {
            throw Exception("Файл обновления не найден")
        }
        if (!canInstall()) {
            openInstallPermissionSettings()
            throw Exception("Разрешите установку из этого источника и нажмите «Обновить» снова")
        }
        try {
            installViaSession(apk)
        } catch (e: Exception) {
            try {
                installViaViewIntent(apk)
            } catch (e2: Exception) {
                throw Exception(
                    e2.message?.takeIf { it.isNotBlank() }
                        ?: e.message
                        ?: "Не удалось открыть установщик"
                )
            }
        }
    }

    private fun installViaSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        try {
            params.setAppPackageName(context.packageName)
        } catch (_: Exception) {}
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apk.inputStream().buffered().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val action = "${context.packageName}.UPDATE_INSTALL_RESULT"
            unregisterInstallReceiver()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirm = extrasIntent(intent)
                            if (confirm != null) {
                                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    ctx.startActivity(confirm)
                                } catch (_: Exception) {
                                    installViaViewIntent(apk)
                                }
                            } else {
                                installViaViewIntent(apk)
                            }
                        }
                        PackageInstaller.STATUS_SUCCESS -> { }
                        PackageInstaller.STATUS_FAILURE,
                        PackageInstaller.STATUS_FAILURE_ABORTED,
                        PackageInstaller.STATUS_FAILURE_BLOCKED,
                        PackageInstaller.STATUS_FAILURE_CONFLICT,
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                        PackageInstaller.STATUS_FAILURE_INVALID,
                        PackageInstaller.STATUS_FAILURE_STORAGE -> {
                            // пользователь увидит системное сообщение; повтор — через диалог обновления
                        }
                    }
                    if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        unregisterInstallReceiver()
                    }
                }
            }
            installReceiver = receiver
            val filter = IntentFilter(action)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(action).setPackage(context.packageName),
                flags
            )
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            try { session.abandon() } catch (_: Exception) {}
            throw e
        } finally {
            try { session.close() } catch (_: Exception) {}
        }
    }

    private fun extrasIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    private fun unregisterInstallReceiver() {
        val r = installReceiver ?: return
        installReceiver = null
        try { context.unregisterReceiver(r) } catch (_: Exception) {}
    }

    private fun installViaViewIntent(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val flags = if (Build.VERSION.SDK_INT >= 33) {
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        } else null
        val handlers = if (flags != null) {
            context.packageManager.queryIntentActivities(intent, flags)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        for (ri in handlers) {
            context.grantUriPermission(
                ri.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        // На части прошивок нужен явный grant пакету установщика
        try {
            context.grantUriPermission(
                "com.google.android.packageinstaller",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        try {
            context.grantUriPermission(
                "com.android.packageinstaller",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        context.startActivity(intent)
    }
}
