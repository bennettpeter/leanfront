package org.mythtv.leanfront.ui;

import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_CALLSIGN;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_CHANID;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_CHANNUM;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_RECTYPE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.COLUMN_SUBTITLE;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.RECTYPE_CHANNEL;
import static org.mythtv.leanfront.data.VideoContract.VideoEntry.VIEW_NAME;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.GuidedActionsStylist;
import androidx.leanback.widget.GuidedDatePickerAction;

import com.kostyabakay.guidedtimepickeraction.GuidedActionsStylistExtended;
import com.kostyabakay.guidedtimepickeraction.GuidedTimePickerAction;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.VideoDbHelper;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.RecordRule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@SuppressLint("SimpleDateFormat")
public class CreateManualSchedule  extends GuidedStepSupportFragment {
    private static final String TAG = "lfe";
    private static final String CLASS = "CreateManualSchedule";
    private ArrayList<XmlNode> mDetailsList;
    private int mRecordId;
    private int searchType;
    private int chanid;
    private String chanName;
    private String station;
    private ArrayList<String> names = new ArrayList<>();
    private ArrayList<Integer> chanids = new ArrayList<>();
    private ArrayList<String> callSigns = new ArrayList<>();
    private int selection = -1;
    private GuidedAction actionTitle;
    private GuidedAction actionSubtitle;
    private GuidedAction actionChannel;
    private GuidedDatePickerAction actionDate;
    private GuidedTimePickerAction actionTime;
    private GuidedAction actionDuration;
    private GuidedAction actionNext;

    private static final int ID_TITLE    = 1;
    private static final int ID_SUBTITLE = 2;
    private static final int ID_CHANNEL  = 3;
    private static final int ID_DATE     = 4;
    private static final int ID_TIME     = 5;
    private static final int ID_DURATION = 6;
    private static final int ID_NEXT     = 7;

    public CreateManualSchedule(ArrayList<XmlNode> detailsList, int recordId, int searchType) {
        /*
            Details are in this order
                Video.ACTION_GETPROGRAMDETAILS,
                Video.ACTION_GETRECORDSCHEDULELIST,
                Video.ACTION_GETPLAYGROUPLIST,
                Video.ACTION_GETRECGROUPLIST,
                Video.ACTION_GETRECSTORAGEGROUPLIST,
                Video.ACTION_GETINPUTLIST
                Video.ACTION_GETRECRULEFILTERLIST
         */
        mDetailsList = detailsList;
        mRecordId= recordId;
        this.searchType = searchType;
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        Activity activity = getActivity();
        String title = getContext().getString(R.string.sched_manual_srch);
        Drawable icon = activity.getDrawable(R.drawable.ic_voicemail);
        return new GuidanceStylist.Guidance(title, null, null, icon);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> mainActions, Bundle savedInstanceState) {
        mainActions.add(actionTitle = new GuidedAction.Builder(getActivity())
                .id(ID_TITLE)
                .title(R.string.sched_title)
                .description("")
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_TEXT)
                .build());
        mainActions.add(actionSubtitle = new GuidedAction.Builder(getActivity())
                .id(ID_SUBTITLE)
                .title(R.string.sched_subtitle)
                .description("")
                .descriptionEditable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_TEXT)
                .build());
        mainActions.add(actionChannel = new GuidedAction.Builder(getActivity())
                .id(ID_CHANNEL)
                .title(R.string.sched_channel)
                .build());
        mainActions.add(actionDate = new GuidedDatePickerAction.Builder(getActivity())
                .id(ID_DATE)
                .date(System.currentTimeMillis())
                .maxDate(System.currentTimeMillis()+ 1000L * 60L * 60L * 24L * 30L)
                .minDate(System.currentTimeMillis())
                .title(R.string.sched_date)
                .build());
        mainActions.add(actionTime = new GuidedTimePickerAction.Builder(getActivity())
                .id(ID_TIME)
                .time(System.currentTimeMillis())
                .title(R.string.sched_time)
                .build());
        mainActions.add(actionDuration = new GuidedAction.Builder(getActivity())
                .id(ID_DURATION)
                .title(R.string.sched_duration)
                .description("60")
                .descriptionEditable(true)
                .enabled(true)
                .focusable(true)
                .descriptionEditInputType(InputType.TYPE_CLASS_NUMBER)
                .build());
        mainActions.add(actionNext = new GuidedAction.Builder(getActivity())
                .id(ID_NEXT)
                .title(R.string.sched_next)
                .description(R.string.sched_next_desc)
                .enabled(false)
                .build());

    }

    @Override
    public GuidedActionsStylist onCreateActionsStylist() {
        return new GuidedActionsStylistExtended();
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        int id = (int) action.getId();
        switch (id) {
            case ID_CHANNEL:
                showChannelSelector();
                break;
            case ID_NEXT:
                FragmentManager fm = getFragmentManager();
                GuidedStepSupportFragment.add(fm,
                        new EditScheduleFragment(mDetailsList, mRecordId, searchType, this, false));
        }
        super.onGuidedActionClicked(action);
    }

    void showChannelSelector() {
        if (names.size() == 0) {
            // Get list of channels
            Context context = getContext();
            VideoDbHelper dbh = VideoDbHelper.getInstance(context);
            SQLiteDatabase db = dbh.getReadableDatabase();
            if (db == null)
                return;
            final String[] columns = {COLUMN_SUBTITLE, COLUMN_CHANID, COLUMN_CALLSIGN};
            Cursor cursor = db.query(
                    VIEW_NAME,   // The table to query
                    columns,             // The array of columns to return (pass null to get all)
                    COLUMN_RECTYPE + " = " + RECTYPE_CHANNEL, // The where clause
                    null,          // The values for the WHERE clause
                    null,                   // don't group the rows
                    null,                   // don't filter by row groups
                    "CAST (" + COLUMN_CHANNUM + " AS REAL), " + COLUMN_CHANNUM  // The sort order
            );
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
                chanids.add(cursor.getInt(1));
                callSigns.add(cursor.getString(2));
            }
            cursor.close();
            VideoDbHelper.releaseDatabase();
        }

        // Theme_AppCompat_Light_Dialog_Alert or Theme_AppCompat_Dialog_Alert
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
                R.style.Theme_AppCompat_Dialog_Alert);
        AlertDialog dlg = builder
                .setTitle(R.string.sched_channel)
                .setItems(names.toArray(new String[0]),
                        (dialog, which) -> {
                            // The 'which' argument contains the index position
                            // of the selected item
                            selection = which;
                            chanName = names.get(which);
                            actionChannel.setDescription(chanName);
                            notifyActionChanged(findActionPositionById(ID_CHANNEL));
                            chanid = chanids.get(which);
                            station = callSigns.get(which);
                            actionNext.setEnabled(true);
                            notifyActionChanged(findActionPositionById(ID_NEXT));
                        })
                .create();
                dlg.show();
        if (selection >= 0)
            dlg.getListView().setSelection(selection);
    }

    @Override
    public long onGuidedActionEditedAndProceed(GuidedAction action) {
        int id = (int) action.getId();
        switch (id) {
            case ID_DURATION:
                validateNumber(action, 5, 360, 60);
                break;
        }
        return GuidedAction.ACTION_ID_CURRENT;
    }

    @Override
    public void onGuidedActionEditCanceled(GuidedAction action) {
        onGuidedActionEditedAndProceed(action);
    }

    public void setManualParms(RecordRule rule) {
        rule.chanId = chanid;
        rule.station = station;
        // 86400000 is number of milliseconds in a day
        rule.startTime = new Date(actionDate.getDate()  / 86400000 * 86400000
                + actionTime.getTime() % 86400000);
        // 60000 is number of milliseconds in a minute
        rule.endTime = new Date(rule.startTime.getTime()
                + Long.parseLong((actionDuration.getDescription().toString())) * 60000);
        rule.title = actionTitle.getDescription().toString();
        if (rule.title.length() == 0) {
            java.text.DateFormat timeFormatter = android.text.format.DateFormat.getTimeFormat(getContext());
            java.text.DateFormat dateFormatter = android.text.format.DateFormat.getLongDateFormat(getContext());
            java.text.DateFormat dayFormatter = new SimpleDateFormat("EEE ");
            StringBuilder title = new StringBuilder(chanName).append(' ')
                .append(dayFormatter.format(rule.startTime))
                .append(dateFormatter.format(rule.startTime)).append(' ')
                .append(timeFormatter.format(rule.startTime));
            rule.title = title.toString();
        }
        rule.subtitle = actionSubtitle.getDescription().toString();
        Calendar cal = Calendar.getInstance();
        cal.setTime(rule.startTime);
        // findday: Saturday = 0 , Sunday = 1, etc
        // java DAY_OF_WEEK Saturday = 7, Sunday = 1
        rule.findDay = cal.get(Calendar.DAY_OF_WEEK) % 7;
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss.SSS");
        rule.findTime = sdfTime.format(rule.startTime);
    }

    private String validateNumber(GuidedAction action, int min, int max, int defValue) {
        String s;
        int i;
        s = action.getDescription().toString();
        try {
            i = Integer.parseInt(s);
        } catch (Exception e) {
            i = defValue;
        }
        if (i < min)
            i = min;
        else if (i > max)
            i = max;
        s = String.valueOf(i);
        action.setDescription(s);
        notifyActionChanged(findActionPositionById(action.getId()));
        return s;
    }
}
