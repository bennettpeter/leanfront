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

import android.content.Context;

import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;

import org.mythtv.leanfront.model.RowSlot;
import org.mythtv.leanfront.model.RecRuleSlot;

public class MyPresenterSelector extends PresenterSelector {

    private IconCardPresenter mIconCardPresenter;
    private IconCardPresenter mIconCardPresenterSmall;
    private GuideCardPresenter mGuideCardPresenter;
    private RecRuleCardPresenter mRecRuleCardPresenter;
    private IconCardPresenter mCheckboxCardPresenter;

    public MyPresenterSelector(Context context)
    {
        mIconCardPresenter = new IconCardPresenter(context, IconCardView.TYPE_LARGE);
        mIconCardPresenterSmall = new IconCardPresenter(context, IconCardView.TYPE_SMALL);
        mGuideCardPresenter = new GuideCardPresenter(GuideCardView.TYPE_SMALL);
        mRecRuleCardPresenter = new RecRuleCardPresenter(RecRuleCardView.TYPE_WIDE);
        mCheckboxCardPresenter = new IconCardPresenter(context, IconCardView.TYPE_WIDE);
    }

    @Override
    public Presenter getPresenter(Object item) {
        if (item instanceof RowSlot) {
            RowSlot slot = (RowSlot) item;
            switch (slot.cellType) {
                case RowSlot.CELL_LEFTARROW:
                case RowSlot.CELL_RIGHTARROW:
                    return mIconCardPresenter;
                case RecRuleSlot.CELL_PAPERCLIP:
                case RecRuleSlot.CELL_PENCIL:
                case RecRuleSlot.CELL_EMPTY:
                    return mIconCardPresenterSmall;
                case RecRuleSlot.CELL_RULE:
                    return mRecRuleCardPresenter;
                case RecRuleSlot.CELL_CHECKBOX:
                    return mCheckboxCardPresenter;
                default:
                    return mGuideCardPresenter;
            }
        }
        return null;
    }
}
