package com.app.mycity.util;

import android.content.Context;
import android.net.Uri;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.util.HashMap;
import java.util.Map;

public class CloudinaryManager {

    private static final String CLOUD_NAME  = "YOUR_CLOUD_NAME";
    private static final String UPLOAD_PRESET = "YOUR_UPLOAD_PRESET"; // (unsigned)

    private static boolean initialized = false;

    /** Вызвать в Application.onCreate() */
    public static void init(Context context) {
        if (initialized) return;

        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", CLOUD_NAME);

        MediaManager.init(context, config);
        initialized = true;
    }

    /**
     * Загружает файл по Uri в Cloudinary.
     *
     * @param uri      URI файла (из галереи / камеры)
     * @param folder   папка в Cloudinary, например "avatars" или "issues"
     * @param callback результат: onSuccess / onError
     */
    public static void upload(Uri uri, String folder, UploadResultCallback callback) {
        MediaManager.get()
                .upload(uri)
                .option("upload_preset", UPLOAD_PRESET)
                .option("folder", folder)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) { }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) { }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        callback.onSuccess(url);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        callback.onError(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) { }
                })
                .dispatch();
    }

    /** Простой колбэк для результата загрузки */
    public interface UploadResultCallback {
        void onSuccess(String secureUrl);
        void onError(String errorMessage);
    }
}