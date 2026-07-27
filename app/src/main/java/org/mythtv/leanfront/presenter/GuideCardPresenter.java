/*
 * Copyright (c) 2019-2020 Peter Bennett
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

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import org.mythtv.leanfront.model.GuideSlot;

public class GuideCardPresenter extends Presenter {
    private final int mType;

    public GuideCardPresenter(int type) {
        super();
        mType = type;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(new GuideCardView(parent.getContext(), mType));
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        ((GuideCardView)viewHolder.view).updateUi((GuideSlot) item);
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
    }
}
