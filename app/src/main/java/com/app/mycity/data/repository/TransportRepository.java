package com.app.mycity.data.repository;

import androidx.annotation.NonNull;

import com.app.mycity.data.model.TransportRoute;
import com.app.mycity.data.model.TransportStop;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class TransportRepository {

    public static final String COLLECTION_ROUTES = "transportRoutes";
    public static final String COLLECTION_STOPS = "transportStops";

    public enum TypeFilter { ALL, BUS, TRAM, TROLLEYBUS, MARSHRUTKA }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public DocumentReference newRouteRef() {
        return db.collection(COLLECTION_ROUTES).document();
    }

    public DocumentReference newStopRef() {
        return db.collection(COLLECTION_STOPS).document();
    }

    public ListenerRegistration listenRoutes(TypeFilter filter, Listener<List<TransportRoute>> listener) {
        Query q = db.collection(COLLECTION_ROUTES)
                .orderBy("routeNumber", Query.Direction.ASCENDING);
        return q.addSnapshotListener((snap, error) -> {
            if (error != null || snap == null) {
                listener.onResult(new ArrayList<>(), error);
                return;
            }
            List<TransportRoute> list = snap.toObjects(TransportRoute.class);
            if (filter != null && filter != TypeFilter.ALL) {
                String want;
                switch (filter) {
                    case BUS: want = TransportRoute.TYPE_BUS; break;
                    case TRAM: want = TransportRoute.TYPE_TRAM; break;
                    case TROLLEYBUS: want = TransportRoute.TYPE_TROLLEYBUS; break;
                    case MARSHRUTKA: want = TransportRoute.TYPE_MARSHRUTKA; break;
                    default: want = null;
                }
                if (want != null) {
                    List<TransportRoute> filtered = new ArrayList<>();
                    for (TransportRoute r : list) if (want.equals(r.getType())) filtered.add(r);
                    list = filtered;
                }
            }
            listener.onResult(list, null);
        });
    }

    public ListenerRegistration listenStops(Listener<List<TransportStop>> listener) {
        return db.collection(COLLECTION_STOPS)
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        listener.onResult(new ArrayList<>(), error);
                        return;
                    }
                    listener.onResult(snap.toObjects(TransportStop.class), null);
                });
    }

    public Task<DocumentSnapshot> getRoute(String id) {
        return db.collection(COLLECTION_ROUTES).document(id).get();
    }

    public Task<DocumentSnapshot> getStop(String id) {
        return db.collection(COLLECTION_STOPS).document(id).get();
    }

    public Task<Void> saveRoute(TransportRoute route) {
        DocumentReference ref = (route.getId() != null)
                ? db.collection(COLLECTION_ROUTES).document(route.getId())
                : newRouteRef();
        if (route.getId() == null) route.setId(ref.getId());
        return ref.set(route);
    }

    public Task<Void> saveStop(TransportStop stop) {
        DocumentReference ref = (stop.getId() != null)
                ? db.collection(COLLECTION_STOPS).document(stop.getId())
                : newStopRef();
        if (stop.getId() == null) stop.setId(ref.getId());
        return ref.set(stop);
    }

    public Task<Void> deleteRoute(String id) {
        return db.collection(COLLECTION_ROUTES).document(id).delete();
    }

    public Task<Void> deleteStop(String id) {
        return db.collection(COLLECTION_STOPS).document(id).delete();
    }

    public interface Listener<T> {
        void onResult(@NonNull T result, Exception error);
    }
}
