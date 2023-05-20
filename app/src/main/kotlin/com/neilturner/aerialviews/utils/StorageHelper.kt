package com.neilturner.aerialviews.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.reflect.Array
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Objects


object StorageHelper {

    // https://github.com/moneytoo/Player/blob/master/android-file-chooser/src/main/java/com/obsez/android/lib/filechooser/internals/FileUtil.java

    fun getStorageVols(context: Context): List<StorageVolume?> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            getStorageVolsLow(context)
        } else {
            getStorageVols24(context)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun getStorageVols24(context: Context): List<StorageVolume?> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            return Objects.requireNonNull(storageManager).storageVolumes
        } catch (e: java.lang.NullPointerException) {
            e.printStackTrace()
        }
        return ArrayList()
    }

    private fun getStorageVolsLow(context: Context): List<StorageVolume?> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            val getVolumeList = storageManager.javaClass.getMethod("getVolumeList")
            val result = getVolumeList.invoke(storageManager)
            return result as List<StorageVolume?>
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
        return ArrayList()
    }

    fun getStoragePaths(context: Context): LinkedHashMap<String?, String?> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            getStoragePathsLow(context)
        } else {
            getStoragePaths24(context)
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private fun getStoragePaths24(context: Context): LinkedHashMap<String?, String?> {
        val paths = LinkedHashMap<String?, String?>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            val result = Objects.requireNonNull(storageManager).storageVolumes
            for (vol in result) {
                Log.d("X", "  ---Object--" + vol + " | desc: " + vol.getDescription(context))
                if (Build.VERSION.SDK_INT >= 30) {
                    val dir = vol.directory ?: continue
                    paths[dir.absolutePath] = formatPathAsLabel(dir.absolutePath)
                } else {
                    val getPath = vol.javaClass.getMethod("getPath")
                    val path = getPath.invoke(vol) as String
                    Log.d("X", "    ---path--$path")
                    paths[path] = formatPathAsLabel(path)
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

    private fun getStoragePathsLow(context: Context): LinkedHashMap<String?, String?> {
        val paths = LinkedHashMap<String?, String?>()
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
            val length: Int = Array.getLength(result)
            Log.d("X", "---length--$length")
            for (i in 0 until length) {
                val storageVolumeElement: Any = Array.get(result, i) as Any
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