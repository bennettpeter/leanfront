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

package org.mythtv.leanfront.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.VerticalGridPresenter;

import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.RecRuleSlot;
import org.mythtv.leanfront.model.RecordRule;
import org.mythtv.leanfront.model.RowSlot;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.presenter.RecRuleCardPresenter;
import org.mythtv.leanfront.presenter.RecRuleCardView;

public class RecRulesFragment  extends GridFragment implements AsyncBackendCall.OnBackendCallListener {

    private final int ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_XSMALL;

    private boolean mLoadInProgress;
    private boolean mDoingUpdate;
    private final int PAGING_ROWS = 9;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pagingRows = PAGING_ROWS;
        if (savedInstanceState != null) {
            mDoingUpdate = savedInstanceState.getBoolean("mDoingUpdate", mDoingUpdate);
        }
        boolean isTelevision = getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        if (isTelevision)
            numberColumns = 3;
        else
            numberColumns = 1;
        setupAdapter();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("mDoingUpdate",mDoingUpdate);
        super.onSaveInstanceState(outState);
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new VerticalGridPresenter(ZOOM_FACTOR);
        presenter.setNumberOfColumns(numberColumns);
        setGridPresenter(presenter);

        int type;
        if (numberColumns == 1)
            type = RecRuleCardView.TYPE_WIDE;
        else
            type = RecRuleCardView.TYPE_SMALL;
        mGridAdapter = new ArrayObjectAdapter(new RecRuleCardPresenter(type));
        setAdapter(mGridAdapter);

        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (mLoadInProgress)
                return;
            RecordRule card = ((RecRuleSlot)item).rule;
                    recRuleClicked(card);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        setupGridData();
    }

    private void recRuleClicked(RecordRule card) {
        if (card == null)
            return;
        Intent intent = new Intent(getContext(), EditScheduleActivity.class);
        intent.putExtra(EditScheduleActivity.RECORDID, card.recordId);
        if ("Dummy_AddNew".equals(card.type)) {
            // Set up for new manual recording schedule
            intent.putExtra(EditScheduleActivity.SEARCHTYPE,EditScheduleActivity.SEARCH_MANUAL);
        }
        mDoingUpdate = true;
        startActivity(intent);
    }

    private void setupGridData() {
        if (mLoadInProgress)
            return;
        mLoadInProgress = true;
        AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
        if (mDoingUpdate)
            call.execute(Video.ACTION_PAUSE, Video.ACTION_GETRECORDSCHEDULELIST);
        else
            call.execute(Video.ACTION_GETRECORDSCHEDULELIST);
        mDoingUpdate = false;
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        int [] tasks = taskRunner.getTasks();
        switch (tasks[0]) {
            case Video.ACTION_GETRECORDSCHEDULELIST:
                loadData(taskRunner.getXmlResult());
                break;
            case Video.ACTION_PAUSE:
                loadData(taskRunner.getXmlResults().get(1));
        }
    }

    void loadData(XmlNode result) {
        mLoadInProgress = false;
        if (result == null)
            return;
        if (!isStarted)
            return;
        mGridAdapter.clear();
        // Add a grid entry for "New Recording Rule"
        RecordRule ruleNew = new RecordRule();
        ruleNew.type = "Dummy_AddNew";
        RecRuleSlot slot = new RecRuleSlot(RowSlot.CELL_RULE, ruleNew);
        mGridAdapter.add(slot);
        XmlNode recRuleNode = null;
        for (; ; ) {
            if (recRuleNode == null)
                recRuleNode = result.getNode("RecRules").getNode("RecRule");
            else
                recRuleNode = recRuleNode.getNextSibling();
            if (recRuleNode == null)
                break;
            RecordRule rule = new RecordRule().fromSchedule(recRuleNode);
            mGridAdapter.add(new RecRuleSlot(RowSlot.CELL_RULE, rule));
        }
        while (mGridAdapter.size() % numberColumns != 0)
            mGridAdapter.add(null);
        updateAdapter();
    }

}
