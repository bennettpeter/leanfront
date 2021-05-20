/*
 * Copyright (c) 2016 The Android Open Source Project
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

package org.mythtv.leanfront.presenter;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowHeaderPresenter;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.ui.MainFragment;
import org.mythtv.leanfront.model.MyHeaderItem;

public class IconHeaderItemPresenter extends RowHeaderPresenter {

    private float mUnselectedAlpha;
    private MyHeaderItem headerItem;
    private MainFragment mainFragment;

    public IconHeaderItemPresenter(MainFragment mainFragment) {
        this.mainFragment = mainFragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup) {
        mUnselectedAlpha = viewGroup.getResources()
                .getFraction(R.fraction.lb_browse_header_unselect_alpha, 1, 1);
        LayoutInflater inflater = (LayoutInflater) viewGroup.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.icon_header_item, null);
        view.setAlpha(mUnselectedAlpha); // Initialize icons to be at half-opacity.
        view.setOnLongClickListener( (v) -> mainFragment.onHeaderMenu(headerItem));
        view.setOnKeyListener( (v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_DOWN)
                return mainFragment.onHeaderMenu(headerItem);
            return false;
        });

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        headerItem = (MyHeaderItem) ((ListRow) item).getHeaderItem();
        View rootView = viewHolder.view;
        rootView.setFocusable(true);

        ImageView iconView = rootView.findViewById(R.id.header_icon);
        Drawable icon;
        String name = headerItem.getName();

        ObjectAdapter adapter = ((ListRow)item).getAdapter();
        int count = adapter.size();
        switch (headerItem.getItemType()) {
            case MainFragment.TYPE_TOOLS:
                icon = rootView.getResources().getDrawable(R.drawable.ic_tools, null);
                count = 0;
                break;
            case MainFragment.TYPE_VIDEODIR:
            case MainFragment.TYPE_VIDEODIR_ALL:
                icon = rootView.getResources().getDrawable(R.drawable.im_folder, null);
                break;
            case MainFragment.TYPE_RECGROUP:
            case MainFragment.TYPE_RECGROUP_ALL:
            case MainFragment.TYPE_TOP_ALL:
            case MainFragment.TYPE_SERIES:
                if ("LiveTV".equals(name)) {
                    icon = rootView.getResources().getDrawable(R.drawable.im_live_tv, null);
                }
                else
                    icon = rootView.getResources().getDrawable(R.drawable.ic_voicemail, null);
                break;
            case MainFragment.TYPE_RECENTS:
                icon = rootView.getResources().getDrawable(R.drawable.im_movie, null);
                break;
            case MainFragment.TYPE_CHANNEL:
            case MainFragment.TYPE_CHANNEL_ALL:
                icon = rootView.getResources().getDrawable(R.drawable.im_live_tv, null);
                break;
            default:
                icon = rootView.getResources().getDrawable(R.drawable.ic_launcher_lean, null);
        }
        iconView.setImageDrawable(icon);

        TextView label = rootView.findViewById(R.id.header_label);
        label.setText(headerItem.getName());
        label.setTextColor(rootView.getResources().getColor(R.color.header_text));

        TextView countView = rootView.findViewById(R.id.header_count);
        if (countView != null && count > 0)
            countView.setText(String.valueOf(count));
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        // no op
    }

    // TODO: This is a temporary fix. Remove me when leanback onCreateViewHolder no longer sets the
    // mUnselectAlpha, and also assumes the xml inflation will return a RowHeaderView.
    @Override
    protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder) {
        holder.view.setAlpha(mUnselectedAlpha + holder.getSelectLevel() *
                (1.0f - mUnselectedAlpha));
    }
}
