package com.app.mycity.ui.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.mycity.R;
import com.app.mycity.data.model.ChatMessage;
import com.app.mycity.data.model.ChatThread;
import com.app.mycity.data.repository.ChatRepository;
import com.app.mycity.databinding.FragmentChatDetailBinding;
import com.app.mycity.ui.main.MainActivity;
import com.app.mycity.util.FirebaseErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Map;

public class ChatDetailFragment extends Fragment {

    private static final String ARG_CHAT_ID = "chat_id";

    public static ChatDetailFragment newInstance(String chatId) {
        ChatDetailFragment f = new ChatDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CHAT_ID, chatId);
        f.setArguments(args);
        return f;
    }

    private FragmentChatDetailBinding b;
    private final ChatRepository repo = new ChatRepository();
    private ChatMessageAdapter adapter;
    private ListenerRegistration messagesRegistration;
    private ListenerRegistration threadRegistration;
    private ChatThread thread;
    private String chatId;
    private String currentUid;
    private String currentName;
    private boolean markedRead;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentChatDetailBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        chatId = requireArguments().getString(ARG_CHAT_ID);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).popHost();
            return;
        }
        currentUid = user.getUid();
        currentName = resolveCurrentName(user);

        b.btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).popHost();
        });

        adapter = new ChatMessageAdapter(currentUid, false);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        b.rvMessages.setLayoutManager(lm);
        b.rvMessages.setItemAnimator(null);
        b.rvMessages.setAdapter(adapter);

        updateSendEnabled();
        b.etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { updateSendEnabled(); }
        });
        b.btnSend.setOnClickListener(v -> sendMessage());

        threadRegistration = repo.listenOne(chatId, (t, err) -> {
            if (b == null) return;
            if (t == null) return;
            thread = t;
            renderHeader();
            rebuildAdapterIfGroupChanged();
            tryMarkRead();
        });

        messagesRegistration = repo.listenMessages(chatId, (list, err) -> {
            if (b == null || adapter == null) return;
            adapter.submit(list);
            boolean empty = list == null || list.isEmpty();
            b.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (!empty) b.rvMessages.scrollToPosition(list.size() - 1);
            tryMarkRead();
        });
    }

    private String resolveCurrentName(FirebaseUser user) {
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) return displayName;
        String email = user.getEmail();
        if (email != null && !email.isEmpty()) return email;
        return "Пользователь";
    }

    private void renderHeader() {
        if (b == null || thread == null) return;
        String title;
        if (ChatThread.TYPE_GROUP.equals(thread.getType())) {
            String name = thread.getName();
            title = (name != null && !name.isEmpty())
                    ? name
                    : getString(R.string.chat_group_default_title);
        } else {
            title = resolveOtherName();
        }
        b.tvTitle.setText(title);
    }

    private String resolveOtherName() {
        Map<String, String> names = thread.getParticipantNames();
        if (names != null) {
            for (Map.Entry<String, String> e : names.entrySet()) {
                if (e.getKey() != null && !e.getKey().equals(currentUid)) {
                    String n = e.getValue();
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        }
        return getString(R.string.chat_default_title);
    }

    private Boolean currentIsGroup;

    private void rebuildAdapterIfGroupChanged() {
        if (b == null || adapter == null || thread == null) return;
        boolean isGroup = ChatThread.TYPE_GROUP.equals(thread.getType());
        if (currentIsGroup != null && currentIsGroup == isGroup) return;
        currentIsGroup = isGroup;
        adapter = new ChatMessageAdapter(currentUid, isGroup);
        b.rvMessages.swapAdapter(adapter, false);
    }

    private void tryMarkRead() {
        if (markedRead || thread == null || chatId == null || currentUid == null) return;
        markedRead = true;
        repo.markRead(chatId, currentUid);
    }

    private void updateSendEnabled() {
        if (b == null) return;
        boolean hasText = b.etMessage.getText() != null
                && !TextUtils.isEmpty(b.etMessage.getText().toString().trim());
        b.btnSend.setEnabled(hasText);
        b.btnSend.setAlpha(hasText ? 1f : 0.4f);
    }

    private void sendMessage() {
        if (b == null) return;
        if (currentUid == null) return;
        String text = b.etMessage.getText() == null ? "" : b.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        b.btnSend.setEnabled(false);

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(currentUid);
        msg.setSenderName(currentName);
        msg.setText(text);

        repo.sendMessage(chatId, msg)
                .addOnSuccessListener(v -> {
                    if (b == null) return;
                    b.etMessage.setText("");
                    updateSendEnabled();
                })
                .addOnFailureListener(err -> {
                    if (b == null) return;
                    updateSendEnabled();
                    String reason = FirebaseErrors.humanize(err);
                    Toast.makeText(requireContext(),
                            getString(R.string.chat_send_error_format, reason),
                            Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messagesRegistration != null) { messagesRegistration.remove(); messagesRegistration = null; }
        if (threadRegistration != null) { threadRegistration.remove(); threadRegistration = null; }
        b = null;
    }
}
