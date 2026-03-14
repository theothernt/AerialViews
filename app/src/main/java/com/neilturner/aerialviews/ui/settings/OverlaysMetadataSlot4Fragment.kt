package com.neilturner.aerialviews.ui.settings

import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs

class OverlaysMetadataSlot4Fragment : BaseOverlaysMetadataSlotFragment() {
    override val analyticsScreenName = "Metadata Slot 4"
    override val prefKeyVideoSelection = "overlay_metadata4_videos"
    override val prefKeyVideoFolderLevel = "overlay_metadata4_video_folder_levels"
    override val prefKeyVideoLocationType = "overlay_metadata4_video_location_type"
    override val prefKeyPhotoSelection = "overlay_metadata4_photos"
    override val prefKeyPhotoFolderLevel = "overlay_metadata4_photo_folder_levels"
    override val prefKeyPhotoLocationType = "overlay_metadata4_photo_location_type"
    override val prefKeyPhotoDateType = "overlay_metadata4_photo_date_type"
    override val prefKeyPhotoDateCustom = "overlay_metadata4_photo_date_custom"

    override fun getDateType(): DateType = GeneralPrefs.overlayMetadata4PhotosDateType ?: DateType.COMPACT

    override fun getPreferenceResource() = R.xml.settings_overlays_metadata_slot4
}
