package me.kareluo.intensify.image;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by felix on 15/12/17.
 */
public abstract class IntensifyView extends View {

    public IntensifyView(Context context) {
        this(context, null, 0);
    }

    public IntensifyView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IntensifyView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs, defStyleAttr);
    }

    protected void initialize(Context context, AttributeSet attrs, int defStyleAttr) {

    }
}
