package com.app.mycity.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.Issue;
import com.app.mycity.databinding.ItemIssueCardBinding;
import com.app.mycity.util.DateUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class IssueCardAdapter extends RecyclerView.Adapter<IssueCardAdapter.VH> {

    public interface OnClick { void onClick(Issue issue); }
    public interface OnAuthorClick { void onClick(String authorId); }

    private List<Issue> items = new ArrayList<>();
    private final OnClick onClick;
    private OnAuthorClick onAuthorClick;

    public IssueCardAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void setOnAuthorClick(OnAuthorClick cb) { this.onAuthorClick = cb; }

    public void submit(List<Issue> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newList.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getId();
                String nid = newList.get(n).getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                Issue a = items.get(o), b = newList.get(n);
                return equals(a.getTitle(), b.getTitle())
                        && equals(a.getStatus(), b.getStatus())
                        && a.getCommentCount() == b.getCommentCount();
            }
            private boolean equals(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
        });
        items = new ArrayList<>(newList);
        result.dispatchUpdatesTo(this);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemIssueCardBinding b = ItemIssueCardBinding.inflate(
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
        final ItemIssueCardBinding b;

        VH(ItemIssueCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Issue issue) {
            b.tvTitle.setText(issue.getTitle());
            b.tvAddress.setText(issue.getAddress() != null ? issue.getAddress() : "");
            b.tvDescription.setText(issue.getDescription());
            b.tvDate.setText(DateUtils.format(issue.getCreatedAt()));

            String author = issue.getAuthorName();
            if (author != null && !author.isEmpty()) {
                b.tvAuthorName.setText(author);
                b.tvAuthorName.setVisibility(View.VISIBLE);
                String aid = issue.getAuthorId();
                if (aid != null && onAuthorClick != null) {
                    b.tvAuthorName.setOnClickListener(v -> onAuthorClick.onClick(aid));
                } else {
                    b.tvAuthorName.setOnClickListener(null);
                }
            } else {
                b.tvAuthorName.setVisibility(View.GONE);
            }

            if (issue.isResolved()) {
                b.tvStatus.setText(R.string.status_resolved);
                b.tvStatus.setBackgroundResource(R.drawable.bg_status_resolved);
            } else {
                b.tvStatus.setText(R.string.status_active);
                b.tvStatus.setBackgroundResource(R.drawable.bg_status_active);
            }

            if (issue.getPhotoUrls() != null && !issue.getPhotoUrls().isEmpty()) {
                Glide.with(b.ivPhoto.getContext())
                        .load(issue.getPhotoUrls().get(0))
                        .placeholder(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(b.ivPhoto);
                b.ivPhoto.setVisibility(View.VISIBLE);
            } else {
                b.ivPhoto.setVisibility(View.GONE);
            }

            b.getRoot().setOnClickListener(v -> { if (onClick != null) onClick.onClick(issue); });
        }
    }
}
