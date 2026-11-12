package com.neilturner.aerialviews.ui.overlays

/** The values of `GeneralPrefs.overlayVisibility`. */
const val MODE_ALWAYS_VISIBLE = "ALWAYS_VISIBLE"
const val MODE_ALWAYS_HIDDEN = "ALWAYS_HIDDEN"
const val MODE_HIDE_AFTER_DELAY = "HIDE_AFTER_DELAY"
const val MODE_SHOW_AFTER_DELAY = "SHOW_AFTER_DELAY"

/**
 * The state an overlay layer settles in: the overlay fade alpha and the gradient alpha behind it.
 *
 * Both engines take their numbers from these, so a mode cannot describe itself one way for the
 * Compose layer and another for the Views - which is how `SHOW_AFTER_DELAY` once left the Compose
 * layer hidden for the whole session while the Views were fine.
 */
data class OverlayVisibilityTarget(
    val alpha: Float,
    val gradient: Float,
)

/** Both layers down: nothing visible, no gradient behind it. */
val hiddenOverlayTarget = OverlayVisibilityTarget(alpha = 0f, gradient = 0f)

/** Both layers up at the user's overlay opacity. */
fun visibleOverlayTarget(overlayOpacity: Float) = OverlayVisibilityTarget(alpha = overlayOpacity, gradient = 1f)

/**
 * What one visibility mode asks of the overlay layer: the state to settle in, and optionally a
 * second state to move to once `overlayVisibilityDelay` has passed.
 *
 * [revealsWhileWaiting] says whether a user reveal is allowed while the mode is still waiting for
 * its delay. The two delayed modes differ: hiding has nothing to interrupt, while showing has a
 * transition still to come, so a reveal would race it.
 */
data class OverlayVisibilityPlan(
    val start: OverlayVisibilityTarget,
    val afterDelay: OverlayVisibilityTarget? = null,
    val revealsWhileWaiting: Boolean = false,
)

/**
 * The single description of what [mode] wants, for both engines.
 *
 * An unrecognised mode settles hidden and stays there, which is the safe reading of a preference
 * this code does not understand.
 */
fun overlayVisibilityPlan(
    mode: String,
    overlayOpacity: Float,
): OverlayVisibilityPlan {
    val visible = visibleOverlayTarget(overlayOpacity)
    return when (mode) {
        MODE_ALWAYS_VISIBLE -> {
            OverlayVisibilityPlan(start = visible, revealsWhileWaiting = true)
        }

        MODE_ALWAYS_HIDDEN -> {
            OverlayVisibilityPlan(start = hiddenOverlayTarget, revealsWhileWaiting = true)
        }

        MODE_HIDE_AFTER_DELAY -> {
            OverlayVisibilityPlan(start = visible, afterDelay = hiddenOverlayTarget, revealsWhileWaiting = true)
        }

        MODE_SHOW_AFTER_DELAY -> {
            OverlayVisibilityPlan(start = hiddenOverlayTarget, afterDelay = visible)
        }

        else -> {
            OverlayVisibilityPlan(start = hiddenOverlayTarget, revealsWhileWaiting = true)
        }
    }
}
