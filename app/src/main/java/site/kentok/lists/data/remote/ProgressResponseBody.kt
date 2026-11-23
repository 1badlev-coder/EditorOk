package site.kentok.lists.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

/** Callback for response-body download progress (bytes). */
fun interface DownloadProgressListener {
    fun onProgress(bytesRead: Long, contentLength: Long, done: Boolean)
}

/**
 * Wraps a ResponseBody and reports read progress.
 * When Content-Length is unknown, reports best-effort percent via [percentOf].
 */
class ProgressResponseBody(
    private val delegate: ResponseBody,
    private val listener: DownloadProgressListener
) : ResponseBody() {
    private var counted: BufferedSource? = null

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource {
        if (counted == null) {
            counted = source(delegate.source()).buffer()
        }
        return counted!!
    }

    private fun source(source: Source): Source = object : ForwardingSource(source) {
        private var totalBytesRead = 0L
        override fun read(sink: Buffer, byteCount: Long): Long {
            val bytesRead = super.read(sink, byteCount)
            val done = bytesRead == -1L
            totalBytesRead += if (done) 0 else bytesRead
            val len = contentLength()
            listener.onProgress(totalBytesRead, len, done)
            return bytesRead
        }
    }

    companion object {
        /** 0–100; unknown length asymptotes toward 90 until done. */
        fun percentOf(bytesRead: Long, contentLength: Long, done: Boolean): Int {
            if (done) return 100
            if (contentLength > 0L) {
                return ((bytesRead * 100L) / contentLength).toInt().coerceIn(0, 99)
            }
            // ~50 KB → ~45%, ~200 KB → ~72%, soft-cap 90
            val soft = (90.0 * (1.0 - 1.0 / (1.0 + bytesRead / 50_000.0))).toInt()
            return soft.coerceIn(0, 90)
        }
    }
}

fun progressInterceptor(listener: () -> DownloadProgressListener?): Interceptor =
    Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val body = response.body ?: return@Interceptor response
        val cb = listener() ?: return@Interceptor response
        response.newBuilder()
            .body(ProgressResponseBody(body, cb))
            .build()
    }
