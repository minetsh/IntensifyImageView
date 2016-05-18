package me.kareluo.intensify.image;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
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
    private static final boolean DEBUG = false;
    private static final String TAG = "IntensifyImageView";

    private volatile long mPreInvalidateTime = 0l;

    private volatile Runnable mRunnable;

    private IntensifyImageManager mIntensifyManager;

    private IntensifyInfo mInfo = new IntensifyInfo();

    private Paint mPaint;

    private Paint mTextPaint;

    private Paint mBoardPaint;

    private List<Float> mScaleSteps = new ArrayList<>();

    private IntensifyViewAttacher<IntensifyImageView> mAttacher;

    private float mMinimumScale = 0.01f;

    private float mMaximumScale = 1000f;

    private volatile Rect mDrawingRect = new Rect();

    private Scale mScale = new Scale(1f, 0f, 0f);

    private OverScroller mScroller;

    private volatile boolean fling = false;

    // 最高62.5帧每秒
    private static final int LOOP_FRAME_MILLIS = 16;

    {
        mInfo.mImageRect = new Rect();
        mInfo.mVisibleRect = new Rect();
        mInfo.mScale = new Scale(1f, 0, 0);
    }

    public IntensifyImageView(Context context) {
        super(context);
        initialize(context, null, 0);
    }

    public IntensifyImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs, 0);
    }

    public IntensifyImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs, defStyleAttr);
    }

    public IntensifyImageView(Context context, AttributeSet attrs, int styleAttr, int styleRes) {
        super(context, attrs, styleAttr, styleRes);
        initialize(context, attrs, styleAttr);
    }

    private void initialize(Context context, AttributeSet attrs, int defStyleAttr) {
        mIntensifyManager = new IntensifyImageManager(getResources().getDisplayMetrics(), mInfo, this);
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

        mAttacher = new IntensifyViewAttacher<>(this);
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

    @Override
    protected void onUpdateWindow(Rect rect) {
        mRunnable = null;
        mPreInvalidateTime = SystemClock.uptimeMillis();
        invalidate(getVisibleRect());
    }

    private void requestInvalidate() {
        postInvalidate();

        if (mRunnable != null) return;
        long duration = SystemClock.uptimeMillis() - mPreInvalidateTime;
        if (duration < LOOP_FRAME_MILLIS) {
            postDelayed(mRunnable = new Runnable() {
                @Override
                public void run() {
                    mRunnable = null;
                    mPreInvalidateTime = SystemClock.uptimeMillis();
                    postInvalidate();
                }
            }, LOOP_FRAME_MILLIS - duration);
        } else {
            if (Looper.getMainLooper() == Looper.myLooper()) {
                mRunnable = null;
                mPreInvalidateTime = SystemClock.uptimeMillis();
                invalidate(getVisibleRect());
            } else {
                post(mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mRunnable = null;
                        mPreInvalidateTime = SystemClock.uptimeMillis();
                        invalidate(getVisibleRect());
                    }
                });
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getDrawingRect(mDrawingRect);

        Logger.d(TAG, "DR:" + mDrawingRect);
        int save = canvas.save();



        List<ImageDrawable> drawables = mIntensifyManager.getImageDrawables(mDrawingRect, mScale);

        int i = 0;
        for (ImageDrawable drawable : drawables) {
            if (drawable.mBitmap.isRecycled()) {
//                if (DEBUG) canvas.drawRect(drawable.mDst, mPaint);
                continue;
            }
            canvas.drawBitmap(drawable.mBitmap, drawable.mSrc, drawable.mDst, mPaint);
            if (DEBUG) {
                canvas.drawRect(drawable.mDst, mPaint);
                canvas.drawText(String.valueOf(++i), drawable.mDst.left + 4, drawable.mDst.top + mTextPaint.getTextSize(), mTextPaint);
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
        mInfo.mScale.set(1f, 0, 0);
        mIntensifyManager.load(path);
    }

    @Override
    public void setImage(File file) {
        mInfo.mScale.set(1f, 0, 0);
        mIntensifyManager.load(file);
    }

    @Override
    public void setImage(InputStream inputStream) {
        mInfo.mScale.set(1f, 0, 0);
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
    public Scale getScale() {
        return mInfo.mScale;
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
//        mInfo.mScale.set(scale, focusX, focusY);
        mScale.set(scale, focusX, focusY);
        Logger.d(TAG, "Scale: scale=" + scale + ", focusX=" + focusX + ", focusY=" + focusY);
        requestInvalidate();
    }

    @Override
    public void addScale(float scale, int focusX, int focusY) {
        mIntensifyManager.transform(mScale, scale, focusX + getScrollX(), focusY + getScrollY());
        requestInvalidate();
    }

    @Override
    public void scroll(float distanceX, float distanceY) {
        Logger.i(TAG, "Scroll: distanceX=%f, distanceY=%f", distanceX, distanceY);
        getDrawingRect(mDrawingRect);
        Float2 damping = mIntensifyManager.damping(mDrawingRect, distanceX, distanceY);
        Logger.i(TAG, "Damping: X=%f, Y=%f", damping.x, damping.y);
        scrollBy(Math.round(damping.x), Math.round(damping.y));
    }

    @Override
    public void scrollBy(int x, int y) {
        super.scrollBy(x, y);
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(x, y);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        } else {
            if (fling) {
                getDrawingRect(mDrawingRect);
                mIntensifyManager.home(mDrawingRect);
                fling = false;
            }
        }
    }

    @Override
    public void fling(float velocityX, float velocityY) {
        Logger.d(TAG, "Fling: velocityX=" + velocityX + ", velocityY=" + velocityY);
        getDrawingRect(mDrawingRect);
        RectF imageArea = mIntensifyManager.getImageArea();
        if (imageArea != null && !imageArea.isEmpty() && !Rectangle.contains(mDrawingRect, imageArea)) {

            Logger.i(TAG, "Fling: begin");

            if (mDrawingRect.left <= imageArea.left && velocityX < 0) {
                velocityX = 0f;
            }

            if (mDrawingRect.right >= imageArea.right && velocityX > 0) {
                velocityX = 0f;
            }

            if (mDrawingRect.top <= imageArea.top && velocityY < 0) {
                velocityY = 0f;
            }

            if (mDrawingRect.bottom >= imageArea.bottom && velocityY > 0) {
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
    public void nextStepScale(int focusX, int focusY) {
        if (mScaleSteps.isEmpty()) return;
        float scale = mInfo.mScale.curScale;
        int step = 0;
        while (scale >= mScaleSteps.get(step)) {
            step = (step + 1) % mScaleSteps.size();
            if (step == 0) break;
        }
        setScale(mScaleSteps.get(step), focusX, focusY);
    }

    @Override
    public void onTouch(float x, float y) {
        Logger.i(TAG, "OnTouch: x=%f, y=%f", x, y);
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
    }

    @Override
    public void home() {
        if (mScroller.isFinished()) {
            getDrawingRect(mDrawingRect);
            mIntensifyManager.home(mDrawingRect);
        }
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
        requestInvalidate();
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
//        scrollTo(Math.round(x), Math.round(y));
        mScale.setScale(scale);
        requestInvalidate();
    }

    @Override
    public void onImageBlockLoadFinished() {
        requestInvalidate();
    }

    @Override
    public void onRequestInvalidate() {
        requestInvalidate();
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
