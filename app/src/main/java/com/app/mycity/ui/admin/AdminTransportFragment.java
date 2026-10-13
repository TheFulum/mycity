package com.app.mycity.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.app.mycity.R;
import com.app.mycity.databinding.FragmentAdminTransportBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.android.material.tabs.TabLayoutMediator;

public class AdminTransportFragment extends Fragment {

    private FragmentAdminTransportBinding b;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentAdminTransportBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).popHost();
            }
        });

        b.pager.setAdapter(new PagerAdapter(requireActivity()));

        new TabLayoutMediator(b.tabs, b.pager, (tab, position) -> {
            if (position == 0) tab.setText(R.string.admin_transport_tab_routes);
            else if (position == 1) tab.setText(R.string.admin_transport_tab_stops);
            else tab.setText(R.string.admin_transport_tab_import);
        }).attach();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (b != null) {
            b.pager.setAdapter(null);
        }
        b = null;
    }

    private static class PagerAdapter extends FragmentStateAdapter {
        PagerAdapter(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @NonNull @Override
        public Fragment createFragment(int position) {
            if (position == 0) return new AdminTransportRoutesListFragment();
            if (position == 1) return new AdminTransportStopsListFragment();
            return new AdminTransportImportFragment();
        }

        @Override
        public int getItemCount() { return 3; }
    }
}
