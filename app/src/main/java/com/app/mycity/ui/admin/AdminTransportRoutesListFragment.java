package com.app.mycity.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.R;
import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.data.repository.TransportRepository;
import com.app.mycity.databinding.FragmentAdminTransportRoutesListBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class AdminTransportRoutesListFragment extends Fragment {

    private FragmentAdminTransportRoutesListBinding b;
    private final TransportRepository repo = new TransportRepository();
    private ListenerRegistration listener;
    private AdminTransportRouteListAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentAdminTransportRoutesListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new AdminTransportRouteListAdapter(new AdminTransportRouteListAdapter.Actions() {
            @Override public void onOpen(TransportRoute route) {
                openEdit(route.getId());
            }
            @Override public void onDelete(TransportRoute route) {
                confirmDelete(route);
            }
        });
        b.rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rv.setItemAnimator(null);
        b.rv.setAdapter(adapter);

        b.fabAdd.setOnClickListener(v -> openEdit(null));

        listener = repo.listenRoutes(TransportRepository.TypeFilter.ALL, (list, err) -> {
            if (b == null) return;
            List<TransportRoute> safe = list != null ? list : new ArrayList<>();
            adapter.submit(safe);
            b.tvEmpty.setVisibility(safe.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void openEdit(@Nullable String routeId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openHostFragment(
                    AdminTransportRouteEditFragment.newInstance(routeId),
                    routeId == null ? "admin_transport_route_new" : "admin_transport_route_edit_" + routeId);
        }
    }

    private void confirmDelete(TransportRoute route) {
        new MaterialAlertDialogBuilder(requireContext(), R.style.DialogTheme)
                .setTitle(R.string.admin_transport_delete_confirm)
                .setMessage(route.getName() != null ? route.getName() : route.getRouteNumber())
                .setNegativeButton(R.string.admin_transport_cancel, null)
                .setPositiveButton(R.string.admin_transport_delete, (d, w) -> {
                    if (route.getId() == null) return;
                    repo.deleteRoute(route.getId())
                            .addOnFailureListener(e ->
                                    toast(getString(R.string.admin_transport_delete_error_format, e.getMessage())));
                })
                .show();
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) { listener.remove(); listener = null; }
        b = null;
    }
}
