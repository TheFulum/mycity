package com.app.mycity.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

    private static final SimpleDateFormat FULL =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", new Locale("ru"));
    private static final SimpleDateFormat SHORT =
            new SimpleDateFormat("dd.MM.yyyy", new Locale("ru"));

    public static String format(Date date) {
        if (date == null) return "";
        return FULL.format(date);
    }

    public static String formatShort(Date date) {
        if (date == null) return "";
        return SHORT.format(date);
    }
}
