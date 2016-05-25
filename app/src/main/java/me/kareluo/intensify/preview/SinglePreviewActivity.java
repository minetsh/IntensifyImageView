package me.kareluo.intensify.preview;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.io.IOException;

import me.kareluo.intensify.image.IntensifyImage;
import me.kareluo.intensify.image.IntensifyImageView;

/**
 * Created by felix on 15/12/25.
 */
public class SinglePreviewActivity extends AppCompatActivity {
    private static final String TAG = "SinglePreviewActivity";

    private IntensifyImageView mIntensifyImageView;

    private String[] mPictures;

    private View mRootView;

    private static final String PIC_DIR = "pictures";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single);

        mRootView = findViewById(R.id.ll_root);
        mIntensifyImageView = (IntensifyImageView) findViewById(R.id.intensify_image);

        try {
            mPictures = getAssets().list(PIC_DIR);
        } catch (IOException e) {
            Log.w(TAG, e);
        }

        try {
            mIntensifyImageView.setImage(getAssets().open(PIC_DIR + "/xingren.jpg"));
        } catch (IOException e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_single, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_choose:
                chooseAssetsPictures();
                return true;
            case R.id.menu_camera:

                return true;
            case R.id.menu_scale_type:
                chooseScaleType();
                return true;
            case R.id.menu_background:
                chooseBackground();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void chooseAssetsPictures() {
        new AlertDialog.Builder(this)
                .setTitle("请选择图片")
                .setItems(mPictures, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            mIntensifyImageView.setImage(getAssets().open(PIC_DIR + "/" + mPictures[which]));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .show();
    }

    private void chooseScaleType() {
        IntensifyImage.ScaleType[] values = IntensifyImage.ScaleType.values();
        String[] scaleTypes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            scaleTypes[i] = values[i].name();
        }

        new AlertDialog.Builder(this)
                .setTitle("请选择ScaleType")
                .setItems(scaleTypes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIntensifyImageView.setScaleType(IntensifyImage.ScaleType.values()[which]);
                    }
                })
                .show();
    }

    private void chooseBackground() {
        new AlertDialog.Builder(this)
                .setTitle("请选择背景颜色")
                .setItems(new CharSequence[]{"黑色", "白色"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                mRootView.setBackgroundResource(android.R.color.black);
                                break;
                            case 1:
                                mRootView.setBackgroundResource(android.R.color.white);
                                break;
                        }
                    }
                })
                .show();
    }
}
