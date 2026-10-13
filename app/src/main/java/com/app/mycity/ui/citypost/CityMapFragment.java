package com.app.mycity.ui.citypost;

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
import com.app.mycity.data.model.CityPost;
import com.app.mycity.data.repository.CityPostRepository;
import com.app.mycity.data.repository.CityPostRepository.CategoryFilter;
import com.app.mycity.data.repository.CityPostRepository.SortField;
import com.app.mycity.databinding.FragmentCityMapBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.GeoUtils;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.Marker;

public class CityMapFragment extends Fragment {

    private FragmentCityMapBinding b;
    private final CityPostRepository repo = new CityPostRepository();
    private ListenerRegistration registration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentCityMapBinding.inflate(inflater, container, false);
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
        registration = repo.listen(SortField.DATE, false, CategoryFilter.ALL, (list, error) -> {
            if (b == null) return;
            if (error != null) {
                Toast.makeText(requireContext(), R.string.city_load_error, Toast.LENGTH_SHORT).show();
            }
            renderMarkers(list);
        });
    }

    private void renderMarkers(java.util.List<CityPost> posts) {
        if (b == null) return;
        b.mapView.getOverlays().clear();
        if (posts == null) {
            b.mapView.invalidate();
            return;
        }
        Drawable icon = markerIcon();
        for (CityPost post : posts) {
            if (post == null || !post.hasLocation()) continue;
            if (post.getLat() == null || post.getLng() == null) continue;
            Marker marker = new Marker(b.mapView);
            marker.setPosition(new GeoPoint(post.getLat(), post.getLng()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            if (icon != null) marker.setIcon(icon);
            marker.setTitle(post.getTitle() != null ? post.getTitle() : "");
            marker.setOnMarkerClickListener((m, mapView) -> {
                if (post.getId() != null && getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openCityPostDetail(post.getId());
                }
                return true;
            });
            b.mapView.getOverlays().add(marker);
        }
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
        if (b != null) b.mapView.onDetach();
        b = null;
    }
}
