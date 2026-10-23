package com.neilturner.aerialviews.providers.webdav

import okhttp3.OkHttpClient

/**
 * The contract as it is met by the current client. The suite was written against the sardine
 * implementation the app used before, so it describes the behaviour the app depends on.
 */
internal class OkHttpWebDavClientContractTest : WebDavListingClientContract() {
    override fun createClient(): WebDavListingClient =
        OkHttpWebDavClient(OkHttpClient()).apply {
            setCredentials("user", "password", true)
        }
}
