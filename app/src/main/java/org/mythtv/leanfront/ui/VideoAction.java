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

import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_CHANNEL;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_RECORDING;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_VIDEO;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TabStopSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.SparseArrayObjectAdapter;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.BackendCache;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.presenter.DetailsDescriptionPresenter;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * VideoAction handles the video actions that can be invoked by menu from video
 * details or MainFragment
 */
public class VideoAction implements OnActionClickedListener, AsyncBackendCall.OnBackendCallListener {
    private static final String TAG = "lfe";
    private static final String CLASS = "VideoAction";

    private Fragment fragment;
    private Activity activity;
    private Video mSelectedVideo;
    long mBookmark = 0;     // milliseconds
    long mPosBookmark = -1;  // position in frames
    long mLastPlay = 0;     // milliseconds
    long mPosLastPlay = -1;  // position in frames
    boolean mWatched;
    private ArrayList<String> mRecGroupList;
    private String mNewValueText;
    private ProgressBar mProgressBar = null;
    // the next 2 are optional
    private DetailsDescriptionPresenter mDetailsDescriptionPresenter;
    private SparseArrayObjectAdapter mActionsAdapter = null;


    public VideoAction(Fragment fragment, Video selectedVideo) {
        this.fragment = fragment;
        this.activity = fragment.getActivity();
        this.mSelectedVideo = selectedVideo;
    }

    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
        this.activity = fragment.getActivity();
    }

    public void setDetailsDescriptionPresenter(DetailsDescriptionPresenter mDetailsDescriptionPresenter) {
        this.mDetailsDescriptionPresenter = mDetailsDescriptionPresenter;
    }

    public void setActionsAdapter(SparseArrayObjectAdapter mActionsAdapter) {
        this.mActionsAdapter = mActionsAdapter;
    }

    public void setSelectedVideo(Video mSelectedVideo) {
        this.mSelectedVideo = mSelectedVideo;
    }

    @Override
    public void onActionClicked(Action action) {
        int id = (int) action.getId();
        long bookmark = 0;
        long posbookmark = -1;
        ArrayList<String> prompts = null;
        ArrayList<Action> actions = null;
        String alertTitle = null;

        if (mSelectedVideo.rectype == RECTYPE_CHANNEL
                && (id == Video.ACTION_PLAY_FROM_BOOKMARK || id == Video.ACTION_PLAY
                || id == Video.ACTION_PLAY_FROM_LASTPOS))
            id = Video.ACTION_LIVETV;
        if (id == Video.ACTION_PLAY_FROM_LASTPOS && mLastPlay <= 0 && mPosLastPlay <= 0)
            id = Video.ACTION_PLAY_FROM_BOOKMARK;
        AsyncBackendCall call;
        boolean set = false;
        switch (id) {
            case Video.ACTION_PLAY_FROM_BOOKMARK:
                bookmark = mBookmark;
                posbookmark = mPosBookmark;
                set = true;
            case Video.ACTION_PLAY_FROM_LASTPOS:
                if (!set) {
                    bookmark = mLastPlay;
                    posbookmark = mPosLastPlay;
                    set = true;
                }
            case Video.ACTION_PLAY:
                Intent intent = new Intent(activity, PlaybackActivity.class);
                intent.putExtra(PlaybackActivity.VIDEO, mSelectedVideo);
                intent.putExtra(PlaybackActivity.BOOKMARK, bookmark);
                intent.putExtra(PlaybackActivity.POSBOOKMARK, posbookmark);
                fragment.startActivityForResult(intent, Video.ACTION_PLAY);
                break;
            case Video.ACTION_LIVETV:
                setProgressBar(true);
                call = new AsyncBackendCall(activity, this);
                call.setStartTime(null);
                call.setChanid(Integer.parseInt(mSelectedVideo.chanid));
                call.setCallSign(mSelectedVideo.callsign);
                call.execute(Video.ACTION_LIVETV, Video.ACTION_ADD_OR_UPDATERECRULE, Video.ACTION_WAIT_RECORDING);
                break;
            case Video.ACTION_DELETE:
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REFRESH, Video.ACTION_DELETE,
                        Video.ACTION_PAUSE, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_DELETE_AND_RERECORD:
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REFRESH, Video.ACTION_DELETE_AND_RERECORD,
                        Video.ACTION_PAUSE, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_ALLOW_RERECORD:
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_ALLOW_RERECORD);
                break;
            case Video.ACTION_UNDELETE:
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_UNDELETE, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_SET_WATCHED:
            case Video.ACTION_SET_UNWATCHED:
                mWatched = (id == Video.ACTION_SET_WATCHED);
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.setWatched(mWatched);
                call.execute(Video.ACTION_SET_WATCHED, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_REMOVE_LASTPLAYPOS:
                mBookmark = 0;
                mPosBookmark = 0;
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.setLastPlay(mBookmark);
                call.setPosLastPlay(mPosBookmark);
                call.execute(Video.ACTION_SET_LASTPLAYPOS, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_REMOVE_BOOKMARK:
                mBookmark = 0;
                mPosBookmark = 0;
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.setBookmark(mBookmark);
                call.setPosBookmark(mPosBookmark);
                call.execute(Video.ACTION_SET_BOOKMARK, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_QUERY_STOP_RECORDING:
                prompts = new ArrayList<>();
                actions = new ArrayList<>();
                alertTitle = fragment.getString(R.string.title_are_you_sure);
                prompts.add(fragment.getString(R.string.menu_dont_stop_recording));
                actions.add(new Action(Video.ACTION_CANCEL));
                prompts.add(fragment.getString(R.string.menu_stop_recording));
                actions.add(new Action(Video.ACTION_STOP_RECORDING));
                break;
            case Video.ACTION_STOP_RECORDING:
                if (mSelectedVideo.recordedid != null) {
                    // Terminate a recording that may be a scheduled event
                    // so don't remove the record rule.
                    call = new AsyncBackendCall(activity, this);
                    call.setVideo(mSelectedVideo);
                    call.setRecordedId(Integer.parseInt(mSelectedVideo.recordedid));
                    call.execute(Video.ACTION_STOP_RECORDING,
                            Video.ACTION_PAUSE,
                            Video.ACTION_REFRESH);
                }
                break;
            case Video.ACTION_GETRECGROUPLIST:
                call = new AsyncBackendCall(activity, this);
                call.execute(Video.ACTION_GETRECGROUPLIST);
                break;
            case Video.ACTION_QUERY_UPDATE_RECGROUP:
                alertTitle = fragment.getString(R.string.menu_update_recgrp);
                prompts = (ArrayList<String>) mRecGroupList.clone();
                prompts.remove("LiveTV");
                prompts.add(fragment.getString(R.string.sched_new_entry));
                final ArrayList<String> groups = prompts;
                AlertDialog.Builder listBbuilder = new AlertDialog.Builder(activity,
                        R.style.Theme_AppCompat_Dialog_Alert);
                listBbuilder
                        .setTitle(alertTitle)
                        .setItems(groups.toArray(new String[0]),
                                (dialog, which) -> {
                                    // The 'which' argument contains the index position
                                    // of the selected item
                                    // Last item in the list is "Create
                                    if (which == groups.size() - 1) {
                                        mNewValueText="";
                                        promptForNewValue(R.string.sched_rec_group, Video.ACTION_UPDATE_RECGROUP);
                                    } else {
                                        mNewValueText = groups.get(which);
                                        onActionClicked(new Action(Video.ACTION_UPDATE_RECGROUP));
                                    }
                                });
                listBbuilder.show();

                break;
            case Video.ACTION_UPDATE_RECGROUP:
                if (mSelectedVideo.recordedid != null && mNewValueText.length() > 0) {
                    call = new AsyncBackendCall(activity, this);
                    mSelectedVideo.recGroup = mNewValueText;
                    call.setVideo(mSelectedVideo);
                    call.execute(Video.ACTION_UPDATE_RECGROUP);
                }
                break;
            case Video.ACTION_CANCEL:
                break;
            case Video.ACTION_VIEW_DESCRIPTION:
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_VIEW_DESCRIPTION);
                break;
            case Video.ACTION_REMOVE_RECENT:
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REMOVE_RECENT, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_PLAY_FROM_OTHER:
                prompts = new ArrayList<>();
                actions = new ArrayList<>();

                if (mLastPlay > 0 || mPosLastPlay > 0) {
                    prompts.add(fragment.getString(R.string.resume_last_1) + " "
                            + fragment.getString(R.string.resume_last_2));
                    actions.add(new Action(Video.ACTION_PLAY_FROM_LASTPOS));
                }
                if (mBookmark > 0 || mPosBookmark > 0) {
                    prompts.add(fragment.getString(R.string.resume_bkmrk_1) + " "
                            + fragment.getString(R.string.resume_bkmrk_2));
                    actions.add(new Action(Video.ACTION_PLAY_FROM_BOOKMARK));
                }
                prompts.add(fragment.getString(R.string.play_1) + " "
                        + fragment.getString(R.string.play_2));
                actions.add(new Action(Video.ACTION_PLAY));
                break;
            case Video.ACTION_REFRESH:
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REFRESH);
                break;
            case Video.ACTION_REFRESH_FULL:
                call = new AsyncBackendCall(activity, this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REFRESH, Video.ACTION_FULL_MENU);
                break;
            case Video.ACTION_FULL_MENU:
            case Video.ACTION_OTHER:
                if (mSelectedVideo.rectype != RECTYPE_RECORDING
                        && mSelectedVideo.rectype != RECTYPE_VIDEO)
                    break;
                prompts = new ArrayList<>();
                actions = new ArrayList<>();
                if (id == Video.ACTION_FULL_MENU) {
                    if (mLastPlay > 0 || mPosLastPlay > 0) {
                        prompts.add(fragment.getString(R.string.resume_last_1) + " "
                                + fragment.getString(R.string.resume_last_2));
                        actions.add(new Action(Video.ACTION_PLAY_FROM_LASTPOS));
                    }
                    if (mBookmark > 0 || mPosBookmark > 0) {
                        prompts.add(fragment.getString(R.string.resume_bkmrk_1) + " "
                                + fragment.getString(R.string.resume_bkmrk_2));
                        actions.add(new Action(Video.ACTION_PLAY_FROM_BOOKMARK));
                    }
                    prompts.add(fragment.getString(R.string.play_1) + " "
                            + fragment.getString(R.string.play_2));
                    actions.add(new Action(Video.ACTION_PLAY));
                }
                boolean busyRecording = false;
                // Check End Time
                try {
                    SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
                    if (mSelectedVideo.endtime != null) {
                        Date dateEnd = dbFormat.parse(mSelectedVideo.endtime + "+0000");
                        long dateMS = dateEnd.getTime();
                        // If end time is more than 2 mins in the future allow stopping
                        if (dateMS > System.currentTimeMillis() + 120000)
                            busyRecording = true;
                    }
                } catch (ParseException e) {
                    Log.e(TAG, CLASS + " Exception parsing endtime.", e);
                }
                if (mSelectedVideo.rectype == RECTYPE_RECORDING && !busyRecording) {
                    if ("Deleted".equals(mSelectedVideo.recGroup)) {
                        prompts.add(fragment.getString(R.string.menu_undelete));
                        actions.add(new Action(Video.ACTION_UNDELETE));
                    } else {
                        prompts.add(fragment.getString(R.string.menu_delete));
                        actions.add(new Action(Video.ACTION_DELETE));
                        prompts.add(fragment.getString(R.string.menu_delete_rerecord));
                        actions.add(new Action(Video.ACTION_DELETE_AND_RERECORD));
                    }
                    prompts.add(fragment.getString(R.string.menu_rerecord));
                    actions.add(new Action(Video.ACTION_ALLOW_RERECORD));
                    if (BackendCache.getInstance().canUpdateRecGroup) {
                        prompts.add(fragment.getString(R.string.menu_update_recgrp));
                        actions.add(new Action(Video.ACTION_GETRECGROUPLIST));
                    }
                }
                if (mWatched) {
                    prompts.add(fragment.getString(R.string.menu_mark_unwatched));
                    actions.add(new Action(Video.ACTION_SET_UNWATCHED));
                } else {
                    prompts.add(fragment.getString(R.string.menu_mark_watched));
                    actions.add(new Action(Video.ACTION_SET_WATCHED));
                }
                if (mLastPlay > 0 || mPosLastPlay > 0) {
                    prompts.add(fragment.getString(R.string.menu_remove_lastplaypos));
                    actions.add(new Action(Video.ACTION_REMOVE_LASTPLAYPOS));
                }
                if (mBookmark > 0 || mPosBookmark > 0) {
                    prompts.add(fragment.getString(R.string.menu_remove_bookmark));
                    actions.add(new Action(Video.ACTION_REMOVE_BOOKMARK));
                }
                if (mSelectedVideo.isRecentViewed()) {
                    prompts.add(fragment.getString(R.string.menu_remove_from_recent));
                    actions.add(new Action(Video.ACTION_REMOVE_RECENT));
                }
                if (busyRecording) {
                    prompts.add(fragment.getString(R.string.menu_stop_recording));
                    actions.add(new Action(Video.ACTION_QUERY_STOP_RECORDING));
                }

                // View Description
                if (mDetailsDescriptionPresenter != null) {
                    prompts.add(fragment.getString(R.string.menu_view_description));
                    actions.add(new Action(Video.ACTION_VIEW_DESCRIPTION));
                }
                break;

            default:
                Toast.makeText(activity, action.toString(), Toast.LENGTH_SHORT).show();
        }
        if (prompts != null && actions != null) {
            final ArrayList<Action> finalActions = actions; // needed because used in inner class
            if (alertTitle == null)
                alertTitle = mSelectedVideo.title + ": " + mSelectedVideo.subtitle;
            // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
            AlertDialog.Builder builder = new AlertDialog.Builder(activity,
                    R.style.Theme_AppCompat_Dialog_Alert);
            OnActionClickedListener parent = this;
            builder
                    .setTitle(alertTitle)
                    .setItems(prompts.toArray(new String[0]),
                            new DialogInterface.OnClickListener() {
                                ArrayList<Action> mActions = finalActions;
                                OnActionClickedListener mParent = parent;

                                public void onClick(DialogInterface dialog, int which) {
                                    // The 'which' argument contains the index position
                                    // of the selected item
                                    if (which < mActions.size()) {
                                        mParent.onActionClicked(mActions.get(which));
                                    }
                                }
                            });
            builder.show();
        }
    }
    public void onPostExecute(AsyncBackendCall taskRunner) {
        if (taskRunner == null)
            return;
        int [] tasks = taskRunner.getTasks();
        XmlNode xml = taskRunner.getXmlResult();
        switch (tasks[0]) {
            case Video.ACTION_LIVETV:
                setProgressBar(false);
                Video video = taskRunner.getVideo();
                // video null means recording failed
                // activity null means user pressed back button
                if (video == null || activity == null) {
                    if (activity != null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity,
                                R.style.Theme_AppCompat_Dialog_Alert);
                        builder.setTitle(R.string.title_alert_livetv);
                        String msg = activity.getString(R.string.alert_livetv_fail_message,taskRunner.getStringParameter());
                        builder.setMessage(msg);
                        // add a button
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.show();
                    }
                    long recordId = taskRunner.getRecordId();
                    long recordedId = taskRunner.getRecordedId();
                    video = new Video.VideoBuilder()
                            .recGroup("LiveTV")
                            .recordedid(String.valueOf(recordedId))
                            .build();
                    if (recordId >= 0) {
                        // Terminate Live TV
                        AsyncBackendCall call = new AsyncBackendCall(activity, this);
                        call.setVideo(video);
                        call.setRecordId(recordId);
                        call.setRecordedId(Integer.parseInt(video.recordedid));
                        call.execute(
                                Video.ACTION_STOP_RECORDING,
                                Video.ACTION_REMOVE_RECORD_RULE);
                    }
                    break;
                }
                Intent intent = new Intent(activity, PlaybackActivity.class);
                intent.putExtra(PlaybackActivity.VIDEO, video);
                intent.putExtra(PlaybackActivity.BOOKMARK, 0L);
                intent.putExtra(PlaybackActivity.RECORDID, taskRunner.getRecordId());
                intent.putExtra(PlaybackActivity.ENDTIME, taskRunner.getEndTime().getTime());
                activity.startActivity(intent);
                break;
            case Video.ACTION_ALLOW_RERECORD:
                if (xml != null && "true".equals(xml.getString())) {
                    Toast.makeText(activity,R.string.msg_allowed_rerecord, Toast.LENGTH_LONG)
                            .show();
                }
                else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity,
                            R.style.Theme_AppCompat_Dialog_Alert);
                    builder.setTitle(R.string.msg_error_title);
                    builder.setMessage(R.string.msg_fail_allowed_rerecord);
                    builder.show();
                }
                break;
            case Video.ACTION_GETRECGROUPLIST:
                mRecGroupList = XmlNode.getStringList(xml); // ACTION_GETRECGROUPLIST
                onActionClicked(new Action(Video.ACTION_QUERY_UPDATE_RECGROUP));
                break;
            case Video.ACTION_VIEW_DESCRIPTION:
                if (mDetailsDescriptionPresenter == null)
                    break;
                xml = taskRunner.getXmlResult();
                AlertDialog.Builder builder = new AlertDialog.Builder(activity,
                        R.style.Theme_AppCompat);
                String msg = mSelectedVideo.title + "\n"
                        + mDetailsDescriptionPresenter.getSubtitle() + "\n"
                        + mDetailsDescriptionPresenter.getDescription(xml);
                SpannableStringBuilder span = new SpannableStringBuilder(msg);
                span.setSpan(new TabStopSpan.Standard(400), 0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setMessage(span);
                builder.show();
                break;

            default:
                // Assume ACTION_REFRESH was in the list
                if (activity == null)
                    break;
                mBookmark = taskRunner.getBookmark();
                mPosBookmark = taskRunner.getPosBookmark();
                mLastPlay = taskRunner.getLastPlay();
                mPosLastPlay = taskRunner.getPosLastPlay();
                int progflags = Integer.parseInt(mSelectedVideo.progflags);
                mWatched = ((progflags & Video.FL_WATCHED) != 0);
                if (mDetailsDescriptionPresenter != null)
                    mDetailsDescriptionPresenter.setupDescription();
                if (mActionsAdapter != null) {
                    int i = 0;
                    mActionsAdapter.clear();
                    if (mLastPlay > 0 || mPosLastPlay > 0)
                        mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY_FROM_LASTPOS, fragment.getResources()
                                .getString(R.string.resume_last_1),
                                fragment.getResources().getString(R.string.resume_last_2)));
                    if (mBookmark > 0 || mPosBookmark > 0) {
                        if (i == 0)
                            mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY_FROM_BOOKMARK, fragment.getResources()
                                    .getString(R.string.resume_bkmrk_1),
                                    fragment.getResources().getString(R.string.resume_bkmrk_2)));
                        else
                            mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY_FROM_OTHER, fragment.getResources()
                                    .getString(R.string.play_other_1),
                                    fragment.getResources().getString(R.string.play_other_2)));
                    }
                    if (i <= 1)
                        mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY, fragment.getResources()
                                .getString(R.string.play_1),
                                fragment.getResources().getString(R.string.play_2)));
                    mActionsAdapter.set(++i, new Action(Video.ACTION_OTHER, fragment.getResources()
                            .getString(R.string.button_other_1),
                            fragment.getResources().getString(R.string.button_other_2)));
               }
                break;
        }
        if (tasks.length == 2 && tasks[1] == Video.ACTION_FULL_MENU)
            onActionClicked(new Action(Video.ACTION_FULL_MENU));
    }
    private void promptForNewValue(int msgid, int nextId) {
        mNewValueText = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity,
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(msgid);
        EditText input = new EditText(activity);
        input.setText(mNewValueText);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            mNewValueText = input.getText().toString();
            onActionClicked(new Action(nextId));
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }
    private void setProgressBar(boolean show) {
        if (mProgressBar == null) {
            if (!show)
                return;
            View mainView = fragment.getView();
            if (mainView == null)
                return;
            int height = mainView.getHeight();
            int padding = height * 5 / 12;
            mProgressBar = new ProgressBar(activity);
            mProgressBar.setPadding(padding,padding,padding,padding);
            ViewGroup grp = mainView.findViewById(R.id.details_fragment_root);
            grp.addView(mProgressBar);
        }
        mProgressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }
}
