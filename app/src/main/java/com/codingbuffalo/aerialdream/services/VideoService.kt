package com.codingbuffalo.aerialdream.services

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.codingbuffalo.aerialdream.models.videos.Video
import com.codingbuffalo.aerialdream.models.VideoPlaylist
import com.codingbuffalo.aerialdream.models.VideoSource
import com.codingbuffalo.aerialdream.models.videos.SimpleVideo
import com.codingbuffalo.aerialdream.providers.Apple2019Provider
import com.codingbuffalo.aerialdream.providers.VideoProvider
import java.util.*

class VideoService(context: Context, private val videoSource: Int, private val source_apple_2019: String) {
    private val repositories: MutableList<VideoProvider> = LinkedList()
    private val context: Context = context.applicationContext

    init {
        repositories.add(Apple2019Provider())
    }

    fun fetchVideos(): VideoPlaylist {
        val videos = buildVideoList()
        return VideoPlaylist(videos.toMutableList())
    }

    private fun buildVideoList(): List<Video> {
        val remoteVideos: MutableList<Video> = ArrayList()
        var localVideos: List<String?> = ArrayList()
        val videos: MutableList<Video> = ArrayList()

        for (repository in repositories) {
            remoteVideos.addAll(repository.fetchVideos(context))
        }

        if (videoSource != VideoSource.REMOTE) {
            localVideos = allMedia()
        }

        for (video in remoteVideos) {
            val remoteUri = video.uri(source_apple_2019)
            val remoteFilename = remoteUri!!.lastPathSegment!!.toLowerCase(Locale.ROOT)
            if (videoSource == VideoSource.REMOTE) {
                Log.i("FetchVideosTask", "Remote video: $remoteFilename")
                videos.add(SimpleVideo(remoteUri, video.location))
                continue
            }
            if (videoSource != VideoSource.REMOTE) {
                val localUri = findLocalVideo(localVideos, remoteFilename)
                if (localUri != null) {
                    Log.i("FetchVideosTask", "Local video: " + localUri.lastPathSegment)
                    videos.add(SimpleVideo(localUri, video.location))
                } else if (videoSource == VideoSource.LOCAL_AND_REMOTE) {
                    Log.i("FetchVideosTask", "Remote video: $remoteFilename")
                    videos.add(SimpleVideo(remoteUri, video.location))
                }
            }
        }
        Log.i("FetchVideosTask", "Videos found: " + videos.size)
        return videos
    }

    private fun findLocalVideo(localVideos: List<String?>, remoteFilename: String): Uri? {
        for (localUrl in localVideos) {
            val localUri = Uri.parse(localUrl)
            val localFilename = localUri.lastPathSegment!!.toLowerCase(Locale.ROOT)
            if (localFilename.contains(remoteFilename)) {
                return localUri
            }
        }
        return null
    }

    private fun allMedia(): ArrayList<String?> {
            val videoItemHashSet = HashSet<String?>()
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val column = "_data"
            val projection = arrayOf(column)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            try {
                cursor!!.moveToFirst()
                do {
                    videoItemHashSet.add(cursor.getString(cursor.getColumnIndexOrThrow(column)))
                } while (cursor.moveToNext())
                cursor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ArrayList(videoItemHashSet)
        }
}