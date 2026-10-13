package com.app.mycity;

import android.app.Application;
import android.preference.PreferenceManager;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.app.mycity.util.CloudinaryManager;
import com.app.mycity.util.ThemeManager;
import com.app.mycity.worker.RssNewsWorker;

import org.osmdroid.config.Configuration;

import java.util.concurrent.TimeUnit;

public class MyCityApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedTheme(this);
        CloudinaryManager.init(this);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                RssNewsWorker.class, 60, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "rss_news_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic);
        OneTimeWorkRequest immediate = new OneTimeWorkRequest.Builder(RssNewsWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(this).enqueueUniqueWork(
                "rss_news_sync_now",
                ExistingWorkPolicy.KEEP,
                immediate);
    }
}
