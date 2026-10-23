package com.neilturner.aerialviews.providers.webdav

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * The streaming path of the WebDAV data source, which is what the video player reads from.
 */
internal class WebDavDataSourceTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `opens a file and reports the total size from the content range`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-4/10")
                .setBody("01234"),
        )

        val dataSource = WebDavDataSource()
        val length = dataSource.open(dataSpec())

        assertEquals(10L, length)
        val buffer = ByteArray(5)
        assertEquals(5, dataSource.read(buffer, 0, 5))
        assertArrayEquals("01234".toByteArray(), buffer)
        dataSource.close()
    }

    @Test
    fun `falls back to the body length when the server ignores the range`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("0123456789"),
        )

        val dataSource = WebDavDataSource()
        val length = dataSource.open(dataSpec())

        assertEquals(10L, length)
        dataSource.close()
    }

    @Test
    fun `sends the range header and the credentials`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-4/10")
                .setBody("01234"),
        )

        val dataSource = WebDavDataSource()
        dataSource.open(dataSpec())
        dataSource.close()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("bytes=0-", request.getHeader("Range"))
        assertEquals(
            "Basic " +
                java.util.Base64
                    .getEncoder()
                    .encodeToString("user:password".toByteArray()),
            request.getHeader("Authorization"),
        )
        assertEquals("/media/clip.mp4", request.path)
    }

    @Test
    fun `fails when the server answers with an error`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))

        val dataSource = WebDavDataSource()

        assertThrows(IOException::class.java) {
            dataSource.open(dataSpec())
        }
    }

    private fun dataSpec(): DataSpec {
        val uri =
            mockk<Uri>().also {
                every { it.toString() } returns "http://user:password@localhost:${server.port}/media/clip.mp4"
                every { it.userInfo } returns "user:password"
            }
        return DataSpec(uri, 0L, C.LENGTH_UNSET.toLong(), null)
    }
}
