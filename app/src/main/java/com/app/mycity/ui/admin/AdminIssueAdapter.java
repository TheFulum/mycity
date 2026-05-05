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
                        && a.isApproved() == b.isApproved();
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
            b.btnApprove.setVisibility(approved ? android.view.View.GONE : android.view.View.VISIBLE);
            b.btnReject.setVisibility(approved ? android.view.View.GONE : android.view.View.VISIBLE);
            b.btnToggle.setVisibility(approved ? android.view.View.VISIBLE : android.view.View.GONE);
            b.btnDelete.setVisibility(approved ? android.view.View.VISIBLE : android.view.View.GONE);

            updateToggleStartMargin(approved);

            if (!approved) {
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
            b.btnToggle.setOnClickListener(v -> actions.onToggleStatus(issue));
            b.btnDelete.setOnClickListener(v -> actions.onDelete(issue));
        }

        private void updateToggleStartMargin(boolean approved) {
            android.view.ViewGroup.LayoutParams lp = b.btnToggle.getLayoutParams();
            if (!(lp instanceof android.view.ViewGroup.MarginLayoutParams)) return;
            android.view.ViewGroup.MarginLayoutParams mlp = (android.view.ViewGroup.MarginLayoutParams) lp;
            int px8 = (int) (8 * b.btnToggle.getResources().getDisplayMetrics().density);
            mlp.leftMargin = 0;
            b.btnToggle.setLayoutParams(mlp);
        }
    }
}
