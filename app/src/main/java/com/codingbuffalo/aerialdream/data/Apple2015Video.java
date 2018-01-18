package com.codingbuffalo.aerialdream.data;

import android.net.Uri;
import android.support.annotation.Nullable;

import java.util.Calendar;

public class Apple2015Video extends Video {
    private String url;
    private String timeOfDay;

    @Nullable
    @Override
    public Uri getUri(String option) {
        boolean videoIsDay = timeOfDay.equals("day");
        switch (option) {
            case "daytime":
                if (!videoIsDay)
                    return null;
                break;
            case "nighttime":
                if (videoIsDay)
                    return null;
                break;
            case "localtime":
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                boolean localIsDay = hour >= 7 && hour < 19;
                if (localIsDay != videoIsDay)
                    return null;
                break;
        }
        return Uri.parse(url);
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }
}
