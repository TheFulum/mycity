package com.app.mycity.ui.news;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.model.NewsItem;
import com.app.mycity.data.repository.NewsRepository;
import com.app.mycity.data.repository.NewsRepository.CategoryFilter;
import com.app.mycity.databinding.FragmentNewsMapBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.GeoUtils;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

public class NewsMapFragment extends Fragment {

    private FragmentNewsMapBinding b;
    private final NewsRepository repo = new NewsRepository();
    private ListenerRegistration registration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentNewsMapBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.mapView.setTileSource(TileSourceFactory.MAPNIK);
        b.mapView.setMultiTouchControls(true);
        b.mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        b.mapView.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
        b.mapView.getController().setCenter(new GeoPoint(GeoUtils.MOGILEV_LAT, GeoUtils.MOGILEV_LNG));

        subscribe();
    }

    private void subscribe() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        registration = repo.listen(CategoryFilter.ALL, (list, error) -> {
            if (b == null) return;
            if (error != null) {
                Toast.makeText(requireContext(), R.string.news_load_error, Toast.LENGTH_SHORT).show();
            }
            renderMarkers(list);
        });
    }

    private void renderMarkers(List<NewsItem> items) {
        if (b == null) return;
        b.mapView.getOverlays().clear();
        int placedCount = 0;
        if (items != null) {
            Drawable icon = markerIcon();
            for (NewsItem item : items) {
                if (item == null) continue;
                if (item.getLat() == null || item.getLng() == null) continue;
                Marker marker = new Marker(b.mapView);
                marker.setPosition(new GeoPoint(item.getLat(), item.getLng()));
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                if (icon != null) marker.setIcon(icon);
                marker.setTitle(item.getTitle() != null ? item.getTitle() : "");
                final String id = item.getId();
                marker.setOnMarkerClickListener((m, mapView) -> {
                    if (id != null && getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).openNewsDetail(id);
                    }
                    return true;
                });
                b.mapView.getOverlays().add(marker);
                placedCount++;
            }
        }
        b.tvNoGeo.setVisibility(placedCount == 0 ? View.VISIBLE : View.GONE);
        b.mapView.invalidate();
    }

    private Drawable markerIcon() {
        Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
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
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        b = null;
    }

    @Override
    public void onDetach() {
        if (b != null && b.mapView != null) b.mapView.onDetach();
        super.onDetach();
    }
}
