package com.neilturner.aerialviews.providers.webdav

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * A WebDAV client that lists a folder with a `PROPFIND` request.
 *
 * It only asks for the properties the app uses, and the response is parsed by
 * [DavMultistatusParser].
 */
internal class OkHttpWebDavClient(
    okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) : WebDavListingClient {
    private var userName: String = ""
    private var password: String = ""
    private var preemptive: Boolean = true

    private var client: OkHttpClient =
        okHttpClient
            .newBuilder()
            .authenticator { _, response ->
                if (response.request.header("Authorization") != null) {
                    null
                } else {
                    response.request
                        .newBuilder()
                        .header("Authorization", Credentials.basic(userName, password))
                        .build()
                }
            }.build()

    override fun setCredentials(
        userName: String,
        password: String,
        preemptive: Boolean,
    ) {
        this.userName = userName
        this.password = password
        this.preemptive = preemptive
    }

    override fun list(url: String): List<WebDavResourceInfo> {
        val request =
            Request
                .Builder()
                .url(url)
                .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
                .header("Depth", "1")
                .apply {
                    if (preemptive && userName.isNotEmpty()) {
                        header("Authorization", Credentials.basic(userName, password))
                    }
                }.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavHttpException(
                    response.code,
                    "Invalid response: ${response.code} ${response.message}",
                )
            }

            val body = response.body.byteStream() ?: throw IOException("Empty response from $url")
            return DavMultistatusParser.parse(body).map { it.toResourceInfo() }
        }
    }

    companion object {
        private val XML_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()

        private val PROPFIND_BODY =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:"><D:prop>
            <D:resourcetype/><D:getlastmodified/><D:getcontentlength/><D:displayname/>
            </D:prop></D:propfind>
            """.trimIndent()
    }
}
