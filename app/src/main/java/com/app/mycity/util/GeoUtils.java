package com.app.mycity.util;

public class GeoUtils {

    public static final double MOGILEV_LAT = 53.9045;
    public static final double MOGILEV_LNG = 30.3416;
    public static final int DEFAULT_ZOOM = 13;

    public static String formatCoords(double lat, double lng) {
        return String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng);
    }
}
