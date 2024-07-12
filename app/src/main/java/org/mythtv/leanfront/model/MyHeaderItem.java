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

package org.mythtv.leanfront.model;

import androidx.fragment.app.Fragment;
import androidx.leanback.widget.HeaderItem;

// This class is used for row headers as well as for group cells

public class MyHeaderItem extends HeaderItem implements ListItem {
    // For type values see MainFragment
    private int mItemType;
    private String mBaseName;
    private Fragment fragment;

    public MyHeaderItem(String name, int type, String baseName) {
        super(name);
        mItemType = type;
        mBaseName = baseName;
    }
    public MyHeaderItem(long id, String name) {
        super(id, name);
    }
    public int getItemType() { return mItemType; }
    public String getBaseName() { return mBaseName; }

    public Fragment getFragment() {
        return fragment;
    }

    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
    }
}
