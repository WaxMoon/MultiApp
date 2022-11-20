package org.waxmoon;

import android.content.Context;

import com.hack.opensdk.HackApplication;

public class MoonApplication extends HackApplication {

    private static MoonApplication sInstance;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        sInstance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static MoonApplication INSTANCE() {
        return sInstance;
    }
}
