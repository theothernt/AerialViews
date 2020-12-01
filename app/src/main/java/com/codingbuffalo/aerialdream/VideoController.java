package com.codingbuffalo.aerialdream;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import androidx.databinding.DataBindingUtil;
import androidx.preference.PreferenceManager;
import com.codingbuffalo.aerialdream.data.Video;
import com.codingbuffalo.aerialdream.data.VideoInteractor;
import com.codingbuffalo.aerialdream.data.VideoPlaylist;
import com.codingbuffalo.aerialdream.databinding.AerialDreamBinding;
import com.codingbuffalo.aerialdream.databinding.VideoViewBinding;

import java.util.Set;

public class VideoController implements VideoInteractor.Listener, ExoPlayerView.OnPlayerEventListener {
    private AerialDreamBinding binding;
    private VideoPlaylist playlist;
    private String videoType2019;
    private boolean canSkip;
    private int videoSource;

    public VideoController(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        binding = DataBindingUtil.inflate(inflater, R.layout.aerial_dream, null, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> uiPrefs = prefs.getStringSet("ui_options", null);

        boolean showClock = true;
        boolean showLocation = true;
        boolean alternateText = false;

        if (!uiPrefs.contains("0")) showClock = false;
        if (!uiPrefs.contains("1")) showLocation = false;
        if (uiPrefs.contains("3")) alternateText = true;

        videoType2019 = prefs.getString("source_apple_2019", "1080_h264");
        videoSource = Integer.parseInt(prefs.getString("video_source", "0"));

        binding.setShowLocation(showLocation);

        if (showClock) {
            binding.setShowClock(!alternateText);
            binding.setShowAltClock(alternateText);
        } else {
            binding.setShowClock(showClock);
            binding.setShowAltClock(showClock);
        }

        binding.videoView0.setController(binding.videoView0.videoView);
        binding.videoView0.videoView.setOnPlayerListener(this);

        new VideoInteractor(
                context,
                videoSource,
                videoType2019,
                this
        ).fetchVideos();

        canSkip = true;
    }

    public View getView() {
        return binding.getRoot();
    }

    public void start() { loadVideo(binding.videoView0, getVideo()); }

    public void stop() {
        binding.videoView0.videoView.release();
    }

    public void skipVideo() { fadeOutCurrentVideo(); }

    private void fadeOutCurrentVideo() {

        if (!canSkip) return;
        canSkip = false;

        Animation animation = new AlphaAnimation(0, 1);
        animation.setDuration(ExoPlayerView.DURATION);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                binding.loadingView.setVisibility(View.VISIBLE);
                loadVideo(binding.videoView0, getVideo());
                binding.setAltTextPosition(!binding.getAltTextPosition());
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        binding.loadingView.startAnimation(animation);
    }

    private void fadeInNextVideo() {
        if (binding.loadingView.getVisibility() == View.VISIBLE) {

            Animation animation = new AlphaAnimation(1, 0);
            animation.setDuration(ExoPlayerView.DURATION);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    binding.loadingView.setVisibility(View.GONE);
                    canSkip = true;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

            binding.loadingView.startAnimation(animation);
        }
    }

    @Override
    public void onFetch(VideoPlaylist videos) {
        this.playlist = videos;
        binding.getRoot().post(this::start);
    }

    private void loadVideo(VideoViewBinding videoBinding, Video video) {
        Log.i("LoadVideo", "Playing: " + video.getLocation() + " - " + video.getUri(videoType2019));

        videoBinding.videoView.setUri(video.getUri(videoType2019));
        videoBinding.location.setText(video.getLocation());

        videoBinding.videoView.start();
    }

    private Video getVideo() {
        return playlist.getVideo();
    }

    @Override
    public void onPrepared(ExoPlayerView view) {
        fadeInNextVideo();
    }

    @Override
    public void onAlmostFinished(ExoPlayerView view) {
        fadeOutCurrentVideo();
    }

    @Override
    public void onError(ExoPlayerView view) {
        binding.getRoot().post(this::start);
    }
}
