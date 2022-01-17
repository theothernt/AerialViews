@file:Suppress("unused", "unused")

package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

object FileHelper {

     fun findAllMedia(context: Context): List<String?> {
        val videos = mutableListOf<String>()
        try {
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val column = "_data"
            val projection = arrayOf(column)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            try {
                cursor!!.moveToFirst()
                do {
                    videos.add(cursor.getString(cursor.getColumnIndexOrThrow(column)))
                } while (cursor.moveToNext())
                cursor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Exception in findAllMedia cursor: ${e.message}")
                //e.printStackTrace()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in findAllMedia: ${e.message}")
        }
         Log.i(TAG, "findAllMedia found ${videos.size} files")
         return videos
    }

    fun isLocalVideo(uri: Uri): Boolean {
        return !uri.toString().contains("http://") &&
                !uri.toString().contains("https://")
    }

    fun isNetworkVideo(uri: Uri): Boolean {
        return uri.toString().contains("smb://")
    }

    fun fileExists(uri: Uri): Boolean {
        val file = File(uri.toString())
        return file.exists()
    }

    fun isVideoFilename(filename: String): Boolean {
        if (filename.startsWith("."))
            return false

        if (filename.endsWith(".mov") ||
            filename.endsWith(".mp4") ||
            filename.endsWith(".webm") ||
            filename.endsWith(".mkv"))
            return true

        return false
    }

    fun filenameToTitleCase(uri: Uri): String {
        val filename = uri.lastPathSegment!!
        val index = filename.lastIndexOf(".")

        // some.video.mov -> some.video
        var location = filename.substring(0, index)

        // somevideo -> Somevideo
        // city-place_video -> City - Place Video
        // some.video -> Some Video
        location = location.replace("-",".-.")
        location = location.replace("_",".")
        return location.split(".").joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
    }

    private const val TAG = "FileHelper"
}