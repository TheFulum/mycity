package com.app.mycity.ui.transport;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.app.mycity.databinding.FragmentSectionHostBinding;

public class TransportSectionFragment extends Fragment {

    private FragmentSectionHostBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentSectionHostBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.sectionPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return position == 0 ? new TransportFeedFragment() : new TransportMapFragment();
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });
        b.sectionPager.setOffscreenPageLimit(1);
        b.sectionPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                b.sectionPager.setUserInputEnabled(position == 0);
                updateIndicator(position);
            }
        });
        updateIndicator(0);
    }

    private void updateIndicator(int position) {
        if (b == null) return;
        b.dotFeed.setSelected(position == 0);
        b.dotMap.setSelected(position == 1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
