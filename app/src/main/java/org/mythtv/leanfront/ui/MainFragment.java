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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AlertDialog;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.app.ProgressBarManager;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.leanback.widget.SparseArrayObjectAdapter;
import androidx.leanback.widget.TitleViewAdapter;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.loader.app.LoaderManager;
import androidx.core.content.ContextCompat;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.mythtv.leanfront.MyApplication;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.FetchVideoService;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.VideoDbHelper;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.ListItem;
import org.mythtv.leanfront.model.MyHeaderItem;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.model.VideoCursorMapper;
import org.mythtv.leanfront.presenter.CardPresenter;
import org.mythtv.leanfront.presenter.IconHeaderItemPresenter;
import org.mythtv.leanfront.recommendation.UpdateRecommendationsService;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class MainFragment extends BrowseSupportFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, AsyncBackendCall.OnBackendCallListener {

    private static final String TAG = "lfe";
    private static final String CLASS = "MainFragment";

    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mCategoryRowAdapter;
    private Drawable mDefaultBackground;
    private Uri mDefaultBackgroundURI;
    private DisplayMetrics mMetrics;
    private Runnable mBackgroundTask;
    private Uri mBackgroundURI;
    private BackgroundManager mBackgroundManager;
    private static final int CATEGORY_LOADER = 123; // Unique ID for Category Loader.
    private CursorObjectAdapter videoCursorAdapter;
    private int mType;
    public static final String KEY_TYPE = "LEANFRONT_TYPE";
    // Type applicable to main screen
    public static final int TYPE_TOPLEVEL = 1;
    // Types applicable to main screen or row
    public static final int TYPE_RECGROUP = 2;
    // Types applicable to main screen row, or cell
    public static final int TYPE_VIDEODIR = 3;
    // Types applicable to row or cell
    public static final int TYPE_SERIES = 4;
    // Types applicable to cell
    public static final int TYPE_EPISODE = 5;
    public static final int TYPE_VIDEO = 6;
    public static final int TYPE_CHANNEL = 7;
    // Types of rows
    public static final int TYPE_TOP_ALL = 8;
    public static final int TYPE_RECGROUP_ALL = 9;
    public static final int TYPE_VIDEODIR_ALL = 10;
    public static final int TYPE_RECENTS = 11;
    // Type applicable to row or cell
    public static final int TYPE_CHANNEL_ALL = 12;
    // Special row type
    public static final int TYPE_TOOLS = 20;
    // Special Item Type
    public static final int TYPE_SETTINGS = 21;
    public static final int TYPE_REFRESH = 22;
    public static final int TYPE_INFO = 23;
    public static final int TYPE_MANAGE = 24;

    public static final String KEY_BASENAME = "LEANFRONT_BASENAME";
    public static final String KEY_ROWNAME = "LEANFRONT_ROWNAME";
    public static final String KEY_ITEMNAME = "LEANFRONT_ITEMNAME";
    // mBase is the current recgroup or directory being displayed.
    private String mBaseName;
    private String mSelectedRowName;
    private int mSelectedRowType = -1;
    private String mSelectedItemId;
    private int mSelectedItemType = -1;
    private TextView mUsageView;

    private static ScheduledExecutorService executor = null;
    private static MythTask mythTask = new MythTask();
    private static boolean scheduledTaskRunning;
    public static volatile long mFetchTime = 0;
    // Keep track of the fragment currently showing, if any.
    private static MainFragment mActiveFragment = null;
    private static boolean mWasInBackground = true;
    // Not final so I can change it during debug
    private static int TASK_INTERVAL = 240;
    private ItemViewClickedListener mItemViewClickedListener;
    private ScrollSupport scrollSupport;
    private Loader loader;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scrollSupport = new ScrollSupport((getContext()));
        Intent intent = getActivity().getIntent();
        mType = intent.getIntExtra(KEY_TYPE, TYPE_TOPLEVEL);
        if (mType == TYPE_TOPLEVEL) {
            // Clear ip address cache
            XmlNode.clearCache();
            VideoDbHelper dbh = new VideoDbHelper(getContext());
            SQLiteDatabase db = dbh.getWritableDatabase();
            // delete stale entries from bookmark table
            String where = VideoContract.StatusEntry.COLUMN_LAST_USED + " < ? ";
            // 60 days in milliseconds
            String[] selectionArgs = {String.valueOf(System.currentTimeMillis() - 60L*24*60*60*1000)};
            // https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html
            db.delete(VideoContract.StatusEntry.TABLE_NAME, where,selectionArgs);
            db.close();
            // Initialize startup members
            if (executor != null)
                executor.shutdown();
            executor = null;
            mFetchTime = 0;
            mActiveFragment = null;
            mWasInBackground = true;

        } else {
            mBaseName = intent.getStringExtra(KEY_BASENAME);
            mSelectedRowName = intent.getStringExtra(KEY_ROWNAME);
            if (mType == TYPE_VIDEODIR)
                mSelectedRowType = TYPE_VIDEODIR;
            else
                mSelectedRowType = TYPE_SERIES;
        }
        startLoader();
    }

    private void setProgressBar(boolean show) {
        ProgressBarManager manager = getProgressBarManager();
        // Initial delay defaults to 1000 (1 second)
        if (show)
            manager.show();
        else
            manager.hide();
    }

    private void setUsage(int used) {
        if (getContext() == null)
            return;
        if (mUsageView == null) {
            View mainView = getView();
            if (mainView == null)
                return;
            ViewGroup grp = mainView.findViewById(R.id.browse_title_group);
            int height = grp.getHeight();
            int width = grp.getWidth();
            mUsageView = new TextView(getContext());
            mUsageView.setTextSize(16.0f);
            mUsageView.setPadding(width / 15, height / 3, 0, 0);
            grp.addView(mUsageView, new AbsoluteLayout.LayoutParams(width / 5, height,0,0));
        }
        mUsageView.setText(getContext().getResources().getString(R.string.title_disk_usage,used));
    }

    /**
     * Fetch video list
     *
     * @param rectype Set to -1 to fetch all, or to either
     *                VideoContract.VideoEntry.RECTYPE_RECORDING or
     *                VideoContract.VideoEntry.RECTYPE_VIDEO
     * @param recordedId Set to null, or recordedId if only one to be refreshed
     * @param recGroup Set to a recordimng group if only that one is to
     *                 be refreshed
     *
     */
    public void startFetch(int rectype, String recordedId, String recGroup) {
        if (rectype == -1)
            mFetchTime = System.currentTimeMillis();
        // Start an Intent to fetch the videos.
        Intent serviceIntent = new Intent(getActivity(), FetchVideoService.class);
        serviceIntent.putExtra(FetchVideoService.RECTYPE, rectype);
        serviceIntent.putExtra(FetchVideoService.RECORDEDID, recordedId);
        serviceIntent.putExtra(FetchVideoService.RECGROUP, recGroup);
        getActivity().startService(serviceIntent);
    }

    // Load user interface from local database.
    public void startLoader() {
        LoaderManager manager = LoaderManager.getInstance(this);
        loader = manager.initLoader(CATEGORY_LOADER, null, this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // Final initialization, modifying UI elements.
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mSelectedRowName = savedInstanceState.getString("SELECTEDROWNAME");
            mSelectedItemId = savedInstanceState.getString("SELECTEDITEMID");
            mSelectedItemType = savedInstanceState.getInt("SELECTEDITEMTYPE", -1);
        }
        // Prepare the manager that maintains the same background image between activities.
        prepareBackgroundManager();

        setupUIElements();
        setupEventListeners();
        prepareEntranceTransition();

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);

        updateRecommendations();
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mBackgroundTask);
        mBackgroundManager = null;
        if (mType == TYPE_TOPLEVEL) {
            if (executor != null)
                executor.shutdown();
            executor = null;
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mActiveFragment = this;
        startBackgroundTimer();
        if (mWasInBackground || executor == null)
            restartMythTask();
        mWasInBackground = false;
        // If it's been more than an hour, refresh
        if (mFetchTime < System.currentTimeMillis() - 60*60*1000) {
            startFetch(-1, null, null);
        }
    }

    public static void restartMythTask() {
        if (!scheduledTaskRunning) {
            if (executor != null)
                executor.shutdown();
            executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate(mythTask, 0, TASK_INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onPause() {
        mActiveFragment = null;
        saveSelected();
        super.onPause();
    }

    private void saveSelected() {
        int selectedRowNum = getSelectedPosition();
        int selectedItemNum = -1;
        if (selectedRowNum >= 0 && selectedRowNum < mCategoryRowAdapter.size()) {
            ListRow selectedRow = (ListRow) mCategoryRowAdapter.get(selectedRowNum);
            ListItem headerItem = (ListItem) selectedRow.getHeaderItem();
            mSelectedRowName = headerItem.getName();
            mSelectedRowType = headerItem.getItemType();
            ListRowPresenter.ViewHolder selectedViewHolder
                    = (ListRowPresenter.ViewHolder) getRowsSupportFragment()
                    .getRowViewHolder(selectedRowNum);
            if (selectedViewHolder != null)
                selectedItemNum = selectedViewHolder.getSelectedPosition();
            if (selectedItemNum >= 0) {
                ObjectAdapter itemAdapter = selectedRow.getAdapter();
                if (itemAdapter != null
                    && (itemAdapter instanceof SparseArrayObjectAdapter
                        || selectedItemNum < itemAdapter.size())) {
                    Video item = (Video) itemAdapter.get(selectedItemNum);
                    if (item != null) {
                        mSelectedItemId = item.recordedid;
                        mSelectedItemType = item.getItemType();
                    }
                }
            }
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (outState != null) {
            outState.putString("SELECTEDROWNAME", mSelectedRowName);
            outState.putString("SELECTEDITEMID", mSelectedItemId);
            outState.putInt("SELECTEDITEMTYPE", mSelectedItemType);
        }
    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    public boolean onPlay() {
        int selectedRowNum = getSelectedPosition();
        int selectedItemNum = -1;
        if (selectedRowNum >= 0) {
            int liType = -1;
            Video video = null;
            if (!isShowingHeaders()) {
                ListRow selectedRow = (ListRow) mCategoryRowAdapter.get(selectedRowNum);
                ListRowPresenter.ViewHolder selectedViewHolder
                        = (ListRowPresenter.ViewHolder) getRowsSupportFragment()
                        .getRowViewHolder(selectedRowNum);
                if (selectedViewHolder != null)
                    selectedItemNum = selectedViewHolder.getSelectedPosition();
                if (selectedItemNum >= 0) {
                    ObjectAdapter itemAdapter = selectedRow.getAdapter();
                    video = (Video) itemAdapter.get(selectedItemNum);
                    liType = video.getItemType();
                }
                switch (liType) {
                    case TYPE_EPISODE:
                    case TYPE_VIDEO:
                    case TYPE_SERIES:
                        new AsyncBackendCall(video, 0L, false,
                                this).execute(Video.ACTION_REFRESH);
                        return true;
                    // This could be used to start live tv, but commented
                    // to suppress live tv play from channel list
                    // to discourage channel surfing
                    //                    case TYPE_CHANNEL:
                    //                        playLiveTV(video);
                    //                        return true;
                }
            }
        }
        return false;
    }

    public void playLiveTV(Video video) {
        // Schedule a recording for 3 hours starting now
        // Wait for recording to be ready
        // Play recording, passing in the info needed for cancelling it on exit.
        setProgressBar(true);
        new AsyncBackendCall(video, 0L, false,
                this).execute(Video.ACTION_LIVETV);
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        Context context = getContext();
        if (taskRunner == null)
            return;
        int [] tasks = taskRunner.getTasks();
        Intent intent;
        switch (tasks[0]) {
            case Video.ACTION_REFRESH:
                if (context == null)
                    break;
                intent = new Intent(context, PlaybackActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, taskRunner.getVideo());
                intent.putExtra(VideoDetailsActivity.BOOKMARK, taskRunner.getBookmark());
                intent.putExtra(VideoDetailsActivity.POSBOOKMARK, taskRunner.getPosBookmark());
                startActivity(intent);
                break;
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
                        builder.setMessage(R.string.alert_livetv_message);
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
                        new AsyncBackendCall(video, recordId, false,
                                null).execute(
                                Video.ACTION_STOP_RECORDING,
                                Video.ACTION_REMOVE_RECORD_RULE);
                    }
                    break;
                }
                intent = new Intent(context, PlaybackActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, video);
                intent.putExtra(VideoDetailsActivity.BOOKMARK, 0L);
                intent.putExtra(VideoDetailsActivity.RECORDID, taskRunner.getRecordId());
                startActivity(intent);
                break;
            case Video.ACTION_BACKEND_INFO_HTML:
                String result = taskRunner.getStringResult();
                if (result == null)
                    break;
                // Get rid of span elements, which are pop=ups and should not be displayed here
                String fix = result.replaceAll("<span>.+</span>","");
                Spanned spanned;
                if (android.os.Build.VERSION.SDK_INT >= 24)
                    spanned = Html.fromHtml(fix,Html.FROM_HTML_MODE_COMPACT);
                else
                    spanned =  Html.fromHtml(fix);
                AlertDialog.Builder builder = new AlertDialog.Builder(context,
                        R.style.Theme_AppCompat);
                builder.setMessage(spanned);
                builder.show();
                break;
            case Video.ACTION_BACKEND_INFO:
                XmlNode xml = taskRunner.getXmlResult();
                if (xml != null) {
                    XmlNode mach = xml.getNode("MachineInfo");
                    if (mach == null)
                        break;
                    XmlNode stg = mach.getNode("Storage");
                    if (stg == null)
                        break;
                    XmlNode grp = null;
                    for (int ix = 0;;ix++) {
                        grp = stg.getNode("Group",ix);
                        if (grp == null)
                            break;
                        String dir = grp.getAttribute("dir");
                        if ("TotalDiskSpace".equals(dir))
                            break;
                    }
                    if (grp == null)
                        break;
                    String usedStr = grp.getAttribute("used");
                    String totalStr = grp.getAttribute("total");
                    long used = Long.parseLong(usedStr);
                    long total = Long.parseLong(totalStr);
                    setUsage((int)(used * 100 / total));
                }
                break;

        }
    }


    public static MainFragment getActiveFragment() {
        return mActiveFragment;
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
        mDefaultBackground = resources.getDrawable(R.drawable.background, null);
        mBackgroundTask = new UpdateBackgroundTask();
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
        if (mType == TYPE_TOPLEVEL)
            setBadgeDrawable(
                    getActivity().getResources().getDrawable(R.drawable.mythtv_320x180_icon, null));
        setTitle(mBaseName);
        showTitle(TitleViewAdapter.FULL_VIEW_VISIBLE);
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getActivity(), R.color.fastlane_background));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.search_opaque));

        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object o) {
                return new IconHeaderItemPresenter(MainFragment.this);
            }
        });
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            }
        });

        setOnItemViewClickedListener(mItemViewClickedListener = new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        HeadersSupportFragment header = getHeadersSupportFragment();
        if (header != null)
            header.setOnHeaderClickedListener(new HeaderClickedListener());
    }

    private void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;

        if (uri == null)
            return;

        RequestOptions options = new RequestOptions()
                .centerCrop()
                .error(mDefaultBackground);

        Glide.with(this)
                .asBitmap()
                .load(uri)
                .apply(options)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(
                            Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
    }

    private void startBackgroundTimer() {
        mHandler.removeCallbacks(mBackgroundTask);
        mHandler.postDelayed(mBackgroundTask, BACKGROUND_UPDATE_DELAY);
    }

    private void updateRecommendations() {
        Intent recommendationIntent = new Intent(getActivity(), UpdateRecommendationsService.class);
        getActivity().startService(recommendationIntent);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        String seq = Settings.getString("pref_seq");
        String ascdesc = Settings.getString("pref_seq_ascdesc");
        StringBuilder orderby = new StringBuilder();
        StringBuilder selection = new StringBuilder();
        String [] selectionArgs = null;

        /*
        SQL "order by" is complicated. Below are examples for the various cases

        Top Level list or Videos list
            CASE WHEN rectype = 3 THEN 1 ELSE rectype END,
            CASE WHEN rectype = 2
             THEN REPLACE(REPLACE(REPLACE('/'||UPPER(filename),'/THE ','/'),'/A ','/'),'/AN ','/')
             ELSE NULL END,
            recgroup,
            REPLACE(REPLACE(REPLACE('^'||UPPER(suggest_text_1),'^THE ','^'),'^A ','^'),'^AN ','^'),
            starttime asc, airdate asc

        Recording Group list
            REPLACE(REPLACE(REPLACE('^'||UPPER(suggest_text_1),'^THE ','^'),'^A ','^'),'^AN ','^'),
            starttime asc, airdate asc

        LiveTV list
            CAST (channum as real), channum,
            REPLACE(REPLACE(REPLACE('^'||UPPER(suggest_text_1),'^THE ','^'),'^A ','^'),'^AN ','^'),
            starttime asc, airdate asc
         */

        if (mType == TYPE_TOPLEVEL || mType == TYPE_VIDEODIR) {
            // This case will sort channels together with videos
            orderby.append("CASE WHEN ");
            orderby.append(VideoContract.VideoEntry.COLUMN_RECTYPE).append(" = ");
            orderby.append(VideoContract.VideoEntry.RECTYPE_CHANNEL);
            orderby.append(" THEN ").append(VideoContract.VideoEntry.RECTYPE_RECORDING);
            orderby.append(" ELSE ").append(VideoContract.VideoEntry.COLUMN_RECTYPE).append(" END, ");
            orderby.append("CASE WHEN ");
            orderby.append(VideoContract.VideoEntry.COLUMN_RECTYPE).append(" = ");
            orderby.append(VideoContract.VideoEntry.RECTYPE_VIDEO).append(" THEN ");
            StringBuilder fnSort = makeTitleSort(VideoContract.VideoEntry.COLUMN_FILENAME, '/');
            orderby.append(fnSort);
            orderby.append(" ELSE NULL END, ");
            orderby.append(VideoContract.VideoEntry.COLUMN_RECGROUP).append(", ");
        }
        // for Recording Group page, limit selection to those recordings.
        if (mType == TYPE_RECGROUP) {
            // Only the "All" recgroup basename ends with \t
            if (!mBaseName.endsWith("\t")) {
                selection.append(VideoContract.VideoEntry.COLUMN_RECGROUP).append(" = ? ");
                selectionArgs = new String[1];
                selectionArgs[0] = mBaseName;
                if (mBaseName.equals("LiveTV")) {
                    orderby.append("CAST (").append(VideoContract.VideoEntry.COLUMN_CHANNUM).append(" as real), ");
                    orderby.append(VideoContract.VideoEntry.COLUMN_CHANNUM).append(", ");
                }
            }
        }
        // for Video Directory page, limit selection to videos
        if (mType == TYPE_VIDEODIR) {
            selection.append(VideoContract.VideoEntry.COLUMN_RECTYPE).append(" = ");
            selection.append(VideoContract.VideoEntry.RECTYPE_VIDEO);
        }

        StringBuilder titleSort = makeTitleSort(VideoContract.VideoEntry.COLUMN_TITLE, '^');
        orderby.append(titleSort).append(", ");
        if ("airdate".equals(seq)) {
            orderby.append(VideoContract.VideoEntry.COLUMN_AIRDATE).append(" ")
                    .append(ascdesc).append(", ");
            orderby.append(VideoContract.VideoEntry.COLUMN_STARTTIME).append(" ")
                    .append(ascdesc);
        }
        else {
            orderby.append(VideoContract.VideoEntry.COLUMN_STARTTIME).append(" ")
                    .append(ascdesc).append(", ");
            orderby.append(VideoContract.VideoEntry.COLUMN_AIRDATE).append(" ")
                    .append(ascdesc);
        }

        // Add recordedid to sort for in case of duplicates or split recordings
        orderby.append(", ").append(VideoContract.VideoEntry.COLUMN_RECORDEDID).append(" ")
                .append(ascdesc);

        Loader ret = new CursorLoader(
                getContext(),
                VideoContract.VideoEntry.CONTENT_URI, // Table to query
                null, // Projection to return - null means return all fields
                selection.toString(), // Selection clause
                selectionArgs,  // Select based on the category id.
                orderby.toString());
        // Map video results from the database to Video objects.
        videoCursorAdapter =
                new CursorObjectAdapter(new CardPresenter());
        videoCursorAdapter.setMapper(new VideoCursorMapper());
        return ret;
    }

    static final String[] articles = MyApplication.getAppContext().getResources().getStringArray(R.array.title_sort_articles);
    /**
     * Create the Sql to sort with excluding articles "the" "a" etc at the front
     * or at the front of directory names
     * @param columnName Column for sorting on
     * @param delim Delimiter to use - ^ for title and / for directory
     * @return StringBuilder with resulting phrase for "order by"
     */
    public static StringBuilder makeTitleSort(String columnName, char delim) {
        // Sort uppercase title
        StringBuilder titleSort = new StringBuilder();
        titleSort.append("'").append(delim).append("'||UPPER(")
                .append(columnName).append(")");
        for (String article : articles) {
            if (article != null && article.length() > 0) {
                titleSort.insert(0, "REPLACE(");
                titleSort.append(",'").append(delim).append(article)
                        .append(" ','").append(delim).append("')");
            }
        }
        return titleSort;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // the mLoadStarted check is needed because for some reason onLoadFinished
        // gets called every time the screen goes into the BG and this causes
        // the current selection and focus to be lost.
        if (videoCursorAdapter != null && data != null) {
            // Fill in usage
            new AsyncBackendCall(null, 0L, false,
                    MainFragment.this).execute(Video.ACTION_BACKEND_INFO);

            String seq = Settings.getString("pref_seq");
            String ascdesc = Settings.getString("pref_seq_ascdesc");
            boolean showRecents = "true".equals(Settings.getString("pref_show_recents"));
            boolean recentsTrim = "true".equals(Settings.getString("pref_recents_trim"));
            boolean showRecentDeleted = "true".equals(Settings.getString("pref_recents_deleted"));
            boolean showRecentWatched = "true".equals(Settings.getString("pref_recents_watched"));
            recentsTrim = recentsTrim && (showRecentDeleted || showRecentWatched);

            int allType = TYPE_RECGROUP_ALL;
            String allTitle = null;
            if (mType == TYPE_TOPLEVEL) {
                allTitle = getString(R.string.all_title) + "\t";
                allType = TYPE_TOP_ALL;
            }
            if (mType == TYPE_RECGROUP) {
                if (!mBaseName.endsWith("\t"))
                    allTitle = mBaseName + "\t";
                allType = TYPE_RECGROUP_ALL;
            }

            final int loaderId = loader.getId();
            if (loaderId == CATEGORY_LOADER) {
                int rectypeIndex =
                        data.getColumnIndex(VideoContract.VideoEntry.COLUMN_RECTYPE);
                int recgroupIndex =
                        data.getColumnIndex(VideoContract.VideoEntry.COLUMN_RECGROUP);
                int titleIndex =
                        data.getColumnIndex(VideoContract.VideoEntry.COLUMN_TITLE);
                int airdateIndex =
                        data.getColumnIndex(VideoContract.VideoEntry.COLUMN_AIRDATE);
                int starttimeIndex =
                        data.getColumnIndex(VideoContract.VideoEntry.COLUMN_STARTTIME);
                int filenameIndex =
                        data.getColumnIndex(VideoContract.VideoEntry.COLUMN_FILENAME);
                SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                SimpleDateFormat dbTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                int sortkey;
                SimpleDateFormat sortKeyFormat;
                if ("airdate".equals(seq)) {
                    sortkey = airdateIndex;
                    sortKeyFormat = dbDateFormat;
                }
                else {
                    sortkey = starttimeIndex;
                    sortKeyFormat = dbTimeFormat;
                }
                boolean cursorHasData = data.moveToFirst();
                int selectedRowNum = -1;
                int selectedItemNum = -1;

                // Every time we have to re-get the category loader, we must re-create the sidebar.
                mCategoryRowAdapter.clear();
                ArrayObjectAdapter rowObjectAdapter = null;
                SparseArrayObjectAdapter allObjectAdapter = null;
                SparseArrayObjectAdapter recentsObjectAdapter = null;
                ArrayObjectAdapter rootObjectAdapter = null;
                videoCursorAdapter.changeCursor(data);

                String currentCategory = null;
                int currentRowType = -1;
                String currentItem = null;
                int currentRowNum = -1;
                int allRowNum = -1;
                int recentsRowNum = -1;
                int rootRowNum = -1;
                MyHeaderItem header;
                ListRow row;

                // Create the recents row, only for top level
                if (mType == TYPE_TOPLEVEL && showRecents) {
                    String title = getString(R.string.recents_title) + "\t";
                    header = new MyHeaderItem(title, TYPE_RECENTS, mBaseName);
                    recentsObjectAdapter = new SparseArrayObjectAdapter(new CardPresenter());
                    row = new ListRow(header, recentsObjectAdapter);
                    row.setContentDescription(title);
                    mCategoryRowAdapter.add(row);
                    recentsRowNum = mCategoryRowAdapter.size() - 1;
                    if (mSelectedRowType == TYPE_RECENTS
                            && Objects.equals(title,mSelectedRowName))
                        selectedRowNum = recentsRowNum;
                }

                // Create "All" row (but not for videos)
                if (mType != TYPE_VIDEODIR) {
                    header = new MyHeaderItem(allTitle,
                            allType, mBaseName);
                    allObjectAdapter = new SparseArrayObjectAdapter(new CardPresenter());
                    row = new ListRow(header, allObjectAdapter);
                    row.setContentDescription(allTitle);
                    mCategoryRowAdapter.add(row);
                    allRowNum = mCategoryRowAdapter.size() - 1;
                    if (mSelectedRowType == allType
                            && Objects.equals(allTitle,mSelectedRowName))
                        selectedRowNum = allRowNum;
                }

                // Create "Root" row
                if (mType == TYPE_VIDEODIR) {
                    String rootTitle = "\t";
                    header = new MyHeaderItem(rootTitle,
                            TYPE_VIDEODIR,mBaseName);
                    rootObjectAdapter = new ArrayObjectAdapter(new CardPresenter());
                    row = new ListRow(header, rootObjectAdapter);
                    row.setContentDescription(rootTitle);
                    mCategoryRowAdapter.add(row);
                    rootRowNum = mCategoryRowAdapter.size() - 1;
                    if (mSelectedRowType == TYPE_VIDEODIR
                            && Objects.equals(rootTitle,mSelectedRowName))
                        selectedRowNum = rootRowNum;
                }

                // Iterate through each category entry and add it to the ArrayAdapter.
                while (cursorHasData && !data.isAfterLast()) {

                    boolean addToRow = true;
                    int itemType = -1;
                    int rowType = -1;

                    String recgroup = data.getString(recgroupIndex);
                    int rectype = data.getInt(rectypeIndex);

                    String category = null;
                    Video video = (Video) videoCursorAdapter.get(data.getPosition());
                    Video dbVideo = video;

                    // For Rec Group type, only use recordings from that recording group.
                    // categories are titles.
                    if (mType == TYPE_RECGROUP) {
                        category = data.getString(titleIndex);
                        if (recgroup != null
                            && (getString(R.string.all_title) + "\t").equals(mBaseName)) {
                            // Do not mix deleted episodes or LiveTV in the All group
                            if ("Deleted".equals(recgroup) || "LiveTV".equals(recgroup)) {
                                data.moveToNext();
                                continue;
                            }
                        } else {
                            if (!Objects.equals(mBaseName,recgroup)) {
                                data.moveToNext();
                                continue;
                            }
                        }
                        if (rectype == VideoContract.VideoEntry.RECTYPE_RECORDING) {
                            rowType = TYPE_SERIES;
                            itemType = TYPE_EPISODE;
                        }
                        else if (rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL) {
                            rowType = TYPE_CHANNEL_ALL;
                            itemType = TYPE_CHANNEL;
                        }
                    }

                    // For Top Level type, only use 1 recording from each title
                    // categories are recgroups
                    String filename = data.getString(filenameIndex);
                    String [] fileparts;
                    String dirname = null;
                    String itemname = null;
                    // Split file name and see if it is a directory
                    if (rectype == VideoContract.VideoEntry.RECTYPE_VIDEO && filename != null) {
                        String shortName = filename;
                        // itemlevel 0 means there is only one row for all
                        // videos so the first part of the name is the entry
                        // in the row.
                        int itemlevel = 1;
                        if (mType == TYPE_VIDEODIR) {
                            // itemlevel 1 means there is one row for each
                            // directory so the second part of the name is the entry
                            // in the row.
                            itemlevel = 2;
                            if (mBaseName.length() == 0)
                                shortName = filename;
                            else if (shortName.startsWith(mBaseName + "/"))
                                shortName = filename.substring(mBaseName.length()+1);
                            else {
                                data.moveToNext();
                                continue;
                            }
                        }
                        fileparts = shortName.split("/");
                        if (fileparts.length == 1 || mType == TYPE_TOPLEVEL) {
                            itemname = fileparts[0];
                        }
                        else {
                            dirname = fileparts[0];
                            itemname = fileparts[1];
                        }
                        if ((fileparts.length <= 2 && mType == TYPE_VIDEODIR)
                                || fileparts.length == 1)
                            itemType = TYPE_VIDEO;
                        else
                            itemType = TYPE_VIDEODIR;
                        if (itemType == TYPE_VIDEODIR && Objects.equals(itemname,currentItem)) {
                            itemType = TYPE_VIDEO;
                            addToRow = false;
                        }
                        else
                            currentItem = itemname;
                    }

                    if (mType == TYPE_TOPLEVEL) {
                        if (rectype == VideoContract.VideoEntry.RECTYPE_VIDEO) {
                            category = getString(R.string.row_header_videos)+ "\t";
                            rowType = TYPE_VIDEODIR_ALL;
                        }
                        else if (rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
                                || rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL) {
                            category = recgroup;
                            String title;
                            if (rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL)
                                title = "Channels\t";
                            else
                                title = data.getString(titleIndex);
                            if (Objects.equals(title,currentItem)) {
                                addToRow = false;
                            }
                            else {
                                currentItem = title;
                                rowType = TYPE_RECGROUP;
                                itemType = TYPE_SERIES;
                            }
                        }
                    }

                    // For Video Directory type, only use videos (recgroup null)
                    // category is full directory name.
                    // Only one videos page
                    // First is "all" row, then "root" row, then dir rows
                    // mBaseName = "Videos" String
                    // Display = "Videos" String
                    if (mType == TYPE_VIDEODIR) {
                        category = dirname;
                        rowType = TYPE_VIDEODIR;
                    }

                    // Change of row
                    if (addToRow && category != null && !Objects.equals(category,currentCategory)) {
                        // Finish off prior row
                        if (rowObjectAdapter != null) {
                            // Create header for this category.
                            header = new MyHeaderItem(currentCategory,
                                    currentRowType,mBaseName);
                            row = new ListRow(header, rowObjectAdapter);
                            row.setContentDescription(currentCategory);
                            mCategoryRowAdapter.add(row);
                        }
                        currentRowNum = mCategoryRowAdapter.size();
                        currentRowType = rowType;
                        rowObjectAdapter = new ArrayObjectAdapter(new CardPresenter());
                        currentCategory = category;
                        if (mSelectedRowType == rowType
                                && Objects.equals(currentCategory,mSelectedRowName))
                            selectedRowNum = currentRowNum;
                    }

                    // If a directory, create a placeholder for directory name
                    if (itemType == TYPE_VIDEODIR)
                        video = new Video.VideoBuilder()
                                .id(-1).title(itemname)
                                .recordedid(itemname)
                                .subtitle("")
                                .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                                .progflags("0")
                                .build();
                    video.type = itemType;

                    // Add video to row
                    if (addToRow && category != null) {
                        Video tVideo = video;
                        if (mType == TYPE_TOPLEVEL && video.rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL) {
                            // Create dummy video for "All Channels"
                            tVideo = new Video.VideoBuilder()
                                    .id(-1).channel(getString(R.string.row_header_channels))
                                    .rectype(VideoContract.VideoEntry.RECTYPE_CHANNEL)
                                    .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                                    .progflags("0")
                                    .build();
                            tVideo.type = TYPE_CHANNEL_ALL;
                        }
                        rowObjectAdapter.add(tVideo);
                        if (selectedRowNum == currentRowNum) {
                            if (video.getItemType() == mSelectedItemType
                                    && Objects.equals(mSelectedItemId,video.recordedid))
                                selectedItemNum = rowObjectAdapter.size() - 1;
                        }
                    }

                    // Add video to "Root" row
                    if (addToRow && rootObjectAdapter != null
                        && category == null) {
                        rootObjectAdapter.add(video);
                        if (selectedRowNum == rootRowNum) {
                            if (video.getItemType() == mSelectedItemType
                                    && Objects.equals(video.recordedid,mSelectedItemId))
                                selectedItemNum = rootObjectAdapter.size() - 1;
                        }
                    }

                    // Add video to "All" row
                    if (addToRow && allObjectAdapter != null && rowType != TYPE_VIDEODIR_ALL
                        && rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
                        && !(mType == TYPE_TOPLEVEL && "Deleted".equals(recgroup))) {
                        int position = 0;
                        String sortKeyStr = data.getString(sortkey);
                        if (sortKeyStr != null) {
                            try {
                                Date date = sortKeyFormat.parse(sortKeyStr);
                                // 525960 minutes in a year
                                // Get position as number of minutes since 1970
                                position = (int) (date.getTime() / 60000L);
                                // Add 70 years in case it is before 1970
                                position += 36817200;
                                if ("desc".equals(ascdesc))
                                    position = Integer.MAX_VALUE - position;
                            } catch (ParseException | NullPointerException e) {
                                e.printStackTrace();
                                position = 0;
                            }
                        }
                        // Make sure we have an empty slot
                        try {
                            while (allObjectAdapter.lookup(position) != null)
                                position++;
                        } catch (ArrayIndexOutOfBoundsException e) { }

                        allObjectAdapter.set(position,video);

                        if (selectedRowNum == allRowNum) {
                            if (video.getItemType() == mSelectedItemType
                                    && Objects.equals(video.recordedid,mSelectedItemId))
                                selectedItemNum = position;
                        }
                    }

                    // Add to recents row if applicable
                    if (recentsObjectAdapter != null
                            && dbVideo.isRecentViewed()) {
                        // 525960 minutes in a year
                        // Get key as number of minutes since 1970
                        // Will stop working in the year 5982
                        int key = (int) (dbVideo.lastUsed / 60000L);
                        // Add 70 years in case it is before 1970
                        key += 36817200;
                        // descending
                        key = Integer.MAX_VALUE - key;
                        // Make sure we have an empty slot
                        try {
                            while (recentsObjectAdapter.lookup(key) != null)
                                key++;
                        } catch (ArrayIndexOutOfBoundsException e) { }

                        if (selectedRowNum == recentsRowNum) {
                            if (dbVideo.getItemType() == mSelectedItemType
                                    && Objects.equals(dbVideo.recordedid, mSelectedItemId))
                                selectedItemNum = key;
                        }

                        // Check if there is already an entry for that series / directory
                        // If the user does not want duplicates of recent titles that were
                        // watched or deleted

                        boolean isDeleted = "Deleted".equals(dbVideo.recGroup);
                        boolean isWatched = dbVideo.isWatched();
                        if (recentsTrim) {

                            // If all recently viewed episodes of a series are watched/deleted, show the most
                            // recently viewed.
                            // If some recently viewed episodes of a series are watched/deleted and some are not,
                            // show only the ones not watched/deleted

                            String series = dbVideo.getSeries();
                            if (series != null) {
                                for (int fx = 0; fx < recentsObjectAdapter.size(); fx++) {
                                    Video fvid = (Video) recentsObjectAdapter.get(fx);
                                    boolean fisDeleted = "Deleted".equals(fvid.recGroup);
                                    if (series.equals(fvid.getSeries())
                                            && (isDeleted || fisDeleted || Objects.equals(dbVideo.recGroup,fvid.recGroup))) {
                                        int fkey = Integer.MAX_VALUE - ((int) (fvid.lastUsed / 60000L) + 36817200);
                                        boolean fisWatched = fvid.isWatched();
                                        if ((isDeleted || isWatched) && (fisDeleted || fisWatched)) {
                                            // If the episode we are processing is watched/deleted and the matched
                                            // episode in the list is also, keep the most recent
                                            if (key < fkey) {
                                                if (selectedRowNum == recentsRowNum && selectedItemNum == fkey)
                                                    selectedItemNum = key;
                                                // position is closer to front, delete the other one
                                                recentsObjectAdapter.clear(fkey);
                                                break;
                                            } else {
                                                if (selectedRowNum == recentsRowNum && selectedItemNum == key)
                                                    selectedItemNum = fkey;
                                                // position is later in list - drop this one
                                                key = -1;
                                                break;
                                            }
                                        } else if (isDeleted || isWatched) {
                                            // If the episode we are processing is watched/deleted and the matched
                                            // episode in the list is not, keep the non-watched
                                            if (selectedRowNum == recentsRowNum && selectedItemNum == key)
                                                selectedItemNum = fkey;
                                            key = -1;
                                            break;
                                        } else if (fisDeleted || fisWatched) {
                                            // If the episode we are processing is not watched/deleted and the matched
                                            // episode in the list is, keep the non-watched
                                            if (selectedRowNum == recentsRowNum && selectedItemNum == fkey)
                                                selectedItemNum = key;
                                            recentsObjectAdapter.clear(fkey);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        if (key != -1)
                            recentsObjectAdapter.set(key, dbVideo);
                    }

                    data.moveToNext();
                }
                // Finish off prior row
                if (rowObjectAdapter != null) {
                    // Create header for this category.
                    header = new MyHeaderItem(currentCategory,
                            currentRowType,mBaseName);
                    row = new ListRow(header, rowObjectAdapter);
                    mCategoryRowAdapter.add(row);
                }

                // Remove recents if empty
                if (recentsObjectAdapter != null && recentsObjectAdapter.size() == 0) {
                    mCategoryRowAdapter.removeItems(recentsRowNum,1);
                    if (selectedRowNum > 0)
                        selectedRowNum --;
                }

                // Create a row for tools.
                MyHeaderItem gridHeader = new MyHeaderItem(getString(R.string.row_header_tools),
                        TYPE_TOOLS,mBaseName);
                CardPresenter presenter = new CardPresenter();
                ArrayObjectAdapter toolsRowAdapter = new ArrayObjectAdapter(presenter);
                row = new ListRow(gridHeader, toolsRowAdapter);
                mCategoryRowAdapter.add(row);

                Video video = new Video.VideoBuilder()
                        .id(-1).title(getString(R.string.button_settings))
                        .subtitle("")
                        .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                        .progflags("0")
                        .build();
                video.type = TYPE_SETTINGS;
                toolsRowAdapter.add(video);

                video = new Video.VideoBuilder()
                        .id(-1).title(getString(R.string.button_refresh_lists))
                        .subtitle("")
                        .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                        .progflags("0")
                        .build();
                video.type = TYPE_REFRESH;
                toolsRowAdapter.add(video);

                video = new Video.VideoBuilder()
                        .id(-1).title(getString(R.string.button_backend_status))
                        .subtitle("")
                        .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                        .progflags("0")
                        .build();
                video.type = TYPE_INFO;
                toolsRowAdapter.add(video);

                video = new Video.VideoBuilder()
                        .id(-1).title(getString(R.string.button_manage_recordings))
                        .subtitle("")
                        .bgImageUrl("android.resource://org.mythtv.leanfront/" + R.drawable.background)
                        .progflags("0")
                        .build();
                video.type = TYPE_MANAGE;
                toolsRowAdapter.add(video);

                if (selectedRowNum == allRowNum) {
                    if (allObjectAdapter == null)
                        selectedItemNum = -1;
                    else
                        selectedItemNum = allObjectAdapter.indexOf(selectedItemNum);
                }

                if (selectedRowNum == recentsRowNum) {
                    if (recentsObjectAdapter == null)
                        selectedItemNum = -1;
                    else
                        selectedItemNum = recentsObjectAdapter.indexOf(selectedItemNum);
                }

                if (selectedRowNum != -1) {
                    SelectionSetter setter = new SelectionSetter(selectedRowNum, selectedItemNum);

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(setter, 100);
                }
            }
            setProgressBar(false);
        }
    }


    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        int loaderId = loader.getId();
        if (loaderId == CATEGORY_LOADER)
            mCategoryRowAdapter.clear();
    }

    public int getType() {
        return mType;
    }

    public static ScheduledExecutorService getExecutor() {
        return executor;
    }

    private class UpdateBackgroundTask implements Runnable {
        @Override
        public void run() {
            if (mBackgroundURI != null) {
                updateBackground(mBackgroundURI.toString());
            }
        }
    }
    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {

            ListItem li = (ListItem) item;
            int liType = li.getItemType();
            Activity context = getActivity();
            Bundle bundle;
            MyHeaderItem headerItem = (MyHeaderItem) row.getHeaderItem();
            if (headerItem.getItemType() == TYPE_RECENTS)
                liType = TYPE_EPISODE;
            switch (liType) {
                case TYPE_EPISODE:
                case TYPE_VIDEO:
                case TYPE_CHANNEL:
                    Video video = (Video) item;
                    Intent intent = new Intent(context, VideoDetailsActivity.class);
                    intent.putExtra(VideoDetailsActivity.VIDEO, video);

                    bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            context,
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    context.startActivity(intent, bundle);
                    break;
                case TYPE_SERIES:
                case TYPE_CHANNEL_ALL:
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(KEY_TYPE,MainFragment.TYPE_RECGROUP);
                    intent.putExtra(KEY_BASENAME,headerItem.getName());
                    intent.putExtra(KEY_ROWNAME,((Video)li).title);
                    bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(context)
                                    .toBundle();
                    context.startActivity(intent, bundle);
                    break;
                case TYPE_VIDEODIR:
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(KEY_TYPE,MainFragment.TYPE_VIDEODIR);
                    String baseName = mBaseName;
                    if (mType == TYPE_TOPLEVEL)
                        baseName = "";
                    else {
                        if (baseName != null && baseName.length() > 0)
                            baseName = baseName + "/" + headerItem.getName();
                        else
                            baseName = headerItem.getName();
                    }
                    intent.putExtra(KEY_BASENAME,baseName);
                    intent.putExtra(KEY_ROWNAME,((Video)li).title);
                    bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(context)
                                    .toBundle();
                    context.startActivity(intent, bundle);
                    break;
                case TYPE_SETTINGS:
                    intent = new Intent(context, SettingsActivity.class);
                    startActivity(intent);
                    break;
                case TYPE_REFRESH:
                    mSelectedRowType = -1;
                    mSelectedRowName = null;
                    setProgressBar(true);
                    int recType = -1;
                    String recGroup = null;
                    if (mType == TYPE_RECGROUP) {
                        recType = VideoContract.VideoEntry.RECTYPE_RECORDING;
                        if (!mBaseName.endsWith("\t"))
                            recGroup = mBaseName;
                    }
                    if (mType == TYPE_VIDEODIR)
                        recType = VideoContract.VideoEntry.RECTYPE_VIDEO;
                    startFetch(recType, null, recGroup);
                    break;
                case TYPE_INFO:
                    new AsyncBackendCall(null, 0L, false,
                            MainFragment.this).execute(Video.ACTION_BACKEND_INFO_HTML);
                    break;
                case TYPE_MANAGE:
                    intent = new Intent(context, ManageRecordingsActivity.class);
                    startActivity(intent);
                    break;
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video && ((Video) item).bgImageUrl != null)
                mBackgroundURI = Uri.parse(((Video) item).bgImageUrl);
            else
                mBackgroundURI = mDefaultBackgroundURI;

            startBackgroundTimer();
            scrollSupport.onItemSelected(itemViewHolder,rowViewHolder, getRowsSupportFragment());
        }
    }

    private final class HeaderClickedListener implements HeadersSupportFragment.OnHeaderClickedListener {
        @Override
        public void onHeaderClicked(RowHeaderPresenter.ViewHolder viewHolder, Row row) {
            Context context = getActivity();
            MyHeaderItem headerItem = (MyHeaderItem) row.getHeaderItem();

            Intent intent;
            int type = headerItem.getItemType();
            switch (type) {
                case MainFragment.TYPE_RECGROUP:
                case MainFragment.TYPE_TOP_ALL:
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainFragment.KEY_TYPE,MainFragment.TYPE_RECGROUP);
                    intent.putExtra(MainFragment.KEY_BASENAME,headerItem.getName());
                    break;
                case MainFragment.TYPE_VIDEODIR_ALL:
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainFragment.KEY_TYPE,MainFragment.TYPE_VIDEODIR);
                    intent.putExtra(MainFragment.KEY_BASENAME,"");
                    break;
                case MainFragment.TYPE_VIDEODIR:
                    String name = headerItem.getName();
                    // All and Root entries
                    if (name.endsWith("\t")) {
                        int rownum = mCategoryRowAdapter.indexOf(row);
                        if (rownum == -1)
                            return;
                        if (rownum == getSelectedPosition())
                            startHeadersTransition(false);
                        else
                            setSelectedPosition(rownum);
                        return;
                    }
                    intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainFragment.KEY_TYPE,MainFragment.TYPE_VIDEODIR);
                    String baseName = headerItem.getBaseName();
                    if (baseName != null && baseName.length() > 0)
                        baseName = baseName + "/" + name;
                    else
                        baseName = name;
                    intent.putExtra(MainFragment.KEY_BASENAME,baseName);
                    break;
                default:
                    int rownum = mCategoryRowAdapter.indexOf(row);
                    if (rownum == -1)
                        return;
                    if (rownum == getSelectedPosition())
                        startHeadersTransition(false);
                    else
                        setSelectedPosition(rownum);
                    return;
            }
            Bundle bundle =
                    ActivityOptionsCompat.makeSceneTransitionAnimation((Activity)context)
                            .toBundle();
            context.startActivity(intent, bundle);

        }
    }

    public boolean onHeaderMenu(MyHeaderItem headerItem) {
        int type = headerItem.getItemType();
        ArrayList<String> prompts = new ArrayList<>();
        ArrayList<Action> actions = new ArrayList<>();
        switch (type) {
            case MainFragment.TYPE_SERIES:
            case MainFragment.TYPE_VIDEODIR:
                Row row = null;
                ObjectAdapter rowsAdapter = getAdapter();
                int size = rowsAdapter.size();
                for (int ix = 0 ; ix < size ; ix++) {
                    row = (Row)rowsAdapter.get(ix);
                    if (row.getHeaderItem() == headerItem)
                        break;
                }
                Row selectedRow = row;
                if (((ListRow)row).getAdapter().size() == 0)
                    break;
                String alertTitle;
                if (type == MainFragment.TYPE_SERIES) {
                    alertTitle = getContext().getString(R.string.title_menu_series,
                            headerItem.getName(),headerItem.getBaseName());
                    if ("Deleted".equals(headerItem.getBaseName())) {
                        prompts.add(getString(R.string.menu_undelete));
                        actions.add(new Action(Video.ACTION_UNDELETE));
                    }
                    else {
                        prompts.add(getString(R.string.menu_delete));
                        actions.add(new Action(Video.ACTION_DELETE));
                        prompts.add(getString(R.string.menu_delete_rerecord));
                        actions.add(new Action(Video.ACTION_DELETE_AND_RERECORD));
                    }
                    prompts.add(getString(R.string.menu_rerecord));
                    actions.add(new Action(Video.ACTION_ALLOW_RERECORD));
                }
                else {
                    String baseName = headerItem.getBaseName();
                    if (baseName.length() > 0)
                        baseName = baseName + "/";
                    alertTitle = getContext().getString(R.string.title_menu_videodir,
                            baseName + headerItem.getName());
                }
                prompts.add(getString(R.string.menu_mark_unwatched));
                actions.add(new Action(Video.ACTION_SET_UNWATCHED));
                prompts.add(getString(R.string.menu_mark_watched));
                actions.add(new Action(Video.ACTION_SET_WATCHED));
                prompts.add(getString(R.string.menu_remove_bookmark));
                actions.add(new Action(Video.ACTION_REMOVE_BOOKMARK));
                prompts.add(getString(R.string.menu_remove_from_recent));
                actions.add(new Action(Video.ACTION_REMOVE_RECENT));

                if (prompts != null && actions != null) {
                    final ArrayList<Action> finalActions = actions; // needed because used in inner class
                    // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                            R.style.Theme_AppCompat_Dialog_Alert);
                    builder
                            .setTitle(alertTitle)
                            .setItems(prompts.toArray(new String[0]),
                                    new DialogInterface.OnClickListener() {
                                        ArrayList<Action> mActions = finalActions;
                                        MainFragment mParent = MainFragment.this;

                                        public void onClick(DialogInterface dialog, int which) {
                                            // The 'which' argument contains the index position
                                            // of the selected item
                                            if (which < mActions.size()) {
                                                mParent.onMenuClicked(mActions.get(which), selectedRow);
                                            }
                                        }
                                    });
                    builder.show();
                }

                return true;
        }
        return true; // Do not treat long press as a short press
    }

    public void onMenuClicked(Action action, Row row) {
        saveSelected();
        loader.stopLoading();
        ListRow listRow = (ListRow) row;
        ObjectAdapter rowAdapter = listRow.getAdapter();
        AsyncBackendCall call = new AsyncBackendCall(
                new AsyncBackendCall.OnBackendCallListener() {
                    @Override
                    public void onPostExecute(AsyncBackendCall taskRunner) {
                        ArrayList<XmlNode> results = taskRunner.getXmlResults();
                        int nSuccess = 0;
                        int nFail = 0;
                        XmlNode xmlResult;
                        // only look at every alternate result, others are
                        // refresh or dummy
                        for (int ix = 1; ix < results.size(); ix+=2) {
                            xmlResult = results.get(ix);
                            String result = null;
                            if (xmlResult != null)
                                result = xmlResult.getString();
                            if ("true".equals(result))
                                nSuccess++;
                            else
                                nFail++;
                        }
                        if (nFail > 0) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                                    R.style.Theme_AppCompat_Dialog_Alert);
                            builder.setTitle(R.string.title_alert_rowresults);
                            String msg = getContext().getString(R.string.alert_rowresults, nSuccess, nFail);
                            builder.setMessage(msg);
                            builder.show();
                        }
                        loader.startLoading();
                    }
        });
        call.setBookmark(0);
        call.setPosBookmark(0);
        call.setRowAdapter(rowAdapter);
        Integer [] tasks;
        int task = (int)action.getId();

        switch (task) {
            case Video.ACTION_DELETE:
            case Video.ACTION_DELETE_AND_RERECORD:
                tasks = new Integer [] {Video.ACTION_REFRESH, task};
                break;
            case Video.ACTION_SET_UNWATCHED:
            case Video.ACTION_SET_WATCHED:
                call.setWatched(task == Video.ACTION_SET_WATCHED);
                // Set the task since both watched and unwatched are done with
                // ACTION_SET_WATCHED in AsyncBackend
                task = Video.ACTION_SET_WATCHED;
                // Fall Through to default
            default:
                tasks = new Integer [] {Video.ACTION_DUMMY, task};
                break;
        }
        call.execute(tasks);
        setProgressBar(true);
    }

    private static class MythTask implements Runnable {
        boolean mVersionMessageShown = false;

        @Override
        public void run() {
            try {
                scheduledTaskRunning = true;
                boolean connection = false;
                boolean connectionfail = false;
                if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                        == Lifecycle.State.CREATED) {
                    // process is now in the background
                    mWasInBackground = true;
                    if (executor != null)
                        executor.shutdown();
                    executor = null;
                    return;
                }
                String backendIP = Settings.getString("pref_backend");
                if (backendIP == null || backendIP.length() == 0)
                    return;
                while (!connection) {
                    if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                            == Lifecycle.State.CREATED) {
                        // process is now in the background
                        mWasInBackground = true;
                        if (executor != null)
                            executor.shutdown();
                        executor = null;
                        return;
                    }
                    int toastMsg = 0;
                    int toastLeng = 0;
                    try {
                        String result = null;
                        String url = XmlNode.mythApiUrl(null,
                                "/Myth/DelayShutdown");
                        if (url == null)
                            return;
                        XmlNode bkmrkData = XmlNode.fetch(url, "POST");
                        result = bkmrkData.getString();
                        connection = true;
                    } catch (FileNotFoundException e) {
                        if (!mVersionMessageShown) {
                            toastMsg = R.string.msg_no_delayshutdown;
                            toastLeng = Toast.LENGTH_LONG;
                            mVersionMessageShown = true;
                            connection = true;
                        }
                    } catch (IOException e) {
                        toastMsg = R.string.msg_no_connection;
                        toastLeng = Toast.LENGTH_LONG;
                        connectionfail = true;
                        mFetchTime = 0; // Force a fetch when it comes back
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    if (connectionfail)
                        if (wakeBackend())
                            toastMsg = R.string.msg_wake_backend;

                    if (toastMsg != 0) {
                        Activity activity = MainActivity.getContext();
                        if (activity == null)
                            return;
                        ToastShower toastShower = new ToastShower(activity, toastMsg, toastLeng);
                        activity.runOnUiThread(toastShower);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                if (mFetchTime < System.currentTimeMillis() - 60 * 60 * 1000) {
                    Activity activity = MainActivity.getContext();
                    if (activity == null)
                        return;
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            if (mActiveFragment != null)
                                mActiveFragment.saveSelected();
                            MainActivity.getContext().getMainFragment().startFetch(-1, null, null);
                        }
                    });
                }
            } finally {
                scheduledTaskRunning = false;
            }
        }

        public boolean wakeBackend() {
            MainActivity main = MainActivity.getContext();
            if (main == null)
                return false;
            String backendMac = Settings.getString("pref_backend_mac");
            if (backendMac.length() == 0)
                return false;

            // The magic packet is a broadcast frame containing anywhere within its payload
            // 6 bytes of all 255 (FF FF FF FF FF FF in hexadecimal), followed by sixteen
            // repetitions of the target computer's 48-bit MAC address, for a total of 102 bytes.

            byte [] msg = new byte[102];
            int ix;
            for (ix=0; ix < 6; ix++)
                msg[ix] = (byte)0xff;

            int  msglen = 6;
            String[] tokens = backendMac.split(":");
            byte[] macaddr = new byte[6];

            if (tokens.length != 6) {
                Log.e(TAG, CLASS + " wakeBackend WakeOnLan("+backendMac+"): Incorrect MAC length");
                return false;
            }

            for (int y = 0; y < 6; y++)
            {
                try {
                    macaddr[y] = (byte) Integer.parseInt(tokens[y], 16);
                } catch (NumberFormatException e) {
                    Log.e(TAG, CLASS +" wakeBackend WakeOnLan("+backendMac+"): Invalid MAC address");
                    return false;
                }

            }

            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 6; y++)
                    msg[msglen++] = macaddr[y];

            Log.i(TAG, CLASS + " wakeBackend WakeOnLan(): Sending WOL packet to "+backendMac);

            try {
                DatagramPacket DpSend = new DatagramPacket(msg, msg.length, InetAddress.getByName("255.255.255.255"), 9);
                DatagramSocket ds = new DatagramSocket();
                ds.send(DpSend);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    }


    private static class ToastShower implements Runnable {

        private Activity activity;
        private int toastMsg;
        private int toastLeng;
        private static Toast mToast;

        public ToastShower(Activity activity, int toastMsg, int toastLeng) {
            this.activity = activity;
            this.toastMsg = toastMsg;
            this.toastLeng = toastLeng;
        }
        public void run() {
            // show toast here
            if (mToast != null)
                mToast.cancel();
            mToast = Toast.makeText(activity,
                    activity.getString(toastMsg), toastLeng);
            mToast.show();
        }
    }

    private class SelectionSetter implements Runnable {

        private int selectedRowNum;
        private int selectedItemNum;

        public SelectionSetter(int selectedRowNum, int selectedItemNum) {
            this.selectedRowNum = selectedRowNum;
            this.selectedItemNum = selectedItemNum;
        }
        public void run() {
            RowsSupportFragment frag = getRowsSupportFragment();
            if (frag != null) {
                frag.setSelectedPosition(selectedRowNum, false,
                        new ListRowPresenter.SelectItemViewHolderTask(selectedItemNum));
                if (selectedItemNum == -1)
                    getHeadersSupportFragment().getView().requestFocus();
            }
        }
    }

}
