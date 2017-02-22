package com.codingbuffalo.aerialdream;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.TextureView;
import android.widget.MediaController;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

public class ExoPlayerView extends TextureView implements MediaController.MediaPlayerControl, SimpleExoPlayer.VideoListener, ExoPlayer.EventListener {
    public static final long DURATION = 5000;

    private static final long GB_IN_BYTES = 1073741824;

    private SimpleExoPlayer player;
    private MediaSource mediaSource;
    private OnPlayerEventListener listener;
    private Handler handler;
    private float aspectRatio;
    private boolean prepared;
    private long cacheSize;

    public ExoPlayerView(Context context) {
        this(context, null);
    }

    public ExoPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (isInEditMode()) {
            return;
        }

        handler = new Handler();
        player = ExoPlayerFactory.newSimpleInstance(context, new DefaultTrackSelector(), new DefaultLoadControl());

        player.setVideoTextureView(this);
        player.setVideoListener(this);
        player.addListener(this);
    }

    public void setCacheSize(int cacheSize) {
        if (cacheSize < 0) {
            this.cacheSize = Long.MAX_VALUE;
        } else {
            this.cacheSize = cacheSize * GB_IN_BYTES;
        }
    }

    public void setUri(Uri uri) {
        if (uri == null) {
            return;
        }

        player.stop();
        prepared = false;
        if (mediaSource != null) {
            mediaSource.releaseSource();
        }

        DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory("Aerial Dream");
        DataSource.Factory dataSourceFactory = cacheSize > 0
                ? new CacheDataSourceFactory(new SimpleCache(getContext().getCacheDir(), new LeastRecentlyUsedCacheEvictor(cacheSize)), httpDataSourceFactory, 0)
                : httpDataSourceFactory;

        mediaSource = new ExtractorMediaSource(uri, dataSourceFactory, new DefaultExtractorsFactory(), handler, null);
        player.prepare(mediaSource);
    }

    @Override
    protected void onDetachedFromWindow() {
        pause();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (aspectRatio > 0) {
            int newWidth;
            int newHeight;

            newHeight = MeasureSpec.getSize(heightMeasureSpec);
            newWidth = (int) (newHeight * aspectRatio);
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setOnPlayerListener(OnPlayerEventListener listener) {
        this.listener = listener;
    }

    public void release() {
        player.release();
    }

    /* MediaPlayerControl */
    @Override
    public void start() {
        player.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        player.setPlayWhenReady(false);
    }

    @Override
    public int getDuration() {
        return (int) player.getDuration();
    }

    @Override
    public int getCurrentPosition() {
        return (int) player.getCurrentPosition();
    }

    @Override
    public void seekTo(int pos) {
        player.seekTo(pos);
    }

    @Override
    public boolean isPlaying() {
        return player.getPlayWhenReady();
    }

    @Override
    public int getBufferPercentage() {
        return player.getBufferedPercentage();
    }

    @Override
    public boolean canPause() {
        return player.getDuration() > 0;
    }

    @Override
    public boolean canSeekBackward() {
        return player.getDuration() > 0;
    }

    @Override
    public boolean canSeekForward() {
        return player.getDuration() > 0;
    }

    @Override
    public int getAudioSessionId() {
        return player.getAudioSessionId();
    }

    /* EventListener */
    @Override
    public void onLoadingChanged(boolean isLoading) {
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (!prepared && playbackState == ExoPlayer.STATE_READY) {
            prepared = true;
            listener.onPrepared(this);
        }

        if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
            removeCallbacks(timerRunnable);
            postDelayed(timerRunnable, getDuration() - DURATION);
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        error.printStackTrace();

        // Attempt to reload video
        removeCallbacks(errorRecoveryRunnable);
        postDelayed(errorRecoveryRunnable, DURATION);
    }

    @Override
    public void onPositionDiscontinuity() {
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        aspectRatio = height == 0 ? 0 : (width * pixelWidthHeightRatio) / height;
        requestLayout();
    }

    @Override
    public void onRenderedFirstFrame() {
    }

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            listener.onAlmostFinished(ExoPlayerView.this);
        }
    };

    private Runnable errorRecoveryRunnable = new Runnable() {
        @Override
        public void run() {
            player.prepare(mediaSource);
        }
    };

    public interface OnPlayerEventListener {
        void onAlmostFinished(ExoPlayerView view);

        void onPrepared(ExoPlayerView view);
    }
}
