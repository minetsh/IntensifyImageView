package me.kareluo.intensify.preview;

import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;

import me.kareluo.intensify.image.IntensifyImageView;

/**
 * Created by felix on 16/5/18.
 */
public class MultiPreviewActivity extends AppCompatActivity {

    private static final String TAG = "MultiPreviewActivity";

    private ViewPager mViewPager;

    private ImagePageAdapter mAdapter;

    private static final String[] images = {"pictures/smallcat.jpg", "pictures/tinycat.jpg", "pictures/xingren.jpg"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi);
        mViewPager = (ViewPager) findViewById(R.id.vp_pager);
        mAdapter = new ImagePageAdapter();
        mViewPager.setAdapter(mAdapter);
    }

    private class ImagePageAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return images.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            IntensifyImageView imageView = new IntensifyImageView(container.getContext());
            try {
                imageView.setImage(getAssets().open(images[position]));
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
