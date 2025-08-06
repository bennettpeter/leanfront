/*
 * Copyright (c) 2017 The Android Open Source Project
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

package org.mythtv.leanfront.ui.playback;

import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_AIRDATE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_EPISODE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_FILENAME;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_RECGROUP;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_RECTYPE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_SEASON;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_STARTTIME;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_TITLE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_TITLEMATCH;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_VIDEO_URL;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.CONTENT_URI;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_VIDEO;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.leanback.app.ProgressBarManager;
import androidx.leanback.app.VideoSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.core.app.ActivityOptionsCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.BackendCache;
import org.mythtv.leanfront.data.CommBreakTable;
import org.mythtv.leanfront.data.MythHttpDataSource;
import org.mythtv.leanfront.model.Playlist;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.model.VideoCursorMapper;
import org.mythtv.leanfront.player.MyExtractorsFactory;
import org.mythtv.leanfront.player.MyRenderersFactory;
import org.mythtv.leanfront.player.VideoPlayerGlue;
import org.mythtv.leanfront.presenter.CardPresenter;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;
import androidx.media3.ui.SubtitleView;
import org.mythtv.leanfront.ui.MainFragment;
import org.mythtv.leanfront.ui.VideoDetailsActivity;

import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import androidx.media3.ui.leanback.LeanbackPlayerAdapter;

import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.SampleQueue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Plays selected video, loads playlist and related videos, and delegates playback to {@link
 * VideoPlayerGlue}.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackFragment extends VideoSupportFragment
        implements AsyncBackendCall.OnBackendCallListener {

    private static final int UPDATE_DELAY = 16;

    VideoPlayerGlue mPlayerGlue;
    private LeanbackPlayerAdapter mPlayerAdapter;
    ExoPlayer mPlayer;
    private DefaultTrackSelector mTrackSelector;
    PlaybackActionListener mPlaybackActionListener;
    private PlayerEventListener mPlayerEventListener;

    Video mVideo;
    private Playlist mPlaylist;
    private VideoLoaderCallbacks mVideoLoaderCallbacks;
    private CursorObjectAdapter mVideoCursorAdapter;
    private long mBookmark = 0;     // milliseconds
    private long posBookmark = -1;  // position in frames
    private boolean mWatched = false;
    private static final float SPEED_START_VALUE = 1.0f;
    float mSpeed = SPEED_START_VALUE;
    Toast mToast = null;
    private SubtitleView mSubtitles;
    // for these selections, -2 = default, -1 = disabled,
    // 0 or above = enabled track number
    int mTextSelection = -2;
    int mAudioSelection = -2;
    private long mFileLength = -1;
    private MythHttpDataSource.Factory mDsFactory;
    ProgressiveMediaSource mMediaSource;
    private MythHttpDataSource mDataSource;
    // Settings - These are default values that will be changed if the video
    // uses a different playback group
    private int mSkipFwd = 1000 * Settings.getInt("pref_skip_fwd");
    private int mSkipBack = 1000 * Settings.getInt("pref_skip_back");
    private int mJump = 60000 * Settings.getInt("pref_jump");
    private String prefAudio = Settings.getString("pref_audio");
    private String prefVideo = Settings.getString("pref_video");
    private boolean mFrameMatch = "true".equals(Settings.getString("pref_framerate_match"));
    private int mSubtitleSize =  Settings.getInt("pref_subtitle_size");
    private int mBgColor = Settings.getInt("pref_letterbox_color");
    public boolean mAudioPause = "true".equals(Settings.getString("pref_audio_pause"));
    // Selectable command for certain keys
    static final int CMD_CONTROLS = 1;
    static final int CMD_JUMP = 2;
    static final int CMD_SKIPCOM = 3;
    static final int CMD_REWFF = 4;
    // Options selected for certain keys, from Settings
    // Values here are the defaults
    int optUpDown = CMD_CONTROLS;
    int optLeftRight = CMD_JUMP;
    int optRewFF = CMD_REWFF;

    int mCaptions = 0;

    private View mFocusView;
    private Action mCurrentAction;

    // Support for Live TV
    private long mRecordid = -1;
    private long mNextRecordid = -1;
    private Date endTime = null;
    private Date nextEndTime = null;
    boolean saveLiveRec = false;
    private ScheduledFuture<?> nextScheduler;

    private static final String TAG = "lfe";
    private static final String CLASS = "PlaybackFragment";

    private float frameRate = -1.0f;
    private boolean possibleEmptyTrack;
    private boolean playWhenPrepared;
    private ScheduledFuture<?> audioFixTask;
    private boolean isTV;
    boolean isIncreasing;
    CommBreakTable commBreakTable = new CommBreakTable();
    int commBreakOption =  Settings.getInt("pref_commskip");
    public static final int COMMBREAK_OFF = 0;
    public static final int COMMBREAK_NOTIFY = 1;
    public static final int COMMBREAK_SKIP = 2;
    long priorCommBreak = -1;
    // Initialized to invalid value so that the playgroup settings
    // will always be initialized on the first playback.
    private String currentPlayGroup = " ";
    private ScheduledFuture<?> monitorSched;
    private ScheduledFuture<?> tablesSched;
    private static final int STATUS_MONITOR_INTERVAL = 5000;
    private static final int TABLES_MONITOR_INTERVAL = 180000;
    private boolean tablesFilled = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiModeManager uiModeManager = (UiModeManager) getContext().getSystemService(Context.UI_MODE_SERVICE);
        isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

        mVideo = getActivity().getIntent().getParcelableExtra(PlaybackActivity.VIDEO);
        setPlaySettings(mVideo.playGroup);
        mBookmark = getActivity().getIntent().getLongExtra(PlaybackActivity.BOOKMARK, 0);
        posBookmark = getActivity().getIntent().getLongExtra(PlaybackActivity.POSBOOKMARK, -1);
        mRecordid = getActivity().getIntent().getLongExtra(PlaybackActivity.RECORDID, -1);
        long endTimeL = getActivity().getIntent().getLongExtra(PlaybackActivity.ENDTIME, -1);
        if (endTimeL > 0)
            endTime = new Date(endTimeL);
        mPlaylist = new Playlist();
        mWatched = (Integer.parseInt(mVideo.progflags, 10) & Video.FL_WATCHED) != 0;

        mVideoLoaderCallbacks = new VideoLoaderCallbacks(mPlaylist);

        // Loads the playlist.
        Bundle args = new Bundle();
        // related videos only display directory list in this case
        // otherwise treated as tv series
        if (mVideo.rectype == RECTYPE_VIDEO
                && (mVideo.season == null || mVideo.season.equals("0"))
                && (mVideo.episode == null || mVideo.episode.equals("0"))
                && mVideo.airdate == null)
            args.putInt(COLUMN_RECTYPE, mVideo.rectype);
        args.putString(COLUMN_TITLE, mVideo.title);
        args.putString(COLUMN_TITLEMATCH, mVideo.titlematch);
        args.putString(COLUMN_RECGROUP, mVideo.recGroup);
        args.putString(COLUMN_FILENAME, mVideo.filename);
        args.putString(COLUMN_VIDEO_URL, mVideo.videoUrl);

        LoaderManager manager = LoaderManager.getInstance(this);
        manager.initLoader(VideoLoaderCallbacks.QUEUE_VIDEOS_LOADER, args, mVideoLoaderCallbacks);

        mVideoCursorAdapter = setupRelatedVideosCursor();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer(true);
        }
        scheduleNext();
    }

    void scheduleNext () {
        // Live TV - set a recording for the next program
        // will create the schedule 5 minutes before the time due or now
        // if there is less than 5 minutes.
        // If the start time for the next episode is already past, do not schedule.
        if (endTime != null && mRecordid > 0 && endTime.getTime() > System.currentTimeMillis()) {
            ScheduledExecutorService executor = MainFragment.getExecutor();
            if (executor != null) {
                long millis = endTime.getTime() - System.currentTimeMillis() - 5L*60000;
                if (millis < 1000)
                    millis = 1000;
                if (nextScheduler !=  null && !nextScheduler.isCancelled() && !nextScheduler.isDone())
                    nextScheduler.cancel(true);
                nextScheduler = executor.schedule(() -> {
                    AsyncBackendCall call = new AsyncBackendCall(getActivity(), this );
                    call.setStartTime(endTime);
                    call.setChanid(Integer.parseInt(mVideo.chanid));
                    call.setCallSign(mVideo.callsign);
                    call.execute(Video.ACTION_LIVETV, Video.ACTION_ADD_OR_UPDATERECRULE);
                }, millis, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || mPlayer == null)) {
            initializePlayer(true);
        }
        hideNavigation();
        // To reduce dimming when showing controls.
        // This also make controls difficult to see on light videos
        setBackgroundType(BG_LIGHT);

        View view = getView();
        view.setBackgroundColor(mBgColor);
    }

    public void hideNavigation () {
        if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            View view = getView();
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /**
     * Pauses the player.
     */
    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public void onPause() {
        super.onPause();
        setBookmark(Video.ACTION_SET_LASTPLAYPOS);
        isIncreasing = false;
        mPlayerGlue.setIncreasing(isIncreasing);
        if ( monitorSched != null && !monitorSched.isDone() && !monitorSched.isCancelled())
            monitorSched.cancel(false);
        if ( tablesSched != null && !tablesSched.isDone() && !tablesSched.isCancelled())
            tablesSched.cancel(false);
        if (mPlayerGlue != null && mPlayerGlue.isPlaying()) {
            mPlayerGlue.pause();
        }
        if (mRecordid > 0) {
            // Terminate Live TV
            AsyncBackendCall call;
            if (!saveLiveRec) {
                call = new AsyncBackendCall(getActivity(), null);
                call.setRecordedId(Integer.parseInt(mVideo.recordedid));
                call.setRecordId(mRecordid);
                call.execute(
                        Video.ACTION_STOP_RECORDING,
                        Video.ACTION_REMOVE_RECORD_RULE);
            }
            if (nextScheduler != null && !nextScheduler.isCancelled()
                    && !nextScheduler.isDone())
                nextScheduler.cancel(true);
            if (mNextRecordid > 0) {
                call = new AsyncBackendCall(getActivity(), null);
                call.setRecordId(mNextRecordid);
                call.execute(
                        Video.ACTION_GET_RECORDED,
                        Video.ACTION_STOP_RECORDING,
                        Video.ACTION_REMOVE_RECORD_RULE);
            }
            mRecordid = -1;
            mNextRecordid = -1;
        }
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    /**
     * Set a last play pos or bookmark on MythTV. If backend does not support last play pos, the
     * last play pos will be set as a bookmark. So you should only use the ACTION_SET_BOOKMARK option
     * if the backend supports last play position.
     * @param action Value from Video ACTIONS Video.ACTION_SET_LASTPLAYPOS or Video.ACTION_SET_BOOKMARK
     */
    void setBookmark(int action) {
        long bookmark;
        long pos = mPlayerGlue.getCurrentPosition();
        long leng = mPlayerGlue.myGetDuration();
        if (pos < 0)
            pos = mPlayerGlue.getSavedCurrentPosition();
        if ( (pos > 10000 && (isIncreasing || pos < leng - 10000))
                || action == Video.ACTION_SET_BOOKMARK)
            bookmark = pos;
        else
            bookmark = 0;
        int action2 = Video.ACTION_DUMMY;
        posBookmark = bookmark * (long)(frameRate * 100.0f) / 100000;
        AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
        call.setVideo(mVideo);
        switch (action) {
            case Video.ACTION_SET_LASTPLAYPOS:
                if (leng > 1000 && pos > leng - 10000 && !isIncreasing) {
                    mWatched = true;
                    action2 = Video.ACTION_SET_WATCHED;
                    call.setWatched(mWatched);
                }
                call.setLastPlay(bookmark);
                call.setPosLastPlay(posBookmark);
                break;
            case Video.ACTION_SET_BOOKMARK:
                call.setBookmark(bookmark);
                call.setPosBookmark(posBookmark);
                break;
            default:
                Log.e(TAG, CLASS + " setBookmark invalid parameter:" + action, new Throwable());
                return;
        }
        posBookmark = -1;
        call.execute(action, action2);
        // Wait for the update to complete before returning. Hope it takes less than 100 ms
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
    }

    boolean canEnd() {
        if (mRecordid >= 0) {
                // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                        R.style.Theme_AppCompat_Dialog_Alert);
                builder
                        .setTitle(R.string.title_are_you_sure)
                        .setItems(R.array.prompt_stop_livetv,
                                (dialog, which) -> {
                                    // The 'which' argument contains the index position
                                    // of the selected item
                                    // 0 = don't stop, 1 = stop
                                    if (which == 1) {
                                        getActivity().finish();
                                    }
                                });
                builder.show();
                return false;
            }
        return true;
    }


    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    private void initializePlayer(boolean enableControls) {
        Log.i(TAG, CLASS + " Initializing Player for " + mVideo.title + " " + mVideo.videoUrl);
        mTrackSelector = new DefaultTrackSelector(getContext());
        MyRenderersFactory rFactory = new MyRenderersFactory(getContext());
        int extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
        if ("mediacodec".equals(prefAudio))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        else if ("ffmpeg".equals(prefAudio))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
        rFactory.setExtensionRendererMode(extMode);
        extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
        if ("mediacodec".equals(prefVideo))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
        else if ("ffmpeg".equals(prefVideo))
            extMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
        rFactory.setVideoExtensionRendererMode(extMode);
        rFactory.setEnableDecoderFallback(true);
        ExoPlayer.Builder builder = new ExoPlayer.Builder(getContext(),rFactory);
        builder.setTrackSelector(mTrackSelector);
        mPlayer = builder.build();

        mSubtitles = getActivity().findViewById(R.id.leanback_subtitles);
        if (mSubtitles != null) {
            mSubtitles.setFractionalTextSize
                    (SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * mSubtitleSize / 100.0f);
        }

        mPlayerEventListener = new PlayerEventListener();
        mPlayer.addListener(mPlayerEventListener);

        mPlayerAdapter = new LeanbackPlayerAdapter(getActivity(), mPlayer, UPDATE_DELAY);
        if (mPlaybackActionListener == null) {
            mPlaybackActionListener = new PlaybackActionListener(this, mPlaylist);
            int sampleOffsetUs = 1000 * Settings.getInt("pref_audio_sync", mVideo.playGroup);
            mPlaybackActionListener.sampleOffsetUs = sampleOffsetUs;
//            if (sampleOffsetUs != 0)
//                mAudioPause = true;
        }
        VideoPlayerGlue oldGlue = mPlayerGlue;
        mPlayerGlue = new VideoPlayerGlue(getActivity(), mPlayerAdapter,
                mPlaybackActionListener, mRecordid > 0);
        mPlayerGlue.setEnableControls(enableControls);
        if (oldGlue == null)
            mPlayerGlue.setAutoPlay("true".equals(Settings.getString("pref_autoplay",mVideo.playGroup)));
        else
            mPlayerGlue.setAutoPlay(oldGlue.getAutoPlay());
        mPlayerGlue.setHost(new VideoSupportFragmentGlueHost(this));
        hideControlsOverlay(false);
        play(mVideo);
        ArrayObjectAdapter mRowsAdapter = initializeRelatedVideosRow();
        setAdapter(mRowsAdapter);
        mPlayerGlue.setupSelectedListener();
    }

    private void audioFix(int millis, boolean setTracks) {
        // cancel prior audioFix
        if (audioFixTask !=  null) {
            audioFixTask.cancel(false);
            audioFixTask = null;
        }
        ScheduledExecutorService executor = MainFragment.getExecutor();
        if (executor != null && audioFixTask == null) {
            audioFixTask = executor.schedule(() -> getActivity().runOnUiThread(() -> {
                audioFixTask = null;
                if (mPlaybackActionListener == null)
                    return;
                // Enable subtitle if necessary
                int msgOn = 0;
                int msgOff = 0;
                if (setTracks && mCaptions > 0) {
                    TrackInfo tracks
                            = new TrackInfo(PlaybackFragment.this, C.TRACK_TYPE_TEXT);
                    if (mCaptions <= tracks.trackList.size()) {
                        mTextSelection = mCaptions - 1;
                        msgOn = R.string.msg_subtitle_on;
                        msgOff = R.string.msg_subtitle_off;
                    }
                    mCaptions = 0;  // Reset - this should only be used once pre playback
                }
                if (setTracks && mTextSelection != -2)
                    mTextSelection = trackSelector(C.TRACK_TYPE_TEXT, mTextSelection,
                            msgOn, msgOff, true, false);
                // change audio track if necessary
                if (setTracks && mAudioSelection != -2)
                    mAudioSelection = trackSelector(C.TRACK_TYPE_AUDIO, mAudioSelection,
                            0, 0, true, false);
                // This may not be needed with new Exoplayer release
                else if (mAudioPause) {
                    // disable and enable to fix audio sync
                    enableTrack(C.TRACK_TYPE_AUDIO, false);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                    if (mPlaybackActionListener == null)
                        return;
                    enableTrack(C.TRACK_TYPE_AUDIO, true);
                    if (mPlaybackActionListener.sampleOffsetUs != 0)
                        mPlaybackActionListener.setAudioSync();
                }
            }), millis, TimeUnit.MILLISECONDS);
        }
    }

    private void playWait(int delay, String msg) {
        ScheduledExecutorService executor = MainFragment.getExecutor();
        if (executor != null) {
            ScheduledFuture<?> sched;
            sched = executor.schedule(() -> getActivity().runOnUiThread(() -> {
                if (msg != null) {
                    if (mToast != null)
                        mToast.cancel();
                    mToast = Toast.makeText(getActivity(),
                            msg,
                            Toast.LENGTH_LONG);
                    mToast.show();
                }
                if (!playWhenPrepared) {
                    mPlayerGlue.playWhenPrepared();
                    playWhenPrepared = true;
                    // disable and enable audio to fix sync errors
                    audioFix(5000, true);
                }
            }), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void enableTrack(int trackType, boolean enable) {
        if (mTrackSelector == null)
            return;
        MappingTrackSelector.MappedTrackInfo mti = mTrackSelector.getCurrentMappedTrackInfo();
        if (mti == null)
            return;

        for (int rendIx = 0 ; rendIx < mti.getRendererCount(); rendIx ++) {
            if (mti.getRendererType(rendIx) == trackType) {
                mTrackSelector.setParameters(
                        mTrackSelector
                                .buildUponParameters()
                                .setRendererDisabled(rendIx, !enable)
                );
            }
        }
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mTrackSelector = null;
            mPlayerGlue.setPlayerClosed(true);
            mPlayerGlue = null;
            mPlayerAdapter = null;
            mPlaybackActionListener = null;
        }
    }

    // This is called with a null video to plat mVideo after table is filled
    // based on the tablesFilled flag.
    @OptIn(markerClass = UnstableApi.class)
    private void play(Video video) {

        if (video != null) {
            mVideo = video;
            tablesFilled = false;
            commBreakTable = new CommBreakTable();
            fillTables();
            return;
        }

        setPlaySettings(mVideo.playGroup);

        View view = getView();
        view.setBackgroundColor(mBgColor);

        mPlayerGlue.setTitle(mVideo.title);

        StringBuilder subtitle = new StringBuilder();

        // This is to mark unwatched when play starts - does not seem a good idea.
        // possible characters for watched - "👁" "⏿" "👀"
        //        int progflags = Integer.parseInt(mVideo.progflags);
        //        if ((progflags & mVideo.FL_WATCHED) != 0)
        //            markWatched(false);

        if (mVideo.season != null && mVideo.season.compareTo("0") > 0) {
            subtitle.append('S').append(mVideo.season).append('E').append(mVideo.episode)
                    .append(' ');
        }
        subtitle.append(mVideo.subtitle);
        mPlayerGlue.setSubtitle(subtitle);
        // This is needed to fix jkjsdevelop bad audio where audio track starts late
        if (mBookmark <= 0 && posBookmark <= 0)
            mBookmark = 100;
        prepareMediaForPlaying(Uri.parse(mVideo.videoUrl));
        // set desired playback speed
        PlaybackParameters parms = new PlaybackParameters(mSpeed);
        mPlayer.setPlaybackParameters(parms);
        // This makes future seeks faster.
        mPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        playWhenPrepared = false;
        audioFix(5000, true);
        mPlayerGlue.setPlaybackStartTime(System.currentTimeMillis());
    }

    private void startStatusMonitor() {
        if (monitorSched == null || monitorSched.isDone() || monitorSched.isCancelled()) {
            ScheduledExecutorService executor = MainFragment.getExecutor();
            monitorSched = executor.schedule(() -> {
                getFileLength(false);
                if (isSpeededUp()) {
                    Activity activity = getActivity();
                    activity.runOnUiThread(() -> {
                        if (mPlayerGlue.getCurrentPosition() > mPlayerGlue.myGetDuration() - 10000)
                            resetSpeed();
                    });
                }
            }, STATUS_MONITOR_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    private void startTablesReload() {
        if (tablesSched == null || tablesSched.isDone() || tablesSched.isCancelled()) {
            ScheduledExecutorService executor = MainFragment.getExecutor();
            tablesSched = executor.scheduleWithFixedDelay(() -> {
                fillTables();
            }, TABLES_MONITOR_INTERVAL, TABLES_MONITOR_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    private void setPlaySettings(String group) {
        // null and Default should be treated as equal
        // This is to reset settings if the next video is a different playgroup.
        // otherwise leave them as is. This way if any on-the-fly changes were made,
        // they are kept as long as the playgroup does not change
        if (group == null)
            group = "Default";
        if (currentPlayGroup.equals(group))
            return;
        mSkipFwd = 1000 * Settings.getInt("pref_skip_fwd",group);
        mSkipBack = 1000 * Settings.getInt("pref_skip_back",group);
        mJump = 60000 * Settings.getInt("pref_jump",group);
        mFrameMatch = "true".equals(Settings.getString("pref_framerate_match",group));
        mSubtitleSize =  Settings.getInt("pref_subtitle_size",group);
        mBgColor = Settings.getInt("pref_letterbox_color",group);
        if (mPlayerGlue != null)
            mPlayerGlue.setAutoPlay("true".equals(Settings.getString("pref_autoplay",group)));
        int sampleOffsetUs = 1000 * Settings.getInt("pref_audio_sync",group);
        if (mPlaybackActionListener != null) {
            mPlaybackActionListener.sampleOffsetUs = sampleOffsetUs;
//            if (sampleOffsetUs != 0)
//                mAudioPause = true;
        }
        mCaptions = Settings.getInt("pref_captions",group);
        mSpeed = (float)Settings.getInt("pref_speed",group) / 100.0f;
        possibleEmptyTrack = "true".equals(Settings.getString("pref_poss_empty",mVideo.playGroup));
        commBreakOption = Settings.getInt("pref_commskip");
        switch(Settings.getString("pref_updown",group)) {
            case "controls": optUpDown = CMD_CONTROLS; break;
            case "jump":     optUpDown = CMD_JUMP;     break;
            case "skipcom":  optUpDown = CMD_SKIPCOM;  break;
            default:         optUpDown = CMD_CONTROLS; break;
        }
        switch(Settings.getString("pref_leftright",group)) {
            case "rewff":    optLeftRight = CMD_REWFF;   break;
            case "jump":     optLeftRight = CMD_JUMP;   break;
            case "skipcom":  optLeftRight = CMD_SKIPCOM; break;
            default:         optLeftRight = CMD_REWFF;   break;
        }
        switch(Settings.getString("pref_rewff",group)) {
            case "rewff":    optRewFF = CMD_REWFF;   break;
            case "jump":     optRewFF = CMD_JUMP;   break;
            case "skipcom":  optRewFF = CMD_SKIPCOM; break;
            default:         optRewFF = CMD_REWFF;   break;
        }
        currentPlayGroup = group;
    }

    private void setupRefreshRate() {
        // Setup video frame rate
        float ratio = 1.0f;
        float desiredRefreshRate = 1.0f;
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        float refreshRate = display.getRefreshRate();
        if (frameRate > 1.0f) {
            if (frameRate < 35.0f)
                // assume interlaced, prefer double rate
                desiredRefreshRate = frameRate * 2;
            else
                desiredRefreshRate = frameRate;
        }
        // try to get ratio as close as possible to 1.0.
        int setId = -1;
        float setRate = 0.0f;
        if (desiredRefreshRate > 1.0f) {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                float matchedRate = 0.0f;
                float halfRate = 0.0f;
                float dblRate = 0.0f;
                int matchedId = -1;
                int halfId = -1;
                int dblId = -1;
                float diffM = 999.0f;
                float diffH = 999.0f;
                float diffD = 999.0f;
                Display.Mode[] modes = display.getSupportedModes();
                Display.Mode currMode = display.getMode();
                for (Display.Mode mode : modes) {
                    if (mode.getPhysicalHeight() != currMode.getPhysicalHeight()
                            || mode.getPhysicalWidth() != currMode.getPhysicalWidth())
                        continue;
                    float rate = mode.getRefreshRate();
                    ratio = rate / desiredRefreshRate;
                    float diff = Math.abs(ratio - 1.0f);
                    if (diff < diffM) {
                        matchedId = mode.getModeId();
                        matchedRate = rate;
                        diffM = diff;
                    }
                    diff = Math.abs(ratio - 0.5f);
                    if (diff < diffH) {
                        halfId = mode.getModeId();
                        halfRate = rate;
                        diffH = diff;
                    }
                    diff = Math.abs(ratio - 2.0f);
                    if (diff < diffD) {
                        dblId = mode.getModeId();
                        dblRate = rate;
                        diffD = diff;
                    }
                }
                if (matchedId > -1 && diffM < 0.005f) {
                    setId = matchedId;
                    setRate = matchedRate;
                }
                else if (dblId > -1 && diffD < 0.005f) {
                    setId = dblId;
                    setRate = dblRate;
                }
                else if (halfId > -1 && diffH < 0.005f) {
                    setId = halfId;
                    setRate = halfRate;
                }

                String msg = null;
                int displayMode = setId;
                float displayRate = setRate;

                if (displayMode == -1)
                    msg = getActivity().getString(R.string.msg_no_good_refresh,frameRate);

                else if (displayMode != currMode.getModeId())
                    msg = getContext().getString(R.string.msg_setting_framerate, displayRate);

                if (displayMode != -1 && displayMode != currMode.getModeId()) {
                    displayMode = setId;
                    displayRate = setRate;
                    Window window = getActivity().getWindow();
                    WindowManager.LayoutParams params = window.getAttributes();
                    params.preferredDisplayModeId = displayMode;
                    window.setAttributes(params);
                    // 3 seconds delay to allow mode switch
                    playWait(3000, msg);
                }
                else {
                    if (msg != null) {
                        if (mToast != null)
                            mToast.cancel();
                        mToast = Toast.makeText(getActivity(),
                                msg,
                                Toast.LENGTH_LONG);
                        mToast.show();
                    }
                    if (!playWhenPrepared) {
                        mPlayerGlue.playWhenPrepared();
                        playWhenPrepared = true;
                        // disable and enable audio to fix sync errors
                        audioFix(5000, true);
                    }
                }
            }

                // This code would support android sdk < 23, however it is not tested
                // and few systems use sdk < 23
//            else {
//                float matchedRate = 0.0f;
//                float halfRate = 0.0f;
//                float dblRate = 0.0f;
//                float diffM = 999.0f;
//                float diffH = 999.0f;
//                float diffD = 999.0f;
//                float[] rates = display.getSupportedRefreshRates();
//                for (float rate : rates) {
//                    ratio = rate / desiredRefreshRate;
//
//                    float diff = Math.abs(ratio - 1.0f);
//                    if (diff < diffM) {
//                        matchedRate = rate;
//                        diffM = diff;
//                    }
//                    diff = Math.abs(ratio - 0.5f);
//                    if (diff < diffH) {
//                        halfRate = rate;
//                        diffH = diff;
//                    }
//                    diff = Math.abs(ratio - 2.0f);
//                    if (diff < diffD) {
//                        dblRate = rate;
//                        diffD = diff;
//                    }
//                }
//                float setRate = 0.0f;
//                if (matchedRate > 0.0f && diffM < 0.005f)
//                    setRate = matchedRate;
//                else if (dblRate > 0.0f && diffD < 0.005f)
//                    setRate = dblRate;
//                else if (halfRate > 0.0f && diffH < 0.005f)
//                    setRate = halfRate;
//
//                String msg = null;
//                if (setRate <= 1.0f)
//                    msg = getActivity().getString(R.string.msg_no_good_refresh,desiredRefreshRate);
//                else if (setRate != refreshRate) {
//                    params.preferredRefreshRate = setRate;
//                    msg = getActivity().getString(R.string.msg_setting_framerate, setRate);
//                }
//                if (msg != null) {
//                    if (mToast != null)
//                        mToast.cancel();
//                    mToast = Toast.makeText(getActivity(),
//                            msg,
//                            Toast.LENGTH_LONG);
//                    mToast.show();
//                }
//                if (setRate != refreshRate) {
//                    window.setAttributes(params);
//                }
//            }
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareMediaForPlaying(Uri mediaSourceUri) {
        mFileLength = -1;
        getFileLength(false);
        String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
        mDsFactory = new MythHttpDataSource.Factory(userAgent, this);
        MediaItem item = MediaItem.fromUri(mediaSourceUri);
        MyExtractorsFactory extFactory = new MyExtractorsFactory();
//        ProgressiveMediaSource.Factory pmf = new ProgressiveMediaSource.Factory
        DefaultMediaSourceFactory pmf = new DefaultMediaSourceFactory
                (mDsFactory,
                        extFactory);
        mMediaSource = (ProgressiveMediaSource) pmf.createMediaSource(item);
        mMediaSource.setPossibleEmptyTrack(possibleEmptyTrack);
        if (mBookmark < 0)
            mBookmark = 0;
        mPlayer.setMediaSource(mMediaSource, mBookmark);
        mPlayer.prepare();
        // Get file length again to see if it is increasing
        getFileLength(true);
    }


    private ArrayObjectAdapter initializeRelatedVideosRow() {
        /*
         * To add a new row to the mPlayerAdapter and not lose the controls row that is provided by the
         * glue, we need to compose a new row with the controls row and our related videos row.
         *
         * We start by creating a new {@link ClassPresenterSelector}. Then add the controls row from
         * the media player glue, then add the related videos row.
         */
        ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        presenterSelector.addClassPresenter(
                mPlayerGlue.getControlsRow().getClass(), mPlayerGlue.getPlaybackRowPresenter());
        presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());

        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(presenterSelector);

        rowsAdapter.add(mPlayerGlue.getControlsRow());

        HeaderItem header = new HeaderItem(getString(R.string.related_movies));
        ListRow row = new ListRow(header, mVideoCursorAdapter);
        rowsAdapter.add(row);

        setOnItemViewClickedListener(new ItemViewClickedListener());

        return rowsAdapter;
    }

    private CursorObjectAdapter setupRelatedVideosCursor() {
        CardPresenter p = new CardPresenter(this);
        p.setType(CardPresenter.TYPE_PLAYBACK);
        CursorObjectAdapter videoCursorAdapter = new CursorObjectAdapter(p);
        videoCursorAdapter.setMapper(new VideoCursorMapper());

        Bundle args = new Bundle();
        // related videos only display directory list in this case
        // otherwise treated as tv series
        if (mVideo.rectype == RECTYPE_VIDEO
                && (mVideo.season == null || mVideo.season.equals("0"))
                && (mVideo.episode == null || mVideo.episode.equals("0"))
                && mVideo.airdate == null)
            args.putInt(COLUMN_RECTYPE, mVideo.rectype);
        args.putString(COLUMN_TITLE, mVideo.title);
        args.putString(COLUMN_TITLEMATCH, mVideo.titlematch);
        args.putString(COLUMN_RECGROUP, mVideo.recGroup);
        args.putString(COLUMN_FILENAME, mVideo.filename);
        args.putString(COLUMN_VIDEO_URL, mVideo.videoUrl);

        LoaderManager manager = LoaderManager.getInstance(this);
        manager.initLoader(VideoLoaderCallbacks.RELATED_VIDEOS_LOADER, args, mVideoLoaderCallbacks);

        return videoCursorAdapter;
    }

    public void skipToNext() {
        // We do not support this for LiveTV
        if (mRecordid >= 0)
            return;
        Video v = mPlaylist.next();
        if (v != null) {
            setBookmark(Video.ACTION_SET_LASTPLAYPOS);
            mBookmark = 0;
            mPlayer.stop();
            mPlayer.clearMediaItems();
            saveLiveRec = false;
            mVideo = v;
            initializePlayer(true);
        }

    }

    public void skipToPrevious() {
        // We do not support this for LiveTV
        if (mRecordid >= 0)
            return;
        // If more than 1 minute past beginning, seek to start
        // otherwise got to previous video
        if (mPlayerGlue.getCurrentPosition() > 60000) {
            seekTo(0);
            return;
        }
        Video v = mPlaylist.previous();
        if (v != null) {
            setBookmark(Video.ACTION_SET_LASTPLAYPOS);
            mBookmark = 0;
            mVideo = v;
        }
        play(mVideo);
    }

    public void ffRew(int direction) {
        tickle();
        if (direction < 0)
            rewind();
        else if (direction > 0)
            fastForward();
    }

    /** Skips backwards 1 minute. */
    public void rewind() {
        moveBackward(mSkipBack);
    }

    /**
     * Skip backward by specified amount
     * @param millis Milliseconds to skip
     */
    public void moveBackward(int millis) {
        long newPosition = mPlayerGlue.getCurrentPosition() - millis;
        newPosition = (newPosition < 0) ? 0 : newPosition;
        seekTo(newPosition);
    }


    /** Skips forward 1 minute. */
    public void fastForward() {
        moveForward(mSkipFwd);
    }

    /** Jumps backwards 5 min. */
    public void jumpBack() {
        moveBackward(mJump);
    }

    /** Jumps forward 5 min. */
    public void jumpForward() {
        moveForward(mJump);
    }

    private void moveForward(int millis) {
        long newPosition = mPlayerGlue.getCurrentPosition() + millis;
        newPosition = (newPosition < 0) ? 0 : newPosition;
        seekTo(newPosition);
    }
    // set position to -1 for a reset with no seek.
    // set doReset true to refresh file size information.
    void seekTo(long position) {
        boolean doReset = false;
        long newPosition;
        if (mPlayerGlue.myGetDuration() > mPlayerGlue.getDuration()
                && position > mPlayerGlue.getDuration())
            doReset = true;
        if (position == -1)
            newPosition = mPlayerGlue.getCurrentPosition();
        else
            newPosition = position;
        if (newPosition > mPlayerGlue.myGetDuration() - 5000) {
            newPosition = mPlayerGlue.myGetDuration() - 5000;
            if (isSpeededUp())
                resetSpeed();
        }
        if (!doReset) {
            if (position != -1)
                mPlayerGlue.seekTo(newPosition);
        }
        else {
            mBookmark = newPosition;
            mPlayer.stop();
            play(mVideo);
        }
    }

    // trackSelection = current selection. -1 = disabled, -2 = leave as is
    // disable = true : Include disabled in the rotation
    // doChange = true : select a new track, false = leave same track
    // Return = new track selection.

    @OptIn(markerClass = UnstableApi.class)
    int trackSelector(int trackType, int trackSelection, int msgOn,
            int msgOff, boolean disable, boolean doChange) {
        TrackInfo tracks = new TrackInfo(this, trackType);
        StringBuilder msg = new StringBuilder();
        if (doChange) {
            if (trackSelection == -2)
                trackSelection = -1;
            if (tracks.trackList.size() == 0)
                trackSelection = -1;
            else if (++trackSelection >= tracks.trackList.size()) {
                if (disable)
                    trackSelection = -1;
                else
                    trackSelection = 0;
            }
        }
        if (trackSelection >= 0) {
            TrackEntry entry = tracks.trackList.get(trackSelection);
            if (trackType != C.TRACK_TYPE_TEXT || "application/x-media3-cues".equals(entry.format.sampleMimeType)
                      || "application/cea-608".equals(entry.format.sampleMimeType)
                      || "application/cea-708".equals(entry.format.sampleMimeType)) {
                mPlayer.setTrackSelectionParameters(
                        mPlayer.getTrackSelectionParameters()
                                .buildUpon()
                                .setTrackTypeDisabled(trackType,false)
                                .setOverrideForType(
                                    new TrackSelectionOverride(entry.tg, entry.ixTrack)
                                )
                                .build()
                );
                String language = entry.format.language;
                if (language == null) {
                    if (MimeTypes.APPLICATION_CEA608.equals(entry.format.sampleMimeType))
                        language = trackSelection + 1 + " " + getContext().getString(R.string.msg_subtitle_cc);
                    else
                        language = String.valueOf(trackSelection + 1);
                } else {
                    Locale locale = new Locale(language);
                    String langDesc = locale.getDisplayLanguage();
                    language = trackSelection + 1 + " " + langDesc;
                }
                if (msgOn > 0)
                    msg.append(getActivity().getString(msgOn, language));
            } else {
                msg.append(getActivity().getString(R.string.msg_subtitle_notsupp,
                        entry.format.sampleMimeType));
            }
        } else if (trackSelection == -1){
            mPlayer.setTrackSelectionParameters(
                    mPlayer.getTrackSelectionParameters()
                            .buildUpon()
                            .setTrackTypeDisabled(trackType,true)
                            .build()
            );
            if (msgOff > 0)
                msg.append(getActivity().getString(msgOff));
        }

        if (msg.length() > 0) {
            if (mToast != null)
                mToast.cancel();
            mToast = Toast.makeText(getActivity(),
                    msg, Toast.LENGTH_LONG);
            mToast.show();
        }
        // For some reason changing the subtitle pauses playback. This fixes that.
        // That seems to be No longer necessary...
//        if (trackType == C.TRACK_TYPE_TEXT && isPlaying) {
//            mPlayerGlue.pause();
//            mPlayerGlue.play();
//        }
        return trackSelection;
    }

    public void actionSelected(Action action) {
        View view = getView();
        TextView text = view.findViewById(R.id.button_selected);
        if (text == null)
            return;
        if (action != null) {
            mCurrentAction = action;
            text.setText(action.getLabel1());
            mFocusView = view.findFocus();
        } else {
            // Called with null when a key is pressed. Will clear help message
            // if we are no longer on the control.
            View newView = view.findFocus();
            if (newView != mFocusView) {
                text.setText("");
                mFocusView = newView;
                mCurrentAction = null;
            }
            // This is to handle buttons that change title, for example
            // play / pause.
            else if (mCurrentAction != null)
                text.setText(mCurrentAction.getLabel1());
        }
    }

    boolean onControlsUp() {
        ArrayObjectAdapter primaryActionsAdapter
            = (ArrayObjectAdapter) mPlayerGlue.getControlsRow().getPrimaryActionsAdapter();
        if (primaryActionsAdapter.indexOf(mCurrentAction) >= 0) {
            hideControlsOverlay(true);
            return true;
        }
        return false;
    }

    public void onRecord() {
        saveLiveRec = true;
        if (mVideo.recordedid != null && BackendCache.getInstance().canUpdateRecGroup) {
            AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
            mVideo.recGroup = "Default";
            call.setVideo(mVideo);
            call.execute(Video.ACTION_UPDATE_RECGROUP);
            if (mToast != null)
                mToast.cancel();
            mToast = Toast.makeText(getActivity(),
                    getActivity().getString(R.string.msg_save_record),
                    Toast.LENGTH_LONG);
            mToast.show();

        }
    }

    public void markWatched(boolean watched) {
        mWatched = watched;
        AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
        call.setVideo(mVideo);
        call.setWatched(mWatched);
        call.execute(Video.ACTION_SET_WATCHED);
    }

    /**
     * Get the file length at this time
     * @param multiCheck indicates to check up to 5 times to see if it changed
     */
    public void getFileLength(boolean multiCheck) {
        long priorFileLeng = -1;
        if (multiCheck)
            priorFileLeng = mFileLength;
        AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
        call.setVideo(mVideo);
        call.setmValue(priorFileLeng);
        call.execute(Video.ACTION_FILELENGTH);
    }

    private void fillTables() {
        AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
        call.setVideo(mVideo);
        call.setCommBreakTable(commBreakTable);
        call.execute(Video.ACTION_CUTLIST_LOAD, Video.ACTION_COMMBREAK_LOAD);
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        if (getContext() == null)
            return;
        int [] tasks = taskRunner.getTasks();
        switch (tasks[0]) {
            case Video.ACTION_FILELENGTH:
                long fileLength = taskRunner.getFileLength();
                // If file has got bigger, resume with bigger file
                Log.i(TAG, CLASS + " File Length changed from " + mFileLength + " to " + fileLength);
                if (fileLength == -1) {
                    mPlayerEventListener.handlePlayerError(null, R.string.pberror_file_length_fail);
                }
                if (mFileLength > -1 && fileLength > mFileLength)
                    isIncreasing = true;
                else
                    isIncreasing = false;
                mPlayerGlue.setIncreasing(isIncreasing);
                mPlayerGlue.onUpdateDuration();
                mFileLength = fileLength;
                // Now check for a subsequent show if in LiveTV mode
                // or next show in autoplay mode
                if (isIncreasing) {
                    startStatusMonitor();
                    startTablesReload();
                }
                if (mPlayerGlue.isPlayCompleted())
                    checkNextShow();
                break;
            case Video.ACTION_SET_BOOKMARK:
                if (mToast != null)
                    mToast.cancel();
                mToast = Toast.makeText(getActivity(),
                        getActivity().getString(R.string.msg_bookmark_set),
                        Toast.LENGTH_LONG);
                mToast.show();
                break;
            case Video.ACTION_CUTLIST_LOAD:
                if (commBreakTable.frameratex1000 > 1)
                    frameRate = (float) (commBreakTable.frameratex1000 / 1000.);
                if (commBreakTable.entries.length > 0)
                    mPlaybackActionListener.setNextCommBreak(-1);
                if (!tablesFilled) {
                    tablesFilled = true;
                    play(null);
                }
                break;
            case Video.ACTION_LIVETV:
                mNextRecordid = taskRunner.getRecordId();
                nextEndTime = taskRunner.getEndTime();
                break;
            case Video.ACTION_WAIT_RECORDING:
                setProgressBar(false);
                Video nextVid = taskRunner.getVideo();
                Context context = getContext();
                if (nextVid == null || context == null) {
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
                    Video video = new Video.VideoBuilder()
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
                hideControlsOverlay(false);
                mRecordid = mNextRecordid;
                mNextRecordid = 0;
                endTime = nextEndTime;
                nextEndTime = null;
                mBookmark = 0;
                mPlayer.stop();
                mPlayer.clearMediaItems();
                saveLiveRec = false;
                mVideo = nextVid;
                initializePlayer(true);
                scheduleNext();
                break;
        }
    }

    // check for a subsequent show if in LiveTV mode
    // or next show in autoplay mode.
    void checkNextShow() {
        if (mRecordid > 0 && mNextRecordid > 0) {
            setProgressBar(true);
            AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
            call.setRecordId(mNextRecordid);
            call.execute(Video.ACTION_WAIT_RECORDING);
            // delete record rule for completed show
            call = new AsyncBackendCall(getActivity(), null);
            call.setRecordedId(Integer.parseInt(mVideo.recordedid));
            call.setRecordId(mRecordid);
            call.execute(
                    Video.ACTION_PAUSE,
                    Video.ACTION_REMOVE_RECORD_RULE);
        }
        else if (mPlayerGlue.getAutoPlay()) {
            tickle();
            skipToNext();
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


    public void setDataSource(MythHttpDataSource mDataSource) {
        this.mDataSource = mDataSource;
    }

    public void tickle(boolean autohide, boolean showActions) {
        mPlayerGlue.setActions(showActions);
        setControlsOverlayAutoHideEnabled(false);
        showControlsOverlay(true);
        if (autohide)
            setControlsOverlayAutoHideEnabled(true);
    }

    public void setActions(boolean showActions) {
        mPlayerGlue.setActions(showActions);
    }


    @Override
    // Overridden because the default tickle disables the fade timer.
    public void tickle() {
        tickle(false, true);
    }

    public void resetSpeed() {
        mSpeed = SPEED_START_VALUE;
        PlaybackParameters parms = new PlaybackParameters(mSpeed);
        mPlayer.setPlaybackParameters(parms);
        mPlaybackActionListener.dismissDialog();
    }

    public boolean isSpeededUp() {
        return mSpeed > SPEED_START_VALUE;
    }

    public PlaybackActionListener getPlaybackActionListener() {
        return mPlaybackActionListener;
    }


    private void fixSpeed() {
        long duration = mPlayerGlue.myGetDuration();
        if (duration > 0) {
            // If we cannot change speed, switch to ffmpeg audio.
            if (!"ffmpeg".equals(prefAudio)) {
                prefAudio = "ffmpeg";
                if (mBookmark == 0)
                    mBookmark = mPlayerGlue.getCurrentPosition();
                mPlayer.stop();
                mPlayer.clearMediaItems();
                initializePlayer(true);
                return;
            }
            mSpeed = SPEED_START_VALUE;
            if (mToast != null)
                mToast.cancel();
            mToast = Toast.makeText(getActivity(),
                    getActivity().getString(R.string.msg_unable_speed),
                    Toast.LENGTH_LONG);
            mToast.show();
        }
    }

    static class TrackInfo {
        ArrayList <Integer> renderList;
        ArrayList <TrackEntry> trackList;

        @OptIn(markerClass = UnstableApi.class)
        TrackInfo(PlaybackFragment pb, int trackType) {
            trackList = new ArrayList<>();
            renderList = new ArrayList<>();
            MappingTrackSelector.MappedTrackInfo mti = pb.mTrackSelector.getCurrentMappedTrackInfo();
            if (mti == null)
                return;
            int ccNum=0;
            for (int ixRenderer = 0 ; ixRenderer < mti.getRendererCount(); ixRenderer ++) {
                Log.i(TAG, CLASS + " Renderer "+ixRenderer+": "+mti.getRendererName(ixRenderer)
                        +" type: "+mti.getRendererType(ixRenderer));
                if (mti.getRendererType(ixRenderer) == trackType) {
                    renderList.add(ixRenderer);
                    TrackGroupArray tga = mti.getTrackGroups(ixRenderer);
                    if (tga != null) {
                        TrackGroup tg = null;
                        for (int ixTrackGroup = 0 ; ixTrackGroup < tga.length; ixTrackGroup++) {
                            tg = tga.get(ixTrackGroup);
                            if (tg != null) {
                                Log.i(TAG, CLASS + " Trk Group "+ixTrackGroup+": "+tg.id
                                        +" type: "+tg.type);
                                for (int ixTrack = 0; ixTrack < tg.length; ixTrack++) {
                                    Format format = tg.getFormat(ixTrack);
                                    String description = format.language;
                                    if (description == null) {
                                        if (MimeTypes.APPLICATION_CEA608.equals(format.sampleMimeType))
                                            description = ++ccNum + " "
                                                    + pb.getContext().getString(R.string.msg_subtitle_cc);
                                        else
                                            description = String.valueOf(++ccNum);
                                    }
                                    else {
                                        Locale locale = new Locale(description);
                                        String langDesc = locale.getDisplayLanguage();
                                        description = ++ccNum + " " + langDesc;
                                    }
                                    Log.i(TAG, CLASS + " Track "+ixTrack+": "+description
                                            +" mimetype: "+format.sampleMimeType+" codecs: "+format.codecs);

                                    trackList.add (new TrackEntry
                                        (ixRenderer,ixTrackGroup,tg, ixTrack,format,description));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static class TrackEntry {
        int ixRenderer;
        int ixTrackGroup;
        int ixTrack;
        TrackGroup tg;
        Format format;
        String description;
        TrackEntry(int ixRenderer, int ixTrackGroup, TrackGroup tg, int ixTrack, Format format, String description) {
            this.ixRenderer = ixRenderer;
            this.ixTrackGroup = ixTrackGroup;
            this.ixTrack = ixTrack;
            this.format = format;
            this.description = description;
            this.tg = tg;
        }
    }

    /**
     * Opens the video details page when a related video has been clicked.
     */
    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {

            if (item instanceof Video) {
                Video video = (Video) item;

                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(PlaybackActivity.VIDEO, video);

                Bundle bundle =
                        ActivityOptionsCompat.makeSceneTransitionAnimation(
                                getActivity(),
                                ((ImageCardView) itemViewHolder.view).getMainImageView(),
                                PlaybackActivity.SHARED_ELEMENT_NAME)
                                .toBundle();
                getActivity().startActivity(intent, bundle);
            }
        }
    }

    /**
     * Loads a playlist with videos from a cursor and also updates the related videos cursor.
     */
    protected class VideoLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        static final int RELATED_VIDEOS_LOADER = 1;
        static final int QUEUE_VIDEOS_LOADER = 2;

        private final VideoCursorMapper mVideoCursorMapper = new VideoCursorMapper();

        private final Playlist playlist;

        private VideoLoaderCallbacks(Playlist playlist) {
            this.playlist = playlist;
        }

        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {

            boolean showDeleted = "true".equals(Settings.getString("pref_related_deleted"));
            // Always show watched videos because the one you are watching could become watched
            // Also when rewatching a series you want all episodes not just the unwatched.
//            boolean showWatched = "true".equals(Settings.getString("pref_related_watched"));
            String seq = Settings.getString("pref_seq");
            String ascdesc = Settings.getString("pref_seq_ascdesc");

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
//                if (!showWatched)
//                    where.append(" and ")
//                            .append(COLUMN_PROGFLAGS)
//                            .append(" & ").append(Video.FL_WATCHED)
//                            .append(" == 0 ");
                where.append(" or ")
                        .append(COLUMN_VIDEO_URL)
                        .append(" = ? ");
                return new CursorLoader(
                        getActivity(),
                        CONTENT_URI,
                        null,
                        where.toString(),
                        new String[]{dirname, subdirname,
                                args.getString(COLUMN_VIDEO_URL)},
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
//                if (!showWatched)
//                    where.append(" and ")
//                            .append(COLUMN_PROGFLAGS)
//                            .append(" & ").append(Video.FL_WATCHED)
//                            .append(" == 0 ");
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
                selectionArgs = new String[]{category,
                        args.getString(COLUMN_VIDEO_URL)};
                return new CursorLoader(
                        getActivity(),
                        CONTENT_URI,
                        null,
                        where.toString(),
                        selectionArgs,
                        orderby.toString());
            }
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            int id = loader.getId();
            if (id == QUEUE_VIDEOS_LOADER) {
                // Do not update the list when a video is marked watched.
                // So only load the play list once.
                if (playlist.size() > 0)
                    return;
                playlist.clear();
                do {
                    Video video = (Video) mVideoCursorMapper.convert(cursor);

                    // Set the current position to the selected video.
                    if (video.videoUrl != null && video.videoUrl.equals(mVideo.videoUrl)) {
                        playlist.setCurrentPosition(playlist.size());
                    }

                    playlist.add(video);

                } while (cursor.moveToNext());
            } else if (id == RELATED_VIDEOS_LOADER) {
                mVideoCursorAdapter.changeCursor(cursor);
            }
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            mVideoCursorAdapter.changeCursor(null);
        }
    }

    // Allowed fps values. 0.0f means unknown
    private static final float[] FPS_VALUES = {
            0.0f,  59.94f, 50.0f, 29.97f, 25.0f, 23.976f, 0.0f};
    // Frame intervals in microsecs. Values below each of these are taken
    // as being for the corresponding fps value above.
    private static final long[] FPS_INTERVALS = {
            15000, 18000,  30000, 35000,  40800, 43000, Long.MAX_VALUE};

    @UnstableApi
    class PlayerEventListener implements Player.Listener {
        private int mDialogStatus = 0;
        private static final int DIALOG_NONE   = 0;
        private static final int DIALOG_ACTIVE = 1;
        private static final int DIALOG_EXIT   = 2;
        private static final int DIALOG_RETRY  = 3;
        private long mTimeLastError = 0;

        @Override
        public void onPositionDiscontinuity( Player.PositionInfo oldPosition,
                  Player.PositionInfo newPosition,int reason) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                // disable and enable to fix audio sync
                audioFix(5000, true);
            }
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY && !playWhenPrepared) {
                if (commBreakTable.frameratex1000 > 1)
                    frameRate = (float) (commBreakTable.frameratex1000 / 1000.);
                if (frameRate < 0.0f) {
                    SampleQueue[] sampleQueues = mMediaSource.getSampleQueues();
                    for (SampleQueue sampleQueue : sampleQueues) {
                        if (MimeTypes.isVideo(sampleQueue.getUpstreamFormat().sampleMimeType)) {
                            long[] timesUs = sampleQueue.getTimesUs();
                            int leng = sampleQueue.getWriteIndex();
                            long[] sortedTimes = Arrays.copyOf(timesUs, leng);
                            Arrays.sort(sortedTimes);

                            // cater for short array
                            if (sortedTimes.length < 2)
                                continue;

                            int start = 30;
                            if (sortedTimes.length < 50)
                                start = 0;

                            // Another way to get interval ? Maybe better ?
                            long tottimes = sortedTimes[sortedTimes.length - 1]
                                    - sortedTimes[start];
                            long avtime = tottimes / (sortedTimes.length - start - 1);
                            float calcFrameRate = 1000000.0f / (float) avtime;

                            int[] counters = new int[FPS_INTERVALS.length];
                            for (int timeix = 1; timeix < sortedTimes.length; timeix++) {
                                if (sortedTimes[timeix] == 0)
                                    continue;
                                for (int ivlix = 0; ivlix < FPS_INTERVALS.length; ivlix++) {
                                    if (sortedTimes[timeix] - sortedTimes[timeix - 1]
                                            < FPS_INTERVALS[ivlix]) {
                                        counters[ivlix]++;
                                        break;
                                    }
                                }
                            }
                            int maxix = 0;
                            int maxcount = 0;
                            // ignore the first and last which are "unknown".
                            for (int ivlix = 1; ivlix < FPS_INTERVALS.length - 1; ivlix++) {
                                if (counters[ivlix] > maxcount) {
                                    maxcount = counters[ivlix];
                                    maxix = ivlix;
                                }
                            }
                            // if there is a mixture of 29.97 and 23.976, then select 29.97
                            if (counters[3] > 5 && counters[5] > 5)
                                maxix = 3;

                            if (maxcount > 5)
                                frameRate = FPS_VALUES[maxix];
                            else
                                frameRate = 0.0f;

                            // Get estimated framerate for cases where timestamps are messed up
                            if (frameRate < 1.0f && sortedTimes.length > 0) {
                                long interval = sortedTimes[sortedTimes.length-1]
                                        - sortedTimes[0];
                                int frames = sortedTimes.length - 1;
                                if (frames > 0 && interval > 0)
                                    frameRate = (float) frames * 1_000_000.0f / (float)interval;
                            }

                            Log.i(TAG, CLASS + " Frame rate Calculated:" + calcFrameRate + ", Normalized:"+frameRate);
                            // Special Case for 60fps recordings
                            if (calcFrameRate > 59.95 && calcFrameRate < 60.05) {
                                frameRate = calcFrameRate;
                                Log.i(TAG, CLASS + " Using Frame Rate:" + frameRate);
                            }
                            break;
                        }
                    }
                }
                commBreakTable.frameratex1000 = (long)(frameRate * 1000.0f);
                if (mBookmark <= 0 && posBookmark >= 0 && frameRate > 0.0f) {
                    mBookmark = posBookmark * 100000 / (long) (frameRate * 100.0f);
                    posBookmark = -1;
                }
                if (mBookmark > 0) {
                    mPlayerGlue.seekTo(mBookmark);
                    mBookmark = 0;
                }
                if (mFrameMatch && frameRate > 1.0f)
                    setupRefreshRate();
                else {
                    mPlayerGlue.playWhenPrepared();
                    playWhenPrepared = true;
                    audioFix(5000, true);
                }
            } // end of if (state == Player.STATE_READY)
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            int stretchPerc = Math.round(playbackParameters.speed * 100.0f);
            StringBuilder msg = new StringBuilder(getActivity().getString(R.string.playback_speed))
                    .append(" ").append(stretchPerc).append("%");
            if (mToast != null)
                mToast.cancel();
            mToast = Toast.makeText(getActivity(),
                    msg, Toast.LENGTH_LONG);
            mToast.show();
            if (playbackParameters.speed == 1.0f && mSpeed != 1.0f)
                fixSpeed();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException ex) {
            handlePlayerError(ex, -1);
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            // use mPlayerGlue.isPlaying instead of the value
            // passed in because this is called with false on a skip
            if (mPlayerGlue.isPlaying())
                // Prevent screen saver during playback
                getActivity().getWindow()
                    .addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else {
                // Enable screen saver during pause
                getActivity().getWindow()
                        .clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        @Override
        public void onCues(@NonNull List<Cue> cues) {
            if (mSubtitles != null)
                mSubtitles.setCues(cues);
        }

        private void handlePlayerError(Exception ex, int msgNum) {
            Throwable cause = null;
            if (ex != null)
                Log.e(TAG, CLASS + " Player Error " + mVideo.title + " " + mVideo.videoUrl, ex);
            long now = System.currentTimeMillis();
            int recommendation = 0;
            boolean setPossibleEmptyTrack = false;
            if (ex != null && ex instanceof ExoPlaybackException) {
                ExoPlaybackException error = (ExoPlaybackException)ex;
                switch (error.type) {
                    case ExoPlaybackException.TYPE_REMOTE:
                        msgNum = R.string.pberror_remote;
                        cause = null;
                        break;
                    case ExoPlaybackException.TYPE_RENDERER:
                        msgNum = R.string.pberror_renderer;
                        cause = error.getRendererException();
                        // handle error caused by selecting an unsupported audio track
                        if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                            String mimeType = ((MediaCodecRenderer.DecoderInitializationException) cause).mimeType;
                            if (mimeType != null && mimeType.startsWith("audio")) {
                                recommendation = R.string.pberror_recommend_audio;
                            }
                        }
                        break;
                    case ExoPlaybackException.TYPE_SOURCE:
                        msgNum = R.string.pberror_source;
                        cause = error.getSourceException();
                        if (cause != null && cause.getMessage().startsWith("Unexpected ArrayIndexOutOfBoundsException")) {
                            msgNum = R.string.pberror_extractor_array;
                            break;
                        }
                        else
                            setPossibleEmptyTrack = true;
                        break;
                    case ExoPlaybackException.TYPE_UNEXPECTED:
                        msgNum = R.string.pberror_unexpected;
                        cause = error.getUnexpectedException();
                        if (mPlayerGlue.getSavedCurrentPosition() < 200
                            && "Playback stuck buffering and not loading".equals(cause.getMessage()))
                            setPossibleEmptyTrack = true;
                        // this error comes from fire stick 4k when selecting an mpeg level l2 audio track
                        if ("Multiple renderer media clocks enabled.".equals(cause.getMessage()))
                            recommendation = R.string.pberror_recommend_ffmpeg;
                        break;
                    default:
                        msgNum = R.string.pberror_default;
                        cause = null;
                        break;
                }
            }

            Context context = getContext();
            if (context != null) {
                StringBuilder msg = new StringBuilder();
                if (msgNum > -1)
                    msg.append(context.getString(msgNum));
                if (ex != null)
                    msg.append("\n").append(ex.getMessage());
                String alertMsg = msg.toString();
                if (cause != null)
                    msg.append("\n").append(cause.getMessage());
                Log.e(TAG, CLASS + " Player Error " + msg);
                // if we are near the start or end
                long currPos = mPlayerGlue.getSavedCurrentPosition();
                long duration = mPlayerGlue.getSavedDuration();
                boolean failAtStart = duration <= 0 || currPos <= 0;
                boolean failAtEnd = !failAtStart && Math.abs(duration - currPos) < 10000;
                if (mDialogStatus == DIALOG_NONE) {
                    // If there has been over 30 seconds since last error report
                    // try to recover from error by playing on.
                    if (failAtEnd
                        || mTimeLastError < now - 30000 ) {
                        if ("true".equals(Settings.getString("pref_error_toast"))) {
                            if (mToast != null)
                                mToast.cancel();
                            mToast = Toast.makeText(getActivity(),
                                    getActivity().getString(msgNum),
                                    Toast.LENGTH_LONG);
                            mToast.show();
                        }
                        // if we are at the end - just end playback
                        if (failAtEnd)
                            markWatched(true);
                        else {
                            // Try to continue playback
                            if (currPos > 0)
                                mBookmark = currPos;
                            if (setPossibleEmptyTrack && !possibleEmptyTrack) {
                                possibleEmptyTrack = true;
                                if (mToast != null)
                                    mToast.cancel();
                                mToast = Toast.makeText(getActivity(),
                                        getActivity().getString(R.string.pberror_recommend_ignoreextra),
                                        Toast.LENGTH_LONG);
                                mToast.show();
                                mPlayer.stop();
                                initializePlayer(true);
                            }
                            else
                                play(mVideo);
                        }
                        mTimeLastError = now;
                    }
                    else {
                        // More than 1 error per 30 seconds.
                        // Alert message for user to decide on continuing.
                        AlertDialogListener listener = new AlertDialogListener();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                                R.style.Theme_AppCompat_Dialog_Alert);
                        builder.setTitle(R.string.pberror_title);
                        if (recommendation > 0)
                            builder.setMessage(recommendation);
                        else
                            builder.setMessage(alertMsg);
                        // add a button
                        builder.setPositiveButton(R.string.pberror_button_continue, listener);
                        builder.setNegativeButton(R.string.pberror_button_exit, listener);
                        builder.setOnDismissListener(
                                dialog -> {
                                    if (mDialogStatus != DIALOG_RETRY)
                                        getActivity().finish();
                                    mDialogStatus = DIALOG_NONE;
                                });
                        builder.show();
                        mDialogStatus = DIALOG_ACTIVE;
                        mTimeLastError = 0;
                    }
                }
            }

        }

        class AlertDialogListener implements DialogInterface.OnClickListener {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        mDialogStatus = DIALOG_RETRY;
                        mBookmark = mPlayerGlue.getSavedCurrentPosition();
                        play(mVideo);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        mDialogStatus = DIALOG_EXIT;
                        break;
                }
            }
        }
    }
}
