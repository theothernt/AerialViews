package com.neilturner.aerialviews.providers.webdav

import java.io.IOException

/**
 * A WebDAV request that the server answered with an error status.
 */
internal class WebDavHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)
