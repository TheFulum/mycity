package com.app.mycity;

import android.app.Application;
import android.preference.PreferenceManager;

import com.app.mycity.util.CloudinaryManager;
import com.app.mycity.util.ThemeManager;

import org.osmdroid.config.Configuration;

public class MyCityApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedTheme(this);
        CloudinaryManager.init(this);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());
    }
}
