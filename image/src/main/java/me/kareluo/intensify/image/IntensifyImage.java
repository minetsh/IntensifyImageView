package me.kareluo.intensify.image;

import java.io.File;
import java.io.InputStream;

/**
 * Created by felix on 15/12/17.
 */
public interface IntensifyImage {

    int DURATION_ZOOM = 300;

    void setImage(String path);

    void setImage(File file);

    void setImage(InputStream inputStream);

    void setScaleType(ScaleType scaleType);

    int getImageWidth();

    int getImageHeight();

    float getScale();

    ScaleType getScaleType();

    void setScale(float scale);

    void addScale(float scale, float focusX, float focusY);

    void scroll(float distanceX, float distanceY);

    void fling(float velocityX, float velocityY);

    void nextScale(float focusX, float focusY);

    void onTouch(float x, float y);

    void home();

    void singleTap(float x, float y);

    void doubleTap(float x, float y);

    void longPress(float x, float y);

    enum ScaleType {

        // not use this.
        NONE(0),

        FIT_AUTO(1),

        FIT_CENTER(2),

        CENTER(3),

        CENTER_INSIDE(4);

        ScaleType(int ni) {
            nativeInt = ni;
        }

        static ScaleType valueOf(int value) {
            if (value < 0 || value >= values().length) {
                return FIT_CENTER;
            }
            return values()[value];
        }

        final int nativeInt;
    }

    interface OnSingleTapListener {
        void onSingleTap(boolean inside);
    }

    interface OnDoubleTapListener {
        boolean onDoubleTap(boolean inside);
    }

    interface OnLongPressListener {
        void onLongPress(boolean inside);
    }

    interface OnScaleChangeListener {
        void onScaleChange(float scale);
    }
}
