package com.codingbuffalo.aerialdream.data;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class VideoPlaylist {
    private List<? extends Video> videos;

    private int position = 0;
    public int remoteVideos = 0;
    public int localVideos = 0;

    public VideoPlaylist(List<? extends Video> videos) {
        this.videos = videos;

        Predicate<Video> remoteVideoType = v-> v.getUri("any").toString().contains("http://");
        this.remoteVideos = (int)videos.stream().filter(remoteVideoType).count();
        this.localVideos = videos.size() - this.remoteVideos;

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
