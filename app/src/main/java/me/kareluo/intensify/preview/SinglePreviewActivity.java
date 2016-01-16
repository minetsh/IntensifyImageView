package me.kareluo.intensify.preview;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

import me.kareluo.intensify.image.IntensifyImageView;

/**
 * Created by felix on 15/12/25.
 */
public class SinglePreviewActivity extends Activity {
    private static final String TAG = "SinglePreviewActivity";

    private IntensifyImageView mIntensifyImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single);

        mIntensifyImageView = (IntensifyImageView) findViewById(R.id.intensify_image);
        try {
            mIntensifyImageView.setImage(getAssets().open("cat0.jpg"));
        } catch (IOException e) {
            Log.d(TAG, "");
        }
    }
}
