package com.app.mycity.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Хранилище черновика формы "Новая заявка" в SharedPreferences. */
public class DraftStore {

    private static final String PREFS = "mycity_draft_issue";
    private static final String KEY_TITLE = "title";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_PHOTOS = "photos";
    private static final String KEY_GUEST_NAME = "guest_name";
    private static final String KEY_GUEST_CONTACT = "guest_contact";

    private final SharedPreferences prefs;

    public DraftStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasDraft() {
        return prefs.contains(KEY_TITLE) || prefs.contains(KEY_DESCRIPTION)
                || prefs.contains(KEY_PHOTOS) || prefs.contains(KEY_LAT);
    }

    public void save(String title, String description,
                     Double lat, Double lng, String address,
                     List<Uri> photos,
                     String guestName, String guestContact) {
        SharedPreferences.Editor e = prefs.edit();
        e.putString(KEY_TITLE, title != null ? title : "");
        e.putString(KEY_DESCRIPTION, description != null ? description : "");
        if (lat != null && lng != null) {
            e.putLong(KEY_LAT, Double.doubleToRawLongBits(lat));
            e.putLong(KEY_LNG, Double.doubleToRawLongBits(lng));
        } else {
            e.remove(KEY_LAT).remove(KEY_LNG);
        }
        e.putString(KEY_ADDRESS, address != null ? address : "");

        Set<String> uriSet = new HashSet<>();
        if (photos != null) {
            for (Uri u : photos) if (u != null) uriSet.add(u.toString());
        }
        e.putStringSet(KEY_PHOTOS, uriSet);

        e.putString(KEY_GUEST_NAME, guestName != null ? guestName : "");
        e.putString(KEY_GUEST_CONTACT, guestContact != null ? guestContact : "");
        e.apply();
    }

    public String getTitle() { return prefs.getString(KEY_TITLE, ""); }
    public String getDescription() { return prefs.getString(KEY_DESCRIPTION, ""); }
    public String getAddress() { return prefs.getString(KEY_ADDRESS, ""); }
    public String getGuestName() { return prefs.getString(KEY_GUEST_NAME, ""); }
    public String getGuestContact() { return prefs.getString(KEY_GUEST_CONTACT, ""); }

    public Double getLat() {
        if (!prefs.contains(KEY_LAT)) return null;
        return Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0));
    }

    public Double getLng() {
        if (!prefs.contains(KEY_LNG)) return null;
        return Double.longBitsToDouble(prefs.getLong(KEY_LNG, 0));
    }

    public List<Uri> getPhotos() {
        Set<String> set = prefs.getStringSet(KEY_PHOTOS, Collections.emptySet());
        List<Uri> uris = new ArrayList<>();
        if (set != null) {
            for (String s : set) uris.add(Uri.parse(s));
        }
        return uris;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
