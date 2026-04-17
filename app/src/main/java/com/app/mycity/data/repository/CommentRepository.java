package com.app.mycity.data.repository;

import com.app.mycity.data.model.Comment;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CommentRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final IssueRepository issueRepo = new IssueRepository();

    private com.google.firebase.firestore.CollectionReference col(String issueId) {
        return db.collection(IssueRepository.COLLECTION).document(issueId).collection("comments");
    }

    public ListenerRegistration listen(String issueId, IssueRepository.Listener<List<Comment>> listener) {
        return col(issueId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        listener.onResult(new ArrayList<>(), error);
                        return;
                    }
                    listener.onResult(snap.toObjects(Comment.class), null);
                });
    }

    public Task<Void> upsert(String issueId, Comment comment, OnDone onDone) {
        DocumentReference ref = col(issueId).document(comment.getAuthorId());
        boolean isNew = comment.getCreatedAt() == null;
        if (isNew) comment.setCreatedAt(new Date());
        return ref.set(comment).addOnSuccessListener(v -> {
            if (isNew) issueRepo.incrementCommentCount(issueId, 1);
            if (onDone != null) onDone.done(true, null);
        }).addOnFailureListener(e -> {
            if (onDone != null) onDone.done(false, e);
        });
    }

    public Task<Void> delete(String issueId, String authorId, OnDone onDone) {
        return col(issueId).document(authorId).delete()
                .addOnSuccessListener(v -> {
                    issueRepo.incrementCommentCount(issueId, -1);
                    if (onDone != null) onDone.done(true, null);
                })
                .addOnFailureListener(e -> {
                    if (onDone != null) onDone.done(false, e);
                });
    }

    public interface OnDone { void done(boolean ok, Exception error); }
}
