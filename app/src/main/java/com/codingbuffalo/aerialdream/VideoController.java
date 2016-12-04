package com.codingbuffalo.aerialdream;


import android.content.Context;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import com.codingbuffalo.aerialdream.data.Video;
import com.codingbuffalo.aerialdream.data.VideoInteractor;
import com.codingbuffalo.aerialdream.data.VideoPlaylist;
import com.codingbuffalo.aerialdream.databinding.AerialDreamBinding;
import com.google.android.exoplayer2.ExoPlaybackException;

import java.util.Calendar;
import java.util.TimerTask;

public class VideoController implements VideoInteractor.Listener {
    private static final long FADE_DURATION = 5000;
    private AerialDreamBinding binding;

    private ExoPlayerView activePlayer;
    private ExoPlayerView bufferPlayer;
    private Handler timer;

    private VideoPlaylist videos;
    private Video activeVideo;
    private Video bufferVideo;

    private boolean filterTime;
    private boolean firstPlay = true;

    public VideoController(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        binding = DataBindingUtil.inflate(inflater, R.layout.aerial_dream, null, false);

        activePlayer = binding.videoView0;
        bufferPlayer = binding.videoView1;

        timer = new Handler();

        // Apply preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showClock = prefs.getBoolean("show_clock", false);
        boolean showLocation = prefs.getBoolean("show_location", false);
        filterTime = prefs.getBoolean("filter_time", false);

        binding.clock.setVisibility(showClock ? View.VISIBLE : View.GONE);
        binding.location.setVisibility(showLocation ? View.VISIBLE : View.GONE);

        new VideoInteractor(this).fetchVideos();
    }

    public View getView() {
        return binding.getRoot();
    }

    public void start() {
        PlayerEventListener listener0 = new PlayerEventListener(activePlayer, bufferPlayer);
        PlayerEventListener listener1 = new PlayerEventListener(bufferPlayer, activePlayer);

        activePlayer.setOnPlayerListener(listener0);
        bufferPlayer.setOnPlayerListener(listener1);

        activeVideo = getVideo();
        bufferVideo = getVideo();
        activePlayer.setVideoURI(activeVideo.getUri());
    }

    public void stop() {
        timer.removeCallbacks(replacementTask);

        activePlayer.release();
        bufferPlayer.release();
    }

    private void playVideo() {
        binding.location.setText(activeVideo.getLocation());

        activePlayer.start();

        Animation animation = new AlphaAnimation(1, 0);
        animation.setDuration(FADE_DURATION);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                binding.container.bringChildToFront(activePlayer);
                bufferPlayer.setAlpha(1);
                bufferPlayer.setVideoURI(bufferVideo.getUri());
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        bufferPlayer.startAnimation(animation);
    }

    private Runnable replacementTask = new TimerTask() {
        @Override
        public void run() {
            ExoPlayerView temp = activePlayer;
            activePlayer = bufferPlayer;
            bufferPlayer = temp;

            activeVideo = bufferVideo;
            bufferVideo = getVideo();

            playVideo();
        }
    };

    @Override
    public void onFetch(VideoPlaylist videos) {
        this.videos = videos;
        start();
    }

    private Video getVideo() {
        VideoPlaylist.TYPE type = VideoPlaylist.TYPE.ALL;

        if (filterTime) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            type = hour < 7 || hour >= 19 ? VideoPlaylist.TYPE.NIGHT : VideoPlaylist.TYPE.DAY;
        }

        return videos.getVideo(type);
    }

    private class PlayerEventListener implements ExoPlayerView.OnPlayerEventListener {
        private ExoPlayerView thisView;
        private ExoPlayerView otherView;

        public PlayerEventListener(ExoPlayerView thisView, ExoPlayerView otherView) {
            this.thisView = thisView;
            this.otherView = otherView;
        }

        @Override
        public void onPrepared() {
            if (firstPlay) {
                firstPlay = false;
                playVideo();
            } else {
                long delay = otherView.getDuration() - otherView.getCurrentPosition() - FADE_DURATION;

                timer.removeCallbacks(replacementTask);
                timer.postDelayed(replacementTask, delay);
            }
        }

        @Override
        public void onError(ExoPlaybackException error) {
            Toast.makeText(thisView.getContext(), "Error: " + error.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            error.printStackTrace();

            thisView.setVideoURI(bufferVideo.getUri());
        }
    }
}
