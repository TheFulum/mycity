package com.app.mycity.ui.onboarding;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.app.mycity.R;

public class OnboardingPagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_COUNT = 5;

    public OnboardingPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return OnboardingStepFragment.newInstance(
                        R.drawable.ic_launcher_foreground,
                        R.string.onboarding_step1_title,
                        R.string.onboarding_step1_body);
            case 1:
                return OnboardingStepFragment.newInstance(
                        R.drawable.ic_section_problems,
                        R.string.onboarding_step2_title,
                        R.string.onboarding_step2_body);
            case 2:
                return OnboardingStepFragment.newInstance(
                        R.drawable.ic_section_city,
                        R.string.onboarding_step3_title,
                        R.string.onboarding_step3_body);
            case 3:
                return OnboardingStepFragment.newInstance(
                        R.drawable.ic_section_news,
                        R.string.onboarding_step4_title,
                        R.string.onboarding_step4_body);
            case 4:
            default:
                return OnboardingStepFragment.newInstance(
                        R.drawable.ic_section_transport,
                        R.string.onboarding_step5_title,
                        R.string.onboarding_step5_body);
        }
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
