package com.neilturner.aerialviews.models.enums

import android.content.Context

/**
 * Enum representing different types of errors that can occur across media providers.
 * Each error type corresponds to specific validation or connection issues for different provider types.
 */
enum class MediaProviderError {
    // Authentication/Credential
    MISSING_HOSTNAME_OR_IP,
    MISSING_HOSTNAME_OR_IP_AND_PORT,
    MISSING_USERNAME,
    MISSING_PASSWORD,
    MISSING_PATHNAME,
    MISSING_FOLDER,
    MISSING_VOLUME,
    MISSING_API_KEY,
    MISSING_SHARED_LINK,

    // Invalid Entries
    INVALID_HOSTNAME_OR_IP,
    INVALID_HOSTNAME_OR_IP_AND_PORT,
    INVALID_FOLDER,
    INVALID_VOLUME,
    INVALID_API_KEY,
    INVALID_SHARED_LINK,

    // Network/Connection Errors
    NO_INTERNET_CONNECTION,
    AUTHENTICATION_FAILED,
    CONNECTION_FAILED,
    CONNECTION_SUCCESSFUL,
    NETWORK_ERROR,
    API_ERROR,
    SSL_ERROR,
    TIMEOUT_ERROR,

    // Permission Errors
    PERMISSION_DENIED,

    // Unknown/Generic Error
    UNKNOWN_ERROR;

    companion object {
        /**
         * Returns a localized string key for the given error type.
         * This can be used to look up the appropriate localized error message.
         * The key is dynamically generated as "error_" + enum name in lowercase.
         *
         * @param error The MediaProviderError enum value
         * @return String key that can be used for localization lookup (e.g., "error_missing_volume")
         */
        fun getLocalizedStringKey(error: MediaProviderError): String {
            return "error_${error.name.lowercase()}"
        }

        /**
         * Returns the localized string resource for the given error type.
         * Uses reflection to dynamically look up the string resource based on the enum name.
         *
         * @param context Android context for accessing string resources
         * @param error The MediaProviderError enum value
         * @return Localized error message string, or the string key if resource not found
         */
        fun getLocalizedString(context: Context, error: MediaProviderError): String {
            val stringKey = getLocalizedStringKey(error)
            return try {
                val resourceId = context.resources.getIdentifier(
                    stringKey,
                    "string",
                    context.packageName
                )
                if (resourceId != 0) {
                    context.getString(resourceId)
                } else {
                    stringKey // Fallback to key if resource not found
                }
            } catch (e: Exception) {
                stringKey // Fallback to key if any error occurs
            }
        }

        /**
         * Extension function to get the localized string key directly from the enum instance.
         * Usage: MediaProviderError.MISSING_USERNAME.getStringKey()
         */
        fun MediaProviderError.getStringKey(): String {
            return getLocalizedStringKey(this)
        }

        /**
         * Extension function to get the localized string directly from the enum instance.
         * Usage: MediaProviderError.MISSING_USERNAME.getString(context)
         */
        fun MediaProviderError.getString(context: Context): String {
            return getLocalizedString(context, this)
        }
    }
}
