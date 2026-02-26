package com.neilturner.aerialviews.ui.settings

import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs

class OverlaysMetadataSlotFragment : BaseOverlaysMetadataSlotFragment() {
    override val analyticsScreenName = "Metadata Slot 1"
    override val prefKeyVideoSelection = "overlay_metadata1_videos"
    override val prefKeyVideoFolderLevel = "overlay_metadata1_video_folder_levels"
    override val prefKeyPhotoSelection = "overlay_metadata1_photos"
    override val prefKeyPhotoFolderLevel = "overlay_metadata1_photo_folder_levels"
    override val prefKeyPhotoLocationType = "overlay_metadata1_photo_location_type"
    override val prefKeyPhotoDateType = "overlay_metadata1_photo_date_type"
    override val prefKeyPhotoDateCustom = "overlay_metadata1_photo_date_custom"

    override fun getDateType(): DateType = GeneralPrefs.overlayMetadata1PhotosDateType ?: DateType.COMPACT

    override fun getPreferenceResource() = R.xml.settings_overlays_metadata_slot
}
