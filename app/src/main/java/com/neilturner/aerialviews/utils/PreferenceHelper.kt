package com.neilturner.aerialviews.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Properties

object PreferenceHelper {
    private val ignorePrefs =
        listOf(
            "check_for_hevc_support",
        )

    fun exportPreferences(context: Context): Boolean =
        try {
            val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!documentsFolder.exists()) {
                documentsFolder.mkdirs()
            }

            val outputFile = File(documentsFolder, "backup.avsettings")
            val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val properties = Properties()

            prefs.all.forEach { (key, value) ->
                if (ignorePrefs.contains(key)) {
                    Timber.i("Ignoring key: $key")
                    return@forEach
                }

                when (value) {
                    is Boolean -> properties.setProperty("bool_$key", value.toString())
                    is Float -> properties.setProperty("float_$key", value.toString())
                    is Int -> properties.setProperty("int_$key", value.toString())
                    is Long -> properties.setProperty("long_$key", value.toString())
                    is String -> properties.setProperty("string_$key", value)
                    is Set<*> -> {
                        val setString = value.joinToString("|||")
                        properties.setProperty("stringset_$key", setString)
                    }
                }
            }

            FileOutputStream(outputFile).use { fos ->
                properties.store(
                    fos,
                    "Settings backup for Aerial Views ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}.${BuildConfig.BUILD_TYPE})",
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    fun importPreferences(
        context: Context,
        uri: Uri,
        clearExisting: Boolean = false,
    ): Boolean =
        try {
            val properties =
                Properties().apply {
                    val stream = context.contentResolver.openInputStream(uri)
                    this.load(stream)
                }

            val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            prefs.edit {
                if (clearExisting) clear()

                properties.forEach { (key, value) ->
                    val keyString = key.toString()
                    val valueString = value.toString()

                    when {
                        keyString.startsWith("bool_") -> {
                            putBoolean(keyString.removePrefix("bool_"), valueString.toBoolean())
                        }

                        keyString.startsWith("float_") -> {
                            putFloat(keyString.removePrefix("float_"), valueString.toFloat())
                        }

                        keyString.startsWith("int_") -> {
                            putInt(keyString.removePrefix("int_"), valueString.toInt())
                        }

                        keyString.startsWith("long_") -> {
                            putLong(keyString.removePrefix("long_"), valueString.toLong())
                        }

                        keyString.startsWith("string_") -> {
                            putString(keyString.removePrefix("string_"), valueString)
                        }

                        keyString.startsWith("stringset_") -> {
                            val set =
                                if (valueString.isEmpty()) {
                                    emptySet()
                                } else {
                                    valueString.split("|||").toSet()
                                }
                            putStringSet(keyString.removePrefix("stringset_"), set)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    fun isExitToSettingSet(): Boolean {
        var isSetup = false
        GeneralPrefs.preferences.all.forEach {
            if (it.key.startsWith("button_") &&
                (it.key.endsWith("_press") || it.key.endsWith("_hold")) &&
                it.value.toStringOrEmpty().contains("EXIT_TO_SETTINGS")
            ) {
                isSetup = true
                return@forEach
            }
        }
        return isSetup
    }
}
