package com.neilturner.aerialviews.providers.immich

internal fun cleanSharedLinkKey(input: String): String {
    return input
        .trim()
        .replace(Regex("^/+|/+$"), "") // Remove leading and trailing slashes
        .replace(Regex("^(share|s)/"), "") // Support both "/share/<key>" and "/s/<slug>" formats
}

internal fun isSlugFormat(input: String): Boolean {
    val trimmed = input.trim().replace(Regex("^/+"), "")
    return trimmed.startsWith("s/")
}
