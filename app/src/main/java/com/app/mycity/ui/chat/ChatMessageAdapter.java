package com.app.mycity.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.data.model.ChatMessage;
import com.app.mycity.databinding.ItemChatMessageIncomingBinding;
import com.app.mycity.databinding.ItemChatMessageOutgoingBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_INCOMING = 0;
    public static final int TYPE_OUTGOING = 1;

    private final String currentUid;
    private final boolean isGroup;
    private List<ChatMessage> items = new ArrayList<>();

    public ChatMessageAdapter(String currentUid, boolean isGroup) {
        this.currentUid = currentUid;
        this.isGroup = isGroup;
    }

    public void submit(List<ChatMessage> newList) {
        final List<ChatMessage> safeNew = newList != null ? newList : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return safeNew.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getId();
                String nid = safeNew.get(n).getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                ChatMessage a = items.get(o), b = safeNew.get(n);
                return strEq(a.getText(), b.getText())
                        && strEq(a.getStatus(), b.getStatus())
                        && dateEq(a.getSentAt(), b.getSentAt());
            }
            private boolean strEq(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
            private boolean dateEq(Date a, Date b) {
                if (a == null) return b == null;
                return b != null && a.getTime() == b.getTime();
            }
        });
        items = new ArrayList<>(safeNew);
        result.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage m = items.get(position);
        String sender = m.getSenderId();
        boolean mine = sender != null && sender.equals(currentUid);
        return mine ? TYPE_OUTGOING : TYPE_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_OUTGOING) {
            ItemChatMessageOutgoingBinding bo = ItemChatMessageOutgoingBinding.inflate(inflater, parent, false);
            return new OutgoingVH(bo);
        }
        ItemChatMessageIncomingBinding bi = ItemChatMessageIncomingBinding.inflate(inflater, parent, false);
        return new IncomingVH(bi);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage m = items.get(position);
        if (holder instanceof OutgoingVH) {
            ((OutgoingVH) holder).bind(m);
        } else if (holder instanceof IncomingVH) {
            ((IncomingVH) holder).bind(m, isGroup);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String formatTime(Date sentAt) {
        if (sentAt == null) return "";
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return fmt.format(sentAt);
    }

    static class IncomingVH extends RecyclerView.ViewHolder {
        final ItemChatMessageIncomingBinding b;

        IncomingVH(ItemChatMessageIncomingBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(ChatMessage m, boolean isGroup) {
            b.tvText.setText(m.getText() != null ? m.getText() : "");
            b.tvTime.setText(formatTime(m.getSentAt()));
            String senderName = m.getSenderName();
            if (isGroup && senderName != null && !senderName.isEmpty()) {
                b.tvSenderName.setVisibility(View.VISIBLE);
                b.tvSenderName.setText(senderName);
            } else {
                b.tvSenderName.setVisibility(View.GONE);
            }
        }
    }

    static class OutgoingVH extends RecyclerView.ViewHolder {
        final ItemChatMessageOutgoingBinding b;

        OutgoingVH(ItemChatMessageOutgoingBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(ChatMessage m) {
            b.tvText.setText(m.getText() != null ? m.getText() : "");
            b.tvTime.setText(formatTime(m.getSentAt()));
        }
    }
}
