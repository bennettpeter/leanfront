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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.DetailsSupportFragment;
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
import androidx.core.app.ActivityOptionsCompat;
import androidx.loader.app.LoaderManager;
import androidx.core.content.ContextCompat;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
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
    private int mGlobalSearchVideoId = 2;

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
    private long posBookmark = -1;  // position in frames
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
                .getParcelableExtra(VideoDetailsActivity.VIDEO);

        if (savedInstanceState != null) {
            mBookmark = savedInstanceState.getLong("mBookmark");
            posBookmark = savedInstanceState.getLong("posBookmark");
        }

        if (mSelectedVideo != null) {
            removeNotification(getActivity().getIntent()
                    .getIntExtra(VideoDetailsActivity.NOTIFICATION_ID, NO_NOTIFICATION));
            setupAdapter();
            setupDetailsOverviewRow();
            setupMovieListRow();
            updateBackground(mSelectedVideo.bgImageUrl);
            int progflags = Integer.parseInt(mSelectedVideo.progflags);
            mWatched = ((progflags & Video.FL_WATCHED) != 0);
            if (mSelectedVideo.rectype != VideoContract.VideoEntry.RECTYPE_CHANNEL)
                new AsyncBackendCall(mSelectedVideo, 0, false,
                        this).execute(Video.ACTION_REFRESH);

            // When a Related Video item is clicked.
            setOnItemViewClickedListener(itemViewClickedListener);
            setOnItemViewSelectedListener(itemViewSelectedListener);
        }
    }

    public void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("mBookmark", mBookmark);
        outState.putLong("posBookmark", posBookmark);
    }

    private void removeNotification(int notificationId) {
        if (notificationId != NO_NOTIFICATION) {
            NotificationManager notificationManager = (NotificationManager) getActivity()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
        }
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

    /**
     * Check if there is a global search intent. If there is, load that video.
     */
    private boolean hasGlobalSearchIntent() {
        Intent intent = getActivity().getIntent();
        String intentAction = intent.getAction();
        String globalSearch = getString(R.string.global_search);

        if (globalSearch.equalsIgnoreCase(intentAction)) {
            Uri intentData = intent.getData();
            String videoId = intentData.getLastPathSegment();

            Bundle args = new Bundle();
            args.putString(VideoContract.VideoEntry._ID, videoId);
            LoaderManager manager = LoaderManager.getInstance(this);
            manager.initLoader(mGlobalSearchVideoId++, args, this);
            return true;
        }
        return false;
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

        Glide.with(this)
                .asBitmap()
                .load(uri)
                .apply(options)
                .into(new SimpleTarget<Bitmap>(mMetrics.widthPixels, mMetrics.heightPixels) {
                    @Override
                    public void onResourceReady(
                            Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
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
                VideoDetailsActivity.SHARED_ELEMENT_NAME);
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

    @Override
    public void onActionClicked(Action action) {
        int id = (int) action.getId();
        long bookmark = 0;
        long posbookmark = -1;
        ArrayList<String> prompts = null;
        ArrayList<Action> actions = null;
        String alertTitle = null;

        if (mSelectedVideo.rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL
            && (id == Video.ACTION_PLAY_FROM_BOOKMARK || id == Video.ACTION_PLAY))
            id = Video.ACTION_LIVETV;
        switch (id) {
            case Video.ACTION_PLAY_FROM_BOOKMARK:
                bookmark = mBookmark;
                posbookmark = posBookmark;
            case Video.ACTION_PLAY:
                Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, mSelectedVideo);
                intent.putExtra(VideoDetailsActivity.BOOKMARK, bookmark);
                intent.putExtra(VideoDetailsActivity.POSBOOKMARK, posbookmark);
                startActivityForResult(intent, Video.ACTION_PLAY);
                break;
            case Video.ACTION_LIVETV:
                setProgressBar(true);
                new AsyncBackendCall(mSelectedVideo, 0L, false,
                        this).execute(Video.ACTION_LIVETV);
                break;
            case Video.ACTION_DELETE:
                new AsyncBackendCall(mSelectedVideo, 0, false,
                        this)
                        .execute(Video.ACTION_REFRESH, Video.ACTION_DELETE, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_DELETE_AND_RERECORD:
                new AsyncBackendCall(mSelectedVideo, 0, false,
                        this)
                        .execute(Video.ACTION_REFRESH, Video.ACTION_DELETE_AND_RERECORD, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_ALLOW_RERECORD:
                new AsyncBackendCall(mSelectedVideo, 0, false,
                        this)
                        .execute(Video.ACTION_ALLOW_RERECORD);
                break;
            case Video.ACTION_UNDELETE:
                new AsyncBackendCall(mSelectedVideo, 0, false,
                        this)
                        .execute(Video.ACTION_UNDELETE, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_SET_WATCHED:
            case Video.ACTION_SET_UNWATCHED:
                mWatched = (id == Video.ACTION_SET_WATCHED);
                new AsyncBackendCall(mSelectedVideo, 0, mWatched,
                        this)
                        .execute(Video.ACTION_SET_WATCHED, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_REMOVE_BOOKMARK:
                mBookmark = 0;
                posBookmark = 0;
                AsyncBackendCall call = new AsyncBackendCall(mSelectedVideo, mBookmark, false,
                        this);
                call.setPosBookmark(posBookmark);
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
                    new AsyncBackendCall(mSelectedVideo, 0, false,
                            this)
                            .execute(Video.ACTION_STOP_RECORDING,
                                    Video.ACTION_REFRESH);
                }
                break;
            case Video.ACTION_CANCEL:
                break;
            case Video.ACTION_VIEW_DESCRIPTION:
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                        R.style.Theme_AppCompat);
                String msg = mSelectedVideo.title + "\n"
                    + mDetailsDescriptionPresenter.getSubtitle() + "\n"
                        + mDetailsDescriptionPresenter.getDescription();
                builder.setMessage(msg);
                builder.show();
                break;
            case Video.ACTION_REMOVE_RECENT:
                new AsyncBackendCall(mSelectedVideo, 0, false,
                        this)
                        .execute(Video.ACTION_REMOVE_RECENT, Video.ACTION_REFRESH);
                break;
            case Video.ACTION_OTHER:
                if (mSelectedVideo.rectype != VideoContract.VideoEntry.RECTYPE_RECORDING
                        && mSelectedVideo.rectype != VideoContract.VideoEntry.RECTYPE_VIDEO)
                    break;
                prompts = new ArrayList<>();
                actions = new ArrayList<>();
                if (mSelectedVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING) {
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
                }
                if (mWatched) {
                    prompts.add(getString(R.string.menu_mark_unwatched));
                    actions.add(new Action(Video.ACTION_SET_UNWATCHED));
                } else {
                    prompts.add(getString(R.string.menu_mark_watched));
                    actions.add(new Action(Video.ACTION_SET_WATCHED));
                }

                if (mBookmark > 0 || posBookmark > 0) {
                    prompts.add(getString(R.string.menu_remove_bookmark));
                    actions.add(new Action(Video.ACTION_REMOVE_BOOKMARK));
                }
                if (mSelectedVideo.isRecentViewed()) {
                    prompts.add(getString(R.string.menu_remove_from_recent));
                    actions.add(new Action(Video.ACTION_REMOVE_RECENT));
                }

                // End Time
                try {
                    SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
                    if (mSelectedVideo.endtime != null) {
                        Date dateEnd = dbFormat.parse(mSelectedVideo.endtime + "+0000");
                        long dateMS = dateEnd.getTime();
                        // If end time is more than 2 mins in the future allow stopping
                        if (dateMS > System.currentTimeMillis() + 120000) {
                            prompts.add(getString(R.string.menu_stop_recording));
                            actions.add(new Action(Video.ACTION_QUERY_STOP_RECORDING));
                        }
                    }
                } catch (ParseException e) {
                    Log.e(TAG, CLASS + " Exception parsing endtime.", e);
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


    public void onActivityResult (int requestCode,
                                  int resultCode,
                                  Intent intent) {
        if (requestCode == Video.ACTION_PLAY
                && mSelectedVideo.rectype != VideoContract.VideoEntry.RECTYPE_CHANNEL)
            new AsyncBackendCall(mSelectedVideo, 0, false,
                    this).execute(Video.ACTION_REFRESH);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        boolean showDeleted = "true".equals(Settings.getString("pref_related_deleted"));
        boolean showWatched = "true".equals(Settings.getString("pref_related_watched"));
        String seq = Settings.getString("pref_seq");
        String ascdesc = Settings.getString("pref_seq_ascdesc");

        switch (id) {

            case RELATED_VIDEO_LOADER: {
                // When loading related videos or videos for the playlist, query by category.
                int rectype = args.getInt(VideoContract.VideoEntry.COLUMN_RECTYPE, -1);
                String recgroup = args.getString(VideoContract.VideoEntry.COLUMN_RECGROUP);
                String filename = args.getString(VideoContract.VideoEntry.COLUMN_FILENAME);
                StringBuilder orderby;
                if (rectype == VideoContract.VideoEntry.RECTYPE_VIDEO) {
                    // Videos
                    int pos = filename.lastIndexOf('/');
                    String dirname = "";
                    if (pos >= 0)
                        dirname = filename.substring(0, pos + 1);
                    dirname = dirname + "%";
                    String subdirname = dirname + "%/%";

                    orderby = MainFragment.makeTitleSort
                            (VideoContract.VideoEntry.COLUMN_FILENAME, '/');
                    StringBuilder where = new StringBuilder();
                    where   .append(VideoContract.VideoEntry.COLUMN_RECTYPE)
                            .append(" = ").append(VideoContract.VideoEntry.RECTYPE_VIDEO)
                            .append(" and ")
                            .append(VideoContract.VideoEntry.COLUMN_FILENAME)
                            .append(" like ? and ")
                            .append(VideoContract.VideoEntry.COLUMN_FILENAME)
                            .append(" not like ? ");
                    if (!showWatched)
                        where.append(" and ")
                             .append(VideoContract.VideoEntry.COLUMN_PROGFLAGS)
                             .append(" & ").append(Video.FL_WATCHED)
                             .append(" == 0");

                    return new CursorLoader(
                            getActivity(),
                            VideoContract.VideoEntry.CONTENT_URI,
                            null,
                            where.toString(),
                            new String[]{dirname, subdirname},
                            orderby.toString());
                } else {
                    // Recordings or LiveTV
                    String category;
                    if (rectype == VideoContract.VideoEntry.RECTYPE_RECORDING)
                        category = args.getString(VideoContract.VideoEntry.COLUMN_TITLE);
                    else
                        category = "X\t"; // To prevent anything being found
                    StringBuilder where = new StringBuilder();
                    where.append(VideoContract.VideoEntry.COLUMN_RECTYPE)
                            .append(" = ")
                            .append(VideoContract.VideoEntry.RECTYPE_RECORDING);
                    boolean deleted = "Deleted".equals(recgroup);
                    if (deleted) {
                        // when we are in the Deleted group, show all rec groups
                        // unless asked to exclude deleted.
                        if (!showDeleted) {
                            where.append(" and ");
                            where.append(VideoContract.VideoEntry.COLUMN_RECGROUP)
                                    .append(" != 'Deleted' ");
                        }
                    } else {
                        where.append(" and ");
                        if (showDeleted)
                            where.append(" ( ");
                        where.append(VideoContract.VideoEntry.COLUMN_RECGROUP)
                             .append(" = ? ");
                        if (showDeleted) {
                            where.append(" or ");
                            where.append(VideoContract.VideoEntry.COLUMN_RECGROUP)
                                    .append(" = 'Deleted' ) ");
                        }
                    }
                    where.append(" and ")
                            .append(VideoContract.VideoEntry.COLUMN_TITLE)
                            .append(" = ? ");
                    if (!showWatched)
                        where.append(" and ")
                                .append(VideoContract.VideoEntry.COLUMN_PROGFLAGS)
                                .append(" & ").append(Video.FL_WATCHED)
                                .append(" == 0 ");
                    orderby = MainFragment.makeTitleSort(VideoContract.VideoEntry.COLUMN_TITLE, '^')
                        .append(", ");
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
                    String [] selectionArgs;
                    if (deleted)
                        selectionArgs = new String[]{category};
                    else
                        selectionArgs = new String[]{recgroup,category};
                    return new CursorLoader(
                            getActivity(),
                            VideoContract.VideoEntry.CONTENT_URI,
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
                        VideoContract.VideoEntry.CONTENT_URI,
                        null,
                        VideoContract.VideoEntry._ID + " = ?",
                        new String[]{videoId},
                        null);
            }
        }

    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
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
                    new AsyncBackendCall(mSelectedVideo, 0, false,
                            this).execute(Video.ACTION_REFRESH);
                    // When a Related Video item is clicked.
                    setOnItemViewClickedListener(itemViewClickedListener);
                    setOnItemViewSelectedListener(itemViewSelectedListener);
                }
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
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
        if (mSelectedVideo.rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL) {
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
                            Bitmap resource,
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
        else
            Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .apply(options)
                    .into(target);

        mActionsAdapter = new SparseArrayObjectAdapter();
        if (mSelectedVideo.rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL) {
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

        // Generating related video list.
        String category = mSelectedVideo.title;

        Bundle args = new Bundle();
        args.putInt(VideoContract.VideoEntry.COLUMN_RECTYPE, mSelectedVideo.rectype);
        args.putString(VideoContract.VideoEntry.COLUMN_TITLE, category);
        args.putString(VideoContract.VideoEntry.COLUMN_RECGROUP, mSelectedVideo.recGroup);
        args.putString(VideoContract.VideoEntry.COLUMN_FILENAME, mSelectedVideo.filename);
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
                intent.putExtra(VideoDetailsActivity.VIDEO, video);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
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
                Intent intent = new Intent(context, PlaybackActivity.class);
                intent.putExtra(VideoDetailsActivity.VIDEO, video);
                intent.putExtra(VideoDetailsActivity.BOOKMARK, 0L);
                intent.putExtra(VideoDetailsActivity.RECORDID, taskRunner.getRecordId());
                startActivity(intent);
                break;
            case Video.ACTION_ALLOW_RERECORD:
                XmlNode xml = taskRunner.getXmlResult();
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

            default:
                if (context == null)
                    break;
                xml = taskRunner.getXmlResult();
                mBookmark = taskRunner.getBookmark();
                posBookmark = taskRunner.getPosBookmark();
                int progflags = Integer.parseInt(mSelectedVideo.progflags);
                mWatched = ((progflags & Video.FL_WATCHED) != 0);
                mDetailsDescriptionPresenter.setupDescription();
                int i = 0;
                mActionsAdapter.clear();
                if (mBookmark > 0 || posBookmark > 0)
                    mActionsAdapter.set(++i, new Action(Video.ACTION_PLAY_FROM_BOOKMARK, getResources()
                            .getString(R.string.resume_1),
                            getResources().getString(R.string.resume_2)));
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
