package com.neilturner.aerialviews.models.enums

enum class DescriptionFilenameType {
    DISABLED,
    FILENAME, // as-is, leave alone
    LAST_FOLDER_FILENAME, // eg. Summer Photos / Family & Friends (.jpg)
    LAST_FOLDER_NAME, // eg. Summer Photos
    // FULL_PATH // eg. Photos / Summer Photos / Family & Friends (.jpg)
}
