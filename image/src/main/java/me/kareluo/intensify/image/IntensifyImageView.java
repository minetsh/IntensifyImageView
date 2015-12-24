package me.kareluo.intensify.image;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

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

//    private volatile Scale mScale = new Scale(1f, 0, 0);

    private volatile Rect mDrawRect = new Rect();

    private volatile long mPreInvalidateTime = 0l;

    private volatile Runnable mRunnable;

    private IntensifyImageManager mIntensifyManager;

    private IntensifyInfo mInfo = new IntensifyInfo();

    private Paint mPaint;

    private List<Float> mScaleSteps = new ArrayList<>();

    private IntensifyViewAttacher<IntensifyImageView> mAttacher;

    private float mMinimumScale = 0.01f;

    private float mMaximumScale = 1000f;

    // Fling摩擦系数
    private float FRICTION_RATIO = 6f;

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
        mAttacher = new IntensifyViewAttacher<>(this);
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
        if (mRunnable != null) return;
        long duration = SystemClock.uptimeMillis() - mPreInvalidateTime;
        if (duration < LOOP_FRAME_MILLIS) {
            postDelayed(mRunnable = new Runnable() {
                @Override
                public void run() {
                    mRunnable = null;
                    mPreInvalidateTime = SystemClock.uptimeMillis();
                    invalidate(getVisibleRect());
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
        if (!mIntensifyManager.isImageLoaded() || !getLocalVisibleRect(mInfo.mVisibleRect)) {
            // 如果没有加载图片返回
            return;
        }
        int save = canvas.save();
        List<ImageDrawable> drawables = mIntensifyManager.getImageDrawables();
        for (ImageDrawable drawable : drawables) {
            canvas.drawBitmap(drawable.mBitmap, drawable.mSrc, drawable.mDst, mPaint);
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
        mInfo.mScale.set(scale, focusX, focusY);
        requestInvalidate();
    }

    @Override
    public void addScale(float scale, int focusX, int focusY) {
        setScale(mInfo.mScale.curScale * scale, focusX, focusY);
    }

    @Override
    public void scroll(float distanceX, float distanceY) {
        mInfo.mImageRect.offset(-Math.round(distanceX), -Math.round(distanceY));
        requestInvalidate();
    }

    @Override
    public void fling(final float velocityX, final float velocityY) {
        if (Math.abs(velocityY) > Math.abs(velocityX)) {
            ValueAnimator animator = ValueAnimator.ofFloat(velocityY / FRICTION_RATIO, 0);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.setDuration(FLING_DURATION);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                float offset = velocityY / FRICTION_RATIO;

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    Float value = (Float) animation.getAnimatedValue();
                    if (value != null) {
                        scroll(0, value - offset);
                        offset = value;
                    }
                }
            });
            animator.start();
        } else {
            ValueAnimator animator = ValueAnimator.ofFloat(velocityX / FRICTION_RATIO, 0);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.setDuration(FLING_DURATION);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                float offset = velocityX / FRICTION_RATIO;

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    Float value = (Float) animation.getAnimatedValue();
                    if (value != null) {
                        scroll(value - offset, 0);
                        offset = value;
                    }
                }
            });
            animator.start();
        }
    }

    @Override
    public void nextStepScale(int focusX, int focusY) {
        float scale = mInfo.mScale.curScale;
        int step = 0;
        while (scale >= mScaleSteps.get(step)) {
            step = (step + 1) % mScaleSteps.size();
            if (step == 0) break;
        }
        setScale(mScaleSteps.get(step), focusX, focusY);
    }

    @Override
    public void home() {
        mIntensifyManager.home();
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

    }

    @Override
    public void omImageInitFinished(float scale) {
        mScaleSteps.clear();
        float baseScale = mIntensifyManager.getBaseScale();
        mScaleSteps.add(baseScale);
        mScaleSteps.add(baseScale * 2f);
        mScaleSteps.add(baseScale * 3f);
    }

    @Override
    public void onImageBlockLoadFinished() {
        requestInvalidate();
    }

    @Override
    public void onRequestInvalidate() {
        requestInvalidate();
    }

    public interface IntensifyImageLoadListener {

        void onImageLoadBegin();

        void onImageLoadEnd();

        void onImageInitFinished();
    }

    public static class IntensifyImageLoadAdapter implements IntensifyImageLoadListener {

        @Override
        public void onImageLoadBegin() {

        }

        @Override
        public void onImageLoadEnd() {

        }

        @Override
        public void onImageInitFinished() {

        }
    }
}
