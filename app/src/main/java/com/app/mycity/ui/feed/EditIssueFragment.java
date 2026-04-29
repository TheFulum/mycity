package com.app.mycity.ui.feed;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.databinding.FragmentEditIssueBinding;
import com.app.mycity.databinding.ItemCreatePhotoBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.CloudinaryManager;
import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EditIssueFragment extends Fragment {

    private static final String ARG_ISSUE_ID = "issue_id";
    private static final int MAX_PHOTOS = 5;

    public static EditIssueFragment newInstance(String issueId) {
        EditIssueFragment f = new EditIssueFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ISSUE_ID, issueId);
        f.setArguments(args);
        return f;
    }

    private FragmentEditIssueBinding b;
    private final IssueRepository issueRepo = new IssueRepository();

    private Issue currentIssue;
    private final List<String> keptUrls = new ArrayList<>();
    private final List<Uri> newUris = new ArrayList<>();
    private Uri cameraUri;

    private ActivityResultLauncher<String[]> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> cameraPermLauncher;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentEditIssueBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String issueId = requireArguments().getString(ARG_ISSUE_ID);

        registerLaunchers();

        b.btnCamera.setOnClickListener(v -> requestCamera());
        b.btnGallery.setOnClickListener(v -> galleryLauncher.launch(new String[]{"image/*"}));
        b.btnSubmit.setOnClickListener(v -> submit());

        issueRepo.get(issueId).addOnSuccessListener(snap -> {
            if (b == null || snap == null || !snap.exists()) return;
            currentIssue = snap.toObject(Issue.class);
            if (currentIssue == null) return;
            currentIssue.setId(snap.getId());

            b.etTitle.setText(currentIssue.getTitle());
            b.etDescription.setText(currentIssue.getDescription());

            if (currentIssue.getPhotoUrls() != null) {
                keptUrls.addAll(currentIssue.getPhotoUrls());
            }
            rebuildPhotoRow();
        }).addOnFailureListener(e -> toast("Не удалось загрузить заявку"));
    }

    private void registerLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                    if (uris == null) return;
                    for (Uri u : uris) {
                        if (keptUrls.size() + newUris.size() >= MAX_PHOTOS) break;
                        try {
                            requireContext().getContentResolver()
                                    .takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) { }
                        newUris.add(u);
                    }
                    rebuildPhotoRow();
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(), ok -> {
                    if (ok != null && ok && cameraUri != null) {
                        if (keptUrls.size() + newUris.size() < MAX_PHOTOS) newUris.add(cameraUri);
                        rebuildPhotoRow();
                    }
                });

        cameraPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), perms -> {
                    Boolean cam = perms.get(Manifest.permission.CAMERA);
                    if (cam != null && cam) launchCamera();
                    else toast("Нужно разрешение камеры");
                });
    }

    private void requestCamera() {
        if (keptUrls.size() + newUris.size() >= MAX_PHOTOS) {
            toast("Максимум " + MAX_PHOTOS + " фото"); return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), "images");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "edit_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(cameraUri);
        } catch (Exception e) {
            toast("Не удалось открыть камеру");
        }
    }

    private void rebuildPhotoRow() {
        if (b == null) return;
        b.photoRow.removeAllViews();
        int dp80 = (int) (80 * getResources().getDisplayMetrics().density);
        int dp4 = (int) (4 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < keptUrls.size(); i++) {
            String url = keptUrls.get(i);
            ItemCreatePhotoBinding item = ItemCreatePhotoBinding.inflate(
                    LayoutInflater.from(requireContext()), b.photoRow, false);
            Glide.with(item.ivPhoto).load(url).centerCrop().into(item.ivPhoto);
            final int idx = i;
            item.btnRemove.setOnClickListener(v -> { keptUrls.remove(idx); rebuildPhotoRow(); });
            b.photoRow.addView(item.getRoot());
        }
        for (int i = 0; i < newUris.size(); i++) {
            Uri uri = newUris.get(i);
            ItemCreatePhotoBinding item = ItemCreatePhotoBinding.inflate(
                    LayoutInflater.from(requireContext()), b.photoRow, false);
            Glide.with(item.ivPhoto).load(uri).centerCrop().into(item.ivPhoto);
            final int idx = i;
            item.btnRemove.setOnClickListener(v -> { newUris.remove(idx); rebuildPhotoRow(); });
            b.photoRow.addView(item.getRoot());
        }
    }

    private void submit() {
        if (currentIssue == null) return;
        String title = b.etTitle.getText().toString().trim();
        String desc = b.etDescription.getText().toString().trim();

        if (title.length() < 5) { b.tilTitle.setError("Минимум 5 символов"); return; }
        b.tilTitle.setError(null);
        if (desc.isEmpty()) { b.tilDescription.setError("Обязательное поле"); return; }
        b.tilDescription.setError(null);
        if (keptUrls.isEmpty() && newUris.isEmpty()) { toast("Добавьте хотя бы одно фото"); return; }

        setLoading(true);
        currentIssue.setTitle(title);
        currentIssue.setDescription(desc);

        uploadNewPhotos(0, new ArrayList<>(keptUrls));
    }

    private void uploadNewPhotos(int index, List<String> collected) {
        if (index >= newUris.size()) {
            currentIssue.setPhotoUrls(collected);
            issueRepo.save(currentIssue).addOnSuccessListener(v -> {
                setLoading(false);
                toast("Заявка обновлена");
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).popHost();
                }
            }).addOnFailureListener(e -> {
                setLoading(false);
                toast("Ошибка сохранения");
            });
            return;
        }
        CloudinaryManager.upload(newUris.get(index), "issues/" + currentIssue.getId(),
                new CloudinaryManager.UploadResultCallback() {
                    @Override public void onSuccess(String url) {
                        collected.add(url);
                        uploadNewPhotos(index + 1, collected);
                    }
                    @Override public void onError(String msg) {
                        setLoading(false);
                        toast("Ошибка загрузки фото");
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (b == null) return;
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnSubmit.setEnabled(!loading);
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}
