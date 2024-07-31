/*
 * Copyright (c) 2015 The Android Open Source Project
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnChildLaidOutListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;

import org.mythtv.leanfront.R;

import java.util.Date;

public class GridFragment extends Fragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private static final String TAG = "GridFragment";

    private ObjectAdapter mAdapter;
    private VerticalGridPresenter mGridPresenter;
    protected VerticalGridPresenter.ViewHolder mGridViewHolder;
    private OnItemViewSelectedListener mOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;
    protected int mSelectedPosition = -1;
    protected boolean isStarted;
    private BrowseSupportFragment.MainFragmentAdapter mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter(this);
    // These must be filled in the derived class for use with paging
    protected int numberColumns;
    protected ArrayObjectAdapter mGridAdapter;
    protected int pagingRows;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mSelectedPosition = savedInstanceState.getInt("mSelectedPosition", mSelectedPosition);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("mSelectedPosition",mSelectedPosition);
        super.onSaveInstanceState(outState);
    }

    /**
     * Sets the grid presenter.
     */
    public void setGridPresenter(VerticalGridPresenter gridPresenter) {
        if (gridPresenter == null) {
            throw new IllegalArgumentException("Grid presenter may not be null");
        }
        mGridPresenter = gridPresenter;
        mGridPresenter.setOnItemViewSelectedListener(mViewSelectedListener);
        if (mOnItemViewClickedListener != null) {
            mGridPresenter.setOnItemViewClickedListener(mOnItemViewClickedListener);
        }
    }

    /**
     * Returns the grid presenter.
     */
    public VerticalGridPresenter getGridPresenter() {
        return mGridPresenter;
    }

    /**
     * Sets the object adapter for the fragment.
     */
    public void setAdapter(ObjectAdapter adapter) {
        mAdapter = adapter;
        updateAdapter();
    }

    /**
     * Returns the object adapter.
     */
    public ObjectAdapter getAdapter() {
        return mAdapter;
    }

    final private OnItemViewSelectedListener mViewSelectedListener =
            new OnItemViewSelectedListener() {
                @Override
                public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                           RowPresenter.ViewHolder rowViewHolder, Row row) {
                    int position = mGridViewHolder.getGridView().getSelectedPosition();
                    gridOnItemSelected(position);
                    if (mOnItemViewSelectedListener != null) {
                        mOnItemViewSelectedListener.onItemSelected(itemViewHolder, item,
                                rowViewHolder, row);
                    }
                }
            };

    final private OnChildLaidOutListener mChildLaidOutListener =
            (parent, view, position, id) -> {
                if (position == 0) {
                    showOrHideTitle();
                }
            };

    /**
     * Sets an item selection listener.
     */
    public void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener = listener;
    }

    private void gridOnItemSelected(int position) {
        if (position != -1 && position != mSelectedPosition) {
            mSelectedPosition = position;
            showOrHideTitle();
        }
    }

    private void showOrHideTitle() {
        if (mGridViewHolder.getGridView().findViewHolderForAdapterPosition(mSelectedPosition)
                == null) {
            return;
        }
        if (!mGridViewHolder.getGridView().hasPreviousViewInSameRow(mSelectedPosition)) {
            mMainFragmentAdapter.getFragmentHost().showTitleView(true);
        } else {
            mMainFragmentAdapter.getFragmentHost().showTitleView(false);
        }
    }

    /**
     * Sets an item clicked listener.
     */
    public void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener = listener;
        if (mGridPresenter != null) {
            mGridPresenter.setOnItemViewClickedListener(mOnItemViewClickedListener);
        }
    }

    /**
     * Returns the item clicked listener.
     */
    public OnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    @Override
    public void onStart() {
        super.onStart();
        isStarted = true;
    }

    @Override
    public void onStop() {
        isStarted = false;
        super.onStop();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.grid_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewGroup gridDock = view.findViewById(R.id.browse_grid_dock);
        mGridViewHolder = mGridPresenter.onCreateViewHolder(gridDock);
        gridDock.addView(mGridViewHolder.view);
        mGridViewHolder.getGridView().setOnChildLaidOutListener(mChildLaidOutListener);

        BrowseSupportFragment.FragmentHost host = getMainFragmentAdapter().getFragmentHost();
        if (host != null)
            host.notifyViewCreated(mMainFragmentAdapter);
        updateAdapter();

    }

    @Override
    public void onResume() {
        BrowseSupportFragment.FragmentHost host = getMainFragmentAdapter().getFragmentHost();
        if (host != null)
            host.notifyDataReady(getMainFragmentAdapter());
        super.onResume();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mGridViewHolder = null;
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    /**
     * Gets the selected item position.
     */
    public int getSelectedPosition() {
        if(mGridViewHolder != null && mGridViewHolder.getGridView().getAdapter() != null) {
            return mGridViewHolder.getGridView().getSelectedPosition();
        }
        return -1;
    }

    /**
     * Sets the selected item position.
     */
    public void setSelectedPosition(int position, boolean smooth) {
        mSelectedPosition = position;
        if(mGridViewHolder != null && mGridViewHolder.getGridView().getAdapter() != null) {
            if (smooth)
                mGridViewHolder.getGridView().setSelectedPositionSmooth(position);
            else
                mGridViewHolder.getGridView().setSelectedPosition(position);
        }
    }

    void updateAdapter() {
        if (mGridViewHolder != null) {
            mGridPresenter.onBindViewHolder(mGridViewHolder, mAdapter);
            if (mSelectedPosition != -1) {
                mGridViewHolder.getGridView().setSelectedPosition(mSelectedPosition);
            }
        }
    }

    void pageDown(int direction) {
        int selectedCellNum = getSelectedPosition();
        int rowStart = selectedCellNum / numberColumns * numberColumns;
        int newPos = rowStart + pagingRows * numberColumns * direction;
        if (newPos < 0)
            newPos = 0;
        if (newPos >= mGridAdapter.size())
            newPos = mGridAdapter.size() - numberColumns;
        setSelectedPosition(newPos, false);
    }

}
