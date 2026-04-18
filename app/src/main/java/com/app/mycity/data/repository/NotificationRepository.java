package com.app.mycity.data.repository;

import com.app.mycity.data.model.Notification;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationRepository {

    private static final String COLLECTION = "notifications";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public Task<Void> send(String userId, String issueId, String issueTitle, String adminName) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("issueId", issueId);
        data.put("issueTitle", issueTitle);
        data.put("message", "Администратор " + adminName + " закрыл вашу заявку");
        data.put("createdAt", new Date());
        data.put("read", false);
        return db.collection(COLLECTION).document().set(data);
    }

    public ListenerRegistration listenUnreadCount(String userId, UnreadCountListener listener) {
        return db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .addSnapshotListener((snap, err) -> {
                    if (snap == null) { listener.onCount(0); return; }
                    int count = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        Boolean read = doc.getBoolean("read");
                        if (read == null || !read) count++;
                    }
                    listener.onCount(count);
                });
    }

    public ListenerRegistration listenAll(String userId, IssueRepository.Listener<List<Notification>> listener) {
        return db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .addSnapshotListener((snap, err) -> {
                    if (err != null || snap == null) {
                        listener.onResult(new java.util.ArrayList<>(), err);
                        return;
                    }
                    List<Notification> list = snap.toObjects(Notification.class);
                    list.sort((a, b) -> {
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });
                    listener.onResult(list, null);
                });
    }

    public void markAllRead(String userId) {
        db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) return;
                    WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                        Boolean read = doc.getBoolean("read");
                        if (read == null || !read) batch.update(doc.getReference(), "read", true);
                    }
                    batch.commit();
                });
    }

    public interface UnreadCountListener {
        void onCount(int count);
    }
}
