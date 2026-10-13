package com.app.mycity.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.data.model.TransportStop;
import com.app.mycity.data.repository.TransportRepository;
import com.app.mycity.databinding.FragmentAdminTransportImportBinding;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminTransportImportFragment extends Fragment {

    private FragmentAdminTransportImportBinding b;
    private final TransportRepository repo = new TransportRepository();
    private final Gson gson = new Gson();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentAdminTransportImportBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.btnImport.setOnClickListener(v -> runImport());
    }

    private void runImport() {
        if (b == null) return;
        String raw = b.etJson.getText() != null ? b.etJson.getText().toString().trim() : "";
        if (raw.isEmpty()) {
            toast(getString(R.string.admin_transport_validation_required));
            return;
        }

        ImportPayload payload;
        try {
            payload = gson.fromJson(raw, ImportPayload.class);
        } catch (JsonSyntaxException ex) {
            toast(getString(R.string.admin_transport_import_error));
            return;
        }
        if (payload == null || payload.stops == null || payload.routes == null) {
            toast(getString(R.string.admin_transport_import_error));
            return;
        }

        setBusy(true);

        final List<String> stopIds = new ArrayList<>(Collections.nCopies(payload.stops.size(), null));
        final List<Task<Void>> stopTasks = new ArrayList<>();
        for (int i = 0; i < payload.stops.size(); i++) {
            ImportStop src = payload.stops.get(i);
            if (src == null || src.name == null) continue;
            DocumentReference ref = repo.newStopRef();
            TransportStop stop = new TransportStop();
            stop.setId(ref.getId());
            stop.setName(src.name);
            if (src.lat != null) stop.setLat(src.lat);
            if (src.lng != null) stop.setLng(src.lng);
            stop.setRouteIds(new ArrayList<>());
            stopIds.set(i, ref.getId());
            stopTasks.add(repo.saveStop(stop));
        }

        Tasks.whenAll(stopTasks).addOnSuccessListener(unused -> {
            final List<Task<Void>> routeTasks = new ArrayList<>();
            int validRoutes = 0;
            for (ImportRoute src : payload.routes) {
                if (src == null || src.number == null || src.name == null || src.stops == null) continue;
                DocumentReference ref = repo.newRouteRef();
                TransportRoute route = new TransportRoute();
                route.setId(ref.getId());
                route.setRouteNumber(src.number);
                route.setName(src.name);
                route.setType(mapType(src.type));
                route.setColor(src.color != null ? src.color : "#3478F6");
                List<String> mappedStops = new ArrayList<>();
                for (int idx : src.stops) {
                    if (idx >= 0 && idx < stopIds.size() && stopIds.get(idx) != null) {
                        mappedStops.add(stopIds.get(idx));
                    }
                }
                route.setStopIds(mappedStops);
                route.setScheduleTimes(src.scheduleTimes != null ? src.scheduleTimes : new ArrayList<>());
                route.setActive(true);
                routeTasks.add(repo.saveRoute(route));
                validRoutes++;
            }
            final int routesCount = validRoutes;
            Tasks.whenAll(routeTasks).addOnSuccessListener(u -> {
                setBusy(false);
                showSummary(stopTasks.size(), routesCount);
            }).addOnFailureListener(e -> {
                setBusy(false);
                toast(getString(R.string.admin_transport_import_error) + ": " + e.getMessage());
            });
        }).addOnFailureListener(e -> {
            setBusy(false);
            toast(getString(R.string.admin_transport_import_error) + ": " + e.getMessage());
        });
    }

    private String mapType(String input) {
        if (input == null) return TransportRoute.TYPE_BUS;
        switch (input.trim().toUpperCase()) {
            case "BUS": return TransportRoute.TYPE_BUS;
            case "TRAM": return TransportRoute.TYPE_TRAM;
            case "TROLLEYBUS":
            case "TROLLEY": return TransportRoute.TYPE_TROLLEYBUS;
            case "MARSHRUTKA":
            case "MINIBUS": return TransportRoute.TYPE_MARSHRUTKA;
            default: return TransportRoute.TYPE_BUS;
        }
    }

    private void showSummary(int stopsCount, int routesCount) {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_transport_tab_import)
                .setMessage(getString(R.string.admin_transport_import_success_format, stopsCount, routesCount))
                .setPositiveButton(R.string.admin_transport_ok, (d, w) -> {
                    if (b != null && b.etJson != null) b.etJson.setText("");
                })
                .show();
    }

    private void setBusy(boolean busy) {
        if (b == null) return;
        b.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        b.btnImport.setEnabled(!busy);
        b.etJson.setEnabled(!busy);
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private static class ImportPayload {
        List<ImportStop> stops;
        List<ImportRoute> routes;
    }

    private static class ImportStop {
        String name;
        Double lat;
        Double lng;
    }

    private static class ImportRoute {
        String number;
        String type;
        String name;
        String color;
        List<Integer> stops;
        @SerializedName("scheduleTimes")
        List<String> scheduleTimes;
    }
}
