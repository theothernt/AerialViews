package com.codingbuffalo.aerialdream.data.protium;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class RestRepository {
    private static OkHttpClient client;
    private static Gson jsonParser;

    protected <T> T fetch(Class<T> tClass, String url) throws IOException {
        createClient();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        String json = client.newCall(request)
                .execute()
                .body()
                .string();

        // Remove invalid trailing commas from json
        // Apple can do a lot of things, but apparently valid json isn't one of them ¯\_(ツ)_/¯
        json = json.replaceAll("(,)\\n * \\}", "}");

        return jsonParser.fromJson(json, tClass);
    }

    private synchronized void createClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .build();

            jsonParser = new Gson();
        }
    }
}
