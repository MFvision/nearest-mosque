package sa.zood.nearmosque.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sa.zood.nearmosque.BuildConfig
import sa.zood.nearmosque.core.Weather
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The real HttpURLConnection request against a local server (no network): GET, the app's User-Agent instead of
 * the platform default MET blocks, If-Modified-Since only when cached, and the body read for 200 only.
 */
class MetWeatherSourceTest {
    private val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
    private val requests = LinkedBlockingQueue<List<String>>()
    private val responses = LinkedBlockingQueue<String>()
    private val ua = Weather.userAgent("Android", BuildConfig.VERSION_NAME)

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                s.use { sock ->
                    val reader = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val lines = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    requests.put(lines)
                    sock.getOutputStream().apply { write(responses.take().toByteArray(Charsets.UTF_8)); flush() }
                }
            }
        }
    }

    @After fun close() = server.close()

    private val url get() = "http://127.0.0.1:${server.localPort}/weatherapi/locationforecast/2.0/compact?lat=21.42&lon=39.83"

    private fun header(lines: List<String>, name: String) =
        lines.drop(1).firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) }?.substringAfter(':')?.trim()

    @Test
    fun sendsTheAppUserAgentAndRevalidatesWithTheStoredDate() = runBlocking {
        val body = """{"properties":{"timeseries":[]}}"""
        responses.put(
            "HTTP/1.1 200 OK\r\nExpires: Sat, 17 Oct 2026 13:47:30 GMT\r\nLast-Modified: Sat, 17 Oct 2026 13:17:28 GMT\r\n" +
                "Content-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body",
        )
        val first = MetWeatherSource().fetch(url, ua, null)!!
        val req = requests.poll(5, TimeUnit.SECONDS)!!
        assertTrue(req[0], req[0].startsWith("GET /weatherapi/locationforecast/2.0/compact?lat=21.42&lon=39.83 "))
        assertEquals(ua, header(req, "User-Agent"))
        val agent = header(req, "User-Agent").orEmpty()
        assertFalse(agent.startsWith("Dalvik") || agent.startsWith("Java") || agent.startsWith("okhttp"))
        assertNull(header(req, "If-Modified-Since"))
        assertNull(header(req, "Cookie"))
        assertEquals(WeatherHttp(200, "Sat, 17 Oct 2026 13:47:30 GMT", "Sat, 17 Oct 2026 13:17:28 GMT", body), first)

        responses.put("HTTP/1.1 304 Not Modified\r\nExpires: Sat, 17 Oct 2026 14:17:30 GMT\r\nConnection: close\r\n\r\n")
        val second = MetWeatherSource().fetch(url, ua, "Sat, 17 Oct 2026 13:17:28 GMT")!!
        val req2 = requests.poll(5, TimeUnit.SECONDS)!!
        assertEquals("Sat, 17 Oct 2026 13:17:28 GMT", header(req2, "If-Modified-Since"))
        assertEquals(ua, header(req2, "User-Agent"))
        assertEquals(WeatherHttp(304, "Sat, 17 Oct 2026 14:17:30 GMT", null, null), second)
    }

    @Test
    fun errorsCarryNoBodyAndNoNetworkIsNull() = runBlocking {
        responses.put("HTTP/1.1 429 Too Many Requests\r\nContent-Length: 4\r\nConnection: close\r\n\r\nslow")
        assertEquals(WeatherHttp(429, null, null, null), MetWeatherSource().fetch(url, ua, null))
        server.close()
        assertNull(MetWeatherSource().fetch(url, ua, null))
    }
}
