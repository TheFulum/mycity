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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.VH> {

    public interface Callbacks {
        void onEdit(Comment c);
        void onDelete(Comment c);
        void onReply(Comment c);
        void onSendInlineReply(Comment parent, String text);
        void onGoToComment(String commentId);
    }
    public interface OnAuthorClick { void onClick(String authorId); }

    private List<Row> items = new ArrayList<>();
    private final Map<String, Comment> byId = new HashMap<>();
    private final String myUid;
    private final boolean isAdmin;
    private final Callbacks callbacks;
    private OnAuthorClick onAuthorClick;
    private String replyingToId;

    public CommentAdapter(String myUid, boolean isAdmin, Callbacks callbacks) {
        this.myUid = myUid;
        this.isAdmin = isAdmin;
        this.callbacks = callbacks;
    }

    public void setOnAuthorClick(OnAuthorClick cb) { this.onAuthorClick = cb; }

    public void openInlineReply(Comment c) {
        if (c == null || c.getId() == null) return;
        String prev = replyingToId;
        replyingToId = c.getId();
        if (prev != null) notifyItemChanged(findPositionById(prev));
        notifyItemChanged(findPositionById(replyingToId));
    }

    public void closeInlineReply() {
        String prev = replyingToId;
        replyingToId = null;
        if (prev != null) notifyItemChanged(findPositionById(prev));
    }

    public void submit(List<Comment> newList) {
        byId.clear();
        for (Comment c : newList) {
            if (c.getId() != null) byId.put(c.getId(), c);
        }
        List<Row> flattened = flattenThread(newList);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return flattened.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                String oid = items.get(o).comment.getId();
                String nid = flattened.get(n).comment.getId();
                return oid != null && oid.equals(nid);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                Row a = items.get(o), b = flattened.get(n);
                return a.depth == b.depth
                        && a.comment.getRating() == b.comment.getRating()
                        && equals(a.comment.getText(), b.comment.getText())
                        && equals(a.comment.getParentCommentId(), b.comment.getParentCommentId())
                        && isReplyingTo(a.comment) == isReplyingTo(b.comment);
            }
            private boolean equals(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
            private boolean isReplyingTo(Comment c) {
                return c != null && c.getId() != null && c.getId().equals(replyingToId);
            }
        });
        items = flattened;
        result.dispatchUpdatesTo(this);
    }

    public int findPositionById(String commentId) {
        if (commentId == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (commentId.equals(items.get(i).comment.getId())) return i;
        }
        return -1;
    }

    private List<Row> flattenThread(List<Comment> comments) {
        Map<String, List<Comment>> byParent = new HashMap<>();
        List<Comment> roots = new ArrayList<>();
        for (Comment c : comments) {
            String parentId = c.getParentCommentId();
            if (parentId == null || parentId.isEmpty()) {
                roots.add(c);
            } else {
                byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(c);
            }
        }
        Comparator<Comment> byDate = (a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return -1;
            if (b.getCreatedAt() == null) return 1;
            return a.getCreatedAt().compareTo(b.getCreatedAt());
        };
        Collections.sort(roots, byDate);
        for (List<Comment> replies : byParent.values()) {
            Collections.sort(replies, byDate);
        }
        List<Row> flat = new ArrayList<>();
        for (Comment root : roots) {
            appendNode(root, 0, byParent, flat);
        }
        return flat;
    }

    private void appendNode(Comment node, int depth, Map<String, List<Comment>> byParent, List<Row> out) {
        out.add(new Row(node, depth));
        if (node.getId() == null) return;
        List<Comment> replies = byParent.get(node.getId());
        if (replies == null) return;
        for (Comment child : replies) {
            appendNode(child, depth + 1, byParent, out);
        }
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

        void bind(Row row) {
            Comment c = row.comment;
            b.tvAuthor.setText(c.getAuthorName() != null ? c.getAuthorName() : "Аноним");
            b.tvText.setText(c.getText());
            b.tvDate.setText(DateUtils.format(c.getCreatedAt()));
            b.rating.setRating(c.getRating());
            boolean isReply = c.getParentCommentId() != null && !c.getParentCommentId().isEmpty();
            b.rating.setVisibility(isReply ? View.GONE : View.VISIBLE);

            int base = (int) (12 * itemView.getResources().getDisplayMetrics().density);
            int indent = (int) (12 * itemView.getResources().getDisplayMetrics().density) * Math.min(row.depth, 6);
            b.commentRoot.setPadding(base + indent, base, base, base);

            b.ivCrown.setVisibility("admin".equals(c.getAuthorRole()) ? View.VISIBLE : View.GONE);

            String aid = c.getAuthorId();
            if (aid != null && onAuthorClick != null) {
                b.tvAuthor.setOnClickListener(v -> onAuthorClick.onClick(aid));
            } else {
                b.tvAuthor.setOnClickListener(null);
            }

            boolean mine = myUid != null && myUid.equals(c.getAuthorId());
            boolean canModerate = mine || isAdmin;
            b.btnEdit.setVisibility(canModerate ? View.VISIBLE : View.GONE);
            b.btnDelete.setVisibility(canModerate ? View.VISIBLE : View.GONE);

            b.btnEdit.setOnClickListener(v -> { if (callbacks != null) callbacks.onEdit(c); });
            b.btnDelete.setOnClickListener(v -> { if (callbacks != null) callbacks.onDelete(c); });
            b.btnReply.setOnClickListener(v -> { if (callbacks != null) callbacks.onReply(c); });

            String parentId = c.getParentCommentId();
            if (parentId != null && !parentId.isEmpty()) {
                Comment parent = byId.get(parentId);
                String label = parent != null
                        ? formatReplyLabel(parent)
                        : "К предыдущему комментарию";
                b.tvReplyTo.setText(label);
                b.layoutReplyTo.setVisibility(View.VISIBLE);
                b.layoutReplyTo.setOnClickListener(v -> {
                    if (callbacks != null) callbacks.onGoToComment(parentId);
                });
            } else {
                b.layoutReplyTo.setVisibility(View.GONE);
                b.layoutReplyTo.setOnClickListener(null);
            }

            boolean showInline = c.getId() != null && c.getId().equals(replyingToId);
            b.layoutInlineReply.setVisibility(showInline ? View.VISIBLE : View.GONE);
            if (showInline) {
                b.etInlineReply.requestFocus();
                b.btnInlineCancel.setOnClickListener(v -> closeInlineReply());
                b.btnInlineSend.setOnClickListener(v -> {
                    String text = b.etInlineReply.getText() != null ? b.etInlineReply.getText().toString().trim() : "";
                    if (text.isEmpty()) return;
                    b.btnInlineSend.setEnabled(false);
                    if (callbacks != null) callbacks.onSendInlineReply(c, text);
                    b.btnInlineSend.setEnabled(true);
                    b.etInlineReply.setText("");
                    closeInlineReply();
                });
            } else {
                b.btnInlineSend.setOnClickListener(null);
                b.btnInlineCancel.setOnClickListener(null);
            }
        }

        private String formatReplyLabel(Comment parent) {
            String author = parent.getAuthorName();
            if (author == null || author.trim().isEmpty()) {
                author = "пользователь";
            }

            String parentText = parent.getText();
            if (parentText == null || parentText.trim().isEmpty()) {
                return "Ответ для @" + author;
            }

            String normalized = parentText.replace('\n', ' ').trim();
            if (normalized.length() > 38) {
                normalized = normalized.substring(0, 38).trim() + "…";
            }
            return "Ответ для @" + author + ": " + normalized;
        }
    }

    static class Row {
        final Comment comment;
        final int depth;
        Row(Comment comment, int depth) {
            this.comment = comment;
            this.depth = depth;
        }
    }
}
