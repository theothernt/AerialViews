package com.neilturner.aerialviews.ui.helpers

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.edit
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.toStringOrEmpty
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Properties

object PreferenceHelper {
    private const val BACKUP_FILENAME = "backup.avsettings"

    private val ignorePrefs =
        listOf(
            "check_for_hevc_support",
        )

    fun exportPreferences(context: Context): String? {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val properties = Properties()

        prefs.all.forEach { (key, value) ->
            if (ignorePrefs.contains(key)) {
                Timber.i("Ignoring key: $key")
                return@forEach
            }

            when (value) {
                is Boolean -> {
                    properties.setProperty("bool_$key", value.toString())
                }

                is Float -> {
                    properties.setProperty("float_$key", value.toString())
                }

                is Int -> {
                    properties.setProperty("int_$key", value.toString())
                }

                is Long -> {
                    properties.setProperty("long_$key", value.toString())
                }

                is String -> {
                    properties.setProperty("string_$key", value)
                }

                is Set<*> -> {
                    val setString = value.joinToString("|||")
                    properties.setProperty("stringset_$key", setString)
                }
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            exportViaMediaStore(context, properties)
        } else {
            exportViaLegacyFile(properties)
        }
    }

    // Android 12+ (API 31+): write to public Documents via MediaStore (no permission needed)
    private fun exportViaMediaStore(
        context: Context,
        properties: Properties,
    ): String? {
        return try {
            val contentResolver = context.contentResolver
            val externalUri = MediaStore.Files.getContentUri("external")

            // Delete any existing backup file to avoid duplicates
            val selection =
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val selectionArgs =
                arrayOf(
                    BACKUP_FILENAME,
                    "${Environment.DIRECTORY_DOCUMENTS}/",
                )
            contentResolver.delete(externalUri, selection, selectionArgs)

            // Insert new entry into the public Documents folder
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILENAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                }
            val insertedUri =
                contentResolver.insert(externalUri, values) ?: run {
                    Timber.e("MediaStore insert returned null URI")
                    return null
                }

            contentResolver.openOutputStream(insertedUri)?.use { os ->
                properties.store(
                    os,
                    "Settings backup for Aerial Views ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}.${BuildConfig.BUILD_TYPE})",
                )
            }

            val displayPath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_FILENAME"
            Timber.i("Settings exported via MediaStore to: $displayPath")
            displayPath
        } catch (e: Exception) {
            Timber.e(e, "Failed to export settings via MediaStore")
            null
        }
    }

    // Android 11 and below (API 30-): write via legacy File API to public Documents folder
    private fun exportViaLegacyFile(properties: Properties): String? {
        return try {
            val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!documentsFolder.exists()) {
                documentsFolder.mkdirs()
            }

            if (!documentsFolder.canWrite()) {
                Timber.e("Documents folder not writable: ${documentsFolder.absolutePath}")
                return null
            }

            val outputFile = File(documentsFolder, BACKUP_FILENAME)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            FileOutputStream(outputFile).use { fos ->
                properties.store(
                    fos,
                    "Settings backup for Aerial Views ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}.${BuildConfig.BUILD_TYPE})",
                )
            }

            Timber.i("Settings exported successfully to: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to export settings to Documents folder")
            null
        }
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
