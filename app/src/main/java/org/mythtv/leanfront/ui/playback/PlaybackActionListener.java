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

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.leanback.widget.Action;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import org.mythtv.leanfront.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.util.MimeTypes;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.Playlist;
import org.mythtv.leanfront.player.VideoPlayerGlue;

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

    private static final String TAG = "lfe";
    private static final String CLASS = "PlaybackActionListener";

    PlaybackActionListener(PlaybackFragment playbackFragment, Playlist playlist) {
        this.playbackFragment = playbackFragment;
        this.mPlaylist = playlist;
    }

    @Override
    public void onPrevious() {
        playbackFragment.skipToPrevious();
    }

    @Override
    public void onNext() {
        playbackFragment.skipToNext();
    }

    @Override
    public void onPlayCompleted(VideoPlayerGlue.MyAction playlistPlayAction) {
        playbackFragment.markWatched(true);
        if (playlistPlayAction.getIndex() == 1) // playlist selected
            onNext();
        else if (playbackFragment.mIsBounded) {
            Log.i(TAG, CLASS + " onPlayCompleted checking File Length.");
            playbackFragment.mIsPlayResumable = true;
            playbackFragment.getFileLength();
        }
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
        playbackFragment.mTextSelection = playbackFragment.trackSelector(C.TRACK_TYPE_TEXT, playbackFragment.mTextSelection,
                R.string.msg_subtitle_on, R.string.msg_subtitle_off, true, true);
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
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.title_select_speed)
                .setView(R.layout.leanback_preference_widget_seekbar);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
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
                    case KeyEvent.KEYCODE_BACK:
                        return false;
                    default:
                        dlg.dismiss();
                        playbackFragment.getActivity().onKeyDown(event.getKeyCode(), event);
                        return true;
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
                    playbackFragment.mPlayer.setPlaybackParameters(parms);
                }
               @Override
               public void onStartTrackingTouch(SeekBar seekBar) {  }
               @Override
               public void onStopTrackingTouch(SeekBar seekBar) {  }
            }
        );
        mDialog.setOnDismissListener(
                (DialogInterface dialog) -> {
                    mDialog = null;
                    playbackFragment.hideNavigation();
                }
        );
    }

    private void showZoomSelector() {
        playbackFragment.hideControlsOverlay(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.title_select_zoom)
                .setView(R.layout.leanback_preference_widget_seekbar);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
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

                        case KeyEvent.KEYCODE_BACK:
                            return false;
                        default:
                            dlg.dismiss();
                            playbackFragment.getActivity().onKeyDown(event.getKeyCode(), event);
                            return true;
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
        mDialog.setOnDismissListener(
                (DialogInterface dialog) -> {
                    mDialog = null;
                    playbackFragment.hideNavigation();
                }
        );
    }

    private void showStretchSelector() {
        playbackFragment.hideControlsOverlay(true);
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.title_select_stretch)
                .setView(R.layout.leanback_preference_widget_seekbar);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
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
                        case KeyEvent.KEYCODE_BACK:
                            return false;
                        default:
                            dlg.dismiss();
                            playbackFragment.getActivity().onKeyDown(event.getKeyCode(), event);
                            return true;
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
        mDialog.setOnDismissListener(
                (DialogInterface dialog) -> {
                    mDialog = null;
                    playbackFragment.hideNavigation();
                }
        );
    }


    private void showPivotSelector() {
        playbackFragment.hideControlsOverlay(true);
        int x = Math.round(mPivotX * 100.0f);
        int y = Math.round(mPivotY * 100.0f);
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.title_select_position)
                .setMessage(getPivotMessage());
        mDialog = builder.create();
        mDialog.show();
        TextView messageText = (TextView)mDialog.findViewById(android.R.id.message);
        messageText.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
        mDialog.getWindow().setAttributes(lp);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(100,0,0,0)));
        mDialog.setOnKeyListener(
                (DialogInterface dlg, int keyCode, KeyEvent event) -> {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
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
                        case KeyEvent.KEYCODE_BACK:
                            return false;
                        default:
                            dlg.dismiss();
                            playbackFragment.getActivity().onKeyDown(event.getKeyCode(), event);
                            return true;
                    }
                    mPivotX = (float) xvalue * 0.01f;
                    mPivotY = (float) yvalue * 0.01f;
                    setScale();
                    mDialog.setMessage(getPivotMessage());
                    return true;
                }
        );
        mDialog.setOnDismissListener(
                (DialogInterface dialog) -> {
                    mDialog = null;
                    playbackFragment.hideNavigation();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(playbackFragment.getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.title_select_audiosync)
                .setView(R.layout.leanback_preference_widget_seekbar);
        mDialog = builder.create();
        mDialog.show();
        WindowManager.LayoutParams lp = mDialog.getWindow().getAttributes();
        lp.dimAmount = 0.0f; // Dim level. 0.0 - no dim, 1.0 - completely opaque
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
                        case KeyEvent.KEYCODE_BACK:
                            return false;
                        default:
                            dlg.dismiss();
                            playbackFragment.getActivity().onKeyDown(event.getKeyCode(), event);
                            return true;
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
        mDialog.setOnDismissListener(
                (DialogInterface dialog) -> {
                    mDialog = null;
                    playbackFragment.hideNavigation();
                }
        );
    }

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
    public void onAudioTrack() {
        playbackFragment.mAudioSelection = playbackFragment.trackSelector(C.TRACK_TYPE_AUDIO, playbackFragment.mAudioSelection,
                R.string.msg_audio_track, R.string.msg_audio_track_off, true, true);
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

}
