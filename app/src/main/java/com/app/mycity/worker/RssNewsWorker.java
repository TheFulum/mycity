package com.app.mycity.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.app.mycity.data.model.NewsItem;
import com.app.mycity.data.repository.NewsRepository;
import com.app.mycity.util.NewsCategorizer;
import com.app.mycity.util.RssParser;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RssNewsWorker extends Worker {

    private static final String TAG = "RssNewsWorker";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final String USER_AGENT = "MyCityApp/1.0";
    private static final long UPSERT_TIMEOUT_SEC = 5;

    private static final Source[] SOURCES = new Source[] {
            new Source("Могилёвские новости", "https://mogilevnews.by/rss", true),
            new Source("Могилёв.Онлайн", "https://mogilev.online/rss", false)
    };

    private final NewsRepository newsRepo = new NewsRepository();

    public RssNewsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        int successCount = 0;
        int failureCount = 0;

        for (Source source : SOURCES) {
            if (!source.enabled) continue;
            try {
                int processed = fetchAndIngest(source);
                Log.i(TAG, "Source '" + source.name + "' processed " + processed + " items");
                successCount++;
            } catch (Throwable t) {
                failureCount++;
                Log.w(TAG, "Source '" + source.name + "' failed: " + t.getMessage());
            }
        }

        if (successCount == 0 && failureCount > 0) {
            return Result.retry();
        }
        return Result.success();
    }

    private int fetchAndIngest(Source source) throws Exception {
        HttpURLConnection conn = null;
        InputStream stream = null;
        try {
            URL url = new URL(source.url);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("HTTP " + code + " from " + source.url);
            }
            stream = new BufferedInputStream(conn.getInputStream());
            List<NewsItem> items = RssParser.parse(stream, source.name);
            int processed = 0;
            for (NewsItem item : items) {
                try {
                    item.setCategory(NewsCategorizer.classify(item.getTitle(), item.getBody()));
                    Task<Void> task = newsRepo.upsertByExternalId(item);
                    Tasks.await(task, UPSERT_TIMEOUT_SEC, TimeUnit.SECONDS);
                    processed++;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return processed;
                } catch (Throwable t) {
                    Log.w(TAG, "Upsert failed for '" + item.getTitle() + "': " + t.getMessage());
                }
            }
            return processed;
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Throwable ignored) { }
            }
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }
    }

    private static final class Source {
        final String name;
        final String url;
        final boolean enabled;

        Source(String name, String url, boolean enabled) {
            this.name = name;
            this.url = url;
            this.enabled = enabled;
        }
    }
}
