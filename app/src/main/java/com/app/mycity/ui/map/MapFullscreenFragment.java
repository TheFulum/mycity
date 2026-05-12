package com.app.mycity.ui.map;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.remote.NominatimAddressResolver;
import com.app.mycity.databinding.FragmentMapFullscreenBinding;
import com.app.mycity.util.GeoUtils;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class MapFullscreenFragment extends Fragment {

    public static final String RESULT_KEY = "map_pick";
    public static final String RESULT_LAT = "lat";
    public static final String RESULT_LNG = "lng";

    private static final String ARG_LAT      = "lat";
    private static final String ARG_LNG      = "lng";
    private static final String ARG_TITLE    = "title";
    private static final String ARG_EDITABLE = "editable";

    public static MapFullscreenFragment newInstance(double lat, double lng, String title) {
        return newInstance(lat, lng, title, false);
    }

    public static MapFullscreenFragment newInstanceEditable(double lat, double lng, String title) {
        return newInstance(lat, lng, title, true);
    }

    private static MapFullscreenFragment newInstance(double lat, double lng, String title, boolean editable) {
        MapFullscreenFragment f = new MapFullscreenFragment();
        Bundle args = new Bundle();
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        args.putString(ARG_TITLE, title);
        args.putBoolean(ARG_EDITABLE, editable);
        f.setArguments(args);
        return f;
    }

    private FragmentMapFullscreenBinding b;
    private Marker marker;
    private double currentLat;
    private double currentLng;
    private boolean editable;
    private boolean picked;
    private long addressSeq;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentMapFullscreenBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        currentLat = requireArguments().getDouble(ARG_LAT);
        currentLng = requireArguments().getDouble(ARG_LNG);
        editable   = requireArguments().getBoolean(ARG_EDITABLE, false);
        String title = requireArguments().getString(ARG_TITLE, "");

        b.mapView.setTileSource(TileSourceFactory.MAPNIK);
        b.mapView.setMultiTouchControls(true);
        b.mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);

        GeoPoint point = new GeoPoint(currentLat, currentLng);
        b.mapView.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
        b.mapView.getController().setCenter(point);

        marker = new Marker(b.mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker));
        marker.setTitle(title);
        marker.setInfoWindow(null);
        b.mapView.getOverlays().add(marker);

        if (editable) {
            MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
                @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                    currentLat = p.getLatitude();
                    currentLng = p.getLongitude();
                    marker.setPosition(p);
                    picked = true;
                    b.mapView.invalidate();
                    resolveAddress(currentLat, currentLng);
                    return true;
                }
                @Override public boolean longPressHelper(GeoPoint p) { return false; }
            });
            b.mapView.getOverlays().add(0, eventsOverlay);
        }

        b.mapView.invalidate();
        resolveAddress(currentLat, currentLng);

        b.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        b.btnOpenMaps.setOnClickListener(v -> {
            Intent geo = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("geo:" + currentLat + "," + currentLng
                            + "?q=" + currentLat + "," + currentLng));
            if (geo.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(geo);
            }
        });
    }

    private void resolveAddress(double lat, double lng) {
        if (b == null) return;
        b.tvAddress.setText("Определяем адрес…");
        final long seq = ++addressSeq;
        NominatimAddressResolver.resolve(
                b.getRoot(),
                requireContext().getApplicationContext(),
                seq,
                () -> addressSeq,
                lat,
                lng,
                new NominatimAddressResolver.AddressListener() {
                    @Override
                    public void onResolved(@NonNull String address) {
                        if (b == null || seq != addressSeq) {
                            return;
                        }
                        b.tvAddress.setText(address);
                    }

                    @Override
                    public void onNotFound() {
                        if (b == null || seq != addressSeq) {
                            return;
                        }
                        b.tvAddress.setText("Адрес не найден");
                    }
                });
    }

    @Override public void onResume()  { super.onResume();  if (b != null) b.mapView.onResume(); }
    @Override public void onPause()   { if (b != null) b.mapView.onPause(); super.onPause(); }

    @Override
    public void onDestroyView() {
        if (editable && picked) {
            Bundle result = new Bundle();
            result.putDouble(RESULT_LAT, currentLat);
            result.putDouble(RESULT_LNG, currentLng);
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        }
        super.onDestroyView();
        b = null;
        marker = null;
    }
}
