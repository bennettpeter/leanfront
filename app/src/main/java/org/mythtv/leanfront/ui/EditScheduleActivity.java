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

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.leanback.app.GuidedStepSupportFragment;

import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.Video;

import java.util.ArrayList;
import java.util.Date;


public class EditScheduleActivity extends FragmentActivity implements AsyncBackendCall.OnBackendCallListener {

    EditScheduleFragment mEditFragment;
    private CreateManualSchedule manualFragment;
    private int mRecordId;
    private int searchType;
    private boolean isOverride;

    public static final String CHANID = "CHANID";
    public static final String STARTTIME = "STARTTIME";
    public static final String RECORDID = "RECORDID";
    public static final String SEARCHTYPE = "SEARCHTYPE";
    public static final String ISOVERRIDE = "ISOVERRIDE";
    public static final int SEARCH_MANUAL = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int chanId = getIntent().getIntExtra(CHANID, 0);
        Date startTime = (Date) getIntent().getSerializableExtra(STARTTIME);
        mRecordId = getIntent().getIntExtra(RECORDID,0);
        searchType = getIntent().getIntExtra(SEARCHTYPE,0);
        isOverride = getIntent().getBooleanExtra(ISOVERRIDE, false);
        AsyncBackendCall call = new AsyncBackendCall(this,this);
        int firstCall;
        if (chanId != 0 && startTime != null) {
            // Creating from program schedule
            call.setId(chanId);
            call.setStartTime(startTime);
            firstCall = Video.ACTION_GETPROGRAMDETAILS;
        } else {
            // New Recording - searchType currently only manual.
            firstCall = Video.ACTION_DUMMY;
        }
        call.execute(
                firstCall,
                Video.ACTION_GETRECORDSCHEDULELIST,
                Video.ACTION_GETPLAYGROUPLIST,
                Video.ACTION_GETRECGROUPLIST,
                Video.ACTION_GETRECSTORAGEGROUPLIST,
                Video.ACTION_GETINPUTLIST,
                Video.ACTION_GETRECRULEFILTERLIST);
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm == null || fm.isDestroyed())
            return;
        int [] tasks = taskRunner.getTasks();
        switch (tasks[0]) {
            case Video.ACTION_GETPROGRAMDETAILS:
            case Video.ACTION_DUMMY:
                ArrayList<XmlNode> resultsList = taskRunner.getXmlResults();
                switch (searchType) {
                    case SEARCH_MANUAL:
                        GuidedStepSupportFragment.addAsRoot(this,
                                manualFragment = new CreateManualSchedule(resultsList, mRecordId, searchType),
                                android.R.id.content);
                        break;
                    default:
                        GuidedStepSupportFragment.addAsRoot(this,
                                mEditFragment = new EditScheduleFragment(resultsList, mRecordId,
                                searchType, null, isOverride),
                                android.R.id.content);
                        break;
                }
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mEditFragment == null || mEditFragment.canEnd())
            super.onBackPressed();
    }

}
