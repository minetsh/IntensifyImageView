package me.kareluo.intensify.image;

import android.util.Log;

/**
 * Created by felix on 15/12/25.
 */
class Logger {
    public static final boolean DEBUG = true;

    public static void i(String tag, String msg) {
        if (DEBUG) Log.i(tag, msg);
    }

    public static void i(String tag, String format, Object... args) {
        if (DEBUG) Log.i(tag, String.format(format, args));
    }

    public static void d(String TAG, String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }

    public static void w(String tag, String msg) {
        if (DEBUG) Log.w(tag, msg);
    }

    public static void w(String tag, Throwable tr) {
        if (DEBUG) Log.w(tag, tr);
    }

    public static void w(String tag, String msg, Throwable tr) {
        if (DEBUG) Log.w(tag, msg, tr);
    }

    public static void e(String tag, String msg) {
        if (DEBUG) Log.e(tag, msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (DEBUG) Log.e(tag, msg, tr);
    }
}
