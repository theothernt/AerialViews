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
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;

public class ExoPlayerView extends TextureView implements MediaController.MediaPlayerControl, SimpleExoPlayer.VideoListener, ExoPlayer.EventListener {
    private SimpleExoPlayer player;
    private OnPlayerEventListener listener;
    private Handler handler;
    private float aspectRatio;
    private boolean prepared;

    public ExoPlayerView(Context context) {
        this(context, null);
    }

    public ExoPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        handler = new Handler();
        player = ExoPlayerFactory.newSimpleInstance(context, new DefaultTrackSelector(handler), new DefaultLoadControl());

        player.setVideoTextureView(this);
        player.setVideoListener(this);
        player.addListener(this);
    }

    public void setVideoURI(Uri uri) {
        player.setPlayWhenReady(false);
        prepared = false;

        MediaSource mediaSource = new ExtractorMediaSource(uri, new DefaultHttpDataSourceFactory("Aerial Dream"), new DefaultExtractorsFactory(), handler, null);
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
            listener.onPrepared();
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        error.printStackTrace();
        listener.onError(error);
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

    @Override
    public void onVideoTracksDisabled() {
    }

    public interface OnPlayerEventListener {
        void onPrepared();

        void onError(ExoPlaybackException error);
    }
}
