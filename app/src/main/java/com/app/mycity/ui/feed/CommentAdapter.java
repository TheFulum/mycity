package com.app.mycity.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.mycity.data.model.Comment;
import com.app.mycity.databinding.ItemCommentBinding;
import com.app.mycity.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.VH> {

    public interface Callbacks {
        void onEdit(Comment c);
        void onDelete(Comment c);
    }
    public interface OnAuthorClick { void onClick(String authorId); }

    private List<Comment> items = new ArrayList<>();
    private final String myUid;
    private final Callbacks callbacks;
    private OnAuthorClick onAuthorClick;

    public CommentAdapter(String myUid, Callbacks callbacks) {
        this.myUid = myUid;
        this.callbacks = callbacks;
    }

    public void setOnAuthorClick(OnAuthorClick cb) { this.onAuthorClick = cb; }

    public void submit(List<Comment> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newList.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).getAuthorId();
                String nid = newList.get(n).getAuthorId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                Comment a = items.get(o), b = newList.get(n);
                return a.getRating() == b.getRating() && equals(a.getText(), b.getText());
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
        ItemCommentBinding b = ItemCommentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override public int getItemCount() { return items.size(); }

    class VH extends RecyclerView.ViewHolder {
        final ItemCommentBinding b;
        VH(ItemCommentBinding b) { super(b.getRoot()); this.b = b; }

        void bind(Comment c) {
            b.tvAuthor.setText(c.getAuthorName() != null ? c.getAuthorName() : "Аноним");
            b.tvText.setText(c.getText());
            b.tvDate.setText(DateUtils.format(c.getCreatedAt()));
            b.rating.setRating(c.getRating());

            b.ivCrown.setVisibility("admin".equals(c.getAuthorRole()) ? View.VISIBLE : View.GONE);

            String aid = c.getAuthorId();
            if (aid != null && onAuthorClick != null) {
                b.tvAuthor.setOnClickListener(v -> onAuthorClick.onClick(aid));
            } else {
                b.tvAuthor.setOnClickListener(null);
            }

            boolean mine = myUid != null && myUid.equals(c.getAuthorId());
            b.btnEdit.setVisibility(mine ? View.VISIBLE : View.GONE);
            b.btnDelete.setVisibility(mine ? View.VISIBLE : View.GONE);

            b.btnEdit.setOnClickListener(v -> { if (callbacks != null) callbacks.onEdit(c); });
            b.btnDelete.setOnClickListener(v -> { if (callbacks != null) callbacks.onDelete(c); });
        }
    }
}
