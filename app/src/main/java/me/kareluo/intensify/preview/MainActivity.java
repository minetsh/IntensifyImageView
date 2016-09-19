package me.kareluo.intensify.preview;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_single_preview).setOnClickListener(this);
        findViewById(R.id.btn_multi_preview).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_single_preview:
                startActivity(new Intent(this, SinglePreviewActivity.class));
                break;
            case R.id.btn_multi_preview:
                startActivity(new Intent(this, MultiPreviewActivity.class));
                break;
        }
    }
}
