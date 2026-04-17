package com.app.mycity.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
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

    private final List<Issue> items = new ArrayList<>();
    private final OnClick onClick;

    public IssueCardAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<Issue> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
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
