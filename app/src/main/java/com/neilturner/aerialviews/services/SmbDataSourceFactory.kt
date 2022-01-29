package com.neilturner.aerialviews.services

import com.google.android.exoplayer2.upstream.DataSource

class SmbDataSourceFactory : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return SmbDataSource()
    }
}
