package com.app.mycity.data.remote;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class NominatimClient {

    private static final String USER_AGENT = "MyCityApp/1.0 (mycity@example.com)";
    private static NominatimApi api;

    public static NominatimApi get() {
        if (api == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request r = chain.request().newBuilder()
                                .header("Accept-Language", NominatimApi.ACCEPT_LANGUAGE)
                                .build();
                        return chain.proceed(r);
                    })
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(NominatimApi.BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            api = retrofit.create(NominatimApi.class);
        }
        return api;
    }

    public static String userAgent() { return USER_AGENT; }
}
