/*
 * Copyright (C) 2017 The Android Open Source Project
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

package org.mythtv.leanfront.player;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.OptIn;
import androidx.core.content.res.ResourcesCompat;
import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackTransportRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.WidgetAccess;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.leanback.LeanbackPlayerAdapter;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.BackendCache;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;

/**
 * Manages customizing the actions in the {@link PlaybackControlsRow}. Adds and manages the
 * following actions to the primary and secondary controls:
 *
 * <ul>
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.RepeatAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.ThumbsDownAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.ThumbsUpAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.SkipPreviousAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.SkipNextAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.FastForwardAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.RewindAction}
 * </ul>
 *
 * Note that the superclass, {@link PlaybackTransportControlGlue}, manages the playback controls
 * row.
 */
public class VideoPlayerGlue extends PlaybackTransportControlGlue<LeanbackPlayerAdapter> {

    /** Listens for when skip to next and previous actions have been dispatched. */
    public interface OnActionClickedListener {
        /** Skip to the previous item in the queue. */
        void onPrevious();
        /** Skip to the next item in the queue. */
        void onNext();
        void onPlayCompleted(MyAction playlistPlayAction);
        void onZoom();
        void onAspect();
        void onCaption();
        void onPivot();
        void onRewind();
        void onFastForward();
        void onJumpForward();
        void onJumpBack();
        void onSpeed();
        void onAudioTrack();
        void onAudioSync();
        void onBookmark();
        boolean onMenu();
        void onCommSkip();
        void onRecord();
        void onActionSelected(Action action);
        void onCommBreak(long nextCommBreakMs, long position);
        void onEndCommBreak();
        void onPlayStateChanged();
        void dismissDialog();
        void onIdleTimeout();
    }

    private final OnActionClickedListener mActionListener;
    private WidgetAccess.MySelectedListener mActionSelectedListener = new SelectedListener();

    private PlaybackControlsRow.SkipPreviousAction mSkipPreviousAction;
    private PlaybackControlsRow.SkipNextAction mSkipNextAction;
    private PlaybackControlsRow.FastForwardAction mFastForwardAction;
    private PlaybackControlsRow.RewindAction mRewindAction;
    private PlaybackControlsRow.ClosedCaptioningAction mClosedCaptioningAction;
    private MyAction mZoomAction;
    private MyAction mPivotAction;
    private MyAction mAspectAction;
    private MyAction mSpeedAction;
    private MyAction mAudioTrackAction;
    private MyAction mAudioSyncAction;
    private MyAction mAutoPlayAction;
    private MyAction mBookmarkAction;
    public final MyAction mMenuAction;
    private MyAction mCommSkipAction;
    private final MyAction mRecordAction;
    private boolean mActionsVisible;
    // Skip means go to next or previous track
    // Skip is disallowed when playing Live TV
    private boolean mAllowSkip;
    private boolean mLiveTV;
    private long mSavedCurrentPosition = -1;
    private long mSavedDuration = -1;
    private final boolean isTV;
    private boolean playerClosed;
    private boolean playCompleted;
    private boolean enableControls = true;
    private long nextCommBreakMs = Long.MAX_VALUE;
    private long endCommBreakMs = Long.MAX_VALUE;
    private PlaybackActivity activity;
    private long idleTimeoutMillis;
    private long playbackStartTime;
    private long recordAdjustment;
    private boolean isIncreasing;


    public VideoPlayerGlue(
            Activity context,
            LeanbackPlayerAdapter playerAdapter,
            OnActionClickedListener actionListener,
            boolean liveTV) {
        super(context, playerAdapter);

        activity = (PlaybackActivity) context;
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

        mActionListener = actionListener;
        mLiveTV = liveTV;
        mAllowSkip = !liveTV;

        mSkipPreviousAction = new PlaybackControlsRow.SkipPreviousAction(context);
        mSkipNextAction = new PlaybackControlsRow.SkipNextAction(context);
        mFastForwardAction = new PlaybackControlsRow.FastForwardAction(context);
        mRewindAction = new PlaybackControlsRow.RewindAction(context);
        mZoomAction = new MyAction(context,Video.ACTION_ZOOM, R.drawable.ic_zoom_button,R.string.button_zoom);
        mAspectAction = new MyAction(context,Video.ACTION_ASPECT, R.drawable.ic_aspect_button,R.string.button_aspect);
        mSpeedAction = new MyAction(context,Video.ACTION_SPEEDUP, R.drawable.ic_speed_increase,R.string.button_speedup);
        mAudioTrackAction = new MyAction(context,Video.ACTION_AUDIOTRACK, R.drawable.ic_audiotrack,R.string.button_audiotrack);
        mAudioSyncAction = new MyAction(context,Video.ACTION_AUDIOSYNC, R.drawable.ic_av_timer,R.string.button_audiosync);
        mAutoPlayAction = new MyAction(context,Video.ACTION_PLAYLIST_PLAY,
                new int[] {R.drawable.ic_playlist_play,R.drawable.ic_playlist_play_actve},
                new int[] {R.string.button_playlistplay,R.string.button_playlistplay_active});
        mClosedCaptioningAction = new PlaybackControlsRow.ClosedCaptioningAction(context);
        Resources res = context.getResources();
        String[] labels = new String[1];
        labels[0] = res.getString(R.string.button_cc);
        mClosedCaptioningAction.setLabels(labels);
        mPivotAction = new MyAction(context,Video.ACTION_PIVOT, R.drawable.ic_up_down_button,R.string.button_pivot);
        mBookmarkAction = new MyAction(context,Video.ACTION_SET_BOOKMARK, R.drawable.ic_bookmark_border,R.string.button_bookmark);
        mMenuAction = new MyAction(context,Video.ACTION_MENU, R.drawable.ic_menu,R.string.button_menu);
        mCommSkipAction = new MyAction(context,Video.ACTION_COMMSKIP, R.drawable.ic_bolt,R.string.button_commskip);
        mRecordAction = new MyAction(context,Video.ACTION_RECORD,
                new int[] {R.drawable.ic_record, R.drawable.ic_record_active},
                new int[] {R.string.button_record, R.string.button_record});
        idleTimeoutMillis = Settings.getInt("pref_idle_timeout") * 60000;
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter adapter) {
        // Order matters, super.onCreatePrimaryActions() will create the play / pause action.
        // Will display as follows:
        // play/pause, previous, rewind, fast forward, next
        //   > /||      |<        <<        >>         >|
        super.onCreatePrimaryActions(adapter);
        if (mAllowSkip)
            adapter.add(mSkipPreviousAction);
        adapter.add(mRewindAction);
        adapter.add(mFastForwardAction);
        if (mAllowSkip)
            adapter.add(mSkipNextAction);
        adapter.add(mSpeedAction);
        adapter.add(mMenuAction);
        if (mLiveTV)
            adapter.add(mRecordAction);
        if (BackendCache.getInstance().supportLastPlayPos)
            adapter.add(mBookmarkAction);
        adapter.add(mCommSkipAction);
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter adapter) {
        super.onCreateSecondaryActions(adapter);
        adapter.add(mClosedCaptioningAction);
        adapter.add(mZoomAction);
        adapter.add(mAspectAction);
        adapter.add(mPivotAction);
        adapter.add(mAudioTrackAction);
        adapter.add(mAudioSyncAction);
        if (mAllowSkip)
            adapter.add(mAutoPlayAction);
        mActionsVisible = true;
    }

    public void setAutoPlay(boolean enable) {
        if (!mAllowSkip)
            enable = false;
        int value;
        if (enable)
            value=1;
        else
            value=0;
        mAutoPlayAction.setIndex(value);
    }

    public boolean getAutoPlay() {
        return mAutoPlayAction.getIndex() == 1;
    }

    public void setActions(boolean showActions) {
        if (showActions) {
            if (mActionsVisible)
                return;
            PlaybackControlsRow row =  getControlsRow();
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getPrimaryActionsAdapter();
            adapter.clear();
            onCreatePrimaryActions(adapter);
            adapter.notifyArrayItemRangeChanged(0,adapter.size());
            adapter = (ArrayObjectAdapter) row.getSecondaryActionsAdapter();
            adapter.clear();
            onCreateSecondaryActions(adapter);
            adapter.notifyArrayItemRangeChanged(0,adapter.size());
            mActionsVisible = true;
            onPlayStateChanged();
        }
        else {
            if (!mActionsVisible)
                return;
            PlaybackControlsRow row =  getControlsRow();
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getPrimaryActionsAdapter();
            adapter.clear();
            adapter.notifyArrayItemRangeChanged(0,0);
            adapter = (ArrayObjectAdapter) row.getSecondaryActionsAdapter();
            adapter.clear();
            adapter.notifyArrayItemRangeChanged(0,0);
            mActionsVisible = false;
        }
    }

    public void setupSelectedListener() {
        PlaybackRowPresenter presenter = getPlaybackRowPresenter();
        if (presenter instanceof PlaybackTransportRowPresenter) {
            WidgetAccess w = new WidgetAccess();
            w.setMySelectedListener(
                    (PlaybackTransportRowPresenter)presenter, mActionSelectedListener);
        }
    }

    @Override
    public void onActionClicked(Action action) {
        if (shouldDispatchAction(action)) {
            dispatchAction(action);
            return;
        }
        // Super class handles play/pause and delegates to abstract methods next()/previous().
        super.onActionClicked(action);
    }

    // Should dispatch actions that the super class does not supply callbacks for.
    private boolean shouldDispatchAction(Action action) {
        if (playerClosed)
            return false;
        if (action == mAutoPlayAction && mAllowSkip)
            return true;
        return action == mRewindAction
                || action == mFastForwardAction
                || action == mClosedCaptioningAction
                || action == mZoomAction
                || action == mAspectAction
                || action == mPivotAction
                || action == mSpeedAction
                || action == mAudioTrackAction
                || action == mAudioSyncAction
                || action == mBookmarkAction
                || action == mMenuAction
                || action == mCommSkipAction
                || action == mRecordAction;
    }

    private void dispatchAction(Action action) {
        // Primary actions are handled manually.
        if (action == mRewindAction) {
            mActionListener.onRewind();
        } else if (action == mFastForwardAction) {
            mActionListener.onFastForward();
        } else if (action == mZoomAction) {
            mActionListener.onZoom();
        } else if (action == mAspectAction) {
            mActionListener.onAspect();
        } else if (action == mClosedCaptioningAction) {
            mActionListener.onCaption();
        } else if (action == mPivotAction) {
            mActionListener.onPivot();
        } else if (action == mSpeedAction) {
            mActionListener.onSpeed();
        } else if (action == mAudioTrackAction) {
            mActionListener.onAudioTrack();
        } else if (action == mAudioSyncAction) {
            mActionListener.onAudioSync();
        } else if (action == mBookmarkAction) {
            mActionListener.onBookmark();
        } else if (action == mMenuAction) {
            mActionListener.onMenu();
        } else if (action == mCommSkipAction) {
            mActionListener.onCommSkip();
        } else if (action == mRecordAction) {
            mActionListener.onRecord();
            mRecordAction.setIndex(1);
            PlaybackControlsRow row =  getControlsRow();
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getPrimaryActionsAdapter();
            notifyActionChanged(mRecordAction, adapter);
        } else if (action instanceof PlaybackControlsRow.MultiAction) {
            PlaybackControlsRow.MultiAction multiAction = (PlaybackControlsRow.MultiAction) action;
            multiAction.nextIndex();
            // Notify adapter of action changes to handle secondary actions, such as, thumbs up/down
            // and repeat.
            notifyActionChanged(
                    multiAction,
                    (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter());
        }
        if (action == mAutoPlayAction) {
            if (mAutoPlayAction.getIndex() == 1
                && playCompleted)
                next();
        }
    }

    // To prevent controls showing when changing audio sync, they are "disabled" here.
    public void setEnableControls(boolean enable) {
        enableControls = enable;
    }

    @Override
    protected void onPlayStateChanged() {
        mActionListener.onPlayStateChanged();
        if (enableControls) {
            mActionListener.dismissDialog();
            super.onPlayStateChanged();
        }
        if (isPlaying())
            playCompleted = false;
    }

    public boolean isPlayCompleted() {
        return playCompleted;
    }

    private void notifyActionChanged(
            Action action, ArrayObjectAdapter adapter) {
        if (adapter != null) {
            int index = adapter.indexOf(action);
            if (index >= 0) {
                adapter.notifyArrayItemRangeChanged(index, 1);
            }
        }
    }

    public void setPlayerClosed(boolean playerClosed) {
        this.playerClosed = playerClosed;
    }

    private void onActionSelected(Action action) {
        mActionListener.onActionSelected(action);
    }

    @Override
    public void next() {
        mActionListener.onNext();
    }

    @Override
    public void previous() {
        mActionListener.onPrevious();
    }

    @Override
    protected void onPlayCompleted() {
        mActionListener.onPlayCompleted(mAutoPlayAction);
        playCompleted = true;
        super.onPlayCompleted();
    }

    static int getIconHighlightColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.playbackControlsIconHighlightColor,
                outValue, true)) {
            return outValue.data;
        }
        return context.getResources().getColor(R.color.lb_playback_icon_highlight_no_theme);
    }

    @Override
    public long getCurrentPosition() {
        long currentPosition = super.getCurrentPosition();
        if (currentPosition > 200)
            mSavedCurrentPosition = currentPosition;
        return currentPosition;
    }

    public long getSavedCurrentPosition() {
        return mSavedCurrentPosition;
    }

    // This method was copied from PlaybackBaseControlGlue
    // so we can modify the duration
    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onUpdateDuration() {
        PlaybackControlsRow controlsRow = getControlsRow();
        LeanbackPlayerAdapter adapter = getPlayerAdapter();
        if (controlsRow != null) {
            controlsRow.setDuration(
                    adapter.isPrepared() ? myGetDuration() : -1);
        }
        else
            // This is to satisfy the @CallSuper annotation
            super.onUpdateDuration();
    }

    public void setNextCommBreakMs(long nextCommBreakMs) {
        synchronized (this) {
            this.nextCommBreakMs = nextCommBreakMs;
        }
    }

    public void setEndCommBreakMs(long endCommBreakMs) {
        this.endCommBreakMs = endCommBreakMs;
    }

    @Override
    protected void onUpdateProgress() {
        long currPos = super.getCurrentPosition();
        if (currPos >= endCommBreakMs) {
            synchronized (this) {
                endCommBreakMs = Long.MAX_VALUE;
            }
            mActionListener.onEndCommBreak();
        }
        if (currPos >= nextCommBreakMs) {
            long next = nextCommBreakMs;
            synchronized (this) {
                nextCommBreakMs = Long.MAX_VALUE;
            }
            mActionListener.onCommBreak(next, currPos);
        }
        if (idleTimeoutMillis > 0
                && System.currentTimeMillis() - activity.getTouchTime() > idleTimeoutMillis)
            mActionListener.onIdleTimeout();
        super.onUpdateProgress();
        onUpdateDuration();
    }

    public void setPlaybackStartTime(long playbackStartTime) {
        this.playbackStartTime = playbackStartTime;
        recordAdjustment = 0;
    }

    public void setIncreasing(boolean increasing) {
        isIncreasing = increasing;
    }

    public boolean isIncreasing() {
        return isIncreasing;
    }

    public long myGetDuration() {
        long duration = getDuration();
        if (isIncreasing && duration > 0 && playbackStartTime > 0)
            recordAdjustment = System.currentTimeMillis();
        if (recordAdjustment > 0)
            duration += recordAdjustment - playbackStartTime;
        if (duration > 0)
            mSavedDuration = duration;
        return duration;
    }

    public long getSavedDuration() {
        return mSavedDuration;
    }

    public class  SelectedListener implements WidgetAccess.MySelectedListener {
        @Override
        public void onControlSelected(Presenter.ViewHolder controlViewHolder, Object item) {
            if (item instanceof Action) {
                onActionSelected((Action) item);
                if (!isTV)
                    onActionClicked((Action) item);
            }
        }
    }

    /**
     * Our custom actions
     */
    public static class MyAction extends PlaybackControlsRow.MultiAction {

        public MyAction(Context context, int id, int [] icons, int [] labels) {
            super(id);
            Resources res = context.getResources();
            Drawable[] drawables = new Drawable[icons.length];
            String[] labelStr = new String[icons.length];
            for (int i = 0; i<icons.length; i++) {
                drawables[i] = ResourcesCompat.getDrawable(res, icons[i], null);
                labelStr[i] = res.getString(labels[i]);
            }
            setDrawables(drawables);
            setLabels(labelStr);
        }

        public MyAction(Context context, int id, int icon, int label) {
            this(context, id, new int[]{icon},new int[]{label});
        }
    }



}
