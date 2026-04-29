package com.app.mycity.ui.profile;

import android.app.Dialog;
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

import com.app.mycity.R;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.BottomSheetEditProfileBinding;
import com.app.mycity.util.CloudinaryManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EditProfileBottomSheet extends BottomSheetDialogFragment {

    public static EditProfileBottomSheet newInstance(String currentName, String currentAvatar) {
        EditProfileBottomSheet s = new EditProfileBottomSheet();
        Bundle args = new Bundle();
        args.putString("name", currentName);
        args.putString("avatar", currentAvatar);
        s.setArguments(args);
        return s;
    }

    public interface Listener { void onProfileUpdated(); }

    private BottomSheetEditProfileBinding b;
    private Uri pickedAvatar;
    private ActivityResultLauncher<String[]> pickLauncher;
    private final UserRepository repo = new UserRepository();

    @Override
    public int getTheme() { return R.style.BottomSheetTheme; }

    @Override @NonNull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        return d;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = BottomSheetEditProfileBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        pickLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                pickedAvatar = uri;
                Glide.with(b.ivAvatar).load(uri).into(b.ivAvatar);
            }
        });

        String name = getArguments() != null ? getArguments().getString("name", "") : "";
        String avatar = getArguments() != null ? getArguments().getString("avatar") : null;
        b.etName.setText(name);
        if (avatar != null && !avatar.isEmpty()) {
            Glide.with(b.ivAvatar).load(avatar).placeholder(R.drawable.ic_avatar_placeholder).into(b.ivAvatar);
        }

        b.btnPickAvatar.setOnClickListener(v -> pickLauncher.launch(new String[]{"image/*"}));
        b.btnSave.setOnClickListener(v -> save());
    }

    private void save() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { dismiss(); return; }

        String name = b.etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            b.tilName.setError("Введите никнейм");
            return;
        }
        b.btnSave.setEnabled(false);
        repo.updateName(user.getUid(), name);

        if (pickedAvatar != null) {
            CloudinaryManager.upload(pickedAvatar, "avatars/" + user.getUid(),
                    new CloudinaryManager.UploadResultCallback() {
                        @Override public void onSuccess(String secureUrl) {
                            repo.updateAvatar(user.getUid(), secureUrl)
                                    .addOnCompleteListener(t -> done());
                        }
                        @Override public void onError(String errorMessage) {
                            toast("Ошибка загрузки аватара");
                            done();
                        }
                    });
        } else {
            done();
        }
    }

    private void done() {
        if (getParentFragment() instanceof Listener) ((Listener) getParentFragment()).onProfileUpdated();
        dismiss();
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); b = null; }
}
