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

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import org.mythtv.leanfront.R;

/*
 * SearchActivity for SearchFragment
 */
public class SearchActivity extends LeanbackActivity {
    private SearchFragment mFragment;
    long mMultipleKeyTime;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);
        mFragment = (SearchFragment) getSupportFragmentManager()
                .findFragmentById(R.id.search_fragment);
        mFragment.type = SearchFragment.TYPE_SEARCH;
    }

    @Override
    public boolean onSearchRequested() {
        if (mFragment.hasResults()) {
            startActivity(new Intent(this, SearchActivity.class));
        } else {
            mFragment.startRecognition();
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If there are no results found, press the left key to reselect the microphone
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !mFragment.hasResults()) {
            mFragment.focusOnSearch();
        }
        int direction = 1;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                long newKeyTime=System.currentTimeMillis();
                long diff = newKeyTime - mMultipleKeyTime;
                // Restrict key interval to 50ms to prevent crash
                if (diff < 50)
                    return true;
                mMultipleKeyTime=newKeyTime;
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
