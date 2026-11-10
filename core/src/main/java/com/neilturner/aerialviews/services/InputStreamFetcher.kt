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
import java.io.InputStream

class InputStreamFetcher(
    private val inputStream: InputStream,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult =
        withContext(Dispatchers.IO) {
            try {
                // Use Okio to create a source from the buffered stream
                val source = inputStream.source().buffer()

                // Create a SourceResult with the buffered stream
                SourceFetchResult(
                    source =
                        ImageSource(
                            source = source,
                            fileSystem = options.fileSystem,
                            metadata = null,
                        ),
                    mimeType = null,
                    dataSource = DataSource.NETWORK,
                )
            } catch (e: Exception) {
                Timber.e(e, "Error fetching data from InputStream")
                throw e
            }
        }

    class Factory : Fetcher.Factory<InputStream> {
        override fun create(
            data: InputStream,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = InputStreamFetcher(data, options)
    }
}
