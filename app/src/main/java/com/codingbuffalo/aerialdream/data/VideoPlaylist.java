package com.codingbuffalo.aerialdream.data;

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
        return videos.get(position++ % videos.size());
    }
}
