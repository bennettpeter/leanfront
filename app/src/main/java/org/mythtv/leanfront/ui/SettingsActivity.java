/*
 * Copyright (c) 2016 The Android Open Source Project
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

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.leanback.app.GuidedStepSupportFragment;

import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.BackendCache;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.Video;

import java.util.ArrayList;


public class SettingsActivity extends FragmentActivity
        implements AsyncBackendCall.OnBackendCallListener {

    private ArrayList<String> mPlayGroupList;
    private SettingsEntryFragment fragment;

    public ArrayList<String> getPlayGroupList() {
        return mPlayGroupList;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            BackendCache bCache = BackendCache.getInstance();
            if (bCache.isConnected) {
                AsyncBackendCall call = new AsyncBackendCall(this, this);
                call.execute(Video.ACTION_GETPLAYGROUPLIST);
            } else {
                mPlayGroupList = new ArrayList<>();
                mPlayGroupList.add("Default");
                GuidedStepSupportFragment.addAsRoot(this,
                        fragment = new SettingsEntryFragment(), android.R.id.content);
            }
        }
    }
    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm == null || fm.isDestroyed())
            return;
        mPlayGroupList = XmlNode.getStringList(taskRunner.getXmlResult()); // ACTION_GETPLAYGROUPLIST
        GuidedStepSupportFragment.addAsRoot(this,
                fragment = new SettingsEntryFragment(), android.R.id.content);
    }

    public void onClickMenu(View view) {
        fragment.onClickMenu(view);
    }
}
