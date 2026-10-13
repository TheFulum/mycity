package com.app.mycity.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.app.mycity.databinding.FragmentOnboardingStepBinding;

public class OnboardingStepFragment extends Fragment {

    private static final String ARG_IMAGE_RES = "arg_image_res";
    private static final String ARG_TITLE_RES = "arg_title_res";
    private static final String ARG_BODY_RES = "arg_body_res";

    private FragmentOnboardingStepBinding binding;

    public static OnboardingStepFragment newInstance(@DrawableRes int imageRes,
                                                     @StringRes int titleRes,
                                                     @StringRes int bodyRes) {
        OnboardingStepFragment f = new OnboardingStepFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_IMAGE_RES, imageRes);
        args.putInt(ARG_TITLE_RES, titleRes);
        args.putInt(ARG_BODY_RES, bodyRes);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingStepBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args == null) return;
        binding.ivStepImage.setImageResource(args.getInt(ARG_IMAGE_RES));
        binding.tvStepTitle.setText(args.getInt(ARG_TITLE_RES));
        binding.tvStepBody.setText(args.getInt(ARG_BODY_RES));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
