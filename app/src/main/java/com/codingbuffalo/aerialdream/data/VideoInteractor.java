package com.codingbuffalo.aerialdream.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;

import com.codingbuffalo.aerialdream.data.protium.Interactor;
import com.codingbuffalo.aerialdream.data.protium.ValueTask;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;

public class VideoInteractor extends Interactor {
    private Listener listener;
    private List<VideoRepository> repositories = new LinkedList<>();
    private Context context;
    private String source_apple_2019;
    private int videoSource = 0;

    public VideoInteractor(Context context, String source_apple_2019, @NonNull Listener listener) {
        super(Executors.newCachedThreadPool());

        this.context = context.getApplicationContext();
        this.listener = listener;

        this.source_apple_2019 = source_apple_2019;
        repositories.add(new Apple2019Repository());

        // videoSource
        // 0 remote only
        // 1 local only
        // 2 remote and local
        this.videoSource = 2;
    }

    public void fetchVideos() {
        execute(new FetchVideosTask());
    }

    private class FetchVideosTask extends ValueTask<List<? extends Video>> {
        @Override
        public List<? extends Video> onExecute() throws Exception {
            List<Video> remoteVideos = new ArrayList<>();
            List<Video> videos = new ArrayList<>();

            for (VideoRepository repository : repositories) {
                remoteVideos.addAll(repository.fetchVideos(context));
            }

            List<String> localVideos = getAllMedia();
            for (Video video : remoteVideos) {
                Uri remoteUri = video.getUri(source_apple_2019);
                String remoteFilename = remoteUri.getLastPathSegment().toLowerCase();

                if (videoSource == 0) {
                    // add remote video only
                    Log.i("","Adding remote video: " + remoteFilename);
                    continue;
                }

                if (videoSource != 0) {
                    String localUrl = findLocalVideo(localVideos, remoteFilename);
                    if(!localUrl.isEmpty()) {
                        Log.i("","Adding local video: " + localUrl);
                    } else if (videoSource == 2) {
                        Log.i("","Adding remote video: " + remoteFilename);
                    }
                }
            }

            return remoteVideos;
        }

        @Override
        public void onComplete(List<? extends Video> data) {
            listener.onFetch(new VideoPlaylist(data));
        }
    }

    private String findLocalVideo(List<String> localVideos, String remoteFilename) {
        for (String localUrl : localVideos ) {
            Uri localUri = Uri.parse(localUrl);
            String localFilename = localUri.getLastPathSegment().toLowerCase();
            if (localFilename.contains(remoteFilename)) {
                return localUrl;
            }
        }
        return "";
    }

    private ArrayList<String> getAllMedia() {
        HashSet<String> videoItemHashSet = new HashSet<>();
        String[] projection = {MediaStore.Video.VideoColumns.DATA, MediaStore.Video.Media.DISPLAY_NAME};
        Cursor cursor = this.context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);

        try {
            cursor.moveToFirst();
            do {
                videoItemHashSet.add((cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))));
            } while (cursor.moveToNext());
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList<String> downloadedList = new ArrayList<>(videoItemHashSet);
        return downloadedList;
    }

    public interface Listener {
        void onFetch(VideoPlaylist videos);
    }
}
