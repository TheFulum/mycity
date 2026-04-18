package com.app.mycity.ui.feed;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.app.mycity.R;

import java.util.ArrayList;

public class PhotoGalleryActivity extends AppCompatActivity {

    public static final String EXTRA_URLS = "photo_urls";
    public static final String EXTRA_INDEX = "start_index";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_photo_gallery);

        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        int startIndex = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (urls == null || urls.isEmpty()) { finish(); return; }

        ViewPager2 pager = findViewById(R.id.vp_gallery);
        TextView counter = findViewById(R.id.tv_counter);
        ImageButton btnClose = findViewById(R.id.btn_close);

        pager.setAdapter(new PhotoSwipeAdapter(urls));
        pager.setCurrentItem(startIndex, false);
        updateCounter(counter, startIndex + 1, urls.size());

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                updateCounter(counter, position + 1, urls.size());
            }
        });

        btnClose.setOnClickListener(v -> finish());

        // Скрыть UI через тап
        pager.setOnClickListener(v -> toggleSystemUi());
    }

    private void updateCounter(TextView tv, int current, int total) {
        tv.setText(current + " / " + total);
        tv.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
    }

    private void toggleSystemUi() {
        View decorView = getWindow().getDecorView();
        int flags = decorView.getSystemUiVisibility();
        if ((flags & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
            decorView.setSystemUiVisibility(0);
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
