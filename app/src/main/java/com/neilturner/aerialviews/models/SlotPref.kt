package com.neilturner.aerialviews.models

import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType

data class SlotPref(val pref: OverlayType, val type: SlotType, val label: String)
