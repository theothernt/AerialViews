package com.codingbuffalo.aerialdream;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.MediaController;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.video.VideoListener;

public class ExoPlayerView extends SurfaceView implements MediaController.MediaPlayerControl, VideoListener, Player.EventListener {
    public static final long DURATION = 800;
    public static final long MAX_RETRIES = 2;

    private SimpleExoPlayer player;
    private MediaItem mediaItem;
    private OnPlayerEventListener listener;
    private int retries;
    private float aspectRatio;
    private boolean useReducedBuffering;
    private boolean enableTunneling;
    private boolean muteVideo;
    private boolean prepared;

    public ExoPlayerView(Context context) {
        this(context, null);
    }

    public ExoPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (isInEditMode()) {
            return;
        }

        muteVideo = true;
        enableTunneling = true;
        useReducedBuffering = true;

        player = buildPlayer(context);
        player.setVideoSurfaceView(this);
        player.addVideoListener(this);
        player.addListener(this);
    }

    public void setUri(Uri uri) {
        if (uri == null) {
            return;
        }

        player.stop();
        prepared = false;
        retries = 0;

        mediaItem = new MediaItem.Builder()
                .setUri(uri)
                .build();

        player.setMediaItem(mediaItem);
        player.prepare();
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
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                Log.i("ExoPlayerView", "Player: Buffering...");
                break;
            case Player.STATE_READY:
                Log.i("ExoPlayerView", "Player: Ready...");
                break;
            case Player.STATE_IDLE:
                Log.i("ExoPlayerView", "Player: Idle...");
                break;
            case Player.STATE_ENDED:
                Log.i("ExoPlayerView", "Player: Ended...");
                break;
            default:
        }

        if (!prepared && playbackState == Player.STATE_READY) {
            prepared = true;
            listener.onPrepared(this);
        }

        if (playWhenReady && playbackState == Player.STATE_READY) {
            removeCallbacks(timerRunnable);
            postDelayed(timerRunnable, getDuration() - DURATION);
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        //error.printStackTrace();

        // Attempt to reload video
        removeCallbacks(errorRecoveryRunnable);
        postDelayed(errorRecoveryRunnable, DURATION);
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        aspectRatio = height == 0 ? 0 : (width * pixelWidthHeightRatio) / height;
        requestLayout();
    }

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            listener.onAlmostFinished(ExoPlayerView.this);
        }
    };

    private Runnable delayedStartRunnable = new Runnable() {
        @Override
        public void run() {
            listener.onPrepared(ExoPlayerView.this);
        }
    };

    private Runnable errorRecoveryRunnable = new Runnable() {
        @Override
        public void run() {

            retries++;
            Log.i("ExoPlayerView", "Retries: " + retries);

            if (retries >= MAX_RETRIES) {
                listener.onError(ExoPlayerView.this);
            } else {
                player.stop();
                player.setMediaItem(mediaItem);
                player.prepare();
            }
        }
    };

    private SimpleExoPlayer buildPlayer(Context context) {

        DefaultLoadControl loadControl;
        if (useReducedBuffering) {
            // Buffer sizes while playing
            final int minBuffer = 5000;
            final int maxBuffer = 10000;

            // Initial buffer size to start playback
            final int bufferForPlayback = 1000;
            final int bufferForPlaybackAfterRebuffer = 5000;

            loadControl = new DefaultLoadControl
                    .Builder()
                    .setBufferDurationsMs(
                            minBuffer,
                            maxBuffer,
                            bufferForPlayback,
                            bufferForPlaybackAfterRebuffer)
                    .build();
        } else {
            loadControl = new DefaultLoadControl
                    .Builder()
                    .build();
        }

        DefaultTrackSelector trackSelector;
        if (enableTunneling) {

            DefaultTrackSelector.Parameters parameters = new DefaultTrackSelector
                    .ParametersBuilder(context)
                    .setTunnelingAudioSessionId(C.generateAudioSessionIdV21(context))
                    .build();

            trackSelector = new DefaultTrackSelector(context);
            trackSelector.setParameters(parameters);
        } else {
            trackSelector = new DefaultTrackSelector(context);
        }

        SimpleExoPlayer player =  new SimpleExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setTrackSelector(trackSelector)
                .build();

        if (muteVideo)  {
            player.setVolume(0);
        }

        return player;
    }

    public interface OnPlayerEventListener {
        void onAlmostFinished(ExoPlayerView view);

        void onPrepared(ExoPlayerView view);

        void onError(ExoPlayerView view);
    }
}