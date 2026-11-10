package com.neilturner.aerialviews.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.lang.reflect.InvocationTargetException

object StorageHelper {
    // https://github.com/moneytoo/Player/blob/master/android-file-chooser/src/main/java/com/obsez/android/lib/filechooser/internals/FileUtil.java

    fun getStoragePaths(context: Context): LinkedHashMap<String, String> {
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
        if (paths.isEmpty()) {
            val path = Environment.getExternalStorageDirectory().absolutePath
            paths[path] = formatPathAsLabel(path)
        }
        return paths
    }

    private fun formatPathAsLabel(path: String): String = "[ $path ]"
}
