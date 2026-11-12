package org.mythtv.leanfront;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class MyApplication extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    public static OkHttpClient httpClient;
    public static OkHttpClient apiHttpClient;
    public static OkHttpClient fileLengthHttpClient;
    public FirebaseRemoteConfig firebaseRemoteConfig;
    @SuppressLint("StaticFieldLeak")
    public static MyApplication singleton;


    public MyApplication() {
        httpClient = new OkHttpClient();
        apiHttpClient = httpClient.newBuilder()
                .readTimeout(300000, TimeUnit.MILLISECONDS)
                .connectTimeout(5000, TimeUnit.MILLISECONDS)
                .build();
        fileLengthHttpClient = httpClient.newBuilder()
                .readTimeout(1000, TimeUnit.MILLISECONDS)
                .connectTimeout(1000, TimeUnit.MILLISECONDS)
                .build();
        singleton = this;
    }
    public void onCreate() {
        super.onCreate();
        MyApplication.context = getApplicationContext();
    }

    public static Context getAppContext() {
        return MyApplication.context;
    }
}
