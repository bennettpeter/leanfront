/*
 * Copyright (c) 2019-2020 Peter Bennett
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

package org.mythtv.leanfront.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.mythtv.leanfront.MyApplication;

import java.util.Objects;

public class Settings {
    private static final String TAG = "lfe";
    private static final String CLASS = "Settings";

    private Settings() {
    }

    public static SharedPreferences.Editor getEditor() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences
                (MyApplication.getAppContext());
        return prefs.edit();
    }

    // Omit the "pref_" prefix when calling this.
    // This adds prefxxxx_ to string where xxxx is the group
    // null group or "Default" = empty string
    // If group value not found return default value.
    public static String getString(String key, @Nullable String group, boolean getDefault) {
        Context context = MyApplication.getAppContext();
        if (key.startsWith("pref_"))
            key = key.substring(5);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences
                (context);
        SharedPreferences prefDefaults = context.getSharedPreferences("pref_defaults",0);
        SharedPreferences prefOverrides = context.getSharedPreferences("pref_overrides",0);

        if (prefs != null) {
            if (group == null || group.equals("Default"))
                group = "";
            String result = null;
            String actualKey = "pref" + group + "_" + key;
            if (!getDefault)
                result = prefs.getString(actualKey, null);
            actualKey =  "pref" + "_" + key;
            if (result == null || result.isEmpty())
                result = prefOverrides.getString(actualKey,null);
            if (result == null || result.isEmpty())
                result = prefDefaults.getString(actualKey,null);
            if (result == null)
                result = "";
            return result;
        }
        else
            return "";
    }

    public static String getString(String key, @Nullable String group) {
        return getString(key, group, false);
    }

    public static String getDefaultString(String key) {
        return getString(key, null, true);
    }

    public static String getString(String key) {
        return getString(key,null);
    }

    public static int getInt(String key, @Nullable String group, boolean getDefault) {
        String str = getString(key, group, getDefault).trim();
        if (str.isEmpty())
            return 0;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            Log.e(TAG, CLASS + " Exception ", e);
            return 0;
        }
    }

    public static int getDefaultInt(String key) {
        return getInt(key, null, true);
    }

    public static int getInt(String key, @Nullable String group) {
        return getInt(key, group, false);
    }

    public static int getInt(String key) {
        return getInt(key,null);
    }

    public static void putString(SharedPreferences.Editor editor, String key,
                                 @Nullable String group, String value) {
        String defaultValue = getDefaultString(key);
        if (Objects.equals(value, defaultValue))
            value = null;
        if (key.startsWith("pref_"))
            key = key.substring(5);
        if (editor != null) {
            if (group == null || group.equals("Default"))
                group = "";
            String actualKey = "pref" + group + "_" + key;
            if (value == null || value.isEmpty())
                editor.remove(actualKey);
            else
                editor.putString(actualKey,value);
        }
    }

    public static void putString(SharedPreferences.Editor editor, String key, String value) {
        putString(editor, key, null, value);
    }
}
