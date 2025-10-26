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

package org.mythtv.leanfront.ui.playback;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.SeekBar;

import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.ui.LeanbackActivity;

/**
 * Loads PlaybackFragment and delegates input from a game controller.
 * <br>
 * For more information on game controller capabilities with leanback, review the
 * <a href="https://developer.android.com/training/game-controllers/controller-input.html">docs</href>.
 */
public class PlaybackActivity extends LeanbackActivity {
    public static final String SHARED_ELEMENT_NAME = "hero";
    public static final String VIDEO = "Video";
    public static final String NOTIFICATION_ID = "NotificationId";
    public static final String BOOKMARK = "bookmark";
    public static final String POSBOOKMARK = "posbookmark";
    public static final String RECORDID = "recordid";
    public static final String ENDTIME = "endtime";

    private static final float GAMEPAD_TRIGGER_INTENSITY_ON = 0.5f;
    // Off-condition slightly smaller for button debouncing.
    private static final float GAMEPAD_TRIGGER_INTENSITY_OFF = 0.45f;
    private boolean gamepadTriggerPressed = false;
    private PlaybackFragment mPlaybackFragment;
    private boolean mArrowSkipJump;
    private RepeatListener rewindListener;
    private RepeatListener ffListener;
    private GestureDetector detector;
    private boolean isLongKeyPress;
    private long touchTime;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateTouchTime();
        setContentView(R.layout.activity_playback);
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof PlaybackFragment) {
            mPlaybackFragment = (PlaybackFragment) fragment;
        }
        // Touch screen
        rewindListener = new RepeatListener(400,100,mPlaybackFragment,-1);
        ffListener = new RepeatListener(400,100,mPlaybackFragment,1);
        detector = new GestureDetector(this, new GestureTap());
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    @Override
    public void onBackPressed() {
        if (mPlaybackFragment.canEnd())
            super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (processKey(keyCode))
            return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // This method will handle gamepad events.
        updateTouchTime();
        if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.rewind();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.fastForward();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF
                && event.getAxisValue(MotionEvent.AXIS_RTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF) {
            gamepadTriggerPressed = false;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    // Note that onTouchEvent does not get dispatched so we need this
    public boolean dispatchTouchEvent(MotionEvent ev) {
        updateTouchTime();
        boolean ret = false;
        int w = mPlaybackFragment.getView().getWidth();
        int h = mPlaybackFragment.getView().getHeight();
        float x = ev.getX();
        float y = ev.getY();
        if (y < h / 8 && x < w / 8) {
            ret = rewindListener.onTouch(ev);
        }
        else if (y < h / 8 && x > w * 7 / 8) {
            ret = ffListener.onTouch(ev);
        }
        else {
            rewindListener.cancel();
            ffListener.cancel();
        }

        // This is to act on a tap but ignore a swipe
        if (!ret) {
            detector.onTouchEvent(ev);
        }
        if (ret)
            return true;
        else
            return super.dispatchTouchEvent(ev);
    }

    boolean tapEvent() {
        if (!mPlaybackFragment.isControlsOverlayVisible())
            return mPlaybackFragment.mPlaybackActionListener.onTap();
        return false;
    }

    boolean doubleTap() {
        if (mPlaybackFragment.isControlsOverlayVisible())
            return mPlaybackFragment.mPlaybackActionListener.onDoubleTap();
        return false;
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event){
        updateTouchTime();
        int keycode = event.getKeyCode();
        int newKeyCode = -1;
        boolean overload = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View view = getCurrentFocus();
            boolean isSeekBar = view instanceof SeekBar;

            if ((keycode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keycode == KeyEvent.KEYCODE_ENTER)
                    && ! mPlaybackFragment.isControlsOverlayVisible()) {
                if (event.isLongPress()) {
                    isLongKeyPress = true;
                    newKeyCode = KeyEvent.KEYCODE_MENU;
                }
                else
                    return true;
            }

            if (keycode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                switch (mPlaybackFragment.optRewFF) {
                    case PlaybackFragment.CMD_REWFF:   newKeyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD; break;
                    case PlaybackFragment.CMD_JUMP:    newKeyCode = KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD; break;
                    case PlaybackFragment.CMD_SKIPCOM: newKeyCode = KeyEvent.KEYCODE_MEDIA_STEP_FORWARD; break;
                    default:                           newKeyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD; break;
                }
            }

            if (keycode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (mPlaybackFragment.optUpDown != PlaybackFragment.CMD_CONTROLS
                        && !mPlaybackFragment.isControlsOverlayVisible()) {
                    mArrowSkipJump = true;
                }
                mPlaybackFragment.tickle(mArrowSkipJump,!mArrowSkipJump);
                if (mArrowSkipJump || isSeekBar) {
                    overload = true;
                    switch (mPlaybackFragment.optLeftRight) {
                        case PlaybackFragment.CMD_REWFF:   newKeyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD; break;
                        case PlaybackFragment.CMD_JUMP:    newKeyCode = KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD; break;
                        case PlaybackFragment.CMD_SKIPCOM: newKeyCode = KeyEvent.KEYCODE_MEDIA_STEP_FORWARD; break;
                        default:                           newKeyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD; break;
                    }
                }
            }

            if (keycode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                switch (mPlaybackFragment.optRewFF) {
                    case PlaybackFragment.CMD_REWFF:   newKeyCode = KeyEvent.KEYCODE_MEDIA_REWIND;        break;
                    case PlaybackFragment.CMD_JUMP:    newKeyCode = KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD; break;
                    case PlaybackFragment.CMD_SKIPCOM: newKeyCode = KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD; break;
                    default:                           newKeyCode = KeyEvent.KEYCODE_MEDIA_REWIND;        break;
                }
            }

            if (keycode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (mPlaybackFragment.optUpDown != PlaybackFragment.CMD_CONTROLS
                        && !mPlaybackFragment.isControlsOverlayVisible()) {
                    mArrowSkipJump = true;
                }
                mPlaybackFragment.tickle(mArrowSkipJump, !mArrowSkipJump);
                if (mArrowSkipJump || isSeekBar) {
                    overload = true;
                    switch (mPlaybackFragment.optLeftRight) {
                        case PlaybackFragment.CMD_REWFF:   newKeyCode = KeyEvent.KEYCODE_MEDIA_REWIND;        break;
                        case PlaybackFragment.CMD_JUMP:    newKeyCode = KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD; break;
                        case PlaybackFragment.CMD_SKIPCOM: newKeyCode = KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD; break;
                        default:                           newKeyCode = KeyEvent.KEYCODE_MEDIA_REWIND;        break;
                    }
                }
            }

            if (keycode == KeyEvent.KEYCODE_DPAD_UP) {
                // Code to support dismissing the controls on up arrow
                if (!mArrowSkipJump && mPlaybackFragment.isControlsOverlayVisible()) {
                    if (mPlaybackFragment.onControlsUp())
                        return true;
                }
                else if (mPlaybackFragment.optUpDown != PlaybackFragment.CMD_CONTROLS) {
                    mArrowSkipJump = true;
                    mPlaybackFragment.tickle(mArrowSkipJump, !mArrowSkipJump);
                    overload = true;
                    switch (mPlaybackFragment.optUpDown) {
                        case PlaybackFragment.CMD_JUMP:    newKeyCode = KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD; break;
                        case PlaybackFragment.CMD_SKIPCOM: newKeyCode = KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD; break;
                    }
                }
                else
                    return true;
            }

            if (mPlaybackFragment.optUpDown != PlaybackFragment.CMD_CONTROLS
                    && keycode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!mPlaybackFragment.isControlsOverlayVisible()) {
                    mArrowSkipJump = true;
                }
                mPlaybackFragment.tickle(mArrowSkipJump, !mArrowSkipJump);
                if (mArrowSkipJump) {
                    overload = true;
                    switch (mPlaybackFragment.optUpDown) {
                        case PlaybackFragment.CMD_JUMP:    newKeyCode = KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD; break;
                        case PlaybackFragment.CMD_SKIPCOM: newKeyCode = KeyEvent.KEYCODE_MEDIA_STEP_FORWARD; break;
                    }
                }
            }
            if (newKeyCode == -1) {
                mArrowSkipJump = false;
                mPlaybackFragment.setActions(true);
            }
        }
        else if (event.getAction() == KeyEvent.ACTION_UP) {
            if ((keycode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keycode == KeyEvent.KEYCODE_ENTER)
                  && !isLongKeyPress) {
                boolean wasVisible = mPlaybackFragment.isControlsOverlayVisible();
                if (wasVisible && mArrowSkipJump)
                    wasVisible = false;
                mArrowSkipJump = false;
                mPlaybackFragment.tickle(false,!mArrowSkipJump);
                if (!wasVisible)
                    return true;
            }
            isLongKeyPress = false;
        }

        if (newKeyCode != -1) {
            return processKey(newKeyCode, overload);
        }
        boolean ret = super.dispatchKeyEvent(event);
        mPlaybackFragment.actionSelected(null);
        return ret;
    }


    private boolean processKey(int keyCode) {
        return processKey(keyCode,false);
    }

    // Process a key command. The keyCode may be changed by the caller
    // to a standard key code for an operation
    // override true indicates an overloaded key, normally up/down/left/right,
    // Must not tickle as that is already done
    private boolean processKey(int keyCode, boolean overload) {
        // Note: KeyEvent.KEYCODE_MEDIA_NEXT and KeyEvent.KEYCODE_MEDIA_PREVIOUS
        // are sent to methods next() and previous() of VideoPlayerGlue by the
        // framework.
        if (mPlaybackFragment == null
                || mPlaybackFragment.getPlaybackActionListener() == null)
            return false;
        switch(keyCode) {
            case KeyEvent.KEYCODE_BUTTON_L1:
                mPlaybackFragment.skipToPrevious();
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                mPlaybackFragment.skipToNext();
                return true;
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!overload)
                    mPlaybackFragment.tickle();
                mPlaybackFragment.rewind();
                return true;
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!overload)
                    mPlaybackFragment.tickle();
                mPlaybackFragment.fastForward();
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                finish();
                return true;
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
                mPlaybackFragment.getPlaybackActionListener().onAudioTrack();
                return true;
            case KeyEvent.KEYCODE_CAPTIONS:
                mPlaybackFragment.getPlaybackActionListener().onCaption();
                return true;
            // STEP = commercial skip, SKIP = jump, next = next video
            case KeyEvent.KEYCODE_MEDIA_STEP_FORWARD:
                if (!overload)
                    mPlaybackFragment.tickle();
                mPlaybackFragment.mPlaybackActionListener.skipComForward();
                return true;
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
                if (!overload)
                    mPlaybackFragment.tickle();
                mPlaybackFragment.jumpForward();
                return true;
            // STEP = commercial skip, SKIP = jump,
            // previous = start or previous video start
            case KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD:
                if (!overload)
                    mPlaybackFragment.tickle();
                mPlaybackFragment.mPlaybackActionListener.skipComBack();
                return true;
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
                if (!overload)
                    mPlaybackFragment.tickle();
                mPlaybackFragment.jumpBack();
                return true;
            case KeyEvent.KEYCODE_TV_ZOOM_MODE:
                mPlaybackFragment.getPlaybackActionListener().onAspect();
                return true;
            case KeyEvent.KEYCODE_ZOOM_IN:
                mPlaybackFragment.getPlaybackActionListener().zoom(1);
                return true;
            case KeyEvent.KEYCODE_ZOOM_OUT:
                mPlaybackFragment.getPlaybackActionListener().zoom(-1);
                return true;
            case KeyEvent.KEYCODE_MENU:
                return mPlaybackFragment.getPlaybackActionListener().onMenu();
            case KeyEvent.KEYCODE_BOOKMARK:
                mPlaybackFragment.getPlaybackActionListener().onBookmark();
                return true;
        }
        return false;
    }

    public long getTouchTime() {
        return touchTime;
    }

    public void updateTouchTime() {
        touchTime = System.currentTimeMillis();
    }

    class GestureTap extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            return tapEvent();
        }
        public boolean 	onDoubleTapEvent(@NonNull MotionEvent e) {
            return doubleTap();
        }

    }

}
