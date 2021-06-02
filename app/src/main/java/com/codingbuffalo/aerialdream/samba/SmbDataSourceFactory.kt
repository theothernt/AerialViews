package com.codingbuffalo.aerialdream.samba

import com.google.android.exoplayer2.upstream.DataSource

class SmbDataSourceFactory : DataSource.Factory {

    override fun createDataSource(): DataSource {
        //val dataSpec = DataSpec(Uri.parse(fileUrl))
        return SmbDataSource()
    }
}