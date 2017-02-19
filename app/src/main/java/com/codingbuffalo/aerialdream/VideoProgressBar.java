package com.codingbuffalo.aerialdream;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View; 
import android.widget.MediaController;

public class VideoProgressBar extends View {
    private static final int COLOR = 0x66FFFFFF;

    private MediaController.MediaPlayerControl controller;
    private Paint paint;

    public VideoProgressBar(Context context) {
        this(context, null);
    }

    public VideoProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR);
    }

    public void setController(MediaController.MediaPlayerControl controller) {
        this.controller = controller;
        postInvalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (controller == null || isInEditMode()) {
            return;
        }

        float progress = controller.getCurrentPosition() / (float) controller.getDuration();
        float x = progress * getWidth();

        canvas.drawRect(0, 0, x, getHeight(), paint);

        postInvalidate();
    }
}