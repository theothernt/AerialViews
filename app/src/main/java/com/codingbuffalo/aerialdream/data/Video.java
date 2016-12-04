package com.codingbuffalo.aerialdream.data;

import android.net.Uri;

public class Video {
    private String url;
    private String accessibilityLabel;
    private String timeOfDay;

    public Video(String url, String location, String timeOfDay) {
        this.url = url;
        this.accessibilityLabel = location;
        this.timeOfDay = timeOfDay;
    }

    public String getUrl() {
        return url;
    }

    public Uri getUri() {
        return Uri.parse(url);
    }

    public String getLocation() {
        return accessibilityLabel;
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }
}
