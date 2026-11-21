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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;

import org.mythtv.leanfront.BuildConfig;
import org.mythtv.leanfront.MyApplication;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.VideoDbHelper;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.mobile.MobileWelcomeActivity;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/*
 * MainActivity class that loads MainFragment.
 */
public class MainActivity extends LeanbackActivity {

    private static final String TAG = "lfe";
    private static final String CLASS = "MainActivity";
    long mMultipleKeyTime;
    private MainFragment mainFragment;
    public static Task<Boolean> fetchTask;
    private final static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final static FirebaseAuth fbAuth = FirebaseAuth.getInstance();
    private final DatabaseReference fbDatabase = FirebaseDatabase.getInstance().getReference();
    private final HashSet<String> recentlySynced = new HashSet<>();
    private ScheduledFuture<?> resetFuture;
    public static MainActivity mainInstance;


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
        if (isTaskRoot()) {
            mainInstance = this;
            deployAssetFile();
            fetchConfig();
            if ("true".equals(Settings.getString("pref_share_recents")))
                fbSignUp();
        }
        setContentView(R.layout.main);
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag("main");
        if (fragment instanceof MainFragment) {
            mainFragment = (MainFragment) fragment;
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();
        int lMemoryClass = am.getLargeMemoryClass();
        Log.i(TAG, CLASS + " memoryClass:" + memoryClass + " lMemoryClass:" + lMemoryClass);
        Intent intent = getIntent();
        boolean settingsReq = intent.getBooleanExtra("LEANFRONT_SETTINGS", false);
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

    static void deployAssetFile() {
        Context context = MyApplication.getAppContext();
        String assetFileName = "pref_defaults.xml";
        String outputFileName = "shared_prefs/pref_defaults.xml";
        // Target location: /data/user/0/<your.package>/<outputFileName>
        File outFile = new File(context.getDataDir(), outputFileName);
        // Check if the defaults file is already up to date and if so,
        // return to avoid copying it unnecessarily again
        if (outFile.exists()) {
            try (BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(outFile)))) {
                for (; ; ) {
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
        }

        try (
                BufferedReader rdr = new BufferedReader(new InputStreamReader(context.getAssets().open(assetFileName)));
                FileWriter wrt = new FileWriter(outFile)) {
            for (; ; ) {
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

    void fetchConfig() {
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
        fetchTask.addOnCompleteListener((task) -> {
            if (task.isSuccessful())
                fbSettings();
            else
                Log.e(TAG, CLASS + " fetchConfig failed.", task.getException());
        });
    }

    // Apply firebase updates to Settings
    void fbSettings() {
        handler.postDelayed(() -> {
            if (MainActivity.fetchTask.isSuccessful()) {
                String msgJson = MyApplication.singleton.firebaseRemoteConfig.getString("setting");
                if (!msgJson.isEmpty()) {
                    ArrayList<String> settingNames = new ArrayList<>();
                    ArrayList<String> values = new ArrayList<>();
                    parseSettings(msgJson, settingNames, values);
                    if (!settingNames.isEmpty()) {
                        SharedPreferences prefOverrides = getSharedPreferences("pref_overrides", 0);
                        SharedPreferences.Editor editor = prefOverrides.edit();
                        for (int ix = 0; ix < settingNames.size(); ix++) {
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
                        editor.apply();
                    }
                }
            }
        }, 5000);

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

    public void fbSignUp() {
        FirebaseUser currentUser = fbAuth.getCurrentUser();
        String email;
        String userId;
        if (currentUser == null)
            userId = null;
        else {
            email = currentUser.getEmail();
            if (email == null) {
                Log.e(TAG, CLASS + " fbSignUp: email address is null ");
                return;
            }
            String[] split = email.split("@");
            userId = split[0];
        }
        Log.i(TAG, CLASS + " fbSignUp: Already signed in as " + userId);
        AsyncBackendCall call = new AsyncBackendCall(this,
                (taskrunner) -> fbCheckSignIn(userId));
        call.execute(Video.ACTION_GET_UUID);
    }

    void fbCheckSignIn(String userId) {
        String uuid = Settings.getString("pref_uuid");
        if (userId != null && userId.equals(uuid)) {
            startSharing();
            return;
        }
        Log.i(TAG, CLASS + " fbCheckSignIn: Sign off " + userId + " and sign on " + uuid);

        if (userId != null)
            fbAuth.signOut();
        fbAuth.signInWithEmailAndPassword(uuid + "@nowhere.org", uuid)
            .addOnSuccessListener(authResult -> {
                Log.i(TAG, CLASS + " fbCheckSignIn: Sign in successful " + uuid);
                // Sign-in succeeded
                startSharing();
            })
            .addOnFailureListener(e -> {
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    fbCreateUser(uuid);
                } else {
                    Log.e(TAG, CLASS + " fbCheckSignIn: Exception signing in ", e);
                }
            });
    }

    void fbCreateUser(String uuid) {
        fbAuth.createUserWithEmailAndPassword(uuid + "@nowhere.org", uuid)
                .addOnCompleteListener((task)  -> {
                    if (task.isSuccessful()) {
                        Log.i(TAG, CLASS + " fbCreateUser: Successful created User id " + uuid);
                        // Create User Succeeded
                        startSharing();
                    }
                    else
                        Log.e(TAG, CLASS + " Failure creating user ", task.getException());
                });
    }

    void startSharing() {
        executor.submit(() -> {
            fbCleanup();
            fbListenForChanges();
        });
        // Wait 60 seconds for the changes to be finished before updating
        // firebase, in case we uipdate with old values
        executor.schedule(() -> fbUpdateFromDB(null), 15, TimeUnit.SECONDS);
    }

    void fbCleanup() {
        String uid = fbGetUid();
        fbDatabase.child("videostatus")
          .child(uid)
          .get().addOnCompleteListener( (task) -> {
            if (task.isSuccessful()) {
                Iterable<DataSnapshot> iter = task.getResult().getChildren();
                iter.forEach((data) -> {
                    // 60 days in milliseconds
                    long expiration = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000;
                    Long lastUsed = (Long) data.child("last_used").getValue(false);
                    if (lastUsed == null || lastUsed < expiration) {
                        String key = data.getKey();
                        if (key != null)
                            fbDatabase.child("videostatus").child(uid).child(data.getKey()).removeValue();
                    }
                });
            }
            else {
                Log.e(TAG, CLASS + " fbCleanup: error accessing data ", task.getException());
            }
        });

    }

    public void fbUpdateFromDB(String url) {
        String uid = fbGetUid();
        VideoDbHelper dbh = VideoDbHelper.getInstance(this);
        SQLiteDatabase db = dbh.getReadableDatabase();
        if (db == null)
            return;
        final String [] columns = {
                VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH,
                VideoContract.StatusEntry.COLUMN_LAST_USED,
                VideoContract.StatusEntry.COLUMN_SHOW_RECENT
        };
        String selection = null;
        String[] args = null;
        if (url != null) {
            selection = VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH + " = ? ";
            args = new String[]{url};
        }

        Cursor csr = db.query(
                VideoContract.StatusEntry.TABLE_NAME,   // The table to query
                columns,             // The array of columns to return (pass null to get all)
                selection,              // The columns for the WHERE clause
                args,          // The values for the WHERE clause
                null,                   // don't group the rows
                null,                   // don't filter by row groups
                null);               // The sort order
        boolean cursorHasData = csr.moveToFirst();

        // 60 days in milliseconds
        long expiration = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000;
        while (cursorHasData && !csr.isAfterLast()) {
            url = csr.getString(0);
            if (!recentlySynced.contains(url)) {
                long lastUsed = csr.getLong(1);
                if (lastUsed < expiration) {
                    csr.moveToNext();
                    continue;
                }
                String urlKey;
                try {
                    urlKey = URLEncoder.encode(url, "UTF-8")
                            .replace("+", "%20")
                            .replace(".", "%2E")
                            .replace("*", "%2A");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                fbDatabase.child("videostatus")
                        .child(uid)
                        .child(urlKey)                     // url is a key
                        .child("last_used")       // key
                        .setValue(lastUsed);       // last used value
                fbDatabase.child("videostatus")
                        .child(uid)
                        .child(urlKey)                      // url is a key
                        .child("show_recent")      // key
                        .setValue(csr.getInt(2) != 0);    // show recent value (boolean)
                recentlySynced.add(url);
            }
            csr.moveToNext();
        }
        csr.close();

        if (resetFuture != null)
            resetFuture.cancel(false);
        resetFuture = executor.schedule(() -> {
            recentlySynced.clear();
            resetFuture = null;
        }, 60, TimeUnit.SECONDS);
    }

    void fbListenForChanges() {
        String uid = fbGetUid();
        fbDatabase.child("videostatus")
            .child(uid)
            .addChildEventListener(new ChildEventListener() {

            public void childChange(@NonNull DataSnapshot snapshot, boolean delete) {
                // 60 days in milliseconds
                long expiration = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000;
                Long lastUsed = (Long) snapshot.child("last_used").getValue(false);
                Boolean showRecent = (Boolean) snapshot.child("show_recent").getValue(false);
                if (lastUsed == null || showRecent == null || lastUsed < expiration)
                    return;
                String url;
                try {
                    url = java.net.URLDecoder.decode(snapshot.getKey(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                if (!recentlySynced.contains(url)) {
                    // Gets the data repository in write mode
                    VideoDbHelper dbh = VideoDbHelper.getInstance(MainActivity.this);
                    SQLiteDatabase db = dbh.getWritableDatabase();
                    if (db == null)
                        return;

                    String selection = VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH + " = ?";
                    String[] selectionArgs = {url};

                    if (delete) {
                        db.delete(
                                VideoContract.StatusEntry.TABLE_NAME,
                                selection,
                                selectionArgs);
                    } else {
                        // Create a new map of values, where column names are the keys
                        ContentValues values = new ContentValues();
                        values.put(VideoContract.StatusEntry.COLUMN_LAST_USED, lastUsed);
                        values.put(VideoContract.StatusEntry.COLUMN_BOOKMARK, 0);
                        values.put(VideoContract.StatusEntry.COLUMN_SHOW_RECENT, showRecent);

                        // First try an update
                        int sqlCount = db.update(
                                VideoContract.StatusEntry.TABLE_NAME,
                                values,
                                selection,
                                selectionArgs);

                        if (sqlCount == 0) {
                            // Try an insert instead
                            values.put(VideoContract.StatusEntry.COLUMN_VIDEO_URL_PATH, url);
                            // Insert the new row, returning the primary key value of the new row
                            db.insert(VideoContract.StatusEntry.TABLE_NAME,
                                    null, values);
                        }
                        recentlySynced.add(url);
                    }
                    VideoDbHelper.releaseDatabase();
                }
                if (resetFuture != null)
                    resetFuture.cancel(false);
                resetFuture = executor.schedule(() -> {
                    recentlySynced.clear();
                    resetFuture = null;
                }, 60, TimeUnit.SECONDS);
            }

                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    childChange(snapshot, false);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    childChange(snapshot, false);
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                    childChange(snapshot, true);
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    // Do nothing
                }
                @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, CLASS + " fbListenForChanges failed " + error.getMessage());
            }
        });
    }

    String fbGetUid() {
        FirebaseUser currentUser = fbAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, CLASS + " fbGetUid: User is null ", new Throwable());
            return "";
        }
        return currentUser.getUid();
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
