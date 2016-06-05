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
import android.util.Log;
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

    private DisplayMetrics mDisplayMetrics;

    private Callback mCallback;

    private HandlerThread mHandlerThread;

    private IntensifyImageHandler mHandler;

    private Image mImage;

    private float mBaseScale = 1f;

    private float mDefaultMinScale = 0.1f;

    private float mDefaultMaxScale = 10f;

    private ValueAnimator mZoomAnimator;

    private boolean mIsVertical = true;

    private RectF mImageArea = new RectF();

    private RectF mStartRect = new RectF(), mEndRect = new RectF();

    private Matrix mMatrix = new Matrix();

    private volatile List<ImageDrawable> mDrawables = new ArrayList<>();

    private ScaleType mScaleType = ScaleType.FIT_CENTER;

    private State mState = State.NONE;

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
        mCallback = callback;
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new IntensifyImageHandler(mHandlerThread.getLooper());
        mZoomAnimator = ValueAnimator.ofFloat(0, 1f);
        mZoomAnimator.setDuration(IntensifyImage.DURATION_ZOOM);
        mZoomAnimator.setInterpolator(new DecelerateInterpolator());
        mZoomAnimator.addUpdateListener(new ZoomAnimatorAdapter());
    }

    public void onAttached() {

    }

    public boolean isAttached() {
        return mHandlerThread != null;
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
        mState = State.SRC;
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
        mImageArea.set(0, 0, mImage.mImageWidth, mImage.mImageHeight);
        if (mScaleType == ScaleType.NONE) {
            mState = State.FREE;
            return;
        }

        // 是否为垂直型图片
        mIsVertical = Double.compare(mImage.mImageHeight * drawingRect.width(),
                mImage.mImageWidth * drawingRect.height()) > 0;

        switch (mScaleType) {
            case FIT_CENTER:
                mBaseScale = mIsVertical ? (1f * drawingRect.height() / mImage.mImageHeight)
                        : (1f * drawingRect.width() / mImage.mImageWidth);

                mMatrix.setScale(mBaseScale, mBaseScale);
                mMatrix.mapRect(mImageArea);

                Utils.center(mImageArea, drawingRect);
                break;
            case FIT_AUTO:
                mBaseScale = 1f * drawingRect.width() / mImage.mImageWidth;

                mMatrix.setScale(mBaseScale, mBaseScale);
                mMatrix.mapRect(mImageArea);

                Utils.centerHorizontal(mImageArea, drawingRect);
                if (mIsVertical) {
                    mImageArea.offsetTo(mImageArea.left, drawingRect.top);
                } else {
                    Utils.centerVertical(mImageArea, drawingRect);
                }
                break;
        }
        mState = State.FREE;
    }

    //@WorkerThread
    private void prepareDraw(Rect rect) {
        float curScale = getScale();
        int sampleSize = getSampleSize(1f / curScale);
        mDrawables.clear();
        if (mImage.mImageSampleSize > sampleSize) {
            RectF drawingRect = new RectF(rect);

            if (drawingRect.intersect(mImageArea)) {
                drawingRect.offset(-mImageArea.left, -mImageArea.top);
            }

            float blockSize = BLOCK_SIZE * curScale;
            Rect blocks = Utils.blocks(drawingRect, blockSize);

            List<ImageDrawable> drawables = new ArrayList<>();
            int roundLeft = Math.round(mImageArea.left);
            int roundTop = Math.round(mImageArea.top);
            for (int i = blocks.top; i <= blocks.bottom; i++) {
                for (int j = blocks.left; j <= blocks.right; j++) {
                    Bitmap bitmap = null;
                    IntensifyImageCache.ImageCache imageCache = mImage.mImageCaches.get(sampleSize);
                    if (imageCache != null) bitmap = imageCache.get(new Point(j, i));
                    if (bitmap == null) continue;
                    Rect src = bitmapRect(bitmap);
                    Rect dst = Utils.blockRect(j, i, blockSize, roundLeft, roundTop);
                    if (src.bottom * sampleSize != BLOCK_SIZE || src.right * sampleSize != BLOCK_SIZE) {
                        dst.set(src.left + dst.left, src.top + dst.top,
                                Math.round(src.right * sampleSize * curScale) + dst.left,
                                Math.round(src.bottom * sampleSize * curScale) + dst.top);
                    }
                    drawables.add(new ImageDrawable(bitmap, src, dst));
                }
            }

            mDrawables.addAll(drawables);
        }
        mImage.mCurrentState = Pair.create(new RectF(mImageArea), new Rect(rect));
    }

    //@WorkerThread
    private void release() {
        if (mImage != null) {
            mImage.release();
            mImage = null;
        }
        mState = State.NONE;
    }

    public float getScale() {
        return 1f * mImageArea.width() / mImage.mImageWidth;
    }

    public float getBaseScale() {
        return mBaseScale;
    }

    public float getNextStepScale(Rect drawingRect) {
        if (Utils.isEmpty(drawingRect)) return mBaseScale;

        float v;

        if (mIsVertical) {
            v = mImageArea.width() / drawingRect.width();
        } else {
            v = mImageArea.height() / drawingRect.height();
        }

        // + 0.1 避免.99999型误差
        int index = Math.abs(Arrays.binarySearch(
                SCALE_STEP, (int) Math.round(Math.floor(v + 0.1))) + 1);

        if (index >= SCALE_STEP.length) {
            return mBaseScale / getScale();
        }
        return SCALE_STEP[index % SCALE_STEP.length] / v;
    }

    /**
     * 请求图片归位
     */
    public void zoomHoming(Rect drawingRect) {
        if (Utils.contains(mImageArea, drawingRect)) return;
        if (mZoomAnimator.isRunning()) mZoomAnimator.cancel();
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

    public int getWidth() {
        return mImage != null ? mImage.mImageWidth : 0;
    }

    public int getHeight() {
        return mImage != null ? mImage.mImageHeight : 0;
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
        mMatrix.setScale(scale, scale, focusX, focusY);
        mMatrix.mapRect(mImageArea);
    }

    public void zoomScale(Rect drawingRect, float scale, float focusX, float focusY) {
        if (Utils.isEmpty(drawingRect)) return;
        if (mZoomAnimator.isRunning()) mZoomAnimator.cancel();
        mStartRect.set(mImageArea);

        mMatrix.setScale(scale, scale, focusX, focusY);
        mMatrix.mapRect(mImageArea);

        mEndRect.set(mImageArea);
        if (!Utils.contains(mImageArea, drawingRect)) {
            Utils.home(mEndRect, drawingRect);
        }
        mZoomAnimator.start();
    }

    private void requestInvalidate() {
        if (mCallback != null) {
            mCallback.onRequestInvalidate();
        }
    }

    public List<ImageDrawable> getImageDrawables(Rect drawingRect) {
        if (Utils.isEmpty(drawingRect) || isNeedPrepare(drawingRect)) {
            return Collections.emptyList();
        }

        if (!Utils.equals(mImage.mCurrentState, Pair.create(mImageArea, drawingRect))) {
            mHandler.removeMessages(MSG_IMAGE_DRAW);
            sendMessage(MSG_IMAGE_DRAW, drawingRect);
        }

        ArrayList<ImageDrawable> drawables = new ArrayList<>(mDrawables);

        drawables.add(0, new ImageDrawable(mImage.mImageCache,
                bitmapRect(mImage.mImageCache), Utils.round(mImageArea)));

        return drawables;
    }

    public boolean isNeedPrepare(Rect drawingRect) {
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
            requestInvalidate();
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
    }

    private class IntensifyImageHandler extends Handler {

        public IntensifyImageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
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
                case MSG_IMAGE_SCALE:
                    initScaleType((Rect) msg.obj);
                    requestInvalidate();
                    break;
                case MSG_IMAGE_DRAW:
                    prepareDraw((Rect) msg.obj);
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
