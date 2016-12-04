package com.codingbuffalo.aerialdream.data.protium;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;

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

        return jsonParser.fromJson(json, tClass);
    }

    private synchronized void createClient() {
        if (client == null) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE);

            client = new OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .build();

            jsonParser = new Gson();
        }
    }
}
