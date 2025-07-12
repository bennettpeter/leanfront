package org.mythtv.leanfront.mobile;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import org.mythtv.leanfront.R;

public class MainActivity extends FragmentActivity {
    public static boolean isMobile = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        boolean isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        if (isTV && org.mythtv.leanfront.ui.MainActivity.isLeanback) {
            startActivity(new Intent(this, org.mythtv.leanfront.ui.MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
    }
}
