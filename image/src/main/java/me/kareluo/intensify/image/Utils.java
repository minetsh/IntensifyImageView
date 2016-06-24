package me.kareluo.intensify.image;

import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Created by felix on 16/1/16.
 */
class Utils {

    private Utils() {

    }

    public static void center(RectF rect, Rect frame) {
        rect.offset(frame.exactCenterX() - rect.centerX(), frame.exactCenterY() - rect.centerY());
    }

    public static void center(Rect rect, Rect frame) {
        rect.offset(frame.centerX() - rect.centerX(), frame.centerY() - rect.centerY());
    }

    public static void centerVertical(RectF rect, Rect frame) {
        rect.offset(0, frame.exactCenterY() - rect.centerY());
    }

    public static void centerHorizontal(RectF rect, Rect frame) {
        rect.offset(frame.exactCenterX() - rect.centerX(), 0);
    }

    public static void home(RectF rect, Rect frame) {
        if (rect.height() < frame.height()) {
            Utils.centerVertical(rect, frame);
        } else {
            if (rect.top > frame.top) {
                rect.offset(0, frame.top - rect.top);
            } else if (rect.bottom < frame.bottom) {
                rect.offset(0, frame.bottom - rect.bottom);
            }
        }

        if (rect.width() < frame.width()) {
            Utils.centerHorizontal(rect, frame);
        } else {
            if (rect.left > frame.left) {
                rect.offset(frame.left - rect.left, 0);
            } else if (rect.right < frame.right) {
                rect.offset(frame.right - rect.right, 0);
            }
        }
    }

    public static boolean contains(RectF rect, Rect r) {
        return rect.contains(r.left, r.top, r.right, r.bottom);
    }

    public static boolean contains(Rect r, RectF rect) {
        return r.contains(Math.round(rect.left), Math.round(rect.top),
                Math.round(rect.right), Math.round(rect.bottom));
    }

    public static RectF evaluate(float fraction, RectF startValue, RectF endValue, RectF reuseRect) {
        float left = startValue.left + (endValue.left - startValue.left) * fraction;
        float top = startValue.top + (endValue.top - startValue.top) * fraction;
        float right = startValue.right + (endValue.right - startValue.right) * fraction;
        float bottom = startValue.bottom + (endValue.bottom - startValue.bottom) * fraction;
        reuseRect.set(left, top, right, bottom);
        return reuseRect;
    }

    public static boolean isEmpty(Rect rect) {
        return rect == null || rect.isEmpty();
    }

    public static boolean isEmpty(RectF rect) {
        return rect == null || rect.isEmpty();
    }

    /**
     * 获取接近size的2的幂的数字,如1、2、4
     *
     * @param size
     * @return
     */
    public static int getSampleSize(int size) {
        if (size <= 1) return 1;
        int sampleSize = 1;
        while (size > 1) {
            size >>= 1;
            sampleSize <<= 1;
        }
        return sampleSize;
    }

    public static boolean equals(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    public static Rect round(RectF rect) {
        return new Rect(Math.round(rect.left), Math.round(rect.top),
                Math.round(rect.right), Math.round(rect.bottom));
    }

    public static Rect blockRect(int x, int y, float size, int offsetX, int offsetY) {
        Rect result = new Rect();
        new RectF(x * size + offsetX, y * size + offsetY,
                (x + 1) * size + offsetX, (y + 1) * size + offsetY).round(result);
        return result;
    }

    public static Rect blocks(RectF rect, float size) {
        return new Rect(
                floor(rect.left / size), floor(rect.top / size),
                ceil(rect.right / size), ceil(rect.bottom / size)
        );
    }

    public static int floor(float value) {
        return (int) Math.floor(value);
    }

    public static int ceil(float value) {
        return (int) Math.ceil(value);
    }

    /**
     * Returns {@code o} if non-null, or throws {@code NullPointerException}.
     */
    public static <T> T requireNonNull(T o) {
        if (o == null) {
            throw new NullPointerException();
        }
        return o;
    }

    public static float range(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static boolean strictInRange(float v, float min, float max) {
        return v > min && v < max;
    }

    public static boolean inRange(float v, float min, float max) {
        return v >= min && v <= max;
    }

    public static boolean strictOutRange(float v, float min, float max) {
        return v < min || v > max;
    }

    public static boolean outRange(float v, float min, float max) {
        return v <= min || v >= max;
    }
}
