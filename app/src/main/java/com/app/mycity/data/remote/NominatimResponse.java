package com.app.mycity.data.remote;

import com.google.gson.annotations.SerializedName;

public class NominatimResponse {

    @SerializedName("display_name")
    public String displayName;

    @SerializedName("address")
    public Address address;

    public static class Address {
        public String road;
        @SerializedName("house_number")
        public String houseNumber;
        public String suburb;
        public String city;
        public String town;
        public String village;
    }

    public String shortAddress() {
        if (address != null) {
            StringBuilder sb = new StringBuilder();
            if (address.road != null) sb.append(address.road);
            if (address.houseNumber != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(address.houseNumber);
            }
            if (sb.length() > 0) return sb.toString();
        }
        return displayName;
    }
}
