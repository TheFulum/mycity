package com.app.mycity.ui.citypost;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.CityPost;
import com.app.mycity.databinding.ItemCityPostBinding;
import com.app.mycity.util.DateUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class CityPostCardAdapter extends RecyclerView.Adapter<CityPostCardAdapter.VH> {

    public interface OnPostClickListener {
        void onClick(CityPost post);
    }

    public interface OnAuthorClickListener {
        void onClick(String authorUid);
    }

    private List<CityPost> items = new ArrayList<>();
    private final OnPostClickListener onPostClick;
    private OnAuthorClickListener onAuthorClick;

    public CityPostCardAdapter(OnPostClickListener onPostClick) {
        this.onPostClick = onPostClick;
    }

    public void setOnAuthorClick(OnAuthorClickListener listener) {
        this.onAuthorClick = listener;
    }

    public void submit(List<CityPost> newList) {
        final List<CityPost> safeNew = newList != null ? newList : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return safeNew.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getId();
                String nid = safeNew.get(n).getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                CityPost a = items.get(o), b = safeNew.get(n);
                return strEq(a.getTitle(), b.getTitle())
                        && strEq(a.getDescription(), b.getDescription())
                        && strEq(a.getAuthorName(), b.getAuthorName())
                        && strEq(a.getAuthorAvatarUrl(), b.getAuthorAvatarUrl())
                        && strEq(a.getCategory(), b.getCategory())
                        && strEq(a.getAddress(), b.getAddress())
                        && a.getLikeCount() == b.getLikeCount()
                        && a.getCommentCount() == b.getCommentCount()
                        && samePhoto(a, b);
            }
            private boolean strEq(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
            private boolean samePhoto(CityPost a, CityPost b) {
                String pa = (a.getPhotoUrls() != null && !a.getPhotoUrls().isEmpty()) ? a.getPhotoUrls().get(0) : null;
                String pb = (b.getPhotoUrls() != null && !b.getPhotoUrls().isEmpty()) ? b.getPhotoUrls().get(0) : null;
                return strEq(pa, pb);
            }
        });
        items = new ArrayList<>(safeNew);
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCityPostBinding binding = ItemCityPostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class VH extends RecyclerView.ViewHolder {
        final ItemCityPostBinding b;

        VH(ItemCityPostBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(CityPost post) {
            String authorName = post.getAuthorName();
            b.tvAuthorName.setText(authorName != null && !authorName.isEmpty() ? authorName : "Аноним");
            b.tvDate.setText(DateUtils.format(post.getCreatedAt()));

            String avatar = post.getAuthorAvatarUrl();
            if (avatar != null && !avatar.isEmpty()) {
                Glide.with(b.ivAuthorAvatar.getContext())
                        .load(avatar)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(b.ivAuthorAvatar);
            } else {
                b.ivAuthorAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            String aid = post.getAuthorId();
            if (aid != null && !aid.isEmpty() && onAuthorClick != null) {
                View.OnClickListener authorListener = v -> onAuthorClick.onClick(aid);
                b.tvAuthorName.setOnClickListener(authorListener);
                b.ivAuthorAvatar.setOnClickListener(authorListener);
            } else {
                b.tvAuthorName.setOnClickListener(null);
                b.ivAuthorAvatar.setOnClickListener(null);
            }

            b.tvTitle.setText(post.getTitle() != null ? post.getTitle() : "");
            String description = post.getDescription();
            if (description != null && !description.isEmpty()) {
                b.tvDescription.setText(description);
                b.tvDescription.setVisibility(View.VISIBLE);
            } else {
                b.tvDescription.setVisibility(View.GONE);
            }

            b.tvCategory.setText(categoryLabel(post.getCategory()));

            if (post.getPhotoUrls() != null && !post.getPhotoUrls().isEmpty()) {
                Glide.with(b.ivPhoto.getContext())
                        .load(post.getPhotoUrls().get(0))
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(b.ivPhoto);
                b.ivPhoto.setVisibility(View.VISIBLE);
            } else {
                b.ivPhoto.setVisibility(View.GONE);
            }

            String address = post.getAddress();
            if (post.hasLocation() && address != null && !address.trim().isEmpty()) {
                b.tvAddress.setText(address.trim());
                b.rowAddress.setVisibility(View.VISIBLE);
            } else {
                b.rowAddress.setVisibility(View.GONE);
            }

            b.tvLikes.setText(b.getRoot().getContext().getString(R.string.city_likes_prefix) + " " + post.getLikeCount());
            b.tvComments.setText(b.getRoot().getContext().getString(R.string.city_comments_prefix) + " " + post.getCommentCount());

            b.getRoot().setOnClickListener(v -> {
                if (onPostClick != null) onPostClick.onClick(post);
            });
        }

        private String categoryLabel(String category) {
            if (category == null) return "";
            switch (category) {
                case CityPost.CATEGORY_NATURE:
                    return b.getRoot().getContext().getString(R.string.city_filter_nature);
                case CityPost.CATEGORY_PLACE:
                    return b.getRoot().getContext().getString(R.string.city_filter_place);
                case CityPost.CATEGORY_VENUE:
                    return b.getRoot().getContext().getString(R.string.city_filter_venue);
                case CityPost.CATEGORY_HISTORY:
                    return b.getRoot().getContext().getString(R.string.city_filter_history);
                default:
                    return "";
            }
        }
    }
}
