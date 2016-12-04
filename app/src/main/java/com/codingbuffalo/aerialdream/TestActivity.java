package com.codingbuffalo.aerialdream;

import android.app.Activity;
import android.os.Bundle;

public class TestActivity extends Activity {
    private VideoController videoController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        videoController = new VideoController(this);
        setContentView(videoController.getView());
    }

    @Override
    protected void onStop() {
        videoController.stop();
        super.onStop();
    }
}
