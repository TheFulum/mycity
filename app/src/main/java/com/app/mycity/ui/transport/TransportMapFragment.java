package com.app.mycity.ui.transport;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.data.model.TransportStop;
import com.app.mycity.data.repository.TransportRepository;
import com.app.mycity.data.repository.TransportRepository.TypeFilter;
import com.app.mycity.databinding.FragmentTransportMapBinding;
import com.app.mycity.util.GeoUtils;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransportMapFragment extends Fragment {

    private FragmentTransportMapBinding b;
    private MapView heldMapView;
    private final TransportRepository repo = new TransportRepository();
    private ListenerRegistration routesRegistration;
    private ListenerRegistration stopsRegistration;

    private TypeFilter currentFilter = TypeFilter.ALL;
    private List<TransportStop> stops = new ArrayList<>();
    private List<TransportRoute> allRoutes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentTransportMapBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.mapView.setTileSource(TileSourceFactory.MAPNIK);
        b.mapView.setMultiTouchControls(true);
        b.mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        b.mapView.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
        b.mapView.getController().setCenter(new GeoPoint(GeoUtils.MOGILEV_LAT, GeoUtils.MOGILEV_LNG));

        b.chipAll.setOnClickListener(v -> selectFilter(TypeFilter.ALL));
        b.chipBus.setOnClickListener(v -> selectFilter(TypeFilter.BUS));
        b.chipTram.setOnClickListener(v -> selectFilter(TypeFilter.TRAM));
        b.chipTrolleybus.setOnClickListener(v -> selectFilter(TypeFilter.TROLLEYBUS));
        b.chipMarshrutka.setOnClickListener(v -> selectFilter(TypeFilter.MARSHRUTKA));

        applyChipSelection();
        subscribe();
    }

    private void selectFilter(TypeFilter filter) {
        if (currentFilter == filter) return;
        currentFilter = filter;
        applyChipSelection();
        renderOverlays();
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
        if (routesRegistration != null) {
            routesRegistration.remove();
            routesRegistration = null;
        }
        if (stopsRegistration != null) {
            stopsRegistration.remove();
            stopsRegistration = null;
        }
        routesRegistration = repo.listenRoutes(TypeFilter.ALL, (list, error) -> {
            if (b == null) return;
            allRoutes = list != null ? list : new ArrayList<>();
            renderOverlays();
        });
        stopsRegistration = repo.listenStops((list, error) -> {
            if (b == null) return;
            stops = list != null ? list : new ArrayList<>();
            renderOverlays();
        });
    }

    private void renderOverlays() {
        if (b == null) return;
        b.mapView.getOverlays().clear();
        b.mapView.invalidate();
        if (true) return;

        List<TransportRoute> visible = filterRoutes(allRoutes, currentFilter);
        Map<String, TransportStop> stopById = buildStopIndex(stops);

        for (TransportRoute route : visible) {
            Polyline line = buildPolyline(route, stopById);
            if (line != null) {
                b.mapView.getOverlays().add(line);
            }
        }

        Set<String> routeFilterIds = new HashSet<>();
        for (TransportRoute r : visible) {
            if (r.getId() != null) routeFilterIds.add(r.getId());
        }

        Drawable icon = markerIcon();
        for (TransportStop stop : stops) {
            if (stop == null) continue;
            if (currentFilter != TypeFilter.ALL && !stopBelongsToVisibleRoute(stop, routeFilterIds)) continue;
            Marker marker = new Marker(b.mapView);
            marker.setPosition(new GeoPoint(stop.getLat(), stop.getLng()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            if (icon != null) marker.setIcon(icon);
            marker.setTitle(stop.getName() != null ? stop.getName() : "");
            marker.setSnippet(buildRouteSnippet(stop, allRoutes));
            b.mapView.getOverlays().add(marker);
        }

        b.mapView.invalidate();
    }

    private boolean stopBelongsToVisibleRoute(TransportStop stop, Set<String> visibleRouteIds) {
        List<String> routeIds = stop.getRouteIds();
        if (routeIds == null || routeIds.isEmpty()) return false;
        for (String rid : routeIds) {
            if (rid != null && visibleRouteIds.contains(rid)) return true;
        }
        return false;
    }

    private String buildRouteSnippet(TransportStop stop, List<TransportRoute> routes) {
        List<String> routeIds = stop.getRouteIds();
        if (routeIds == null || routeIds.isEmpty() || routes == null) return "";
        Map<String, String> numberById = new HashMap<>();
        for (TransportRoute r : routes) {
            if (r.getId() != null) numberById.put(r.getId(), r.getRouteNumber() != null ? r.getRouteNumber() : "");
        }
        StringBuilder sb = new StringBuilder();
        for (String rid : routeIds) {
            String num = numberById.get(rid);
            if (num == null || num.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(num);
        }
        return sb.toString();
    }

    private List<TransportRoute> filterRoutes(List<TransportRoute> routes, TypeFilter filter) {
        if (filter == null || filter == TypeFilter.ALL) return routes != null ? routes : new ArrayList<>();
        String want;
        switch (filter) {
            case BUS: want = TransportRoute.TYPE_BUS; break;
            case TRAM: want = TransportRoute.TYPE_TRAM; break;
            case TROLLEYBUS: want = TransportRoute.TYPE_TROLLEYBUS; break;
            case MARSHRUTKA: want = TransportRoute.TYPE_MARSHRUTKA; break;
            default: return routes != null ? routes : new ArrayList<>();
        }
        List<TransportRoute> filtered = new ArrayList<>();
        if (routes == null) return filtered;
        for (TransportRoute r : routes) {
            if (want.equals(r.getType())) filtered.add(r);
        }
        return filtered;
    }

    private Map<String, TransportStop> buildStopIndex(List<TransportStop> source) {
        Map<String, TransportStop> map = new HashMap<>();
        if (source == null) return map;
        for (TransportStop s : source) {
            if (s != null && s.getId() != null) map.put(s.getId(), s);
        }
        return map;
    }

    private Polyline buildPolyline(TransportRoute route, Map<String, TransportStop> stopById) {
        List<GeoPoint> points = collectPolylinePoints(route, stopById);
        if (points.size() < 2) return null;
        int color = parseRouteColor(route.getColor());
        Polyline line = new Polyline();
        line.setPoints(points);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setStrokeWidth(8f);
        return line;
    }

    private List<GeoPoint> collectPolylinePoints(TransportRoute route, Map<String, TransportStop> stopById) {
        List<GeoPoint> points = new ArrayList<>();
        List<com.google.firebase.firestore.GeoPoint> stored = route.getPolyline();
        if (stored != null && !stored.isEmpty()) {
            for (com.google.firebase.firestore.GeoPoint gp : stored) {
                if (gp == null) continue;
                points.add(new GeoPoint(gp.getLatitude(), gp.getLongitude()));
            }
            if (points.size() >= 2) return points;
            points.clear();
        }
        List<String> stopIds = route.getStopIds();
        if (stopIds == null || stopIds.isEmpty()) return points;
        for (String id : stopIds) {
            TransportStop s = id != null ? stopById.get(id) : null;
            if (s == null) return new ArrayList<>();
            points.add(new GeoPoint(s.getLat(), s.getLng()));
        }
        return points;
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

    private Drawable markerIcon() {
        Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTintList(d, ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.accent_blue)));
        }
        return d;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b != null) b.mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (b != null) b.mapView.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (routesRegistration != null) {
            routesRegistration.remove();
            routesRegistration = null;
        }
        if (stopsRegistration != null) {
            stopsRegistration.remove();
            stopsRegistration = null;
        }
        if (b != null) {
            heldMapView = b.mapView;
        }
        b = null;
    }

    @Override
    public void onDetach() {
        if (heldMapView != null) {
            heldMapView.onDetach();
            heldMapView = null;
        }
        super.onDetach();
    }
}
