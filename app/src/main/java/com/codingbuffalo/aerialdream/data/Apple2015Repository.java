package com.codingbuffalo.aerialdream.data;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class Apple2015Repository extends VideoRepository {
    private final static String ENDPOINT = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/entries.json";

    public List<Apple2015Video> fetchVideos() throws IOException {
        // Retrieve wrappers from endpoint
        Wrapper[] wrappers = fetch(Wrapper[].class, ENDPOINT);

        // Join wrappers in a single list
        List<Apple2015Video> videos = new LinkedList<>();
        for (Wrapper wrapper : wrappers) {
            videos.addAll(wrapper.assets);
        }
        return videos;
    }

    private static class Wrapper {
        private List<Apple2015Video> assets;
    }
}
