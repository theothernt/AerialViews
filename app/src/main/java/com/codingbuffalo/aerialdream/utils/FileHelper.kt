package com.codingbuffalo.aerialdream.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.util.HashSet

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
        } finally {
            Log.i(TAG, "findAllMedia found ${videos.size} files")
            return videos
        }
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

    private const val TAG = "FileHelper"
}