package com.app.mycity.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.data.model.ChatThread;
import com.app.mycity.data.model.UserProfile;
import com.app.mycity.data.repository.ChatRepository;
import com.app.mycity.data.repository.UserRepository;
import com.app.mycity.databinding.FragmentChatListBinding;
import com.app.mycity.ui.main.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatListFragment extends Fragment {

    private FragmentChatListBinding b;
    private final ChatRepository repo = new ChatRepository();
    private final UserRepository userRepo = new UserRepository();
    private ChatListAdapter adapter;
    private ListenerRegistration registration;
    private String currentUid;

    private final Map<String, ListenerRegistration> userListeners = new HashMap<>();
    private final Map<String, String> nameOverrides = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentChatListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).popHost();
            return;
        }
        currentUid = user.getUid();

        adapter = new ChatListAdapter(currentUid, thread -> {
            if (thread == null || thread.getId() == null) return;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openChatDetail(thread.getId());
            }
        });
        b.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvChats.setItemAnimator(null);
        b.rvChats.setAdapter(adapter);

        b.btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).popHost();
        });

        b.fabNewChat.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openCreateChat();
        });

        registration = repo.listenUserChats(currentUid, (list, err) -> {
            if (b == null) return;
            adapter.submit(list);
            syncUserListeners(list);
            boolean empty = list == null || list.isEmpty();
            b.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            b.rvChats.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    private void syncUserListeners(List<ChatThread> chats) {
        Set<String> needed = new HashSet<>();
        if (chats != null) {
            for (ChatThread t : chats) {
                List<String> pids = t.getParticipantIds();
                if (pids == null) continue;
                for (String pid : pids) {
                    if (pid != null && !pid.equals(currentUid)) needed.add(pid);
                }
            }
        }

        Set<String> toRemove = new HashSet<>(userListeners.keySet());
        toRemove.removeAll(needed);
        for (String uid : toRemove) {
            ListenerRegistration reg = userListeners.remove(uid);
            if (reg != null) reg.remove();
            nameOverrides.remove(uid);
        }

        for (String uid : needed) {
            if (userListeners.containsKey(uid)) continue;
            ListenerRegistration reg = userRepo.listen(uid, (UserProfile profile, Exception err) -> {
                if (b == null || adapter == null) return;
                if (profile == null) return;
                String dn = profile.getDisplayName();
                if (dn != null && !dn.trim().isEmpty()) {
                    nameOverrides.put(uid, dn.trim());
                } else {
                    nameOverrides.remove(uid);
                }
                adapter.setNameOverrides(nameOverrides);
            });
            userListeners.put(uid, reg);
        }

        if (!nameOverrides.isEmpty()) adapter.setNameOverrides(nameOverrides);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) { registration.remove(); registration = null; }
        for (ListenerRegistration reg : userListeners.values()) {
            if (reg != null) reg.remove();
        }
        userListeners.clear();
        nameOverrides.clear();
        b = null;
    }
}
