package me.kareluo.intensify.image;

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

    float getScale();

    void setScaleType(ScaleType scaleType);

    ScaleType getScaleType();

    void setScale(float scale, int focusX, int focusY);

    void addScale(float scale, int focusX, int focusY);

    void scroll(float distanceX, float distanceY);

    void fling(float velocityX, float velocityY);

    void nextStepScale(int focusX, int focusY);

    void onTouch(float x, float y);

    void home();

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
