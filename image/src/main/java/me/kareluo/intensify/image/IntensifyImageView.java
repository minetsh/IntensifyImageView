package me.kareluo.intensify.image;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.OverScroller;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import me.kareluo.intensify.image.IntensifyImageDelegate.ImageDrawable;

/**
 * Created by felix on 15/12/17.
 */
public class IntensifyImageView extends View implements IntensifyImage,
        IntensifyImageDelegate.Callback {

    private static final String TAG = "IntensifyImageView";

    private Paint mPaint;

    private Paint mTextPaint;

    private Paint mBoardPaint;

    private volatile Rect mDrawingRect = new Rect();

    private OverScroller mScroller;

    private IntensifyImageDelegate mDelegate;

    private OnSingleTapListener mOnSingleTapListener;

    private OnDoubleTapListener mOnDoubleTapListener;

    private OnLongPressListener mOnLongPressListener;

    private OnScaleChangeListener mOnScaleChangeListener;

    private volatile boolean vFling = false;

    private static final boolean DEBUG = false;

    public IntensifyImageView(Context context) {
        this(context, null, 0);
    }

    public IntensifyImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IntensifyImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs, defStyleAttr);
    }

    protected void initialize(Context context, AttributeSet attrs, int defStyleAttr) {
        mDelegate = new IntensifyImageDelegate(getResources().getDisplayMetrics(), this);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.IntensifyImageView);

        mDelegate.setScaleType(ScaleType.valueOf(
                a.getInt(R.styleable.IntensifyImageView_scaleType, ScaleType.FIT_CENTER.nativeInt)));

        mDelegate.setAnimateScaleType(
                a.getBoolean(R.styleable.IntensifyImageView_animateScaleType, false));

        mDelegate.setMinimumScale(
                a.getFloat(R.styleable.IntensifyImageView_minimumScale, 0f));

        mDelegate.setMaximumScale(
                a.getFloat(R.styleable.IntensifyImageView_maximumScale, Float.MAX_VALUE));

        mDelegate.setScale(a.getFloat(R.styleable.IntensifyImageView_scale, -1f));

        a.recycle();

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mPaint.setColor(Color.GREEN);
        mPaint.setStrokeWidth(1f);
        mPaint.setStyle(Paint.Style.STROKE);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mTextPaint.setColor(Color.GREEN);
        mTextPaint.setStrokeWidth(1f);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setTextSize(24);

        mBoardPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mBoardPaint.setColor(Color.RED);
        mBoardPaint.setStrokeWidth(2f);
        mBoardPaint.setStyle(Paint.Style.STROKE);

        new IntensifyImageAttacher(this);
        mScroller = new OverScroller(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDelegate.onAttached();
    }

    @Override
    protected void onDetachedFromWindow() {
        mDelegate.onDetached();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getDrawingRect(mDrawingRect);

        List<ImageDrawable> drawables = mDelegate.obtainImageDrawables(mDrawingRect);

        int save = canvas.save();
        int i = 0;
        for (ImageDrawable drawable : drawables) {
            if (drawable == null || drawable.mBitmap.isRecycled()) {
                continue;
            }
            canvas.drawBitmap(drawable.mBitmap, drawable.mSrc, drawable.mDst, mPaint);
            if (DEBUG) {
                canvas.drawRect(drawable.mDst, mPaint);
                canvas.drawText(String.valueOf(++i), drawable.mDst.left + 4,
                        drawable.mDst.top + mTextPaint.getTextSize(), mTextPaint);
            }
        }
        if (DEBUG) {
            canvas.drawRect(mDrawingRect, mBoardPaint);
            canvas.drawRect(mDelegate.getImageArea(), mBoardPaint);
        }

        canvas.restoreToCount(save);
    }

    @Override
    public void setImage(String path) {
        mScroller.abortAnimation();
        mDelegate.load(path);
    }

    @Override
    public void setImage(File file) {
        mScroller.abortAnimation();
        mDelegate.load(file);
    }

    @Override
    public void setImage(InputStream inputStream) {
        mScroller.abortAnimation();
        mDelegate.load(inputStream);
    }

    @Override
    public int getImageWidth() {
        return mDelegate.getWidth();
    }

    @Override
    public int getImageHeight() {
        return mDelegate.getHeight();
    }

    @Override
    public float getScale() {
        return mDelegate.getScale();
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        mDelegate.setScaleType(scaleType);
    }

    @Override
    public ScaleType getScaleType() {
        return mDelegate.getScaleType();
    }

    @Override
    public void setScale(float scale) {
        mDelegate.setScale(scale);
    }

    @Override
    public void addScale(float scale, float focusX, float focusY) {
        mDelegate.scale(scale, focusX + getScrollX(), focusY + getScrollY());
        postInvalidate();
    }

    @Override
    public void scroll(float distanceX, float distanceY) {
        getDrawingRect(mDrawingRect);
        Point damping = mDelegate.damping(mDrawingRect, distanceX, distanceY);
        getParent().requestDisallowInterceptTouchEvent(damping.x != 0 || damping.y != 0);
        scrollBy(damping.x, damping.y);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        } else {
            if (vFling) {
                getDrawingRect(mDrawingRect);
                mDelegate.zoomHoming(mDrawingRect);
                vFling = false;
            }
        }
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return mDelegate.getHorizontalOffset(getScrollX());
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return mDelegate.getImageWidth();
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mDelegate.getVerticalOffset(getScrollY());
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mDelegate.getImageHeight();
    }

    @Override
    public void fling(float velocityX, float velocityY) {
        getDrawingRect(mDrawingRect);
        RectF imageArea = mDelegate.getImageArea();
        if (!Utils.isEmpty(imageArea) && !Utils.contains(mDrawingRect, imageArea)) {
            if (mDrawingRect.left <= imageArea.left && velocityX < 0
                    || mDrawingRect.right >= imageArea.right && velocityX > 0) {
                velocityX = 0f;
            }

            if (mDrawingRect.top <= imageArea.top && velocityY < 0
                    || mDrawingRect.bottom >= imageArea.bottom && velocityY > 0) {
                velocityY = 0f;
            }

            if (Float.compare(velocityX, 0f) == 0 && Float.compare(velocityY, 0f) == 0) return;

            mScroller.fling(getScrollX(), getScrollY(), Math.round(velocityX), Math.round(velocityY),
                    Math.round(Math.min(imageArea.left, mDrawingRect.left)),
                    Math.round(Math.max(imageArea.right - mDrawingRect.width(), mDrawingRect.left)),
                    Math.round(Math.min(imageArea.top, mDrawingRect.top)),
                    Math.round(Math.max(imageArea.bottom - mDrawingRect.height(), mDrawingRect.top)),
                    100, 100);
            vFling = true;
            postInvalidate();
        }
    }

    @Override
    public void nextScale(float focusX, float focusY) {
        getDrawingRect(mDrawingRect);
        mDelegate.zoomScale(mDrawingRect, mDelegate.getNextStepScale(mDrawingRect),
                focusX + getScrollX(), focusY + getScrollY());
    }

    @Override
    public void onTouch(float x, float y) {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
    }

    @Override
    public void home() {
        if (mScroller.isFinished()) {
            getDrawingRect(mDrawingRect);
            mDelegate.zoomHoming(mDrawingRect);
        }
    }

    @Override
    public void singleTap(float x, float y) {
        if (mOnSingleTapListener != null) {
            mOnSingleTapListener.onSingleTap(isInside(x, y));
        }
    }

    @Override
    public void doubleTap(float x, float y) {
        if (mOnDoubleTapListener != null) {
            if (!mOnDoubleTapListener.onDoubleTap(isInside(x, y))) {
                nextScale(x, y);
            }
        } else nextScale(x, y);
    }

    @Override
    public void longPress(float x, float y) {
        if (mOnLongPressListener != null) {
            mOnLongPressListener.onLongPress(isInside(x, y));
        }
    }

    public boolean isInside(float x, float y) {
        return mDelegate.getImageArea().contains(x, y);
    }

    public void setOnSingleTapListener(OnSingleTapListener listener) {
        mOnSingleTapListener = listener;
    }

    public void setOnDoubleTapListener(OnDoubleTapListener listener) {
        mOnDoubleTapListener = listener;
    }

    public void setOnLongPressListener(OnLongPressListener listener) {
        mOnLongPressListener = listener;
    }

    public void setOnScaleChangeListener(OnScaleChangeListener listener) {
        mOnScaleChangeListener = listener;
    }

    public float getBaseScale() {
        return mDelegate.getBaseScale();
    }

    /**
     * 设置过大可能会影响图片的正常显示
     *
     * @param scale 缩放值
     */
    public void setMinimumScale(float scale) {
        mDelegate.setMinimumScale(scale);
    }

    /**
     * 设置过小可能会影响图片的正常显示
     *
     * @param scale 缩放值
     */
    public void setMaximumScale(float scale) {
        mDelegate.setMaximumScale(scale);
    }

    public float getMinimumScale() {
        return mDelegate.getMinimumScale();
    }

    public float getMaximumScale() {
        return mDelegate.getMaximumScale();
    }

    @Override
    public void onRequestInvalidate() {
        postInvalidate();
    }

    @Override
    public boolean onRequestAwakenScrollBars() {
        return awakenScrollBars();
    }

    @Override
    public void onScaleChange(final float scale) {
        if (mOnScaleChangeListener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    mOnScaleChangeListener.onScaleChange(scale);
                }
            });
        }
    }
}
