package com.neilturner.aerialviews.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Objects

object StorageHelper {

    // https://github.com/moneytoo/Player/blob/390c5a1aa9e433823af7b524d5812b13412c39ab/android-file-chooser/src/main/java/com/obsez/android/lib/filechooser/internals/FileUtil.java#L237

    fun getStoragePath(context: Context, isRemovable: Boolean): String {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            getStoragePathLow(context, isRemovable)
        } else {
            getStoragePath24(context, isRemovable)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun getStoragePath24(context: Context, isRemovable: Boolean): String {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            val result: List<StorageVolume> =
                Objects.requireNonNull(storageManager).storageVolumes
            for (vol in result) {
                Log.d("X", "  ---Object--" + vol + " | desc: " + vol.getDescription(context))
                if (isRemovable != vol.isRemovable) {
                    continue
                }
                return if (Build.VERSION.SDK_INT >= 30) {
                    // TODO: Handle multiple removable volumes
                    val dir = vol.directory ?: continue
                    dir.absolutePath
                } else {
                    val getPath: Method = vol.javaClass.getMethod("getPath")
                    val path = getPath.invoke(vol) as String
                    Log.d("X", "    ---path--$path")
                    // HACK
                    if (isRemovable && result.size > 2 && path.startsWith("/storage/")) "/storage" else path
                }
            }
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
        return Environment.getExternalStorageDirectory().absolutePath
    }

    private fun getStoragePathLow(context: Context, isRemovable: Boolean): String {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
            val getVolumeList: Method = storageManager.javaClass.getMethod("getVolumeList")
            val getPath: Method = storageVolumeClazz.getMethod("getPath")
            val isRemovableMtd: Method = storageVolumeClazz.getMethod("isRemovable")
            val result: Any = getVolumeList.invoke(storageManager) as Any
            val length: Int = java.lang.reflect.Array.getLength(result)
            //final int length = result.size();
            Log.d("X", "---length--$length")
            for (i in 0 until length) {
                val storageVolumeElement: Any = java.lang.reflect.Array.get(result, i) as Any
                Log.d("X", "  ---Object--" + storageVolumeElement + "i==" + i)
                val path = getPath.invoke(storageVolumeElement) as String
                Log.d("X", "  ---path_total--$path")
                val removable = isRemovableMtd.invoke(storageVolumeElement) as Boolean
                if (isRemovable == removable) {
                    Log.d("X", "    ---path--$path")
                    // HACK
                    return if (isRemovable && length > 2 && path.startsWith("/storage/")) "/storage" else path
                }
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
        return Environment.getExternalStorageDirectory().absolutePath
    }
}