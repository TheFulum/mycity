package com.app.mycity.ui.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.model.UserProfile;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentAdminUsersBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminUsersFragment extends Fragment {

    private FragmentAdminUsersBinding b;
    private final UserRepository userRepo = new UserRepository();
    private ListenerRegistration listener;
    private AdminUserAdapter adapter;
    private final List<UserProfile> allUsers = new ArrayList<>();
    private String query = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentAdminUsersBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        String myUid = me != null ? me.getUid() : null;
        adapter = new AdminUserAdapter(myUid, this::confirmToggleRole);
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
                if (matches(u, query)) filtered.add(u);
            }
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
        new AlertDialog.Builder(requireContext())
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
