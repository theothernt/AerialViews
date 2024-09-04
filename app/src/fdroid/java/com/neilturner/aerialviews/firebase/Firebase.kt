package com.neilturner.aerialviews.firebase

import android.os.Bundle
import androidx.annotation.NonNull

class Firebase() {
    object crashlytics {
        fun recordException(
            @NonNull throwable: Throwable,
        ) {
        }
    }

    object analytics {
        fun logEvent(
            x: String,
            bundle: Bundle,
        ) {
        }
    }
}
