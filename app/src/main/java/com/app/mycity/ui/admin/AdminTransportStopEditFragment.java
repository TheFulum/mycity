package com.app.mycity.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.model.TransportStop;
import com.app.mycity.data.remote.NominatimClient;
import com.app.mycity.data.remote.NominatimResponse;
import com.app.mycity.data.repository.TransportRepository;
import com.app.mycity.databinding.FragmentAdminTransportStopEditBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.ui.map.MapFullscreenFragment;
import com.app.mycity.util.GeoUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminTransportStopEditFragment extends Fragment {

    private static final String ARG_ID = "stop_id";

    public static AdminTransportStopEditFragment newInstance(@Nullable String stopId) {
        AdminTransportStopEditFragment f = new AdminTransportStopEditFragment();
        Bundle args = new Bundle();
        if (stopId != null) args.putString(ARG_ID, stopId);
        f.setArguments(args);
        return f;
    }

    private FragmentAdminTransportStopEditBinding b;
    private final TransportRepository repo = new TransportRepository();
    private TransportStop stop;
    private Double pendingLat, pendingLng;
    private String pendingAddress;
    private boolean coordsPicked;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentAdminTransportStopEditBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).popHost();
            }
        });
        b.btnPickLocation.setOnClickListener(v -> openPicker());
        b.btnSave.setOnClickListener(v -> save());

        getParentFragmentManager().setFragmentResultListener(
                MapFullscreenFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (key, bundle) -> {
                    double lat = bundle.getDouble(MapFullscreenFragment.RESULT_LAT);
                    double lng = bundle.getDouble(MapFullscreenFragment.RESULT_LNG);
                    setCoords(lat, lng);
                });

        String stopId = getArguments() != null ? getArguments().getString(ARG_ID) : null;
        if (stopId != null) {
            b.tvScreenTitle.setText(R.string.admin_transport_edit_stop);
            loadStop(stopId);
        } else {
            b.tvScreenTitle.setText(R.string.admin_transport_add_stop);
            stop = new TransportStop();
        }
    }

    private void loadStop(String id) {
        setLoading(true);
        repo.getStop(id).addOnSuccessListener(snap -> {
            if (b == null) return;
            setLoading(false);
            if (snap == null || !snap.exists()) {
                toast(getString(R.string.admin_transport_stop_not_found));
                popBack();
                return;
            }
            stop = snap.toObject(TransportStop.class);
            if (stop == null) {
                stop = new TransportStop();
                stop.setId(id);
            }
            bindStop();
        }).addOnFailureListener(e -> {
            setLoading(false);
            toast("Ошибка: " + e.getMessage());
            popBack();
        });
    }

    private void bindStop() {
        if (stop == null) return;
        b.etName.setText(stop.getName() != null ? stop.getName() : "");
        pendingLat = stop.getLat();
        pendingLng = stop.getLng();
        coordsPicked = pendingLat != 0d || pendingLng != 0d;
        if (coordsPicked) {
            b.tvAddress.setText(GeoUtils.formatCoords(pendingLat, pendingLng));
            resolveAddress(pendingLat, pendingLng);
        }
    }

    private void openPicker() {
        double lat = pendingLat != null ? pendingLat : GeoUtils.MOGILEV_LAT;
        double lng = pendingLng != null ? pendingLng : GeoUtils.MOGILEV_LNG;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openMapPicker(lat, lng);
        }
    }

    private void setCoords(double lat, double lng) {
        pendingLat = lat;
        pendingLng = lng;
        coordsPicked = true;
        pendingAddress = null;
        if (b != null) {
            b.tvAddress.setText(GeoUtils.formatCoords(lat, lng));
        }
        resolveAddress(lat, lng);
    }

    private void resolveAddress(double lat, double lng) {
        NominatimClient.get().reverse(NominatimClient.userAgent(), lat, lng)
                .enqueue(new Callback<NominatimResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<NominatimResponse> call,
                                           @NonNull Response<NominatimResponse> response) {
                        if (b == null) return;
                        NominatimResponse body = response.body();
                        String addr = body != null ? body.shortAddress() : null;
                        if (addr != null && !addr.isEmpty()) {
                            pendingAddress = addr;
                            b.tvAddress.setText(addr + " (" + GeoUtils.formatCoords(lat, lng) + ")");
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<NominatimResponse> call, @NonNull Throwable t) { }
                });
    }

    private void save() {
        if (b == null) return;
        String name = b.etName.getText() != null ? b.etName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            b.tilName.setError(getString(R.string.admin_transport_validation_required));
            return;
        }
        b.tilName.setError(null);
        if (pendingLat == null || pendingLng == null || !coordsPicked) {
            toast(getString(R.string.admin_transport_validation_required));
            return;
        }

        if (stop == null) stop = new TransportStop();
        stop.setName(name);
        stop.setLat(pendingLat);
        stop.setLng(pendingLng);

        setLoading(true);
        repo.saveStop(stop)
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    toast("Сохранено");
                    popBack();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast("Ошибка: " + e.getMessage());
                });
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
        b = null;
    }
}
