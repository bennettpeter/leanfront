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

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;
import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackTransportRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.WidgetAccess;

import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.Video;

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
        void onActionSelected(Action action);
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
    private MyAction mPlaylistPlayAction;
    private boolean mActionsVisible;
    private long mOffsetMillis = 0;
    // Skip means go to next or previous track
    // Skip is disallowed when playing Live TV
    private boolean mAllowSkip;
    private long mSavedCurrentPosition = -1;
    private long mSavedDuration = -1;
    private final boolean isTV;
    private boolean playerClosed;
    private boolean playCompleted;

    public VideoPlayerGlue(
            Context context,
            LeanbackPlayerAdapter playerAdapter,
            OnActionClickedListener actionListener,
            boolean allowSkip) {
        super(context, playerAdapter);

        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

        mActionListener = actionListener;
        mAllowSkip = allowSkip;

        mSkipPreviousAction = new PlaybackControlsRow.SkipPreviousAction(context);
        mSkipNextAction = new PlaybackControlsRow.SkipNextAction(context);
        mFastForwardAction = new PlaybackControlsRow.FastForwardAction(context);
        mRewindAction = new PlaybackControlsRow.RewindAction(context);
        mZoomAction = new MyAction(context,Video.ACTION_ZOOM, R.drawable.ic_zoom_button,R.string.button_zoom);
        mAspectAction = new MyAction(context,Video.ACTION_ASPECT, R.drawable.ic_aspect_button,R.string.button_aspect);
        mSpeedAction = new MyAction(context,Video.ACTION_SPEEDUP, R.drawable.ic_speed_increase,R.string.button_speedup);
        mAudioTrackAction = new MyAction(context,Video.ACTION_AUDIOTRACK, R.drawable.ic_audiotrack,R.string.button_audiotrack);
        mAudioSyncAction = new MyAction(context,Video.ACTION_AUDIOSYNC, R.drawable.ic_av_timer,R.string.button_audiosync);
        mPlaylistPlayAction = new MyAction(context,Video.ACTION_PLAYLIST_PLAY,
                new int[] {R.drawable.ic_playlist_play,R.drawable.ic_playlist_play_actve},
                new int[] {R.string.button_playlistplay,R.string.button_playlistplay_active});
        mClosedCaptioningAction = new PlaybackControlsRow.ClosedCaptioningAction(context);
        Resources res = context.getResources();
        String[] labels = new String[1];
        labels[0] = res.getString(R.string.button_cc);
        mClosedCaptioningAction.setLabels(labels);
        mPivotAction = new MyAction(context,Video.ACTION_PIVOT, R.drawable.ic_up_down_button,R.string.button_pivot);
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
        adapter.add(mPlaylistPlayAction);
        mActionsVisible = true;
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
        return action == mRewindAction
                || action == mFastForwardAction
                || action == mClosedCaptioningAction
                || action == mZoomAction
                || action == mAspectAction
                || action == mPivotAction
                || action == mSpeedAction
                || action == mAudioTrackAction
                || action == mAudioSyncAction
                || action == mPlaylistPlayAction;
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
        } else if (action instanceof PlaybackControlsRow.MultiAction) {
            PlaybackControlsRow.MultiAction multiAction = (PlaybackControlsRow.MultiAction) action;
            multiAction.nextIndex();
            // Notify adapter of action changes to handle secondary actions, such as, thumbs up/down
            // and repeat.
            notifyActionChanged(
                    multiAction,
                    (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter());
        }
        if (action == mPlaylistPlayAction) {
            if (mPlaylistPlayAction.getIndex() == 1
                && playCompleted)
                next();
        }
    }

    @Override
    protected void onPlayStateChanged() {
        super.onPlayStateChanged();
        if (isPlaying())
            playCompleted = false;
    }

    private void notifyActionChanged(
            PlaybackControlsRow.MultiAction action, ArrayObjectAdapter adapter) {
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
        mActionListener.onPlayCompleted(mPlaylistPlayAction);
        playCompleted = true;
        super.onPlayCompleted();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return super.onKey(v, keyCode, event);
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
        long currentPosition = super.getCurrentPosition() + mOffsetMillis;
        if (currentPosition > 200)
            mSavedCurrentPosition = currentPosition;
        return currentPosition;
    }

    public long getSavedCurrentPosition() {
        return mSavedCurrentPosition;
    }

    @Override
    // This method was copied from PlaybackBaseControlGlue
    // so we can modify the duration
    protected void onUpdateDuration() {
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

    public long myGetDuration() {
        long duration = getDuration();
        if (duration >= 0)
            duration += mOffsetMillis;
        if (duration > 0)
            mSavedDuration = duration;
        return duration;
    }

    public long getSavedDuration() {
        return mSavedDuration;
    }

    public long getOffsetMillis() {
        return mOffsetMillis;
    }

    public void setOffsetMillis(long offsetMillis) {
        this.mOffsetMillis = offsetMillis;
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
