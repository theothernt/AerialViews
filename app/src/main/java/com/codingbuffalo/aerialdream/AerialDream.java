package com.codingbuffalo.aerialdream;

import android.net.Uri;
import android.service.dreams.DreamService;
import android.widget.Toast;
import android.widget.VideoView;

import com.codingbuffalo.aerialdream.service.AerialVideo;
import com.codingbuffalo.aerialdream.service.VideoService;

import java.util.List;
import java.util.Random;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class AerialDream extends DreamService implements Callback<List<AerialVideo>> {
	private List<AerialVideo> mAerialVideos;
	private Random mRandom;
	
	private VideoView mVideoView;
	
	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		setFullscreen(true);
		setContentView(R.layout.daydream_aerial);
				
		mVideoView = (VideoView) findViewById(R.id.video);
		
		mRandom = new Random(System.currentTimeMillis());
	}
	
	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
	}
	
	@Override
	public void onDreamingStarted() {
		super.onDreamingStarted();
		
		VideoService.fetchVideos(this);
	}
	
	@Override
	public void onDreamingStopped() {
		mVideoView.stopPlayback();
		
		super.onDreamingStopped();
	}
	
	/* Callback */
	@Override
	public void success(List<AerialVideo> aerialVideos, Response response) {
		mAerialVideos = aerialVideos;
		
		loadVideo();
	}
	
	@Override
	public void failure(RetrofitError error) {
		Toast.makeText(this, error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
	}
	
	private void loadVideo() {
		// Select random video
		AerialVideo video = mAerialVideos.get(mRandom.nextInt(mAerialVideos.size()));
		
		// Play video
		mVideoView.setVideoURI(Uri.parse(video.getUrl()));
		mVideoView.start();
	}
}
