package com.codingbuffalo.aerialdream.data;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideoPlaylist {
    public enum TYPE {
        DAY, NIGHT, ALL
    }

    private List<Video> videos;
    private List<Video> dayVideos;
    private List<Video> nightVideos;

    private int position = 0;

    public VideoPlaylist(List<Video> videos) {
        this.videos = videos;
        Collections.shuffle(this.videos);
        
        dayVideos = new ArrayList<>();
        nightVideos = new ArrayList<>();
        for (Video video : this.videos) {
            if (video.getTimeOfDay().equals("day")) {
                dayVideos.add(video);
            } else {
                nightVideos.add(video);
            }
        }
    }

    public Video getVideo(TYPE type) {
        List<Video> list;
        switch (type) {
            case DAY:
                list = dayVideos;
                break;
            case NIGHT:
                list = nightVideos;
                break;
            default:
                list = videos;
        }

        // Make sure we're not requesting a position
        position = position % list.size();
        Video video = list.get(position);
        position++;
        return video;
    }
}
