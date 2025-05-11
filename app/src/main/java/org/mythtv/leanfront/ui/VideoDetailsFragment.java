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

import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_AIRDATE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_EPISODE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_FILENAME;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_PROGFLAGS;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_RECGROUP;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_RECTYPE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_SEASON;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_STARTTIME;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_TITLE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_TITLEMATCH;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_VIDEO_URL;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.CONTENT_URI;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_CHANNEL;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_RECORDING;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_VIDEO;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TabStopSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.DetailsOverviewLogoPresenter;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SparseArrayObjectAdapter;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.BackendCache;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.model.VideoCursorMapper;
import org.mythtv.leanfront.presenter.CardPresenter;
import org.mythtv.leanfront.presenter.DetailsDescriptionPresenter;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/*
 * VideoDetailsFragment extends DetailsFragment, a Wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its metadata plus related videos.
 */
@SuppressLint("SimpleDateFormat")
public class VideoDetailsFragment extends DetailsSupportFragment
        implements LoaderManager.LoaderCallbacks<Cursor>,
        AsyncBackendCall.OnBackendCallListener, OnActionClickedListener {

    private static final String TAG = "lfe";
    private static final String CLASS = "VideoDetailsFragment";

    private static final int NO_NOTIFICATION = -1;

    // ID for loader that loads related videos.
    private static final int RELATED_VIDEO_LOADER = 1;

    // Parsing results of GetRecorded
    private static final String[] XMLTAGS_RECGROUP = {"Recording","RecGroup"};
    private static final String[] XMLTAGS_PROGRAMFLAGS = {"ProgramFlags"};

    // ID for loader that loads the video from global search.

    private Video mSelectedVideo;
    private ArrayObjectAdapter mAdapter;
    private ClassPresenterSelector mPresenterSelector;
    private BackgroundManager mBackgroundManager;
    private Drawable mDefaultBackground;
    private Uri mDefaultBackgroundURI;
    private DisplayMetrics mMetrics;
    private CursorObjectAdapter mVideoCursorAdapter;
    private FullWidthDetailsOverviewSharedElementHelper mHelper;
    private final VideoCursorMapper mVideoCursorMapper = new VideoCursorMapper();
    private long mBookmark = 0;     // milliseconds
    private long mPosBookmark = -1;  // position in frames
    private long mLastPlay = 0;     // milliseconds
    private long mPosLastPlay = -1;  // position in frames
    private SparseArrayObjectAdapter mActionsAdapter = null;
    private DetailsOverviewRow mDetailsOverviewRow = null;
    private boolean mWatched;
    private DetailsDescriptionPresenter mDetailsDescriptionPresenter;
    private ProgressBar mProgressBar = null;
    private ItemViewClickedListener itemViewClickedListener = new ItemViewClickedListener();
    private ItemViewSelectedListener itemViewSelectedListener = new ItemViewSelectedListener();
    private ScrollSupport scrollSupport;
    private boolean isTV;
    private boolean actionInitialSelect;
    private ArrayList<String> mRecGroupList;
    private String mNewValueText;
//    private boolean canUpdateRecGroup = false;
    private int actionClicked;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiModeManager uiModeManager = (UiModeManager)getContext().getSystemService(Context.UI_MODE_SERVICE);
        isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

        scrollSupport = new ScrollSupport((getContext()));
        prepareBackgroundManager();
        mVideoCursorAdapter = new CursorObjectAdapter(new CardPresenter());
        mVideoCursorAdapter.setMapper(mVideoCursorMapper);

        mSelectedVideo = getActivity().getIntent()
                .getParcelableExtra(PlaybackActivity.VIDEO);

        if (savedInstanceState != null) {
            mBookmark = savedInstanceState.getLong("mBookmark");
            mPosBookmark = savedInstanceState.getLong("posBookmark");
        }

        if (mSelectedVideo != null) {
            removeNotification(getActivity().getIntent()
                    .getIntExtra(PlaybackActivity.NOTIFICATION_ID, NO_NOTIFICATION));
            setupAdapter();
            setupDetailsOverviewRow();
            setupMovieListRow();
            updateBackground(mSelectedVideo.bgImageUrl);
            int progflags = Integer.parseInt(mSelectedVideo.progflags);
            mWatched = ((progflags & Video.FL_WATCHED) != 0);
            if (mSelectedVideo.rectype != RECTYPE_CHANNEL) {
                AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REFRESH);
            }

            // When a Related Video item is clicked.
            setOnItemViewClickedListener(itemViewClickedListener);
            setOnItemViewSelectedListener(itemViewSelectedListener);
        }
    }

    public void onSaveInstanceState (Bundle outState) {
        outState.putLong("mBookmark", mBookmark);
        outState.putLong("posBookmark", mPosBookmark);
        super.onSaveInstanceState(outState);
    }

    private void removeNotification(int notificationId) {
        if (notificationId != NO_NOTIFICATION) {
            NotificationManager notificationManager = (NotificationManager) getActivity()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
        }
    }

    @Override
    public void onDestroy() {
        mBackgroundManager = null;
        super.onDestroy();
    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    @Override
    public void onResume() {
        updateBackground(mSelectedVideo.bgImageUrl);
        super.onResume();
        actionInitialSelect = true;
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        int resourceId = R.drawable.background;
        Resources resources = getResources();
        mDefaultBackgroundURI = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(resourceId))
                .appendPath(resources.getResourceTypeName(resourceId))
                .appendPath(resources.getResourceEntryName(resourceId))
                .build();
        mDefaultBackground = getResources().getDrawable(R.drawable.background, null);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void updateBackground(String uri) {
        if (uri == null)
            uri = mDefaultBackgroundURI.toString();
        RequestOptions options = new RequestOptions()
                .centerCrop()
                .error(mDefaultBackground);

        String auth =  BackendCache.getInstance().authorization;
        LazyHeaders.Builder lzhb =  new LazyHeaders.Builder();
        if (auth != null && auth.length() > 0)
            lzhb.addHeader("Authorization", auth);
        GlideUrl url = new GlideUrl(uri, lzhb.build());

        Glide.with(this)
                .asBitmap()
                .load(url)
                .apply(options)
                .into(new CustomTarget<Bitmap>(mMetrics.widthPixels, mMetrics.heightPixels) {
                    @Override
                    public void onResourceReady(
                            @NonNull Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        if (mBackgroundManager != null && mBackgroundManager.getDrawable() != null)
                            mBackgroundManager.clearDrawable();
                    }
                });
    }

    private void setupAdapter() {
        mDetailsDescriptionPresenter = new DetailsDescriptionPresenter();
        // Set detail background and style.
        FullWidthDetailsOverviewRowPresenter detailsPresenter =
                new FullWidthDetailsOverviewRowPresenter(mDetailsDescriptionPresenter,
                        new MovieDetailsOverviewLogoPresenter());
        Activity activity = getActivity();
        detailsPresenter.setBackgroundColor(
                ContextCompat.getColor(activity, R.color.selected_background));
        detailsPresenter.setInitialState(FullWidthDetailsOverviewRowPresenter.STATE_HALF);

        // Hook up transition element.
        mHelper = new FullWidthDetailsOverviewSharedElementHelper();
        mHelper.setSharedElementEnterTransition(activity,
                PlaybackActivity.SHARED_ELEMENT_NAME);
        detailsPresenter.setListener(mHelper);
        detailsPresenter.setParticipatingEntranceTransition(false);
        prepareEntranceTransition();

        detailsPresenter.setOnActionClickedListener(this);

        mPresenterSelector = new ClassPresenterSelector();
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
        mAdapter = new ArrayObjectAdapter(mPresenterSelector);
        setAdapter(mAdapter);
    }

    public void pageDown(int direction) {
        RowsSupportFragment frag = getRowsSupportFragment();
        int selectedRowNum = frag.getSelectedPosition();
        ListRowPresenter.ViewHolder selectedViewHolder
                = (ListRowPresenter.ViewHolder) getRowsSupportFragment()
                .getRowViewHolder(selectedRowNum);
        if (selectedViewHolder == null)
            return;
        int selectedItemNum = selectedViewHolder.getSelectedPosition();
        int newPos = selectedItemNum + 5 * direction; // 5 = 1 page
        if (newPos < 0)
            newPos = 0;
        ListRowPresenter.SelectItemViewHolderTask task
                = new ListRowPresenter.SelectItemViewHolderTask(newPos);
        task.setSmoothScroll(false);
        frag.setSelectedPosition(selectedRowNum, false, task);
    }


    @Override
    public void onActionClicked(Action action) {
        int id = (int) action.getId();
        long bookmark = 0;
        long posbookmark = -1;
        ArrayList<String> prompts = null;
        ArrayList<Action> actions = null;
        String alertTitle = null;
        Log.e(TAG, CLASS + " selectedPosition:" + getRowsSupportFragment().getSelectedPosition());

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
                Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                intent.putExtra(PlaybackActivity.VIDEO, mSelectedVideo);
                intent.putExtra(PlaybackActivity.BOOKMARK, bookmark);
                intent.putExtra(PlaybackActivity.POSBOOKMARK, posbookmark);
                startActivityForResult(intent, Video.ACTION_PLAY);
                break;
            case Video.ACTION_LIVETV:
                setProgressBar(true);
                call = new AsyncBackendCall(getActivity(), this);
                call.setStartTime(null);
                call.setChanid(Integer.parseInt(mSelectedVideo.chanid));
                call.setCallSign(mSelectedVideo.callsign);
                call.execute(Video.ACTION_LIVETV, Video.ACTION_ADD_OR_UPDATERECRULE, Video.ACTION_WAIT_RECORDING);
                break;
            case Video.ACTION_DELETE:
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REFRESH, Video.ACTION_DELETE,
                                Video.ACTION_PAUSE, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_DELETE_AND_RERECORD:
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REFRESH, Video.ACTION_DELETE_AND_RERECORD,
                                Video.ACTION_PAUSE, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_ALLOW_RERECORD:
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_ALLOW_RERECORD);
                break;
            case Video.ACTION_UNDELETE:
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_UNDELETE, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_SET_WATCHED:
            case Video.ACTION_SET_UNWATCHED:
                mWatched = (id == Video.ACTION_SET_WATCHED);
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.setWatched(mWatched);
                call.execute(Video.ACTION_SET_WATCHED, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_REMOVE_LASTPLAYPOS:
                mBookmark = 0;
                mPosBookmark = 0;
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.setLastPlay(mBookmark);
                call.setPosLastPlay(mPosBookmark);
                call.execute(Video.ACTION_SET_LASTPLAYPOS, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_REMOVE_BOOKMARK:
                mBookmark = 0;
                mPosBookmark = 0;
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.setBookmark(mBookmark);
                call.setPosBookmark(mPosBookmark);
                call.execute(Video.ACTION_SET_BOOKMARK, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_QUERY_STOP_RECORDING:
                prompts = new ArrayList<>();
                actions = new ArrayList<>();
                alertTitle = getString(R.string.title_are_you_sure);
                prompts.add(getString(R.string.menu_dont_stop_recording));
                actions.add(new Action(Video.ACTION_CANCEL));
                prompts.add(getString(R.string.menu_stop_recording));
                actions.add(new Action(Video.ACTION_STOP_RECORDING));
                break;
            case Video.ACTION_STOP_RECORDING:
                if (mSelectedVideo.recordedid != null) {
                    // Terminate a recording that may be a scheduled event
                    // so don't remove the record rule.
                    call = new AsyncBackendCall(getActivity(), this);
                    call.setVideo(mSelectedVideo);
                    call.setRecordedId(Integer.parseInt(mSelectedVideo.recordedid));
                    call.execute(Video.ACTION_STOP_RECORDING,
                                    Video.ACTION_PAUSE,
                                    Video.ACTION_REFRESH);
                }
                break;
            case Video.ACTION_GETRECGROUPLIST:
                call = new AsyncBackendCall(getActivity(), this);
                call.execute(Video.ACTION_GETRECGROUPLIST);
                break;
            case Video.ACTION_QUERY_UPDATE_RECGROUP:
                alertTitle = getString(R.string.menu_update_recgrp);
                prompts = (ArrayList<String>) mRecGroupList.clone();
                prompts.remove("LiveTV");
                prompts.add(getString(R.string.sched_new_entry));
                final ArrayList<String> groups = prompts;
                AlertDialog.Builder listBbuilder = new AlertDialog.Builder(getActivity(),
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
                    call = new AsyncBackendCall(getActivity(), this);
                    mSelectedVideo.recGroup = mNewValueText;
                    call.setVideo(mSelectedVideo);
                    call.execute(Video.ACTION_UPDATE_RECGROUP);
                }
                break;
            case Video.ACTION_CANCEL:
                break;
            case Video.ACTION_VIEW_DESCRIPTION:
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_VIEW_DESCRIPTION);
                break;
            case Video.ACTION_REMOVE_RECENT:
                call = new AsyncBackendCall(getActivity(), this);
                call.setVideo(mSelectedVideo);
                call.execute(Video.ACTION_REMOVE_RECENT, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_PLAY_FROM_OTHER:
                prompts = new ArrayList<>();
                actions = new ArrayList<>();

                if (mLastPlay > 0 || mPosLastPlay > 0) {
                    prompts.add(getString(R.string.resume_last_1) + " "
                            + getString(R.string.resume_last_2));
                    actions.add(new Action(Video.ACTION_PLAY_FROM_LASTPOS));
                }
                if (mBookmark > 0 || mPosBookmark > 0) {
                    prompts.add(getString(R.string.resume_bkmrk_1) + " "
                            + getString(R.string.resume_bkmrk_2));
                    actions.add(new Action(Video.ACTION_PLAY_FROM_BOOKMARK));
                }
                prompts.add(getString(R.string.play_1) + " "
                        + getString(R.string.play_2));
                actions.add(new Action(Video.ACTION_PLAY));
                break;
            case Video.ACTION_OTHER:
                if (mSelectedVideo.rectype != RECTYPE_RECORDING
                        && mSelectedVideo.rectype != RECTYPE_VIDEO)
                    break;
                prompts = new ArrayList<>();
                actions = new ArrayList<>();
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
                        prompts.add(getString(R.string.menu_undelete));
                        actions.add(new Action(Video.ACTION_UNDELETE));
                    } else {
                        prompts.add(getString(R.string.menu_delete));
                        actions.add(new Action(Video.ACTION_DELETE));
                        prompts.add(getString(R.string.menu_delete_rerecord));
                        actions.add(new Action(Video.ACTION_DELETE_AND_RERECORD));
                    }
                    prompts.add(getString(R.string.menu_rerecord));
                    actions.add(new Action(Video.ACTION_ALLOW_RERECORD));
                    if (BackendCache.getInstance().canUpdateRecGroup) {
                        prompts.add(getString(R.string.menu_update_recgrp));
                        actions.add(new Action(Video.ACTION_GETRECGROUPLIST));
                    }
                }
                if (mWatched) {
                    prompts.add(getString(R.string.menu_mark_unwatched));
                    actions.add(new Action(Video.ACTION_SET_UNWATCHED));
                } else {
                    prompts.add(getString(R.string.menu_mark_watched));
                    actions.add(new Action(Video.ACTION_SET_WATCHED));
                }
                if (mLastPlay > 0 || mPosLastPlay > 0) {
                    prompts.add(getString(R.string.menu_remove_lastplaypos));
                    actions.add(new Action(Video.ACTION_REMOVE_LASTPLAYPOS));
                }
                if (mBookmark > 0 || mPosBookmark > 0) {
                    prompts.add(getString(R.string.menu_remove_bookmark));
                    actions.add(new Action(Video.ACTION_REMOVE_BOOKMARK));
                }
                if (mSelectedVideo.isRecentViewed()) {
                    prompts.add(getString(R.string.menu_remove_from_recent));
                    actions.add(new Action(Video.ACTION_REMOVE_RECENT));
                }
                if (busyRecording) {
                    prompts.add(getString(R.string.menu_stop_recording));
                    actions.add(new Action(Video.ACTION_QUERY_STOP_RECORDING));
                }

                // View Description
                prompts.add(getString(R.string.menu_view_description));
                actions.add(new Action(Video.ACTION_VIEW_DESCRIPTION));
                break;

            default:
                Toast.makeText(getActivity(), action.toString(), Toast.LENGTH_SHORT).show();
        }
        if (prompts != null && actions != null) {
            final ArrayList<Action> finalActions = actions; // needed because used in inner class
            if (alertTitle == null)
                alertTitle = mSelectedVideo.title;
            // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
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

    private void promptForNewValue(int msgid, int nextId) {
        mNewValueText = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(msgid);
        EditText input = new EditText(getContext());
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


    public void onActivityResult (int requestCode,
                                  int resultCode,
                                  Intent intent) {
        if (requestCode == Video.ACTION_PLAY
                && mSelectedVideo.rectype != RECTYPE_CHANNEL) {
            AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
            call.setVideo(mSelectedVideo);
            call.execute(Video.ACTION_REFRESH);
        }
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        boolean showDeleted = "true".equals(Settings.getString("pref_related_deleted"));
        // Always show deleted videos if the one you are watching is deleted
        boolean showWatched = "true".equals(Settings.getString("pref_related_watched"));
        String seq = Settings.getString("pref_seq");
        String ascdesc = Settings.getString("pref_seq_ascdesc");

        switch (id) {

            case RELATED_VIDEO_LOADER: {
                // When loading related videos or videos for the playlist, query by category.
                int rectype = args.getInt(COLUMN_RECTYPE, -1);
                String recgroup = args.getString(COLUMN_RECGROUP);
                String filename = args.getString(COLUMN_FILENAME);
                StringBuilder orderby;
                if (rectype == RECTYPE_VIDEO) {
                    // Videos
                    int pos = filename.lastIndexOf('/');
                    String dirname = "";
                    if (pos >= 0)
                        dirname = filename.substring(0, pos + 1);
                    dirname = dirname + "%";
                    String subdirname = dirname + "%/%";

                    orderby = MainFragment.makeTitleSort
                            (COLUMN_FILENAME, '/');
                    StringBuilder where = new StringBuilder();
                    where   .append(COLUMN_RECTYPE)
                            .append(" = ").append(RECTYPE_VIDEO)
                            .append(" and ")
                            .append(COLUMN_FILENAME)
                            .append(" like ? and ")
                            .append(COLUMN_FILENAME)
                            .append(" not like ? ");
                    if (!showWatched)
                        where.append(" and ")
                             .append(COLUMN_PROGFLAGS)
                             .append(" & ").append(Video.FL_WATCHED)
                             .append(" == 0");
                    where.append(" or ")
                            .append(COLUMN_VIDEO_URL)
                            .append(" = ? ");

                    return new CursorLoader(
                            getActivity(),
                            CONTENT_URI,
                            null,
                            where.toString(),
                            new String[]{dirname, subdirname, args.getString(COLUMN_VIDEO_URL)},
                            orderby.toString());
                } else {
                    // Recordings, LiveTV or videos that are part of a series
                    String category = args.getString(COLUMN_TITLEMATCH);
                    StringBuilder where = new StringBuilder();
                    where.append(COLUMN_TITLEMATCH).append(" = ? ")
                            .append(" AND ( ").append(COLUMN_RECGROUP)
                            .append(" IS NULL OR ( ")
                            .append(COLUMN_RECGROUP)
                            .append(" != 'LiveTV' ");
                    if (!showDeleted) {
                        where.append(" AND ").append(COLUMN_RECGROUP)
                            .append(" != 'Deleted' ");
                    }
                    where.append(" ) ) ");
                    if (!showWatched)
                        where.append(" AND ")
                                .append(COLUMN_PROGFLAGS)
                                .append(" & ").append(Video.FL_WATCHED)
                                .append(" == 0 ");
                    where.append(" or ")
                            .append(COLUMN_VIDEO_URL)
                            .append(" = ? ");

                    orderby = new StringBuilder();
                    if ("airdate".equals(seq)) {
                        // +0 is used to convert the value to a number
                        orderby.append(COLUMN_SEASON).append("+0 ")
                                .append(ascdesc).append(", ");
                        orderby.append(COLUMN_EPISODE).append("+0 ")
                                .append(ascdesc).append(", ");
                        orderby.append(COLUMN_AIRDATE).append(" ")
                                .append(ascdesc).append(", ");
                        orderby.append(COLUMN_STARTTIME).append(" ")
                                .append(ascdesc);
                    }
                    else {
                        orderby.append(COLUMN_STARTTIME).append(" ")
                                .append(ascdesc).append(", ");
                        orderby.append(COLUMN_AIRDATE).append(" ")
                                .append(ascdesc);
                    }
                    String [] selectionArgs;
                    selectionArgs = new String[]{category, args.getString(COLUMN_VIDEO_URL)};
                    return new CursorLoader(
                            getActivity(),
                            CONTENT_URI,
                            null,
                            where.toString(),
                            selectionArgs,
                            orderby.toString());
                }
            }
            default: {
                // Loading video from global search.
                String videoId = args.getString(VideoContract.VideoEntry._ID);
                return new CursorLoader(
                        getActivity(),
                        CONTENT_URI,
                        null,
                        VideoContract.VideoEntry._ID + " = ?",
                        new String[]{videoId},
                        null);
            }
        }

    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        if (cursor != null && cursor.moveToNext()) {
            switch (loader.getId()) {
                case RELATED_VIDEO_LOADER: {
                    mVideoCursorAdapter.changeCursor(cursor);
                    break;
                }
                default: {
                    // Loading video from global search.
                    mSelectedVideo = (Video) mVideoCursorMapper.convert(cursor);

                    setupAdapter();
                    setupDetailsOverviewRow();
                    setupMovieListRow();
                    updateBackground(mSelectedVideo.bgImageUrl);
                    AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
                    call.setVideo(mSelectedVideo);
                    call.execute(Video.ACTION_REFRESH);
                    // When a Related Video item is clicked.
                    setOnItemViewClickedListener(itemViewClickedListener);
                    setOnItemViewSelectedListener(itemViewSelectedListener);
                }
            }
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mVideoCursorAdapter.changeCursor(null);
    }

    static class MovieDetailsOverviewLogoPresenter extends DetailsOverviewLogoPresenter {

        static class ViewHolder extends DetailsOverviewLogoPresenter.ViewHolder {
            public ViewHolder(View view) {
                super(view);
            }

            public FullWidthDetailsOverviewRowPresenter getParentPresenter() {
                return mParentPresenter;
            }

            public FullWidthDetailsOverviewRowPresenter.ViewHolder getParentViewHolder() {
                return mParentViewHolder;
            }
        }

        @Override
        public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
            ImageView imageView = (ImageView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.lb_fullwidth_details_overview_logo, parent, false);

            Resources res = parent.getResources();
            int width = res.getDimensionPixelSize(R.dimen.detail_thumb_width);
            int height = res.getDimensionPixelSize(R.dimen.detail_thumb_height);
            imageView.setLayoutParams(new ViewGroup.MarginLayoutParams(width, height));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            return new ViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
            DetailsOverviewRow row = (DetailsOverviewRow) item;
            ImageView imageView = ((ImageView) viewHolder.view);
            imageView.setImageDrawable(row.getImageDrawable());
            if (isBoundToImage((ViewHolder) viewHolder, row)) {
                MovieDetailsOverviewLogoPresenter.ViewHolder vh =
                        (MovieDetailsOverviewLogoPresenter.ViewHolder) viewHolder;
                vh.getParentPresenter().notifyOnBindLogo(vh.getParentViewHolder());
            }
        }
    }

    private void setupDetailsOverviewRow() {
        mDetailsOverviewRow = new DetailsOverviewRow(mSelectedVideo);
        Drawable defaultImage = getResources().getDrawable(R.drawable.im_movie, null);

        int defaultIcon = R.drawable.im_movie;
        String imageUrl = mSelectedVideo.cardImageUrl;
        if (mSelectedVideo.rectype == RECTYPE_CHANNEL) {
            defaultIcon = R.drawable.im_live_tv;
            try {
                imageUrl = XmlNode.mythApiUrl(null, "/Guide/GetChannelIcon?ChanId=" + mSelectedVideo.chanid);
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
        }

        RequestOptions options = new RequestOptions()
                .error(defaultIcon)
                .fallback(defaultIcon)
                .dontAnimate();

        CustomTarget<Bitmap> target = new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(
                            @NonNull Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mDetailsOverviewRow.setImageBitmap(getActivity(), resource);
                        startEntranceTransition();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        super.onLoadFailed(errorDrawable);
                        mDetailsOverviewRow.setImageDrawable(defaultImage);
                    }
                };
        if (imageUrl == null)
            Glide.with(this)
                    .asBitmap()
                    .load(defaultIcon)
                    .apply(options)
                    .into(target);
        else {

            String auth =  BackendCache.getInstance().authorization;
            LazyHeaders.Builder lzhb =  new LazyHeaders.Builder();
            if (auth != null && auth.length() > 0)
                lzhb.addHeader("Authorization", auth);
            GlideUrl url = new GlideUrl(imageUrl, lzhb.build());

            Glide.with(this)
                    .asBitmap()
                    .load(url)
                    .apply(options)
                    .into(target);
        }
        mActionsAdapter = new SparseArrayObjectAdapter();
        if (mSelectedVideo.rectype == RECTYPE_CHANNEL) {
            mActionsAdapter.set(0, new Action(Video.ACTION_LIVETV, getResources()
                    .getString(R.string.play_livetv_1),
                    getResources().getString(R.string.play_livetv_2)));
        }
        mDetailsOverviewRow.setActionsAdapter(mActionsAdapter);
        actionInitialSelect = true;

        mAdapter.add(mDetailsOverviewRow);
    }

    private void setupMovieListRow() {
        String[] subcategories = {getString(R.string.related_movies)};
        Bundle args = new Bundle();
        // related videos only display directory list in this case
        // otherwise treated as tv series
        if (mSelectedVideo.rectype == RECTYPE_VIDEO
                && (mSelectedVideo.season == null || mSelectedVideo.season.equals("0"))
                && (mSelectedVideo.episode == null || mSelectedVideo.episode.equals("0"))
                && mSelectedVideo.airdate == null)
            args.putInt(COLUMN_RECTYPE, mSelectedVideo.rectype);
        args.putString(COLUMN_TITLE, mSelectedVideo.title);
        args.putString(COLUMN_TITLEMATCH, mSelectedVideo.titlematch);
        args.putString(COLUMN_RECGROUP, mSelectedVideo.recGroup);
        args.putString(COLUMN_FILENAME, mSelectedVideo.filename);
        args.putString(COLUMN_VIDEO_URL, mSelectedVideo.videoUrl);

        LoaderManager manager = LoaderManager.getInstance(this);
        manager.initLoader(RELATED_VIDEO_LOADER, args, this);

        HeaderItem header = new HeaderItem(0, subcategories[0]);
        mAdapter.add(new ListRow(header, mVideoCursorAdapter));
    }

    private void setProgressBar(boolean show) {
        if (mProgressBar == null) {
            if (!show)
                return;
            View mainView = getView();
            if (mainView == null)
                return;
            int height = mainView.getHeight();
            int padding = height * 5 / 12;
            mProgressBar = new ProgressBar(getActivity());
            mProgressBar.setPadding(padding,padding,padding,padding);
            ViewGroup grp = mainView.findViewById(R.id.details_fragment_root);
            grp.addView(mProgressBar);
        }
        mProgressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Video) {
                Video video = (Video) item;
                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(PlaybackActivity.VIDEO, video);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        PlaybackActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {

        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Action) {
                if (!isTV && !actionInitialSelect)
                    onActionClicked((Action)item);
                actionInitialSelect = false;
            }
            else
                scrollSupport.onItemSelected(itemViewHolder,rowViewHolder, getRowsSupportFragment());
        }
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        Context context = getContext();
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
                if (video == null || context == null) {
                    if (context != null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                                R.style.Theme_AppCompat_Dialog_Alert);
                        builder.setTitle(R.string.title_alert_livetv);
                        String msg = context.getString(R.string.alert_livetv_fail_message,taskRunner.getStringParameter());
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
                        AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
                        call.setVideo(video);
                        call.setRecordId(recordId);
                        call.setRecordedId(Integer.parseInt(video.recordedid));
                        call.execute(
                                Video.ACTION_STOP_RECORDING,
                                Video.ACTION_REMOVE_RECORD_RULE);
                    }
                    break;
                }
                Intent intent = new Intent(context, PlaybackActivity.class);
                intent.putExtra(PlaybackActivity.VIDEO, video);
                intent.putExtra(PlaybackActivity.BOOKMARK, 0L);
                intent.putExtra(PlaybackActivity.RECORDID, taskRunner.getRecordId());
                intent.putExtra(PlaybackActivity.ENDTIME, taskRunner.getEndTime().getTime());
                startActivity(intent);
                break;
            case Video.ACTION_ALLOW_RERECORD:
                if (xml != null && "true".equals(xml.getString())) {
                    Toast.makeText(getContext(),R.string.msg_allowed_rerecord, Toast.LENGTH_LONG)
                            .show();
                }
                else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context,
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
//            case Video.ACTION_DVR_WSDL:
//                // Check if the UpdateRecordedMetadata method takes the RecGroup parameter
//                XmlNode  recGroupNode = xml.getNode(new String[]{"types", "schema"}, 1);
//                if (recGroupNode != null)
//                    recGroupNode = recGroupNode.getNode
//                            (new String[]{"UpdateRecordedMetadata", "complexType", "sequence", "RecGroup"},0);
//                if (recGroupNode !=null)
//                    canUpdateRecGroup = true;
//                else
//                    canUpdateRecGroup = false;
//                break;
            case Video.ACTION_VIEW_DESCRIPTION:
                xml = taskRunner.getXmlResult();
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
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
                if (context == null)
                    break;
                mBookmark = taskRunner.getBookmark();
                mPosBookmark = taskRunner.getPosBookmark();
                mLastPlay = taskRunner.getLastPlay();
                mPosLastPlay = taskRunner.getPosLastPlay();
                int progflags = Integer.parseInt(mSelectedVideo.progflags);
                mWatched = ((progflags & Video.FL_WATCHED) != 0);
                mDetailsDescriptionPresenter.setupDescription();
                int i = 0;
                mActionsAdapter.clear();
                if (mLastPlay > 0 || mPosLastPlay > 0)
                    mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY_FROM_LASTPOS, getResources()
                            .getString(R.string.resume_last_1),
                            getResources().getString(R.string.resume_last_2)));
                if (mBookmark > 0 || mPosBookmark > 0) {
                    if (i == 0)
                        mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY_FROM_BOOKMARK, getResources()
                                .getString(R.string.resume_bkmrk_1),
                                getResources().getString(R.string.resume_bkmrk_2)));
                    else
                        mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY_FROM_OTHER, getResources()
                                .getString(R.string.play_other_1),
                                getResources().getString(R.string.play_other_2)));
                }
                if (i <= 1)
                    mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY, getResources()
                            .getString(R.string.play_1),
                            getResources().getString(R.string.play_2)));
                // These add extra if needed buttons - these are now done with menu instead
                //        if ("Deleted".equals(mSelectedVideo.recGroup))
                //            mActionsAdapter.set(++i, new Action(Video.ACTION_UNDELETE, getResources()
                //                 .getString(R.string.undelete_1),
                //                  getResources().getString(R.string.undelete_2)));
                //        else
                //            mActionsAdapter.set(++i, new Action(Video.ACTION_DELETE, getResources()
                //                   .getString(R.string.delete_1),
                //                    getResources().getString(R.string.delete_2)));
                mActionsAdapter.set(++i, new Action(Video.ACTION_OTHER, getResources()
                        .getString(R.string.button_other_1),
                        getResources().getString(R.string.button_other_2)));
                actionInitialSelect = true;
                break;
        }
    }
}
