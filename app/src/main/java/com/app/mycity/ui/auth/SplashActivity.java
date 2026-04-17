package com.app.mycity.ui.auth;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.app.mycity.R;
import com.app.mycity.databinding.ActivitySplashBinding;
import com.app.mycity.databinding.ViewCircleButtonBinding;
import com.app.mycity.ui.main.MainActivity;


public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private FirebaseAuth firebaseAuth;

    private final float[] centerX = new float[4];
    private final float[] centerY = new float[4];
    private static final float CAPTURE_RADIUS_DP = 72f;
    private int capturedIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();

        if (firebaseAuth.getCurrentUser() != null) {
            goToMain();
            return;
        }

        setupCircleAppearance();
        setupCircles();
        playEntranceAnimation();
    }

    /** Выставляем цвет фона, иконку и подпись каждого кружка */
    private void setupCircleAppearance() {
        applyCircle(binding.circleLogin,    R.drawable.bg_circle_login,    R.drawable.ic_login,    "ВХОД");
        applyCircle(binding.circleRegister, R.drawable.bg_circle_register, R.drawable.ic_register, "РЕГИСТРАЦИЯ");
        applyCircle(binding.circleGuest,    R.drawable.bg_circle_guest,    R.drawable.ic_guest,    "ГОСТЬ");
        applyCircle(binding.circleExit,     R.drawable.bg_circle_exit,     R.drawable.ic_exit,     "ВЫЙТИ");
    }

    private void applyCircle(ViewCircleButtonBinding circle, int bgRes, int iconRes, String label) {
        circle.circleBackground.setBackgroundResource(bgRes);
        circle.circleIcon.setImageResource(iconRes);
        circle.circleLabel.setText(label);
    }

    private void setupCircles() {
        View[] circles = {
                binding.circleLogin.getRoot(),
                binding.circleRegister.getRoot(),
                binding.circleGuest.getRoot(),
                binding.circleExit.getRoot()
        };

        binding.getRoot().post(() -> {
            for (int i = 0; i < circles.length; i++) {
                int[] loc = new int[2];
                circles[i].getLocationInWindow(loc);
                centerX[i] = loc[0] + circles[i].getWidth() / 2f;
                centerY[i] = loc[1] + circles[i].getHeight() / 2f;
            }
        });

        binding.dragHandle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    capturedIndex = -1;
                    break;
                case MotionEvent.ACTION_MOVE:
                    checkCapture(event.getRawX(), event.getRawY(), circles);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (capturedIndex >= 0) onCircleSelected(capturedIndex);
                    resetAll(circles);
                    break;
            }
            return true;
        });

        circles[0].setOnClickListener(v -> onCircleSelected(0));
        circles[1].setOnClickListener(v -> onCircleSelected(1));
        circles[2].setOnClickListener(v -> onCircleSelected(2));
        circles[3].setOnClickListener(v -> onCircleSelected(3));
    }

    private void checkCapture(float rawX, float rawY, View[] circles) {
        float radiusPx = dpToPx(CAPTURE_RADIUS_DP);
        int newCapture = -1;

        for (int i = 0; i < circles.length; i++) {
            float dx = rawX - centerX[i];
            float dy = rawY - centerY[i];
            if (Math.sqrt(dx * dx + dy * dy) < radiusPx) {
                newCapture = i;
                break;
            }
        }

        if (newCapture != capturedIndex) {
            if (capturedIndex >= 0) animateCircle(circles[capturedIndex], 1f, 1f);
            capturedIndex = newCapture;
            if (capturedIndex >= 0) animateCircle(circles[capturedIndex], 1.12f, 0.75f);
        }
    }

    private void animateCircle(View circle, float scale, float alpha) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(circle, "scaleX", scale),
                ObjectAnimator.ofFloat(circle, "scaleY", scale),
                ObjectAnimator.ofFloat(circle, "alpha", alpha)
        );
        set.setDuration(180);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    private void resetAll(View[] circles) {
        for (View c : circles) animateCircle(c, 1f, 1f);
        capturedIndex = -1;
    }

    private void playEntranceAnimation() {
        View[] circles = {
                binding.circleLogin.getRoot(),
                binding.circleRegister.getRoot(),
                binding.circleGuest.getRoot(),
                binding.circleExit.getRoot()
        };

        long[] delays = {0, 120, 240, 360};
        for (int i = 0; i < circles.length; i++) {
            circles[i].setAlpha(0f);
            circles[i].setScaleX(0.5f);
            circles[i].setScaleY(0.5f);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(circles[i], "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(circles[i], "scaleX", 0.5f, 1f),
                    ObjectAnimator.ofFloat(circles[i], "scaleY", 0.5f, 1f)
            );
            set.setDuration(450);
            set.setStartDelay(delays[i]);
            set.setInterpolator(new DecelerateInterpolator(1.5f));
            set.start();
        }
    }

    private void onCircleSelected(int index) {
        switch (index) {
            case 0:
                startActivity(new Intent(this, LoginActivity.class));
                break;
            case 1:
                startActivity(new Intent(this, RegisterActivity.class));
                break;
            case 2:
                Intent guestIntent = new Intent(this, MainActivity.class);
                guestIntent.putExtra("is_guest", true);
                startActivity(guestIntent);
                finish();
                break;
            case 3:
                finishAffinity();
                break;
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}