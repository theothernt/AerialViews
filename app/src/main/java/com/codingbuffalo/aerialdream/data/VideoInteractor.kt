package com.codingbuffalo.aerialdream.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.codingbuffalo.aerialdream.data.protium.Interactor
import com.codingbuffalo.aerialdream.data.protium.ValueTask
import java.util.*
import java.util.concurrent.Executors

class VideoInteractor(context: Context, videoSource: Int, source_apple_2019: String, listener: Listener) : Interactor(Executors.newCachedThreadPool()) {
    private val listener: Listener
    private val repositories: MutableList<VideoRepository> = LinkedList()
    private val context: Context = context.applicationContext
    private val source_apple_2019: String
    private val videoSource: Int

    fun fetchVideos() {
        execute(FetchVideosTask())
    }

    private inner class FetchVideosTask : ValueTask<List<Video?>>() {
        @Throws(Exception::class)
        override fun onExecute(): List<Video> {
            val remoteVideos: MutableList<Video> = ArrayList()
            var localVideos: List<String?> = ArrayList()
            val videos: MutableList<Video> = ArrayList()
            for (repository in repositories) {
                remoteVideos.addAll(repository.fetchVideos(context))
            }
            if (videoSource != VideoSource.REMOTE) {
                localVideos = allMedia
            }
            for (video in remoteVideos) {
                val remoteUri = video.getUri(source_apple_2019)
                val remoteFilename = remoteUri!!.lastPathSegment!!.toLowerCase()
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

        override fun onComplete(data: List<Video?>) {
            listener.onFetch(VideoPlaylist(data))
        }
    }

    private fun findLocalVideo(localVideos: List<String?>, remoteFilename: String): Uri? {
        for (localUrl in localVideos) {
            val localUri = Uri.parse(localUrl)
            val localFilename = localUri.lastPathSegment!!.toLowerCase()
            if (localFilename.contains(remoteFilename)) {
                return localUri
            }
        }
        return null
    }

    private val allMedia: ArrayList<String?>
        get() {
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

    interface Listener {
        fun onFetch(videos: VideoPlaylist?)
    }

    init {
        this.listener = listener
        this.videoSource = videoSource
        this.source_apple_2019 = source_apple_2019
        repositories.add(Apple2019Repository())
    }
}