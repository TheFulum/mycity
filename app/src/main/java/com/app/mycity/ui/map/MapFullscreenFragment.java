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
import com.app.mycity.databinding.FragmentMapFullscreenBinding;
import com.app.mycity.util.GeoUtils;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.Marker;

public class MapFullscreenFragment extends Fragment {

    private static final String ARG_LAT   = "lat";
    private static final String ARG_LNG   = "lng";
    private static final String ARG_TITLE = "title";

    public static MapFullscreenFragment newInstance(double lat, double lng, String title) {
        MapFullscreenFragment f = new MapFullscreenFragment();
        Bundle args = new Bundle();
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        args.putString(ARG_TITLE, title);
        f.setArguments(args);
        return f;
    }

    private FragmentMapFullscreenBinding b;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentMapFullscreenBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        double lat   = requireArguments().getDouble(ARG_LAT);
        double lng   = requireArguments().getDouble(ARG_LNG);
        String title = requireArguments().getString(ARG_TITLE, "");

        b.mapView.setTileSource(TileSourceFactory.MAPNIK);
        b.mapView.setMultiTouchControls(true);
        b.mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);

        GeoPoint point = new GeoPoint(lat, lng);
        b.mapView.getController().setZoom((double) GeoUtils.DEFAULT_ZOOM);
        b.mapView.getController().setCenter(point);

        Marker marker = new Marker(b.mapView);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_marker));
        marker.setTitle(title);
        marker.setInfoWindow(null);
        b.mapView.getOverlays().add(marker);
        b.mapView.invalidate();

        b.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        b.btnOpenMaps.setOnClickListener(v -> {
            Intent geo = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng));
            if (geo.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(geo);
            }
        });
    }

    @Override public void onResume()  { super.onResume();  if (b != null) b.mapView.onResume(); }
    @Override public void onPause()   { if (b != null) b.mapView.onPause(); super.onPause(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
