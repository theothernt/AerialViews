package com.neilturner.aerialviews.utils

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class SwipeGestureDetector(context: Context) : View.OnTouchListener {

    private val gestureDetector: GestureDetector
    private var listener: OnSwipeListener? = null
    private val swipeThreshold = 100
    private val swipeVelocityThreshold = 100

    init {
        gestureDetector = GestureDetector(context, GestureListener())
    }

    fun setOnSwipeListener(listener: OnSwipeListener) {
        this.listener = listener
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        return event?.let { gestureDetector.onTouchEvent(it) } == true
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            try {
                if (e1 == null) return false

                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x

                // Which was greater? Movement across Y or X?
                if (abs(diffX) > abs(diffY)) {
                    // Horizontal swipe
                    if (abs(diffX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
                        if (diffX > 0) {
                            // Right swipe
                            listener?.onSwipeRight()
                        } else {
                            // Left swipe
                            listener?.onSwipeLeft()
                        }
                    }
                } else {
                    // Vertical swipe
                    if (abs(diffY) > swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
                        if (diffY > 0) {
                            // Down swipe
                            listener?.onSwipeDown()
                        } else {
                            // Up swipe
                            listener?.onSwipeUp()
                        }
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return true
        }
    }

    interface OnSwipeListener {
        fun onSwipeRight()
        fun onSwipeLeft()
        fun onSwipeUp()
        fun onSwipeDown()
    }
}