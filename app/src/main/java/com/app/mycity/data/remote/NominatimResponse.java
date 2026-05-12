package com.app.mycity.data.remote;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class NominatimResponse {

    @SerializedName("display_name")
    public String displayName;

    @SerializedName("address")
    public Address address;

    /**
     * OSM name tags (name:ru, …). Values are usually strings; Gson may use other types — read safely.
     */
    @SerializedName("namedetails")
    public Map<String, Object> namedetails;

    public static class Address {
        public String road;
        public String pedestrian;
        public String footway;
        public String path;
        public String residential;
        @SerializedName("house_number")
        public String houseNumber;
        public String suburb;
        @SerializedName("city_district")
        public String cityDistrict;
        public String neighbourhood;
        public String quarter;
        public String city;
        public String town;
        public String village;
        public String hamlet;
    }

    /**
     * Short line for UI: structured address, else shortened {@code display_name} (often Russian
     * when Accept-Language is set).
     */
    public String shortAddress() {
        String structured = shortAddressStructured();
        if (structured != null) {
            String t = structured.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return shortenDisplayName(displayName);
    }

    private String shortAddressStructured() {
        if (address == null) {
            return null;
        }
        String street = firstNonEmpty(
                address.road, address.pedestrian, address.footway,
                address.path, address.residential);
        street = preferRussianStreetLabel(street);
        StringBuilder sb = new StringBuilder();
        if (street != null) {
            sb.append(street);
        }
        if (address.houseNumber != null && !address.houseNumber.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(address.houseNumber);
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        String area = firstNonEmpty(
                address.neighbourhood, address.quarter, address.suburb,
                address.cityDistrict);
        String locality = firstNonEmpty(
                address.city, address.town, address.village, address.hamlet);
        if (area != null && locality != null) {
            return area + ", " + locality;
        }
        if (area != null) {
            return area;
        }
        if (locality != null) {
            return locality;
        }
        return null;
    }

    private static String shortenDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String t = displayName.trim();
        if (t.isEmpty()) {
            return null;
        }
        String[] parts = t.split(",\\s*");
        int max = Math.min(parts.length, 5);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parts[i].trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String preferRussianStreetLabel(String fromAddress) {
        if (fromAddress == null || namedetails == null) {
            return fromAddress;
        }
        String ru = firstNonEmpty(detailString("name:ru"), detailString("official_name:ru"));
        if (ru != null) {
            return ru;
        }
        return fromAddress;
    }

    private String detailString(String key) {
        if (namedetails == null) {
            return null;
        }
        Object v = namedetails.get(key);
        if (v instanceof String) {
            String s = ((String) v).trim();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    private static String firstNonEmpty(String... vs) {
        for (String v : vs) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
