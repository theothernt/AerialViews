package com.neilturner.aerialviews.providers.webdav

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import retrofit2.http.Url

// Taken from https://github.com/alexbakker/webdav-provider
interface WebDavService {
    @GET
    @Streaming
    suspend fun get(@Url path: String, @Header("Range") range: String?): Response<ResponseBody>
}
