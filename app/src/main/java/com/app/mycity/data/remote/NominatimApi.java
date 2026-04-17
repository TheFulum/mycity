package com.app.mycity.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface NominatimApi {

    String BASE_URL = "https://nominatim.openstreetmap.org/";

    @GET("reverse?format=jsonv2&accept-language=ru")
    Call<NominatimResponse> reverse(
            @Header("User-Agent") String userAgent,
            @Query("lat") double lat,
            @Query("lon") double lon
    );
}
