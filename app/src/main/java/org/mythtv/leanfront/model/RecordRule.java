package org.mythtv.leanfront.model;

import android.annotation.SuppressLint;
import android.content.Context;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.XmlNode;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

@SuppressLint("SimpleDateFormat")
public class RecordRule {

    public int     recordId;
    public int     parentId;
    public String  title;
    public String  subtitle;
    public String  description;
    public String  category;
    public Date    startTime;
    public Date    endTime;
    public Date    airDate;
    public boolean repeat;
    public String  seriesId;
    public String  programId;
    public int     chanId;
    public String  chanNum;
    public String  channelName;
    public String  station;
    public int     findDay = -1;
    public String  findTime;
    public boolean inactive;
    public int     season;
    public int     episode;
    public String  inetref = "";
    public String  type;
    public String  searchType;
    public int     recPriority;
    public int     preferredInput;
    public int     startOffset;
    public int     endOffset;
    public String  dupMethod;
    public String  dupIn;
    public String autoExtend;
    public boolean newEpisOnly;
    public int     filter;
    public String  recProfile;
    public String  recGroup;
    public String  storageGroup;
    public String  playGroup;
    public boolean autoExpire;
    public int     maxEpisodes;
    public boolean maxNewest;
    public boolean autoCommflag;
    public boolean autoTranscode;
    public boolean autoMetaLookup;
    public boolean autoUserJob1;
    public boolean autoUserJob2;
    public boolean autoUserJob3;
    public boolean autoUserJob4;
    public int     transcoder;
    public Date    lastRecorded;
    public String  recordingStatus;
    public String  encoderName;

    public boolean isFromProgram;
    public boolean isFromSchedule;

    private static final String TAG = "lfe";
    private static final String CLASS = "RecordSchedule";
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    private static DateFormat timeFormatter;
    private static DateFormat dateFormatter;
    private static DateFormat shortDateFormatter;
    private static DateFormat dayFormatter;

    private static final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd");

    public RecordRule fromProgram(XmlNode programNode) {
        isFromProgram = true;
        title = programNode.getString("Title");
        subtitle = programNode.getString("SubTitle");
        description = programNode.getString("Description");
        category = programNode.getString("Category");
        startTime = programNode.getNode("StartTime").getDate();
        endTime = programNode.getNode("EndTime").getDate();
        String t = programNode.getString("Airdate");
        if (t == null)
            airDate = null;
        else {
            try {
                airDate = dateOnlyFormat.parse(t);
            } catch (ParseException e) {
                airDate = null;
            }
        }
        repeat = programNode.getNode("Repeat").getBoolean();
        seriesId = programNode.getString("SeriesId");
        programId = programNode.getString("ProgramId");
        chanId = programNode.getNode("Channel").getInt("ChanId",0);
        station = programNode.getNode("Channel").getString("CallSign");
        chanNum = programNode.getNode("Channel").getString("ChanNum");
        channelName = programNode.getNode("Channel").getString("ChannelName");
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(startTime);
        findDay = cal.get(GregorianCalendar.DAY_OF_WEEK);
        if (findDay == 7)
            findDay = 0;
        findTime = timeFormat.format(startTime);
        // inactive
        season = programNode.getInt("Season",0);
        episode = programNode.getInt("Episode",0);
        inetref = programNode.getString("Inetref");
        XmlNode recording = programNode.getNode("Recording");
        if (recording != null) {
            recordId = recording.getInt("RecordId", 0);
            recordingStatus = recording.getString("StatusName");
            if (recordingStatus == null)
                recordingStatus = recording.getString("Status");
            if ("Unknown".equals(recordingStatus))
                recordingStatus = null; // save storage
            encoderName = recording.getString("EncoderName");
            recGroup = recording.getString("RecGroup");
            storageGroup = recording.getString("StorageGroup");
            playGroup = recording.getString("PlayGroup");
            recPriority = recording.getInt("Priority",0);
            recProfile = recording.getString("Profile");
        }
        return this;
    }

    public RecordRule fromSchedule(XmlNode scheduleNode) {
        isFromSchedule = true;
        recordId = scheduleNode.getInt("Id",0);
        parentId = scheduleNode.getInt("ParentId",0);
        title = scheduleNode.getString("Title");
        subtitle = scheduleNode.getString("SubTitle");
        description = scheduleNode.getString("Description");
        category = scheduleNode.getString("Category");
        startTime = scheduleNode.getNode("StartTime").getDate();
        endTime = scheduleNode.getNode("EndTime").getDate();
        seriesId = scheduleNode.getString("SeriesId");
        programId = scheduleNode.getString("ProgramId");
        chanId = scheduleNode.getInt("ChanId",0);
        station = scheduleNode.getString("CallSign");
        findDay = scheduleNode.getInt("FindDay",0);
        findTime = scheduleNode.getString("FindTime");
        inactive = scheduleNode.getNode("Inactive").getBoolean();
        season = scheduleNode.getInt("Season",0);
        episode = scheduleNode.getInt("Episode",0);
        inetref = scheduleNode.getString("Inetref");
        type = scheduleNode.getString("Type");
        searchType = scheduleNode.getString("SearchType");
        recPriority = scheduleNode.getInt("RecPriority",0);
        preferredInput = scheduleNode.getInt("PreferredInput",0);
        startOffset = scheduleNode.getInt("StartOffset",0);
        endOffset = scheduleNode.getInt("EndOffset",0);
        dupMethod = scheduleNode.getString("DupMethod");
        dupIn = scheduleNode.getString("DupIn");
        // cater for bug in services.
        if ("Unknown".equals(dupIn)) {
            dupIn = "All Recordings";
            // do not set this because we cannot update it with bug.
//            newEpisOnly = true;
        }
        autoExtend = scheduleNode.getString("AutoExtend");
        XmlNode node = scheduleNode.getNode("NewEpisOnly");
        if (node != null)
            newEpisOnly = node.getBoolean();
        filter = scheduleNode.getInt("Filter",0);
        recProfile = scheduleNode.getString("RecProfile");
        recGroup = scheduleNode.getString("RecGroup");
        storageGroup = scheduleNode.getString("StorageGroup");
        playGroup = scheduleNode.getString("PlayGroup");
        autoExpire = scheduleNode.getNode("AutoExpire").getBoolean();
        maxEpisodes = scheduleNode.getInt("MaxEpisodes",0);
        maxNewest = scheduleNode.getNode("MaxNewest").getBoolean();
        autoCommflag = scheduleNode.getNode("AutoCommflag").getBoolean();
        autoTranscode = scheduleNode.getNode("AutoTranscode").getBoolean();
        autoMetaLookup = scheduleNode.getNode("AutoMetaLookup").getBoolean();
        autoUserJob1 = scheduleNode.getNode("AutoUserJob1").getBoolean();
        autoUserJob2 = scheduleNode.getNode("AutoUserJob2").getBoolean();
        autoUserJob3 = scheduleNode.getNode("AutoUserJob3").getBoolean();
        autoUserJob4 = scheduleNode.getNode("AutoUserJob4").getBoolean();
        transcoder = scheduleNode.getInt("Transcoder",0);
        lastRecorded = scheduleNode.getNode("LastRecorded").getDate();
        return this;
    }

    public RecordRule mergeProgram(RecordRule program) {
        if (program != null) {
            title = program.title;
            subtitle = program.subtitle;
            description = program.description;
            category = program.category;
            startTime = program.startTime;
            endTime = program.endTime;
            seriesId = program.seriesId;
            programId = program.programId;
            chanId = program.chanId;
            station = program.station;
            chanNum = program.chanNum;
            channelName = program.channelName;
            findDay = program.findDay;
            findTime = program.findTime;
            season = program.season;
            episode = program.episode;
        }
        return this;
    }

    public RecordRule mergeTemplate(RecordRule template) {
        if (template != null) {
            inactive = template.inactive;
            recPriority = template.recPriority;
            preferredInput = template.preferredInput;
            startOffset = template.startOffset;
            endOffset = template.endOffset;
            dupMethod = template.dupMethod;
            dupIn = template.dupIn;
            autoExtend = template.autoExtend;
            newEpisOnly = template.newEpisOnly;
            filter = template.filter;
            recProfile = template.recProfile;
            recGroup = template.recGroup;
            storageGroup = template.storageGroup;
            playGroup = template.playGroup;
            autoExpire = template.autoExpire;
            maxEpisodes = template.maxEpisodes;
            maxNewest = template.maxNewest;
            autoCommflag = template.autoCommflag;
            autoTranscode = template.autoTranscode;
            autoMetaLookup = template.autoMetaLookup;
            autoUserJob1 = template.autoUserJob1;
            autoUserJob2 = template.autoUserJob2;
            autoUserJob3 = template.autoUserJob3;
            autoUserJob4 = template.autoUserJob4;
            transcoder = template.transcoder;
        }
        return this;
    }


    public String getCardText(Context context) {
        StringBuilder build = new StringBuilder();
        if (isFromSchedule) {
            int statusRes;
            if (inactive)
                statusRes = R.string.sched_inactive;
            else
                statusRes = R.string.sched_active;
            build.append(title).append("\n")
                    .append(type).append(" - ")
                    .append(context.getString(statusRes));
        }
        if (isFromProgram) {
            if (timeFormatter == null) {
                timeFormatter = android.text.format.DateFormat.getTimeFormat(context);
                dateFormatter = android.text.format.DateFormat.getLongDateFormat(context);
                shortDateFormatter = android.text.format.DateFormat.getDateFormat(context);
                dayFormatter = new SimpleDateFormat("EEE ");
            }
            build.append(dayFormatter.format(startTime))
                    .append(dateFormatter.format(startTime)).append(' ')
                    .append(timeFormatter.format(startTime)).append(" - ")
                    .append(timeFormatter.format(endTime)).append(" : ");
            if (encoderName != null)


                build.append(encoderName).append(" : ");

            String chanDetails = chanNum + " " + channelName + " " + station;
            build.append(chanDetails).append("\n");
            build.append(title).append("  ");
            if (season > 0 && episode > 0)
                build.append("S").append(season).append("E").append(episode).append(" ");
            if (subtitle != null)
                build.append(subtitle);
            if (repeat) {
                if (airDate != null)
                    build.append(" [").append(shortDateFormatter.format(airDate)).append("]");
            }
            else
                build.append(" [new]");
        }
        return build.toString();
    }

}
