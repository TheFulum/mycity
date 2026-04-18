package com.app.mycity.ui.admin;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.UserProfile;
import com.app.mycity.databinding.ItemAdminUserBinding;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.VH> {

    public interface OnToggleRole { void onToggle(UserProfile user); }
    public interface OnUserClick { void onClick(UserProfile user); }

    private final List<UserProfile> items = new ArrayList<>();
    private final OnToggleRole onToggle;
    private final String currentUid;
    private OnUserClick onUserClick;

    public AdminUserAdapter(String currentUid, OnToggleRole onToggle) {
        this.currentUid = currentUid;
        this.onToggle = onToggle;
    }

    public void setOnUserClick(OnUserClick l) { this.onUserClick = l; }

    public void submit(List<UserProfile> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAdminUserBinding b = ItemAdminUserBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    class VH extends RecyclerView.ViewHolder {
        final ItemAdminUserBinding b;

        VH(ItemAdminUserBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(UserProfile u) {
            itemView.setOnClickListener(v -> { if (onUserClick != null) onUserClick.onClick(u); });
            String name = !TextUtils.isEmpty(u.getDisplayName()) ? u.getDisplayName()
                    : (!TextUtils.isEmpty(u.getEmail()) ? u.getEmail() : "Без имени");
            b.tvName.setText(name);

            String contact;
            if (!TextUtils.isEmpty(u.getEmail()) && !TextUtils.isEmpty(u.getPhone())) {
                contact = u.getEmail() + " · " + u.getPhone();
            } else if (!TextUtils.isEmpty(u.getEmail())) {
                contact = u.getEmail();
            } else if (!TextUtils.isEmpty(u.getPhone())) {
                contact = u.getPhone();
            } else {
                contact = "—";
            }
            b.tvContact.setText(contact);

            if (!TextUtils.isEmpty(u.getAvatarUrl())) {
                Glide.with(b.ivAvatar).load(u.getAvatarUrl())
                        .placeholder(R.drawable.ic_avatar_placeholder).into(b.ivAvatar);
            } else {
                b.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            boolean isSelf = currentUid != null && currentUid.equals(u.getUid());
            b.tvRole.setVisibility(u.isAdmin() ? View.VISIBLE : View.GONE);
            b.btnToggleRole.setText(u.isAdmin() ? "Снять админа" : "Сделать админом");
            b.btnToggleRole.setEnabled(!isSelf);
            b.btnToggleRole.setAlpha(isSelf ? 0.4f : 1f);
            b.btnToggleRole.setOnClickListener(v -> {
                if (!isSelf) onToggle.onToggle(u);
            });
        }
    }
}
