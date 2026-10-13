package com.app.mycity.data.repository;

import androidx.annotation.NonNull;

import com.app.mycity.data.model.CityPost;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CityPostRepository {

    public static final String COLLECTION = "cityPosts";

    public enum CategoryFilter { ALL, NATURE, PLACE, VENUE, HISTORY }
    public enum SortField { DATE, LIKES, COMMENTS }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public DocumentReference newDocRef() {
        return db.collection(COLLECTION).document();
    }

    public Task<Void> save(CityPost post) {
        DocumentReference ref = (post.getId() != null)
                ? db.collection(COLLECTION).document(post.getId())
                : newDocRef();
        if (post.getId() == null) post.setId(ref.getId());
        return ref.set(post);
    }

    public ListenerRegistration listen(SortField sort, boolean asc, CategoryFilter filter, Listener<List<CityPost>> listener) {
        return listen(sort, asc, filter, false, listener);
    }

    public ListenerRegistration listen(SortField sort, boolean asc, CategoryFilter filter, boolean includeUnapproved, Listener<List<CityPost>> listener) {
        String field;
        switch (sort) {
            case LIKES: field = "likeCount"; break;
            case COMMENTS: field = "commentCount"; break;
            case DATE:
            default: field = "createdAt"; break;
        }
        Query q = db.collection(COLLECTION)
                .orderBy(field, asc ? Query.Direction.ASCENDING : Query.Direction.DESCENDING);

        return q.addSnapshotListener((snap, error) -> {
            if (error != null || snap == null) {
                listener.onResult(new ArrayList<>(), error);
                return;
            }
            List<CityPost> list = snap.toObjects(CityPost.class);
            if (!includeUnapproved) {
                List<CityPost> filteredApproved = new ArrayList<>();
                for (CityPost p : list) if (p != null && p.isApproved()) filteredApproved.add(p);
                list = filteredApproved;
            }
            if (filter != null && filter != CategoryFilter.ALL) {
                String want;
                switch (filter) {
                    case NATURE: want = CityPost.CATEGORY_NATURE; break;
                    case PLACE: want = CityPost.CATEGORY_PLACE; break;
                    case VENUE: want = CityPost.CATEGORY_VENUE; break;
                    case HISTORY: want = CityPost.CATEGORY_HISTORY; break;
                    default: want = null;
                }
                if (want != null) {
                    List<CityPost> filtered = new ArrayList<>();
                    for (CityPost p : list) if (want.equals(p.getCategory())) filtered.add(p);
                    list = filtered;
                }
            }
            listener.onResult(list, null);
        });
    }

    public ListenerRegistration listenByAuthor(String uid, Listener<List<CityPost>> listener) {
        return db.collection(COLLECTION)
                .whereEqualTo("authorId", uid)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        listener.onResult(new ArrayList<>(), error);
                        return;
                    }
                    List<CityPost> list = snap.toObjects(CityPost.class);
                    list.sort((a, b) -> {
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });
                    listener.onResult(list, null);
                });
    }

    public ListenerRegistration listenOne(String id, Listener<CityPost> listener) {
        return db.collection(COLLECTION).document(id).addSnapshotListener((snap, error) -> {
            if (error != null || snap == null || !snap.exists()) {
                listener.onResult(null, error);
                return;
            }
            listener.onResult(snap.toObject(CityPost.class), null);
        });
    }

    public Task<com.google.firebase.firestore.DocumentSnapshot> get(String id) {
        return db.collection(COLLECTION).document(id).get();
    }

    public Task<Void> like(String postId, String uid) {
        Map<String, Object> data = new HashMap<>();
        data.put("likedBy", FieldValue.arrayUnion(uid));
        data.put("likeCount", FieldValue.increment(1));
        return db.collection(COLLECTION).document(postId).update(data);
    }

    public Task<Void> unlike(String postId, String uid) {
        Map<String, Object> data = new HashMap<>();
        data.put("likedBy", FieldValue.arrayRemove(uid));
        data.put("likeCount", FieldValue.increment(-1));
        return db.collection(COLLECTION).document(postId).update(data);
    }

    public Task<Void> incrementCommentCount(String postId, int delta) {
        return db.collection(COLLECTION).document(postId)
                .update("commentCount", FieldValue.increment(delta));
    }

    public Task<Void> approve(String postId, String approvedBy) {
        Map<String, Object> data = new HashMap<>();
        data.put("approved", true);
        data.put("approvedAt", new java.util.Date());
        data.put("approvedBy", approvedBy);
        return db.collection(COLLECTION).document(postId).update(data);
    }

    public Task<Void> delete(String postId) {
        return db.collection(COLLECTION).document(postId).delete();
    }

    public interface Listener<T> {
        void onResult(@NonNull T result, Exception error);
    }
}
