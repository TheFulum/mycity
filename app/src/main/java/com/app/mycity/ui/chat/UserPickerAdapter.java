package com.app.mycity.ui.chat;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.UserProfile;
import com.app.mycity.databinding.ItemUserPickerBinding;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class UserPickerAdapter extends RecyclerView.Adapter<UserPickerAdapter.VH> {

    public interface OnUserClickListener {
        void onClick(UserProfile user);
    }

    public interface GroupModeProvider {
        boolean isGroupMode();
    }

    private final List<UserProfile> items = new ArrayList<>();
    private final OnUserClickListener clickListener;
    private final GroupModeProvider groupModeProvider;
    private final Set<String> selectedRef;

    public UserPickerAdapter(OnUserClickListener clickListener,
                             GroupModeProvider groupModeProvider,
                             Set<String> selectedRef) {
        this.clickListener = clickListener;
        this.groupModeProvider = groupModeProvider;
        this.selectedRef = selectedRef;
    }

    public void submit(List<UserProfile> next) {
        List<UserProfile> safeNext = next != null ? new ArrayList<>(next) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(items, safeNext));
        items.clear();
        items.addAll(safeNext);
        diff.dispatchUpdatesTo(this);
    }

    public void refreshSelection() {
        notifyItemRangeChanged(0, items.size(), Boolean.TRUE);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserPickerBinding b = ItemUserPickerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b, clickListener, groupModeProvider, selectedRef);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemUserPickerBinding b;
        private final OnUserClickListener clickListener;
        private final GroupModeProvider groupModeProvider;
        private final Set<String> selectedRef;

        VH(ItemUserPickerBinding b,
           OnUserClickListener clickListener,
           GroupModeProvider groupModeProvider,
           Set<String> selectedRef) {
            super(b.getRoot());
            this.b = b;
            this.clickListener = clickListener;
            this.groupModeProvider = groupModeProvider;
            this.selectedRef = selectedRef;
        }

        void bind(UserProfile user) {
            String name = !TextUtils.isEmpty(user.getDisplayName())
                    ? user.getDisplayName()
                    : (!TextUtils.isEmpty(user.getEmail()) ? user.getEmail() : "Пользователь");
            b.tvName.setText(name);
            b.ivCrown.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);

            if (!TextUtils.isEmpty(user.getAvatarUrl())) {
                Glide.with(b.ivAvatar).load(user.getAvatarUrl())
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .into(b.ivAvatar);
            } else {
                b.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            boolean groupMode = groupModeProvider != null && groupModeProvider.isGroupMode();
            boolean selected = groupMode && user.getUid() != null && selectedRef.contains(user.getUid());
            b.vSelected.setVisibility(selected ? View.VISIBLE : View.GONE);
            b.cardRoot.setCardBackgroundColor(
                    selected
                            ? b.getRoot().getContext().getColor(R.color.surface_overlay)
                            : b.getRoot().getContext().getColor(R.color.bg_card));

            b.getRoot().setOnClickListener(v -> {
                if (clickListener != null) clickListener.onClick(user);
            });
        }
    }

    private static class DiffCallback extends DiffUtil.Callback {
        private final List<UserProfile> oldList;
        private final List<UserProfile> newList;

        DiffCallback(List<UserProfile> oldList, List<UserProfile> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            String a = oldList.get(oldItemPosition).getUid();
            String b = newList.get(newItemPosition).getUid();
            return Objects.equals(a, b);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            UserProfile a = oldList.get(oldItemPosition);
            UserProfile b = newList.get(newItemPosition);
            return Objects.equals(a.getDisplayName(), b.getDisplayName())
                    && Objects.equals(a.getAvatarUrl(), b.getAvatarUrl())
                    && a.isAdmin() == b.isAdmin();
        }
    }
}
