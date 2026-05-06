package com.app.mycity.ui.admin;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.R;
import com.app.mycity.data.model.Issue;
import com.app.mycity.databinding.ItemAdminIssueBinding;
import com.app.mycity.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class AdminIssueAdapter extends RecyclerView.Adapter<AdminIssueAdapter.VH> {

    public interface Actions {
        void onOpen(Issue issue);
        void onApprove(Issue issue);
        void onReject(Issue issue);
        void onToggleStatus(Issue issue);
        void onDelete(Issue issue);
    }

    private List<Issue> items = new ArrayList<>();
    private final Actions actions;

    public AdminIssueAdapter(Actions actions) {
        this.actions = actions;
    }

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
                return equals(a.getStatus(), b.getStatus())
                        && equals(a.getTitle(), b.getTitle())
                        && a.isApproved() == b.isApproved()
                        && equals(a.getRejectedReason(), b.getRejectedReason());
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
        ItemAdminIssueBinding b = ItemAdminIssueBinding.inflate(
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
        final ItemAdminIssueBinding b;

        VH(ItemAdminIssueBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(Issue issue) {
            b.tvTitle.setText(issue.getTitle());
            String author = issue.getAuthorName() != null ? issue.getAuthorName() : "Аноним";
            b.tvAuthor.setText("Автор: " + author);
            b.tvDate.setText(DateUtils.format(issue.getCreatedAt()));
            boolean approved = issue.isApproved();
            boolean rejected = issue.isRejected();
            b.btnApprove.setVisibility(!approved && !rejected ? android.view.View.VISIBLE : android.view.View.GONE);
            b.btnReject.setVisibility(!approved && !rejected ? android.view.View.VISIBLE : android.view.View.GONE);
            b.btnAnnul.setVisibility(approved && !rejected ? android.view.View.VISIBLE : android.view.View.GONE);
            b.btnToggle.setVisibility(approved && !rejected ? android.view.View.VISIBLE : android.view.View.GONE);
            b.btnDelete.setVisibility(approved || rejected ? android.view.View.VISIBLE : android.view.View.GONE);

            applyButtonRowSpacing();

            if (rejected) {
                b.tvStatus.setText("Отклонена");
                b.tvStatus.setBackgroundResource(R.drawable.bg_status_rejected);
            } else if (!approved) {
                b.tvStatus.setText("На модерации");
                b.tvStatus.setBackgroundResource(R.drawable.bg_status_active);
            } else if (issue.isResolved()) {
                b.tvStatus.setText(R.string.status_resolved);
                b.tvStatus.setBackgroundResource(R.drawable.bg_status_resolved);
                b.btnToggle.setText("Вернуть в активные");
            } else {
                b.tvStatus.setText(R.string.status_active);
                b.tvStatus.setBackgroundResource(R.drawable.bg_status_active);
                b.btnToggle.setText("Закрыть как выполненное");
            }
            b.getRoot().setOnClickListener(v -> actions.onOpen(issue));
            b.btnApprove.setOnClickListener(v -> actions.onApprove(issue));
            b.btnReject.setOnClickListener(v -> actions.onReject(issue));
            b.btnAnnul.setOnClickListener(v -> actions.onReject(issue));
            b.btnToggle.setOnClickListener(v -> actions.onToggleStatus(issue));
            b.btnDelete.setOnClickListener(v -> actions.onDelete(issue));
        }

        private void applyButtonRowSpacing() {
            android.view.View[] views = new android.view.View[]{
                    b.btnApprove, b.btnReject, b.btnAnnul, b.btnDelete
            };
            int px8 = (int) (8 * b.btnToggle.getResources().getDisplayMetrics().density);
            boolean firstVisible = true;
            for (android.view.View v : views) {
                if (v.getVisibility() != android.view.View.VISIBLE) continue;
                android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams mlp = (android.view.ViewGroup.MarginLayoutParams) lp;
                    mlp.leftMargin = firstVisible ? 0 : px8;
                    v.setLayoutParams(mlp);
                }
                firstVisible = false;
            }
        }
    }
}
