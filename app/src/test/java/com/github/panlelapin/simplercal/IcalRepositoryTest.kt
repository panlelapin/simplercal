package com.github.panlelapin.simplercal

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IcalRepositoryTest {
    private fun local() = CalendarSource(CalendarSourceKind.LOCAL, location = "content://test/calendar.ics")
    private fun remote() = CalendarSource(CalendarSourceKind.HTTPS, location = "https://example.invalid/calendar.ics")

    @Test fun localImportClosesTheStreamAndStripsTheUtf8Bom() = runBlocking {
        var closed = false
        val stream = object : ByteArrayInputStream("\uFEFFBEGIN:VCALENDAR".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        val repository = IcalRepository({ stream }, { error("Local import attempted network access") })
        assertEquals("BEGIN:VCALENDAR", repository.readSource(local()))
        assertTrue(closed)
    }

    @Test fun unavailableDocumentAndMalformedUtf8FailExplicitly() {
        assertThrows(IOException::class.java) { runBlocking { IcalRepository({ null }).readSource(local()) } }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            runBlocking { IcalRepository({ ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)) }).readSource(local()) }
        }
    }

    @Test fun oversizedInputIsRejectedAndItsStreamIsClosed() {
        var closed = false
        val stream = object : InputStream() {
            var remaining = 20 * 1024 * 1024 + 1
            override fun read(): Int = if (remaining-- > 0) 65 else -1
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (remaining == 0) return -1
                val count = minOf(remaining, len)
                bytes.fill(65.toByte(), off, off + count)
                remaining -= count
                return count
            }
            override fun close() { closed = true }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { IcalRepository({ stream }).readSource(local()) } }
        assertTrue(closed)
    }

    @Test fun relativeHttpsRedirectsAreFollowedWithTimeoutsAndAllConnectionsClose() = runBlocking {
        val connections = mutableListOf<FakeHttps>()
        val repository = IcalRepository({ error("Remote import opened a document") }, { uri ->
            FakeHttps(uri, if (connections.isEmpty()) 302 else 200,
                if (connections.isEmpty()) "/final.ics" else null).also { connections += it }
        })
        assertEquals("Controlled calendar", repository.readSource(remote()))
        assertEquals(listOf("/calendar.ics", "/final.ics"), connections.map { it.url.path })
        connections.forEach {
            assertTrue(it.disconnected)
            assertEquals(15_000, it.connectTimeout)
            assertEquals(30_000, it.readTimeout)
            assertFalse(it.instanceFollowRedirects)
            assertEquals("text/calendar", it.getRequestProperty("Accept"))
        }
    }

    @Test fun redirectDowngradeAndLoopsAreRejectedBeforeAnInsecureConnectionIsOpened() {
        val opened = mutableListOf<URI>()
        val downgrade = IcalRepository({ null }, { uri -> opened += uri; FakeHttps(uri, 302, "http://example.invalid/insecure") })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { downgrade.readSource(remote()) } }
        assertEquals(1, opened.size)
        opened.clear()
        val loop = IcalRepository({ null }, { uri -> opened += uri; FakeHttps(uri, 302, "/loop") })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { loop.readSource(remote()) } }
        assertEquals(6, opened.size)
    }

    @Test fun httpFailureDoesNotReturnItsErrorBodyAsCalendarData() {
        val connection = FakeHttps(URI(remote().location), 503)
        assertThrows(IOException::class.java) {
            runBlocking { IcalRepository({ null }, { connection }).readSource(remote()) }
        }
        assertTrue(connection.disconnected)
        assertFalse(connection.bodyOpened)
    }

    @Test fun cancellationClosesAnActiveDocumentRead() = runBlocking {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int {
                entered.countDown()
                check(closed.await(5, TimeUnit.SECONDS))
                throw IOException("Controlled close")
            }
            override fun close() { closed.countDown() }
        }
        val repository = IcalRepository({ stream })
        val read = async(Dispatchers.Default) { repository.readSource(local()) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            read.cancelAndJoin()
            assertEquals(0L, closed.count)
            assertTrue(read.isCancelled)
        } finally { read.cancelAndJoin() }
    }

    private class FakeHttps(uri: URI, private val status: Int, private val location: String? = null) :
        HttpsURLConnection(uri.toURL()) {
        var disconnected = false
        var bodyOpened = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String) = if (name == "Location") location else null
        override fun getInputStream(): InputStream {
            bodyOpened = true
            return ByteArrayInputStream("Controlled calendar".toByteArray())
        }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() {}
        override fun getCipherSuite() = "TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
