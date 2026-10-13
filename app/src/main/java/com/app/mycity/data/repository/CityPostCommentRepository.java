package com.app.mycity.data.repository;

import com.app.mycity.data.model.Comment;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CityPostCommentRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CityPostRepository postRepo = new CityPostRepository();

    private CollectionReference col(String postId) {
        return db.collection(CityPostRepository.COLLECTION).document(postId).collection("comments");
    }

    public ListenerRegistration listen(String postId, CityPostRepository.Listener<List<Comment>> listener) {
        return col(postId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        listener.onResult(new ArrayList<>(), error);
                        return;
                    }
                    listener.onResult(snap.toObjects(Comment.class), null);
                });
    }

    public Task<Void> upsert(String postId, Comment comment, OnDone onDone) {
        boolean isNew = comment.getId() == null || comment.getId().isEmpty();
        DocumentReference ref = isNew ? col(postId).document() : col(postId).document(comment.getId());
        if (isNew) comment.setId(ref.getId());
        if (isNew) comment.setCreatedAt(new Date());
        return ref.set(comment).addOnSuccessListener(v -> {
            if (isNew) postRepo.incrementCommentCount(postId, 1);
            if (onDone != null) onDone.done(true, null);
        }).addOnFailureListener(e -> {
            if (onDone != null) onDone.done(false, e);
        });
    }

    public Task<Void> delete(String postId, String commentId, OnDone onDone) {
        return col(postId).document(commentId).delete()
                .addOnSuccessListener(v -> {
                    postRepo.incrementCommentCount(postId, -1);
                    if (onDone != null) onDone.done(true, null);
                })
                .addOnFailureListener(e -> {
                    if (onDone != null) onDone.done(false, e);
                });
    }

    public interface OnDone { void done(boolean ok, Exception error); }
}
