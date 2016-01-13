package me.kareluo.intensify.image;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.util.SparseArray;
import android.view.animation.DecelerateInterpolator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import me.kareluo.intensify.image.IntensifyImage.Scale;

import static me.kareluo.intensify.image.IntensifyImage.DURATION_ZOOM_HOME;
import static me.kareluo.intensify.image.IntensifyImage.IntensifyInfo;
import static me.kareluo.intensify.image.IntensifyImage.ScaleType;

/**
 * Created by felix on 15/12/17.
 */
public class IntensifyImageManager {
    private static final String TAG = "IntensifyImageManager";

    private DisplayMetrics mDisplayMetrics;

    private Callback mCallback;

//    private volatile IntensifyImage.IntensifyInfo mInfo;

    private IntensifyInfo mInfo;

    private HandlerThread mHandlerThread;

    private IntensifyImageHandler mHandler;

    private Image mImage;

    private float mBaseScale = 1f;

    private ValueAnimator mZoomHomeAnimator;

    private ZoomAnimatorAdapter mZoomAdapter;

    private ValueAnimator mZoomAnimator;

    private ValueAnimator mFlingAnimator;

    private FlingAnimatorAdapter mFlingAdapter;

    private ValueAnimator mAnimation;

    private RectF mImageArea = new RectF();

    private Rect mStartRect = new Rect(), mEndRect = new Rect();

    /**
     * 图片的初始的缩放类型
     */
    private ScaleType mScaleType = ScaleType.FIT_AUTO;

    private State mState = State.NONE;

    // Fling摩擦系数
    private float FRICTION_RATIO = 6f;

    private static final int BLOCK_SIZE = 200;

    private static final int MSG_IMAGE_LOAD = 0;
    private static final int MSG_IMAGE_INIT = 1;
    private static final int MSG_IMAGE_BLOCK_LOAD = 2;
    private static final int MSG_IMAGE_HOMING = 3;


    private enum State {
        NONE, LOAD, INIT
    }

    public IntensifyImageManager(DisplayMetrics metrics, IntensifyInfo info, @NonNull Callback callback) {
        mDisplayMetrics = metrics;
        mCallback = callback;
        mInfo = info;
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new IntensifyImageHandler(mHandlerThread.getLooper());
        Logger.i(TAG, "Constructor: " + mDisplayMetrics);

        mZoomAdapter = new ZoomAnimatorAdapter();
        mZoomHomeAnimator = ValueAnimator.ofFloat(0, 1f);
        mZoomHomeAnimator.setDuration(IntensifyImage.DURATION_ZOOM_HOME);
        mZoomHomeAnimator.setInterpolator(new DecelerateInterpolator());
        mZoomHomeAnimator.addUpdateListener(mZoomAdapter);

        mZoomAnimator = ValueAnimator.ofFloat(0, 1f);
        mZoomAnimator.setDuration(IntensifyImage.DURATION_ZOOM);
        mZoomAnimator.setInterpolator(new DecelerateInterpolator());
        mZoomAnimator.addUpdateListener(mZoomAdapter);

        mFlingAdapter = new FlingAnimatorAdapter();
        mFlingAnimator = ValueAnimator.ofFloat(0, 1f);
        mFlingAnimator.setDuration(IntensifyImage.DURATION_FLING);
        mFlingAnimator.setInterpolator(new DecelerateInterpolator());
        mFlingAnimator.addUpdateListener(mFlingAdapter);
        mFlingAnimator.addListener(mFlingAdapter);
    }

    public void onAttached() {
        if (mImage != null && mImage.mImageDecoder != null) {
            //
        }
    }

    public boolean isAttached() {
        return mHandlerThread != null;
    }

    public void onDetached() {
        mHandlerThread.quit();
        mHandlerThread = null;
        mHandler = null;
        release();
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
        release();
        mImage = new Image(decoder);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendEmptyMessage(MSG_IMAGE_LOAD);
        mHandler.sendEmptyMessage(MSG_IMAGE_INIT);
    }

    private synchronized void load() {
        try {
            mImage.mImageRegion = mImage.mImageDecoder.newRegionDecoder();
            mImage.mImageWidth = mImage.mImageRegion.getWidth();
            mImage.mImageHeight = mImage.mImageRegion.getHeight();
            mImage.mImageOriginalRect = new Rect(0, 0, mImage.mImageWidth, mImage.mImageHeight);
            mImageArea = new RectF(mImage.mImageOriginalRect);
            mState = State.LOAD;
            mCallback.onImageLoadFinished(mImage.mImageWidth, mImage.mImageHeight);
        } catch (IOException e) {
            Logger.w(TAG, e);
            mCallback.onError("LOAD ERROR", e);
        }
    }

    private synchronized void initialize() {
        mImageArea.set(mImage.mImageOriginalRect);
        mInfo.mImageRect = new Rect(0, 0, mImage.mImageWidth, mImage.mImageHeight);

        center(mInfo.mImageRect, mInfo.mVisibleRect);


        mInfo.mScale.setScale(1f);

        int sampleSize = 1;
        // 获取一个合适的inSampleSize
        if (mInfo.mVisibleRect.isEmpty()) {
            sampleSize = getSampleSize(1f * mImage.mImageWidth / mDisplayMetrics.widthPixels
                    * mImage.mImageHeight / mDisplayMetrics.heightPixels);
        } else {
            sampleSize = getSampleSize(
                    Math.max(1f * mImage.mImageWidth / mInfo.mVisibleRect.width(),
                            1f * mImage.mImageHeight / mInfo.mVisibleRect.height()));
        }

        mImage.mImageCacheScale = sampleSize;
        Options options = new Options();
        options.inSampleSize = sampleSize;
        mImage.mImageCache = mImage.mImageRegion.decodeRegion(mImage.mImageOriginalRect, options);

        Logger.i(TAG, "Initialize: Width=%d, Height=%d, SampleSize=%d", mImage.mImageWidth,
                mImage.mImageHeight, sampleSize);

        initScaleType();
    }

    private void initScaleType() {
        Rect visibleRect = mInfo.mVisibleRect;
        // 是否为垂直型图片
        boolean vertical = Double.compare(mImage.mImageHeight * visibleRect.width(),
                mImage.mImageWidth * visibleRect.height()) > 0;
        Logger.i(TAG, "InitScaleType: IsVerticalType=" + vertical);
        mInfo.mImageRect.set(mImage.mImageOriginalRect);
        switch (mScaleType) {
            case FIT_CENTER:
                Logger.i(TAG, "InitScaleType: FIT_CENTER");
                if (vertical) {
                    // 长图
                    mBaseScale = 1f * visibleRect.height() / mImage.mImageHeight;
                    mInfo.mScale.setScale(mBaseScale);
                    center(mInfo.mImageRect, visibleRect.width(), visibleRect.height());
                    mInfo.mScale.focus.set(visibleRect.centerX(), visibleRect.centerY());
                } else {
                    // 宽图
                    mBaseScale = 1f * visibleRect.width() / mImage.mImageWidth;
                    mInfo.mScale.setScale(mBaseScale);
                    center(mInfo.mImageRect, visibleRect.width(), visibleRect.height());
                    mInfo.mScale.focus.set(visibleRect.centerX(), visibleRect.centerY());
                }
                break;
            case FIT_AUTO:
                Logger.i(TAG, "InitScaleType: FIT_AUTO");
                if (vertical) {
                    mBaseScale = 1f * visibleRect.height() / mImage.mImageHeight;
                    mInfo.mScale.setScale(mBaseScale);
                    mInfo.mImageRect.offsetTo(mInfo.mImageRect.left, 0);
                    centerHorizontal(mInfo.mImageRect, visibleRect.width());
                    mInfo.mScale.focus.set(visibleRect.centerX(), 0);
                } else {
                    mBaseScale = 1f * visibleRect.width() / mImage.mImageWidth;
                    mInfo.mScale.setScale(mBaseScale);
                    center(mInfo.mImageRect, visibleRect);
                    mInfo.mScale.focus.set(visibleRect.centerX(), visibleRect.centerY());
                }
                break;
        }
        Logger.i(TAG, "InitScaleType: ImageRect=" + mInfo.mImageRect
                + ", FocusPoint=" + mInfo.mScale.focus + ", BaseScale=" + mBaseScale);
    }

    private boolean loadImageBlock(BlockInfo info) {
        ImageCache imageCache = mImage.mCurrentImageCache;
        if (info == null || imageCache == null || imageCache.mScale != info.inSampleSize) {
            return false;
        }
        try {
            if (!imageCache.mCaches.containsKey(info.position)) {
                Options options = new Options();
                options.inSampleSize = info.inSampleSize;
                Rect rect = blockRect(info.position.x, info.position.y, BLOCK_SIZE);
//                boolean intersect = rect.intersect(mImage.mImageRect);
                Bitmap bitmap = mImage.mImageRegion.decodeRegion(rect, options);
                if (bitmap != null) {
                    imageCache.mCaches.put(info.position, bitmap);
                    Logger.i(TAG, "Block: " + rect + ", SampleSize=" + info.inSampleSize);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
        return false;
    }

    /**
     * 按照ScaleType类型的放大倍数
     *
     * @return
     */
    public float getBaseScale() {
        return mBaseScale;
    }

    /**
     * 请求图片归位
     */
    public void home() {
        mHandler.sendEmptyMessage(MSG_IMAGE_HOMING);
    }

    /**
     * 图片归位
     *
     * @return
     */
    private boolean homing() {
        // 不需要归位
        if (mInfo.mImageRect.contains(mInfo.mVisibleRect)) return false;

        Rect ir = mInfo.mImageRect;
        Rect vr = mInfo.mVisibleRect;

        if (ir.height() < vr.height()) {
            centerVertical(ir, vr.height());
        } else {
            if (ir.top > vr.top) {
                offsetVertical(ir, vr.top - ir.top);
            } else if (ir.bottom < vr.bottom) {
                offsetVertical(ir, vr.bottom - ir.bottom);
            }
        }

        if (ir.width() < vr.width()) {
            centerHorizontal(ir, vr.width());
        } else {
            if (ir.left > vr.left) {
                offsetHorizontal(ir, vr.left - ir.left);
            } else if (ir.right < vr.right) {
                offsetHorizontal(ir, vr.right - ir.right);
            }
        }

        return true;
    }

    private void zoomHoming() {
        if (mInfo.mImageRect.contains(mInfo.mVisibleRect)) return;

        final Rect reuseRect = new Rect();
        mStartRect.set(mInfo.mImageRect);
        mEndRect.set(mInfo.mImageRect);
        Rect vr = mInfo.mVisibleRect;

        if (mEndRect.height() < vr.height()) {
            centerVertical(mEndRect, vr.height());
        } else {
            if (mEndRect.top > vr.top) {
                offsetVertical(mEndRect, vr.top - mEndRect.top);
            } else if (mEndRect.bottom < vr.bottom) {
                offsetVertical(mEndRect, vr.bottom - mEndRect.bottom);
            }
        }

        if (mEndRect.width() < vr.width()) {
            centerHorizontal(mEndRect, vr.width());
        } else {
            if (mEndRect.left > vr.left) {
                offsetHorizontal(mEndRect, vr.left - mEndRect.left);
            } else if (mEndRect.right < vr.right) {
                offsetHorizontal(mEndRect, vr.right - mEndRect.right);
            }
        }

        mZoomHomeAnimator.start();
    }

    public static Rect evaluate(float fraction, Rect startValue, Rect endValue, @NonNull Rect reuseRect) {
        int left = startValue.left + (int) ((endValue.left - startValue.left) * fraction);
        int top = startValue.top + (int) ((endValue.top - startValue.top) * fraction);
        int right = startValue.right + (int) ((endValue.right - startValue.right) * fraction);
        int bottom = startValue.bottom + (int) ((endValue.bottom - startValue.bottom) * fraction);
        reuseRect.set(left, top, right, bottom);
        return reuseRect;
    }

    public static Rect evaluate(float fraction, Rect startValue, Rect endValue) {
        int left = startValue.left + (int) ((endValue.left - startValue.left) * fraction);
        int top = startValue.top + (int) ((endValue.top - startValue.top) * fraction);
        int right = startValue.right + (int) ((endValue.right - startValue.right) * fraction);
        int bottom = startValue.bottom + (int) ((endValue.bottom - startValue.bottom) * fraction);
        return new Rect(left, top, right, bottom);
    }

    private static void offsetVertical(Rect rect, int offset) {
        rect.top += offset;
        rect.bottom += offset;
    }

    private static void offsetHorizontal(Rect rect, int offset) {
        rect.left += offset;
        rect.right += offset;
    }

    private static void center(Rect rect, Rect frame) {
        center(rect, frame.width(), frame.height());
    }

    private static void center(Rect rect, int width, int height) {
        int w = rect.width(), h = rect.height();
        rect.left = (width - w) >> 1;
        rect.top = (height - h) >> 1;
        rect.right = rect.left + w;
        rect.bottom = rect.top + h;
    }

    private static void centerVertical(Rect rect, int height) {
        int h = rect.height();
        rect.top = (height - h) >> 1;
        rect.bottom = rect.top + h;
    }

    private static void centerHorizontal(Rect rect, int width) {
        int w = rect.width();
        rect.left = (width - w) >> 1;
        rect.right = rect.left + w;
    }

    public void setScaleType(ScaleType scaleType) {
        mScaleType = scaleType;
        initScaleType();
        requestInvalidate();
    }

    public int getWidth() {
        return mImage != null ? mImage.mImageWidth : 0;
    }

    public int getHeight() {
        return mImage != null ? mImage.mImageHeight : 0;
    }

    public boolean isImageLoaded() {
        return mImage != null && mImage.mImageRegion != null;
    }

    public boolean isImageInit() {
        return mImage.mImageCacheScale != 0;
    }

    private void release() {
        if (mImage != null) {
            if (mImage.mImageRegion != null) {
                mImage.mImageRegion.recycle();
                mImage.mImageRegion = null;
            }
            if (mImage.mCurrentImageCache != null) {
                mImage.mCurrentImageCache = null;
            }
            if (mImage.mImageCache != null) {
                mImage.mImageCache.recycle();
                mImage.mImageCache = null;
            }
        }
    }

    private void requestInvalidate() {
        if (mCallback != null) {
            mCallback.onRequestInvalidate();
        }
    }

    public List<ImageDrawable> getImageDrawables(@NonNull Rect drawingRect, @NonNull Scale scale) {
        if (drawingRect.isEmpty() || isNeedPrepare(drawingRect)) return Collections.emptyList();


        return Collections.emptyList();
    }

    public boolean isNeedPrepare(Rect drawingRect) {
        switch (mState) {
            case NONE:
                sendMessage(MSG_IMAGE_LOAD);
                return true;
            case LOAD:
                sendMessage(MSG_IMAGE_INIT, drawingRect);
                return true;
        }
        return false;
    }

    private void sendMessage(int what) {
        if (mHandler != null) {
            mHandler.sendEmptyMessage(what);
        }
    }

    private void sendMessage(int what, Object obj) {
        if (mHandler != null) {
            mHandler.obtainMessage(what, obj).sendToTarget();
        }
    }

    public List<ImageDrawable> getImageDrawables() {
        if (mImage == null || mImage.mImageDecoder == null) {
            return new ArrayList<>(0);
        }

        boolean intersect = false;

        List<ImageDrawable> drawables = new ArrayList<>();

        int imageWidth = mImage.mImageWidth;
        int imageHeight = mImage.mImageHeight;

        int sampleSize = getSampleSize(1f / mInfo.mScale.curScale);
        float preScale = mInfo.mScale.preScale;
        float curScale = mInfo.mScale.curScale;
        int cacheSimpleSize = mImage.mImageCacheScale;

        if (mImage.mImageCache == null) {
            mHandler.sendEmptyMessage(MSG_IMAGE_INIT);
            return drawables;
        } else {
            if (Float.compare(preScale, curScale) != 0) {
                scale(mInfo.mImageRect, curScale / preScale, mInfo.mScale.focus);
                mInfo.mScale.preScale = curScale;
            }
            drawables.add(new ImageDrawable(mImage.mImageCache,
                    bitmapRect(mImage.mImageCache), mInfo.mImageRect));
        }

        if (sampleSize >= cacheSimpleSize) {
            return drawables;
        }

        int originalBlockSize = BLOCK_SIZE;

        int WIDTH = imageWidth / originalBlockSize + bitValue(imageWidth % originalBlockSize);
        int HEIGHT = imageHeight / originalBlockSize + bitValue(imageHeight % originalBlockSize);

        Log.d(TAG, "块: W:" + WIDTH + "/H:" + HEIGHT);

        Rect imageRect = mInfo.mImageRect;
        Rect visibleRect = mInfo.mVisibleRect;

        Rect showRect = new Rect(visibleRect);
        intersect = showRect.intersect(imageRect);
        showRect.offset(-imageRect.left, -imageRect.top);

        Log.d(TAG, "可视区域:" + showRect);

        float imageBlockSize = originalBlockSize * imageRect.width() * 1f / mImage.mImageWidth;

        Log.d(TAG, "显示块大小:" + imageBlockSize);

        Rect blocks = visualBlocks(showRect, Math.round(imageBlockSize));
        Log.d(TAG, "可视块:" + blocks);

        ImageCache imageCache = mImage.mCurrentImageCache;

        if (imageCache != null && imageCache.mScale != sampleSize) {
            ImageCache cache = mImage.mImageCaches.get(sampleSize);
            if (cache == null) {
                cache = new ImageCache(sampleSize, new HashMap<Point, Bitmap>());
                mImage.mImageCaches.put(sampleSize, cache);
            }
            imageCache = mImage.mCurrentImageCache = cache;
            mHandler.removeMessages(MSG_IMAGE_BLOCK_LOAD);
        }

        if (imageCache == null) {
            mImage.mCurrentImageCache = imageCache =
                    new ImageCache(sampleSize, new HashMap<Point, Bitmap>());
            mImage.mImageCaches.put(sampleSize, imageCache);
        }

        for (int i = blocks.top; i <= blocks.bottom; i++) {
            for (int j = blocks.left; j <= blocks.right; j++) {
                Point position = new Point(j, i);
                if (!imageCache.mCaches.containsKey(position)) {
                    mHandler.obtainMessage(MSG_IMAGE_BLOCK_LOAD,
                            new BlockInfo(position, sampleSize)).sendToTarget();
                } else {
                    Bitmap bitmap = imageCache.mCaches.get(position);
                    drawables.add(new ImageDrawable(bitmap, bitmapRect(bitmap),
                            blockRect(j, i, imageBlockSize, imageRect.left, imageRect.top)));
                }
            }
        }


//        Rect block = new Rect(imageRect.left + blocks.left * imageBlockSize, imageRect.top + blocks.top * imageBlockSize, imageRect.left + blocks.right * imageBlockSize, imageRect.top + blocks.bottom * imageBlockSize);

//        Log.d(TAG, "ImageRect:" + imageRect + " / " + "BlockRect:" + block);

        return drawables;
    }

    public static Rect bitmapRect(Bitmap bitmap) {
        return new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
    }

    public static Rect blockRect(int x, int y, int size) {
        return new Rect(x * size, y * size, (x + 1) * size, (y + 1) * size);
    }

    public static Rect blockRect(int x, int y, float size, int offsetX, int offsetY) {
        Rect result = new Rect();
        new RectF(x * size + offsetX, y * size + offsetY,
                (x + 1) * size + offsetX, (y + 1) * size + offsetY).round(result);
        return result;
    }

    public static Rect visualBlocks(Rect visualRect, int blockSize) {
        return new Rect(
                visualRect.left / blockSize, visualRect.top / blockSize,
                visualRect.right / blockSize, visualRect.bottom / blockSize
        );
    }

    public static int bitValue(int value) {
        return value == 0 ? 0 : 1;
    }

    /**
     * @param rect
     * @param scale
     * @param focus
     */
    public static void scale(Rect rect, float scale, PointF focus) {
        Point offset = new Point(rect.left, rect.top);
        rect.offsetTo(0, 0);
        rect.right = Math.round(rect.right * scale);
        rect.bottom = Math.round(rect.bottom * scale);

        float deltaX = (focus.x - offset.x) * scale;
        float deltaY = (focus.y - offset.y) * scale;

        rect.offset(Math.round(focus.x - deltaX), Math.round(focus.y - deltaY));
    }

    /**
     * 获取bitmap的字节数
     *
     * @param bitmap
     * @return
     */
    private static int getSize(Bitmap bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return bitmap.getAllocationByteCount();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            return bitmap.getByteCount();
        }
        return bitmap.getRowBytes() * bitmap.getHeight();
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

    /**
     * 查看{@link #getSampleSize(int)}
     *
     * @param size
     * @return
     */
    public static int getSampleSize(float size) {
        return getSampleSize(Math.round(size));
    }

    public ScaleType getScaleType() {
        return mScaleType;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    private class ZoomAnimatorAdapter extends AnimatorListenerAdapter
            implements AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            Float value = (Float) animation.getAnimatedValue();
            evaluate(value, mStartRect, mEndRect, mInfo.mImageRect);
            requestInvalidate();
        }
    }

    private class FlingAnimatorAdapter extends AnimatorListenerAdapter
            implements AnimatorUpdateListener {

        @Override
        public void onAnimationEnd(Animator animation) {
            Logger.d(TAG, "ANIMATION END");
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            Logger.d(TAG, "ANIMATION CANCEL");
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            Logger.d(TAG, "ANIMATION REPEAT");
        }

        @Override
        public void onAnimationStart(Animator animation) {
            Logger.d(TAG, "ANIMATION START");
        }

        @Override
        public void onAnimationPause(Animator animation) {
            Logger.d(TAG, "ANIMATION PAUSE");
        }

        @Override
        public void onAnimationResume(Animator animation) {
            Logger.d(TAG, "ANIMATION RESUME");
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            Logger.d(TAG, "ANIMATION UPDATE");
        }
    }

    private static class Image {
        private Image(ImageDecoder decoder) {
            mImageDecoder = decoder;
        }

        volatile ImageDecoder mImageDecoder;
        volatile BitmapRegionDecoder mImageRegion;

        volatile SparseArray<ImageCache> mImageCaches = new SparseArray<>();

        volatile ImageCache mCurrentImageCache;
        volatile Bitmap mImageCache;
        volatile ImageDrawable mCacheDrawable;
        volatile int mImageCacheScale;

        volatile Rect mImageOriginalRect;
        volatile int mImageWidth;
        volatile int mImageHeight;
    }

    private static class ImageCache {
        int mScale;
        HashMap<Point, Bitmap> mCaches;

        public ImageCache(int scale, HashMap<Point, Bitmap> caches) {
            this.mScale = scale;
            this.mCaches = caches;
        }
    }

    private static class BlockInfo {
        Point position;
        int inSampleSize;

        public BlockInfo(Point position, int inSampleSize) {
            this.position = position;
            this.inSampleSize = inSampleSize;
        }
    }

    public static class ImageDrawable {
        Bitmap mBitmap;
        Rect mSrc;
        //        Rect mImageSrc;
        Rect mDst;

        public ImageDrawable(Bitmap bitmap, Rect src, Rect dst) {
            this.mBitmap = bitmap;
            this.mSrc = src;
//            this.mImageSrc = imageSrc;
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
        void onImageLoadFinished(int width, int height);

        void omImageInitFinished(float scale);

        void onImageBlockLoadFinished();

        void onRequestInvalidate();

        void onError(String message, Exception e);
    }

    private class IntensifyImageHandler extends Handler {

        public IntensifyImageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_IMAGE_LOAD:
                    Logger.d(TAG, "MSG_IMAGE_LOAD");
                    load();
                    break;
                case MSG_IMAGE_INIT:
                    Logger.d(TAG, "MSG_IMAGE_INIT");
                    initialize();
                    requestInvalidate();
                    break;
                case MSG_IMAGE_BLOCK_LOAD:
                    Logger.d(TAG, "MSG_IMAGE_BLOCK_LOAD");
                    if (msg.obj instanceof BlockInfo) {
                        BlockInfo info = (BlockInfo) msg.obj;
                        if (loadImageBlock(info)) {
                            requestInvalidate();
                        }
                    }
                    break;
                case MSG_IMAGE_HOMING:
                    Logger.d(TAG, "MSG_IMAGE_HOMING");
//                    if (homing()) requestInvalidate();
                    zoomHoming();
                    break;
            }
        }
    }
}
