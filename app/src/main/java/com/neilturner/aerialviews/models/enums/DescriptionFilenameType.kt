package com.neilturner.aerialviews.models.enums

enum class DescriptionFilenameType {
    DISABLED,
    FORMATTED, // use built-in formatting
    FILENAME, // as-is, leave alone
    LAST_FOLDER_FILENAME, // eg. Summer Photos / Family & Friends (.jpg)
    LAST_FOLDERNAME // eg. Summer Photos
    // FULL_PATH // eg. Photos / Summer Photos / Family & Friends (.jpg)
}
