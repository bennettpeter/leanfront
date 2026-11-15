/*
 * Copyright (c) 2014 The Android Open Source Project
 * Copyright (c) 2019-2020 Peter Bennett
 *
 * Incorporates code from "Android TV Samples"
 * <https://github.com/android/tv-samples>
 * Modified by Peter Bennett
 *
 * This file is part of MythTV-leanfront.
 *
 * MythTV-leanfront is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * MythTV-leanfront is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with MythTV-leanfront.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.mythtv.leanfront.ui;

import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import org.mythtv.leanfront.BuildConfig;
import org.mythtv.leanfront.MyApplication;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.mobile.MobileWelcomeActivity;
import org.mythtv.leanfront.model.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * MainActivity class that loads MainFragment.
 */
public class MainActivity extends LeanbackActivity {

    private static final String TAG = "lfe";
    private static final String CLASS = "MainActivity";
    long mMultipleKeyTime;
    private MainFragment mainFragment;
    public static Task<Boolean> fetchTask;
    private final static ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int type = getIntent().getIntExtra(MainFragment.KEY_TYPE, -1);
        if (type == -1) { // This means launched from android launcher
            UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            boolean isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
            if (!isTV) {
                Intent intent = new Intent(this, MobileWelcomeActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        }
        deployAssetFile("pref_defaults.xml", "shared_prefs/pref_defaults.xml");
        fetchConfig(0);
        setContentView(R.layout.main);
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag("main");
        if (fragment instanceof MainFragment) {
            mainFragment = (MainFragment) fragment;
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();
        int lMemoryClass = am.getLargeMemoryClass();
        Log.i(TAG, CLASS + " memoryClass:" + memoryClass +" lMemoryClass:" + lMemoryClass);
        Intent intent = getIntent();
        boolean settingsReq = intent.getBooleanExtra("LEANFRONT_SETTINGS",false);
        if (savedInstanceState != null) {
            settingsReq = savedInstanceState.getBoolean("settingsReq");
        }
        // to test another language uncomment this
        //        setAppLocale("es");
        if (settingsReq || XmlNode.isSetupNotDone()) {
            // This is the first time running the app, let's go to onboarding
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }

    static void deployAssetFile(String assetFileName, String outputFileName) {
        Context context = MyApplication.getAppContext();
        // Target location: /data/user/0/<your.package>/<outputFileName>
        File outFile = new File(context.getDataDir(), outputFileName);
        try (BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(outFile)))) {
            for (;;) {
                String line = rdr.readLine();
                if (line == null)
                    break;
                if (line.contains("name=\"VersionName\">")) {
                    String[] split = line.split("[<>]");
                    if (BuildConfig.VERSION_NAME.equals(split[2]))
                        return;
                    else
                        break;
                }
            }
        } catch (IOException ignored) {
        }

        try (
            BufferedReader rdr = new BufferedReader(new InputStreamReader(context.getAssets().open(assetFileName)));
            FileWriter wrt = new FileWriter(outFile) ) {
            for (;;) {
                String line = rdr.readLine();
                if (line == null)
                    break;
                wrt.write(line + "\n");
                if ("<map>".equals(line))
                    wrt.write("    <string name=\"VersionName\">" + BuildConfig.VERSION_NAME + "</string>\n");
            }
            wrt.flush();
        } catch (IOException e) {
            Log.e(TAG, CLASS + " Exception deploying " + outputFileName, e);
        }
    }

    void fetchConfig(int waitMillis) {
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException ignored) {
            }
        }
        if (MyApplication.singleton.firebaseRemoteConfig == null) {
            MyApplication.singleton.firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
            int minFetch = 86400;  // 24 hours
            if (BuildConfig.DEBUG)
                minFetch = 10;
            FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(minFetch)   // 24 hours
                    .build();
            MyApplication.singleton.firebaseRemoteConfig.setConfigSettingsAsync(configSettings);
        }
        fetchTask = MyApplication.singleton.firebaseRemoteConfig.fetchAndActivate();
        fetchTask.addOnCompleteListener( (task) -> {
            if (task.isSuccessful())
                fbSettings();
            else
                executor.submit(() -> fetchConfig(30000));
        });
    }

    // Apply firebase updates to Settings
    void fbSettings() {
        handler.postDelayed( () -> {
            if (MainActivity.fetchTask.isSuccessful()) {
                String msgJson = MyApplication.singleton.firebaseRemoteConfig.getString("setting");
                if (!msgJson.isEmpty()) {
                    ArrayList<String> settingNames = new ArrayList<>();
                    ArrayList<String> values = new ArrayList<>();
                    parseSettings(msgJson, settingNames, values);
                    if (!settingNames.isEmpty()) {
                        SharedPreferences prefOverrides = getSharedPreferences("pref_overrides",0);
                        SharedPreferences.Editor editor = prefOverrides.edit();
                        for (int ix = 0; ix < settingNames.size() ; ix++) {
                            String settingName = settingNames.get(ix);
                            String value = values.get(ix);
                            if (value != null && !value.isEmpty()) {
                                if (settingName.startsWith("pref_color")) {
                                    try {
                                        Color.parseColor(value);
                                    } catch (Exception e) {
                                        Log.e(TAG, CLASS + " Invalid color in override for " + settingName + " : " + value);
                                        value = null;
                                    }
                                }
                            }
                            String defaultValue = Settings.getDefaultString(settingName);
                            if (value == null || value.isEmpty())
                                editor.remove(settingName);
                            else
                                editor.putString(settingName, value);
                            String actual = Settings.getString(settingName);
                            if (!actual.isEmpty()
                                    && (actual.equals(defaultValue) || actual.equals(value))) {
                                // Remove value when it is the old default or the new default
                                SharedPreferences.Editor editor2 = Settings.getEditor();
                                editor2.remove(settingName);
                                editor2.commit();
                            }
                        }
                        editor.commit();
                    }
                }
            }
        },1000);

    }

    static void parseSettings(String msgJson, ArrayList<String> settingNames, ArrayList<String> values) {

//      Example
//      {
//        "settings": [
//          {
//            "name": "pref_color_card_dflt",
//            "value": "#23699e"
//          },
//          {
//            "name": "pref_color_search_bn"
//          }
//        ]
//      }

        Reader in = new StringReader(msgJson);
        try (JsonReader reader = new JsonReader(in)) {
            reader.beginObject();
            doc:
            for (; ; ) {
                switch (reader.peek()) {
                    case NAME:
                        String name = reader.nextName();
                        if (name.equals("settings")) {
                            reader.beginArray();
                            for (; ; ) {
                                if (reader.peek() == JsonToken.END_ARRAY) {
                                    reader.endArray();
                                    break;
                                }
                                reader.beginObject();
                                String settingName = "";
                                String value = "";
                                entry:
                                for (; ; ) {
                                    switch (reader.peek()) {
                                        case NAME:
                                            String name1 = reader.nextName();
                                            switch (name1) {
                                                case "name":
                                                    settingName = reader.nextString();
                                                    break;
                                                case "value":
                                                    value = reader.nextString();
                                                    break;
                                                default:
                                                    reader.skipValue();
                                            }
                                            break;
                                        case END_OBJECT:
                                            settingNames.add(settingName);
                                            values.add(value);
                                            reader.endObject();
                                            break entry;
                                    }
                                } // entry
                            } // results
                        } else {
                            reader.skipValue();
                        }
                        break;
                    case END_OBJECT:
                        reader.endObject();
                        break doc;
                    default:
                }
            } // doc
        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, CLASS + " Exception parsing: " + msgJson, e);
        }
    }


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("settingsReq",false);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction = 1;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                long newKeyTime=System.currentTimeMillis();
                long diff = newKeyTime - mMultipleKeyTime;
                // Restrict key interval to 50ms to prevent crash
                if (diff < 50)
                    return true;
                mMultipleKeyTime=newKeyTime;
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                direction = -1;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (mainFragment != null) {
                    mainFragment.pageDown(direction);
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }


//    public static void setAppLocale(String localeCode){
//        Resources resources = MyApplication.getAppContext().getResources();
//        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
//        Configuration configuration = resources.getConfiguration();
//        configuration.setLocale(new Locale(localeCode.toLowerCase()));
//        resources.updateConfiguration(configuration, displayMetrics);
//        configuration.locale = new Locale(localeCode.toLowerCase());
//        resources.updateConfiguration(configuration, displayMetrics);
//    }


}
