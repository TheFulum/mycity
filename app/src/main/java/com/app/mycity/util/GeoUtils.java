package com.app.mycity.util;

public class GeoUtils {

    public static final double MOGILEV_LAT = 53.9045;
    public static final double MOGILEV_LNG = 30.3416;
    public static final int DEFAULT_ZOOM = 13;

    public static String formatCoords(double lat, double lng) {
        return String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng);
    }

    private static final java.util.regex.Pattern COORD_PATTERN =
            java.util.regex.Pattern.compile("^-?\\d{1,3}[.,]\\d+\\s*,\\s*-?\\d{1,3}[.,]\\d+$");

    public static String displayAddress(String raw) {
        if (raw == null) return "Без адреса";
        String t = raw.trim();
        if (t.isEmpty()) return "Без адреса";
        if (COORD_PATTERN.matcher(t).matches()) return "Без адреса";
        return t;
    }
}
