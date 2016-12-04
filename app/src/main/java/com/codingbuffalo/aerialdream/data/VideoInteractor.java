package com.codingbuffalo.aerialdream.data;


import android.support.annotation.NonNull;

import com.codingbuffalo.aerialdream.data.protium.Interactor;
import com.codingbuffalo.aerialdream.data.protium.ValueTask;

import java.util.List;
import java.util.concurrent.Executors;

public class VideoInteractor extends Interactor {
    private Listener listener;
    private VideoRepository repository;

    public VideoInteractor(@NonNull Listener listener) {
        super(Executors.newCachedThreadPool());
        
        this.listener = listener;
        repository = new VideoRepository();
    }

    public void fetchVideos() {
        execute(new FetchVideosTask());
    }

    private class FetchVideosTask extends ValueTask<List<Video>> {
        @Override
        public List<Video> onExecute() throws Exception {
            return repository.fetchVideos();
        }

        @Override
        public void onComplete(List<Video> data) {
            listener.onFetch(new VideoPlaylist(data));
        }
    }

    public interface Listener {
        void onFetch(VideoPlaylist videos);
    }
}
