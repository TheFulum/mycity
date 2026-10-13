package com.app.mycity.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.app.mycity.R;
import com.app.mycity.databinding.ActivityOnboardingBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.SessionManager;

public class OnboardingActivity extends AppCompatActivity {

    public static final String EXTRA_REPLAY_MODE = "replay_mode";

    private ActivityOnboardingBinding binding;
    private OnboardingPagerAdapter adapter;
    private View[] dots;
    private boolean replayMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        replayMode = getIntent().getBooleanExtra(EXTRA_REPLAY_MODE, false);

        adapter = new OnboardingPagerAdapter(this);
        binding.pager.setAdapter(adapter);

        setupDots();
        updateState(0);

        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateState(position);
            }
        });

        binding.btnSkip.setOnClickListener(v -> finishAndProceed());
        binding.btnNext.setOnClickListener(v -> {
            int current = binding.pager.getCurrentItem();
            if (current >= OnboardingPagerAdapter.PAGE_COUNT - 1) {
                finishAndProceed();
            } else {
                binding.pager.setCurrentItem(current + 1, true);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAndProceed();
            }
        });
    }

    private void setupDots() {
        binding.dotsContainer.removeAllViews();
        dots = new View[OnboardingPagerAdapter.PAGE_COUNT];
        int dotSize = (int) (8 * getResources().getDisplayMetrics().density);
        int dotMargin = (int) (4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < OnboardingPagerAdapter.PAGE_COUNT; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.leftMargin = dotMargin;
            lp.rightMargin = dotMargin;
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_onboarding_dot_inactive);
            binding.dotsContainer.addView(dot);
            dots[i] = dot;
        }
    }

    private void updateState(int position) {
        if (dots != null) {
            for (int i = 0; i < dots.length; i++) {
                dots[i].setBackgroundResource(
                        i == position
                                ? R.drawable.bg_onboarding_dot_active
                                : R.drawable.bg_onboarding_dot_inactive);
            }
        }
        boolean isLast = position == OnboardingPagerAdapter.PAGE_COUNT - 1;
        binding.btnNext.setText(isLast ? R.string.onboarding_done : R.string.onboarding_next);
    }

    private void finishAndProceed() {
        if (replayMode) {
            finish();
            return;
        }
        new SessionManager(this).setOnboardingCompleted(true);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
