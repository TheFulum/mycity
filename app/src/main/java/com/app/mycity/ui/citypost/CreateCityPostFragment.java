package com.app.mycity.ui.citypost;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.mycity.R;
import com.app.mycity.data.model.CityPost;
import com.app.mycity.data.remote.NominatimClient;
import com.app.mycity.data.remote.NominatimResponse;
import com.app.mycity.data.repository.CityPostRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentCreateCityPostBinding;
import com.app.mycity.databinding.ItemCreatePhotoBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.ui.map.MapFullscreenFragment;
import com.app.mycity.util.CloudinaryManager;
import com.app.mycity.util.GeoUtils;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateCityPostFragment extends Fragment {

    private static final int MAX_PHOTOS = 5;

    private FragmentCreateCityPostBinding b;
    private final List<Uri> selectedPhotos = new ArrayList<>();
    private final CityPostRepository repo = new CityPostRepository();
    private final UserRepository userRepo = new UserRepository();

    private String selectedCategory;
    private Double pickedLat;
    private Double pickedLng;
    private String pickedAddress;
    private boolean noLocation = false;
    private boolean submitting = false;

    private ActivityResultLauncher<String[]> galleryLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentCreateCityPostBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        registerLaunchers();
        setupCategoryChips();
        setupClicks();
        setupLocationResultListener();
        rebuildPhotoRow();
        refreshLocationUi();
    }

    private void registerLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null) return;
                    for (Uri u : uris) {
                        if (selectedPhotos.size() >= MAX_PHOTOS) {
                            toast(getString(R.string.create_city_validation_photos));
                            break;
                        }
                        try {
                            requireContext().getContentResolver()
                                    .takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) { }
                        selectedPhotos.add(u);
                    }
                    rebuildPhotoRow();
                });
    }

    private void setupCategoryChips() {
        b.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedCategory = null;
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chip_nature) selectedCategory = CityPost.CATEGORY_NATURE;
            else if (id == R.id.chip_place) selectedCategory = CityPost.CATEGORY_PLACE;
            else if (id == R.id.chip_venue) selectedCategory = CityPost.CATEGORY_VENUE;
            else if (id == R.id.chip_history) selectedCategory = CityPost.CATEGORY_HISTORY;
            else selectedCategory = null;
        });
    }

    private void setupClicks() {
        b.btnAddPhoto.setOnClickListener(v -> {
            if (selectedPhotos.size() >= MAX_PHOTOS) {
                toast(getString(R.string.create_city_validation_photos));
                return;
            }
            galleryLauncher.launch(new String[]{"image/*"});
        });
        b.btnPickLocation.setOnClickListener(v -> openPicker());
        b.switchNoLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            noLocation = isChecked;
            if (isChecked) {
                pickedLat = null;
                pickedLng = null;
                pickedAddress = null;
            }
            refreshLocationUi();
        });
        b.btnSubmit.setOnClickListener(v -> submit());
    }

    private void setupLocationResultListener() {
        getParentFragmentManager().setFragmentResultListener(
                MapFullscreenFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (key, bundle) -> {
                    double lat = bundle.getDouble(MapFullscreenFragment.RESULT_LAT);
                    double lng = bundle.getDouble(MapFullscreenFragment.RESULT_LNG);
                    pickedLat = lat;
                    pickedLng = lng;
                    pickedAddress = null;
                    noLocation = false;
                    b.switchNoLocation.setChecked(false);
                    refreshLocationUi();
                    resolveAddress(lat, lng);
                });
    }

    private void openPicker() {
        double lat = pickedLat != null ? pickedLat : GeoUtils.MOGILEV_LAT;
        double lng = pickedLng != null ? pickedLng : GeoUtils.MOGILEV_LNG;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openMapPicker(lat, lng);
        }
    }

    private void resolveAddress(double lat, double lng) {
        NominatimClient.get().reverse(NominatimClient.userAgent(), lat, lng)
                .enqueue(new Callback<NominatimResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<NominatimResponse> call, @NonNull Response<NominatimResponse> response) {
                        if (b == null) return;
                        NominatimResponse body = response.body();
                        String addr = body != null ? body.shortAddress() : null;
                        if (addr != null && !addr.isEmpty()) {
                            pickedAddress = addr;
                            refreshLocationUi();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<NominatimResponse> call, @NonNull Throwable t) { }
                });
    }

    private void refreshLocationUi() {
        if (b == null) return;
        if (noLocation) {
            b.tvAddress.setText(R.string.create_city_no_location);
            b.btnPickLocation.setEnabled(false);
            return;
        }
        b.btnPickLocation.setEnabled(true);
        if (pickedLat == null || pickedLng == null) {
            b.tvAddress.setText(R.string.create_city_location_empty);
            return;
        }
        String text = pickedAddress != null && !pickedAddress.isEmpty()
                ? pickedAddress
                : GeoUtils.formatCoords(pickedLat, pickedLng);
        b.tvAddress.setText(text);
    }

    private void rebuildPhotoRow() {
        if (b == null) return;
        b.photoRow.removeAllViews();
        for (int i = 0; i < selectedPhotos.size(); i++) {
            Uri uri = selectedPhotos.get(i);
            ItemCreatePhotoBinding item = ItemCreatePhotoBinding.inflate(
                    LayoutInflater.from(requireContext()), b.photoRow, false);
            Glide.with(item.ivPhoto).load(uri).centerCrop().into(item.ivPhoto);
            final int idx = i;
            item.btnRemove.setOnClickListener(v -> {
                if (idx < selectedPhotos.size()) {
                    selectedPhotos.remove(idx);
                    rebuildPhotoRow();
                }
            });
            b.photoRow.addView(item.getRoot());
        }
    }

    private void submit() {
        if (submitting) return;

        String title = b.etTitle.getText() == null ? "" : b.etTitle.getText().toString().trim();
        String description = b.etDescription.getText() == null ? "" : b.etDescription.getText().toString().trim();

        boolean ok = true;
        if (TextUtils.isEmpty(title)) {
            b.tilTitle.setError(getString(R.string.create_city_validation_title));
            ok = false;
        } else {
            b.tilTitle.setError(null);
        }
        if (TextUtils.isEmpty(description)) {
            b.tilDescription.setError(getString(R.string.create_city_validation_description));
            ok = false;
        } else {
            b.tilDescription.setError(null);
        }
        if (selectedCategory == null) {
            toast(getString(R.string.create_city_validation_category));
            ok = false;
        }
        if (!noLocation && (pickedLat == null || pickedLng == null)) {
            toast(getString(R.string.create_city_validation_location));
            ok = false;
        }
        if (selectedPhotos.size() > MAX_PHOTOS) {
            toast(getString(R.string.create_city_validation_photos));
            ok = false;
        }
        if (!ok) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            toast("Требуется вход");
            return;
        }

        setLoading(true);
        final String uid = user.getUid();
        userRepo.get(uid).addOnSuccessListener(snap -> {
            String name = snap != null && snap.exists() ? snap.getString("displayName") : null;
            if (name == null || name.isEmpty()) {
                name = user.getDisplayName() != null ? user.getDisplayName()
                        : (user.getEmail() != null ? user.getEmail() : "Пользователь");
            }
            String avatarUrl = snap != null && snap.exists() ? snap.getString("avatarUrl") : null;
            proceedSubmit(uid, name, avatarUrl, title, description);
        }).addOnFailureListener(e -> {
            String fallback = user.getDisplayName() != null ? user.getDisplayName()
                    : (user.getEmail() != null ? user.getEmail() : "Пользователь");
            proceedSubmit(uid, fallback, null, title, description);
        });
    }

    private void proceedSubmit(String uid, String authorName, String avatarUrl, String title, String description) {
        CityPost post = new CityPost();
        String postId = repo.newDocRef().getId();
        post.setId(postId);
        post.setAuthorId(uid);
        post.setAuthorName(authorName);
        post.setAuthorAvatarUrl(avatarUrl);
        post.setTitle(title);
        post.setDescription(description);
        post.setCategory(selectedCategory);
        post.setCreatedAt(new Date());
        post.setApproved(Boolean.TRUE);
        post.setApprovedAt(new Date());
        post.setApprovedBy(uid);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setLikedBy(new ArrayList<>());
        if (!noLocation && pickedLat != null && pickedLng != null) {
            post.setLat(pickedLat);
            post.setLng(pickedLng);
            post.setAddress(pickedAddress);
        }
        uploadPhotosSequentially(post, uid, 0, new ArrayList<>());
    }

    private void uploadPhotosSequentially(CityPost post, String uid, int index, List<String> urls) {
        if (index >= selectedPhotos.size()) {
            post.setPhotoUrls(urls);
            repo.save(post).addOnSuccessListener(v -> {
                if (!isAdded() || b == null) return;
                setLoading(false);
                toast(getString(R.string.create_city_success));
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).popHost();
                }
            }).addOnFailureListener(e -> {
                if (!isAdded() || b == null) return;
                setLoading(false);
                toast(getString(R.string.create_city_error_save, friendlyMessage(e)));
            });
            return;
        }
        String folder = "city_posts/" + uid;
        CloudinaryManager.upload(selectedPhotos.get(index), folder,
                new CloudinaryManager.UploadResultCallback() {
                    @Override
                    public void onSuccess(String secureUrl) {
                        if (!isAdded()) return;
                        urls.add(secureUrl);
                        uploadPhotosSequentially(post, uid, index + 1, urls);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isAdded() || b == null) return;
                        setLoading(false);
                        toast(getString(R.string.create_city_error_upload, errorMessage));
                    }
                });
    }

    private String friendlyMessage(Exception e) {
        if (e == null) return "";
        String msg = e.getLocalizedMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    private void setLoading(boolean loading) {
        submitting = loading;
        if (b == null) return;
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnSubmit.setEnabled(!loading);
        b.btnAddPhoto.setEnabled(!loading);
        b.btnPickLocation.setEnabled(!loading && !noLocation);
        b.switchNoLocation.setEnabled(!loading);
    }

    private void toast(String msg) {
        Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
