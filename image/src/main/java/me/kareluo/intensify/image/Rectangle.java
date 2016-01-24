package me.kareluo.intensify.image;

import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Created by felix on 16/1/16.
 */
public class Rectangle {

    public static void center(RectF rect, Rect border) {
        rect.offset(border.centerX() - rect.centerX(), border.centerY() - rect.centerY());
    }

    public static void centerVertical(RectF rect, Rect border) {
        float offsetY = border.centerY() - rect.centerY();
        rect.top += offsetY;
        rect.bottom += offsetY;
    }

    public static void centerHorizontal(RectF rect, Rect border) {
        float offsetX = border.centerX() - rect.centerX();
        rect.left += offsetX;
        rect.right += offsetX;
    }

    public static void home(RectF rect, Rect border) {
        if (rect.height() < border.height()) {
            Rectangle.centerVertical(rect, border);
        } else {
            if (rect.top > border.top) {
                rect.offset(0, border.top - rect.top);
            } else if (rect.bottom < border.bottom) {
                rect.offset(0, border.bottom - rect.bottom);
            }
        }

        if (rect.width() < border.width()) {
            Rectangle.centerHorizontal(rect, border);
        } else {
            if (rect.left > border.left) {
                rect.offset(border.left - rect.left, 0);
            } else if (rect.right < border.right) {
                rect.offset(border.right - rect.right, 0);
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
}
