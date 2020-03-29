package com.codingbuffalo.aerialdream;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

public class AerialDream extends DreamService {
    private VideoController videoController;

    @Override
    public void onAttachedToWindow() {

        super.onAttachedToWindow();
        setFullscreen(true);
        setInteractive(true);

        videoController = new VideoController(this);
        setContentView(videoController.getView());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (event.getAction() == KeyEvent.ACTION_UP &&
                event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
            videoController.skipVideo();
            return false;
        }

        return super.dispatchKeyEvent(event);
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
