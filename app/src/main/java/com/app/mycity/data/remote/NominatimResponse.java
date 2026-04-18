package com.app.mycity.data.remote;

import com.google.gson.annotations.SerializedName;

public class NominatimResponse {

    @SerializedName("display_name")
    public String displayName;

    @SerializedName("address")
    public Address address;

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

    public String shortAddress() {
        if (address != null) {
            String street = firstNonEmpty(
                    address.road, address.pedestrian, address.footway,
                    address.path, address.residential);
            StringBuilder sb = new StringBuilder();
            if (street != null) sb.append(street);
            if (address.houseNumber != null && !address.houseNumber.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(address.houseNumber);
            }
            if (sb.length() > 0) return sb.toString();
            String area = firstNonEmpty(
                    address.neighbourhood, address.quarter, address.suburb,
                    address.cityDistrict);
            String locality = firstNonEmpty(
                    address.city, address.town, address.village, address.hamlet);
            if (area != null && locality != null) return area + ", " + locality;
            if (area != null) return area;
            if (locality != null) return locality;
        }
        return displayName;
    }

    private static String firstNonEmpty(String... vs) {
        for (String v : vs) if (v != null && !v.isEmpty()) return v;
        return null;
    }
}
