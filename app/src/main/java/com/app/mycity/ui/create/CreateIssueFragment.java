package com.app.mycity.ui.create;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.model.Issue;
import com.app.mycity.data.remote.NominatimClient;
import com.app.mycity.data.remote.NominatimResponse;
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentCreateIssueBinding;
import com.app.mycity.databinding.ItemCreatePhotoBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.CloudinaryManager;
import com.app.mycity.util.GeoUtils;
import com.app.mycity.util.SessionManager;
import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateIssueFragment extends Fragment {

    private static final int MAX_PHOTOS = 5;

    private FragmentCreateIssueBinding b;
    private final List<Uri> selectedUris = new ArrayList<>();
    private Uri cameraUri;
    private Double pendingLat, pendingLng;
    private String pendingAddress;
    private boolean isGuest;

    private FusedLocationProviderClient locationClient;
    private final IssueRepository issueRepo = new IssueRepository();
    private final UserRepository userRepo = new UserRepository();

    private ActivityResultLauncher<String[]> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> locationPermLauncher;
    private ActivityResultLauncher<String> cameraPermLauncher;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentCreateIssueBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        isGuest = new SessionManager(requireContext()).isGuest()
                || FirebaseAuth.getInstance().getCurrentUser() == null;
        b.guestBlock.setVisibility(isGuest ? View.VISIBLE : View.GONE);

        registerLaunchers();

        b.btnCamera.setOnClickListener(v -> requestCamera());
        b.btnGallery.setOnClickListener(v -> galleryLauncher.launch(new String[]{"image/*"}));
        b.btnPickLocation.setOnClickListener(v -> requestLocation());
        b.btnSubmit.setOnClickListener(v -> submit());

        requestLocation();
    }

    private void registerLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null) return;
                    for (Uri u : uris) {
                        if (selectedUris.size() >= MAX_PHOTOS) break;
                        try {
                            requireContext().getContentResolver()
                                    .takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) { }
                        selectedUris.add(u);
                    }
                    rebuildPhotoRow();
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success != null && success && cameraUri != null) {
                        if (selectedUris.size() < MAX_PHOTOS) selectedUris.add(cameraUri);
                        rebuildPhotoRow();
                    }
                });

        cameraPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) launchCamera();
                    else toast("Нужно разрешение камеры");
                });

        locationPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), perms -> {
                    Boolean fine = perms.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarse = perms.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if ((fine != null && fine) || (coarse != null && coarse)) fetchLocation();
                    else useDefaultLocation();
                });
    }

    private void requestCamera() {
        if (selectedUris.size() >= MAX_PHOTOS) {
            toast("Максимум " + MAX_PHOTOS + " фото");
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "images");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(cameraUri);
        } catch (Exception e) {
            toast("Не удалось открыть камеру");
        }
    }

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else {
            locationPermLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressWarnings("MissingPermission")
    private void fetchLocation() {
        locationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                setCoords(loc.getLatitude(), loc.getLongitude());
            } else {
                useDefaultLocation();
            }
        }).addOnFailureListener(e -> useDefaultLocation());
    }

    private void useDefaultLocation() {
        setCoords(GeoUtils.MOGILEV_LAT, GeoUtils.MOGILEV_LNG);
        toast("Использованы координаты центра города");
    }

    private void setCoords(double lat, double lng) {
        pendingLat = lat;
        pendingLng = lng;
        b.tvAddress.setText(GeoUtils.formatCoords(lat, lng));
        NominatimClient.get().reverse(NominatimClient.userAgent(), lat, lng)
                .enqueue(new Callback<NominatimResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<NominatimResponse> call,
                                           @NonNull Response<NominatimResponse> response) {
                        if (b == null) return;
                        NominatimResponse body = response.body();
                        if (body != null) {
                            pendingAddress = body.shortAddress();
                            if (pendingAddress != null) b.tvAddress.setText(pendingAddress);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<NominatimResponse> call, @NonNull Throwable t) { }
                });
    }

    private void rebuildPhotoRow() {
        b.photoRow.removeAllViews();
        for (int i = 0; i < selectedUris.size(); i++) {
            Uri uri = selectedUris.get(i);
            ItemCreatePhotoBinding item = ItemCreatePhotoBinding
                    .inflate(LayoutInflater.from(requireContext()), b.photoRow, false);
            Glide.with(item.ivPhoto).load(uri).centerCrop().into(item.ivPhoto);
            final int idx = i;
            item.btnRemove.setOnClickListener(v -> {
                selectedUris.remove(idx);
                rebuildPhotoRow();
            });
            b.photoRow.addView(item.getRoot());
        }
    }

    private void submit() {
        String title = b.etTitle.getText().toString().trim();
        String desc = b.etDescription.getText().toString().trim();

        boolean ok = true;
        if (title.length() < 5) { b.tilTitle.setError("Минимум 5 символов"); ok = false; }
        else b.tilTitle.setError(null);
        if (desc.isEmpty()) { b.tilDescription.setError(getString(R.string.error_required)); ok = false; }
        else b.tilDescription.setError(null);
        if (selectedUris.isEmpty()) { toast(getString(R.string.error_no_photo)); ok = false; }
        if (pendingLat == null || pendingLng == null) { toast(getString(R.string.error_no_location)); ok = false; }

        String guestName = null, guestContact = null;
        if (isGuest) {
            guestName = b.etName.getText().toString().trim();
            guestContact = b.etContact.getText().toString().trim();
            if (TextUtils.isEmpty(guestName)) { b.tilName.setError(getString(R.string.error_required)); ok = false; }
            else b.tilName.setError(null);
            if (TextUtils.isEmpty(guestContact)) { b.tilContact.setError(getString(R.string.error_required)); ok = false; }
            else b.tilContact.setError(null);
        }

        if (!ok) return;

        setLoading(true);

        Issue issue = new Issue();
        String issueId = issueRepo.newDocRef().getId();
        issue.setId(issueId);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !isGuest) {
            issue.setAuthorId(user.getUid());
            issue.setAuthorName(user.getDisplayName());
        } else {
            issue.setAuthorName(guestName);
            issue.setAuthorContact(guestContact);
        }
        issue.setTitle(title);
        issue.setDescription(desc);
        issue.setLat(pendingLat);
        issue.setLng(pendingLng);
        issue.setAddress(pendingAddress != null ? pendingAddress : GeoUtils.formatCoords(pendingLat, pendingLng));
        issue.setStatus(Issue.STATUS_ACTIVE);
        issue.setCreatedAt(new Date());

        uploadPhotosSequentially(issue, 0, new ArrayList<>(), user);
    }

    private void uploadPhotosSequentially(Issue issue, int index, List<String> urls, FirebaseUser user) {
        if (index >= selectedUris.size()) {
            issue.setPhotoUrls(urls);
            issueRepo.save(issue).addOnSuccessListener(v -> {
                if (user != null && !isGuest) userRepo.incrementIssueCount(user.getUid(), 1);
                setLoading(false);
                toast("Заявка создана");
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).popHost();
            }).addOnFailureListener(e -> {
                setLoading(false);
                toast("Ошибка сохранения: " + e.getMessage());
            });
            return;
        }
        CloudinaryManager.upload(selectedUris.get(index), "issues/" + issue.getId(),
                new CloudinaryManager.UploadResultCallback() {
                    @Override
                    public void onSuccess(String secureUrl) {
                        urls.add(secureUrl);
                        uploadPhotosSequentially(issue, index + 1, urls, user);
                    }
                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        toast("Ошибка загрузки фото: " + errorMessage);
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (b == null) return;
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnSubmit.setEnabled(!loading);
    }

    private void toast(String msg) {
        Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); b = null; }
}
