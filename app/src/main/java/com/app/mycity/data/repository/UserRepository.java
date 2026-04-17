package com.app.mycity.data.repository;

import com.app.mycity.data.model.UserProfile;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference doc(String uid) {
        return db.collection("users").document(uid);
    }

    public Task<DocumentSnapshot> get(String uid) {
        return doc(uid).get();
    }

    public ListenerRegistration listen(String uid, IssueRepository.Listener<UserProfile> listener) {
        return doc(uid).addSnapshotListener((snap, error) -> {
            if (error != null || snap == null || !snap.exists()) {
                listener.onResult(new UserProfile(), error);
                return;
            }
            UserProfile p = snap.toObject(UserProfile.class);
            if (p != null) p.setUid(snap.getId());
            listener.onResult(p != null ? p : new UserProfile(), null);
        });
    }

    public Task<Void> save(UserProfile profile) {
        return doc(profile.getUid()).set(profile);
    }

    public Task<Void> upsert(String uid, String displayName, String email, String phone) {
        Map<String, Object> data = new HashMap<>();
        if (displayName != null) data.put("displayName", displayName);
        if (email != null) data.put("email", email);
        if (phone != null) data.put("phone", phone);
        data.put("createdAt", FieldValue.serverTimestamp());
        return doc(uid).set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    public Task<Void> updateAvatar(String uid, String url) {
        return doc(uid).update("avatarUrl", url);
    }

    public Task<Void> updateName(String uid, String name) {
        return doc(uid).update("displayName", name);
    }

    public Task<Void> incrementIssueCount(String uid, int delta) {
        return doc(uid).update("issueCount", FieldValue.increment(delta));
    }
}
