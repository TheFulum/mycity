package com.app.mycity.ui.main;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.Notification;
import com.app.mycity.databinding.ItemNotificationBinding;
import com.app.mycity.util.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.VH> {

    public interface OnClickListener { void onClick(Notification n); }

    private List<Notification> items = new ArrayList<>();
    private OnClickListener clickListener;

    public void setOnClickListener(OnClickListener l) { this.clickListener = l; }

    public void submit(List<Notification> newList) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newList.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                return Objects.equals(items.get(o).getId(), newList.get(n).getId());
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                Notification a = items.get(o), b = newList.get(n);
                return a.isRead() == b.isRead()
                        && Objects.equals(a.getMessage(), b.getMessage());
            }
        });
        items = new ArrayList<>(newList);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Notification n = items.get(position);
        h.b.tvNotifMessage.setText(n.getMessage());
        h.b.tvNotifIssue.setText(n.getIssueTitle() != null ? n.getIssueTitle() : "");
        h.b.tvNotifDate.setText(n.getCreatedAt() != null
                ? DateUtils.format(n.getCreatedAt()) : "");
        int bg = n.isRead()
                ? h.itemView.getContext().getColor(R.color.bg_primary)
                : h.itemView.getContext().getColor(R.color.bg_secondary);
        h.itemView.setBackgroundColor(bg);
        h.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onClick(n); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemNotificationBinding b;
        VH(ItemNotificationBinding b) { super(b.getRoot()); this.b = b; }
    }
}
