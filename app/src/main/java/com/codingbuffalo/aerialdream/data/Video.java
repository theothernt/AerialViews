package com.codingbuffalo.aerialdream.data;

import android.net.Uri;
import androidx.annotation.Nullable;

public abstract class Video {
    private String accessibilityLabel;

    public String getLocation() {
        return accessibilityLabel;
    }

    @Nullable
    public abstract Uri getUri(String option);
}
