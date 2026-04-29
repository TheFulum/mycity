package com.app.mycity.ui.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.RadioGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.model.UserProfile;
import com.app.mycity.data.repository.NotificationRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentAdminUsersBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminUsersFragment extends Fragment {

    private FragmentAdminUsersBinding b;
    private final UserRepository userRepo = new UserRepository();
    private final NotificationRepository notificationRepo = new NotificationRepository();
    private ListenerRegistration listener;
    private AdminUserAdapter adapter;
    private final List<UserProfile> allUsers = new ArrayList<>();
    private String query = "";
    private boolean blockedOnly = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentAdminUsersBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        String myUid = me != null ? me.getUid() : null;
        adapter = new AdminUserAdapter(myUid, this::confirmToggleRole, this::showBlockDialog, this::confirmUnblock);
        adapter.setOnUserClick(user -> {
            if (getActivity() instanceof com.app.mycity.ui.main.MainActivity) {
                ((com.app.mycity.ui.main.MainActivity) getActivity()).openUserProfile(user.getUid());
            }
        });
        b.rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rv.setAdapter(adapter);

        b.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b0, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
        b.btnShowBlocked.setOnClickListener(v -> {
            blockedOnly = !blockedOnly;
            b.btnShowBlocked.setText(blockedOnly ? "Показать всех" : "Показать заблокированных");
            applyFilter();
        });

        listener = userRepo.listenAll((list, err) -> {
            if (b == null) return;
            allUsers.clear();
            allUsers.addAll(list);
            applyFilter();
        });
    }

    private void applyFilter() {
        if (b == null) return;
        List<UserProfile> filtered;
        if (TextUtils.isEmpty(query)) {
            filtered = new ArrayList<>(allUsers);
        } else {
            filtered = new ArrayList<>();
            for (UserProfile u : allUsers) {
                if (matches(u, query) && (!blockedOnly || u.isBlockedNow())) filtered.add(u);
            }
        }
        if (TextUtils.isEmpty(query) && blockedOnly) {
            List<UserProfile> blocked = new ArrayList<>();
            for (UserProfile u : filtered) {
                if (u.isBlockedNow()) blocked.add(u);
            }
            filtered = blocked;
        }
        adapter.submit(filtered);
        b.tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        b.tvEmpty.setText(allUsers.isEmpty() ? "Пользователей нет" : "Ничего не найдено");
    }

    private boolean matches(UserProfile u, String q) {
        return contains(u.getEmail(), q)
                || contains(u.getPhone(), q)
                || contains(u.getDisplayName(), q);
    }

    private boolean contains(String src, String q) {
        return src != null && src.toLowerCase(Locale.ROOT).contains(q);
    }

    private void confirmToggleRole(UserProfile user) {
        boolean willBeAdmin = !user.isAdmin();
        String title = willBeAdmin ? "Сделать админом?" : "Снять права админа?";
        new MaterialAlertDialogBuilder(requireContext(), com.app.mycity.R.style.DialogTheme)
                .setTitle(title)
                .setMessage(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail())
                .setPositiveButton("Да", (d, w) -> {
                    String newRole = willBeAdmin ? UserProfile.ROLE_ADMIN : UserProfile.ROLE_USER;
                    userRepo.updateRole(user.getUid(), newRole)
                            .addOnSuccessListener(v -> toast("Роль изменена"))
                            .addOnFailureListener(e -> toast("Ошибка: " + e.getMessage()));
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showBlockDialog(UserProfile user) {
        View view = LayoutInflater.from(requireContext()).inflate(com.app.mycity.R.layout.dialog_block_user, null, false);
        EditText etReason = view.findViewById(com.app.mycity.R.id.et_reason);
        EditText etDays = view.findViewById(com.app.mycity.R.id.et_days);
        View tilDays = view.findViewById(com.app.mycity.R.id.til_days);
        RadioGroup rgType = view.findViewById(com.app.mycity.R.id.rg_block_type);
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            if (tilDays != null) tilDays.setVisibility(
                    checkedId == com.app.mycity.R.id.rb_temporary ? View.VISIBLE : View.GONE
            );
        });

        if (tilDays != null) tilDays.setVisibility(View.VISIBLE);
        new MaterialAlertDialogBuilder(requireContext(), com.app.mycity.R.style.DialogTheme)
                .setTitle("Заблокировать пользователя")
                .setView(view)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Заблокировать", (d, w) -> {
                    String reason = etReason.getText().toString().trim();
                    if (reason.length() < 20) {
                        toast("Причина блокировки: минимум 20 символов");
                        return;
                    }
                    String adminUid = FirebaseAuth.getInstance().getCurrentUser() != null
                            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                            : null;
                    int checkedId = rgType.getCheckedRadioButtonId();
                    if (checkedId == com.app.mycity.R.id.rb_permanent) {
                        userRepo.blockPermanent(user.getUid(), reason, adminUid)
                                .addOnSuccessListener(v -> {
                                    notificationRepo.sendUserBlocked(user.getUid(), reason, true, null);
                                    toast("Пользователь заблокирован навсегда");
                                })
                                .addOnFailureListener(e -> toast("Ошибка: " + e.getMessage()));
                        return;
                    }

                    String daysRaw = etDays.getText().toString().trim();
                    int days;
                    try {
                        days = Integer.parseInt(daysRaw);
                    } catch (Exception ex) {
                        toast("Укажите срок блокировки в днях");
                        return;
                    }
                    if (days < 1) {
                        toast("Срок блокировки должен быть минимум 1 день");
                        return;
                    }
                    long untilMs = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L;
                    java.util.Date until = new java.util.Date(untilMs);
                    userRepo.blockTemporary(user.getUid(), reason, until, adminUid)
                            .addOnSuccessListener(v -> {
                                notificationRepo.sendUserBlocked(user.getUid(), reason, false, days);
                                toast("Пользователь заблокирован на " + days + " дн.");
                            })
                            .addOnFailureListener(e -> toast("Ошибка: " + e.getMessage()));
                })
                .show();
    }

    private void confirmUnblock(UserProfile user) {
        new MaterialAlertDialogBuilder(requireContext(), com.app.mycity.R.style.DialogTheme)
                .setTitle("Разблокировать пользователя?")
                .setMessage(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail())
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Разблокировать", (d, w) ->
                        userRepo.unblock(user.getUid())
                                .addOnSuccessListener(v -> toast("Пользователь разблокирован"))
                                .addOnFailureListener(e -> toast("Ошибка: " + e.getMessage())))
                .show();
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listener != null) { listener.remove(); listener = null; }
        b = null;
    }
}
