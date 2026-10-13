package com.app.mycity.data.repository;

import androidx.annotation.Nullable;

import com.app.mycity.data.model.NewsItem;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class NewsRepository {

    public static final String COLLECTION = "news";

    public enum CategoryFilter { ALL, IMPORTANT, DANGEROUS, INTERESTING }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public DocumentReference newDocRef() {
        return db.collection(COLLECTION).document();
    }

    public ListenerRegistration listen(CategoryFilter filter, Listener<List<NewsItem>> listener) {
        Query q = db.collection(COLLECTION)
                .orderBy("publishedAt", Query.Direction.DESCENDING);
        return q.addSnapshotListener((snap, error) -> {
            if (error != null || snap == null) {
                listener.onResult(new ArrayList<>(), error);
                return;
            }
            List<NewsItem> list = snap.toObjects(NewsItem.class);
            if (filter != null && filter != CategoryFilter.ALL) {
                String want;
                switch (filter) {
                    case IMPORTANT: want = NewsItem.CATEGORY_IMPORTANT; break;
                    case DANGEROUS: want = NewsItem.CATEGORY_DANGEROUS; break;
                    case INTERESTING: want = NewsItem.CATEGORY_INTERESTING; break;
                    default: want = null;
                }
                if (want != null) {
                    List<NewsItem> filtered = new ArrayList<>();
                    for (NewsItem n : list) if (want.equals(n.getCategory())) filtered.add(n);
                    list = filtered;
                }
            }
            listener.onResult(list, null);
        });
    }

    public ListenerRegistration listenOne(String id, Listener<NewsItem> listener) {
        return db.collection(COLLECTION).document(id).addSnapshotListener((snap, error) -> {
            if (error != null || snap == null || !snap.exists()) {
                listener.onResult(null, error);
                return;
            }
            listener.onResult(snap.toObject(NewsItem.class), null);
        });
    }

    public Task<DocumentSnapshot> get(String id) {
        return db.collection(COLLECTION).document(id).get();
    }

    public Task<Void> save(NewsItem item) {
        DocumentReference ref = (item.getId() != null)
                ? db.collection(COLLECTION).document(item.getId())
                : newDocRef();
        if (item.getId() == null) item.setId(ref.getId());
        return ref.set(item);
    }

    public Task<Void> upsertByExternalId(NewsItem item) {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        String externalId = item.getExternalId();
        if (externalId == null || externalId.isEmpty()) {
            return save(item);
        }
        db.collection(COLLECTION)
                .whereEqualTo("externalId", externalId)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && !snap.isEmpty()) {
                        DocumentReference ref = snap.getDocuments().get(0).getReference();
                        item.setId(ref.getId());
                        ref.set(item)
                                .addOnSuccessListener(v -> tcs.setResult(null))
                                .addOnFailureListener(tcs::setException);
                    } else {
                        save(item)
                                .addOnSuccessListener(v -> tcs.setResult(null))
                                .addOnFailureListener(tcs::setException);
                    }
                })
                .addOnFailureListener(tcs::setException);
        return tcs.getTask();
    }

    public Task<Void> delete(String id) {
        return db.collection(COLLECTION).document(id).delete();
    }

    public interface Listener<T> {
        void onResult(@Nullable T result, @Nullable Exception error);
    }
}
