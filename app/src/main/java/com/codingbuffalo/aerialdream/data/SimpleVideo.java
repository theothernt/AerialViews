package com.codingbuffalo.aerialdream.data;

import android.net.Uri;
import androidx.annotation.Nullable;

public class SimpleVideo extends Video {
    private Uri videoUri;
    private String location;

    public SimpleVideo(Uri videoUrl, String location)
    {
        this.videoUri = videoUrl;
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    @Nullable
    @Override
    public Uri getUri(String option) {
        return videoUri;
    }
}
