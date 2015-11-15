package com.codingbuffalo.aerialdream;

import android.content.Context;
import android.os.Handler;
import android.support.percent.PercentRelativeLayout;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import com.codingbuffalo.aerialdream.service.AerialVideo;
import com.codingbuffalo.aerialdream.service.VideoService;

import java.util.List;
import java.util.Random;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class AerialView extends PercentRelativeLayout implements Callback<List<AerialVideo>>, SimplePlayer.PlayerListener {
	private static final int FADE_DURATION = 3000;
	
	private List<AerialVideo> mAerialVideos;
	private Random            mRandom;
	private Handler           mHandler;
	private boolean           mPlayerReady;
	
	private PercentRelativeLayout mContainer;
	private View                  mLoadingView;
	
	private SimplePlayer mActivePlayer;
	private SimplePlayer mBufferPlayer;
	
	public AerialView(Context context) {
		super(context);
	}
	
	public AerialView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public AerialView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}
	
	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		
		LayoutInflater.from(getContext()).inflate(R.layout.daydream_view, this, true);
		
		mContainer = (PercentRelativeLayout) findViewById(R.id.container);
		mLoadingView = findViewById(R.id.loading);
		mActivePlayer = (SimplePlayer) findViewById(R.id.player1);
		mBufferPlayer = (SimplePlayer) findViewById(R.id.player2);
		
		mActivePlayer.setPlayerListener(this);
		
		mRandom = new Random(System.currentTimeMillis());
		mHandler = new Handler();
		mPlayerReady = false;
		
		VideoService.fetchVideos(this);
	}
	
	public void stop() {
		mHandler.removeCallbacks(mSwitcher);
		mActivePlayer.release();
		mBufferPlayer.release();
	}
	
	/* SimplePlayer.OnPlayerListener */
	@Override
	public void onPlayerInitialized(SimplePlayer simplePlayer) {
		mPlayerReady = true;
		startPlayingVideos();
	}
	
	@Override
	public void onVideoLoaded() {
		mActivePlayer.setPlayerListener(null);
		mActivePlayer.play();
		
		fadeOutLoadingView();
		
		loadNextVideo(mBufferPlayer);
		prepareNextVideo();
	}
	
	/* Retrofit.Callback */
	@Override
	public void success(List<AerialVideo> aerialVideos, Response response) {
		mAerialVideos = aerialVideos;
		startPlayingVideos();
	}
	
	@Override
	public void failure(RetrofitError error) {
		Toast.makeText(getContext(), error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		error.printStackTrace();
	}
	
	/* Helpers */
	private void switchPlayer() {
		// Switch active and buffer players
		SimplePlayer tempPlayer = mActivePlayer;
		mActivePlayer = mBufferPlayer;
		mBufferPlayer = tempPlayer;
		
		mActivePlayer.play();
		
		// Fade out top player
		Animation animation = new AlphaAnimation(1, 0);
		animation.setDuration(FADE_DURATION);
		animation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				// Put active player on top so we can fade it out in the next iteration
				mContainer.bringChildToFront(mActivePlayer);
				
				// Start buffering next video
				mBufferPlayer.setAlpha(1);
				loadNextVideo(mBufferPlayer);
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
		});
		
		mBufferPlayer.startAnimation(animation);
		prepareNextVideo();
	}
	
	private void startPlayingVideos() {
		if (mPlayerReady && mAerialVideos != null) {
			loadNextVideo(mActivePlayer);
		}
	}
	
	private void prepareNextVideo() {
		mHandler.postDelayed(mSwitcher, mActivePlayer.getDuration() - FADE_DURATION);
	}
	
	private void loadNextVideo(SimplePlayer player) {
		AerialVideo video = mAerialVideos.get(mRandom.nextInt(mAerialVideos.size()));
		
		player.load(getContext(), video);
	}
	
	private Runnable mSwitcher = new Runnable() {
		@Override
		public void run() {
			switchPlayer();
		}
	};
	
	private void fadeOutLoadingView() {
		Animation animation = new AlphaAnimation(1, 0);
		animation.setDuration(FADE_DURATION);
		animation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}
			
			@Override
			public void onAnimationEnd(Animation animation) {
				mLoadingView.setVisibility(View.GONE);
			}
			
			@Override
			public void onAnimationRepeat(Animation animation) {
			}
		});
		
		mLoadingView.startAnimation(animation);
	}
}
