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

import static android.app.ProgressDialog.show;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.leanback.app.ProgressBarManager;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.VerticalGridPresenter;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.GuideSlot;
import org.mythtv.leanfront.model.Program;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.presenter.MyPresenterSelector;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class GuideFragment extends GridFragment implements AsyncBackendCall.OnBackendCallListener{

    public static final int TIMESLOTS = 8;
    // 1 cell per timeslot plus 1 for channel and two for arrows
    public static final int COLUMNS = TIMESLOTS+3;
    public static final int TIMESLOT_SIZE = 30; //minutes
    public static final int TIME_ROW_INTERVAL = 8;
    public static final int DATE_RANGE = 21;
    private final int ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_XSMALL;
    private ArrayObjectAdapter mGridAdapter;
    private Date mGridStartTime;
    private long mPriorGridStartTime;
    // map chanid to position in object adapter
    private SparseIntArray mChanArray = new SparseIntArray();
    private GuideSlot [] mTimeRow;
    private static DateFormat mTimeFormatter;
    private static DateFormat mDateFormatter;
    private static DateFormat mDayFormatter;
    private GregorianCalendar mTimeSelectCalendar;
    private AlertDialog mDialog;
    private XmlNode mLoadInProcess;
    private boolean mDoingUpdate;
    private ArrayList<String> mChanGroupNames;
    private ArrayList<Integer> mChanGroupIDs;
    private int mChanGroupIx = -1;
    private static final int ACTION_EDIT_1 = 1;
    private static final int ACTION_EDIT_2 = 2;
    private ProgressBarManager pbm;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            long newTime = savedInstanceState.getLong("mGridStartTime", 0);
            if (newTime > 0)
                mGridStartTime = new Date(newTime);
            mChanGroupIx = savedInstanceState.getInt("mChanGroupIx", mChanGroupIx);
            mDoingUpdate = savedInstanceState.getBoolean("mDoingUpdate", mDoingUpdate);
        }
        if (mGridStartTime == null) {
            long now = System.currentTimeMillis();
            // round down to 30 minute interval
            long startTime = now / (TIMESLOT_SIZE * 60000);
            startTime = startTime * TIMESLOT_SIZE * 60000;
            mGridStartTime = new Date(startTime);
        }
        setupAdapter();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pbm = new ProgressBarManager();
        pbm.setRootView((ViewGroup)view);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putLong("mGridStartTime",mGridStartTime.getTime());
        outState.putInt("mChanGroupIx",mChanGroupIx);
        outState.putBoolean("mDoingUpdate",mDoingUpdate);
        super.onSaveInstanceState(outState);
    }

    private void setupAdapter() {
        VerticalGridPresenter presenter = new VerticalGridPresenter(ZOOM_FACTOR);
        // 1 cell per timeslot plus 1 for channel and two for arrows
        presenter.setNumberOfColumns(COLUMNS);
        setGridPresenter(presenter);

        mGridAdapter = new ArrayObjectAdapter(new MyPresenterSelector(getContext()));
        setAdapter(mGridAdapter);

        setOnItemViewClickedListener((itemViewHolder, item, rowViewHolder, row) -> {
            GuideSlot card = (GuideSlot)item;
            if (card == null)
                return;
            switch (card.cellType) {
                case GuideSlot.CELL_TIMESELECTOR:
                case GuideSlot.CELL_TIMESLOT:
                    showTimeSelector();
                    break;
                case GuideSlot.CELL_CHANNEL:
                    showChannelSelector(card);
                    break;
                case GuideSlot.CELL_LEFTARROW:
                    mGridStartTime = new Date(mGridStartTime.getTime() - TIMESLOTS * TIMESLOT_SIZE * 60000);
                    setupGridData();
                    break;
                case GuideSlot.CELL_RIGHTARROW:
                    mGridStartTime = new Date(mGridStartTime.getTime() + TIMESLOTS * TIMESLOT_SIZE * 60000);
                    setupGridData();
                    break;
                case GuideSlot.CELL_PROGRAM:
                    programClicked(card);
                    break;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        setupGridData();
    }

    private void programClicked(GuideSlot card) {
        String[] prompts = new String[10];
        int[] actions = new int[10];
        int counter = 0;
        if (card.program != null) {
            prompts[counter] = getContext().getString(R.string.msg_edit_schedule, card.program.title);
            actions[counter] = ACTION_EDIT_1;
            ++counter;
        }
        if (card.program2 != null) {
            prompts[counter] = getContext().getString(R.string.msg_edit_schedule2, card.program2.title);
            actions[counter] = ACTION_EDIT_2;
            ++counter;
        }

        if (counter == 1) {
            actionRequest(card,actions[0]);
        } else if (counter > 1) {
            final String[] finalPrompts = new String[counter];
            final int[] finalActions = new int[counter];
            for (int i = 0; i < counter; i++) {
                finalPrompts[i] = prompts[i];
                finalActions[i] = actions[i];
            }
            String alertTitle = getContext().getString(R.string.title_program_guide);
            // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
                    R.style.Theme_AppCompat_Dialog_Alert);
            builder .setTitle(alertTitle)
                    .setItems(finalPrompts,
                            (dialog, which) -> {
                                // The 'which' argument contains the index position
                                // of the selected item
                                if (which < finalActions.length) {
                                    actionRequest(card,finalActions[which]);
                                }
                            });
            builder.show();
        }
    }

    private void actionRequest(GuideSlot card, int action) {
        Intent intent;
        mDoingUpdate = true;
        switch (action) {
            case ACTION_EDIT_1:
                intent = new Intent(getContext(), EditScheduleActivity.class);
                intent.putExtra(EditScheduleActivity.CHANID, card.program.chanId);
                intent.putExtra(EditScheduleActivity.STARTTIME, card.program.startTime);
                startActivity(intent);
                break;
            case ACTION_EDIT_2:
                intent = new Intent(getContext(), EditScheduleActivity.class);
                intent.putExtra(EditScheduleActivity.CHANID, card.program2.chanId);
                intent.putExtra(EditScheduleActivity.STARTTIME, card.program2.startTime);
                startActivity(intent);
                break;
        }
    }

    @SuppressLint("SimpleDateFormat")
    private void showTimeSelector() {
        Context context = getContext();
        if (mTimeFormatter == null) {
            mTimeFormatter = android.text.format.DateFormat.getTimeFormat(context);
            mDateFormatter = android.text.format.DateFormat.getLongDateFormat(context);
            mDayFormatter = new SimpleDateFormat("EEE ");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setTitle(R.string.title_select_timeslot)
                .setView(R.layout.guide_time_select_layout);
        builder.setPositiveButton(android.R.string.ok,
            (dialog, which) -> {
                Spinner dateSpin = mDialog.findViewById(R.id.date_select);
                Spinner timeSpin = mDialog.findViewById(R.id.time_select);
                long newStartTime = mTimeSelectCalendar.getTimeInMillis()
                        + (long) dateSpin.getSelectedItemPosition() * 24*60*60*1000
                        + (long) timeSpin.getSelectedItemPosition() * TIMESLOT_SIZE * 60 * 1000;
                mGridStartTime = new Date(newStartTime);
                Spinner groupSpin = mDialog.findViewById(R.id.group_select);
                int newIx = groupSpin.getSelectedItemPosition();
                if (newIx != mChanGroupIx) {
                    // Clear this to make sure grid is rebuilt
                    mPriorGridStartTime = 0;
                    mSelectedPosition = 0;
                    mChanGroupIx = newIx;
                }
                SharedPreferences.Editor editor = Settings.getEditor();
                Settings.putString(editor,"chan_group", mChanGroupNames.get(mChanGroupIx));
                editor.commit();
                setupGridData();
            } );
        builder.setNegativeButton(android.R.string.cancel, null);
        mDialog = builder.create();
        mDialog.show();
        Spinner groupSpin=mDialog.findViewById(R.id.group_select);
        ArrayAdapter<String> adapter =new ArrayAdapter<>(context,android.R.layout.simple_spinner_item );
        adapter.addAll(mChanGroupNames);
        groupSpin.setAdapter(adapter);
        if (mChanGroupIx > -1)
            groupSpin.setSelection(mChanGroupIx,false);
        Spinner dateSpin=mDialog.findViewById(R.id.date_select);
        adapter = new ArrayAdapter<>(context,android.R.layout.simple_spinner_item );
        mTimeSelectCalendar = new GregorianCalendar();
        mTimeSelectCalendar.set(GregorianCalendar.HOUR_OF_DAY,0);
        mTimeSelectCalendar.set(GregorianCalendar.MINUTE,0);
        mTimeSelectCalendar.set(GregorianCalendar.SECOND,0);
        mTimeSelectCalendar.set(GregorianCalendar.MILLISECOND,0);
        // 1 second after midnight this morning real time.
        long millis = mTimeSelectCalendar.getTimeInMillis() + 1000;
        long startTimeMillis = mGridStartTime.getTime();
        int dateSelection = -1;
        for (int i = 0; i < DATE_RANGE; i++) {
            Date date = new Date(millis);
            adapter.add(mDayFormatter.format(date) + mDateFormatter.format(date));
            long timediff = startTimeMillis - millis;
            if (dateSelection == -1 && timediff < 24*60*60000)
                dateSelection = i;
            millis += (long)(24*60*60*1000);
        }
        dateSpin.setAdapter(adapter);
        Spinner timeSpin=mDialog.findViewById(R.id.time_select);
        adapter = new ArrayAdapter<>(context,android.R.layout.simple_spinner_item );
        millis = mTimeSelectCalendar.getTimeInMillis(); // midnight last night real time
        GregorianCalendar cal2 = new GregorianCalendar();
        cal2.setTime(mGridStartTime);
        int hour = cal2.get(Calendar.HOUR_OF_DAY);
        int min = cal2.get(Calendar.MINUTE);
        cal2.setTimeInMillis(mTimeSelectCalendar.getTimeInMillis());
        cal2.set(Calendar.HOUR_OF_DAY,hour);
        cal2.set(Calendar.MINUTE,min);
        startTimeMillis = cal2.getTimeInMillis();
        int timeSelection = -1;
        for (int i = 0; i < 24*60/TIMESLOT_SIZE; i++) {
            adapter.add(mTimeFormatter.format(new Date(millis)));
            long timediff = startTimeMillis - millis;
            if (timeSelection == -1 && timediff < TIMESLOT_SIZE*60000)
                timeSelection = i;
            millis += (long)(TIMESLOT_SIZE*60*1000);
        }
        timeSpin.setAdapter(adapter);
        dateSpin.setSelection(dateSelection);
        timeSpin.setSelection(timeSelection);
    }

    private void showChannelSelector(GuideSlot card) {
        Context context = getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                R.style.Theme_AppCompat_Dialog_Alert);
        builder.setView(R.layout.guide_channel_select_layout);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> goToChannel(card));
        builder.setNegativeButton(android.R.string.cancel, null);
        mDialog = builder.create();
        mDialog.show();
        RadioButton bnJump = mDialog.findViewById(R.id.jump_button);
        EditText textChan = mDialog.findViewById(R.id.chan_num);
        bnJump.setOnCheckedChangeListener( (buttonView, isChecked) -> {
            textChan.setEnabled(isChecked);
            textChan.setText("");
        });
    }

    private void goToChannel(GuideSlot card) {
        RadioButton bnJump = mDialog.findViewById(R.id.jump_button);
        RadioButton bnPlay = mDialog.findViewById(R.id.play_button);
        if (bnJump.isChecked()) {
            EditText textChan = mDialog.findViewById(R.id.chan_num);
            String text = textChan.getText().toString();
            int chanNum = 0;
            try {
                chanNum = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return;
            }
            int size = mGridAdapter.size();
            boolean found = false;
            for (int ix = 0; ix < size; ix += COLUMNS) {
                GuideSlot slot = (GuideSlot) mGridAdapter.get(ix);
                if (slot.chanNum >= chanNum) {
                    setSelectedPosition(ix, false);
                    found = true;
                    break;
                }
            }
            if (!found)
                setSelectedPosition(size - COLUMNS, false);
        }
        else if (bnPlay.isChecked()) {
            setProgressBar(true);
            AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
            // null StartTime means start now
            call.setStartTime(null);
            call.setChanid(card.chanId);
            call.setCallSign(card.callSign);
            call.execute(Video.ACTION_LIVETV, Video.ACTION_ADD_OR_UPDATERECRULE, Video.ACTION_WAIT_RECORDING);
        }
    }

    void pageDown(int direction) {
        int selectedCellNum = getSelectedPosition();
        int rowStart = selectedCellNum / COLUMNS * COLUMNS;
        int newPos = rowStart + 45 * COLUMNS * direction; // 9 = 1 page
        if (newPos < 0)
            newPos = 0;
        if (newPos >= mGridAdapter.size())
            newPos = mGridAdapter.size() - COLUMNS;
        setSelectedPosition(newPos, false);
    }

    private void setupGridData() {
        Date gridEndTime = new Date(mGridStartTime.getTime() + TIMESLOT_SIZE * TIMESLOTS * 60000);
        AsyncBackendCall call = new AsyncBackendCall(getActivity(),this);
        if (mChanGroupIDs == null)
            // Note that after ACTION_CHAN_GROUPS completes it will call ACTION_GUIDE
            call.execute(Video.ACTION_CHAN_GROUPS);
        else {
            call.setStartTime(mGridStartTime);
            call.setEndTime(gridEndTime);
            call.setId(mChanGroupIDs.get(mChanGroupIx));
            if (mDoingUpdate) {
                call.execute(Video.ACTION_PAUSE, Video.ACTION_GUIDE);
            }
            else {
                call.execute(Video.ACTION_GUIDE);
            }
            mDoingUpdate = false;
        }
    }

    /**
     * Preload the grid with timeslots for each channel.
     * TIMESLOT_SIZE minutes for each cell.
     * 1 cell per timeslot plus 1 for channel and two for arrows
     */
    private void loadCells(XmlNode result) {
        if (mPriorGridStartTime > 0) {
            updateCells();
            return;
        }
        mGridAdapter.clear();
        mPriorGridStartTime = mGridStartTime.getTime();

        // arrow slots
        GuideSlot leftArrowSlot = new GuideSlot(GuideSlot.CELL_LEFTARROW);
        GuideSlot rightArrowSlot = new GuideSlot(GuideSlot.CELL_RIGHTARROW);

        // Counter to ensure new time row every few rows.
        int tsRowCount = 0;
        setupTimeRow(leftArrowSlot, rightArrowSlot);

        XmlNode chanNode = null;
        for (; ; ) {
            if (chanNode == null)
                chanNode = result.getNode("Channels").getNode("ChannelInfo");
            else
                chanNode = chanNode.getNextSibling();
            if (chanNode == null)
                break;
            if (tsRowCount == 0)
                addTimeRow();
            if (++tsRowCount >= TIME_ROW_INTERVAL)
                tsRowCount = 0;
            String callSign = chanNode.getString("CallSign");
            String chanDetails = chanNode.getString("ChanNum")
                    + " " + chanNode.getString("ChannelName")
                    + " " + callSign;
            int chanId = chanNode.getInt("ChanId", 0);
            String chanNumStr = chanNode.getString("ChanNum");
            int chanNum = -1;
            if (chanNumStr != null) {
                String[] chanNumSplit = chanNumStr.split("[^0123456789]");
                if (chanNumSplit.length > 0 && chanNumSplit[0] != null
                  && chanNumSplit[0].length() > 0)
                    chanNum = Integer.parseInt(chanNumSplit[0]);
            }
            // channel slot at front
            GuideSlot slot = new GuideSlot(chanId, chanNum, callSign, chanDetails);
            mGridAdapter.add(slot);
            mGridAdapter.add(leftArrowSlot);
            mChanArray.put(chanId,mGridAdapter.size());
            for (int i = 0; i< TIMESLOTS; i++) {
                int position;
                switch (i) {
                    case 0:
                        position = GuideSlot.POS_LEFT;
                        break;
                    case TIMESLOTS-1:
                        position = GuideSlot.POS_RIGHT;
                        break;
                    default:
                        position = GuideSlot.POS_MIDDLE;
                }
                slot = new GuideSlot(GuideSlot.CELL_PROGRAM, position, mTimeRow[i+2].timeSlot);
                mGridAdapter.add(slot);
            }
            mGridAdapter.add(rightArrowSlot);
        }
        // Make sure time cells are refreshed
        updateAdapter();
    }

    private void updateCells() {
        // Update Time row
        mTimeRow[0].timeSlot = mGridStartTime;
        mPriorGridStartTime = mGridStartTime.getTime();
        for (int ix = 0; ix< TIMESLOTS; ix++) {
            mTimeRow[ix+2].timeSlot =
                    new Date( mGridStartTime.getTime() + ix * TIMESLOT_SIZE * 60000);
        }
        // Clear out program cells
        for (int ix = 0; ix < mGridAdapter.size(); ix++) {
            GuideSlot slot = (GuideSlot) mGridAdapter.get(ix);
            int iPos = ix % (TIMESLOTS + 3);
            if (slot.cellType == GuideSlot.CELL_PROGRAM) {
                slot.program = null;
                slot.program2 = null;
                slot.timeSlot = mTimeRow[iPos].timeSlot;
            }
        }
        // Make sure time cells are refreshed
        updateAdapter();
    }

    private void setupTimeRow(GuideSlot leftArrowSlot, GuideSlot rightArrowSlot) {
        mTimeRow = new GuideSlot[TIMESLOTS + 3];
        // time selector slot at front
        mTimeRow[0] = new GuideSlot(GuideSlot.CELL_TIMESELECTOR);
        mTimeRow[0].timeSlot = mGridStartTime;
        mTimeRow[0].chanGroup = mChanGroupNames.get(mChanGroupIx);
        mTimeRow[1] = leftArrowSlot;
        for (int ix = 0; ix< TIMESLOTS; ix++) {
            mTimeRow[ix+2] = new GuideSlot(GuideSlot.CELL_TIMESLOT,0,
                    new Date( mGridStartTime.getTime() + ix * TIMESLOT_SIZE * 60000));
        }
        mTimeRow[TIMESLOTS+2] = rightArrowSlot;
    }

    private void addTimeRow() {
        for (GuideSlot guideSlot : mTimeRow) mGridAdapter.add(guideSlot);
    }


    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        int [] tasks = taskRunner.getTasks();
        switch (tasks[0]) {
            case Video.ACTION_CHAN_GROUPS:
                loadChanGroups(taskRunner.getXmlResult());
                setupGridData();
                break;
            case Video.ACTION_GUIDE:
                mLoadInProcess = taskRunner.getXmlResult();
                loadGuideData(mLoadInProcess, 0);
                break;
            case Video.ACTION_PAUSE:
                mLoadInProcess = taskRunner.getXmlResults().get(1);
                loadGuideData(mLoadInProcess, 0);
                break;
            case Video.ACTION_LIVETV:
                setProgressBar(false);
                Video video = taskRunner.getVideo();
                Context context = getContext();
                // video null means recording failed
                // activity null means user pressed back button
                if (video == null || context == null) {
                    if (context != null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                                R.style.Theme_AppCompat_Dialog_Alert);
                        builder.setTitle(R.string.title_alert_livetv);
                        String msg = context.getString(R.string.alert_livetv_fail_message,taskRunner.getStringParameter());
                        builder.setMessage(msg);
                        // add a button
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.show();
                    }
                    long recordId = taskRunner.getRecordId();
                    long recordedId = taskRunner.getRecordedId();
                    video = new Video.VideoBuilder()
                            .recGroup("LiveTV")
                            .recordedid(String.valueOf(recordedId))
                            .build();
                    if (recordId >= 0) {
                        // Terminate Live TV
                        AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
                        call.setVideo(video);
                        call.setRecordId(recordId);
                        call.setRecordedId(Integer.parseInt(video.recordedid));
                        call.execute(
                                Video.ACTION_STOP_RECORDING,
                                Video.ACTION_REMOVE_RECORD_RULE);
                    }
                    break;
                }
                Intent intent = new Intent(context, PlaybackActivity.class);
                intent.putExtra(PlaybackActivity.VIDEO, video);
                intent.putExtra(PlaybackActivity.BOOKMARK, 0L);
                intent.putExtra(PlaybackActivity.RECORDID, taskRunner.getRecordId());
                intent.putExtra(PlaybackActivity.ENDTIME, taskRunner.getEndTime().getTime());
                startActivity(intent);
                break;
        }
    }

    private void setProgressBar(boolean show) {
        // Initial delay defaults to 1000 (1 second)
        if (show)
            pbm.show();
        else
            pbm.hide();
    }

    void loadChanGroups(XmlNode result) {
        if (result == null)
            return;
        mChanGroupIDs = new ArrayList<>();
        mChanGroupIDs.add(0);
        mChanGroupNames = new ArrayList<>();
        mChanGroupNames.add(getContext().getString(R.string.all_title) + "\t");
        XmlNode groupNode = null;
        for (; ; ) {
            if (groupNode == null)
                groupNode = result.getNode("ChannelGroups").getNode("ChannelGroup");
            else
                groupNode = groupNode.getNextSibling();
            if (groupNode == null)
                break;
            mChanGroupIDs.add(groupNode.getInt("GroupId",0));
            mChanGroupNames.add(groupNode.getString("Name"));
        }
        String defGroupName = Settings.getString("chan_group");
        mChanGroupIx = mChanGroupNames.indexOf(defGroupName);
        if (mChanGroupIx < 0)
            mChanGroupIx = 0;
    }

    // Number of rows to add before pausing to update display and allow
    // user intraction.
    static final int pageSize = 10;
    // Number of milliseconds to pause after each page.
    static final int pauseTime = 10;
    void loadGuideData(XmlNode result, int start) {
        // If the user has changed toime period or channel group,
        // throw away furter use of the old group or time slot
        if (result == null || result != mLoadInProcess)
            return;
        if (!isStarted)
            return;
        if (start == 0)
            loadCells(result);
        XmlNode chanNode = null;
        for (; ; ) {
            if (chanNode == null)
                chanNode = result.getNode("Channels").getNode("ChannelInfo", start);
            else
                chanNode = chanNode.getNextSibling();
            if (chanNode == null)
                break;
            XmlNode programNode = null;
            for (; ; ) {
                if (programNode == null)
                    programNode = chanNode.getNode("Programs").getNode("Program");
                else
                    programNode = programNode.getNextSibling();
                if (programNode == null)
                    break;
                Program program = new Program(programNode, chanNode);
                int adapterPos = mChanArray.get(program.chanId, -1);
                if (adapterPos == -1)
                    continue;
                if (program.startTime == null || program.endTime == null)
                    continue;

                long lPos = (program.startTime.getTime() - mGridStartTime.getTime())
                        / (TIMESLOT_SIZE * 60);
                float fPos = (float) lPos / 1000.0f;
                // Start position is the slot wherein the show starts.
                int startPos = (int) (fPos);
                if (startPos >= TIMESLOTS)
                    continue;
                if (startPos < 0)
                    startPos = 0;

                lPos = (program.endTime.getTime() - mGridStartTime.getTime())
                        / (TIMESLOT_SIZE * 60);
                fPos = (float) lPos / 1000.0f;
                // End position is the slot before the one where the show ends
                // unless it ends in the same slot as it starts.
                int endPos = (int) (fPos);
                if (endPos <= 0)
                    continue;
                if (endPos >= TIMESLOTS)
                    endPos = TIMESLOTS;
                if (endPos == startPos)
                    ++endPos;

                for (int ix = adapterPos + startPos; ix < adapterPos + endPos; ix++) {
                    GuideSlot slot = (GuideSlot) mGridAdapter.get(ix);
                    if (slot.program == null)
                        slot.program = program;
                    else if (slot.program2 == null) {
                        if (program.startTime.after(slot.program.startTime))
                            slot.program2 = program;
                        else {
                            slot.program2 = slot.program;
                            slot.program = program;
                        }
                    }
                    mGridAdapter.notifyArrayItemRangeChanged(ix, 1);

                }
            }
            if (++start % pageSize == 0) {
                final int nextstart = start;
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        GuideFragment.this.loadGuideData(result, nextstart);
                    }
                }, pauseTime);
                return;
            }
        }
        mLoadInProcess = null;
    }



}
