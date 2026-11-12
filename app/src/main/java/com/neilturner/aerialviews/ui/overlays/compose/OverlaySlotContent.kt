package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.Composable
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState

/**
 * Renders one slot's overlay.
 *
 * Phase 7 replaces each branch with its composable. Until a branch is implemented the slot
 * renders nothing, so with use_compose_screensaver on the overlay layer is wired up but empty.
 * Leave the setting off until the branches are ported.
 */
@Composable
fun OverlaySlotContent(
    type: OverlayType,
    state: OverlayUiState,
) {
    when (type) {
        OverlayType.EMPTY -> Unit

        OverlayType.CLOCK -> Unit

        // Phase 7.1
        OverlayType.DATE -> Unit

        // Phase 7.2
        OverlayType.METADATA1,
        OverlayType.METADATA2,
        OverlayType.METADATA3,
        OverlayType.METADATA4,
        -> Unit

        // Phase 7.3
        OverlayType.MESSAGE1,
        OverlayType.MESSAGE2,
        OverlayType.MESSAGE3,
        OverlayType.MESSAGE4,
        -> Unit

        // Phase 7.4
        OverlayType.COUNTDOWN -> Unit

        // Phase 7.5
        OverlayType.MUSIC1,
        OverlayType.MUSIC2,
        -> Unit

        // Phase 7.6
        OverlayType.WEATHER1 -> Unit

        // Phase 7.8
        OverlayType.WEATHER2 -> Unit // Phase 7.9
    }
}
