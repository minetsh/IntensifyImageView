package me.kareluo.intensify.image;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.animation.DecelerateInterpolator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static me.kareluo.intensify.image.IntensifyImage.ScaleType;

/**
 * Created by felix on 15/12/17.
 */
class IntensifyImageDelegate {
    private static final String TAG = "IntensifyImageDelegate";

    private Callback mCallback;

    private DisplayMetrics mDisplayMetrics;

    private IntensifyImageHandler mHandler;

    private Image mImage;

    private float mBaseScale = 1f;

    private boolean mNeedReset = true;

    private float mTempScale = 1f;

    private float mMinimumScale = 0f;

    private float mMaximumScale = Float.MAX_VALUE;

    private boolean mAnimateScaleType = false;

    private boolean mIsVertical = true;

    private RectF mImageArea = new RectF();

    private Matrix mMatrix = new Matrix();

    private volatile State mState = State.NONE;

    private ValueAnimator mZoomAnimator;

    private ScaleType mScaleType = ScaleType.FIT_CENTER;

    private RectF mStartRect = new RectF(), mEndRect = new RectF();

    private volatile List<ImageDrawable> mDrawables = new ArrayList<>();

    private static final int[] SCALE_STEP = {1, 3};

    private static final int BLOCK_SIZE = 300;
    private static final int MSG_IMAGE_SRC = 0;
    private static final int MSG_IMAGE_LOAD = 1;
    private static final int MSG_IMAGE_INIT = 2;
    private static final int MSG_IMAGE_SCALE = 3;
    private static final int MSG_IMAGE_DRAW = 4;
    private static final int MSG_IMAGE_RELEASE = 5;
    private static final int MSG_QUIT = 6;

    private enum State {
        NONE, SRC, LOAD, INIT, FREE
    }

    public IntensifyImageDelegate(DisplayMetrics metrics, Callback callback) {
        mDisplayMetrics = metrics;
        mCallback = Utils.requireNonNull(callback);
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler = new IntensifyImageHandler(handlerThread.getLooper());
        mZoomAnimator = ValueAnimator.ofFloat(0, 1f);
        mZoomAnimator.setDuration(IntensifyImage.DURATION_ZOOM);
        mZoomAnimator.setInterpolator(new DecelerateInterpolator());
        mZoomAnimator.addUpdateListener(new ZoomAnimatorAdapter());
    }

    public void onAttached() {

    }

    public void onDetached() {
        mHandler.removeCallbacksAndMessages(null);
        sendMessage(MSG_QUIT);
    }

    public void load(String path) {
        load(new ImagePathDecoder(path));
    }

    public void load(File file) {
        load(new ImageFileDecoder(file));
    }

    public void load(InputStream inputStream) {
        load(new ImageInputStreamDecoder(inputStream));
    }

    public void load(ImageDecoder decoder) {
        mHandler.removeCallbacksAndMessages(null);
        sendMessage(MSG_IMAGE_RELEASE);
        sendMessage(MSG_IMAGE_SRC, decoder);
    }

    //@WorkerThread
    private void prepare(ImageDecoder decoder) {
        mImage = new Image(decoder);
        mImageArea.setEmpty();
        mState = State.SRC;
        load();
    }

    //@WorkerThread
    private void load() {
        mImage.mImageWidth = mImage.mImageRegion.getWidth();
        mImage.mImageHeight = mImage.mImageRegion.getHeight();
        mState = State.LOAD;
    }

    //@WorkerThread
    private void initialize(Rect drawingRect) {
        if (Utils.isEmpty(drawingRect)) return;

        int sampleSize = getSampleSize(
                Math.max(1f * mImage.mImageWidth / drawingRect.width(),
                        1f * mImage.mImageHeight / drawingRect.height()));

        mImage.mImageSampleSize = sampleSize;
        Options options = new Options();
        options.inSampleSize = sampleSize;
        mImage.mImageCache = mImage.mImageRegion.decodeRegion(
                new Rect(0, 0, mImage.mImageWidth, mImage.mImageHeight), options);
        mState = State.INIT;
        initScaleType(drawingRect);
    }

    //@WorkerThread
    private void initScaleType(Rect drawingRect) {
        RectF imageArea = new RectF(0, 0, mImage.mImageWidth, mImage.mImageHeight);

        // 是否为垂直型图片
        mIsVertical = Double.compare(mImage.mImageHeight * drawingRect.width(),
                mImage.mImageWidth * drawingRect.height()) > 0;

        switch (mScaleType) {
            case NONE:
                mBaseScale = Utils.range(1f, mMinimumScale, mMaximumScale);
                if (mNeedReset) mTempScale = mBaseScale;
                mMatrix.setScale(mTempScale, mTempScale);
                mMatrix.mapRect(imageArea);
                imageArea.offsetTo(drawingRect.left, drawingRect.top);
                break;

            case FIT_CENTER:
                mBaseScale = mIsVertical ? (1f * drawingRect.height() / mImage.mImageHeight)
                        : (1f * drawingRect.width() / mImage.mImageWidth);
                mBaseScale = Utils.range(mBaseScale, mMinimumScale, mMaximumScale);
                if (mNeedReset) mTempScale = mBaseScale;
                mMatrix.setScale(mTempScale, mTempScale);
                mMatrix.mapRect(imageArea);
                Utils.center(imageArea, drawingRect);
                break;

            case FIT_AUTO:
                mBaseScale = 1f * drawingRect.width() / mImage.mImageWidth;
                mBaseScale = Utils.range(mBaseScale, mMinimumScale, mMaximumScale);
                if (mNeedReset) mTempScale = mBaseScale;
                mMatrix.setScale(mTempScale, mTempScale);
                mMatrix.mapRect(imageArea);
                Utils.centerHorizontal(imageArea, drawingRect);
                if (mIsVertical) {
                    imageArea.offsetTo(imageArea.left, drawingRect.top);
                } else {
                    Utils.centerVertical(imageArea, drawingRect);
                }
                break;

            case CENTER:
                mBaseScale = mIsVertical ? (1f * drawingRect.width() / mImage.mImageWidth)
                        : (1f * drawingRect.height() / mImage.mImageHeight);

                mBaseScale = Utils.range(mBaseScale, mMinimumScale, mMaximumScale);
                if (mNeedReset) mTempScale = mBaseScale;
                mMatrix.setScale(mTempScale, mTempScale);
                mMatrix.mapRect(imageArea);
                Utils.center(imageArea, drawingRect);
                break;

            case CENTER_INSIDE:
                mBaseScale = Math.min(mIsVertical ? (1f * drawingRect.height() / mImage.mImageHeight)
                        : (1f * drawingRect.width() / mImage.mImageWidth), 1f);
                mBaseScale = Utils.range(mBaseScale, mMinimumScale, mMaximumScale);
                if (mNeedReset) mTempScale = mBaseScale;
                mMatrix.setScale(mTempScale, mTempScale);
                mMatrix.mapRect(imageArea);
                Utils.center(imageArea, drawingRect);
                break;
        }
        Logger.d(TAG, "DrawingRect=" + drawingRect + "/ImageArea=" + imageArea);
        if (!mAnimateScaleType || mImageArea.isEmpty() || mImageArea.equals(imageArea)) {
            mImageArea.set(imageArea);
        } else {
            zoomTo(imageArea);
        }
        mNeedReset = true;
        mState = State.FREE;
    }

    //@WorkerThread
    private void prepareDraw(Rect rect) {
        float curScale = getScale();
        int sampleSize = getSampleSize(1f / curScale);
        Pair<RectF, Rect> newState = Pair.create(new RectF(mImageArea), new Rect(rect));

        if (mImage.mImageSampleSize > sampleSize) {
            RectF drawingRect = new RectF(rect);

            if (drawingRect.intersect(mImageArea)) {
                drawingRect.offset(-mImageArea.left, -mImageArea.top);
            }

            float blockSize = BLOCK_SIZE * curScale * sampleSize;
            Rect blocks = Utils.blocks(drawingRect, blockSize);

            List<ImageDrawable> drawables = new ArrayList<>();
            int roundLeft = Math.round(mImageArea.left);
            int roundTop = Math.round(mImageArea.top);
            IntensifyImageCache.ImageCache imageCache = mImage.mImageCaches.get(sampleSize);
            if (imageCache != null) {
                for (int i = blocks.top; i <= blocks.bottom; i++) {
                    for (int j = blocks.left; j <= blocks.right; j++) {
                        Bitmap bitmap = imageCache.createGet(new Point(j, i));
                        if (bitmap == null) continue;
                        Rect src = bitmapRect(bitmap);
                        Rect dst = Utils.blockRect(j, i, blockSize, roundLeft, roundTop);
                        if (src.bottom * sampleSize != BLOCK_SIZE
                                || src.right * sampleSize != BLOCK_SIZE) {

                            dst.set(src.left + dst.left, src.top + dst.top,
                                    Math.round(src.right * sampleSize * curScale) + dst.left,
                                    Math.round(src.bottom * sampleSize * curScale) + dst.top);
                        }
                        drawables.add(new ImageDrawable(bitmap, src, dst));
                    }
                }
            }

            mDrawables.clear();
            if (Utils.equals(newState, Pair.create(new RectF(mImageArea), new Rect(rect)))) {
                mDrawables.addAll(drawables);
            }
        } else mDrawables.clear();

        mImage.mCurrentState = Pair.create(new RectF(mImageArea), new Rect(rect));
    }

    //@WorkerThread
    private void release() {
        mZoomAnimator.cancel();
        if (mImage != null) {
            mImage.release();
            mImage = null;
        }
        mState = State.NONE;
    }

    /**
     * 受限制于minimumScale和maximumScale
     *
     * @param scale 缩放值
     */
    public void setScale(float scale) {
        if (scale < 0.0f) return;
        mTempScale = scale;
        mNeedReset = false;
        if (mState.ordinal() > State.INIT.ordinal()) {
            mState = State.INIT;
            requestInvalidate();
            requestAwakenScrollBars();
        }
    }

    public float getScale() {
        return 1f * mImageArea.width() / mImage.mImageWidth;
    }

    public float getBaseScale() {
        return mBaseScale;
    }

    public float getNextStepScale(Rect drawingRect) {
        if (Utils.isEmpty(drawingRect)) return mBaseScale / getScale();

        float v = mIsVertical ? mImageArea.width() / drawingRect.width() :
                mImageArea.height() / drawingRect.height();

        // + 0.1 避免.99999型误差
        int index = Math.abs(Arrays.binarySearch(
                SCALE_STEP, (int) Math.round(Math.floor(v + 0.1))) + 1);

        if (index >= SCALE_STEP.length) {
            return mBaseScale / getScale();
        }
        return SCALE_STEP[index % SCALE_STEP.length] / v;
    }

    /**
     * 设置最小缩放值
     *
     * @param minimumScale 最小缩放值
     */
    public void setMinimumScale(float minimumScale) {
        if (minimumScale <= mMaximumScale) {
            mMinimumScale = minimumScale;
            if (mState.ordinal() > State.INIT.ordinal()) {
                mState = State.INIT;
                requestInvalidate();
                requestAwakenScrollBars();
            }
        }
    }

    /**
     * 设置最大缩放值
     *
     * @param maximumScale 最大缩放值
     */
    public void setMaximumScale(float maximumScale) {
        if (maximumScale >= mMinimumScale) {
            mMaximumScale = maximumScale;
            if (mState.ordinal() > State.INIT.ordinal()) {
                mState = State.INIT;
                requestInvalidate();
                requestAwakenScrollBars();
            }
        }
    }

    public float getMinimumScale() {
        return mMinimumScale;
    }

    public float getMaximumScale() {
        return mMaximumScale;
    }

    /**
     * 请求图片归位
     */
    public void zoomHoming(Rect drawingRect) {
        if (Utils.contains(mImageArea, drawingRect)) return;
        mZoomAnimator.cancel();
        mStartRect.set(mImageArea);
        mEndRect.set(mImageArea);
        Utils.home(mEndRect, drawingRect);
        mZoomAnimator.start();
    }

    public void setScaleType(ScaleType scaleType) {
        mScaleType = scaleType;
        if (mState.ordinal() >= State.INIT.ordinal()) {
            mState = State.INIT;
            mImage.mCurrentState = null;
            requestInvalidate();
        }
    }

    /**
     * 设置ScaleType动画过渡
     *
     * @param animate true 动画过渡，false 无过渡动画
     */
    public void setAnimateScaleType(boolean animate) {
        mAnimateScaleType = animate;
    }

    public boolean isAnimateScaleType() {
        return mAnimateScaleType;
    }

    /**
     * 图像原始宽度
     *
     * @return 图像原始宽度
     */
    public int getWidth() {
        return mImage != null ? mImage.mImageWidth : 0;
    }

    /**
     * 图像原始高度
     *
     * @return 图像原始高度
     */
    public int getHeight() {
        return mImage != null ? mImage.mImageHeight : 0;
    }

    /**
     * 图像宽度
     *
     * @return 图像宽度
     */
    public int getImageWidth() {
        return Math.round(mImageArea.width());
    }

    /**
     * 图像高度
     *
     * @return 图像高度
     */
    public int getImageHeight() {
        return Math.round(mImageArea.height());
    }

    /**
     * 返回显示区域相对图像区域的X坐标偏移
     *
     * @param scrollX 滚动X坐标
     * @return 偏移值
     */
    public int getHorizontalOffset(int scrollX) {
        return Math.round(scrollX - mImageArea.left);
    }

    /**
     * 返回显示区域相对图像区域的Y坐标偏移
     *
     * @param scrollY 滚动Y坐标
     * @return 偏移值
     */
    public int getVerticalOffset(int scrollY) {
        return Math.round(scrollY - mImageArea.top);
    }

    public Point damping(Rect screen, float distanceX, float distanceY) {
        float dx = distanceX, dy = distanceY;

        if (dx < 0) {
            if (screen.left <= Math.round(mImageArea.left)) dx = 0;
            else if (screen.left + dx < mImageArea.left) {
                dx = mImageArea.left - screen.left;
            }
        } else {
            if (screen.right >= Math.round(mImageArea.right)) dx = 0;
            else if (screen.right + dx > mImageArea.right) {
                dx = mImageArea.right - screen.right;
            }
        }

        if (dy < 0) {
            if (screen.top <= Math.round(mImageArea.top)) dy = 0;
            else if (screen.top + dy < mImageArea.top) {
                dy = mImageArea.top - screen.top;
            }
        } else {
            if (screen.bottom >= Math.round(mImageArea.bottom)) dy = 0;
            else if (screen.bottom + dy > mImageArea.bottom) {
                dy = mImageArea.bottom - screen.bottom;
            }
        }

        if (Math.abs(distanceX) > Math.abs(distanceY)) {
            if (Float.compare(dx, 0f) == 0) {
                dy = 0f;
            }
        } else if (Float.compare(dy, 0f) == 0) {
            dx = 0f;
        }

        return new Point(Math.round(dx), Math.round(dy));
    }

    public void scale(float scale, float focusX, float focusY) {
        if (scale == 1.0f) return;
        float curScale = getScale();
        float preScale = curScale * scale;
        if (!Utils.inRange(preScale, mMinimumScale, mMaximumScale)) {
            scale = Utils.range(preScale, mMinimumScale, mMaximumScale) / curScale;
        }
        mMatrix.setScale(scale, scale, focusX, focusY);
        mMatrix.mapRect(mImageArea);
        requestScaleChange();
    }

    public void zoomScale(Rect drawingRect, float scale, float focusX, float focusY) {
        if (mState.ordinal() < State.FREE.ordinal() || Utils.isEmpty(drawingRect)) return;
        mZoomAnimator.cancel();
        mStartRect.set(mImageArea);

        mMatrix.setScale(scale, scale, focusX, focusY);
        mMatrix.mapRect(mImageArea);

        mEndRect.set(mImageArea);
        if (!Utils.contains(mImageArea, drawingRect)) {
            Utils.home(mEndRect, drawingRect);
        }
        Logger.d(TAG, "Start=" + mStartRect + "/End=" + mEndRect);
        mZoomAnimator.start();
    }

    public void zoomTo(RectF dst) {
        mZoomAnimator.cancel();
        mStartRect.set(mImageArea);
        mEndRect.set(dst);
        mZoomAnimator.start();
    }

    private void requestInvalidate() {
        mCallback.onRequestInvalidate();
    }

    private void requestAwakenScrollBars() {
        mCallback.onRequestAwakenScrollBars();
    }

    private void requestScaleChange() {
        mCallback.onScaleChange(getScale());
    }

    public List<ImageDrawable> obtainImageDrawables(Rect drawingRect) {
        if (Utils.isEmpty(drawingRect) || isNeedPrepare(drawingRect)) {
            return Collections.emptyList();
        }

        ArrayList<ImageDrawable> drawables = obtainBaseDrawables();
        drawables.addAll(mDrawables);
        if (!Utils.equals(mImage.mCurrentState, Pair.create(mImageArea, drawingRect))) {
            mHandler.removeMessages(MSG_IMAGE_DRAW);
            sendMessage(MSG_IMAGE_DRAW, drawingRect);
        }

        return drawables;
    }

    public ArrayList<ImageDrawable> obtainBaseDrawables() {
        ArrayList<ImageDrawable> drawables = new ArrayList<>();
        drawables.add(new ImageDrawable(mImage.mImageCache,
                bitmapRect(mImage.mImageCache), Utils.round(mImageArea)));
        return drawables;
    }

    /**
     * 判断是否需要更新状态
     *
     * @param drawingRect 绘制区域
     * @return true 需要准备
     */
    public boolean isNeedPrepare(Rect drawingRect) {
        mHandler.removeCallbacksAndMessages(null);
        switch (mState) {
            case NONE:
                return true;
            case SRC:
                sendMessage(MSG_IMAGE_LOAD);
                return true;
            case LOAD:
                sendMessage(MSG_IMAGE_INIT, drawingRect);
                return true;
            case INIT:
                sendMessage(MSG_IMAGE_SCALE, drawingRect);
                return mImageArea.isEmpty();
        }
        return false;
    }

    private void sendMessage(int what) {
        mHandler.sendEmptyMessage(what);
    }

    private void sendMessage(int what, Object obj) {
        mHandler.obtainMessage(what, obj).sendToTarget();
    }

    private void sendMessage(int what, int arg1, int arg2, Object obj) {
        mHandler.obtainMessage(what, arg1, arg2, obj).sendToTarget();
    }

    public RectF getImageArea() {
        return mImageArea;
    }

    public static Rect bitmapRect(Bitmap bitmap) {
        return new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
    }

    public static int getSampleSize(float size) {
        return Utils.getSampleSize(Math.round(size));
    }

    public ScaleType getScaleType() {
        return mScaleType;
    }

    private class ZoomAnimatorAdapter extends AnimatorListenerAdapter
            implements AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            Float value = (Float) animation.getAnimatedValue();
            Utils.evaluate(value, mStartRect, mEndRect, mImageArea);
            requestScaleChange();
            requestInvalidate();
            requestAwakenScrollBars();
            Logger.d(TAG, "Anim Update.");
        }
    }

    private class Image {

        BitmapRegionDecoder mImageRegion;

        int mImageSampleSize;
        Bitmap mImageCache;

        int mImageWidth;
        int mImageHeight;

        IntensifyImageCache mImageCaches;

        volatile Pair<RectF, Rect> mCurrentState;

        private Image(ImageDecoder decoder) {
            try {
                mImageRegion = decoder.newRegionDecoder();
            } catch (IOException e) {
                throw new RuntimeException("无法访问图片");
            }

            mImageCaches = new IntensifyImageCache(5,
                    mDisplayMetrics.widthPixels * mDisplayMetrics.heightPixels << 4,
                    BLOCK_SIZE, mImageRegion);
        }

        public void release() {
            mImageRegion.recycle();
            if (mImageCache != null && !mImageCache.isRecycled()) {
                mImageCache.recycle();
            }
            mImage.mImageCaches.evictAll();
            mCurrentState = null;
        }
    }

    public static class ImageDrawable {
        Bitmap mBitmap;
        Rect mSrc;
        Rect mDst;

        public ImageDrawable(Bitmap bitmap, Rect src, Rect dst) {
            this.mBitmap = bitmap;
            this.mSrc = src;
            this.mDst = dst;
        }
    }

    public static class ImagePathDecoder implements ImageDecoder {
        private String mPath;

        public ImagePathDecoder(String path) {
            mPath = path;
        }

        @Override
        public BitmapRegionDecoder newRegionDecoder() throws IOException {
            return BitmapRegionDecoder.newInstance(mPath, false);
        }
    }

    public static class ImageFileDecoder implements ImageDecoder {
        private File mFile;

        public ImageFileDecoder(File file) {
            mFile = file;
        }

        @Override
        public BitmapRegionDecoder newRegionDecoder() throws IOException {
            return BitmapRegionDecoder.newInstance(mFile.getAbsolutePath(), false);
        }
    }

    public static class ImageInputStreamDecoder implements ImageDecoder {
        private InputStream mInputStream;

        public ImageInputStreamDecoder(InputStream inputStream) {
            mInputStream = inputStream;
        }

        @Override
        public BitmapRegionDecoder newRegionDecoder() throws IOException {
            return BitmapRegionDecoder.newInstance(mInputStream, false);
        }
    }

    public interface ImageDecoder {
        BitmapRegionDecoder newRegionDecoder() throws IOException;
    }

    public interface Callback {
        void onRequestInvalidate();

        boolean onRequestAwakenScrollBars();

        void onScaleChange(float scale);
    }

    private class IntensifyImageHandler extends Handler {

        public IntensifyImageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_IMAGE_DRAW:
                    prepareDraw((Rect) msg.obj);
                    requestInvalidate();
                    break;

                case MSG_IMAGE_SCALE:
                    initScaleType((Rect) msg.obj);
                    requestInvalidate();
                    requestAwakenScrollBars();
                    break;

                case MSG_IMAGE_SRC:
                    prepare((ImageDecoder) msg.obj);
                    requestInvalidate();
                    break;

                case MSG_IMAGE_LOAD:
                    load();
                    requestInvalidate();
                    break;

                case MSG_IMAGE_INIT:
                    initialize((Rect) msg.obj);
                    requestInvalidate();
                    break;

                case MSG_IMAGE_RELEASE:
                    release();
                    break;

                case MSG_QUIT:
                    release();
                    try {
                        getLooper().quit();
                    } catch (Throwable throwable) {
                        Logger.w(TAG, throwable);
                    }
                    break;
            }
        }
    }
}
