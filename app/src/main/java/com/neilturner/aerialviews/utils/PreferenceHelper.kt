package com.neilturner.aerialviews.utils

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

object PreferencesHelper {
    fun exportPreferences(context: Context, prefsName: String, outputFile: File): Boolean {
        return try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val properties = Properties()

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

    fun importPreferences(context: Context, prefsName: String, inputFile: File): Boolean {
        return try {
            val properties = Properties()
            FileInputStream(inputFile).use { fis ->
                properties.load(fis)
            }

            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit() {

                // Clear existing preferences
                clear()

                // Import all preferences from Properties
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
                            val set = if (valueString.isEmpty()) {
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
    }
}