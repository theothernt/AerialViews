package com.codingbuffalo.aerialdream;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class TestActivity extends Activity {
    private VideoController videoController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setTitle(R.string.daydream_name);

        videoController = new VideoController(this);
        setContentView(videoController.getView());
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    protected void onStop() {
        videoController.stop();
        super.onStop();
    }
}
