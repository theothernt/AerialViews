package com.neilturner.aerialviews.providers.custom

data class CustomFeedValidationResult(
    val videoCount: Int = 0,
    val urlCount: Int = 0,
    val rtspCount: Int = 0,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = urlCount > 0 || rtspCount > 0
}