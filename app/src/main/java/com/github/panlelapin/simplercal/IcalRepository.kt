package com.github.panlelapin.simplercal

import android.content.Context
import android.net.Uri
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Only explicit source selection/Refresh may call readSource; cached snapshots are separate. */
internal class IcalRepository(
    private val openDocument: (String) -> InputStream?,
    private val openHttps: (URI) -> HttpsURLConnection = { it.toURL().openConnection() as HttpsURLConnection },
) : FileCalendarReader {
    constructor(context: Context) : this({ location ->
        val uri = Uri.parse(location)
        require(uri.scheme == "content")
        context.contentResolver.openInputStream(uri)
    })
    override suspend fun readSource(source: CalendarSource): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val input = AtomicReference<Closeable?>()
            val connection = AtomicReference<HttpsURLConnection?>()
            continuation.invokeOnCancellation {
                connection.get()?.disconnect()
                try { input.get()?.close() } catch (_: IOException) { /* already closing */ }
            }
            try {
                val bytes = if (source.kind == CalendarSourceKind.LOCAL) {
                    val stream = openDocument(source.location) ?: throw IOException()
                    input.set(stream)
                    stream.use { readLimited(it) { continuation.isActive } }
                } else {
                    var address = httpsAddress(source.location)
                    var result: ByteArray? = null
                    for (redirect in 0..5) {
                        check(continuation.isActive)
                        val current = openHttps(address)
                        connection.set(current)
                        current.connectTimeout = 15_000
                        current.readTimeout = 30_000
                        current.instanceFollowRedirects = false
                        current.setRequestProperty("Accept", "text/calendar")
                        try {
                            val status = current.responseCode
                            if (status in listOf(301, 302, 303, 307, 308)) {
                                require(redirect < 5)
                                address = httpsAddress(address.resolve(current.getHeaderField("Location") ?: throw IOException()).toString())
                            } else {
                                if (status !in 200..299) throw IOException()
                                val stream = current.inputStream
                                input.set(stream)
                                result = stream.use { readLimited(it) { continuation.isActive } }
                                break
                            }
                        } finally { current.disconnect() }
                    }
                    result ?: throw IOException()
                }
                // RFC 5545 default encoding; malformed UTF-8 is an explicit import failure.
                val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
                if (continuation.isActive) continuation.resume(text)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                connection.get()?.disconnect()
                try { input.get()?.close() } catch (_: IOException) { /* already closed */ }
            }
        }
    }
}

internal fun httpsAddress(value: String): URI {
    val uri = URI(value.trim())
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.fragment == null)
    return uri
}

private fun readLimited(input: InputStream, active: () -> Boolean): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        check(active())
        val size = input.read(buffer)
        if (size < 0) break
        require(output.size() + size <= 20 * 1024 * 1024)
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}
