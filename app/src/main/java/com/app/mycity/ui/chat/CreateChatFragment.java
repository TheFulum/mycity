package com.app.mycity.ui.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.R;
import com.app.mycity.data.model.UserProfile;
import com.app.mycity.data.repository.ChatRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentCreateChatBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreateChatFragment extends Fragment {

    private FragmentCreateChatBinding b;

    private final UserRepository userRepo = new UserRepository();
    private final ChatRepository chatRepo = new ChatRepository();

    private UserPickerAdapter adapter;
    private ListenerRegistration registration;

    private boolean groupMode = false;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<UserProfile> cachedUsers = new ArrayList<>();

    private String currentUid;
    private String currentName;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentCreateChatBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            popHost();
            return;
        }
        currentUid = current.getUid();
        currentName = !TextUtils.isEmpty(current.getDisplayName())
                ? current.getDisplayName()
                : "Пользователь";

        userRepo.get(currentUid).addOnSuccessListener(snap -> {
            if (snap != null && snap.exists()) {
                UserProfile p = snap.toObject(UserProfile.class);
                if (p != null && !TextUtils.isEmpty(p.getDisplayName())) {
                    currentName = p.getDisplayName();
                }
            }
        });

        adapter = new UserPickerAdapter(this::onUserClicked, () -> groupMode, selected);
        b.rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvUsers.setItemAnimator(null);
        b.rvUsers.setAdapter(adapter);

        b.btnBack.setOnClickListener(v -> popHost());

        b.swGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            groupMode = isChecked;
            selected.clear();
            applyGroupModeVisibility();
            updateSelectedCount();
            if (adapter != null) adapter.refreshSelection();
        });

        b.btnCreateGroup.setOnClickListener(v -> createGroup());

        applyGroupModeVisibility();
        updateSelectedCount();

        registration = userRepo.listenAll((list, err) -> {
            if (b == null) return;
            if (list == null) return;
            List<UserProfile> filtered = new ArrayList<>();
            for (UserProfile u : list) {
                if (u == null || u.getUid() == null) continue;
                if (u.getUid().equals(currentUid)) continue;
                if (u.isPermanentlyBlocked()) continue;
                filtered.add(u);
            }
            Collections.sort(filtered, new Comparator<UserProfile>() {
                @Override public int compare(UserProfile a, UserProfile b2) {
                    String an = a.getDisplayName() != null ? a.getDisplayName() : "";
                    String bn = b2.getDisplayName() != null ? b2.getDisplayName() : "";
                    return an.compareToIgnoreCase(bn);
                }
            });
            cachedUsers.clear();
            cachedUsers.addAll(filtered);
            adapter.submit(filtered);

            Set<String> validIds = new LinkedHashSet<>();
            for (UserProfile u : filtered) validIds.add(u.getUid());
            boolean changed = selected.retainAll(validIds);
            if (changed) {
                updateSelectedCount();
                adapter.refreshSelection();
            }
        });
    }

    private void onUserClicked(UserProfile user) {
        if (user == null || user.getUid() == null) return;
        if (!groupMode) {
            startDirectChat(user);
            return;
        }
        if (selected.contains(user.getUid())) {
            selected.remove(user.getUid());
        } else {
            selected.add(user.getUid());
        }
        updateSelectedCount();
        if (adapter != null) adapter.refreshSelection();
    }

    private void startDirectChat(UserProfile other) {
        String otherName = !TextUtils.isEmpty(other.getDisplayName())
                ? other.getDisplayName()
                : "Пользователь";
        chatRepo.createDirectChat(currentUid, currentName, other.getUid(), otherName)
                .addOnSuccessListener(ref -> {
                    if (!isAdded() || ref == null) return;
                    if (getActivity() instanceof MainActivity) {
                        MainActivity m = (MainActivity) getActivity();
                        m.popHost();
                        m.openChatDetail(ref.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(),
                                getString(R.string.chat_create_error_format,
                                        e.getMessage() != null ? e.getMessage() : ""),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createGroup() {
        if (b == null) return;
        String name = b.etGroupName.getText() != null ? b.etGroupName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name) || selected.size() < 2) {
            Toast.makeText(requireContext(),
                    R.string.chat_create_group_validation, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> participantIds = new ArrayList<>();
        Map<String, String> participantNames = new HashMap<>();
        participantIds.add(currentUid);
        participantNames.put(currentUid, currentName);
        for (UserProfile u : cachedUsers) {
            if (u.getUid() != null && selected.contains(u.getUid())) {
                participantIds.add(u.getUid());
                participantNames.put(u.getUid(),
                        !TextUtils.isEmpty(u.getDisplayName()) ? u.getDisplayName() : "Пользователь");
            }
        }

        b.btnCreateGroup.setEnabled(false);
        chatRepo.createGroupChat(name, currentUid, participantIds, participantNames)
                .addOnSuccessListener(ref -> {
                    if (!isAdded() || ref == null) return;
                    if (getActivity() instanceof MainActivity) {
                        MainActivity m = (MainActivity) getActivity();
                        m.popHost();
                        m.openChatDetail(ref.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    if (b != null) b.btnCreateGroup.setEnabled(true);
                    if (getContext() != null) {
                        Toast.makeText(getContext(),
                                getString(R.string.chat_create_error_format,
                                        e.getMessage() != null ? e.getMessage() : ""),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void applyGroupModeVisibility() {
        if (b == null) return;
        b.tilGroupName.setVisibility(groupMode ? View.VISIBLE : View.GONE);
        b.btnCreateGroup.setVisibility(groupMode ? View.VISIBLE : View.GONE);
        b.tvSelectedCount.setVisibility(groupMode ? View.VISIBLE : View.GONE);
        if (!groupMode && b.etGroupName != null) {
            b.etGroupName.setText("");
        }
    }

    private void updateSelectedCount() {
        if (b == null) return;
        b.tvSelectedCount.setText(getString(R.string.chat_create_selected_format, selected.size()));
    }

    private void popHost() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).popHost();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        b = null;
    }
}
