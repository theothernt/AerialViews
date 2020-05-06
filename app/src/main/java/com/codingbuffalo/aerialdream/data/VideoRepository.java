package com.codingbuffalo.aerialdream.data;

import android.content.Context;
import android.support.annotation.RawRes;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;

public abstract class VideoRepository {
    private static Gson jsonParser = new Gson();

    public abstract List<? extends Video> fetchVideos(Context context) throws IOException;

    <T> T parseJson(Context context, @RawRes int res, Class<T> tClass) {
        InputStream is = context.getResources().openRawResource(res);
        String json = new Scanner(is).useDelimiter("\\A").next();
        return  jsonParser.fromJson(json, tClass);
    }
}
