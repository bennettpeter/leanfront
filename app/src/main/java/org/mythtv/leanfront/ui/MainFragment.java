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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.util.Pair;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.app.ProgressBarManager;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
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
import androidx.leanback.widget.TitleViewAdapter;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.core.content.ContextCompat;

import android.os.Looper;
import android.text.Html;
import android.text.InputType;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowMetrics;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.mythtv.leanfront.MyApplication;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.BackendCache;
import org.mythtv.leanfront.data.FetchVideos;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.VideoDbHelper;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.ListItem;
import org.mythtv.leanfront.model.MyHeaderItem;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.presenter.CardPresenter;
import org.mythtv.leanfront.presenter.IconHeaderItemPresenter;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class MainFragment extends BrowseSupportFragment
        implements AsyncBackendCall.OnBackendCallListener {

    private static final String TAG = "lfe";
    private static final String CLASS = "MainFragment";

    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ArrayObjectAdapter mCategoryRowAdapter;
    private Drawable mDefaultBgDrawable;
    private int width;
    private int height;
    private Runnable mBackgroundTask;
    private String mBackgroundUrl;
    private BackgroundManager mBackgroundManager;
    int mType;
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
    public static final int TYPE_GUIDE = 25;

    int filterType;
    public static final int FILTER_RECGRP = 1;
    public static final int FILTER_CATEGORY = 2;
    public static final int FILTER_NONE = 3;
    public static final String KEY_BASENAME = "LEANFRONT_BASENAME";
    public static final String KEY_ROWNAME = "LEANFRONT_ROWNAME";
    // mBase is the current recgroup or directory being displayed.
    String mBaseName;
    String mRowName;
    private TextView mUsageView;
    private int[] mSavedSelection = null;

    private static ScheduledExecutorService executor = null;
    private static final MythTask mythTask = new MythTask();
    private static volatile boolean scheduledTaskRunning;
    public static volatile long mFetchTime = 0;
    // Keep track of the fragment currently showing, if any.
    private static MainFragment mActiveFragment = null;
    private static boolean mWasInBackground = true;
    private static final int TASK_INTERVAL = 240;
//    private ItemViewClickedListener mItemViewClickedListener;
    private ScrollSupport scrollSupport;
    volatile boolean isLoaderRunning;
    private ArrayList<String> mRecGroupList;
    private String mNewValueText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initFilterType();
        if (savedInstanceState == null)
            mSavedSelection = null;
        else
            mSavedSelection = savedInstanceState.getIntArray("selection");
        scrollSupport = new ScrollSupport((requireContext()));
        Intent intent = requireActivity().getIntent();
        mType = intent.getIntExtra(KEY_TYPE, TYPE_TOPLEVEL);
        if (mType == TYPE_TOPLEVEL) {
            // Clear ip address cache
            BackendCache.flush();
            VideoDbHelper dbh = VideoDbHelper.getInstance(getContext());
            SQLiteDatabase db = dbh.getWritableDatabase();
            if (db != null) {
                // delete stale entries from bookmark table
                String where = VideoContract.StatusEntry.COLUMN_LAST_USED + " < ? ";
                // 60 days in milliseconds
                String[] selectionArgs = {String.valueOf(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)};
                // https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html
                db.delete(VideoContract.StatusEntry.TABLE_NAME, where, selectionArgs);
                VideoDbHelper.releaseDatabase();
            }
            // Initialize startup members
            mFetchTime = 0;
            mActiveFragment = null;
            showNotes();
//            mythTask.context = getContext();
        } else {
            mBaseName = intent.getStringExtra(KEY_BASENAME);
            mRowName = intent.getStringExtra(KEY_ROWNAME);
        }
    }


    public void initFilterType() {
        String tempFType = Settings.getString("pref_filter");
        switch (tempFType) {
            case "category":
                filterType = FILTER_CATEGORY;
                break;
            case "none":
                filterType = FILTER_NONE;
                break;
            case "recgrp":
            default:
                filterType = FILTER_RECGRP;
        }
    }

    private void setProgressBar(boolean show) {
        ProgressBarManager manager = getProgressBarManager();
        // Initial delay defaults to 1000 (1 second)
        if (show)
            manager.show();
        else
            manager.hide();
    }

    @SuppressLint("RtlHardcoded")
    private void setUsage(boolean call) {
        BackendCache bCache = BackendCache.getInstance();
        // Do this call once per hour at most
        if (call && bCache.infoTime < System.currentTimeMillis() - 60*60*1000L)
            new AsyncBackendCall(getActivity(), this).execute(Video.ACTION_BACKEND_INFO);
        if (bCache.diskUsage < 0)
            return;
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
            grp.addView(mUsageView, new FrameLayout.LayoutParams(width / 5, height,
                    Gravity.TOP + Gravity.LEFT));
            TextClock clock = new TextClock(getContext());
            clock.setGravity(Gravity.BOTTOM + Gravity.RIGHT);
            grp.addView(clock, new FrameLayout.LayoutParams(width / 10, height / 5,
                    Gravity.BOTTOM + Gravity.RIGHT));
        }
        mUsageView.setText(getContext().getResources().getString(R.string.title_disk_usage,
                bCache.diskUsage));
    }

    /**
     * Fetch video list
     *
     * @param rectype    Set to -1 to fetch all, or to either
     *                   VideoContract.VideoEntry.RECTYPE_RECORDING or
     *                   VideoContract.VideoEntry.RECTYPE_VIDEO
     * @param recordedId Set to null, or recordedId if only one to be refreshed
     * @param recGroup   Set to a recordimng group if only that one is to
     *                   be refreshed
     */

    static public void startFetch(int rectype, String recordedId, String recGroup, boolean isProgressBar) {
        FetchVideos fetchVideos = new FetchVideos(MyApplication.getAppContext(), rectype, recordedId, recGroup, isProgressBar);
        fetchVideos.execute();
    }

    // Replacement for StartLoader. This needs to be called after any database update.
    // Must be called on UI Thread
    public void fetchComplete(boolean isProgressBar) {
        startAsyncLoader(isProgressBar);
    }

    public void startAsyncLoader(boolean isProgressBar) {
        if (isLoaderRunning) {
            if (isProgressBar)
                setProgressBar(false);
        }
        else {
            initFilterType();
            new AsyncMainLoader(requireActivity(), isProgressBar).execute(this);
            isLoaderRunning = true;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        // Final initialization, modifying UI elements.
        super.onViewCreated(view, savedInstanceState);

        // Prepare the manager that maintains the same background image between activities.
        prepareBackgroundManager();

        setupUIElements();
        setupEventListeners();
        prepareEntranceTransition();

        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mCategoryRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mCategoryRowAdapter);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        mBackgroundManager = null;
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mActiveFragment = this;
        setProgressBar(false);
        startBackgroundTimer();
        if (mWasInBackground || executor == null)
            restartMythTask();
        mWasInBackground = false;
        // If it's been more than an hour, refresh
        if (mFetchTime > 0 && mFetchTime < System.currentTimeMillis()
                - (long)Settings.getInt("pref_refresh_mins") * 60 * 1000 + 100)
            startFetch(-1, null, null, false);
        else
            startAsyncLoader(false);
    }

    // Notes dialog that comes up when you start for the first time.
    // To advise users of new features etc.
    // Currently, no messages are displayed but an array of strings can be provided
    // in sNotes in the parens, e.g. {R.string.notes_audio}
    @SuppressWarnings("ConstantConditions")
    void showNotes() {
        // deleted: R.string.notes_paging
        //noinspection MismatchedReadAndWriteOfArray
        final int[] sNotes = {};
        int deletedNotes = 1;
        int notesVersion = Settings.getInt("pref_notes_version");
        if (notesVersion < deletedNotes)
            notesVersion = deletedNotes;
        if (notesVersion >= sNotes.length + deletedNotes)
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.notes_title);
        StringBuilder msg = new StringBuilder();
        for (int ix = notesVersion - deletedNotes ; ix < sNotes.length ; ix++) {
            msg.append(getContext().getString(sNotes[ix]));
        }
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.notes_seen, (dialog, which) -> {
            SharedPreferences.Editor editor = Settings.getEditor();
            Settings.putString(editor,"pref_notes_version",String.valueOf(sNotes.length + deletedNotes));
            editor.commit();
            dialog.cancel();
        });
        builder.setNegativeButton(android.R.string.ok, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    public static void restartMythTask() {
        if (!scheduledTaskRunning) {
            synchronized (mythTask) {
                if (!scheduledTaskRunning) {
                    if (executor != null)
                        executor.shutdown();
                    executor = Executors.newScheduledThreadPool(1);
                    executor.scheduleWithFixedDelay(mythTask, 0, TASK_INTERVAL, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Override
    public void onPause()
    {
        scrollSupport.stop();
        super.onPause();
        mActiveFragment = null;
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        int [] selection = getSelection();
        savedInstanceState.putIntArray("selection",selection);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    /*
        Get current selection
        Returns array:
            [0]: Selected row
            [1]: Selected item
        Either or both can be -1 to indicate no selection.
     */
    // TODO: Other places duplicate this code. Call this instead
    int [] getSelection() {
        if (mSavedSelection != null) {
            int [] ret = mSavedSelection;
            mSavedSelection = null;
            return ret;
        }
        int selectedRowNum = getSelectedPosition();
        int selectedItemNum = -1;
        if (selectedRowNum >= 0) {
            if (!isShowingHeaders()) {
                ListRowPresenter.ViewHolder selectedViewHolder
                        = (ListRowPresenter.ViewHolder) getRowsSupportFragment()
                        .getRowViewHolder(selectedRowNum);
                if (selectedViewHolder != null)
                    selectedItemNum = selectedViewHolder.getSelectedPosition();
            }
        }
        return new int[]{selectedRowNum, selectedItemNum};
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        Context context = requireContext();
        if (taskRunner == null)
            return;
        int [] tasks = taskRunner.getTasks();
        switch (tasks[0]) {
            case Video.ACTION_BACKEND_INFO_HTML:
                String result = taskRunner.getStringResult();
                if (result == null)
                    break;
                // Get rid of span elements, which are pop=ups and should not be displayed here
                String fix = result.replaceAll("<span>.+</span>","");
                Spanned spanned;
                if (Build.VERSION.SDK_INT >= 24)
                    spanned = Html.fromHtml(fix,Html.FROM_HTML_MODE_COMPACT);
                else
                    spanned =  Html.fromHtml(fix);
                AlertDialog.Builder builder = new AlertDialog.Builder(context,
                        R.style.Theme_AppCompat);
                builder.setMessage(spanned);
                builder.show();
                break;
            case Video.ACTION_BACKEND_INFO:
                setUsage(false);
                break;
        }
    }


    public static MainFragment getActiveFragment() {
        return mActiveFragment;
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(requireActivity());
        mBackgroundManager.attach(requireActivity().getWindow());
        Resources resources = getResources();
        mDefaultBgDrawable = ResourcesCompat.getDrawable(resources, R.drawable.background, null);
        mBackgroundTask = new UpdateBackgroundTask();
        Activity activity = requireActivity();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            width = bounds.width();
            height = bounds.height();
        }
        else {
            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
    }

    private void setupUIElements() {
        if (mType == TYPE_TOPLEVEL)
            setBadgeDrawable(
                    ResourcesCompat.getDrawable(requireActivity().getResources(), R.drawable.mythtv_320x180_icon, null));
        setTitle(mBaseName);
        showTitle(TitleViewAdapter.FULL_VIEW_VISIBLE);
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // Set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(requireActivity(), R.color.fastlane_background));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(requireActivity(), R.color.search_opaque));

        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object o) {
                return new IconHeaderItemPresenter(MainFragment.this);
            }
        });
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(view -> {
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
        });

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        HeadersSupportFragment header = getHeadersSupportFragment();
        if (header != null)
            header.setOnHeaderClickedListener(new HeaderClickedListener());
    }

    public void pageDown(int direction) {
        RowsSupportFragment frag = getRowsSupportFragment();
        int selectedRowNum = frag.getSelectedPosition();
        if (isShowingHeaders()) {
            int newPos = selectedRowNum + 7 * direction;
            if (newPos < 0)
                newPos = 0;
            frag.setSelectedPosition(newPos, false);
        } else {
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
    }

    private void updateBackground(String uri) {
        RequestOptions options = new RequestOptions()
                .centerCrop()
                .error(mDefaultBgDrawable)
                .timeout(5000);

        RequestBuilder<Bitmap> bld =  Glide.with(this)
                .asBitmap();
        if (uri == null)
            bld = bld.load(R.drawable.background);
        else {
            String auth =  BackendCache.getInstance().authorization;
            LazyHeaders.Builder lzhb =  new LazyHeaders.Builder();
            if (auth != null && !auth.isEmpty())
                lzhb.addHeader("Authorization", auth);
            bld = bld.load(new GlideUrl(uri, lzhb.build()));
        }
        bld.apply(options)
            .into(new CustomTarget<Bitmap>(width, height) {
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

    private void startBackgroundTimer() {
        handler.removeCallbacks(mBackgroundTask);
        handler.postDelayed(mBackgroundTask, BACKGROUND_UPDATE_DELAY);
    }

    /**
     * Create the SQL to sort with excluding articles "the" "a" etc. at the front
     * or at the front of directory names
     * @param columnName Column for sorting on
     * @param delim Delimiter to use - ^ for title and / for directory
     * @return StringBuilder with resulting phrase for "order by"
     */
    public static StringBuilder makeTitleSort(String columnName, char delim) {
        final String[] articles = MyApplication.getAppContext().getResources()
                .getStringArray(R.array.title_sort_articles);
        // Sort uppercase title
        // REPLACE(REPLACE(REPLACE('^'||UPPER(title),'^THE ','^'),'^A ','^'),'^AN ','^')
        StringBuilder titleSort = new StringBuilder();
        titleSort.append("'").append(delim).append("'||UPPER(")
                .append(columnName).append(")");
        for (String article : articles) {
            // Empty entries may be a single space
            article = article.trim();
            if (!article.isEmpty()) {
                titleSort.insert(0, "REPLACE(");
                titleSort.append(",'").append(delim).append(article)
                        .append(" ','").append(delim).append("')");
            }
        }
        return titleSort;
    }

    // replacement for onLoadFinished
    // ArrayList return as follows
    // Each entry is an ArrayList describing one row
    // Each row arraylist has
    //   [0] is a MyHeaderItem
    //   [1] onwards are each a Video

    public void onAsyncLoadFinished(AsyncMainLoader loader, ArrayList<ArrayList<ListItem>> list) {
        isLoaderRunning = false;
        if (getActivity() == null)
            return;
        if (loader.isProgressBar)
            setProgressBar(false);
        if (list == null)
            list = new ArrayList<>();

        int [] selection = getSelection();
        // Fill in disk usage
        setUsage(true);
        // Every time we have to re-get the category loader, we must re-create the sidebar.
        mCategoryRowAdapter.clear();
        ListRow row;
        for (int rownum = 0 ; rownum < list.size() ; rownum++) {
            ArrayList<ListItem> rowList = list.get(rownum);
            MyHeaderItem header = (MyHeaderItem) rowList.get(0);
            if (mRowName != null && mRowName.equals(header.getName()))
                selection[0] = rownum;
            ArrayObjectAdapter rowObjectAdapter = new ArrayObjectAdapter(new CardPresenter(this));
            rowList.remove(0);
            if (!rowList.isEmpty())
                rowObjectAdapter.addAll(0, rowList);
            row = new ListRow(header, rowObjectAdapter);
            mCategoryRowAdapter.add(row);
        }
        mRowName = null;

        // Create a row for tools.
        MyHeaderItem gridHeader = new MyHeaderItem(getString(R.string.row_header_tools),
                TYPE_TOOLS,mBaseName);
        CardPresenter presenter = new CardPresenter(this);
        ArrayObjectAdapter toolsRowAdapter = new ArrayObjectAdapter(presenter);
        row = new ListRow(gridHeader, toolsRowAdapter);
        mCategoryRowAdapter.add(row);

        Video video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.button_settings))
                .subtitle("")
                .progflags("0")
                .build();
        video.type = TYPE_SETTINGS;
        toolsRowAdapter.add(video);

        video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.button_refresh_lists))
                .subtitle("")
                .progflags("0")
                .build();
        video.type = TYPE_REFRESH;
        toolsRowAdapter.add(video);

        video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.button_backend_status))
                .subtitle("")
                .progflags("0")
                .build();
        video.type = TYPE_INFO;
        toolsRowAdapter.add(video);

        video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.title_program_guide))
                .subtitle("")
                .progflags("0")
                .build();
        video.type = TYPE_GUIDE;
        toolsRowAdapter.add(video);

        video = new Video.VideoBuilder()
                .id(-1).title(getString(R.string.button_manage_recordings))
                .subtitle("")
                .progflags("0")
                .build();
        video.type = TYPE_MANAGE;
        toolsRowAdapter.add(video);

        SelectionSetter setter = new SelectionSetter(selection[0], selection[1]);
        handler.postDelayed(setter, 100);
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
            updateBackground(mBackgroundUrl);
        }
    }
    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {

            ListItem li = (ListItem) item;
            int liType = li.getItemType();
            Activity activity = requireActivity();
            Bundle bundle;
            MyHeaderItem headerItem = (MyHeaderItem) row.getHeaderItem();
            if (headerItem.getItemType() == TYPE_RECENTS)
                liType = TYPE_EPISODE;
            Intent intent;
            switch (liType) {
                case TYPE_EPISODE:
                case TYPE_VIDEO:
                case TYPE_CHANNEL:
                    Video video = (Video) item;
                    intent = new Intent(activity, VideoDetailsActivity.class);
                    intent.putExtra(PlaybackActivity.VIDEO, video);

                    bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            activity,
                            Objects.requireNonNull(((ImageCardView) itemViewHolder.view).getMainImageView()),
                            PlaybackActivity.SHARED_ELEMENT_NAME).toBundle();
                    activity.startActivity(intent, bundle);
                    break;
                case TYPE_SERIES:
                case TYPE_CHANNEL_ALL:
                    intent = new Intent(activity, MainActivity.class);
                    intent.putExtra(KEY_TYPE,MainFragment.TYPE_RECGROUP);
                    intent.putExtra(KEY_BASENAME,headerItem.getName());
                    intent.putExtra(KEY_ROWNAME,((Video)li).title);
                    bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation
                                (activity, (Pair<View, String>[]) null)
                                    .toBundle();
                    activity.startActivity(intent, bundle);
                    break;
                case TYPE_VIDEODIR:
                    intent = new Intent(activity, MainActivity.class);
                    intent.putExtra(KEY_TYPE,MainFragment.TYPE_VIDEODIR);
                    String baseName = mBaseName;
                    if (mType == TYPE_TOPLEVEL)
                        baseName = "";
                    else {
                        if (baseName != null && !baseName.isEmpty())
                            baseName = baseName + "/" + headerItem.getName();
                        else
                            baseName = headerItem.getName();
                    }
                    intent.putExtra(KEY_BASENAME,baseName);
                    intent.putExtra(KEY_ROWNAME,((Video)li).title);
                    bundle =
                            ActivityOptionsCompat.makeSceneTransitionAnimation
                                (activity, (Pair<View, String>[]) null)
                                    .toBundle();
                    activity.startActivity(intent, bundle);
                    break;
                case TYPE_SETTINGS:
                    intent = new Intent(activity, SettingsActivity.class);
                    startActivity(intent);
                    if (executor != null)
                        executor.shutdown();
                    executor = null;
                    break;
                case TYPE_REFRESH:
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
                    startFetch(recType, null, recGroup, true);
                    break;
                case TYPE_INFO:
                    if (XmlNode.isSetupNotDone()) {
                        Toast.makeText(getContext(),
                                R.string.msg_need_ipaddress,
                                Toast.LENGTH_LONG).show();
                    }
                    else
                        new AsyncBackendCall(getActivity(),
                            MainFragment.this).execute(Video.ACTION_BACKEND_INFO_HTML);
                    break;
                case TYPE_MANAGE:
                    intent = new Intent(activity, ManageRecordingsActivity.class);
                    startActivity(intent);
                    break;
                case TYPE_GUIDE:
                    intent = new Intent(activity, ManageRecordingsActivity.class);
                    intent.putExtra("TYPE","GUIDE");
                    startActivity(intent);
                    break;
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video)
                mBackgroundUrl = ((Video) item).bgImageUrl;
            startBackgroundTimer();
            scrollSupport.onItemSelected(itemViewHolder,rowViewHolder, getRowsSupportFragment());
        }
    }

    private final class HeaderClickedListener implements HeadersSupportFragment.OnHeaderClickedListener {
        @Override
        public void onHeaderClicked(RowHeaderPresenter.ViewHolder viewHolder, Row row) {
            Activity activity = requireActivity();
            MyHeaderItem headerItem = (MyHeaderItem) row.getHeaderItem();

            Intent intent;
            int type = headerItem.getItemType();
            switch (type) {
                case MainFragment.TYPE_RECGROUP:
                case MainFragment.TYPE_TOP_ALL:
                    intent = new Intent(activity, MainActivity.class);
                    intent.putExtra(MainFragment.KEY_TYPE,MainFragment.TYPE_RECGROUP);
                    intent.putExtra(MainFragment.KEY_BASENAME,headerItem.getName());
                    break;
                case MainFragment.TYPE_VIDEODIR_ALL:
                    intent = new Intent(activity, MainActivity.class);
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
                            setSelectedPosition(rownum, false);
                        return;
                    }
                    intent = new Intent(activity, MainActivity.class);
                    intent.putExtra(MainFragment.KEY_TYPE,MainFragment.TYPE_VIDEODIR);
                    String baseName = headerItem.getBaseName();
                    if (baseName != null && !baseName.isEmpty())
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
                        setSelectedPosition(rownum, false);
                    return;
            }
            Bundle bundle =
                    ActivityOptionsCompat.makeSceneTransitionAnimation
                        (activity, (Pair<View, String>[]) null)
                            .toBundle();
            activity.startActivity(intent, bundle);

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
                    if ((row != null ? row.getHeaderItem() : null) == headerItem)
                        break;
                }
                Row selectedRow = row;
                if (((ListRow)row).getAdapter().size() == 0)
                    break;
                String alertTitle;
                if (type == MainFragment.TYPE_SERIES) {
                    alertTitle = requireContext().getString(R.string.title_menu_series,
                            headerItem.getName(),headerItem.getBaseName());
                    if (!"LiveTV".equals(headerItem.getBaseName())) {
                        if ("Deleted".equals(headerItem.getBaseName())) {
                            prompts.add(getString(R.string.menu_undelete));
                            actions.add(new Action(Video.ACTION_UNDELETE));
                        } else {
                            prompts.add(getString(R.string.menu_delete));
                            actions.add(new Action(Video.ACTION_DELETE));
                            prompts.add(getString(R.string.menu_delete_rerecord));
                            actions.add(new Action(Video.ACTION_DELETE_AND_RERECORD));
                        }
                    }
                    prompts.add(getString(R.string.menu_rerecord));
                    actions.add(new Action(Video.ACTION_ALLOW_RERECORD));
                    if (BackendCache.getInstance().canUpdateRecGroup) {
                        prompts.add(getString(R.string.menu_update_recgrp));
                        actions.add(new Action(Video.ACTION_GETRECGROUPLIST));
                    }
                }
                else {
                    String baseName = headerItem.getBaseName();
                    if (!baseName.isEmpty())
                        baseName = baseName + "/";
                    alertTitle = requireContext().getString(R.string.title_menu_videodir,
                            baseName + headerItem.getName());
                }
                prompts.add(getString(R.string.menu_mark_unwatched));
                actions.add(new Action(Video.ACTION_SET_UNWATCHED));
                prompts.add(getString(R.string.menu_mark_watched));
                actions.add(new Action(Video.ACTION_SET_WATCHED));
                if (BackendCache.getInstance().supportLastPlayPos) {
                    prompts.add(getString(R.string.menu_remove_lastplaypos));
                    actions.add(new Action(Video.ACTION_REMOVE_LASTPLAYPOS));
                }
                prompts.add(getString(R.string.menu_remove_bookmark));
                actions.add(new Action(Video.ACTION_REMOVE_BOOKMARK));
                prompts.add(getString(R.string.menu_remove_from_recent));
                actions.add(new Action(Video.ACTION_REMOVE_RECENT));

                if (!prompts.isEmpty()) {
                    final ArrayList<Action> finalActions = actions; // needed because used in inner class
                    // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
                    AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(),
                            R.style.Theme_AppCompat_Dialog_Alert);
                    builder
                            .setTitle(alertTitle)
                            .setItems(prompts.toArray(new String[0]),
                                    new DialogInterface.OnClickListener() {
                                        final ArrayList<Action> mActions = finalActions;
                                        final MainFragment mParent = MainFragment.this;

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
                break;
            case MainFragment.TYPE_TOP_ALL:
            case MainFragment.TYPE_RECGROUP_ALL:
            case MainFragment.TYPE_RECENTS:
                Intent intent = new Intent(MyApplication.getAppContext(), SettingsActivity.class);
                intent.putExtra(KEY_EXPAND, SettingsEntryFragment.ID_PROG_LIST_OPTIONS);
                requireContext().startActivity(intent);
                break;
        }
        return true; // Do not treat long press as a short press
    }

    public void onMenuClicked(Action action, Row row) {
        int task = (int)action.getId();
        AsyncBackendCall call;
        // Prompting for rec group
        switch (task) {
            case Video.ACTION_GETRECGROUPLIST:
                call = new AsyncBackendCall(getActivity(),
                        taskRunner -> {
                            if (getContext() == null)
                                return;
                            int[] tasks = taskRunner.getTasks();
                            ArrayList<XmlNode> results = taskRunner.getXmlResults();
                            if (tasks[0] == Video.ACTION_GETRECGROUPLIST) {
                                mRecGroupList = XmlNode.getStringList(results.get(0)); // ACTION_GETRECGROUPLIST
                                onMenuClicked(new Action(Video.ACTION_QUERY_UPDATE_RECGROUP), row);
                            }
                        });
                call.execute(task);
                return;

            case Video.ACTION_QUERY_UPDATE_RECGROUP:
                String alertTitle = getString(R.string.menu_update_recgrp);
                @SuppressWarnings("unchecked")
                ArrayList<String> prompts = (ArrayList<String>) mRecGroupList.clone();
                prompts.remove("LiveTV");
                prompts.add(getString(R.string.sched_new_entry));
                final ArrayList<String> groups = prompts;
                AlertDialog.Builder listBbuilder = new AlertDialog.Builder(requireActivity(),
                        R.style.Theme_AppCompat_Dialog_Alert);
                listBbuilder
                        .setTitle(alertTitle)
                        .setItems(groups.toArray(new String[0]),
                                (dialog, which) -> {
                                    // The 'which' argument contains the index position
                                    // of the selected item
                                    // Last item in the list is "Create New Entry"
                                    if (which == groups.size() - 1) {
                                        mNewValueText = "";
                                        promptForNewValue(R.string.sched_rec_group, Video.ACTION_UPDATE_RECGROUP, row);
                                    } else {
                                        mNewValueText = groups.get(which);
                                        onMenuClicked(new Action(Video.ACTION_UPDATE_RECGROUP), row);
                                    }
                                });
                listBbuilder.show();
                return;
        }

        ListRow listRow = (ListRow) row;
        ObjectAdapter rowAdapter = listRow.getAdapter();
        call = new AsyncBackendCall(getActivity(),
                taskRunner -> {
                    if (getContext() == null)
                        return;
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
                });
        call.setBookmark(0);
        call.setPosBookmark(0);
        call.setRowAdapter(rowAdapter);
        call.setStringParameter(mNewValueText);
        Integer [] tasks;

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
    }

    @SuppressWarnings("SameParameterValue")
    private void promptForNewValue(int msgid, int nextId, Row row) {
        mNewValueText = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(msgid);
        EditText input = new EditText(getContext());
        input.setText(mNewValueText);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            mNewValueText = input.getText().toString();
            onMenuClicked(new Action(nextId), row);
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private static final String KEY_EXPAND = "EXPAND";
    private static class MythTask implements Runnable {
        boolean mVersionMessageShown = false;
        private final Handler taskHandler = new Handler(Looper.getMainLooper());

        @Override
        public synchronized void run() {
            try {
                boolean loginTried = false;
                boolean loginNeededNow = false;
                scheduledTaskRunning = true;
                boolean connection = false;
                BackendCache bCache = BackendCache.getInstance();
                String backendIP = bCache.sBackendIP;
                if (backendIP.isEmpty())
                    return;
                while (!connection) {
                    boolean connectionfail = false;
                    if (SettingsEntryFragment.isActive) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                    if (ProcessLifecycleOwner.get().getLifecycle().getCurrentState()
                            == Lifecycle.State.CREATED) {
                        // process is now in the background
                        taskHandler.removeCallbacksAndMessages(null);
                        mWasInBackground = true;
                        if (executor != null)
                            executor.shutdown();
                        executor = null;
                    }
                    if (executor == null)
                        return;
                    int toastMsg = 0;
                    int toastLeng = 0;
                    if (loginNeededNow) {
                        bCache.loginNeeded = true;
                        String user = Settings.getString("pref_backend_userid").trim();
                        try {
                            String result;
                            String url = XmlNode.mythApiUrl(null,
                                    "/Myth/LoginUser") +
                                    "?UserName=" +
                                    URLEncoder.encode(user, "UTF-8") +
                                    "&Password=" +
                                    URLEncoder.encode(Settings.getString("pref_backend_passwd").trim(), "UTF-8");
                            XmlNode loginXml = XmlNode.fetch(url, "POST");
                            result = loginXml.getString();
                            connection = true;
                            if (result.isEmpty()) {
                                Log.e(TAG, CLASS + " MythTask empty response from LoginUser");
                                bCache.authorization = null;
                            }
                            else
                                bCache.authorization = result;
                        } catch (Exception e) {
                            Log.e(TAG, CLASS + " Exception in LoginUser.", e);
                            bCache.authorization = null;
                        }
                        // If the demo user had been used but is no longer aithorized, remove the demo
                        // user and password so that the settings do not display it
                        if (bCache.authorization == null && user.equals(MainFragment.mActiveFragment.getString(R.string.demo_user))) {
                            SharedPreferences.Editor editor = Settings.getEditor();
                            Settings.putString(editor,"pref_backend_userid", "");
                            Settings.putString(editor,"pref_backend_passwd", "");
                            editor.commit();
                        }
                        loginTried = true;
                    }
                    try {
                        String result;
                        String url = XmlNode.mythApiUrl(null,
                                "/Myth/DelayShutdown");
                        if (url == null)
                            return;
                        XmlNode bkmrkData = XmlNode.fetch(url, "POST");
                        result = bkmrkData.getString();
                        connection = true;
                        if (!"true".equals(result))
                            Log.e(TAG, CLASS + " MythTask Incorrect response from DelayShutdown: " + result);
                    } catch (FileNotFoundException | XmlPullParserException ex) {
                        if (!mVersionMessageShown) {
                            toastMsg = R.string.msg_no_delayshutdown;
                            toastLeng = Toast.LENGTH_LONG;
                            mVersionMessageShown = true;
                        }
                        connection = true;
                        Log.e(TAG, CLASS + " MythTask DelayShutdown Exception ", ex);
                    } catch (IOException e) {
                        if ("Unauthorized: 401".equals(e.getMessage())) {
                            if (Settings.getString("pref_backend_userid").isEmpty()
                                || Settings.getString("pref_backend_passwd").isEmpty()
                                || loginTried) {
                                bCache.loginNeeded = true;
                                toastMsg = R.string.msg_backend_login_req;
                                Intent intent = new Intent(MainFragment.mActiveFragment.getActivity(), SettingsActivity.class);
                                intent.putExtra(KEY_EXPAND, SettingsEntryFragment.ID_BACKEND);
                                MainFragment.mActiveFragment.requireActivity().startActivity(intent);
                            }
                            else
                                loginNeededNow = true;
                        }
                        else
                            toastMsg = R.string.msg_no_connection;
                        toastLeng = Toast.LENGTH_LONG;
                        connectionfail = true;
                        mFetchTime = 0; // Force a fetch when it comes back
                    }
                    if (connectionfail)
                        if (wakeBackend())
                            toastMsg = R.string.msg_wake_backend;

                    if (toastMsg != 0) {
                        Context context = MyApplication.getAppContext();
                        if (context == null)
                            return;
                        ToastShower toastShower = new ToastShower(context, toastMsg, toastLeng);
                        taskHandler.post(toastShower);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                if (mFetchTime <= System.currentTimeMillis()
                        - (long) Settings.getInt("pref_refresh_mins") * 60 * 1000 + 100) {
                    BackendCache.flush();
                    MainFragment.startFetch(-1, null, null, false);
                }
            } catch (Exception ex) {
                Log.e(TAG, CLASS + " MythTask Exception ", ex);
            } finally {
                scheduledTaskRunning = false;
            }
        }

        public boolean wakeBackend() {
            Context context = MyApplication.getAppContext();
            if (context == null)
                return false;
            String backendMac = Settings.getString("pref_backend_mac");
            if (backendMac.isEmpty())
                return false;

            // The magic packet is a broadcast frame containing anywhere within its payload
            // 6 bytes of all 255 (FF_FF_FF_FF_FF_FF in hexadecimal), followed by sixteen
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

            try (DatagramSocket ds = new DatagramSocket()){
                DatagramPacket DpSend = new DatagramPacket(msg, msg.length, InetAddress.getByName("255.255.255.255"), 9);
                ds.send(DpSend);
            } catch (IOException e) {
                Log.e(TAG, CLASS + " Exception ", e);
                return false;
            }
            return true;
        }
    }

    public static class ToastShower implements Runnable {

        private final Context context;
        private final int toastMsg;
        private final int toastLeng;
        private static Toast mToast;

        public ToastShower(Context context, int toastMsg, int toastLeng) {
            this.context = context;
            this.toastMsg = toastMsg;
            this.toastLeng = toastLeng;
        }
        public void run() {
            // show toast here
            if (mToast != null)
                mToast.cancel();
            mToast = Toast.makeText(context,
                    context.getString(toastMsg), toastLeng);
            mToast.show();
        }
    }

    private class SelectionSetter implements Runnable {

        private final int selectedRowNum;
        private final int selectedItemNum;

        public SelectionSetter(int selectedRowNum, int selectedItemNum) {
            this.selectedRowNum = selectedRowNum;
            this.selectedItemNum = selectedItemNum;
        }
        public void run() {
            RowsSupportFragment frag = getRowsSupportFragment();
            if (frag != null) {
                // Note we do not need to check selectedRowNum or
                // selectedItemNum, if either is more than the maximum
                // there is no exception - it just selects the last item.
                ListRowPresenter.SelectItemViewHolderTask task
                        = new ListRowPresenter.SelectItemViewHolderTask(selectedItemNum);
                task.setSmoothScroll(false);
                frag.setSelectedPosition(selectedRowNum, false, task);
                if (selectedItemNum == -1)
                    if (getHeadersSupportFragment().getView() != null) {
                        getHeadersSupportFragment().getView().requestFocus();
                    }
            }
        }
    }

}
