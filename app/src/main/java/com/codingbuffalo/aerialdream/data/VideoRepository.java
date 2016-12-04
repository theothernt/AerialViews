package com.codingbuffalo.aerialdream.data;

import com.codingbuffalo.aerialdream.data.protium.RestRepository;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class VideoRepository extends RestRepository {
    private final static String ENDPOINT = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/entries.json";

    public List<Video> fetchVideos() throws IOException {
        // Retrieve wrappers from endpoint
        VideoWrapper[] wrappers = fetch(VideoWrapper[].class, ENDPOINT);

        // Join wrappers in a single list
        List<Video> videos = new LinkedList<>();
        for (VideoWrapper wrapper : wrappers) {
            videos.addAll(wrapper.getVideos());
        }
        return videos;
    }
}
