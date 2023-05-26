package com.neilturner.aerialviews.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.reflect.Array
import java.lang.reflect.InvocationTargetException

object StorageHelper {
    // https://github.com/moneytoo/Player/blob/master/android-file-chooser/src/main/java/com/obsez/android/lib/filechooser/internals/FileUtil.java

    fun getStoragePaths(context: Context): LinkedHashMap<String, String> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            getStoragePathsLow(context)
        } else {
            getStoragePaths24(context)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun getStoragePaths24(context: Context): LinkedHashMap<String, String> {
        val paths = LinkedHashMap<String, String>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            val volumes = storageManager.storageVolumes
            for (vol in volumes) {
                var description = vol.getDescription(context)
                if (description.contains("internal", true)) {
                    description = "Internal"
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    val dir = vol.directory ?: continue
                    paths[dir.absolutePath] = "${dir.absolutePath} ($description)"
                } else {
                    val getPath = vol.javaClass.getMethod("getPath")
                    val path = getPath.invoke(vol) as String
                    paths[path] = "$path ($description)"
                }
            }
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: java.lang.NullPointerException) {
            e.printStackTrace()
        }
        if (paths.size == 0) {
            val path = Environment.getExternalStorageDirectory().absolutePath
            paths[path] = formatPathAsLabel(path)
        }
        return paths
    }

    private fun getStoragePathsLow(context: Context): LinkedHashMap<String, String> {
        val paths = LinkedHashMap<String, String>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
            val getPath = storageVolumeClazz.getMethod("getPath")
            val result = getVolumeList.invoke(storageManager) as Any
            val length: Int = Array.getLength(result)
            Log.d("X", "---length--$length")
            for (i in 0 until length) {
                val storageVolumeElement: Any = Array.get(result, i) as Any
                Log.d("X", "  ---Object--" + storageVolumeElement + "i==" + i)
                val path = getPath.invoke(storageVolumeElement) as String
                Log.d("X", "  ---path_total--$path")
                Log.d("X", "    ---path--$path")
                paths[path] = formatPathAsLabel(path)
            }
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
        if (paths.size == 0) {
            val path = Environment.getExternalStorageDirectory().absolutePath
            paths[path] = formatPathAsLabel(path)
        }
        return paths
    }

    private fun formatPathAsLabel(path: String): String {
        return "[ $path ]"
    }
}
