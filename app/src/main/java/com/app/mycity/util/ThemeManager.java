package com.app.mycity.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static final String PREFS = "mycity_theme";
    private static final String KEY_MODE = "theme_mode";

    private ThemeManager() {}

    public static void applySavedTheme(Context context) {
        int mode = getSavedMode(context);
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }

    public static int getSavedMode(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_MODE, MODE_SYSTEM);
    }

    public static void saveAndApply(Context context, int mode) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }

    private static int toNightMode(int mode) {
        if (mode == MODE_LIGHT) return AppCompatDelegate.MODE_NIGHT_NO;
        if (mode == MODE_DARK) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}
