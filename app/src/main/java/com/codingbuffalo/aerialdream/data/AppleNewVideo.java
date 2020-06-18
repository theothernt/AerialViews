package com.codingbuffalo.aerialdream.data;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

public abstract class AppleNewVideo extends Video {
    @SerializedName("url-1080-H264")
    private String url_1080_H264;
    @SerializedName("url-1080-SDR")
    private String url_1080_SDR;
    @SerializedName("url-1080-HDR")
    private String url_1080_HDR;
    @SerializedName("url-4K-SDR")
    private String url_4K_SDR;
    @SerializedName("url-4K-HDR")
    private String url_4K_HDR;

    @Nullable
    @Override
    public Uri getUri(String option) {
        return Uri.parse(
                getUrl(option)
                        // Apple seems to be using an invalid certificate
                        .replace("https://", "http://")
        );
    }

    @Nullable
    private String getUrl(String option) {
        switch (option) {
            case "1080_sdr":
                return url_1080_SDR;
            case "1080_hdr":
                return url_1080_HDR;
            case "4k_sdr":
                return url_4K_SDR;
            case "4k_hdr":
                return url_4K_HDR;
            default:
                return url_1080_H264;
        }
    }
}