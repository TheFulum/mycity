package com.app.mycity.ui.map;

import android.graphics.drawable.Drawable;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.model.Issue;
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.databinding.FragmentMapBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.GeoUtils;
import com.bumptech.glide.Glide;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.Marker;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment {

    private FragmentMapBinding b;
    private final IssueRepository repo = new IssueRepository();
    private com.google.firebase.firestore.ListenerRegistration listener;
    private FusedLocationProviderClient locationClient;
    private final List<Issue> currentIssues = new ArrayList<>();
    private ActivityResultLauncher<String[]> locationPermLauncher;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentMapBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        locationPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                perms -> {
                    Boolean fine = perms.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarse = perms.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if ((fine != null && fine) || (coarse != null && coarse)) {
                        goToMyLocation();
                    } else {
                        Toast.makeText(requireContext(), "Нужно разрешение на геолокацию", Toast.LENGTH_SHORT).show();
                    }
                });

        b.map.setTileSource(TileSourceFactory.MAPNIK);
        b.map.setMultiTouchControls(true);
        b.map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        b.map.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
        b.map.getController().setCenter(new GeoPoint(GeoUtils.MOGILEV_LAT, GeoUtils.MOGILEV_LNG));

        b.popup.setOnClickListener(v -> { /* consume */ });
        b.popup.setVisibility(View.GONE);
        b.btnMyLocation.setOnClickListener(v -> requestMyLocation());
        b.btnAllIssues.setOnClickListener(v -> centerOnAllIssues());
        b.btnZoomIn.setOnClickListener(v -> b.map.getController().zoomIn());
        b.btnZoomOut.setOnClickListener(v -> b.map.getController().zoomOut());

        subscribe();
    }

    private void subscribe() {
        if (listener != null) listener.remove();
        listener = repo.listenPublic(IssueRepository.SortField.DATE, false,
                IssueRepository.StatusFilter.ALL, (list, err) -> {
                    if (b == null) return;
                    currentIssues.clear();
                    currentIssues.addAll(list);
                    renderMarkers(list);
                });
    }

    private void renderMarkers(List<Issue> issues) {
        b.map.getOverlays().clear();
        for (Issue issue : issues) {
            Marker marker = new Marker(b.map);
            marker.setPosition(new GeoPoint(issue.getLat(), issue.getLng()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(markerIcon(issue));
            marker.setTitle(issue.getTitle());
            marker.setOnMarkerClickListener((m, mapView) -> {
                showPopup(issue);
                return true;
            });
            b.map.getOverlays().add(marker);
        }
        b.map.invalidate();
    }

    private void requestMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            goToMyLocation();
        } else {
            locationPermLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressWarnings("MissingPermission")
    private void goToMyLocation() {
        locationClient.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc == null || b == null) {
                        Toast.makeText(requireContext(), "Не удалось определить местоположение", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    GeoPoint me = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                    b.map.getController().setCenter(me);
                    b.map.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
                    b.map.invalidate();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Не удалось определить местоположение", Toast.LENGTH_SHORT).show());
    }

    private void centerOnAllIssues() {
        if (b == null || currentIssues.isEmpty()) {
            Toast.makeText(requireContext(), "Нет заявок для отображения", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentIssues.size() == 1) {
            Issue one = currentIssues.get(0);
            b.map.getController().setCenter(new GeoPoint(one.getLat(), one.getLng()));
            b.map.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
            b.map.invalidate();
            return;
        }
        double north = -90;
        double south = 90;
        double east = -180;
        double west = 180;
        for (Issue issue : currentIssues) {
            north = Math.max(north, issue.getLat());
            south = Math.min(south, issue.getLat());
            east = Math.max(east, issue.getLng());
            west = Math.min(west, issue.getLng());
        }
        BoundingBox box = new BoundingBox(north, east, south, west);
        b.map.zoomToBoundingBox(box, true, 120);
    }

    private Drawable markerIcon(Issue issue) {
        int res = (issue != null && issue.isResolved()) ? R.drawable.ic_marker_red : R.drawable.ic_marker;
        Drawable d = ContextCompat.getDrawable(requireContext(), res);
        if (d == null) {
            d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker);
        }
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
        }
        return d;
    }

    private void showPopup(Issue issue) {
        b.popup.setVisibility(View.VISIBLE);
        b.popupTitle.setText(issue.getTitle());
        b.popupAddress.setText(GeoUtils.displayAddress(issue.getAddress()));
        b.popupDescription.setText(issue.getDescription());
        if (issue.getPhotoUrls() != null && !issue.getPhotoUrls().isEmpty()) {
            b.popupImage.setVisibility(View.VISIBLE);
            Glide.with(b.popupImage)
                    .load(issue.getPhotoUrls().get(0))
                    .placeholder(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(b.popupImage);
        } else {
            b.popupImage.setVisibility(View.GONE);
        }
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
