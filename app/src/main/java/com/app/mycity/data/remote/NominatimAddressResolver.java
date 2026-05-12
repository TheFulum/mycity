package com.app.mycity.data.remote;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Reverse geocode via Nominatim with zoom fallbacks (some tiles return empty at z18),
 * optional Geocoder only when the line contains Cyrillic (avoids Latin like "Gogolya").
 */
public final class NominatimAddressResolver {

    private static final int[] ZOOM_STEPS = {18, 16, 14, 11, 9};
    private static final long STEP_DELAY_MS = 380L;

    public interface AddressListener {
        void onResolved(@NonNull String address);

        void onNotFound();
    }

    public static void resolve(@Nullable View anchor,
                               @Nullable Context appContext,
                               long requestSeq,
                               @NonNull LongSupplier currentSeq,
                               double lat,
                               double lng,
                               @NonNull AddressListener listener) {
        if (anchor == null) {
            listener.onNotFound();
            return;
        }
        resolveStep(anchor, appContext, requestSeq, currentSeq, lat, lng, listener, 0);
    }

    private static void resolveStep(@NonNull View anchor,
                                    @Nullable Context appContext,
                                    long requestSeq,
                                    @NonNull LongSupplier currentSeq,
                                    double lat,
                                    double lng,
                                    @NonNull AddressListener listener,
                                    int step) {
        if (currentSeq.getAsLong() != requestSeq) {
            return;
        }
        if (step >= ZOOM_STEPS.length) {
            tryGeocoderCyrillicOnly(anchor, appContext, requestSeq, currentSeq, lat, lng, listener);
            return;
        }
        int zoom = ZOOM_STEPS[step];
        NominatimClient.get().reverse(
                        NominatimClient.userAgent(),
                        NominatimApi.ACCEPT_LANGUAGE,
                        lat,
                        lng,
                        zoom)
                .enqueue(new Callback<NominatimResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<NominatimResponse> call,
                                           @NonNull Response<NominatimResponse> response) {
                        if (currentSeq.getAsLong() != requestSeq) {
                            return;
                        }
                        NominatimResponse body = response.isSuccessful() ? response.body() : null;
                        String addr = body != null ? body.shortAddress() : null;
                        if (addr != null && !addr.trim().isEmpty()) {
                            String trimmed = addr.trim();
                            anchor.post(() -> {
                                if (currentSeq.getAsLong() != requestSeq) {
                                    return;
                                }
                                listener.onResolved(trimmed);
                            });
                            return;
                        }
                        boolean slowRetry = response.code() == 429 || response.code() == 503;
                        long delay = slowRetry ? 1500L : STEP_DELAY_MS;
                        anchor.postDelayed(
                                () -> resolveStep(anchor, appContext, requestSeq, currentSeq, lat, lng, listener, step + 1),
                                delay);
                    }

                    @Override
                    public void onFailure(@NonNull Call<NominatimResponse> call, @NonNull Throwable t) {
                        if (currentSeq.getAsLong() != requestSeq) {
                            return;
                        }
                        anchor.postDelayed(
                                () -> resolveStep(anchor, appContext, requestSeq, currentSeq, lat, lng, listener, step + 1),
                                STEP_DELAY_MS);
                    }
                });
    }

    private static void tryGeocoderCyrillicOnly(@NonNull View anchor,
                                                @Nullable Context appContext,
                                                long requestSeq,
                                                @NonNull LongSupplier currentSeq,
                                                double lat,
                                                double lng,
                                                @NonNull AddressListener listener) {
        if (appContext == null || !Geocoder.isPresent()) {
            anchor.post(() -> {
                if (currentSeq.getAsLong() != requestSeq) {
                    return;
                }
                listener.onNotFound();
            });
            return;
        }
        new Thread(() -> {
            String addr = "";
            try {
                Geocoder g = new Geocoder(appContext, Locale.forLanguageTag("ru-BY"));
                List<Address> res = g.getFromLocation(lat, lng, 1);
                if (res != null && !res.isEmpty()) {
                    Address a = res.get(0);
                    String street = a.getThoroughfare();
                    String house = a.getSubThoroughfare();
                    String locality = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
                    StringBuilder sb = new StringBuilder();
                    if (street != null) {
                        sb.append(street);
                    }
                    if (house != null) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(house);
                    }
                    if (sb.length() == 0 && locality != null) {
                        sb.append(locality);
                    }
                    addr = sb.toString().trim();
                }
            } catch (Throwable ignored) {
                addr = "";
            }
            final String out = addr;
            anchor.post(() -> {
                if (currentSeq.getAsLong() != requestSeq) {
                    return;
                }
                if (!out.isEmpty() && containsCyrillic(out)) {
                    listener.onResolved(out);
                } else {
                    listener.onNotFound();
                }
            });
        }, "geocoder-ru").start();
    }

    private static boolean containsCyrillic(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeBlock.of(s.charAt(i)) == Character.UnicodeBlock.CYRILLIC) {
                return true;
            }
        }
        return false;
    }

    private NominatimAddressResolver() {
    }
}
