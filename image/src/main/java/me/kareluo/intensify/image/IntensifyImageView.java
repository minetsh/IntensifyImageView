package me.kareluo.intensify.image;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.renderscript.Float2;
import android.util.AttributeSet;
import android.widget.OverScroller;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import me.kareluo.intensify.image.IntensifyImageManager.ImageDrawable;

/**
 * Created by felix on 15/12/17.
 */
public class IntensifyImageView extends IntensifyView implements IntensifyImage,
        IntensifyImageManager.Callback {
    private static final String TAG = "IntensifyImageView";

    private Paint mPaint;

    private Paint mTextPaint;

    private Paint mBoardPaint;

    private List<Float> mScaleSteps = new ArrayList<>();

    private float mMinimumScale = 0.01f;

    private float mMaximumScale = 1000f;

    private volatile Rect mDrawingRect = new Rect();

    private OverScroller mScroller;

    private IntensifyImageManager mIntensifyManager;

    private OnSingleTapListener mOnSingleTapListener;

    private OnDoubleTapListener mOnDoubleTapListener;

    private OnLongPressListener mOnLongPressListener;

    private volatile boolean fling = false;

    private static final boolean DEBUG = false;

    public IntensifyImageView(Context context) {
        super(context);
    }

    public IntensifyImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IntensifyImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void initialize(Context context, AttributeSet attrs, int defStyleAttr) {
        mIntensifyManager = new IntensifyImageManager(getResources().getDisplayMetrics(), this);
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

        new IntensifyViewAttacher<>(this);
        mScroller = new OverScroller(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mIntensifyManager.onAttached();
    }

    @Override
    protected void onDetachedFromWindow() {
        mIntensifyManager.onDetached();
        super.onDetachedFromWindow();
    }

//    @Override
//    protected void onUpdateWindow(Rect rect) {
//        invalidate(getVisibleRect());
//    }

    @Override
    protected void onDraw(Canvas canvas) {
        getDrawingRect(mDrawingRect);

        List<ImageDrawable> drawables = mIntensifyManager.getImageDrawables(mDrawingRect);

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
            canvas.drawRect(mIntensifyManager.getImageArea(), mBoardPaint);
        }

        canvas.restoreToCount(save);
    }

    @Override
    public void setImage(String path) {
        mIntensifyManager.load(path);
    }

    @Override
    public void setImage(File file) {
        mIntensifyManager.load(file);
    }

    @Override
    public void setImage(InputStream inputStream) {
        mIntensifyManager.load(inputStream);
    }

    @Override
    public int getImageWidth() {
        return mIntensifyManager != null ? mIntensifyManager.getWidth() : 0;
    }

    @Override
    public int getImageHeight() {
        return mIntensifyManager != null ? mIntensifyManager.getHeight() : 0;
    }

    @Override
    public float getScale() {
        return mIntensifyManager.getScale();
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        mIntensifyManager.setScaleType(scaleType);
    }

    @Override
    public ScaleType getScaleType() {
        return mIntensifyManager.getScaleType();
    }

    @Override
    public void setScale(float scale, int focusX, int focusY) {
        if (scale < mMinimumScale) scale = mMinimumScale;
        if (scale > mMaximumScale) scale = mMaximumScale;
        postInvalidate();
    }

    @Override
    public void addScale(float scale, int focusX, int focusY) {
        mIntensifyManager.transform(scale, focusX + getScrollX(), focusY + getScrollY());
        postInvalidate();
    }

    @Override
    public void scroll(float distanceX, float distanceY) {
        getDrawingRect(mDrawingRect);
        Float2 damping = mIntensifyManager.damping(mDrawingRect, distanceX, distanceY);
        scrollBy(Math.round(damping.x), Math.round(damping.y));
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        } else {
            if (fling) {
                getDrawingRect(mDrawingRect);
                mIntensifyManager.zoomHoming(mDrawingRect);
                fling = false;
            }
        }
    }

    @Override
    public void fling(float velocityX, float velocityY) {
        getDrawingRect(mDrawingRect);
        RectF imageArea = mIntensifyManager.getImageArea();
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
            fling = true;
            postInvalidate();
        }
    }

    @Override
    public void nextScale(int focusX, int focusY) {
        getDrawingRect(mDrawingRect);
        mIntensifyManager.zoomScale(mDrawingRect, mIntensifyManager.getNextStepScale(mDrawingRect),
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
            mIntensifyManager.zoomHoming(mDrawingRect);
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
        nextScale(Math.round(x), Math.round(y));
        if (mOnDoubleTapListener != null) {
            mOnDoubleTapListener.onDoubleTap(isInside(x, y));
        }
    }

    @Override
    public void longPress(float x, float y) {
        if (mOnLongPressListener != null) {
            mOnLongPressListener.onLongPress(isInside(x, y));
        }
    }

    public boolean isInside(float x, float y) {
        return mIntensifyManager.getImageArea().contains(x, y);
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

    public void clearScaleStep() {
        mScaleSteps.clear();
    }

    public void addScaleStep(float scale) {
        mScaleSteps.add(scale);
    }

    public float getBaseScale() {
        return mIntensifyManager.getBaseScale();
    }

    public void setMinimumScale(float scale) {
        mMinimumScale = scale;
    }

    public void setMaximumScale(float scale) {
        mMaximumScale = scale;
    }

    @Override
    public void onImageLoadFinished(int width, int height) {
        Logger.d(TAG, "Load finished: width=" + width + ",height=" + height);

    }

    @Override
    public void onImageInitFinished(int sampleSize) {
        Logger.d(TAG, "Init finished: sampleSize=" + sampleSize);
        mScaleSteps.clear();
        float baseScale = mIntensifyManager.getBaseScale();
        mScaleSteps.add(baseScale);
        mScaleSteps.add(baseScale * 2f);
        mScaleSteps.add(baseScale * 3f);
    }

    @Override
    public void onLocationChanged(float x, float y) {
        scrollTo(Math.abs(Math.min(Math.round(x), 0)), Math.abs(Math.min(Math.round(y), 0)));
    }

    @Override
    public void onImageScaleChanged(float scale, float x, float y) {
        Logger.d(TAG, "Scale Changed: scale=" + scale + ", x=" + x + ",y=" + y);
    }

    @Override
    public void onImageBlockLoadFinished() {

    }

    @Override
    public void onRequestInvalidate() {
        postInvalidate();
    }

    @Override
    public void onInitScaleTypeFinished(float scale) {

    }

    @Override
    public void onHomingEnd(RectF imageRect) {

    }

    @Override
    public void onError(String message, Exception e) {
        // TODO: 错误处理
    }
}
