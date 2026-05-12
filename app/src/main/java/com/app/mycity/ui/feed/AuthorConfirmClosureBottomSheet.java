package com.app.mycity.ui.feed;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.app.mycity.R;
import com.app.mycity.data.model.Issue;
import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.databinding.BottomSheetAuthorConfirmClosureBinding;
import com.app.mycity.databinding.ItemCreatePhotoBinding;
import com.app.mycity.util.CloudinaryManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AuthorConfirmClosureBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ISSUE_ID = "issue_id";
    private static final int MAX_PHOTOS = 5;

    public interface OnConfirmedListener { void onConfirmed(); }

    public static AuthorConfirmClosureBottomSheet newInstance(String issueId) {
        AuthorConfirmClosureBottomSheet bs = new AuthorConfirmClosureBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ISSUE_ID, issueId);
        bs.setArguments(args);
        return bs;
    }

    private BottomSheetAuthorConfirmClosureBinding b;
    private final IssueRepository issueRepo = new IssueRepository();
    private final List<Uri> photoUris = new ArrayList<>();
    private Uri cameraUri;
    private OnConfirmedListener onConfirmedListener;

    private ActivityResultLauncher<String[]> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> cameraPermLauncher;

    public void setOnConfirmedListener(OnConfirmedListener l) { this.onConfirmedListener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = BottomSheetAuthorConfirmClosureBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        registerLaunchers();
        b.btnCamera.setOnClickListener(v -> requestCamera());
        b.btnGallery.setOnClickListener(v -> galleryLauncher.launch(new String[]{"image/*"}));
        b.btnCancel.setOnClickListener(v -> dismiss());
        b.btnConfirm.setOnClickListener(v -> submit());
    }

    private void registerLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                    if (uris == null) return;
                    for (Uri u : uris) {
                        if (photoUris.size() >= MAX_PHOTOS) break;
                        try {
                            requireContext().getContentResolver()
                                    .takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) { }
                        photoUris.add(u);
                    }
                    rebuildPhotoRow();
                });

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(), ok -> {
                    if (ok != null && ok && cameraUri != null) {
                        if (photoUris.size() < MAX_PHOTOS) photoUris.add(cameraUri);
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
        if (photoUris.size() >= MAX_PHOTOS) { toast("Максимум " + MAX_PHOTOS + " фото"); return; }
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
            File file = new File(dir, "closure_" + System.currentTimeMillis() + ".jpg");
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
        for (int i = 0; i < photoUris.size(); i++) {
            Uri uri = photoUris.get(i);
            ItemCreatePhotoBinding item = ItemCreatePhotoBinding.inflate(
                    LayoutInflater.from(requireContext()), b.photoRow, false);
            Glide.with(item.ivPhoto).load(uri).centerCrop().into(item.ivPhoto);
            final int idx = i;
            item.btnRemove.setOnClickListener(v -> { photoUris.remove(idx); rebuildPhotoRow(); });
            b.photoRow.addView(item.getRoot());
        }
    }

    private void submit() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) { toast("Ошибка авторизации"); return; }
        final String uid = auth.getCurrentUser().getUid();
        String issueId = requireArguments().getString(ARG_ISSUE_ID);
        if (issueId == null) return;
        String comment = b.etComment.getText() != null ? b.etComment.getText().toString().trim() : "";
        b.tilComment.setError(null);

        setLoading(true);
        issueRepo.get(issueId).addOnSuccessListener(snap -> {
            if (b == null) return;
            if (!snap.exists()) {
                setLoading(false);
                toast("Заявка не найдена");
                dismiss();
                return;
            }
            Issue issue = snap.toObject(Issue.class);
            if (issue == null || issue.getAuthorId() == null || !issue.getAuthorId().equals(uid)) {
                setLoading(false);
                toast(getString(R.string.author_closure_confirm_forbidden));
                dismiss();
                return;
            }
            if (!issue.needsAuthorClosureConfirmation()) {
                setLoading(false);
                toast(getString(R.string.author_closure_confirm_not_pending));
                dismiss();
                return;
            }
            if (photoUris.isEmpty()) {
                finishUpload(issueId, comment, new ArrayList<>());
            } else {
                uploadPhotos(issueId, comment, 0, new ArrayList<>());
            }
        }).addOnFailureListener(e -> {
            if (b == null) return;
            setLoading(false);
            toast("Ошибка загрузки заявки");
        });
    }

    private void uploadPhotos(String issueId, String comment, int index, List<String> collected) {
        if (index >= photoUris.size()) {
            finishUpload(issueId, comment, collected);
            return;
        }
        CloudinaryManager.upload(photoUris.get(index), "issues/" + issueId + "/author_closure",
                new CloudinaryManager.UploadResultCallback() {
                    @Override public void onSuccess(String url) {
                        collected.add(url);
                        uploadPhotos(issueId, comment, index + 1, collected);
                    }
                    @Override public void onError(String msg) {
                        setLoading(false);
                        toast("Ошибка загрузки фото");
                    }
                });
    }

    private void finishUpload(String issueId, String comment, List<String> urls) {
        issueRepo.confirmAuthorClosure(issueId, comment, urls)
                .addOnSuccessListener(v -> {
                    setLoading(false);
                    toast("Закрытие подтверждено");
                    if (onConfirmedListener != null) onConfirmedListener.onConfirmed();
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast("Ошибка сохранения");
                });
    }

    private void setLoading(boolean loading) {
        if (b == null) return;
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnConfirm.setEnabled(!loading);
        b.btnCamera.setEnabled(!loading);
        b.btnGallery.setEnabled(!loading);
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}
