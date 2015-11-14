package com.codingbuffalo.aerialdream;

import android.service.dreams.DreamService;

public class AerialDream extends DreamService {
	private AerialView mAerialView;
	
	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		setFullscreen(true);
		setContentView(R.layout.daydream);
		
		mAerialView = (AerialView) findViewById(R.id.aerial);
	}
	
	/* DreamService */
	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
	}
	
	@Override
	public void onDreamingStarted() {
		super.onDreamingStarted();
	}
	
	public void onDreamingStopped() {
		mAerialView.stop();
		
		super.onDreamingStopped();
	}
}
