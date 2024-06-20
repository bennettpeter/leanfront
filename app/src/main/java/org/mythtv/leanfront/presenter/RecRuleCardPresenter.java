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

import androidx.leanback.widget.Presenter;

import org.mythtv.leanfront.model.RecRuleSlot;

public class RecRuleCardPresenter extends Presenter {
    private int mType;

    public RecRuleCardPresenter(int type) {
        super();
        mType = type;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(new RecRuleCardView(parent.getContext(), mType));
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
    if (item instanceof RecRuleSlot)
        ((RecRuleCardView)viewHolder.view).updateUi(((RecRuleSlot) item).rule);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }
}
