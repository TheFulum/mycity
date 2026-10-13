package com.app.mycity.ui.admin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.data.model.TransportStop;
import com.app.mycity.data.repository.TransportRepository;
import com.app.mycity.databinding.FragmentAdminTransportRouteEditBinding;
import com.app.mycity.databinding.ItemAdminRouteStopBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdminTransportRouteEditFragment extends Fragment {

    private static final String ARG_ID = "route_id";

    private static final String[] PALETTE = {
            "#5B5FBE", "#27AE60", "#C0392B", "#F1C40F",
            "#3498DB", "#9B59B6", "#E67E22", "#16A085"
    };

    public static AdminTransportRouteEditFragment newInstance(@Nullable String routeId) {
        AdminTransportRouteEditFragment f = new AdminTransportRouteEditFragment();
        Bundle args = new Bundle();
        if (routeId != null) args.putString(ARG_ID, routeId);
        f.setArguments(args);
        return f;
    }

    private FragmentAdminTransportRouteEditBinding b;
    private final TransportRepository repo = new TransportRepository();
    private ListenerRegistration stopsListener;
    private TransportRoute route;
    private final List<String> selectedStopIds = new ArrayList<>();
    private final List<TransportStop> allStops = new ArrayList<>();
    private final Map<String, TransportStop> stopById = new HashMap<>();
    private String selectedColor = PALETTE[0];
    private RouteStopAdapter rsAdapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentAdminTransportRouteEditBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.btnBack.setOnClickListener(v -> popBack());
        b.btnSave.setOnClickListener(v -> save());
        b.btnAddStop.setOnClickListener(v -> showStopPicker());

        rsAdapter = new RouteStopAdapter(new RouteStopAdapter.Actions() {
            @Override public void onMoveUp(int position) {
                if (position <= 0 || position >= selectedStopIds.size()) return;
                Collections.swap(selectedStopIds, position, position - 1);
                refreshStopsRv();
            }
            @Override public void onMoveDown(int position) {
                if (position < 0 || position >= selectedStopIds.size() - 1) return;
                Collections.swap(selectedStopIds, position, position + 1);
                refreshStopsRv();
            }
            @Override public void onRemove(int position) {
                if (position < 0 || position >= selectedStopIds.size()) return;
                selectedStopIds.remove(position);
                refreshStopsRv();
            }
        }, stopById);
        b.rvStops.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvStops.setItemAnimator(null);
        b.rvStops.setAdapter(rsAdapter);

        buildColorRow();

        stopsListener = repo.listenStops((list, err) -> {
            if (b == null) return;
            allStops.clear();
            stopById.clear();
            if (list != null) {
                allStops.addAll(list);
                for (TransportStop s : list) {
                    if (s.getId() != null) stopById.put(s.getId(), s);
                }
            }
            refreshStopsRv();
        });

        String routeId = getArguments() != null ? getArguments().getString(ARG_ID) : null;
        if (routeId != null) {
            loadRoute(routeId);
        } else {
            route = new TransportRoute();
            route.setActive(true);
            applyColorSelection(PALETTE[0]);
            checkTypeChip(TransportRoute.TYPE_BUS);
        }
    }

    private void loadRoute(String id) {
        setLoading(true);
        repo.getRoute(id).addOnSuccessListener(snap -> {
            if (b == null) return;
            setLoading(false);
            if (snap == null || !snap.exists()) {
                toast("Маршрут не найден");
                popBack();
                return;
            }
            route = snap.toObject(TransportRoute.class);
            if (route == null) {
                route = new TransportRoute();
                route.setId(id);
            }
            bindRoute();
        }).addOnFailureListener(e -> {
            setLoading(false);
            toast("Ошибка: " + e.getMessage());
            popBack();
        });
    }

    private void bindRoute() {
        if (route == null || b == null) return;
        b.etNumber.setText(route.getRouteNumber() != null ? route.getRouteNumber() : "");
        b.etName.setText(route.getName() != null ? route.getName() : "");
        checkTypeChip(route.getType());
        applyColorSelection(route.getColor() != null ? route.getColor() : PALETTE[0]);
        b.switchActive.setChecked(route.isActive());
        if (route.getScheduleTimes() != null && !route.getScheduleTimes().isEmpty()) {
            b.etSchedule.setText(TextUtils.join(", ", route.getScheduleTimes()));
        }
        selectedStopIds.clear();
        if (route.getStopIds() != null) selectedStopIds.addAll(route.getStopIds());
        refreshStopsRv();
    }

    private void checkTypeChip(@Nullable String type) {
        if (b == null) return;
        b.chipGroupType.clearCheck();
        if (type == null) return;
        Chip chip;
        switch (type) {
            case TransportRoute.TYPE_BUS: chip = b.chipBus; break;
            case TransportRoute.TYPE_TRAM: chip = b.chipTram; break;
            case TransportRoute.TYPE_TROLLEYBUS: chip = b.chipTrolley; break;
            case TransportRoute.TYPE_MARSHRUTKA: chip = b.chipMarshrutka; break;
            default: return;
        }
        chip.setChecked(true);
    }

    private String selectedTypeCode() {
        if (b == null) return null;
        int checked = b.chipGroupType.getCheckedChipId();
        if (checked == R.id.chip_bus) return TransportRoute.TYPE_BUS;
        if (checked == R.id.chip_tram) return TransportRoute.TYPE_TRAM;
        if (checked == R.id.chip_trolley) return TransportRoute.TYPE_TROLLEYBUS;
        if (checked == R.id.chip_marshrutka) return TransportRoute.TYPE_MARSHRUTKA;
        return null;
    }

    private void buildColorRow() {
        if (b == null) return;
        b.colorRow.removeAllViews();
        Context ctx = requireContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int size = (int) (32 * density);
        int margin = (int) (6 * density);
        for (String hex : PALETTE) {
            View dot = new View(ctx);
            ViewGroup.MarginLayoutParams lp = new android.widget.LinearLayout.LayoutParams(size, size);
            ((android.widget.LinearLayout.LayoutParams) lp).setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            dot.setTag(hex);
            dot.setOnClickListener(v -> applyColorSelection((String) v.getTag()));
            b.colorRow.addView(dot);
        }
        renderColorSwatches();
    }

    private void applyColorSelection(String hex) {
        selectedColor = hex;
        renderColorSwatches();
    }

    private void renderColorSwatches() {
        if (b == null) return;
        float density = requireContext().getResources().getDisplayMetrics().density;
        for (int i = 0; i < b.colorRow.getChildCount(); i++) {
            View dot = b.colorRow.getChildAt(i);
            String hex = (String) dot.getTag();
            int color = parseHex(hex, ContextCompat.getColor(requireContext(), R.color.accent_blue));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            boolean active = hex.equalsIgnoreCase(selectedColor);
            if (active) {
                bg.setStroke((int) (3 * density),
                        ContextCompat.getColor(requireContext(), R.color.text_primary));
            }
            dot.setBackground(bg);
        }
    }

    private int parseHex(String hex, int fallback) {
        if (hex == null) return fallback;
        try { return Color.parseColor(hex); } catch (IllegalArgumentException e) { return fallback; }
    }

    private void refreshStopsRv() {
        if (b == null || rsAdapter == null) return;
        rsAdapter.submit(new ArrayList<>(selectedStopIds));
        b.tvStopsEmpty.setVisibility(selectedStopIds.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showStopPicker() {
        if (allStops.isEmpty()) {
            toast("Сначала добавьте остановки на вкладке «Остановки»");
            return;
        }
        String[] names = new String[allStops.size()];
        boolean[] checked = new boolean[allStops.size()];
        Set<String> already = new HashSet<>(selectedStopIds);
        for (int i = 0; i < allStops.size(); i++) {
            TransportStop s = allStops.get(i);
            names[i] = s.getName() != null ? s.getName() : "(без названия)";
            checked[i] = already.contains(s.getId());
        }
        boolean[] working = Arrays.copyOf(checked, checked.length);
        new MaterialAlertDialogBuilder(requireContext(), R.style.DialogTheme)
                .setTitle(R.string.admin_transport_route_pick_stops)
                .setMultiChoiceItems(names, working, (d, which, isChecked) -> working[which] = isChecked)
                .setNegativeButton(R.string.admin_transport_cancel, null)
                .setPositiveButton(R.string.admin_transport_ok, (d, w) -> {
                    Set<String> newSel = new HashSet<>();
                    for (int i = 0; i < working.length; i++) {
                        if (working[i]) newSel.add(allStops.get(i).getId());
                    }
                    List<String> result = new ArrayList<>();
                    for (String id : selectedStopIds) {
                        if (newSel.contains(id)) {
                            result.add(id);
                            newSel.remove(id);
                        }
                    }
                    for (TransportStop s : allStops) {
                        if (newSel.contains(s.getId())) result.add(s.getId());
                    }
                    selectedStopIds.clear();
                    selectedStopIds.addAll(result);
                    refreshStopsRv();
                })
                .show();
    }

    private List<String> parseSchedule(String raw) {
        List<String> times = new ArrayList<>();
        if (raw == null) return times;
        String[] parts = raw.split("[,\\n]+");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) times.add(t);
        }
        return times;
    }

    private void save() {
        if (b == null) return;
        String number = b.etNumber.getText() != null ? b.etNumber.getText().toString().trim() : "";
        String name = b.etName.getText() != null ? b.etName.getText().toString().trim() : "";
        String type = selectedTypeCode();

        boolean ok = true;
        if (TextUtils.isEmpty(number)) { b.tilNumber.setError(getString(R.string.admin_transport_validation_required)); ok = false; }
        else b.tilNumber.setError(null);
        if (TextUtils.isEmpty(name)) { b.tilName.setError(getString(R.string.admin_transport_validation_required)); ok = false; }
        else b.tilName.setError(null);
        if (type == null) { toast(getString(R.string.admin_transport_validation_required)); ok = false; }
        if (selectedStopIds.isEmpty()) { toast(getString(R.string.admin_transport_validation_required)); ok = false; }
        if (!ok) return;

        if (route == null) route = new TransportRoute();
        route.setRouteNumber(number);
        route.setName(name);
        route.setType(type);
        route.setColor(selectedColor);
        route.setStopIds(new ArrayList<>(selectedStopIds));
        route.setActive(b.switchActive.isChecked());
        String scheduleRaw = b.etSchedule.getText() != null ? b.etSchedule.getText().toString() : "";
        route.setScheduleTimes(parseSchedule(scheduleRaw));

        setLoading(true);
        repo.saveRoute(route)
                .addOnSuccessListener(v -> propagateStopLinks())
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast(getString(R.string.admin_transport_save_error_format,
                            e.getMessage() != null ? e.getMessage() : ""));
                });
    }

    private void propagateStopLinks() {
        String routeId = route.getId();
        if (routeId == null) {
            finishSave();
            return;
        }
        List<Task<Void>> tasks = new ArrayList<>();
        for (TransportStop s : stopById.values()) {
            if (s == null || s.getId() == null) continue;
            List<String> ids = s.getRouteIds() != null ? new ArrayList<>(s.getRouteIds()) : new ArrayList<>();
            boolean shouldHave = selectedStopIds.contains(s.getId());
            boolean hasIt = ids.contains(routeId);
            if (shouldHave && !hasIt) {
                ids.add(routeId);
                s.setRouteIds(ids);
                tasks.add(repo.saveStop(s));
            } else if (!shouldHave && hasIt) {
                ids.remove(routeId);
                s.setRouteIds(ids);
                tasks.add(repo.saveStop(s));
            }
        }
        if (tasks.isEmpty()) {
            finishSave();
            return;
        }
        Tasks.whenAll(tasks)
                .addOnSuccessListener(v -> finishSave())
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast(getString(R.string.admin_transport_link_stops_error_format,
                            e.getMessage() != null ? e.getMessage() : ""));
                });
    }

    private void finishSave() {
        setLoading(false);
        toast(getString(R.string.admin_transport_save_success));
        popBack();
    }

    private void popBack() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).popHost();
        }
    }

    private void setLoading(boolean loading) {
        if (b == null) return;
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnSave.setEnabled(!loading);
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (stopsListener != null) { stopsListener.remove(); stopsListener = null; }
        b = null;
    }

    static class RouteStopAdapter extends RecyclerView.Adapter<RouteStopAdapter.VH> {

        interface Actions {
            void onMoveUp(int position);
            void onMoveDown(int position);
            void onRemove(int position);
        }

        private List<String> ids = new ArrayList<>();
        private final Actions actions;
        private final Map<String, TransportStop> stopById;

        RouteStopAdapter(Actions actions, Map<String, TransportStop> stopById) {
            this.actions = actions;
            this.stopById = stopById;
        }

        void submit(List<String> newIds) {
            ids = newIds;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemAdminRouteStopBinding b = ItemAdminRouteStopBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String id = ids.get(position);
            TransportStop stop = stopById.get(id);
            String name = stop != null && stop.getName() != null ? stop.getName() : id;
            holder.bind(position, name, ids.size(), actions);
        }

        @Override
        public int getItemCount() { return ids.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final ItemAdminRouteStopBinding b;

            VH(ItemAdminRouteStopBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(int position, String name, int total, Actions actions) {
                b.tvIndex.setText(String.valueOf(position + 1));
                b.tvName.setText(name);
                b.btnUp.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
                b.btnDown.setVisibility(position == total - 1 ? View.INVISIBLE : View.VISIBLE);
                b.btnUp.setOnClickListener(v -> actions.onMoveUp(position));
                b.btnDown.setOnClickListener(v -> actions.onMoveDown(position));
                b.btnRemove.setOnClickListener(v -> actions.onRemove(position));
            }
        }
    }
}
