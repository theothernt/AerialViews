package com.codingbuffalo.aerialdream;

import android.app.Activity;
import android.os.Bundle;

public class TestActivity extends Activity {
	private AerialView mAerialView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.daydream);
		
		mAerialView = (AerialView) findViewById(R.id.aerial);
	}
	
	@Override
	protected void onStop() {
		mAerialView.stop();
		
		super.onStop();
	}
}
