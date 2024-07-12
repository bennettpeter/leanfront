package org.mythtv.leanfront.ui;

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;
import androidx.leanback.widget.Action;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.Video;

public class ManageRecordingsActivity extends LeanbackActivity {
    ManageRecordingsFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_recordings);
        Fragment fragment =
                getSupportFragmentManager().findFragmentById(R.id.manage_recordings_fragment);
        if (fragment instanceof ManageRecordingsFragment) {
            mFragment = (ManageRecordingsFragment) fragment;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction = 1;
        switch (keyCode) {
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
