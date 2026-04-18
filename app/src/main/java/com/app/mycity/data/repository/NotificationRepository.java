package com.app.mycity.data.repository;

import android.util.Log;

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

    private static final String TAG = "NotifRepo";
    private static final String COLLECTION = "notifications";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public Task<Void> send(String userId, String issueId, String issueTitle, String adminName) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("issueId", issueId);
        data.put("issueTitle", issueTitle);
        String safeTitle = issueTitle != null && !issueTitle.isEmpty() ? issueTitle : "заявку";
        String safeAdmin = adminName != null && !adminName.isEmpty() ? adminName : "Администратор";
        data.put("message", safeAdmin + " закрыл заявку «" + safeTitle + "»");
        data.put("createdAt", new Date());
        data.put("read", false);
        return db.collection(COLLECTION).document().set(data)
                .addOnSuccessListener(v -> Log.d(TAG, "send OK for user=" + userId + " issue=" + issueId))
                .addOnFailureListener(e -> Log.e(TAG, "send FAILED for user=" + userId, e));
    }

    public Task<Void> delete(String notificationId) {
        return db.collection(COLLECTION).document(notificationId).delete()
                .addOnFailureListener(e -> Log.e(TAG, "delete FAILED " + notificationId, e));
    }

    public void deleteAll(String userId) {
        db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) return;
                    WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit();
                });
    }

    public ListenerRegistration listenUnreadCount(String userId, UnreadCountListener listener) {
        return db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) Log.e(TAG, "listenUnreadCount error", err);
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
                    if (err != null) Log.e(TAG, "listenAll error", err);
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
                    Log.d(TAG, "listenAll: " + list.size() + " notifications for " + userId);
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
