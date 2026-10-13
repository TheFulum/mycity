package com.app.mycity.ui.news;

import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.NewsItem;
import com.app.mycity.databinding.ItemNewsBinding;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NewsCardAdapter extends RecyclerView.Adapter<NewsCardAdapter.VH> {

    public interface OnNewsClickListener {
        void onClick(NewsItem item);
    }

    private List<NewsItem> items = new ArrayList<>();
    private final OnNewsClickListener onClick;

    public NewsCardAdapter(OnNewsClickListener onClick) {
        this.onClick = onClick;
    }

    public void submit(List<NewsItem> newList) {
        final List<NewsItem> safeNew = newList != null ? newList : new ArrayList<>();
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return safeNew.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getId();
                String nid = safeNew.get(n).getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                NewsItem a = items.get(o), b = safeNew.get(n);
                return strEq(a.getTitle(), b.getTitle())
                        && strEq(a.getBody(), b.getBody())
                        && strEq(a.getCategory(), b.getCategory())
                        && strEq(a.getSourceName(), b.getSourceName())
                        && strEq(a.getImageUrl(), b.getImageUrl())
                        && dateEq(a.getPublishedAt(), b.getPublishedAt());
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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNewsBinding binding = ItemNewsBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), onClick);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemNewsBinding b;

        VH(ItemNewsBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(NewsItem item, OnNewsClickListener listener) {
            String imageUrl = item.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                b.ivNewsImage.setVisibility(View.VISIBLE);
                Glide.with(b.ivNewsImage.getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(b.ivNewsImage);
            } else {
                b.ivNewsImage.setVisibility(View.GONE);
            }

            b.tvCategoryBadge.setText(categoryLabel(item.getCategory()));
            int badgeColor = ContextCompat.getColor(b.getRoot().getContext(), categoryColor(item.getCategory()));
            b.tvCategoryBadge.setBackgroundTintList(ColorStateList.valueOf(badgeColor));

            b.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "");

            String body = item.getBody();
            if (body != null && !body.isEmpty()) {
                b.tvSnippet.setVisibility(View.VISIBLE);
                b.tvSnippet.setText(body);
            } else {
                b.tvSnippet.setVisibility(View.GONE);
            }

            b.tvSourceAndDate.setText(formatSourceAndDate(item));

            b.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onClick(item);
            });
        }

        private String categoryLabel(String category) {
            if (category == null) return b.getRoot().getContext().getString(R.string.news_category_unknown);
            switch (category) {
                case NewsItem.CATEGORY_IMPORTANT:
                    return b.getRoot().getContext().getString(R.string.news_category_important);
                case NewsItem.CATEGORY_DANGEROUS:
                    return b.getRoot().getContext().getString(R.string.news_category_dangerous);
                case NewsItem.CATEGORY_INTERESTING:
                    return b.getRoot().getContext().getString(R.string.news_category_interesting);
                default:
                    return b.getRoot().getContext().getString(R.string.news_category_unknown);
            }
        }

        @ColorRes
        private int categoryColor(String category) {
            if (category == null) return R.color.text_secondary;
            switch (category) {
                case NewsItem.CATEGORY_DANGEROUS:
                    return R.color.accent_red;
                case NewsItem.CATEGORY_IMPORTANT:
                    return R.color.accent_yellow;
                case NewsItem.CATEGORY_INTERESTING:
                    return R.color.accent_blue;
                default:
                    return R.color.text_secondary;
            }
        }

        private String formatSourceAndDate(NewsItem item) {
            String source = item.getSourceName() != null ? item.getSourceName() : "";
            String relative = "";
            Date published = item.getPublishedAt();
            if (published != null) {
                long now = System.currentTimeMillis();
                long time = published.getTime();
                CharSequence rel = DateUtils.getRelativeTimeSpanString(
                        time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
                relative = rel != null ? rel.toString() : "";
            }
            if (source.isEmpty() && relative.isEmpty()) return "";
            if (source.isEmpty()) return relative;
            if (relative.isEmpty()) return source;
            return b.getRoot().getContext().getString(R.string.news_source_and_date_format, source, relative);
        }
    }
}
