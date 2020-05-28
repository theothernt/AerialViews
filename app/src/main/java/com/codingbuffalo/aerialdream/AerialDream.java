package com.codingbuffalo.aerialdream;

import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.view.View;
import com.billy.android.swipe.SmartSwipe;
import com.billy.android.swipe.SmartSwipeWrapper;
import com.billy.android.swipe.SwipeConsumer;
import com.billy.android.swipe.consumer.StayConsumer;
import com.billy.android.swipe.listener.SimpleSwipeListener;

public class AerialDream extends DreamService {
    private VideoController videoController;

    @Override
    public void onAttachedToWindow() {

        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(true);

        videoController = new VideoController(this);
        View view = videoController.getView();
        setContentView(view);

        SmartSwipe.wrap(view)
                .addConsumer(new StayConsumer())
                .enableHorizontal()
                .addListener(new SimpleSwipeListener() {
                    @Override
                    public void onSwipeOpened(SmartSwipeWrapper wrapper, SwipeConsumer consumer, int direction) {
                        if (direction == 1 || direction == 2) {
                            videoController.skipVideo();
                        }
                    }
                });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (event.getAction() == KeyEvent.ACTION_UP &&
                event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
            videoController.skipVideo();
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
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
        videoController.stop();
        super.onDreamingStopped();
    }
}
