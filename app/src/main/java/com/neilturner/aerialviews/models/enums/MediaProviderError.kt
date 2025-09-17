package com.neilturner.aerialviews.models.enums

import android.content.Context
import com.neilturner.aerialviews.R

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
         * Uses a hardcoded mapping to look up the string resource based on the enum name.
         *
         * @param context Android context for accessing string resources
         * @param error The MediaProviderError enum value
         * @return Localized error message string, or the string key if resource not found
         */
        fun getLocalizedString(context: Context, error: MediaProviderError): String {
            val resourceId = when (error) {
                MISSING_HOSTNAME_OR_IP -> R.string.error_missing_hostname_or_ip
                MISSING_HOSTNAME_OR_IP_AND_PORT -> R.string.error_missing_hostname_or_ip_and_port
                MISSING_USERNAME -> R.string.error_missing_username
                MISSING_PASSWORD -> R.string.error_missing_password
                MISSING_PATHNAME -> R.string.error_missing_pathname
                MISSING_FOLDER -> R.string.error_missing_folder
                MISSING_VOLUME -> R.string.error_missing_volume
                MISSING_API_KEY -> R.string.error_missing_api_key
                MISSING_SHARED_LINK -> R.string.error_missing_shared_link
                INVALID_HOSTNAME_OR_IP -> R.string.error_invalid_hostname_or_ip
                INVALID_HOSTNAME_OR_IP_AND_PORT -> R.string.error_invalid_hostname_or_ip_and_port
                INVALID_FOLDER -> R.string.error_invalid_folder
                INVALID_VOLUME -> R.string.error_invalid_volume
                INVALID_API_KEY -> R.string.error_invalid_api_key
                INVALID_SHARED_LINK -> R.string.error_invalid_shared_link
                NO_INTERNET_CONNECTION -> R.string.error_no_internet_connection
                AUTHENTICATION_FAILED -> R.string.error_authentication_failed
                CONNECTION_FAILED -> R.string.error_connection_failed
                CONNECTION_SUCCESSFUL -> R.string.error_connection_successful
                NETWORK_ERROR -> R.string.error_network_error
                API_ERROR -> R.string.error_api_error
                SSL_ERROR -> R.string.error_ssl_error
                TIMEOUT_ERROR -> R.string.error_timeout_error
                PERMISSION_DENIED -> R.string.error_permission_denied
                UNKNOWN_ERROR -> R.string.error_unknown_error
            }

            return try {
                context.getString(resourceId)
            } catch (e: Exception) {
                getLocalizedStringKey(error) // Fallback to key if any error occurs
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
