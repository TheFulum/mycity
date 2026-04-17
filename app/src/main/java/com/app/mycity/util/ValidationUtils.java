package com.app.mycity.util;

import android.text.TextUtils;
import android.util.Patterns;

public class ValidationUtils {

    public static boolean isEmail(String s) {
        return !TextUtils.isEmpty(s) && Patterns.EMAIL_ADDRESS.matcher(s).matches();
    }

    public static boolean isPhone(String s) {
        return !TextUtils.isEmpty(s) && Patterns.PHONE.matcher(s).matches();
    }

    public static boolean inRange(String s, int min, int max) {
        if (s == null) return false;
        int len = s.trim().length();
        return len >= min && len <= max;
    }
}
