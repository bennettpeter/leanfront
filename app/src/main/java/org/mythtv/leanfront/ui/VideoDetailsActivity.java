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

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;
import androidx.leanback.widget.Action;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.Video;

/*
 * Details activity class that loads VideoDetailsFragment class
 */
public class VideoDetailsActivity extends LeanbackActivity {

    private VideoDetailsFragment mFragment;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_details);
        Fragment fragment =
                getSupportFragmentManager().findFragmentById(R.id.details_fragment);
        if (fragment instanceof VideoDetailsFragment) {
            mFragment = (VideoDetailsFragment) fragment;
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction = 1;
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (mFragment != null) {
                    mFragment.onActionClicked(new Action(Video.ACTION_PLAY_FROM_LASTPOS));
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MENU:
                mFragment.onActionClicked(new Action(Video.ACTION_OTHER));
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                direction = -1;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (mFragment != null) {
                    mFragment.pageDown(direction);
                    return true;
                }
                break;

        }
        return super.onKeyDown(keyCode, event);
    }

}



