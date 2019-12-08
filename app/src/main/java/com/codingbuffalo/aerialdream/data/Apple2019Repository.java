package com.codingbuffalo.aerialdream.data;

import android.content.Context;

import com.codingbuffalo.aerialdream.R;

import java.util.List;

public class Apple2019Repository extends VideoRepository {
    @Override
    public List<Apple2017Video> fetchVideos(Context context) {
        Wrapper wrapper = parseJson(context, R.raw.tvos13, Wrapper.class);
        return wrapper.assets;
    }

    private static class Wrapper {
        private List<Apple2017Video> assets;
    }
}
