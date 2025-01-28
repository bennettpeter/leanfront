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

package org.mythtv.leanfront.data;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;

import org.mythtv.leanfront.MyApplication;
import org.mythtv.leanfront.R;

import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The VideoDbBuilder is used to grab a XML file from a server and parse the data
 * to be placed into a local database
 */
@SuppressLint("SimpleDateFormat")
public class VideoDbBuilder {
    public static final String[] XMLTAGS_PROGRAM = {"Programs", "Program"};
    public static final String[] XMLTAGS_ARTINFO = {"Artwork", "ArtworkInfos", "ArtworkInfo"};
    public static final String[] XMLTAGS_CHANNELNAME = {"Channel", "ChannelName"};
    public static final String[] XMLTAGS_CHANID = {"Channel", "ChanId"};
    public static final String[] XMLTAGS_CALLSIGN = {"Channel", "CallSign"};

    public static final String XMLTAG_RECORDING = "Recording";
    public static final String XMLTAG_TITLE = "Title";
    public static final String XMLTAG_DESCRIPTION = "Description";
    public static final String XMLTAG_STORAGEGROUP = "StorageGroup";
    public static final String XMLTAG_RECGROUP = "RecGroup";
    public static final String XMLTAG_PLAYGROUP = "PlayGroup";
    public static final String XMLTAG_RECORDEDID = "RecordedId";
    public static final String XMLTAG_RECORDID = "RecordId";
    public static final String XMLTAG_SEASON = "Season";
    public static final String XMLTAG_EPISODE = "Episode";
    public static final String XMLTAG_FILENAME = "FileName";
    public static final String XMLTAG_FILESIZE = "FileSize";
    public static final String XMLTAG_ARTTYPE = "Type";
    public static final String XMLTAG_ARTURL = "URL";
    public static final String XMLTAG_SUBTITLE = "SubTitle";
    public static final String XMLTAG_STARTTIME = "StartTime";
    public static final String XMLTAG_ENDTIME = "EndTime";
    public static final String XMLTAG_AIRDATE = "Airdate";
    public static final String XMLTAG_STARTTS = "StartTs";
    public static final String XMLTAG_ENDTS = "EndTs";
    public static final String XMLTAG_PROGFLAGS = "ProgramFlags";
    public static final String XMLTAG_VIDEOPROPS = "VideoProps";
    public static final String XMLTAG_VIDEOPROPNAMES = "VideoPropNames";
    public static final String XMLTAG_HOSTNAME = "HostName";

    // Specific to video list
    public static final String[] XMLTAGS_VIDEO = {"VideoMetadataInfos", "VideoMetadataInfo"};
    public static final String XMLTAG_RELEASEDATE = "ReleaseDate";
    public static final String XMLTAG_ID = "Id";
    public static final String XMLTAG_WATCHED = "Watched";
    public static final String VALUE_WATCHED = (Integer.valueOf(Video.FL_WATCHED)).toString();

    // Channels
    private static final String[] XMLTAGS_CHANNEL = {"ChannelInfos", "ChannelInfo"};
    public static final String XMLTAG_CHANID = "ChanId";
    public static final String XMLTAG_CHANNUM = "ChanNum";
    public static final String XMLTAG_CALLSIGN = "CallSign";
    public static final String XMLTAG_CHANNELNAME = "ChannelName";

    private static final String TAG = "lfe";
    private static final String CLASS = "VideoDbBuilder";

    private Context mContext;
    private boolean mBackendOverride;
    private String mMasterServer;


    // 2018-05-23T00:00:00Z
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
    private static final SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public VideoDbBuilder(Context context) {
        if (!XmlNode.isSetupDone())
            return;
        this.mContext = context;
        try {
            String url = XmlNode.mythApiUrl(null, "/Myth/GetSetting?key=MasterBackendOverride&Default=0&HostName=_GLOBAL_");
            XmlNode result = XmlNode.fetch(url, null);
            String resultValue = result.getString();
            mBackendOverride = ("1".equals(resultValue));
            mMasterServer = null;
            if (mBackendOverride) {
                url = XmlNode.mythApiUrl(null, "/Myth/GetSetting?key=MasterServerName&Default=0&HostName=_GLOBAL_");
                result = XmlNode.fetch(url, null);
                resultValue = result.getString();
                // cater for old version where MasterServerName is not valued
                if ("0".equals(resultValue))
                    mBackendOverride = false;
                else
                    mMasterServer = resultValue;
            }
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
            mBackendOverride = false;
        }
    }

    /**
     * Fetches data representing videos from a server and populates that in a database
     *
     * @param url The location of the video list
     */
    public int fetch(String url, int phase, List<ContentValues> videosToInsert)
            throws IOException, XmlPullParserException {
        if (!XmlNode.isSetupDone())
            return 0;
        XmlNode videoData = XmlNode.fetch(url, null);
        buildMedia(videoData, phase, -1, videosToInsert);
        return videoData.getInt("TotalAvailable", 0);
    }

    static final String[] articles = MyApplication.getAppContext().getResources().getStringArray(R.array.title_sort_articles);
    /**
     * Takes the contents of an XML object and populates the database
     *
     * @param xmlFull The XML object of videos
     * @param phase   0 for recordings, 1 for videos, 2 for channels
     * @param ixSingle if this is -1 process all records, otherwise process the specified single record
     */
    public void buildMedia(XmlNode xmlFull, int phase, int ixSingle, List<ContentValues> videosToInsert)
            throws IOException, XmlPullParserException {
        String[] tagsProgram = null;
        String tagRecordedId = null;
        if (phase == 0) {  //Recordings
            tagsProgram = XMLTAGS_PROGRAM;
            tagRecordedId = XMLTAG_RECORDEDID;
        }
        if (phase == 1) {  //Videos
            tagsProgram = XMLTAGS_VIDEO;
            tagRecordedId = XMLTAG_ID;
        }
        if (phase == 2) { // Channels
            loadChannels(xmlFull, videosToInsert);
            return;
        }
        int maxparental= Settings.getInt("pref_video_parental");
        // Art urls have to be off main backend
        String baseMasterUrl = XmlNode.mythApiUrl(null, null);
        XmlNode programNode = null;
        for (; ; ) {
            if (programNode == null) {
                // Here we allow for the xml to cintains just one program or video.
                if (tagsProgram[tagsProgram.length-1].equals(xmlFull.getName()))
                    programNode = xmlFull;
                else {
                    int ixWant = 0;
                    if (ixSingle >= 0)
                        ixWant = ixSingle;
                    programNode = xmlFull.getNode(tagsProgram, ixWant);
                }
            }
            else
                programNode = programNode.getNextSibling();
            if (programNode == null)
                break;
            XmlNode recordingNode = null;
            int rectype = -1;
            String recGroup = null;
            String playGroup = null;
            String storageGroup = null;
            String channel = null;
            String chanid = null;
            String callsign = null;
            String airdate = null;
            String starttime = null;
            String endtime = null;
            String baseUrl = null;
            long duration = 0;
            String progflags = "0";
            String videoProps = "0";
            String videoPropNames = null;
            long fileSize = 0;
            if (phase == 0) { // Recordings
                rectype = VideoContract.VideoEntry.RECTYPE_RECORDING;
                 fileSize = programNode.getLong(XMLTAG_FILESIZE,0);
                recordingNode = programNode.getNode(XMLTAG_RECORDING);
                String recordId = recordingNode.getString(XMLTAG_RECORDID);
                // Skip dummy LiveTV entry
                if (fileSize == 0 && "0".equals(recordId))
                    continue;
                recGroup = recordingNode.getString(XMLTAG_RECGROUP);
                if (recGroup == null || recGroup.length() == 0)
                    recGroup = "Default";
                playGroup = recordingNode.getString(XMLTAG_PLAYGROUP);
                storageGroup = recordingNode.getString(XMLTAG_STORAGEGROUP);
                channel = programNode.getString(XMLTAGS_CHANNELNAME);
                chanid = programNode.getString(XMLTAGS_CHANID);
                callsign = programNode.getString(XMLTAGS_CALLSIGN);
                airdate = programNode.getString(XMLTAG_AIRDATE);
                starttime = programNode.getString(XMLTAG_STARTTIME);

                String startTS = recordingNode.getString(XMLTAG_STARTTS);
                endtime = recordingNode.getString(XMLTAG_ENDTS);
                long startTimeSecs = 0;
                try {
                    Date dateStart = dateFormat.parse(startTS + "+0000");
                    Date dateEnd = dateFormat.parse(endtime + "+0000");
                    startTimeSecs = dateStart.getTime();
                    duration = (dateEnd.getTime() - startTimeSecs);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                // if airdate missing default it to starttime.
                if (starttime != null && airdate == null
                        && startTimeSecs != 0) {
                    TimeZone tz = TimeZone.getDefault();
                    startTimeSecs += tz.getOffset(startTimeSecs);
                    airdate = dbDateFormat.format(new Date(startTimeSecs));
                }
                progflags = programNode.getString(XMLTAG_PROGFLAGS);
                videoProps = programNode.getString(XMLTAG_VIDEOPROPS);
                videoPropNames = programNode.getString(XMLTAG_VIDEOPROPNAMES);
            }
            if (phase == 1) { // Videos
                if (ixSingle < 0) {
                    int parental = programNode.getInt("ParentalLevel", 1);
                    if (parental > maxparental)
                        continue;
                }
                rectype = VideoContract.VideoEntry.RECTYPE_VIDEO;
                recordingNode = programNode;
                recGroup = null;
                playGroup = null;
                storageGroup = "Videos";
                channel = null;
                airdate = programNode.getString(XMLTAG_RELEASEDATE);
                if (airdate != null && airdate.length() > 10)
                    airdate = airdate.substring(0, 10);
                if (airdate != null)
                    // Default starttime for videos to the airdate 12noon UCT
                    starttime = airdate + "T12:00:00Z";
                String watched = programNode.getString(XMLTAG_WATCHED);
                if ("true".equals(watched))
                    progflags = VALUE_WATCHED;
                else
                    progflags = "0";
            }
            String recordedid = null;
            String videoFileName = null;
            String coverArtUrl = null;
            String title = null;
            String subtitle = null;
            String description = null;
            String videoUrl = null;
            String videoUrlPath = null;
            String hostName = null;
            String fanArtUrl = null;
            String screenShotUrl = null;
            String prodYear = null;
            String baseHostUrl = null;
            if (phase == 0 || phase == 1) {
                recordedid = recordingNode.getString(tagRecordedId);
                title = programNode.getString(XMLTAG_TITLE);
                // These next three lines cause chaos.!!!
                if (phase == 0 && mBackendOverride)  // Recordings
                    hostName = mMasterServer;
                else
                    hostName = recordingNode.getString(XMLTAG_HOSTNAME);
                subtitle = programNode.getString(XMLTAG_SUBTITLE);
                description = programNode.getString(XMLTAG_DESCRIPTION);
                videoFileName = recordingNode.getString(XMLTAG_FILENAME);
                if (videoFileName == null)
                    continue;
                baseUrl = XmlNode.mythApiUrl(hostName, null);
                baseHostUrl = XmlNode.mythApiUrl(recordingNode.getString(XMLTAG_HOSTNAME), null);
                videoUrlPath = "/Content/GetFile?StorageGroup="
                        + storageGroup + "&FileName=/" + URLEncoder.encode(videoFileName, "UTF-8");
                videoUrl = baseUrl + videoUrlPath;
                XmlNode artInfoNode = null;
                for (; ; ) {
                    if (artInfoNode == null)
                        artInfoNode = programNode.getNode(XMLTAGS_ARTINFO, 0);
                    else
                        artInfoNode = artInfoNode.getNextSibling();
                    if (artInfoNode == null)
                        break;
                    String artType = artInfoNode.getString(XMLTAG_ARTTYPE);
                    String artUrl = baseMasterUrl + artInfoNode.getString(XMLTAG_ARTURL);
                    int equ = artUrl.lastIndexOf('=');
                    if (equ > 0) {
                        String fileName = artUrl.substring(equ + 1);
                        if (fileName.length() > 0) {
                            // decode and encode it to ensure it is encoded
                            fileName = URLDecoder.decode(fileName, "UTF-8");
                            fileName = URLEncoder.encode(fileName, "UTF-8");
                            artUrl = artUrl.substring(0, equ + 1) + fileName;
                        }
                    }
                    if ("coverart".equals(artType))
                        coverArtUrl = artUrl;
                    else if ("fanart".equals(artType))
                        fanArtUrl = artUrl;
                    else if ("screenshot".equals(artType))
                        screenShotUrl = artUrl;
                }

                if (airdate != null)
                    prodYear = airdate.substring(0, 4);
                else if (starttime != null)
                    prodYear = starttime.substring(0, 4);
            }
            String cardImageURL = null;
            String dbFileName = null;
            dbFileName = videoFileName;
            if (phase == 0 && baseHostUrl.length() > 0) { // Recordings
                cardImageURL = baseHostUrl + "/Content/GetPreviewImage?Format=png&RecordedId=" + recordedid;
            }
            if (phase == 1) { // Videos
                if (screenShotUrl != null)
                    cardImageURL = screenShotUrl;
                else
                    cardImageURL = coverArtUrl;
            }
            String season = programNode.getString(XMLTAG_SEASON);
            String episode = programNode.getString(XMLTAG_EPISODE);

            if (title == null || title.length() == 0)
                title = " ";
            if (subtitle == null || subtitle.length() == 0)
                subtitle = " ";
            if (description == null || description.length() == 0)
                description = " ";

            String titlematch = title.toUpperCase(Locale.ROOT);

            // Videos without subtitle - use directory name as the title
            // for matching and grouping.
            if (rectype == VideoContract.VideoEntry.RECTYPE_VIDEO && (subtitle.equals(" "))) {
                int pos = dbFileName.lastIndexOf('/');
                if (pos >= 0)
                    titlematch = dbFileName.substring(0, pos + 1).toUpperCase();
            }

            for (String article : articles) {
                if (article != null && article.length() > 0) {
                    titlematch = titlematch.replaceFirst("^" + article + " ", "");
                }
            }
            // Replace text in parens at end of title as long as there are no spaces
            // in the text, for example Ghosts (2019) or Ghosts (US) become Ghosts
            titlematch = titlematch.replaceFirst("\\([^ ]*\\)$", "");
            titlematch = titlematch.trim();

            ContentValues videoValues = new ContentValues();
            videoValues.put(VideoContract.VideoEntry.COLUMN_RECTYPE, rectype);
            videoValues.put(VideoContract.VideoEntry.COLUMN_TITLE, title);
            videoValues.put(VideoContract.VideoEntry.COLUMN_TITLEMATCH, titlematch);
            videoValues.put(VideoContract.VideoEntry.COLUMN_SUBTITLE, subtitle);
            videoValues.put(VideoContract.VideoEntry.COLUMN_DESC, description);
            videoValues.put(VideoContract.VideoEntry.COLUMN_VIDEO_URL, videoUrl);
            videoValues.put(VideoContract.VideoEntry.COLUMN_VIDEO_URL_PATH, videoUrlPath);
            videoValues.put(VideoContract.VideoEntry.COLUMN_FILENAME, dbFileName);
            videoValues.put(VideoContract.VideoEntry.COLUMN_FILESIZE, fileSize);
            videoValues.put(VideoContract.VideoEntry.COLUMN_HOSTNAME, hostName);
            videoValues.put(VideoContract.VideoEntry.COLUMN_CARD_IMG, cardImageURL);
            videoValues.put(VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL, fanArtUrl);
            videoValues.put(VideoContract.VideoEntry.COLUMN_CHANNEL, channel);
            videoValues.put(VideoContract.VideoEntry.COLUMN_CHANID, chanid);
            videoValues.put(VideoContract.VideoEntry.COLUMN_CALLSIGN, callsign);
            videoValues.put(VideoContract.VideoEntry.COLUMN_AIRDATE, airdate);

            videoValues.put(VideoContract.VideoEntry.COLUMN_STARTTIME, starttime);
            videoValues.put(VideoContract.VideoEntry.COLUMN_ENDTIME, endtime);
            videoValues.put(VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR, prodYear);
            videoValues.put(VideoContract.VideoEntry.COLUMN_RECORDEDID, recordedid);
            videoValues.put(VideoContract.VideoEntry.COLUMN_STORAGEGROUP, storageGroup);
            videoValues.put(VideoContract.VideoEntry.COLUMN_RECGROUP, recGroup);
            videoValues.put(VideoContract.VideoEntry.COLUMN_PLAYGROUP, playGroup);
            videoValues.put(VideoContract.VideoEntry.COLUMN_SEASON, season);
            videoValues.put(VideoContract.VideoEntry.COLUMN_EPISODE, episode);

            videoValues.put(VideoContract.VideoEntry.COLUMN_CONTENT_TYPE, "video/mp4");
            videoValues.put(VideoContract.VideoEntry.COLUMN_DURATION, duration);
            if (mContext != null) {
                videoValues.put(VideoContract.VideoEntry.COLUMN_ACTION,
                        mContext.getResources().getString(R.string.global_search));
            }
            videoValues.put(VideoContract.VideoEntry.COLUMN_PROGFLAGS, progflags);
            videoValues.put(VideoContract.VideoEntry.COLUMN_VIDEOPROPS, videoProps);
            videoValues.put(VideoContract.VideoEntry.COLUMN_VIDEOPROPNAMES, videoPropNames);

            videosToInsert.add(videoValues);
            if (ixSingle >= 0)
                break;
        }
    }

    private void loadChannels(XmlNode xmlFull, List<ContentValues> channelsToInsert) {
        XmlNode channelNode = null;
        int rowsize= Settings.getInt("pref_livetv_rowsize");
        for (; ; ) {
            if (channelNode == null)
                channelNode = xmlFull.getNode(XMLTAGS_CHANNEL, 0);
            else
                channelNode = channelNode.getNextSibling();
            if (channelNode == null)
                break;
            int rectype = VideoContract.VideoEntry.RECTYPE_CHANNEL;
            String chanid = channelNode.getString(XMLTAG_CHANID);
            String channum = channelNode.getString(XMLTAG_CHANNUM);
            String callsign = channelNode.getString(XMLTAG_CALLSIGN);
            String channelname = channelNode.getString(XMLTAG_CHANNELNAME);
            String videoUrlPath = "/Channel/GetChannelInfo?Chanid=" + chanid;

            if (channum == null || channum.length() == 0) {
                channum = " ";
            }
            String title;
            float fChannum = -1.0f;
            try {
                fChannum = Float.parseFloat(channum.replace('-', '.'));
            } catch (NumberFormatException e) {

                fChannum = -1.0f;
            }
            if (fChannum < 0.0f) {
                // Non numeric channel number
                title = mContext.getString(R.string.row_header_channels) + " " + channum.toUpperCase().charAt(0);
            }
            else {
                int start = (((int) fChannum) /rowsize) * rowsize;
                int end = start + rowsize-1;
                String spacer;
                if (fChannum < 1.0f)
                    spacer = "    ";
                else if (fChannum < 100.0f)
                    spacer = "   ";
                else if (fChannum < 1000.0f)
                    spacer = "  ";
                else
                    spacer = " ";
                title = mContext.getString(R.string.row_header_channels) + spacer + start + " - " + end;
            }
            ContentValues channelValues = new ContentValues();
            channelValues.put(VideoContract.VideoEntry.COLUMN_RECTYPE, rectype);
            channelValues.put(VideoContract.VideoEntry.COLUMN_TITLE, title);
            channelValues.put(VideoContract.VideoEntry.COLUMN_TITLEMATCH, title);
            channelValues.put(VideoContract.VideoEntry.COLUMN_SUBTITLE, channum + " " + channelname + " " + callsign);
            channelValues.put(VideoContract.VideoEntry.COLUMN_VIDEO_URL_PATH, videoUrlPath);
            channelValues.put(VideoContract.VideoEntry.COLUMN_CHANID, chanid);
            channelValues.put(VideoContract.VideoEntry.COLUMN_CHANNUM, channum);
            channelValues.put(VideoContract.VideoEntry.COLUMN_RECORDEDID, chanid);
            channelValues.put(VideoContract.VideoEntry.COLUMN_CALLSIGN, callsign);
            channelValues.put(VideoContract.VideoEntry.COLUMN_CHANNEL, channelname);
            channelValues.put(VideoContract.VideoEntry.COLUMN_PROGFLAGS, "0");
            channelValues.put(VideoContract.VideoEntry.COLUMN_VIDEOPROPS, "0");
            channelValues.put(VideoContract.VideoEntry.COLUMN_RECGROUP, "LiveTV");
            channelsToInsert.add(channelValues);
        }
    }
}
