package com.codingbuffalo.aerialdream.data;

import java.io.IOException;
import java.util.List;

public class Apple2017Repository extends VideoRepository {
    private final static String ENDPOINT = "http://sylvan.apple.com/Aerials/2x/entries.json";

    public List<Apple2017Video> fetchVideos() throws IOException {
        Wrapper wrapper = fetch(Wrapper.class, ENDPOINT);
        return wrapper.assets;
    }

    private static class Wrapper {
        private List<Apple2017Video> assets;
    }
}
