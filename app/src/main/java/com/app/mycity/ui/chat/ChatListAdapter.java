package com.app.mycity.ui.chat;

import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.ChatThread;
import com.app.mycity.databinding.ItemChatListBinding;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.VH> {

    public interface OnChatClickListener {
        void onClick(ChatThread thread);
    }

    private static final int[] AVATAR_COLORS = {
            0xFF5B5FBE,
            0xFF27AE60,
            0xFFC0392B,
            0xFFE67E22,
            0xFF8E44AD,
            0xFF16A085,
            0xFFD35400,
            0xFF2980B9,
            0xFF7F8C8D,
            0xFFB03A2E
    };

    private List<ChatThread> items = new ArrayList<>();
    private Map<String, String> nameOverrides = new HashMap<>();
    private final OnChatClickListener onClick;
    private final String currentUid;

    public ChatListAdapter(String currentUid, OnChatClickListener onClick) {
        this.currentUid = currentUid;
        this.onClick = onClick;
    }

    public void setNameOverrides(Map<String, String> overrides) {
        this.nameOverrides = overrides != null ? new HashMap<>(overrides) : new HashMap<>();
        notifyDataSetChanged();
    }

    public void submit(List<ChatThread> newList) {
        final List<ChatThread> safeNew = newList != null ? newList : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return safeNew.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getId();
                String nid = safeNew.get(n).getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                ChatThread a = items.get(o), b = safeNew.get(n);
                return strEq(a.getLastMessage(), b.getLastMessage())
                        && strEq(a.getLastMessageSenderId(), b.getLastMessageSenderId())
                        && strEq(a.getName(), b.getName())
                        && dateEq(a.getLastMessageAt(), b.getLastMessageAt())
                        && unreadEq(a.getUnreadCounts(), b.getUnreadCounts());
            }
            private boolean strEq(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
            private boolean dateEq(Date a, Date b) {
                if (a == null) return b == null;
                return b != null && a.getTime() == b.getTime();
            }
            private boolean unreadEq(Map<String, Integer> a, Map<String, Integer> b) {
                if (a == null) return b == null;
                if (b == null) return false;
                Integer va = a.get(currentUid);
                Integer vb = b.get(currentUid);
                if (va == null) va = 0;
                if (vb == null) vb = 0;
                return va.equals(vb);
            }
        });
        items = new ArrayList<>(safeNew);
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatListBinding binding = ItemChatListBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), currentUid, nameOverrides, onClick);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemChatListBinding b;

        VH(ItemChatListBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(ChatThread thread, String currentUid, Map<String, String> nameOverrides, OnChatClickListener listener) {
            String title = resolveTitle(thread, currentUid, nameOverrides);
            b.tvChatName.setText(title);

            String subtitle = resolveSubtitle(thread, currentUid, nameOverrides);
            b.tvLastMessage.setText(subtitle);

            Date when = thread.getLastMessageAt();
            if (when != null) {
                CharSequence rel = DateUtils.getRelativeTimeSpanString(
                        when.getTime(), System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
                b.tvTime.setText(rel != null ? rel.toString() : "");
                b.tvTime.setVisibility(View.VISIBLE);
            } else {
                b.tvTime.setText("");
                b.tvTime.setVisibility(View.GONE);
            }

            int unread = 0;
            Map<String, Integer> counts = thread.getUnreadCounts();
            if (counts != null && currentUid != null) {
                Integer v = counts.get(currentUid);
                if (v != null) unread = v;
            }
            if (unread > 0) {
                b.tvUnread.setVisibility(View.VISIBLE);
                b.tvUnread.setText(unread > 9 ? "9+" : String.valueOf(unread));
            } else {
                b.tvUnread.setVisibility(View.GONE);
            }

            String avatarLetter = resolveAvatarLetter(title);
            b.tvAvatar.setText(avatarLetter);
            int color = AVATAR_COLORS[Math.abs(hashKey(thread.getId())) % AVATAR_COLORS.length];
            b.tvAvatar.setBackgroundTintList(ColorStateList.valueOf(color));

            b.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onClick(thread);
            });
        }

        private String resolveTitle(ChatThread thread, String currentUid, Map<String, String> nameOverrides) {
            if (ChatThread.TYPE_GROUP.equals(thread.getType())) {
                String name = thread.getName();
                if (name != null && !name.isEmpty()) return name;
                return b.getRoot().getContext().getString(R.string.chat_group_default_title);
            }
            List<String> pids = thread.getParticipantIds();
            Map<String, String> cached = thread.getParticipantNames();
            if (pids != null) {
                for (String pid : pids) {
                    if (pid == null || pid.equals(currentUid)) continue;
                    String fresh = nameOverrides != null ? nameOverrides.get(pid) : null;
                    if (fresh != null && !fresh.isEmpty()) return fresh;
                    String cachedName = cached != null ? cached.get(pid) : null;
                    if (cachedName != null && !cachedName.isEmpty()) return cachedName;
                }
            }
            return b.getRoot().getContext().getString(R.string.chat_default_title);
        }

        private String resolveSubtitle(ChatThread thread, String currentUid, Map<String, String> nameOverrides) {
            String last = thread.getLastMessage();
            if (last == null || last.isEmpty()) {
                return b.getRoot().getContext().getString(R.string.chat_no_messages);
            }
            String senderId = thread.getLastMessageSenderId();
            if (senderId != null && senderId.equals(currentUid)) {
                return b.getRoot().getContext().getString(R.string.chat_you_prefix) + last;
            }
            if (ChatThread.TYPE_GROUP.equals(thread.getType()) && senderId != null) {
                String fresh = nameOverrides != null ? nameOverrides.get(senderId) : null;
                if (fresh != null && !fresh.isEmpty()) return fresh + ": " + last;
                Map<String, String> names = thread.getParticipantNames();
                if (names != null) {
                    String cachedSender = names.get(senderId);
                    if (cachedSender != null && !cachedSender.isEmpty()) {
                        return cachedSender + ": " + last;
                    }
                }
            }
            return last;
        }

        private String resolveAvatarLetter(String title) {
            if (title == null || title.isEmpty()) return "?";
            String trimmed = title.trim();
            if (trimmed.isEmpty()) return "?";
            return trimmed.substring(0, 1).toUpperCase();
        }

        private int hashKey(String id) {
            if (id == null || id.isEmpty()) return 0;
            int h = 0;
            for (int i = 0; i < id.length(); i++) h = 31 * h + id.charAt(i);
            return h;
        }
    }
}
