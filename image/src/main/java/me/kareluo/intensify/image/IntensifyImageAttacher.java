package me.kareluo.intensify.image;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Created by felix on 16/5/24.
 */
public class IntensifyImageAttacher implements View.OnTouchListener {
    private static final String TAG = "IntensifyImageAttacher";

    private IntensifyImageView mIntensifyView;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector mGestureDetector;

    public IntensifyImageAttacher(IntensifyImageView intensifyView) {
        mIntensifyView = intensifyView;
        Context context = intensifyView.getContext();
        mScaleGestureDetector = new ScaleGestureDetector(context, new OnScaleGestureAdapter());
        mGestureDetector = new GestureDetector(context, new OnGestureAdapter());
        mIntensifyView.setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return mGestureDetector.onTouchEvent(event) | mScaleGestureDetector.onTouchEvent(event);
    }

    private class OnScaleGestureAdapter extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mIntensifyView.addScale(detector.getScaleFactor(),
                    detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mIntensifyView.home();
        }
    }

    private class OnGestureAdapter extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            mIntensifyView.onTouch(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            mIntensifyView.doubleTap(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mIntensifyView.scroll(distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mIntensifyView.fling(-velocityX, -velocityY);
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            mIntensifyView.singleTap(e.getX(), e.getY());
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mIntensifyView.longPress(e.getX(), e.getY());
        }
    }
}
