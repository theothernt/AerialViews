package com.codingbuffalo.aerialdream.data;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

public class VideoPlaylist {
    private List<? extends Video> videos;

    private int position = 0;

    public VideoPlaylist(List<? extends Video> videos) {
        this.videos = videos;
        Collections.shuffle(this.videos);
    }

    public Video getVideo() {
        if (videos.size() != 0) {
            return videos.get(position++ % videos.size());
        } else {
            return new SimpleVideo(Uri.parse(""), "");
        }
    }
}
