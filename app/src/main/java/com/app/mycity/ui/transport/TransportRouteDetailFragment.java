package com.app.mycity.ui.transport;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.R;
import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.data.model.TransportStop;
import com.app.mycity.data.repository.TransportRepository;
import com.app.mycity.databinding.FragmentTransportRouteDetailBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransportRouteDetailFragment extends Fragment {

    private static final String ARG_ROUTE_ID = "route_id";

    public static TransportRouteDetailFragment newInstance(String routeId) {
        TransportRouteDetailFragment f = new TransportRouteDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROUTE_ID, routeId);
        f.setArguments(args);
        return f;
    }

    private FragmentTransportRouteDetailBinding b;
    private final TransportRepository repo = new TransportRepository();
    private TransportStopCardAdapter stopAdapter;
    private ListenerRegistration stopsRegistration;
    private TransportRoute route;
    private List<TransportStop> allStops = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentTransportRouteDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).popHost();
            }
        });

        stopAdapter = new TransportStopCardAdapter(null);
        b.rvStops.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvStops.setItemAnimator(null);
        b.rvStops.setNestedScrollingEnabled(false);
        b.rvStops.setAdapter(stopAdapter);

        String routeId = getArguments() != null ? getArguments().getString(ARG_ROUTE_ID) : null;
        if (routeId == null || routeId.isEmpty()) return;

        repo.getRoute(routeId).addOnSuccessListener(snap -> {
            if (b == null) return;
            if (snap == null || !snap.exists()) return;
            route = snap.toObject(TransportRoute.class);
            if (route != null && route.getId() == null) route.setId(snap.getId());
            renderHeader();
            renderSchedule();
            subscribeStops();
        });
    }

    private void renderHeader() {
        if (b == null || route == null) return;
        String number = route.getRouteNumber();
        b.tvRouteNumber.setText(number != null ? number : "");
        b.tvTopTitle.setText(number != null ? number : "");

        int color = parseRouteColor(route.getColor());
        b.tvRouteNumber.setBackgroundTintList(ColorStateList.valueOf(color));

        b.tvRouteType.setText(typeLabel(route.getType()));
        b.tvRouteName.setText(route.getName() != null ? route.getName() : "");
        b.tvActiveBadge.setVisibility(route.isActive() ? View.GONE : View.VISIBLE);
    }

    private void renderSchedule() {
        if (b == null || route == null) return;
        List<String> times = readScheduleTimes(route);
        b.llSchedule.removeAllViews();
        if (times == null || times.isEmpty()) {
            b.scrollSchedule.setVisibility(View.GONE);
            b.tvScheduleEmpty.setVisibility(View.VISIBLE);
            return;
        }
        b.scrollSchedule.setVisibility(View.VISIBLE);
        b.tvScheduleEmpty.setVisibility(View.GONE);
        for (String time : times) {
            if (time == null || time.trim().isEmpty()) continue;
            TextView tv = new TextView(requireContext());
            tv.setText(time.trim());
            tv.setTextSize(13f);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            tv.setBackgroundResource(R.drawable.bg_schedule_time);
            tv.setPadding(dp(14), dp(8), dp(14), dp(8));
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            tv.setLayoutParams(lp);
            b.llSchedule.addView(tv);
        }
        if (b.llSchedule.getChildCount() == 0) {
            b.scrollSchedule.setVisibility(View.GONE);
            b.tvScheduleEmpty.setVisibility(View.VISIBLE);
        }
    }

    private List<String> readScheduleTimes(TransportRoute r) {
        return r.getScheduleTimes();
    }

    private void subscribeStops() {
        if (stopsRegistration != null) {
            stopsRegistration.remove();
            stopsRegistration = null;
        }
        stopsRegistration = repo.listenStops((list, error) -> {
            if (b == null) return;
            allStops = list != null ? list : new ArrayList<>();
            renderStops();
        });
    }

    private void renderStops() {
        if (b == null || route == null || stopAdapter == null) return;
        List<String> orderedIds = route.getStopIds();
        if (orderedIds == null || orderedIds.isEmpty()) {
            stopAdapter.submit(new ArrayList<>());
            return;
        }
        Map<String, TransportStop> index = new HashMap<>();
        for (TransportStop s : allStops) {
            if (s != null && s.getId() != null) index.put(s.getId(), s);
        }
        List<TransportStop> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            if (id == null) continue;
            TransportStop s = index.get(id);
            if (s != null) ordered.add(s);
        }
        stopAdapter.submit(ordered);
    }

    private int parseRouteColor(String hex) {
        int fallback = ContextCompat.getColor(requireContext(), R.color.accent_blue);
        if (hex == null || hex.trim().isEmpty()) return fallback;
        try {
            return Color.parseColor(hex.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private String typeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case TransportRoute.TYPE_BUS:
                return getString(R.string.transport_type_bus);
            case TransportRoute.TYPE_TRAM:
                return getString(R.string.transport_type_tram);
            case TransportRoute.TYPE_TROLLEYBUS:
                return getString(R.string.transport_type_trolleybus);
            case TransportRoute.TYPE_MARSHRUTKA:
                return getString(R.string.transport_type_marshrutka);
            default:
                return "";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (stopsRegistration != null) {
            stopsRegistration.remove();
            stopsRegistration = null;
        }
        b = null;
    }
}
