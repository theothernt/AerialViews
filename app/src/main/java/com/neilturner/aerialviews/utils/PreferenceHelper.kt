package com.neilturner.aerialviews.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class PreferenceHelper (
    val context: Context
) {
    private val prefsPackageName = "${context.packageName}_preferences"
    private val prefs = context.getSharedPreferences(prefsPackageName, Context.MODE_PRIVATE)

    private fun getDocumentsFolder(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun exportAll(filename: String): Boolean {
        return try {
            val properties = Properties()

            val documentsFolder = getDocumentsFolder()
            if (!documentsFolder.exists()) {
                documentsFolder.mkdirs()
            }

            val outputFile = File(documentsFolder, filename)
            //outputFile.createNewFile()

            // Convert all preferences to Properties
            // We'll add a type prefix to each key to handle different data types
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is Boolean -> properties.setProperty("bool_$key", value.toString())
                    is Float -> properties.setProperty("float_$key", value.toString())
                    is Int -> properties.setProperty("int_$key", value.toString())
                    is Long -> properties.setProperty("long_$key", value.toString())
                    is String -> properties.setProperty("string_$key", value)
                    is Set<*> -> {
                        // For string sets, we'll join elements with a delimiter
                        val setString = value.joinToString("|||")
                        properties.setProperty("stringset_$key", setString)
                    }
                }
            }

            // Write properties to file with a header comment
            FileOutputStream(outputFile).use { fos ->
                properties.store(fos, "App Preferences Backup")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importAll(filename: String): Boolean {
        return try {
            val inputFile = File(getDocumentsFolder(), filename)
            if (!inputFile.exists()) {
                return false
            }

            val properties = Properties()
            FileInputStream(inputFile).use { fis ->
                properties.load(fis)
            }

            val editor = prefs.edit()
            // Clear existing preferences
            editor.clear()

            // Import all preferences from Properties
            properties.forEach { (key, value) ->
                val keyString = key.toString()
                val valueString = value.toString()

                when {
                    keyString.startsWith("bool_") -> {
                        editor.putBoolean(keyString.removePrefix("bool_"), valueString.toBoolean())
                    }
                    keyString.startsWith("float_") -> {
                        editor.putFloat(keyString.removePrefix("float_"), valueString.toFloat())
                    }
                    keyString.startsWith("int_") -> {
                        editor.putInt(keyString.removePrefix("int_"), valueString.toInt())
                    }
                    keyString.startsWith("long_") -> {
                        editor.putLong(keyString.removePrefix("long_"), valueString.toLong())
                    }
                    keyString.startsWith("string_") -> {
                        editor.putString(keyString.removePrefix("string_"), valueString)
                    }
                    keyString.startsWith("stringset_") -> {
                        val set = if (valueString.isEmpty()) {
                            emptySet()
                        } else {
                            valueString.split("|||").toSet()
                        }
                        editor.putStringSet(keyString.removePrefix("stringset_"), set)
                    }
                }
            }

            editor.apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}