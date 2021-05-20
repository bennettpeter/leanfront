/*
 * Copyright (c) 2016 The Android Open Source Project
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

package org.mythtv.leanfront.model;

import android.content.SharedPreferences;
import android.media.MediaDescription;
import android.os.Parcel;
import android.os.Parcelable;

import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.ui.MainFragment;

/**
 * Video is an object that holds the various metadata associated with a single video.
 */
public final class Video implements Parcelable, ListItem {
    public final long id;
    public final int rectype;
    public final String title;
    public final String subtitle;
    public final String description;
    public final String bgImageUrl;
    public final String cardImageUrl;
    public final String videoUrl;
    public final String channel;
    public final String recordedid;
    public String recGroup;
    // type takes one of the values in MainFragment to indicate
    // a type of UI element
    public int type;
    public final String season;
    public final String episode;
    // Format yyyy-mm-dd
    public final String airdate;
    // Format yyyy-mm-ddThh:mm:ssZ
    public final String starttime;
    public String endtime;
    public final String duration;
    public final String prodyear;
    public final String filename;
    public final String hostname;
    public String progflags;
    // From MythTV libmyth/programtypes.h
    // This flag is also set for videos as needed.
    public static final int FL_WATCHED = 0x00000200;
    public String videoProps;
    public static final int VID_DAMAGED = 0x00000400;
    // Channel values
    public final String chanid;
    public final String channum;
    public final String callsign;
    public final String storageGroup;
    // From status table
    public final long lastUsed;
    public boolean showRecent;

    // Actions used by multiple classes
    public static final int ACTION_PLAY                 =  1;
    public static final int ACTION_PLAY_FROM_BOOKMARK   =  2;
    public static final int ACTION_DELETE               =  3;
    public static final int ACTION_DELETE_AND_RERECORD  =  4;
    public static final int ACTION_UNDELETE             =  5;
    public static final int ACTION_REFRESH              =  6;
    public static final int ACTION_OTHER                =  7;
    public static final int ACTION_SET_WATCHED          =  8;
    public static final int ACTION_SET_UNWATCHED        =  9;
    public static final int ACTION_SET_BOOKMARK         = 10;
    public static final int ACTION_REMOVE_BOOKMARK      = 11;
    public static final int ACTION_ZOOM                 = 13;
    public static final int ACTION_ASPECT               = 14;
    public static final int ACTION_FILELENGTH           = 15;
    public static final int ACTION_SPEEDUP              = 16;
    public static final int ACTION_SLOWDOWN             = 17;
    public static final int ACTION_PIVOT                = 18;
    public static final int ACTION_AUDIOTRACK           = 19;
    public static final int ACTION_LIVETV               = 20;
    public static final int ACTION_QUERY_STOP_RECORDING = 21;
    public static final int ACTION_STOP_RECORDING       = 22;
    public static final int ACTION_REMOVE_RECORD_RULE   = 23;
    public static final int ACTION_CANCEL               = 24;
    public static final int ACTION_BACKEND_INFO         = 25;
    public static final int ACTION_BACKEND_INFO_HTML    = 26;
    public static final int ACTION_VIEW_DESCRIPTION     = 28;
    public static final int ACTION_GUIDE                = 29;
    public static final int ACTION_GETPROGRAMDETAILS    = 30;
    public static final int ACTION_GETRECORDSCHEDULE    = 31;
    public static final int ACTION_GETPLAYGROUPLIST     = 32;
    public static final int ACTION_GETRECGROUPLIST      = 33;
    public static final int ACTION_GETRECSTORAGEGROUPLIST  = 34;
    public static final int ACTION_GETINPUTLIST         = 35;
    public static final int ACTION_GETRECORDSCHEDULELIST = 36;
    public static final int ACTION_GETRECRULEFILTERLIST  = 37;
    public static final int ACTION_ADD_OR_UPDATERECRULE  = 38;
    public static final int ACTION_DELETERECRULE         = 39;
    public static final int ACTION_SEARCHGUIDE           = 40;
    public static final int ACTION_DUMMY                 = 41;
    public static final int ACTION_GETUPCOMINGLIST       = 42;
    public static final int ACTION_PAUSE                 = 43;
    public static final int ACTION_AUDIOSYNC             = 44;
    public static final int ACTION_ALLOW_RERECORD        = 45;
    public static final int ACTION_REMOVE_RECENT         = 46;
    public static final int ACTION_PLAYLIST_PLAY         = 47;


    private Video(
            final long id,
            final int rectype,
            final String title,
            final String subtitle,
            final String desc,
            final String videoUrl,
            final String bgImageUrl,
            final String cardImageUrl,
            final String channel,
            final String recordedid,
            final String recGroup,
            final String season,
            final String episode,
            final String airdate,
            final String starttime,
            final String endtime,
            final String duration,
            final String prodyear,
            final String filename,
            final String hostname,
            final String progflags,
            final String videoProps,
            final String chanid,
            final String channum,
            final String callsign,
            final String storageGroup,
            final long lastUsed,
            final boolean showRecent) {
        this.id = id;
        this.rectype = rectype;
        this.title = title;
        this.subtitle = subtitle;
        this.description = desc;
        this.videoUrl = videoUrl;
        this.bgImageUrl = bgImageUrl;
        this.cardImageUrl = cardImageUrl;
        this.channel = channel;
        this.recordedid = recordedid;
        this.recGroup = recGroup;
        this.season = season;
        this.episode = episode;
        this.airdate = airdate;
        this.starttime = starttime;
        this.endtime = endtime;
        this.duration = duration;
        this.prodyear = prodyear;
        this.filename = filename;
        this.hostname = hostname;
        this.progflags = progflags;
        this.videoProps = videoProps;
        this.chanid = chanid;
        this.channum = channum;
        this.callsign = callsign;
        this.storageGroup = storageGroup;
        this.lastUsed = lastUsed;
        this.showRecent = showRecent;
    }

    protected Video(Parcel in) {
        id = in.readLong();
        rectype = in.readInt();
        title = in.readString();
        subtitle = in.readString();
        description = in.readString();
        bgImageUrl = in.readString();
        cardImageUrl = in.readString();
        videoUrl = in.readString();
        channel = in.readString();
        recordedid = in.readString();
        recGroup = in.readString();
        season = in.readString();
        episode = in.readString();
        airdate = in.readString();
        starttime = in.readString();
        endtime = in.readString();
        duration = in.readString();
        prodyear = in.readString();
        filename = in.readString();
        hostname = in.readString();
        progflags = in.readString();
        videoProps = in.readString();
        chanid = in.readString();
        channum = in.readString();
        callsign = in.readString();
        storageGroup = in.readString();
        lastUsed = in.readLong();
        showRecent = in.readInt() != 0;
    }

    public static final Creator<Video> CREATOR = new Creator<Video>() {
        @Override
        public Video createFromParcel(Parcel in) {
            return new Video(in);
        }

        @Override
        public Video[] newArray(int size) {
            return new Video[size];
        }
    };

    @Override
    public boolean equals(Object m) {
        return m instanceof Video && id == ((Video) m).id;
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeInt(rectype);
        dest.writeString(title);
        dest.writeString(subtitle);
        dest.writeString(description);
        dest.writeString(bgImageUrl);
        dest.writeString(cardImageUrl);
        dest.writeString(videoUrl);
        dest.writeString(channel);
        dest.writeString(recordedid);
        dest.writeString(recGroup);
        dest.writeString(season);
        dest.writeString(episode);
        dest.writeString(airdate);
        dest.writeString(starttime);
        dest.writeString(endtime);
        dest.writeString(duration);
        dest.writeString(prodyear);
        dest.writeString(filename);
        dest.writeString(hostname);
        dest.writeString(progflags);
        dest.writeString(videoProps);
        dest.writeString(chanid);
        dest.writeString(channum);
        dest.writeString(callsign);
        dest.writeString(storageGroup);
        dest.writeLong(lastUsed);
        int showRecentInt = 0;
        if (showRecent)
            showRecentInt = 1;
        dest.writeInt(showRecentInt);
    }

    @Override
    public String toString() {
        String s = "Video{";
        s += "id=" + id;
        s += ", rectype='" + rectype + "'";
        s += ", title='" + title + "'";
        s += ", subtitle='" + subtitle + "'";
        s += ", videoUrl='" + videoUrl + "'";
        s += ", bgImageUrl='" + bgImageUrl + "'";
        s += ", cardImageUrl='" + cardImageUrl + "'";
        s += ", recordedid='" + recordedid + "'";
        s += "}";
        return s;
    }

    @Override
    public int getItemType() {
        if (type != 0)
            return type;
        return recGroup == null || recGroup.length() == 0
                ? MainFragment.TYPE_VIDEO : MainFragment.TYPE_EPISODE;
    }

    @Override
    // This provides a unique identifier for the item, needed by the UI
    public String getName() {
        if (rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
          || rectype == VideoContract.VideoEntry.RECTYPE_VIDEO)
            return videoUrl;
        if (rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL)
            return chanid;
        return null;
    }

    public boolean isRecentViewed() {
        boolean showRecents = "true".equals(Settings.getString("pref_show_recents"));
        boolean showDeleted = "true".equals(Settings.getString("pref_recents_deleted"));
        boolean showWatched = "true".equals(Settings.getString("pref_recents_watched"));
        long recentsDays = Settings.getInt("pref_recents_days");
        long recentsStart = System.currentTimeMillis() - recentsDays * 24*60*60*1000;
        return showRecents
                        && showRecent
                        && lastUsed > recentsStart
                        && (showDeleted || !"Deleted".equals(recGroup))
                        && (showWatched
                        || progflags == null
                        || (Integer.parseInt(progflags) & Video.FL_WATCHED) == 0);
    }

    public boolean isWatched() {
        return progflags != null
                && (Integer.parseInt(progflags) & Video.FL_WATCHED) != 0;
    }

    public boolean isDamaged() {
        return videoProps != null
                && (Integer.parseInt(videoProps) & Video.VID_DAMAGED) != 0;
    }

    public String getSeries() {
        if (rectype == VideoContract.VideoEntry.RECTYPE_RECORDING)
            return title;
        else if (rectype == VideoContract.VideoEntry.RECTYPE_VIDEO
            && filename != null) {
            int lastslashpos = filename.lastIndexOf('/');
            if (lastslashpos == -1)
                return "";
            return filename.substring(0,lastslashpos);
        }
        return null;
    }

    // Builder for Video object.
    public static class VideoBuilder {
        private long id;
        private int rectype;
        private String title;
        private String subtitle;
        private String desc;
        private String bgImageUrl;
        private String cardImageUrl;
        private String videoUrl;
        private String channel;
        private String recordedid;
        private String recGroup;
        private String season;
        private String episode;
        private String airdate;
        private String starttime;
        private String endtime;
        private String duration;
        private String prodyear;
        private String filename;
        private String hostname;
        private String progflags;
        private String videoProps;
        private String chanid;
        private String channum;
        private String callsign;
        private String storageGroup;
        private long lastUsed;
        private boolean showRecent;

        public VideoBuilder id(long id) {
            this.id = id;
            return this;
        }

        public VideoBuilder rectype(int rectype) {
            this.rectype = rectype;
            return this;
        }

        public VideoBuilder title(String title) {
            this.title = title;
            return this;
        }

        public VideoBuilder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public VideoBuilder description(String desc) {
            this.desc = desc;
            return this;
        }

        public VideoBuilder videoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
            return this;
        }

        public VideoBuilder bgImageUrl(String bgImageUrl) {
            this.bgImageUrl = bgImageUrl;
            return this;
        }

        public VideoBuilder cardImageUrl(String cardImageUrl) {
            this.cardImageUrl = cardImageUrl;
            return this;
        }

        public VideoBuilder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public VideoBuilder recordedid(String recordedid) {
            this.recordedid = recordedid;
            return this;
        }

        public VideoBuilder recGroup(String recGroup) {
            this.recGroup = recGroup;
            return this;
        }


        public VideoBuilder season(String season) {
            this.season = season;
            return this;
        }

        public VideoBuilder episode(String episode) {
            this.episode = episode;
            return this;
        }

        public VideoBuilder airdate(String airdate) {
            this.airdate = airdate;
            return this;
        }

        public VideoBuilder starttime(String starttime) {
            this.starttime = starttime;
            return this;
        }

        public VideoBuilder endtime(String endtime) {
            this.starttime = endtime;
            return this;
        }

        public VideoBuilder duration(String duration) {
            this.duration = duration;
            return this;
        }

        public VideoBuilder prodyear(String prodyear) {
            this.prodyear = prodyear;
            return this;
        }

        public VideoBuilder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public VideoBuilder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public VideoBuilder progflags(String progflags) {
            this.progflags = progflags;
            return this;
        }

        public VideoBuilder videoProps(String videoProps) {
            this.videoProps = videoProps;
            return this;
        }

        public VideoBuilder chanid(String chanid) {
            this.chanid = chanid;
            return this;
        }

        public VideoBuilder channum(String channum) {
            this.channum = channum;
            return this;
        }

        public VideoBuilder callsign(String callsign) {
            this.callsign = callsign;
            return this;
        }

        public VideoBuilder storageGroup(String storageGroup) {
            this.storageGroup = storageGroup;
            return this;
        }

        public VideoBuilder lastUsed(long lastUsed) {
            this.lastUsed = lastUsed;
            return this;
        }

        public VideoBuilder showRecent(boolean showRecent) {
            this.showRecent = showRecent;
            return this;
        }

        public Video buildFromMediaDesc(MediaDescription desc) {
            return new Video(
                    Long.parseLong(desc.getMediaId()),
                    VideoContract.VideoEntry.RECTYPE_VIDEO,
                    String.valueOf(desc.getTitle()),
                    "",
                    String.valueOf(desc.getDescription()),
                    "", // Media URI - not provided by MediaDescription.
                    "", // Background Image URI - not provided by MediaDescription.
                    String.valueOf(desc.getIconUri()),
                    String.valueOf(desc.getSubtitle()),
                    "", //recordid not provided
                    "","","","","","","","","","","0","0","","","","", 0, false
            );
        }

        public Video build() {
            return new Video(
                    id,
                    rectype,
                    title,
                    subtitle,
                    desc,
                    videoUrl,
                    bgImageUrl,
                    cardImageUrl,
                    channel,
                    recordedid,
                    recGroup,
                     season,
                     episode,
                     airdate,
                    starttime,
                    endtime,
                     duration,
                     prodyear,
                     filename,
                     hostname,
                     progflags,
                    videoProps,
                    chanid,
                    channum,
                    callsign,
                    storageGroup,
                    lastUsed,
                    showRecent
            );
        }
    }
}
