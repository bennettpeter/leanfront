/*
 * Copyright (c) 2020 Peter Bennett
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackParameters;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.SampleQueue;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.CommBreakTable;
import org.mythtv.leanfront.model.Playlist;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.player.VideoPlayerGlue;

import java.util.ArrayList;
import java.util.List;

@SuppressLint({"RtlHardcoded", "PrivateResource", "SetTextI18n", "DefaultLocale"})
class PlaybackActionListener implements VideoPlayerGlue.OnActionClickedListener {

    private final PlaybackFragment playbackFragment;
    private Playlist mPlaylist;

    static final float[] STRETCH_VALUES = {0.75f, 0.88f, 1.0f, 1.18f, 1.33f, 1.5f};
    static final float[] SCALE_VALUES = {0.88f, 1.0f, 1.17f, 1.33f, 1.5f};
    private float mScale = 1.0f;
    float mStretch = 1.0f;
    float mPivotX = 0.5f;
    float mPivotY = 0.5f;
    long sampleOffsetUs = 0;
    long priorSampleOffsetUs = 0;
    AlertDialog mDialog;
    private DialogDismiss dialogDismiss = new DialogDismiss();
    private boolean yesPress;

    private static final String TAG = "lfe";
    private static final String CLASS = "PlaybackActionListener";

    PlaybackActionListener(PlaybackFragment playbackFragment, Playlist playlist) {
        this.playbackFragment = playbackFragment;
        this.mPlaylist = playlist;
    }

    @Override
    public void onPrevious() {
        if (playbackFragment.commBreakTable == null
                || playbackFragment.commBreakTable.entries.length == 0
                || skipComBack() == 0) {
            playbackFragment.tickle();
            playbackFragment.skipToPrevious();
        }
    }

    @Override
    public void onNext() {
        if (playbackFragment.commBreakTable == null
                || playbackFragment.commBreakTable.entries.length == 0
                ||  skipComForward() == 0) {
            playbackFragment.tickle();
            playbackFragment.skipToNext();
        }
    }

    public boolean onMenu() {
        playbackFragment.hideControlsOverlay(false);
        PlaybackControlsRow row =  playbackFragment.mPlayerGlue.getControlsRow();
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getPrimaryActionsAdapter();
        List<Object> fullList = new ArrayList<>(adapter.unmodifiableList());
        adapter = (ArrayObjectAdapter) row.getSecondaryActionsAdapter();
        fullList.addAll(adapter.unmodifiableList());
        ArrayList<String> prompts = new ArrayList<>();
        ArrayList<Action> actions = new ArrayList<>();
        for (Object obj : fullList) {
            if (obj instanceof PlaybackControlsRow.PlayPauseAction
                    || obj instanceof PlaybackControlsRow.FastForwardAction
                    || obj instanceof PlaybackControlsRow.RewindAction
                    || obj == playbackFragment.mPlayerGlue.mMenuAction)
                continue;
            else if (obj instanceof PlaybackControlsRow.MultiAction) {
                PlaybackControlsRow.MultiAction action
                        = (PlaybackControlsRow.MultiAction) obj;
                if (action.getId() == Video.ACTION_PLAYLIST_PLAY)
                    continue;
                prompts.add(action.getLabel(action.getIndex()));
                actions.add(action);
            }
            else if (obj instanceof Action) {
                Action action = (Action) obj;
                prompts.add(action.getLabel1().toString());
                actions.add(action);
            }
        }
        final ArrayList<Action> finalActions = actions; // needed because used in inner class
        // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        OnActionClickedListener parent = playbackFragment.mPlayerGlue;
        builder
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
                        })
                .setOnDismissListener(dialogDismiss);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 4;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(128,128,128,128)));
        return true;
    }

    @Override
    public void onPlayCompleted(VideoPlayerGlue.MyAction playlistPlayAction) {
        playbackFragment.setBookmark(Video.ACTION_SET_LASTPLAYPOS);
        dismissDialog();
        if (playbackFragment.mPlayerGlue.isIncreasing())
            // This starts it playing again
            playbackFragment.moveBackward(1000);
        else
            playbackFragment.checkNextShow();
    }

    @Override
    public void onZoom() {
        zoom(0);
    }


    // mode = -1 for smaller, 0 for rotate, 1 for bigger
    public void zoom(int mode) {
        showZoomSelector();
    }

    void setScale() {
        SurfaceView view = playbackFragment.getSurfaceView();
        int height = view.getHeight();
        int width = view.getWidth();
        float xScale = mScale * mStretch;
        if (mScale == 1.0f)
            mPivotY = 0.5f;
        if (xScale == 1.0f)
            mPivotX = 0.5f;
        view.setPivotX(mPivotX * width);
        view.setPivotY(mPivotY * height);
        view.setScaleX(xScale);
        view.setScaleY(mScale);
    }

    @Override
    public void onAspect() {
        showStretchSelector();
    }

    @Override
    public void onCaption() {
        // This code could be used for rotating among captions instead of showing a list
        // playbackFragment.mTextSelection = playbackFragment.trackSelector(C.TRACK_TYPE_TEXT, playbackFragment.mTextSelection,
        //         R.string.msg_subtitle_on, R.string.msg_subtitle_off, true, true);
        showCaptionSelector();
    }

    private void showCaptionSelector () {
        playbackFragment.hideControlsOverlay(false);
        PlaybackFragment.TrackInfo tracks
                = new PlaybackFragment.TrackInfo(playbackFragment, C.TRACK_TYPE_TEXT);

        ArrayList<String> prompts = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();
        for (int ix = 0; ix < tracks.trackList.size(); ix++) {
            PlaybackFragment.TrackEntry entry = tracks.trackList.get(ix);
            prompts.add(entry.description);
            actions.add(ix);
        }
        prompts.add(playbackFragment.getString(R.string.msg_subtitle_off));
        actions.add(-1);

        final ArrayList<Integer> finalActions = actions; // needed because used in inner class
        // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder
                .setTitle(R.string.title_select_caption)
                .setItems(prompts.toArray(new String[0]),
                        new DialogInterface.OnClickListener() {
                            ArrayList<Integer> mActions = finalActions;
                            public void onClick(DialogInterface dialog, int which) {
                                // The 'which' argument contains the index position
                                // of the selected item
                                if (which < mActions.size()) {
                                    playbackFragment.trackSelector
                                        (C.TRACK_TYPE_TEXT, mActions.get(which),
                                         R.string.msg_subtitle_on, R.string.msg_subtitle_off,true,false);
                                }
                            }
                        })
                .setOnDismissListener(dialogDismiss);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 4;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(128,128,128,128)));
    }

    @Override
    public void onAudioTrack() {
        // This code could be used for rotating among audio tracks instead of showing a list
        // playbackFragment.mAudioSelection = playbackFragment.trackSelector(C.TRACK_TYPE_AUDIO, playbackFragment.mAudioSelection,
        //         R.string.msg_audio_track, R.string.msg_audio_track_off, true, true);
        showAudioSelector();
    }

    private void showAudioSelector () {
        playbackFragment.hideControlsOverlay(false);
        PlaybackFragment.TrackInfo tracks
                = new PlaybackFragment.TrackInfo(playbackFragment,C.TRACK_TYPE_AUDIO);

        ArrayList<String> prompts = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();
        for (int ix = 0; ix < tracks.trackList.size(); ix++) {
            PlaybackFragment.TrackEntry entry = tracks.trackList.get(ix);
            prompts.add(entry.description);
            actions.add(ix);
        }
        prompts.add(playbackFragment.getString(R.string.msg_audio_track_off));
        actions.add(-1);

        final ArrayList<Integer> finalActions = actions; // needed because used in inner class
        // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder
                .setTitle(R.string.title_select_audio)
                .setItems(prompts.toArray(new String[0]),
                        new DialogInterface.OnClickListener() {
                            ArrayList<Integer> mActions = finalActions;
                            public void onClick(DialogInterface dialog, int which) {
                                // The 'which' argument contains the index position
                                // of the selected item
                                if (which < mActions.size()) {
                                    playbackFragment.trackSelector
                                            (C.TRACK_TYPE_AUDIO, mActions.get(which),
                                                    R.string.msg_audio_track, R.string.msg_audio_track_off,true,false);
                                }
                            }
                        })
                .setOnDismissListener(dialogDismiss);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 4;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(128,128,128,128)));
    }

    @Override
    public void onPivot() {
        showPivotSelector();
    }

    @Override
    public void onSpeed() {
        showSpeedSelector();
    }

    private void showSpeedSelector() {
        playbackFragment.hideControlsOverlay(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert)
                .setOnDismissListener(dialogDismiss);
        builder.setTitle(R.string.title_select_speed)
                .setView(R.layout.leanback_preference_widget_seekbar);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 2;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(100,0,0,0)));
        SeekBar seekBar = mDialog.findViewById(R.id.seekbar);
        seekBar.setMax(800);
        seekBar.setProgress(Math.round(playbackFragment.mSpeed * 100.0f));
        TextView seekValue = mDialog.findViewById(R.id.seekbar_value);
        seekValue.setText( (int)(playbackFragment.mSpeed * 100.0f) + "%");
        mDialog.setOnKeyListener(
            (DialogInterface dlg, int keyCode, KeyEvent event) -> {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        if (event.getAction() == KeyEvent.ACTION_UP)
                            dlg.dismiss();
                        return true;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN)
                    return false;
                int value = seekBar.getProgress();
                switch(keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (value > 10)
                            value -= 10;
                        else
                            return true;
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (value <= 790)
                            value += 10;
                        else
                            return true;
                        break;
                    default:
                        return false;
                }
                seekBar.setProgress(value);
                return true;
            }
        );
        seekBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                    value = value / 10 * 10;
                    if (value < 10)
                        value = 10;
                    seekValue.setText(value + "%");
                    playbackFragment.mSpeed = (float) value * 0.01f;
                    PlaybackParameters parms = new PlaybackParameters(playbackFragment.mSpeed);
                    playbackFragment.mPlayerGlue.setEnableControls(false);
                    dialogDismiss.enableControls = true;
                    playbackFragment.mPlayer.setPlaybackParameters(parms);
                }
               @Override
               public void onStartTrackingTouch(SeekBar seekBar) {  }
               @Override
               public void onStopTrackingTouch(SeekBar seekBar) {  }
            }
        );
    }

    private void showZoomSelector() {
        playbackFragment.hideControlsOverlay(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert)
                .setOnDismissListener(dialogDismiss);
        builder.setTitle(R.string.title_select_zoom)
                .setView(R.layout.leanback_preference_widget_seekbar);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 2;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(100,0,0,0)));
        SeekBar seekBar = mDialog.findViewById(R.id.seekbar);
        seekBar.setMax(200);
        seekBar.setProgress(Math.round(mScale * 100.0f));
        TextView summary = mDialog.findViewById(android.R.id.summary);
        summary.setText( playbackFragment.getString(R.string.seekbar_instructions));
        TextView seekValue = mDialog.findViewById(R.id.seekbar_value);
        seekValue.setText( (int)(mScale * 100.0f) + "%");
        mDialog.setOnKeyListener(
                (DialogInterface dlg, int keyCode, KeyEvent event) -> {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            if (event.getAction() == KeyEvent.ACTION_UP)
                                dlg.dismiss();
                            return true;
                    }
                    if (event.getAction() != KeyEvent.ACTION_DOWN)
                        return false;
                    int value = Math.round((float)seekBar.getProgress() / 5.0f) * 5;
                    float newfvalue = 0.0f;
                    switch(keyCode) {
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_ZOOM_OUT:
                            if (value > 5)
                                value -= 5;
                            break;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_ZOOM_IN:
                            if (value <= 195)
                                value += 5;
                            break;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            newfvalue = SCALE_VALUES [0];
                            for (float ftest : SCALE_VALUES) {
                                if (ftest < mScale)
                                    newfvalue = ftest;
                            }
                            value = Math.round(newfvalue * 100.0f);
                            break;
                        case KeyEvent.KEYCODE_DPAD_UP:
                            newfvalue = SCALE_VALUES [SCALE_VALUES.length-1];
                            for (float ftest : SCALE_VALUES) {
                                if (ftest > mScale) {
                                    newfvalue = ftest;
                                    break;
                                }
                            }
                            value = Math.round(newfvalue * 100.0f);
                            break;

                        default:
                            return false;
                    }
                    seekBar.setProgress(value);
                    return true;
                }
        );
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                        value = value / 5 * 5;
                        seekValue.setText(value + "%");
                        mScale = (float) value * 0.01f;
                        setScale();
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {  }
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {  }
                }
        );
    }

    private void showStretchSelector() {
        playbackFragment.hideControlsOverlay(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert)
                .setOnDismissListener(dialogDismiss);
        builder.setTitle(R.string.title_select_stretch)
                .setView(R.layout.leanback_preference_widget_seekbar);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 2;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(100,0,0,0)));
        SeekBar seekBar = mDialog.findViewById(R.id.seekbar);
        seekBar.setMax(200);
        seekBar.setProgress(Math.round(mStretch * 100.0f));
        TextView summary = mDialog.findViewById(android.R.id.summary);
        summary.setText( playbackFragment.getString(R.string.seekbar_instructions));
        TextView seekValue = mDialog.findViewById(R.id.seekbar_value);
        seekValue.setText( (int)(mStretch * 100.0f) + "%");
        mDialog.setOnKeyListener(
                (DialogInterface dlg, int keyCode, KeyEvent event) -> {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            if (event.getAction() == KeyEvent.ACTION_UP)
                                dlg.dismiss();
                            return true;
                    }
                    if (event.getAction() != KeyEvent.ACTION_DOWN)
                        return false;
                    int value = Math.round((float)seekBar.getProgress() / 5.0f) * 5;
                    float newfvalue = 0.0f;
                    switch(keyCode) {
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            if (value > 5)
                                value -= 5;
                            break;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            if (value <= 195)
                                value += 5;
                            break;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            newfvalue = STRETCH_VALUES [0];
                            for (float ftest : STRETCH_VALUES) {
                                if (ftest < mStretch)
                                    newfvalue = ftest;
                            }
                            value = Math.round(newfvalue * 100.0f);
                            break;
                        case KeyEvent.KEYCODE_DPAD_UP:
                            newfvalue = STRETCH_VALUES [STRETCH_VALUES.length-1];
                            for (float ftest : STRETCH_VALUES) {
                                if (ftest > mStretch) {
                                    newfvalue = ftest;
                                    break;
                                }
                            }
                            value = Math.round(newfvalue * 100.0f);
                            break;
                        default:
                            return false;
                    }
                    seekBar.setProgress(value);
                    return true;
                }
        );
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                        value = value / 5 * 5;
                        seekValue.setText(value + "%");
                        mStretch = (float) value * 0.01f;
                        setScale();
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {  }
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {  }
                }
        );
    }


    private void showPivotSelector() {
        playbackFragment.hideControlsOverlay(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert)
                .setOnDismissListener(dialogDismiss);
        builder.setTitle(R.string.title_select_position)
                .setMessage(getPivotMessage());
        mDialog = builder.create();
        mDialog.show();
        TextView messageText = (TextView)mDialog.findViewById(android.R.id.message);
        messageText.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 2;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(100,0,0,0)));
        mDialog.setOnKeyListener(
                (DialogInterface dlg, int keyCode, KeyEvent event) -> {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            if (event.getAction() == KeyEvent.ACTION_UP)
                                dlg.dismiss();
                            return true;
                    }
                    if (event.getAction() != KeyEvent.ACTION_DOWN)
                        return false;
                    int xvalue = Math.round(mPivotX * 100.0f);
                    int yvalue = Math.round(mPivotY * 100.0f);
                    switch(keyCode) {
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            if (xvalue > 0)
                                xvalue -= 10;
                            else
                                return true;
                            break;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            if (xvalue < 100)
                                xvalue += 10;
                            else
                                return true;
                            break;
                        case KeyEvent.KEYCODE_DPAD_UP:
                            if (yvalue > 0)
                                yvalue -= 10;
                            else
                                return true;
                            break;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            if (yvalue < 100)
                                yvalue += 10;
                            else
                                return true;
                            break;
                        default:
                            return false;
                    }
                    mPivotX = (float) xvalue * 0.01f;
                    mPivotY = (float) yvalue * 0.01f;
                    setScale();
                    mDialog.setMessage(getPivotMessage());
                    return true;
                }
        );
    }

    String getPivotMessage() {
        if (mScale == 1.0f && mStretch == 1.0f)
            return playbackFragment.getString(R.string.msg_cannot_move);
        int x = Math.round(mPivotX * 100.0f);
        int y = Math.round(mPivotY * 100.0f);
        StringBuilder build = new StringBuilder(playbackFragment.getString(R.string.msg_pic_pos));
        build.append(" ");
        if (x == 50)
            build.append(playbackFragment.getString(R.string.msg_pin_center))
                    .append(" / ");
        else if (x == 0)
            build.append(playbackFragment.getString(R.string.msg_pin_left_edge))
                    .append(" / ");
        else if (x < 50)
            build.append(playbackFragment.getString(R.string.msg_pin_left))
                    .append(" / ");
        else if (x == 100)
            build.append(playbackFragment.getString(R.string.msg_pin_right_edge))
                    .append(" / ");
        else if (x > 50)
            build.append(playbackFragment.getString(R.string.msg_pin_right))
                    .append(" / ");

        if (y == 50)
            build.append(playbackFragment.getString(R.string.msg_pin_center));
        else if (y == 0)
            build.append(playbackFragment.getString(R.string.msg_pin_top));
        else if (y < 50)
            build.append(playbackFragment.getString(R.string.msg_pin_up));
        else if (y == 100)
            build.append(playbackFragment.getString(R.string.msg_pin_bottom));
        else if (y > 50)
            build.append(playbackFragment.getString(R.string.msg_pin_down));
        return build.toString();
    }

    @Override
    public void onAudioSync() {
        showAudioSyncSelector();
    }

    private void showAudioSyncSelector() {
        playbackFragment.hideControlsOverlay(true);
        playbackFragment.mPlayerGlue.setEnableControls(false);
        dialogDismiss.enableControls = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.title_select_audiosync)
                .setView(R.layout.leanback_preference_widget_seekbar)
                .setOnDismissListener(dialogDismiss);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 2;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(100,0,0,0)));
        SeekBar seekBar = mDialog.findViewById(R.id.seekbar);
        seekBar.setMax(5000); // --2500ms to +2500ms
        seekBar.setProgress((int)(sampleOffsetUs/1000 + 2500));
        TextView seekValue = mDialog.findViewById(R.id.seekbar_value);
        String text = String.format("%+d",sampleOffsetUs / 1000);
        seekValue.setText(text);
        mDialog.setOnKeyListener(
                (DialogInterface dlg, int keyCode, KeyEvent event) -> {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            if (event.getAction() == KeyEvent.ACTION_UP)
                                dlg.dismiss();
                            return true;
                    }
                    if (event.getAction() != KeyEvent.ACTION_DOWN)
                        return false;
                    int value = (int)(sampleOffsetUs/1000 + 2500);
                    switch(keyCode) {
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            if (value > 10)
                                value -= 10;
                            break;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_DPAD_UP:
                            if (value <= 4990)
                                value += 10;
                            break;
                        default:
                            return false;
                    }
                    seekBar.setProgress(value);
                    return true;
                }
        );
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                        value = value / 10 * 10;
                        sampleOffsetUs = ((long)value - 2500) * 1000;
                        String text1 = String.format("%+d",sampleOffsetUs / 1000);
                        seekValue.setText(text1);
                        setAudioSync();
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {  }
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {  }
                }
        );
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setAudioSync() {
        boolean found = false;
        SampleQueue[] sampleQueues = playbackFragment.mMediaSource.getSampleQueues();
        for (SampleQueue sampleQueue : sampleQueues) {
            if (MimeTypes.isAudio(sampleQueue.getUpstreamFormat().sampleMimeType)) {
                sampleQueue.setSampleOffsetUs(sampleOffsetUs);
                found = true;
            }
        }
        if (found) {
            // This check is needed to prevent it continually resetting, because
            // this routine is called again 5 seconds after doing the moveBackward
            if (priorSampleOffsetUs != sampleOffsetUs)
                playbackFragment.moveBackward(0);
            priorSampleOffsetUs = sampleOffsetUs;
        }
    }

    @Override
    public void onBookmark() {
        playbackFragment.setBookmark(Video.ACTION_SET_BOOKMARK);
    }
    @Override
    public void onRewind() {
        playbackFragment.rewind();
    }

    @Override
    public void onFastForward() {
        playbackFragment.fastForward();
    }

    @Override
    // unused as we do not have OSD icons for this
    public void onJumpForward() {
        playbackFragment.jumpForward();
    }

    @Override
    // unused as we do not have OSD icons for this
    public void onJumpBack() {
        playbackFragment.jumpBack();
    }

    @Override
    public void onActionSelected(Action action) {
        playbackFragment.actionSelected(action);
    }

    @Override
    public void onPlayStateChanged() {
        if (playbackFragment.mPlayerGlue == null)
            return;
        if (playbackFragment.commBreakOption != PlaybackFragment.COMMBREAK_OFF) {
            long position = playbackFragment.mPlayerGlue.getCurrentPosition();
            setNextCommBreak(position);
        }
    }

    public void dismissDialog() {
        if (mDialog != null && mDialog.isShowing())
            mDialog.dismiss();
        mDialog = null;
    }

    long skipComBack() {
        if (!commSkipCheck())
            return 0;
        long position = playbackFragment.mPlayerGlue.getCurrentPosition();
        long newPosition = 0;
        int mark = 0;
        // Get the last entry that satisfies offset < position
        for (CommBreakTable.Entry entry : playbackFragment.commBreakTable.entries) {
            long offsetMs = playbackFragment.commBreakTable.getOffsetMs(entry);
            if (offsetMs < position - 20000) {
                newPosition = offsetMs;
                mark = entry.mark;
            }
            else
                break;
        }

        if (newPosition > 0) {
            playbackFragment.mPlayerGlue.setNextCommBreakMs(Long.MAX_VALUE);
            // If this is a start point, prevent it from immediately skipping
            if (mark == CommBreakTable.MARK_CUT_START)
                playbackFragment.priorCommBreak = newPosition + (long)Settings.getInt("pref_commskip_start") * 1000;
            playbackFragment.mPlayerGlue.seekTo(newPosition);
            comskipToast(mark);
        }
        return newPosition;
    }

    long skipComForward() {
        if (!commSkipCheck())
            return 0;
        long position = playbackFragment.mPlayerGlue.getCurrentPosition();
        long newPosition = 0;
        int mark = 0;
        // Get the first entry that satisfies offset > position
        for (CommBreakTable.Entry entry : playbackFragment.commBreakTable.entries) {
            long offsetMs = playbackFragment.commBreakTable.getOffsetMs(entry);
            if (offsetMs > position + 5000) {
                newPosition = offsetMs;
                mark = entry.mark;
                break;
            }
        }

        if (newPosition > 0) {
            playbackFragment.mPlayerGlue.setNextCommBreakMs(Long.MAX_VALUE);
            // If this is a start point, prevent it from immediately skipping
            if (mark == CommBreakTable.MARK_CUT_START)
                playbackFragment.priorCommBreak = newPosition + (long)Settings.getInt("pref_commskip_start") * 1000;
            playbackFragment.mPlayerGlue.seekTo(newPosition);
            comskipToast(mark);
        }
        return newPosition;
    }

    private void comskipToast(int mark) {
        int msgnum;
        switch (mark) {
            case CommBreakTable.MARK_CUT_START: msgnum = R.string.msg_commskip_start; break;
            case CommBreakTable.MARK_CUT_END:   msgnum = R.string.msg_commskip_end; break;
            case -1:                            msgnum = R.string.msg_commskip_last; break;
            case -2:                            msgnum = R.string.msg_commskip_skipped; break;
            default: return;
        }
        if (playbackFragment.mToast != null)
            playbackFragment.mToast.cancel();
        Context ctx = playbackFragment.getContext();
        playbackFragment.mToast = Toast.makeText(ctx,
                ctx.getString(msgnum),
                Toast.LENGTH_LONG);
        playbackFragment.mToast.show();
    }

    // pass in -1 to get current position, otherwise use position passed in
    public void setNextCommBreak(long position) {
        if (playbackFragment.commBreakOption != PlaybackFragment.COMMBREAK_OFF) {
            if (position == -1)
                position = playbackFragment.mPlayerGlue.getCurrentPosition();
            long nextCommBreak = Long.MAX_VALUE;
            CommBreakTable.Entry startEntry = null;
            long startOffsetMs = 0;
            for (CommBreakTable.Entry entry : playbackFragment.commBreakTable.entries) {
                long offsetMs = playbackFragment.commBreakTable.getOffsetMs(entry);
                if (entry.mark == CommBreakTable.MARK_CUT_START) {
                    startEntry = entry;
                    startOffsetMs = playbackFragment.commBreakTable.getOffsetMs(startEntry);
                }
                else {
                    long possible = startOffsetMs + Settings.getInt("pref_commskip_start") * 1000;
                    if (position <= offsetMs && entry.mark == CommBreakTable.MARK_CUT_END
                            && startEntry != null && possible != playbackFragment.priorCommBreak) {
                        nextCommBreak = possible;
                        break;
                    }
                }
            }
            playbackFragment.mPlayerGlue.setNextCommBreakMs(nextCommBreak);
        }
    }

    private boolean commSkipCheck() {
        if (playbackFragment.commBreakTable == null || playbackFragment.commBreakTable.entries.length == 0) {
            if (playbackFragment.mToast != null)
                playbackFragment.mToast.cancel();
            Context ctx = playbackFragment.getContext();
            playbackFragment.mToast = Toast.makeText(ctx,
                    ctx.getString(R.string.msg_commskip_none),
                    Toast.LENGTH_LONG);
            playbackFragment.mToast.show();
            return false;
        }
        return true;
    }

    // Comm skip - present menu to choose which option you want
    // off, notify or skip.
    @Override
    public void onCommSkip() {
        if (!playbackFragment.mIsBounded) {
            if (playbackFragment.mToast != null)
                playbackFragment.mToast.cancel();
            Context ctx = playbackFragment.getContext();
            playbackFragment.mToast = Toast.makeText(ctx,
                    ctx.getString(R.string.msg_commskip_unavail),
                    Toast.LENGTH_LONG);
            playbackFragment.mToast.show();
            return;
        }
        if (!commSkipCheck())
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder
                .setTitle(R.string.title_commskip)
                .setItems(R.array.menu_commskip,
                        (dialog, which) -> {
                            // The 'which' argument contains the index position
                            // of the selected item
                            playbackFragment.commBreakOption = which;
                            playbackFragment.priorCommBreak = Long.MAX_VALUE;
                        })
                .setOnDismissListener(dialogDismiss);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        lp.x=0;
        lp.y=0;
        lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 4;
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(128,128,128,128)));
        ListView list = mDialog.getListView();
        if (list != null)
            list.setSelection(playbackFragment.commBreakOption);
    }

    @Override
    public void onCommBreak(long nextCommBreakMs, long position) {
        long newPosition = -1;
        switch (playbackFragment.commBreakOption) {
            case PlaybackFragment.COMMBREAK_SKIP:
            case PlaybackFragment.COMMBREAK_NOTIFY:
                for (CommBreakTable.Entry entry : playbackFragment.commBreakTable.entries) {
                    long offsetMs = playbackFragment.commBreakTable.getOffsetMs(entry);
                    // Skip past earlier entries
                    if (offsetMs <= nextCommBreakMs)
                        continue;
                    // We should now be at the MARK_CUT_END of the selected comm break
                    // If not or if we are past it, do nothing.
                    if (position <= offsetMs && entry.mark == CommBreakTable.MARK_CUT_END) {
                        newPosition = offsetMs + (long)Settings.getInt("pref_commskip_end") * 1000;
                    }
                    else
                        Log.e(TAG, CLASS + " No end commbreak entry for: " + nextCommBreakMs);
                    break;
                }
                break;
            default:
                return;
        }
        if (newPosition > 0 && newPosition > position) {
            playbackFragment.priorCommBreak = nextCommBreakMs;
            final long finalNewPosition = newPosition;
            switch (playbackFragment.commBreakOption) {
                case PlaybackFragment.COMMBREAK_SKIP:
                    // Uncomment this To Hide controls while skipping commercials
//                    playbackFragment.mPlayerGlue.setEnableControls(false);
                    // Comment this To Hide controls while skipping commercials
                    playbackFragment.tickle(false,false);
                    playbackFragment.mPlayerGlue.seekTo(newPosition);
                    comskipToast(-2);
                    break;
                case PlaybackFragment.COMMBREAK_NOTIFY:
                    dismissDialog();
                    playbackFragment.hideControlsOverlay(false);
                    AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                            R.style.Theme_AppCompat_Dialog_Alert)
                            .setTitle(R.string.title_comm_playing)
                            .setItems(R.array.menu_commplaying,
                                    (dialog, which) -> {
                                        // The 'which' argument contains the index position
                                        // of the selected item
                                        switch (which) {
                                            // 0 = skip commercial
                                            case 0:
                                                if (playbackFragment.mPlayerGlue.getCurrentPosition()
                                                        < finalNewPosition) {
                                                    // controls will be re-enabled by onEndCommBreak
                                                    // Uncomment this To Hide controls while skipping commercials
//                                                        playbackFragment.mPlayerGlue.setEnableControls(false);
                                                    // Comment this To Hide controls while skipping commercials
                                                    playbackFragment.tickle(false,false);
                                                    dialogDismiss.enableControls = false;
                                                    playbackFragment.mPlayerGlue.seekTo(finalNewPosition);
                                                }
                                                break;
                                            // 1 = do not skip commercial. Defaults to doing nothing
                                        }
                                    })
                            .setOnDismissListener(dialogDismiss)
                            .setOnKeyListener(
                                    (DialogInterface dialog, int keyCode, KeyEvent event) -> {
                                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                            switch (keyCode) {
                                                case KeyEvent.KEYCODE_BUTTON_R2:
                                                case KeyEvent.KEYCODE_BUTTON_L2:
                                                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                                                case KeyEvent.KEYCODE_MEDIA_REWIND:
                                                case KeyEvent.KEYCODE_DPAD_RIGHT:
                                                case KeyEvent.KEYCODE_DPAD_LEFT:
                                                    dialog.dismiss();
                                                    break;
                                            }
                                        }
                                        return false;
                                    }
                            );
                    // re-enable controls igf the dialog is dismissed
                    dialogDismiss.enableControls = true;
                    mDialog = builder.create();
                    mDialog.show();
                    WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
                    lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
                    lp.x=0;
                    lp.y=0;
                    lp.width= Resources.getSystem().getDisplayMetrics().widthPixels / 4;
                    lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    mDialog.getWindow().setAttributes(lp);
                    mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(128,128,128,128)));
                    break;
            }
            playbackFragment.mPlayerGlue.setEndCommBreakMs(newPosition + 500);
        }
        setNextCommBreak(-1);
    }

    @Override
    public void onEndCommBreak() {
        dismissDialog();
        playbackFragment.mPlayerGlue.setEnableControls(true);
        // Uncomment this To Hide controls while skipping commercials
//        playbackFragment.hideControlsOverlay(true);
        // Comment this To Hide controls while skipping commercials
        playbackFragment.setControlsOverlayAutoHideEnabled(true);
    }

    public void onRecord() {
        playbackFragment.onRecord();
    }

    @Override
    public void onIdleTimeout() {
        ((PlaybackActivity)playbackFragment.getActivity()).updateTouchTime();
        dismissDialog();
        playbackFragment.hideControlsOverlay(false);
        yesPress = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle(playbackFragment.mVideo.title)
                .setMessage(R.string.msg_idle_timeout)
                .setPositiveButton(R.string.button_yes, (dialog, which) -> {
                    ((PlaybackActivity)playbackFragment.getActivity()).updateTouchTime();
                    yesPress = true;
                 })
                .setNegativeButton(R.string.button_no,null)
                .setOnDismissListener( (dialog) -> {
                    if (yesPress)
                        dialogDismiss.onDismiss(dialog);
                    else {
                        dialogDismiss.onDismiss(dialog);
                        playbackFragment.getActivity().finish();
                    }
                });
        dialogDismiss.enableControls = false;
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        mDialog.getWindow().setAttributes(lp);
        new Handler().postDelayed( () -> {
            if (mDialog != null)
                mDialog.dismiss();
         }, 120000);
    }

    // Gestures
    public boolean onTap() {
        if (mDialog == null) {
            playbackFragment.tickle();
            return true;
        }
        return false;
    }

    public boolean onDoubleTap() {
        if (mDialog == null) {
            playbackFragment.hideControlsOverlay(true);
            return true;
        }
        return false;
    }

    class DialogDismiss implements DialogInterface.OnDismissListener {
        public boolean enableControls = false;
        @Override
        public void onDismiss(DialogInterface dialogInterface) {
            if (mDialog == dialogInterface)
                mDialog = null;
            playbackFragment.hideNavigation();
            if (enableControls) {
                playbackFragment.mPlayerGlue.setEnableControls(true);
                enableControls = false;
            }
        }
    }
}
