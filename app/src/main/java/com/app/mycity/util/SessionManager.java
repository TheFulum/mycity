package com.app.mycity.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREFS = "mycity_session";
    private static final String KEY_GUEST = "is_guest";
    private static final String KEY_ONBOARDING_DONE = "onboarding_completed";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void setGuest(boolean guest) {
        prefs.edit().putBoolean(KEY_GUEST, guest).apply();
    }

    public boolean isGuest() {
        return prefs.getBoolean(KEY_GUEST, false);
    }

    public void setOnboardingCompleted(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    public boolean isOnboardingCompleted() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
