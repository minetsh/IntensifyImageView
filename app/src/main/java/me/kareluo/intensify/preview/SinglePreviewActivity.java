package me.kareluo.intensify.preview;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import java.io.IOException;
import java.util.Locale;

import me.kareluo.intensify.image.IntensifyImage;
import me.kareluo.intensify.image.IntensifyImageView;

/**
 * Created by felix on 15/12/25.
 */
public class SinglePreviewActivity extends AppCompatActivity implements
        IntensifyImage.OnScaleChangeListener, SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "SinglePreviewActivity";

    private IntensifyImageView mIntensifyImageView;

    private String[] mPictures;

    private View mRootView;

    private ViewSwitcher mViewSwitcher;

    private TextView mCurrentScaleText;

    private TextView mMinimumScaleText;

    private TextView mMaximumScaleText;

    private SeekBar mSeekBar;

    private float mScaleWidth = 10000f;

    private static final String PIC_DIR = "pictures";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single);

        mRootView = findViewById(R.id.ll_root);
        mIntensifyImageView = (IntensifyImageView) findViewById(R.id.intensify_image);
        assert mIntensifyImageView != null;
        mIntensifyImageView.setOnScaleChangeListener(this);

        mViewSwitcher = (ViewSwitcher) findViewById(R.id.vs_switcher);
        mCurrentScaleText = (TextView) findViewById(R.id.tv_cur_scale);
        mMinimumScaleText = (TextView) findViewById(R.id.tv_min_scale);
        mMaximumScaleText = (TextView) findViewById(R.id.tv_max_scale);

        mSeekBar = (SeekBar) findViewById(R.id.sb_scale);
        assert mSeekBar != null;
        mSeekBar.setOnSeekBarChangeListener(this);

        try {
            mPictures = getAssets().list(PIC_DIR);
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
            case R.id.menu_scale:
                chooseScaleValue();
                break;
            case R.id.menu_scale_type:
                chooseScaleType();
                return true;
            case R.id.menu_background:
                chooseBackground();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void chooseScaleValue() {
        mViewSwitcher.showNext();
        float maxScale = Math.min(mIntensifyImageView.getMaximumScale(), 100f);

        mMinimumScaleText.setText(String.format(Locale.CHINA, "%.2f", mIntensifyImageView.getMinimumScale()));
        mMaximumScaleText.setText(String.format(Locale.CHINA, "%.2f", maxScale));
        mScaleWidth = maxScale - mIntensifyImageView.getMinimumScale();
        onScaleChange(mIntensifyImageView.getScale());
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
                            Log.w(TAG, e);
                        }
                        mViewSwitcher.setDisplayedChild(0);
                    }
                })
                .show();
    }

    private void chooseScaleType() {
        IntensifyImage.ScaleType scaleType = mIntensifyImageView.getScaleType();
        IntensifyImage.ScaleType[] values = IntensifyImage.ScaleType.values();
        String[] scaleTypes = new String[values.length];
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            scaleTypes[i] = values[i].name();
            if (scaleType == values[i]) {
                index = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("请选择ScaleType")
                .setSingleChoiceItems(scaleTypes, index, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIntensifyImageView.setScaleType(IntensifyImage.ScaleType.values()[which]);
                        dialog.dismiss();
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

    @Override
    public void onScaleChange(float scale) {
        mCurrentScaleText.setText(String.format(Locale.CHINA, "缩放值：%f", scale));
        int progress = Math.round((mIntensifyImageView.getScale() -
                mIntensifyImageView.getMinimumScale()) / mScaleWidth * mSeekBar.getMax());
        mSeekBar.setProgress(Math.min(Math.max(progress, 0), mSeekBar.getMax()));
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            mIntensifyImageView.setScale(
                    progress * 1f / mSeekBar.getMax() * mScaleWidth + mIntensifyImageView.getMinimumScale());
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
