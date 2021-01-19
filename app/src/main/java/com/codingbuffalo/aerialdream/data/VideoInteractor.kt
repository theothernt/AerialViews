package com.codingbuffalo.aerialdream.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.util.*

class VideoInteractor(context: Context, videoSource: Int, source_apple_2019: String) {
    private val repositories: MutableList<VideoRepository> = LinkedList()
    private val context: Context = context.applicationContext
    private val source_apple_2019: String
    private val videoSource: Int

    init {
        this.videoSource = videoSource
        this.source_apple_2019 = source_apple_2019
        repositories.add(Apple2019Repository())
    }

    fun fetchVideos(): VideoPlaylist {
        val videos = buildVideoList()
        return VideoPlaylist(videos)
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
            val remoteUri = video.getUri(source_apple_2019)
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