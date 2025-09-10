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
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_VIDEO;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.mythtv.leanfront.R;
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
import java.util.ArrayList;

/*
 * VideoDetailsFragment extends DetailsFragment, a Wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its metadata plus related videos.
 */
@SuppressLint("SimpleDateFormat")
public class VideoDetailsFragment extends DetailsSupportFragment
        implements LoaderManager.LoaderCallbacks<Cursor> {

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
    private DisplayMetrics mMetrics;
    private CursorObjectAdapter mVideoCursorAdapter;
    private FullWidthDetailsOverviewSharedElementHelper mHelper;
    private final VideoCursorMapper mVideoCursorMapper = new VideoCursorMapper();
    private SparseArrayObjectAdapter mActionsAdapter = null;
    private DetailsOverviewRow mDetailsOverviewRow = null;
    private boolean mWatched;
    private DetailsDescriptionPresenter mDetailsDescriptionPresenter;
    private ItemViewClickedListener itemViewClickedListener = new ItemViewClickedListener();
    private ItemViewSelectedListener itemViewSelectedListener = new ItemViewSelectedListener();
    private VideoAction videoAction;
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
        mVideoCursorAdapter = new CursorObjectAdapter(new CardPresenter(this));
        mVideoCursorAdapter.setMapper(mVideoCursorMapper);

        mSelectedVideo = getActivity().getIntent()
                .getParcelableExtra(PlaybackActivity.VIDEO);

        videoAction = new VideoAction(this, mSelectedVideo);

        if (savedInstanceState != null) {
            videoAction.mBookmark = savedInstanceState.getLong("mBookmark");
            videoAction.mPosBookmark = savedInstanceState.getLong("posBookmark");
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
                videoAction.onActionClicked(new Action(Video.ACTION_REFRESH));
            }

            // When a Related Video item is clicked.
            setOnItemViewClickedListener(itemViewClickedListener);
            setOnItemViewSelectedListener(itemViewSelectedListener);
        }
    }

    public void onSaveInstanceState (Bundle outState) {
        outState.putLong("mBookmark", videoAction.mBookmark);
        outState.putLong("posBookmark", videoAction.mPosBookmark);
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
        mDefaultBackground = getResources().getDrawable(R.drawable.background, null);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void updateBackground(String uri) {

        RequestOptions options = new RequestOptions()
                .centerCrop()
                .error(mDefaultBackground)
                .timeout(5000);

       RequestBuilder bld =  Glide.with(this)
                .asBitmap();
        if (uri == null)
            bld = bld.load(R.drawable.background);

        else {
            String auth = BackendCache.getInstance().authorization;
            LazyHeaders.Builder lzhb = new LazyHeaders.Builder();
            if (auth != null && auth.length() > 0)
                lzhb.addHeader("Authorization", auth);
            bld = bld.load(new GlideUrl(uri, lzhb.build()));
        }
        bld.apply(options)
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
        videoAction.setDetailsDescriptionPresenter(mDetailsDescriptionPresenter);
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

        detailsPresenter.setOnActionClickedListener(videoAction);

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


    public void onActionClicked(Action action) {
        videoAction.onActionClicked(action);
    }

    public void onActivityResult (int requestCode,
                                  int resultCode,
                                  Intent intent) {
        if (requestCode == Video.ACTION_PLAY
                && mSelectedVideo.rectype != RECTYPE_CHANNEL) {
            videoAction.onActionClicked(new Action(Video.ACTION_REFRESH));
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
                    videoAction.setSelectedVideo(mSelectedVideo);

                    setupAdapter();
                    setupDetailsOverviewRow();
                    setupMovieListRow();
                    updateBackground(mSelectedVideo.bgImageUrl);
                    videoAction.onActionClicked(new Action(Video.ACTION_REFRESH));
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
        options.timeout(5000);

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
        videoAction.setActionsAdapter(mActionsAdapter);
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
}
