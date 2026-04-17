package com.app.mycity;

import android.app.Application;
import android.preference.PreferenceManager;

import com.app.mycity.util.CloudinaryManager;

import org.osmdroid.config.Configuration;

public class MyCityApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CloudinaryManager.init(this);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
    }
}
