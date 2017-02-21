package com.codingbuffalo.aerialdream;

import android.content.Context;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import com.codingbuffalo.aerialdream.data.Video;
import com.codingbuffalo.aerialdream.data.VideoInteractor;
import com.codingbuffalo.aerialdream.data.VideoPlaylist;
import com.codingbuffalo.aerialdream.databinding.AerialDreamBinding;
import com.codingbuffalo.aerialdream.databinding.VideoViewBinding;
import com.google.android.exoplayer2.ExoPlaybackException;

import java.util.Calendar;

public class VideoController implements VideoInteractor.Listener, ExoPlayerView.OnPlayerEventListener {
    private AerialDreamBinding binding;

    private VideoPlaylist videos;

    private boolean filterTime;

    public VideoController(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        binding = DataBindingUtil.inflate(inflater, R.layout.aerial_dream, null, false);

        // Apply preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showClock = prefs.getBoolean("show_clock", false);
        boolean showLocation = prefs.getBoolean("show_location", false);
        boolean showProgress = prefs.getBoolean("show_progress", false);
        boolean cache = prefs.getBoolean("cache", false);
        filterTime = prefs.getBoolean("filter_time", false);

        binding.setShowLocation(showLocation);
        binding.setShowClock(showClock);
        binding.setShowProgress(showProgress);
        binding.setUseCache(cache);

        binding.videoView0.setController(binding.videoView0.videoView);
        binding.videoView1.setController(binding.videoView1.videoView);

        binding.videoView0.videoView.setOnPlayerListener(this);
        binding.videoView1.videoView.setOnPlayerListener(this);

        new VideoInteractor(this).fetchVideos();
    }

    public View getView() {
        return binding.getRoot();
    }

    public void start() {
        binding.videoView0.getRoot().setAlpha(0);

        binding.setVideo0(getVideo());
        binding.setVideo1(getVideo());

        binding.videoView1.videoView.start();
    }

    public void stop() {
        binding.videoView0.videoView.release();
        binding.videoView1.videoView.release();
    }

    private void playVideo(final VideoViewBinding deactivate, final VideoViewBinding activate) {
        activate.videoView.start();

        Animation animation = new AlphaAnimation(1, 0);
        animation.setDuration(ExoPlayerView.DURATION);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                binding.container.bringChildToFront(activate.getRoot());
                deactivate.videoView.pause();
                deactivate.getRoot().setAlpha(1);
                deactivate.setVideo(getVideo());

                binding.loadingView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        deactivate.getRoot().startAnimation(animation);
    }

    @Override
    public void onFetch(VideoPlaylist videos) {
        this.videos = videos;
        binding.getRoot().post(new Runnable() {
            @Override
            public void run() {
                start();
            }
        });
    }

    private Video getVideo() {
        VideoPlaylist.TYPE type = VideoPlaylist.TYPE.ALL;

        if (filterTime) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            type = hour < 7 || hour >= 19 ? VideoPlaylist.TYPE.NIGHT : VideoPlaylist.TYPE.DAY;
        }

        return videos.getVideo(type);
    }

    @Override
    public void onPrepared(ExoPlayerView view) {
        if (binding.loadingView.getVisibility() == View.VISIBLE && view == binding.videoView1.videoView) {
            binding.videoView0.getRoot().setAlpha(1);
            
            Animation animation = new AlphaAnimation(1, 0);
            animation.setDuration(ExoPlayerView.DURATION / 2);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    binding.loadingView.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

            binding.loadingView.startAnimation(animation);
        }
    }

    @Override
    public void onAlmostFinished(ExoPlayerView view) {
        if (view == binding.videoView0.videoView) {
            playVideo(binding.videoView0, binding.videoView1);
        } else {
            playVideo(binding.videoView1, binding.videoView0);
        }
    }
}
