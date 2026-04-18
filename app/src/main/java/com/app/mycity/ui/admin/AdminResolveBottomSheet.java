package com.app.mycity.ui.admin;

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

import com.app.mycity.data.repository.IssueRepository;
import com.app.mycity.data.repository.NotificationRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.BottomSheetAdminResolveBinding;
import com.app.mycity.databinding.ItemCreatePhotoBinding;
import com.app.mycity.util.CloudinaryManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AdminResolveBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ISSUE_ID = "issue_id";
    private static final int MAX_PHOTOS = 5;

    public interface OnResolvedListener { void onResolved(); }

    public static AdminResolveBottomSheet newInstance(String issueId) {
        AdminResolveBottomSheet bs = new AdminResolveBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ISSUE_ID, issueId);
        bs.setArguments(args);
        return bs;
    }

    private BottomSheetAdminResolveBinding b;
    private final IssueRepository issueRepo = new IssueRepository();
    private final NotificationRepository notifRepo = new NotificationRepository();
    private final UserRepository userRepo = new UserRepository();
    private final List<Uri> photoUris = new ArrayList<>();
    private Uri cameraUri;
    private OnResolvedListener onResolvedListener;

    private ActivityResultLauncher<String[]> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String[]> cameraPermLauncher;

    public void setOnResolvedListener(OnResolvedListener l) { this.onResolvedListener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = BottomSheetAdminResolveBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        registerLaunchers();

        b.btnCamera.setOnClickListener(v -> requestCamera());
        b.btnGallery.setOnClickListener(v -> galleryLauncher.launch(new String[]{"image/*"}));
        b.btnCancel.setOnClickListener(v -> dismiss());
        b.btnResolve.setOnClickListener(v -> submit());
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
            File file = new File(dir, "report_" + System.currentTimeMillis() + ".jpg");
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
        String report = b.etReport.getText() != null ? b.etReport.getText().toString().trim() : "";
        if (report.length() < 20) {
            b.tilReport.setError("Минимум 20 символов");
            return;
        }
        b.tilReport.setError(null);
        if (photoUris.isEmpty()) {
            toast("Добавьте хотя бы одно фото");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { toast("Ошибка авторизации"); return; }

        String issueId = requireArguments().getString(ARG_ISSUE_ID);
        String uid = user.getUid();

        setLoading(true);
        userRepo.get(uid).addOnSuccessListener(snap -> {
            String name = snap != null && snap.exists() ? snap.getString("displayName") : null;
            uploadPhotos(issueId, uid, resolveAdminName(name, user), report, 0, new ArrayList<>());
        }).addOnFailureListener(e -> {
            uploadPhotos(issueId, uid, resolveAdminName(null, user), report, 0, new ArrayList<>());
        });
    }

    private String resolveAdminName(String firestoreName, FirebaseUser user) {
        if (firestoreName != null && !firestoreName.trim().isEmpty()) return firestoreName.trim();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        return "Администратор";
    }

    private void uploadPhotos(String issueId, String uid, String displayName,
                              String report, int index, List<String> collected) {
        if (index >= photoUris.size()) {
            issueRepo.resolve(issueId, uid, displayName, report, collected)
                    .addOnSuccessListener(v -> {
                        setLoading(false);
                        sendNotificationToAuthor(issueId, displayName);
                        toast("Заявка закрыта");
                        if (onResolvedListener != null) onResolvedListener.onResolved();
                        dismiss();
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        toast("Ошибка сохранения");
                    });
            return;
        }
        CloudinaryManager.upload(photoUris.get(index), "issues/" + issueId + "/report",
                new CloudinaryManager.UploadResultCallback() {
                    @Override public void onSuccess(String url) {
                        collected.add(url);
                        uploadPhotos(issueId, uid, displayName, report, index + 1, collected);
                    }
                    @Override public void onError(String msg) {
                        setLoading(false);
                        toast("Ошибка загрузки фото");
                    }
                });
    }

    private void sendNotificationToAuthor(String issueId, String adminName) {
        issueRepo.get(issueId).addOnSuccessListener(snap -> {
            if (!snap.exists()) return;
            String authorId = snap.getString("authorId");
            String title = snap.getString("title");
            FirebaseUser me = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (authorId == null || (me != null && authorId.equals(me.getUid()))) return;
            notifRepo.send(authorId, issueId, title != null ? title : "", adminName);
        });
    }

    private void setLoading(boolean loading) {
        if (b == null) return;
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnResolve.setEnabled(!loading);
        b.btnCamera.setEnabled(!loading);
        b.btnGallery.setEnabled(!loading);
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override public void onDestroyView() { super.onDestroyView(); b = null; }
}
