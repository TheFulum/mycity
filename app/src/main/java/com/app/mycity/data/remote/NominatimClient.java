package com.app.mycity.data.remote;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NominatimClient {

    private static final String USER_AGENT = "MyCityApp/1.0 (mycity@example.com)";
    private static NominatimApi api;

    public static NominatimApi get() {
        if (api == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(NominatimApi.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            api = retrofit.create(NominatimApi.class);
        }
        return api;
    }

    public static String userAgent() { return USER_AGENT; }
}
