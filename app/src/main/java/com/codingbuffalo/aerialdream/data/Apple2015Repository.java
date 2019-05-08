package com.codingbuffalo.aerialdream.data;

import android.content.Context;

import com.codingbuffalo.aerialdream.R;

import java.util.LinkedList;
import java.util.List;

public class Apple2015Repository extends VideoRepository {
    @Override
    public List<Apple2015Video> fetchVideos(Context context) {
        Wrapper[] wrappers = parseJson(context, R.raw.tvos10, Wrapper[].class);

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
