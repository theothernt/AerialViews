package com.neilturner.aerialviews.services

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream

class InputStreamFetcher(
    private val inputStream: InputStream,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        return withContext(Dispatchers.IO) {
            try {
                // Check if the InputStream supports mark/reset
                val bufferedStream = if (inputStream.markSupported()) {
                    Timber.Forest.d("InputStream already supports mark/reset")
                    inputStream
                } else {
                    Timber.Forest.d("Wrapping InputStream in BufferedInputStream")
                    BufferedInputStream(inputStream)
                }

                // Use Okio to create a source from the buffered stream
                val source = bufferedStream.source().buffer()

                // Create a SourceResult with the buffered stream
                SourceFetchResult(
                    source = ImageSource(
                        source = source,
                        fileSystem = options.fileSystem,
                        metadata = null,
                    ),
                    mimeType = null,
                    dataSource = DataSource.NETWORK
                )
            } catch (e: Exception) {
                Timber.Forest.e(e, "Error fetching data from InputStream")
                throw e
            }
        }
    }

    class Factory : Fetcher.Factory<InputStream> {
        override fun create(
            data: InputStream,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            return InputStreamFetcher(data, options)
        }
    }
}