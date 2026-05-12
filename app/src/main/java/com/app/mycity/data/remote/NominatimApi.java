package com.app.mycity.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface NominatimApi {

    String BASE_URL = "https://nominatim.openstreetmap.org/";
    /** Russian first; then regional Cyrillic. Sent as query + HTTP header (see NominatimClient). */
    String ACCEPT_LANGUAGE = "ru,be,uk";

    @GET("reverse?format=jsonv2&addressdetails=1&namedetails=1")
    Call<NominatimResponse> reverse(
            @Header("User-Agent") String userAgent,
            @Query("accept-language") String acceptLanguageQuery,
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("zoom") int zoom
    );
}
