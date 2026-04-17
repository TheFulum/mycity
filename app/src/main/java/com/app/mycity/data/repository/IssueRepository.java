package com.app.mycity.data.repository;

import androidx.annotation.NonNull;

import com.app.mycity.data.model.Issue;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class IssueRepository {

    public static final String COLLECTION = "issues";

    public enum SortField { DATE, COMMENTS }
    public enum StatusFilter { ALL, ACTIVE, RESOLVED }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public DocumentReference newDocRef() {
        return db.collection(COLLECTION).document();
    }

    public Task<Void> save(Issue issue) {
        DocumentReference ref = (issue.getId() != null)
                ? db.collection(COLLECTION).document(issue.getId())
                : newDocRef();
        if (issue.getId() == null) issue.setId(ref.getId());
        return ref.set(issue);
    }

    public ListenerRegistration listen(SortField sort, boolean asc, StatusFilter filter, Listener<List<Issue>> listener) {
        Query q = db.collection(COLLECTION);
        if (filter == StatusFilter.ACTIVE) {
            q = q.whereEqualTo("status", Issue.STATUS_ACTIVE);
        } else if (filter == StatusFilter.RESOLVED) {
            q = q.whereEqualTo("status", Issue.STATUS_RESOLVED);
        }
        String field = sort == SortField.COMMENTS ? "commentCount" : "createdAt";
        q = q.orderBy(field, asc ? Query.Direction.ASCENDING : Query.Direction.DESCENDING);

        return q.addSnapshotListener((snap, error) -> {
            if (error != null || snap == null) {
                listener.onResult(new ArrayList<>(), error);
                return;
            }
            List<Issue> list = snap.toObjects(Issue.class);
            listener.onResult(list, null);
        });
    }

    public ListenerRegistration listenByAuthor(String uid, boolean resolved, Listener<List<Issue>> listener) {
        Query q = db.collection(COLLECTION)
                .whereEqualTo("authorId", uid)
                .whereEqualTo("status", resolved ? Issue.STATUS_RESOLVED : Issue.STATUS_ACTIVE)
                .orderBy("createdAt", Query.Direction.DESCENDING);
        return q.addSnapshotListener((snap, error) -> {
            if (error != null || snap == null) {
                listener.onResult(new ArrayList<>(), error);
                return;
            }
            listener.onResult(snap.toObjects(Issue.class), null);
        });
    }

    public Task<QuerySnapshot> activeIssues() {
        return db.collection(COLLECTION)
                .whereEqualTo("status", Issue.STATUS_ACTIVE)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();
    }

    public Task<com.google.firebase.firestore.DocumentSnapshot> get(String id) {
        return db.collection(COLLECTION).document(id).get();
    }

    public ListenerRegistration listenOne(String id, Listener<Issue> listener) {
        return db.collection(COLLECTION).document(id).addSnapshotListener((snap, error) -> {
            if (error != null || snap == null || !snap.exists()) {
                listener.onResult(null, error);
                return;
            }
            listener.onResult(snap.toObject(Issue.class), null);
        });
    }

    public Task<Void> incrementCommentCount(String issueId, int delta) {
        return db.collection(COLLECTION).document(issueId)
                .update("commentCount", FieldValue.increment(delta));
    }

    public interface Listener<T> {
        void onResult(@NonNull T result, Exception error);
    }
}
