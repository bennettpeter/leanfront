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

import android.database.Cursor;
import androidx.leanback.database.CursorMapper;

import org.mythtv.leanfront.data.VideoContract;

/**
 * VideoCursorMapper maps a database Cursor to a Video object.
 * This can be used with video or videoview. With video,
 * lastUsed will be set to 0.
 */
public final class VideoCursorMapper extends CursorMapper {

//    private static int idIndex;
    private static int rectypeIndex;
    private static int titleIndex;
    private static int titlematchIndex;
    private static int nameIndex;
    private static int descIndex;
    private static int videoUrlIndex;
    private static int videoUrlPathIndex;
    private static int bgImageUrlIndex;
    private static int cardImageUrlIndex;
    private static int channelIndex;
    private static int recordedidIndex;
    private static int recGroupIndex;
    private static int playGroupIndex;
    private static int seasonIndex;
    private static int episodeIndex;
    private static int airdateIndex;
    private static int starttimeIndex;
    private static int endtimeIndex;
    private static int durationIndex;
    private static int prodyearIndex;
    private static int filenameIndex;
    private static int filesizeIndex;
    private static int hostnameIndex;
    private static int progflagsIndex;
    private static int videoPropsIndex;
    private static int videoPropNamesIndex;
    private static int chanidIndex;
    private static int channumIndex;
    private static int callsignIndex;
    private static int categoryIndex;
    private static int storageGroupIndex;
    private static int lastUsedIndex;
    private static int showRecentIndex;

    @Override
    protected void bindColumns(Cursor cursor) {
//        idIndex = cursor.getColumnIndex(VideoContract.VideoEntry._ID);
        rectypeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_RECTYPE);
        titleIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_TITLE);
        titlematchIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_TITLEMATCH);
        nameIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_SUBTITLE);
        descIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_DESC);
        videoUrlIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_VIDEO_URL);
        videoUrlPathIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_VIDEO_URL_PATH);
        bgImageUrlIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL);
        cardImageUrlIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CARD_IMG);
        channelIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CHANNEL);
        recordedidIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_RECORDEDID);
        recGroupIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_RECGROUP);
        playGroupIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_PLAYGROUP);
        seasonIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_SEASON);
        episodeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_EPISODE);
        airdateIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_AIRDATE);
        starttimeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_STARTTIME);
        endtimeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_ENDTIME);
        durationIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_DURATION);
        prodyearIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR);
        filenameIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_FILENAME);
        filesizeIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_FILESIZE);
        hostnameIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_HOSTNAME);
        progflagsIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_PROGFLAGS);
        videoPropsIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_VIDEOPROPS);
        videoPropNamesIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_VIDEOPROPNAMES);
        chanidIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CHANID);
        channumIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CHANNUM);
        callsignIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CALLSIGN);
        categoryIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_CATEGORY);
        storageGroupIndex = cursor.getColumnIndex(VideoContract.VideoEntry.COLUMN_STORAGEGROUP);
        lastUsedIndex = cursor.getColumnIndex(VideoContract.StatusEntry.COLUMN_LAST_USED);
        showRecentIndex = cursor.getColumnIndex(VideoContract.StatusEntry.COLUMN_SHOW_RECENT);
    }

    @Override
    protected Object bind(Cursor cursor) {

        // One time it failed with cursor closed. I don't know why
        // but maybe this will catch it.
        if (cursor.isClosed())
            return new Video.VideoBuilder()
                    .title("ERROR - CURSOR CLOSED")
                    .build();
        // Get the values of the video.
        int rectype = cursor.getInt(rectypeIndex);
        String title = cursor.getString(titleIndex);
        String titlematch = cursor.getString(titlematchIndex);
        String subtitle = cursor.getString(nameIndex);
        String desc = cursor.getString(descIndex);
        String videoUrl = cursor.getString(videoUrlIndex);
        String videoUrlPath = cursor.getString(videoUrlPathIndex);
        String bgImageUrl = cursor.getString(bgImageUrlIndex);
        String cardImageUrl = cursor.getString(cardImageUrlIndex);
        String channel = cursor.getString(channelIndex);
        String recordedid = cursor.getString(recordedidIndex);
        String recGroup = cursor.getString(recGroupIndex);
        String playGroup = cursor.getString(playGroupIndex);
        String season = cursor.getString(seasonIndex);
        String episode = cursor.getString(episodeIndex);
        String airdate = cursor.getString(airdateIndex);
        String starttime = cursor.getString(starttimeIndex);
        String endtime = cursor.getString(endtimeIndex);
        String duration = cursor.getString(durationIndex);
        String prodyear = cursor.getString(prodyearIndex);
        String filename = cursor.getString(filenameIndex);
        long filesize = cursor.getLong(filesizeIndex);
        String hostname = cursor.getString(hostnameIndex);
        String progflags = cursor.getString(progflagsIndex);
        String videoProps = cursor.getString(videoPropsIndex);
        String videoPropNames = cursor.getString(videoPropNamesIndex);
        String chanid = cursor.getString(chanidIndex);
        String channum = cursor.getString(channumIndex);
        String callsign = cursor.getString(callsignIndex);
        String category = cursor.getString(categoryIndex);
        String storageGroup = cursor.getString(storageGroupIndex);
        long lastUsed = 0;
        if (lastUsedIndex >= 0 && !cursor.isNull(lastUsedIndex))
            lastUsed = cursor.getLong(lastUsedIndex);
        boolean showRecent = true;
        if (showRecentIndex >= 0 && !cursor.isNull(showRecentIndex))
            showRecent = (cursor.getInt(showRecentIndex) != 0);

        // Build a Video object to be processed.
        return new Video.VideoBuilder()
                .rectype(rectype)
                .title(title)
                .titlematch(titlematch)
                .subtitle(subtitle)
                .description(desc)
                .videoUrl(videoUrl)
                .videoUrlPath(videoUrlPath)
                .bgImageUrl(bgImageUrl)
                .cardImageUrl(cardImageUrl)
                .channel(channel)
                .recordedid(recordedid)
                .recGroup(recGroup)
                .playGroup(playGroup)
                .season(season)
                .episode(episode)
                .airdate(airdate)
                .starttime(starttime)
                .endtime(endtime)
                .duration(duration)
                .prodyear(prodyear)
                .filename(filename)
                .filesize(filesize)
                .hostname(hostname)
                .progflags(progflags)
                .videoProps(videoProps)
                .videoPropNames(videoPropNames)
                .chanid(chanid)
                .channum(channum)
                .callsign(callsign)
                .category(category)
                .storageGroup(storageGroup)
                .lastUsed(lastUsed)
                .showRecent(showRecent)
                .build();
    }

    private Cursor myCursor;
    public void changeCursor(Cursor cursor) {
        myCursor = cursor;
    }

    public Object get(int index) {
        if (index >= 0)
            myCursor.moveToPosition(index);
        return convert(myCursor);
    }
}
