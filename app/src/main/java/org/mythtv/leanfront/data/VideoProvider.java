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

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import androidx.annotation.NonNull;

import java.util.HashMap;

/**
 * VideoProvider is a ContentProvider that provides videos for the rest of applications.
 */
public class VideoProvider extends ContentProvider {
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private VideoDbHelper mOpenHelper;

    // These codes are returned from sUriMatcher#match when the respective Uri matches.
    private static final int VIDEO = 1;
    private static final int VIDEO_WITH_CATEGORY = 2;
    private static final int SEARCH_SUGGEST = 3;
    private static final int REFRESH_SHORTCUT = 4;

    private static final SQLiteQueryBuilder sVideosContainingQueryBuilder;
    private static final String[] sVideosContainingQueryColumns;
    private static final HashMap<String, String> sColumnMap = buildColumnMap();
    private ContentResolver mContentResolver;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null)
            return false;
        mContentResolver = context.getContentResolver();
        mOpenHelper = VideoDbHelper.getInstance(context);
        return true;
    }

    static {
        sVideosContainingQueryBuilder = new SQLiteQueryBuilder();
        sVideosContainingQueryBuilder.setTables(VideoContract.VideoEntry.VIEW_NAME);
        sVideosContainingQueryBuilder.setProjectionMap(sColumnMap);
        sVideosContainingQueryColumns = new String[]{
                VideoContract.VideoEntry.COLUMN_TITLE,
                VideoContract.VideoEntry.COLUMN_SUBTITLE,
                VideoContract.VideoEntry.COLUMN_DESC,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL_PATH,
                VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL,
                VideoContract.VideoEntry.COLUMN_CHANNEL,
                VideoContract.VideoEntry.COLUMN_CARD_IMG,
                VideoContract.VideoEntry.COLUMN_CONTENT_TYPE,
                VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR,
                VideoContract.VideoEntry.COLUMN_DURATION,
                VideoContract.VideoEntry.COLUMN_AIRDATE,
                VideoContract.VideoEntry.COLUMN_STARTTIME,
                VideoContract.VideoEntry.COLUMN_ENDTIME,
                VideoContract.VideoEntry.COLUMN_ACTION,
                VideoContract.VideoEntry.COLUMN_RECORDEDID,
                VideoContract.VideoEntry.COLUMN_STORAGEGROUP,
                VideoContract.VideoEntry.COLUMN_RECGROUP,
                VideoContract.VideoEntry.COLUMN_SEASON,
                VideoContract.VideoEntry.COLUMN_EPISODE,
                VideoContract.VideoEntry.COLUMN_PROGFLAGS,
                VideoContract.VideoEntry.COLUMN_VIDEOPROPS,
                VideoContract.VideoEntry.COLUMN_VIDEOPROPNAMES,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
                VideoContract.StatusEntry.COLUMN_LAST_USED
        };
    }

    static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = VideoContract.CONTENT_AUTHORITY;

        // For each type of URI to add, create a corresponding code.
        matcher.addURI(authority, VideoContract.PATH_VIDEO, VIDEO);
        matcher.addURI(authority, VideoContract.PATH_VIDEO + "/*", VIDEO_WITH_CATEGORY);

        // Search related URIs.
        matcher.addURI(authority, "search/" + SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
        matcher.addURI(authority, "search/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);
        return matcher;
    }

    private Cursor getSuggestions(String query) {
        query = query.toLowerCase();
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        if (db == null)
            return null;
        return sVideosContainingQueryBuilder.query(
                db,
                sVideosContainingQueryColumns,
                VideoContract.VideoEntry.COLUMN_TITLE + " LIKE ? OR " +
                        VideoContract.VideoEntry.COLUMN_SUBTITLE + " LIKE ?",
                new String[]{"%" + query + "%", "%" + query + "%"},
                null,
                null,
                null
        );
    }

    private static HashMap<String, String> buildColumnMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put(VideoContract.VideoEntry.COLUMN_RECTYPE, VideoContract.VideoEntry.COLUMN_RECTYPE);
        map.put(VideoContract.VideoEntry.COLUMN_TITLE, VideoContract.VideoEntry.COLUMN_TITLE);
        map.put(VideoContract.VideoEntry.COLUMN_DESC, VideoContract.VideoEntry.COLUMN_DESC);
        map.put(VideoContract.VideoEntry.COLUMN_SUBTITLE, VideoContract.VideoEntry.COLUMN_SUBTITLE);
        map.put(VideoContract.VideoEntry.COLUMN_VIDEO_URL,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL);
        map.put(VideoContract.VideoEntry.COLUMN_VIDEO_URL_PATH,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL_PATH);
        map.put(VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL,
                VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL);
        map.put(VideoContract.VideoEntry.COLUMN_CARD_IMG, VideoContract.VideoEntry.COLUMN_CARD_IMG);
        map.put(VideoContract.VideoEntry.COLUMN_CHANNEL, VideoContract.VideoEntry.COLUMN_CHANNEL);
        map.put(VideoContract.VideoEntry.COLUMN_CONTENT_TYPE,
                VideoContract.VideoEntry.COLUMN_CONTENT_TYPE);
        map.put(VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR,
                VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR);
        map.put(VideoContract.VideoEntry.COLUMN_DURATION, VideoContract.VideoEntry.COLUMN_DURATION);
        map.put(VideoContract.VideoEntry.COLUMN_AIRDATE, VideoContract.VideoEntry.COLUMN_AIRDATE);
        map.put(VideoContract.VideoEntry.COLUMN_STARTTIME, VideoContract.VideoEntry.COLUMN_STARTTIME);
        map.put(VideoContract.VideoEntry.COLUMN_ENDTIME, VideoContract.VideoEntry.COLUMN_ENDTIME);
        map.put(VideoContract.VideoEntry.COLUMN_ACTION, VideoContract.VideoEntry.COLUMN_ACTION);
        map.put(VideoContract.VideoEntry.COLUMN_RECORDEDID, VideoContract.VideoEntry.COLUMN_RECORDEDID);
        map.put(VideoContract.VideoEntry.COLUMN_STORAGEGROUP,
                VideoContract.VideoEntry.COLUMN_STORAGEGROUP);
        map.put(VideoContract.VideoEntry.COLUMN_RECGROUP, VideoContract.VideoEntry.COLUMN_RECGROUP);
        map.put(VideoContract.VideoEntry.COLUMN_SEASON,  VideoContract.VideoEntry.COLUMN_SEASON);
        map.put(VideoContract.VideoEntry.COLUMN_EPISODE, VideoContract.VideoEntry.COLUMN_EPISODE);
        map.put(VideoContract.VideoEntry.COLUMN_PROGFLAGS, VideoContract.VideoEntry.COLUMN_PROGFLAGS);
        map.put(VideoContract.VideoEntry.COLUMN_VIDEOPROPS, VideoContract.VideoEntry.COLUMN_VIDEOPROPS);
        map.put(VideoContract.VideoEntry.COLUMN_VIDEOPROPNAMES, VideoContract.VideoEntry.COLUMN_VIDEOPROPNAMES);
        map.put(VideoContract.VideoEntry.COLUMN_CHANID, VideoContract.VideoEntry.COLUMN_CHANID);
        map.put(VideoContract.VideoEntry.COLUMN_CHANNUM, VideoContract.VideoEntry.COLUMN_CHANNUM);
        map.put(VideoContract.VideoEntry.COLUMN_CALLSIGN, VideoContract.VideoEntry.COLUMN_CALLSIGN);
        map.put(VideoContract.StatusEntry.COLUMN_LAST_USED, VideoContract.StatusEntry.COLUMN_LAST_USED);

        map.put(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID, VideoContract.VideoEntry._ID + " AS " +
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID);
        map.put(SearchManager.SUGGEST_COLUMN_SHORTCUT_ID,
                VideoContract.VideoEntry._ID + " AS " + SearchManager.SUGGEST_COLUMN_SHORTCUT_ID);
        return map;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        Cursor retCursor;
        switch (sUriMatcher.match(uri)) {
            case SEARCH_SUGGEST: {
                String rawQuery = "";
                if (selectionArgs != null && selectionArgs.length > 0) {
                    rawQuery = selectionArgs[0];
                }
                retCursor = getSuggestions(rawQuery);
                break;
            }
            case VIDEO: {
                // In case any argument is null, a crash occurs, so
                // default it to empty string.
                for (int ix = 0 ; ix < selectionArgs.length; ix++) {
                    if (selectionArgs[ix] == null)
                        selectionArgs[ix] = "";
                }
                SQLiteDatabase db = mOpenHelper.getReadableDatabase();
                if (db == null)
                    return null;
                retCursor = db.query(
                        VideoContract.VideoEntry.VIEW_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
        // Release here becasue we have no control over when or if the cursor is used
        VideoDbHelper.releaseDatabase();
        if (retCursor != null)
            retCursor.setNotificationUri(mContentResolver, uri);
        return retCursor;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            // The application is querying the db for its own contents.
            case VIDEO_WITH_CATEGORY:
                return VideoContract.VideoEntry.CONTENT_TYPE;
            case VIDEO:
                return VideoContract.VideoEntry.CONTENT_TYPE;

            // The Android TV global search is querying our app for relevant content.
            case SEARCH_SUGGEST:
                return SearchManager.SUGGEST_MIME_TYPE;
            case REFRESH_SHORTCUT:
                return SearchManager.SHORTCUT_MIME_TYPE;

            // We aren't sure what is being asked of us.
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        final Uri returnUri;
        final int match = sUriMatcher.match(uri);

        if (match == VIDEO) {
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            if (db == null)
                return null;
            long _id = db.insert(
                    VideoContract.VideoEntry.TABLE_NAME, null, values);
            if (_id > 0) {
                returnUri = VideoContract.VideoEntry.buildVideoUri(_id);
            } else {
                throw new SQLException("Failed to insert row into " + uri);
            }
        } else {
            throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        VideoDbHelper.releaseDatabase();
        mContentResolver.notifyChange(uri, null);
        return returnUri;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        final int rowsDeleted;

        if (selection == null) {
            throw new UnsupportedOperationException("Cannot delete without selection specified.");
        }

        if (sUriMatcher.match(uri) == VIDEO) {
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            if (db == null)
                return 0;

            rowsDeleted = db.delete(
                    VideoContract.VideoEntry.TABLE_NAME, selection, selectionArgs);
        } else {
            throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        VideoDbHelper.releaseDatabase();
        if (rowsDeleted != 0) {
            mContentResolver.notifyChange(uri, null);
        }

        return rowsDeleted;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        final int rowsUpdated;

        if (sUriMatcher.match(uri) == VIDEO) {
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            if (db == null)
                return 0;
            rowsUpdated = db.update(
                    VideoContract.VideoEntry.TABLE_NAME, values, selection, selectionArgs);
        } else {
            throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        VideoDbHelper.releaseDatabase();
        if (rowsUpdated != 0) {
            mContentResolver.notifyChange(uri, null);
        }

        return rowsUpdated;
    }

    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        if (sUriMatcher.match(uri) == VIDEO) {
            final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            if (db == null)
                return 0;
            int returnCount = 0;

            db.beginTransaction();
            try {
                for (ContentValues value : values) {
                    long _id = db.insertWithOnConflict(VideoContract.VideoEntry.TABLE_NAME,
                            null, value, SQLiteDatabase.CONFLICT_REPLACE);
                    if (_id != -1) {
                        returnCount++;
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            VideoDbHelper.releaseDatabase();
            mContentResolver.notifyChange(uri, null);
            return returnCount;
        }
        return super.bulkInsert(uri, values);
    }
}
