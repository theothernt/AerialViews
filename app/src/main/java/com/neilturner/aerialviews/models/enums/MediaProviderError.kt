package com.neilturner.aerialviews.models.enums

import android.content.Context
import com.neilturner.aerialviews.R

/**
 * Enum representing different types of errors that can occur across media providers.
 * Each error type corresponds to specific validation or connection issues for different provider types.
 */
enum class MediaProviderError(val stringResId: Int) {
    // Authentication/Credential
    MISSING_HOSTNAME_OR_IP(R.string.error_missing_hostname_or_ip),
    MISSING_HOSTNAME_OR_IP_AND_PORT(R.string.error_missing_hostname_or_ip_and_port),
    MISSING_USERNAME(R.string.error_missing_username),
    MISSING_PASSWORD(R.string.error_missing_password),
    MISSING_PATHNAME(R.string.error_missing_pathname),
    MISSING_FOLDER(R.string.error_missing_folder),
    MISSING_VOLUME(R.string.error_missing_volume),
    MISSING_API_KEY(R.string.error_missing_api_key),
    MISSING_SHARED_LINK(R.string.error_missing_shared_link),

    // Invalid Entries
    INVALID_HOSTNAME_OR_IP(R.string.error_invalid_hostname_or_ip),
    INVALID_HOSTNAME_OR_IP_AND_PORT(R.string.error_invalid_hostname_or_ip_and_port),
    INVALID_FOLDER(R.string.error_invalid_folder),
    INVALID_VOLUME(R.string.error_invalid_volume),
    INVALID_API_KEY(R.string.error_invalid_api_key),
    INVALID_SHARED_LINK(R.string.error_invalid_shared_link),

    // Network/Connection Errors
    NO_INTERNET_CONNECTION(R.string.error_no_internet_connection),
    AUTHENTICATION_FAILED(R.string.error_authentication_failed),
    CONNECTION_FAILED(R.string.error_connection_failed),
    CONNECTION_SUCCESSFUL(R.string.error_connection_successful),
    NETWORK_ERROR(R.string.error_network_error),
    API_ERROR(R.string.error_api_error),
    SSL_ERROR(R.string.error_ssl_error),
    TIMEOUT_ERROR(R.string.error_timeout_error),

    // Permission Errors
    PERMISSION_DENIED(R.string.error_permission_denied),

    // Unknown/Generic Error
    UNKNOWN_ERROR(R.string.error_unknown_error);

    /**
     * Returns the localized string for this error type.
     * @param context Android context for accessing string resources
     * @return Localized error message string
     */
    fun getString(context: Context): String {
        return context.getString(stringResId)
    }

    /**
     * Returns a localized string key for this error type.
     * The key is dynamically generated as "error_" + enum name in lowercase.
     * @return String key that can be used for localization lookup (e.g., "error_missing_volume")
     */
    fun getStringKey(): String {
        return "error_${name.lowercase()}"
    }

    companion object {
        /**
         * Returns the localized string resource for the given error type.
         * @param context Android context for accessing string resources
         * @param error The MediaProviderError enum value
         * @return Localized error message string
         */
        fun getLocalizedString(context: Context, error: MediaProviderError): String {
            return error.getString(context)
        }

        /**
         * Returns a localized string key for the given error type.
         * @param error The MediaProviderError enum value
         * @return String key that can be used for localization lookup
         */
        fun getLocalizedStringKey(error: MediaProviderError): String {
            return error.getStringKey()
        }
    }
}
