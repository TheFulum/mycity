package com.app.mycity.ui.transport;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.data.repository.TransportRepository;
import com.app.mycity.data.repository.TransportRepository.TypeFilter;
import com.app.mycity.databinding.FragmentTransportFeedBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class TransportFeedFragment extends Fragment {

    private FragmentTransportFeedBinding b;
    private final TransportRepository repo = new TransportRepository();
    private TransportRouteCardAdapter adapter;
    private ListenerRegistration registration;
    private TypeFilter currentFilter = TypeFilter.ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentTransportFeedBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new TransportRouteCardAdapter(route -> {
            if (route == null || route.getId() == null) return;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openTransportRouteDetail(route.getId());
            }
        });

        b.rvRoutes.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvRoutes.setItemAnimator(null);
        b.rvRoutes.setAdapter(adapter);

        b.chipAll.setOnClickListener(v -> selectFilter(TypeFilter.ALL));
        b.chipBus.setOnClickListener(v -> selectFilter(TypeFilter.BUS));
        b.chipTram.setOnClickListener(v -> selectFilter(TypeFilter.TRAM));
        b.chipTrolleybus.setOnClickListener(v -> selectFilter(TypeFilter.TROLLEYBUS));
        b.chipMarshrutka.setOnClickListener(v -> selectFilter(TypeFilter.MARSHRUTKA));

        b.swipeRefresh.setOnRefreshListener(this::subscribe);

        applyChipSelection();
        subscribe();
    }

    private void selectFilter(TypeFilter filter) {
        if (currentFilter == filter) return;
        currentFilter = filter;
        applyChipSelection();
        subscribe();
    }

    private void applyChipSelection() {
        if (b == null) return;
        b.chipAll.setSelected(currentFilter == TypeFilter.ALL);
        b.chipBus.setSelected(currentFilter == TypeFilter.BUS);
        b.chipTram.setSelected(currentFilter == TypeFilter.TRAM);
        b.chipTrolleybus.setSelected(currentFilter == TypeFilter.TROLLEYBUS);
        b.chipMarshrutka.setSelected(currentFilter == TypeFilter.MARSHRUTKA);
    }

    private void subscribe() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        registration = repo.listenRoutes(currentFilter, (list, error) -> {
            if (b == null) return;
            b.swipeRefresh.setRefreshing(false);
            List<TransportRoute> safe = list != null ? list : new ArrayList<>();
            adapter.submit(safe);
            updateEmptyState(safe.isEmpty());
        });
    }

    private void updateEmptyState(boolean empty) {
        if (b == null) return;
        b.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.rvRoutes.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        b = null;
    }
}
