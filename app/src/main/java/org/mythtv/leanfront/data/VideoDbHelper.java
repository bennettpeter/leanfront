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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.mythtv.leanfront.data.VideoContract.VideoEntry;
import org.mythtv.leanfront.data.VideoContract.StatusEntry;


/**
 * VideoDbHelper manages the creation and upgrade of the database used in this sample.
 */
public class VideoDbHelper extends SQLiteOpenHelper {

    private static VideoDbHelper mInstance = null;

    // Change this when you change the database schema.
    private static final int DATABASE_VERSION = 20;
    // The name of our database.
    private static final String DATABASE_NAME = "leanback.db";

    private static int usageCount = 0;
    private static boolean dbLocked = false;
    private static Object sync = new Object();

    private VideoDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized VideoDbHelper getInstance(Context context) {
        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (mInstance == null) {
            mInstance = new VideoDbHelper(context.getApplicationContext());
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db,0,DATABASE_VERSION);
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        SQLiteDatabase db = null;
        synchronized (sync) {
            if (dbLocked)
                return null;
            db = super.getReadableDatabase();
            usageCount++;
        }
        return db;
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        SQLiteDatabase db = null;
        synchronized (sync) {
            if (dbLocked)
                return null;
            db = super.getWritableDatabase();
            usageCount++;
        }
        return db;
    }

    public static void releaseDatabase() {
        synchronized (sync) {
            usageCount--;
        }
    }

    public boolean lockDatabase() {
        synchronized (sync) {
            if (usageCount != 0)
                return false;
            if (dbLocked)
                return true;
            SQLiteDatabase db = super.getWritableDatabase();
            db.execSQL("vacuum");
            close();
            dbLocked = true;
        }
        return true;
    }

    public static void unlockDatabase() {
        synchronized (sync) {
            dbLocked = false;
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < DATABASE_VERSION) {
            // On any upgrade just recreate this table
            db.execSQL("DROP TABLE IF EXISTS " + VideoEntry.TABLE_NAME);
            // Create a table to hold videos.
            // This table gets deleted and recreated periodically
            final String SQL_CREATE_VIDEO_TABLE = "CREATE TABLE " + VideoEntry.TABLE_NAME + " (" +
                    VideoEntry._ID + " INTEGER PRIMARY KEY," +
                    VideoEntry.COLUMN_RECTYPE + " INTEGER, " +
                    VideoEntry.COLUMN_TITLE + " TEXT, " +
                    VideoEntry.COLUMN_TITLEMATCH + " TEXT, " +
                    VideoEntry.COLUMN_SUBTITLE + " TEXT, " +
                    VideoEntry.COLUMN_VIDEO_URL + " TEXT, " +
                    VideoEntry.COLUMN_VIDEO_URL_PATH + " TEXT UNIQUE, " +
                    VideoEntry.COLUMN_FILENAME + " TEXT, " +
                    VideoEntry.COLUMN_FILESIZE + " INTEGER, " +
                    VideoEntry.COLUMN_HOSTNAME + " TEXT, " +
                    VideoEntry.COLUMN_DESC + " TEXT, " +
                    VideoEntry.COLUMN_BG_IMAGE_URL + " TEXT, " +
                    VideoEntry.COLUMN_CHANNEL + " TEXT, " +
                    VideoEntry.COLUMN_CARD_IMG + " TEXT, " +
                    VideoEntry.COLUMN_CONTENT_TYPE + " TEXT, " +
                    VideoEntry.COLUMN_PRODUCTION_YEAR + " TEXT, " +
                    VideoEntry.COLUMN_DURATION + " TEXT, " +
                    VideoEntry.COLUMN_ACTION + " TEXT," +
                    VideoEntry.COLUMN_AIRDATE + " TEXT," +
                    VideoEntry.COLUMN_STARTTIME + " TEXT," +
                    VideoEntry.COLUMN_ENDTIME + " TEXT," +
                    VideoEntry.COLUMN_RECORDEDID + " TEXT," +
                    VideoEntry.COLUMN_STORAGEGROUP + " TEXT," +
                    VideoEntry.COLUMN_RECGROUP + " TEXT," +
                    VideoEntry.COLUMN_PLAYGROUP + " TEXT," +
                    VideoEntry.COLUMN_SEASON + " TEXT," +
                    VideoEntry.COLUMN_EPISODE + " TEXT," +
                    VideoEntry.COLUMN_PROGFLAGS + " TEXT," +
                    VideoEntry.COLUMN_VIDEOPROPS + " TEXT," +
                    VideoEntry.COLUMN_VIDEOPROPNAMES + " TEXT," +
                    VideoEntry.COLUMN_CHANID   + " TEXT," +
                    VideoEntry.COLUMN_CHANNUM  + " TEXT," +
                    VideoEntry.COLUMN_CALLSIGN + " TEXT" +
                    " );";

            // Do the creating of the table.
            db.execSQL(SQL_CREATE_VIDEO_TABLE);
        }
        // This table needs to be preserved. Use alter rather than recreating
        if (oldVersion < 1) {
            db.execSQL("DROP TABLE IF EXISTS " + StatusEntry.TABLE_NAME);
            // videostatus table keeps bookmarks even when video table is reloaded.
            // LAST_USED column is datetime, used to delete entries older than a month.
            final String SQL_CREATE_VIDEOSTATUS_TABLE = "CREATE TABLE " + StatusEntry.TABLE_NAME + " (" +
                    StatusEntry._ID + " INTEGER PRIMARY KEY," +
                    StatusEntry.COLUMN_VIDEO_URL_PATH + " TEXT NOT NULL UNIQUE, " +
                    StatusEntry.COLUMN_LAST_USED + " INTEGER NOT NULL, " +
                    StatusEntry.COLUMN_BOOKMARK + " INTEGER);";
            db.execSQL(SQL_CREATE_VIDEOSTATUS_TABLE);
        }
        if (oldVersion < 13) {
            final String SQL = "ALTER TABLE " + StatusEntry.TABLE_NAME +
                    " ADD COLUMN " +
                    StatusEntry.COLUMN_SHOW_RECENT + " INTEGER DEFAULT 1;";
            db.execSQL(SQL);
        }

        // For DB version 16, update video_url in status table to have only the path part of URL
        // If this creates duplicates, delete the duplicates, keeping the latest.
        if (oldVersion < 16) {
            // Define a projection that specifies which columns from the database
            // you will actually use after this query.
            String[] projection = {
                    StatusEntry._ID,
                    StatusEntry.COLUMN_VIDEO_URL_PATH
            };
            String orderby = StatusEntry.COLUMN_LAST_USED + " DESC ";

            Cursor cursor = db.query(
                    VideoContract.StatusEntry.TABLE_NAME,   // The table to query
                    projection,             // The array of columns to return (pass null to get all)
                    null,              // The columns for the WHERE clause
                    null,          // The values for the WHERE clause
                    null,                   // don't group the rows
                    null,                   // don't filter by row groups
                    orderby               // The sort order
            );

            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String url = cursor.getString(1);
                if (url.startsWith("http")) {
                    int ix = url.indexOf("//");
                    ix = url.indexOf('/',ix+2);
                    url = url.substring(ix);
                    ContentValues values = new ContentValues();
                    values.put(StatusEntry.COLUMN_VIDEO_URL_PATH,url);
                    try {
                        db.update(VideoContract.StatusEntry.TABLE_NAME,
                                values,
                                "_id = ?", new String[]{id});
                    } catch (SQLiteConstraintException ex) {
                        db.delete(VideoContract.StatusEntry.TABLE_NAME,
                                "_id = ?", new String[]{id});
                    }
                }
            }
            cursor.close();
        }
        // View for keeping track of recently watched
        if (oldVersion < DATABASE_VERSION) {
            final String DROP_VIEW = "DROP VIEW IF EXISTS " + VideoEntry.VIEW_NAME + ";";
            db.execSQL(DROP_VIEW);
            StringBuilder createView = new StringBuilder("CREATE VIEW " + VideoEntry.VIEW_NAME);
            // SDK version 24 uses SQLITE version 3.9 which will support view with column names
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                createView.append(" ( " +
                        VideoEntry.COLUMN_RECTYPE + " , " +
                        VideoEntry.COLUMN_TITLE + " , " +
                        VideoEntry.COLUMN_TITLEMATCH + " , " +
                        VideoEntry.COLUMN_SUBTITLE + " , " +
                        VideoEntry.COLUMN_VIDEO_URL + " , " +
                        VideoEntry.COLUMN_VIDEO_URL_PATH + " , " +
                        VideoEntry.COLUMN_FILENAME + " , " +
                        VideoEntry.COLUMN_FILESIZE + " , " +
                        VideoEntry.COLUMN_HOSTNAME + " , " +
                        VideoEntry.COLUMN_DESC + " , " +
                        VideoEntry.COLUMN_BG_IMAGE_URL + " , " +
                        VideoEntry.COLUMN_CHANNEL + " , " +
                        VideoEntry.COLUMN_CARD_IMG + " , " +
                        VideoEntry.COLUMN_CONTENT_TYPE + " , " +
                        VideoEntry.COLUMN_PRODUCTION_YEAR + " , " +
                        VideoEntry.COLUMN_DURATION + " , " +
                        VideoEntry.COLUMN_ACTION + " , " +
                        VideoEntry.COLUMN_AIRDATE + " ," +
                        VideoEntry.COLUMN_STARTTIME + " ," +
                        VideoEntry.COLUMN_ENDTIME + " ," +
                        VideoEntry.COLUMN_RECORDEDID + " ," +
                        VideoEntry.COLUMN_STORAGEGROUP + " ," +
                        VideoEntry.COLUMN_RECGROUP + " ," +
                        VideoEntry.COLUMN_PLAYGROUP + " ," +
                        VideoEntry.COLUMN_SEASON + " ," +
                        VideoEntry.COLUMN_EPISODE + " ," +
                        VideoEntry.COLUMN_PROGFLAGS + " ," +
                        VideoEntry.COLUMN_VIDEOPROPS + " ," +
                        VideoEntry.COLUMN_VIDEOPROPNAMES + " ," +
                        VideoEntry.COLUMN_CHANID + " ," +
                        VideoEntry.COLUMN_CHANNUM + " ," +
                        VideoEntry.COLUMN_CALLSIGN + " , " +
                        StatusEntry.COLUMN_LAST_USED + " , " +
                        StatusEntry.COLUMN_SHOW_RECENT + " ) ");
            }
            createView.append(" AS SELECT " +
                    VideoEntry.COLUMN_RECTYPE + " , " +
                    VideoEntry.COLUMN_TITLE + " , " +
                    VideoEntry.COLUMN_TITLEMATCH + " , " +
                    VideoEntry.COLUMN_SUBTITLE + " , " +
                    VideoEntry.TABLE_NAME+"."+VideoEntry.COLUMN_VIDEO_URL + " , " +
                    VideoEntry.COLUMN_VIDEO_URL_PATH + " , " +
                    VideoEntry.COLUMN_FILENAME + " , " +
                    VideoEntry.COLUMN_FILESIZE + " , " +
                    VideoEntry.COLUMN_HOSTNAME + " , " +
                    VideoEntry.COLUMN_DESC + " , " +
                    VideoEntry.COLUMN_BG_IMAGE_URL + " , " +
                    VideoEntry.COLUMN_CHANNEL + " , " +
                    VideoEntry.COLUMN_CARD_IMG + " , " +
                    VideoEntry.COLUMN_CONTENT_TYPE + " , " +
                    VideoEntry.COLUMN_PRODUCTION_YEAR + " , " +
                    VideoEntry.COLUMN_DURATION + " , " +
                    VideoEntry.COLUMN_ACTION + "  , " +
                    VideoEntry.COLUMN_AIRDATE + " ," +
                    VideoEntry.COLUMN_STARTTIME + " ," +
                    VideoEntry.COLUMN_ENDTIME + " ," +
                    VideoEntry.COLUMN_RECORDEDID + " ," +
                    VideoEntry.COLUMN_STORAGEGROUP + " ," +
                    VideoEntry.COLUMN_RECGROUP + " ," +
                    VideoEntry.COLUMN_PLAYGROUP + " ," +
                    VideoEntry.COLUMN_SEASON + " ," +
                    VideoEntry.COLUMN_EPISODE + " ," +
                    VideoEntry.COLUMN_PROGFLAGS + " ," +
                    VideoEntry.COLUMN_VIDEOPROPS + " ," +
                    VideoEntry.COLUMN_VIDEOPROPNAMES + " ," +
                    VideoEntry.COLUMN_CHANID + " ," +
                    VideoEntry.COLUMN_CHANNUM + " ," +
                    VideoEntry.COLUMN_CALLSIGN + " , " +
                    StatusEntry.COLUMN_LAST_USED + " , " +
                    StatusEntry.COLUMN_SHOW_RECENT + " FROM " +
                    VideoEntry.TABLE_NAME + " LEFT OUTER JOIN " +
                    StatusEntry.TABLE_NAME + " ON " +
                    VideoEntry.TABLE_NAME + "." + VideoEntry.COLUMN_VIDEO_URL_PATH + " = " +
                    StatusEntry.TABLE_NAME + "." + StatusEntry.COLUMN_VIDEO_URL_PATH + " ; ");
            db.execSQL(createView.toString());
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }
}
