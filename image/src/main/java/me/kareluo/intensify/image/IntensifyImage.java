package me.kareluo.intensify.image;

import android.graphics.PointF;
import android.graphics.Rect;

import java.io.File;
import java.io.InputStream;

/**
 * Created by felix on 15/12/17.
 */
public interface IntensifyImage {

    int DURATION_FLING = 400;

    int DURATION_ZOOM_HOME = 400;

    int DURATION_ZOOM = 300;

    void setImage(String path);

    void setImage(File file);

    void setImage(InputStream inputStream);

    int getImageWidth();

    int getImageHeight();

    Scale getScale();

    void setScaleType(ScaleType scaleType);

    ScaleType getScaleType();

    void setScale(float scale, int focusX, int focusY);

    void addScale(float scale, int focusX, int focusY);

    void scroll(float distanceX, float distanceY);

    void fling(float velocityX, float velocityY);

    void nextStepScale(int focusX, int focusY);

    void onTouch(float x, float y);

    void home();

    final class Scale {
        public float preScale;
        public float curScale;
        public PointF focus;

        public Scale(Scale scale) {
            this.preScale = scale.preScale;
            this.curScale = scale.curScale;
            this.focus = scale.focus;
        }

        public Scale(float scale, float focusX, float focusY) {
            this.preScale = scale;
            this.curScale = scale;
            this.focus = new PointF(focusX, focusY);
        }

        public void set(float scale, float focusX, float focusY) {
            this.preScale = curScale;
            this.curScale = scale;
            this.focus.set(focusX, focusY);
        }

        public void setScale(float scale) {
            preScale = curScale;
            curScale = scale;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Scale scale = (Scale) o;
            return scale.focus.equals(focus) && Float.compare(scale.curScale, curScale) == 0;
        }
    }

    final class IntensifyInfo {
        volatile Scale mScale;

        volatile Rect mVisibleRect;

        volatile Rect mImageRect;
    }

    enum ScaleType {

        NONE(0),

        FIT_AUTO(1),

        FIT_START(2),

        FIT_CENTER(3),

        FIT_END(4);

        ScaleType(int ni) {
            nativeInt = ni;
        }

        final int nativeInt;
    }
}
