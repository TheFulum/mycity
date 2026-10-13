package com.app.mycity.data.repository;

import androidx.annotation.NonNull;

import com.app.mycity.data.model.ChatMessage;
import com.app.mycity.data.model.ChatThread;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRepository {

    public static final String COLLECTION_CHATS = "chats";
    public static final String SUBCOLLECTION_MESSAGES = "messages";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ListenerRegistration listenUserChats(String uid, Listener<List<ChatThread>> listener) {
        return db.collection(COLLECTION_CHATS)
                .whereArrayContains("participantIds", uid)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        listener.onResult(new ArrayList<>(), error);
                        return;
                    }
                    listener.onResult(snap.toObjects(ChatThread.class), null);
                });
    }

    public ListenerRegistration listenMessages(String chatId, Listener<List<ChatMessage>> listener) {
        return db.collection(COLLECTION_CHATS).document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        listener.onResult(new ArrayList<>(), error);
                        return;
                    }
                    listener.onResult(snap.toObjects(ChatMessage.class), null);
                });
    }

    public ListenerRegistration listenOne(String chatId, Listener<ChatThread> listener) {
        return db.collection(COLLECTION_CHATS).document(chatId).addSnapshotListener((snap, error) -> {
            if (error != null || snap == null || !snap.exists()) {
                listener.onResult(null, error);
                return;
            }
            listener.onResult(snap.toObject(ChatThread.class), null);
        });
    }

    public Task<Void> sendMessage(String chatId, ChatMessage msg) {
        DocumentReference chatRef = db.collection(COLLECTION_CHATS).document(chatId);
        DocumentReference msgRef = chatRef.collection(SUBCOLLECTION_MESSAGES).document();
        msg.setId(msgRef.getId());
        msg.setChatId(chatId);
        if (msg.getSentAt() == null) msg.setSentAt(new Date());
        if (msg.getStatus() == null) msg.setStatus(ChatMessage.STATUS_SENT);

        return db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snap = transaction.get(chatRef);
            if (!snap.exists()) {
                throw new FirebaseFirestoreException(
                        "Chat not found: " + chatId,
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }
            ChatThread thread = snap.toObject(ChatThread.class);

            Map<String, Object> threadUpdate = new HashMap<>();
            threadUpdate.put("lastMessage", msg.getText());
            threadUpdate.put("lastMessageSenderId", msg.getSenderId());
            threadUpdate.put("lastMessageAt", msg.getSentAt());

            List<String> participants = thread != null ? thread.getParticipantIds() : null;
            if (participants != null) {
                for (String pid : participants) {
                    if (pid != null && !pid.equals(msg.getSenderId())) {
                        threadUpdate.put("unreadCounts." + pid, FieldValue.increment(1));
                    }
                }
            }

            transaction.set(msgRef, msg);
            transaction.update(chatRef, threadUpdate);
            return null;
        });
    }

    public Task<Void> markRead(String chatId, String uid) {
        return db.collection(COLLECTION_CHATS).document(chatId)
                .update("unreadCounts." + uid, 0);
    }

    public Task<DocumentReference> createDirectChat(String currentUid,
                                                    String currentName,
                                                    String otherUid,
                                                    String otherName) {
        TaskCompletionSource<DocumentReference> tcs = new TaskCompletionSource<>();
        db.collection(COLLECTION_CHATS)
                .whereEqualTo("type", ChatThread.TYPE_DIRECT)
                .whereArrayContains("participantIds", currentUid)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null) {
                        for (QueryDocumentSnapshot d : snap) {
                            ChatThread t = d.toObject(ChatThread.class);
                            List<String> pids = t.getParticipantIds();
                            if (pids != null && pids.size() == 2 && pids.contains(otherUid)) {
                                tcs.setResult(d.getReference());
                                return;
                            }
                        }
                    }
                    DocumentReference newRef = db.collection(COLLECTION_CHATS).document();
                    ChatThread thread = new ChatThread();
                    thread.setId(newRef.getId());
                    thread.setType(ChatThread.TYPE_DIRECT);
                    List<String> pids = new ArrayList<>();
                    pids.add(currentUid);
                    pids.add(otherUid);
                    thread.setParticipantIds(pids);
                    Map<String, String> names = new HashMap<>();
                    names.put(currentUid, currentName);
                    names.put(otherUid, otherName);
                    thread.setParticipantNames(names);
                    thread.setCreatedBy(currentUid);
                    thread.setCreatedAt(new Date());
                    thread.setLastMessageAt(new Date());
                    Map<String, Integer> unread = new HashMap<>();
                    unread.put(currentUid, 0);
                    unread.put(otherUid, 0);
                    thread.setUnreadCounts(unread);
                    newRef.set(thread)
                            .addOnSuccessListener(v -> tcs.setResult(newRef))
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);
        return tcs.getTask();
    }

    public Task<DocumentReference> createGroupChat(String name,
                                                   String createdBy,
                                                   List<String> participantIds,
                                                   Map<String, String> participantNames) {
        TaskCompletionSource<DocumentReference> tcs = new TaskCompletionSource<>();
        DocumentReference newRef = db.collection(COLLECTION_CHATS).document();
        ChatThread thread = new ChatThread();
        thread.setId(newRef.getId());
        thread.setType(ChatThread.TYPE_GROUP);
        thread.setName(name);
        thread.setParticipantIds(participantIds != null ? new ArrayList<>(participantIds) : new ArrayList<>());
        thread.setParticipantNames(participantNames != null ? new HashMap<>(participantNames) : new HashMap<>());
        thread.setCreatedBy(createdBy);
        thread.setCreatedAt(new Date());
        thread.setLastMessageAt(new Date());
        Map<String, Integer> unread = new HashMap<>();
        if (participantIds != null) {
            for (String pid : participantIds) unread.put(pid, 0);
        }
        thread.setUnreadCounts(unread);
        newRef.set(thread)
                .addOnSuccessListener(v -> tcs.setResult(newRef))
                .addOnFailureListener(tcs::setException);
        return tcs.getTask();
    }

    public Task<DocumentSnapshot> getChat(String id) {
        return db.collection(COLLECTION_CHATS).document(id).get();
    }

    public interface Listener<T> {
        void onResult(@NonNull T result, Exception error);
    }
}
