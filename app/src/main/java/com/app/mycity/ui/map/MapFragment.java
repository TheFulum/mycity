package com.app.mycity.ui.map;

import android.graphics.drawable.BitmapDrawable;
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
import com.app.mycity.data.model.Issue;
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.databinding.FragmentMapBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.GeoUtils;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

public class MapFragment extends Fragment {

    private FragmentMapBinding b;
    private final IssueRepository repo = new IssueRepository();
    private com.google.firebase.firestore.ListenerRegistration listener;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentMapBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.map.setTileSource(TileSourceFactory.MAPNIK);
        b.map.setMultiTouchControls(true);
        b.map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        b.map.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
        b.map.getController().setCenter(new GeoPoint(GeoUtils.MOGILEV_LAT, GeoUtils.MOGILEV_LNG));

        b.popup.setOnClickListener(v -> { /* consume */ });
        b.popup.setVisibility(View.GONE);

        subscribe();
    }

    private void subscribe() {
        if (listener != null) listener.remove();
        listener = repo.listen(IssueRepository.SortField.DATE, false,
                IssueRepository.StatusFilter.ACTIVE, (list, err) -> {
                    if (b == null) return;
                    renderMarkers(list);
                });
    }

    private void renderMarkers(List<Issue> issues) {
        b.map.getOverlays().clear();
        Drawable icon = tintedMarker();
        for (Issue issue : issues) {
            Marker marker = new Marker(b.map);
            marker.setPosition(new GeoPoint(issue.getLat(), issue.getLng()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(icon);
            marker.setTitle(issue.getTitle());
            marker.setOnMarkerClickListener((m, mapView) -> {
                showPopup(issue);
                return true;
            });
            b.map.getOverlays().add(marker);
        }
        b.map.invalidate();
    }

    private Drawable tintedMarker() {
        Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
        }
        return d;
    }

    private void showPopup(Issue issue) {
        b.popup.setVisibility(View.VISIBLE);
        b.popupTitle.setText(issue.getTitle());
        b.popupAddress.setText(com.app.mycity.util.GeoUtils.displayAddress(issue.getAddress()));
        b.popupDescription.setText(issue.getDescription());
        b.popupOpen.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openIssueDetail(issue.getId());
            }
        });
    }

    @Override public void onResume() { super.onResume(); if (b != null) b.map.onResume(); }
    @Override public void onPause() { super.onPause(); if (b != null) b.map.onPause(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) { listener.remove(); listener = null; }
        b = null;
    }
}
