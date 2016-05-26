package me.kareluo.intensify.preview;

import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;

import me.kareluo.intensify.image.IntensifyImage;
import me.kareluo.intensify.image.IntensifyImageView;

/**
 * Created by felix on 16/5/18.
 */
public class MultiPreviewActivity extends AppCompatActivity {

    private static final String TAG = "MultiPreviewActivity";

    private ViewPager mViewPager;

    private ImagePageAdapter mAdapter;

    private static final String PIC_DIR = "pictures";

    private String[] mPictures;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi);

        try {
            mPictures = getAssets().list(PIC_DIR);
        } catch (IOException e) {
            Log.w(TAG, e);
        }

        mViewPager = (ViewPager) findViewById(R.id.vp_pager);
        mAdapter = new ImagePageAdapter();
        mViewPager.setAdapter(mAdapter);
    }

    private class ImagePageAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return mPictures.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            IntensifyImageView imageView = new IntensifyImageView(container.getContext());
            imageView.setScaleType(IntensifyImage.ScaleType.FIT_AUTO);
            try {
                imageView.setImage(getAssets().open(PIC_DIR + "/" + mPictures[position]));
            } catch (IOException e) {
                Log.w(TAG, e);
            }
            container.addView(imageView);
            return imageView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }
    }

}
