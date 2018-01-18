package com.codingbuffalo.aerialdream.data;

import android.support.annotation.NonNull;

import com.codingbuffalo.aerialdream.data.protium.Interactor;
import com.codingbuffalo.aerialdream.data.protium.ValueTask;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;

public class VideoInteractor extends Interactor {
    private Listener listener;
    private List<VideoRepository> repositories = new LinkedList<>();

    public VideoInteractor(boolean apple2015, boolean apple2017, @NonNull Listener listener) {
        super(Executors.newCachedThreadPool());

        this.listener = listener;
        if (apple2015) {
            repositories.add(new Apple2015Repository());
        }
        if (apple2017) {
            repositories.add(new Apple2017Repository());
        }
    }

    public void fetchVideos() {
        execute(new FetchVideosTask());
    }

    private class FetchVideosTask extends ValueTask<List<? extends Video>> {
        @Override
        public List<? extends Video> onExecute() throws Exception {
            List<Video> videos = new ArrayList<>();
            for (VideoRepository repository : repositories) {
                videos.addAll(repository.fetchVideos());
            }
            return videos;
        }

        @Override
        public void onComplete(List<? extends Video> data) {
            listener.onFetch(new VideoPlaylist(data));
        }
    }

    public interface Listener {
        void onFetch(VideoPlaylist videos);
    }
}
