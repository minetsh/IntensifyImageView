package me.kareluo.intensify.image;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver.OnScrollChangedListener;

import java.util.Arrays;

/**
 * Created by felix on 15/12/17.
 */
public abstract class IntensifyView extends View implements OnScrollChangedListener {

    private int[] mLocation = new int[2];
    private Rect mVisibleRect;

    public IntensifyView(Context context) {
        super(context);
        initialize(context, null, 0);
    }

    public IntensifyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs, 0);
    }

    public IntensifyView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public IntensifyView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize(context, attrs, defStyleAttr);
    }

    private void initialize(Context context, AttributeSet attrs, int defStyleAttr) {

    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (this == changedView) {
            if (getWindowVisibility() == VISIBLE) {
                requestLayout();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(this);
        updateWindow(false, false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onScrollChanged() {
        updateWindow(false, false);
    }

    private void updateWindow(boolean focus, boolean invalidate) {
        int[] location = new int[2];
        getLocationInWindow(location);

        int visibility = getWindowVisibility() | getVisibility();

        if (focus || invalidate || visibility == VISIBLE || !Arrays.equals(location, mLocation)) {
            mLocation[0] = location[0];
            mLocation[1] = location[1];
            Rect visibleRect = getVisibleRect();
            if (visibleRect == null || !visibleRect.equals(mVisibleRect)) {
                mVisibleRect = visibleRect;
                onUpdateWindow(visibleRect);
            }
        }
    }

    protected abstract void onUpdateWindow(Rect rect);

    protected Rect getVisibleRect() {
        int[] location = new int[2];
        getLocationOnScreen(location);
        Rect rect = new Rect();
        getGlobalVisibleRect(rect);
        rect.offset(-location[0], -location[1]);
        return rect;
    }

}
