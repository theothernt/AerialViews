@file:Suppress("DEPRECATION")

package com.neilturner.aerialviews.providers.webdav

import com.neilturner.aerialviews.BuildConfig
import com.thegrizzlylabs.sardineandroid.model.Prop
import com.thegrizzlylabs.sardineandroid.model.Property
import com.thegrizzlylabs.sardineandroid.model.Resourcetype
import com.thegrizzlylabs.sardineandroid.util.EntityWithAnyElementConverter
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.simpleframework.xml.Serializer
import org.simpleframework.xml.convert.Registry
import org.simpleframework.xml.convert.RegistryStrategy
import org.simpleframework.xml.core.Persister
import org.simpleframework.xml.strategy.Strategy
import org.simpleframework.xml.stream.Format
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.IOException
import java.io.InputStream

// Taken from https://github.com/alexbakker/webdav-provider
class WebDavClient(
    url: HttpUrl,
    creds: WebDavCredentials? = null
) {
    private val api: WebDavService = buildApiService(url, creds)

    data class Result<T>(
        val body: T? = null,
        val headers: Headers? = null,
        val contentLength: Long? = null,
        val error: Exception? = null
    ) {
        val isSuccessful: Boolean
            get() {
                return error == null
            }
    }

    suspend fun get(path: String, offset: Long = 0): Result<InputStream> {
        val res = execRequest { api.get(path, if (offset == 0L) null else "bytes=$offset-") }
        if (!res.isSuccessful) {
            return Result(error = res.error)
        }

        val contentLength = res.body?.contentLength()
        return Result(
            res.body?.byteStream(),
            contentLength = if (contentLength == -1L) null else contentLength
        )
    }

    suspend fun get(path: String): Result<InputStream> {
        return get(path, 0)
    }

    private suspend fun <T> execRequest(exec: suspend () -> Response<T>): Result<T> {
        var res: Response<T>? = null
        try {
            res = exec()
            if (!res.isSuccessful) {
                throw IOException("Status code: ${res.code()}")
            }
        } catch (e: IOException) {
            return Result(headers = res?.headers(), error = e)
        }

        return Result(body = res.body(), headers = res.headers())
    }

    private class BasicAuthInterceptor(val auth: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val req = chain.request().newBuilder()
                .header("Authorization", auth)
                .build()
            return chain.proceed(req)
        }
    }

    private fun buildApiService(url: HttpUrl, creds: WebDavCredentials?): WebDavService {
        val builder = OkHttpClient.Builder()

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.BASIC
            builder.addInterceptor(logging)
        }

        if (creds != null) {
            val auth = Credentials.basic(creds.username, creds.password)
            builder.addInterceptor(BasicAuthInterceptor(auth))
        }

        val serializer = buildSerializer()
        val converter = SimpleXmlConverterFactory.create(serializer)
        return Retrofit.Builder()
            .baseUrl(url)
            .client(builder.build())
            .addConverterFactory(converter)
            .build()
            .create(WebDavService::class.java)
    }

    private fun buildSerializer(): Serializer {
        // source: https://git.io/Jkf9B
        val format = Format("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        val registry = Registry()
        val strategy: Strategy = RegistryStrategy(registry)
        val serializer: Serializer = Persister(strategy, format)
        registry.bind(
            Prop::class.java,
            EntityWithAnyElementConverter(serializer, Prop::class.java)
        )
        registry.bind(
            Resourcetype::class.java,
            EntityWithAnyElementConverter(serializer, Resourcetype::class.java)
        )
        registry.bind(Property::class.java, Property.PropertyConverter::class.java)
        return serializer
    }
}
