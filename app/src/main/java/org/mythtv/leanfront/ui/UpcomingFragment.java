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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.VerticalGridPresenter;

import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.Program;
import org.mythtv.leanfront.model.RecRuleSlot;
import org.mythtv.leanfront.model.RecordRule;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.presenter.MyPresenterSelector;

import java.util.Date;

public class UpcomingFragment extends GridFragment implements AsyncBackendCall.OnBackendCallListener {

    private final int ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_NONE;
    // 3 columns - program, override, edit
    private final int NUMBER_COLUMNS = 3;
    private final int PAGING_ROWS = 11;
    public boolean allStatusValues;

    private boolean mLoadInProgress;
    private boolean mDoingUpdate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        numberColumns = NUMBER_COLUMNS;
        pagingRows = PAGING_ROWS;
        if (savedInstanceState != null) {
            mDoingUpdate = savedInstanceState.getBoolean("mDoingUpdate", mDoingUpdate);
        }
        setupAdapter();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("mDoingUpdate",mDoingUpdate);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        setupGridData();
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new VerticalGridPresenter(ZOOM_FACTOR);
        presenter.setNumberOfColumns(NUMBER_COLUMNS);
        setGridPresenter(presenter);

        mGridAdapter = new ArrayObjectAdapter(new MyPresenterSelector(getContext()));
        setAdapter(mGridAdapter);

        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (mLoadInProgress)
                return;
            RecRuleSlot slot = (RecRuleSlot) item;
            switch (slot.cellType) {
                case RecRuleSlot.CELL_RULE:
                case RecRuleSlot.CELL_PENCIL:
                    updateRecRule(slot.rule, null, false);
                    break;
                case RecRuleSlot.CELL_PAPERCLIP:
                    updateRecRule(slot.rule, slot.program, true);
                    break;
                case RecRuleSlot.CELL_CHECKBOX:
                    allStatusValues = ! allStatusValues;
                    mGridAdapter.notifyArrayItemRangeChanged(0,1);
                    setupGridData();
                    break;
            }
        });
    }

    private void updateRecRule(RecordRule rule, Program program, boolean isOverride) {
        if (rule == null)
            return;
        int chanId;
        Date startTime ;
        if (program == null) {
            chanId = rule.chanId;
            startTime = rule.startTime;
        } else {
            chanId = program.chanId;
            startTime = program.startTime;
        }
        Intent intent = new Intent(getContext(), EditScheduleActivity.class);
        intent.putExtra(EditScheduleActivity.RECORDID, rule.recordId);
        intent.putExtra(EditScheduleActivity.STARTTIME, startTime);
        intent.putExtra(EditScheduleActivity.CHANID, chanId);
        intent.putExtra(EditScheduleActivity.ISOVERRIDE, isOverride);

        mDoingUpdate = true;
        startActivity(intent);
    }

    private void setupGridData() {
        if (mLoadInProgress)
            return;
        mLoadInProgress = true;
        AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
        call.setAllStatusValues(allStatusValues);
        if (mDoingUpdate)
            call.execute(Video.ACTION_PAUSE, Video.ACTION_GETUPCOMINGLIST);
        else
            call.execute(Video.ACTION_GETUPCOMINGLIST);
        mDoingUpdate = false;
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        int [] tasks = taskRunner.getTasks();
        switch (tasks[0]) {
            case Video.ACTION_GETUPCOMINGLIST:
                loadData(taskRunner.getXmlResult());
                break;
            case Video.ACTION_PAUSE:
                loadData(taskRunner.getXmlResults().get(1));
        }
    }

    void loadData(XmlNode result) {
        mLoadInProgress = false;
        mGridAdapter.clear();
        if (result == null)
            return;
        if (!isStarted)
            return;
        // Show All checkbox
        mGridAdapter.add(new RecRuleSlot(RecRuleSlot.CELL_CHECKBOX, this));
        mGridAdapter.add(new RecRuleSlot(RecRuleSlot.CELL_EMPTY));
        mGridAdapter.add(new RecRuleSlot(RecRuleSlot.CELL_EMPTY));

        XmlNode programNode = null;
        for (; ; ) {
            if (programNode == null)
                programNode = result.getNode("Programs").getNode("Program");
            else
                programNode = programNode.getNextSibling();
            if (programNode == null)
                break;
            XmlNode channelNode = programNode.getNode("Channel");
            RecordRule rule = new RecordRule().fromProgram(programNode);
            Program program = new Program(programNode, channelNode);
            mGridAdapter.add(new RecRuleSlot(RecRuleSlot.CELL_RULE, rule, program));
            mGridAdapter.add(new RecRuleSlot(RecRuleSlot.CELL_PAPERCLIP, rule, program));
            mGridAdapter.add(new RecRuleSlot(RecRuleSlot.CELL_PENCIL, rule, program));
        }
        updateAdapter();
    }

}
