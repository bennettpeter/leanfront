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

package org.mythtv.leanfront.data;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;

import org.mythtv.leanfront.ui.playback.PlaybackFragment;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class MythHttpDataSource extends BaseDataSource implements DataSource {

    private DataSpec mDataSpec;
    private PlaybackFragment mPlaybackFragment;
    private HttpDataSource mHttpDataSource;
    private long mTotalLength;
    private long mCurrentPos;
    private long mOffsetBytes;
    private static final String TAG = "lfe";
    private static final String CLASS = "MythHttpDataSource";


    public MythHttpDataSource(String userAgent, PlaybackFragment playbackFragment){
        super(true);
        mPlaybackFragment = playbackFragment;
        Map<String, String> defaultRequestProperties = new HashMap<>();
        defaultRequestProperties.put("accept-encoding","identity");
        mHttpDataSource = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(defaultRequestProperties)
                .createDataSource();
        mPlaybackFragment.setDataSource(this);
    }

    @Override
    public long open(DataSpec dataSpec)
            throws IOException {
        mOffsetBytes = mPlaybackFragment.getOffsetBytes();
        this.mDataSpec = new DataSpec.Builder()
                .setUri(dataSpec.uri)
                .setHttpMethod(dataSpec.httpMethod)
                .setHttpBody(dataSpec.httpBody)
                .setPosition(dataSpec.position + mOffsetBytes)
                .setLength(dataSpec.length)
                .setKey(dataSpec.key)
                .setFlags(dataSpec.flags)
                .build();
        long leng = 0;
        try {
            leng = mHttpDataSource.open(mDataSpec);
        } catch (HttpDataSource.InvalidResponseCodeException e) {
            // Response code 416 = read past eof
            if (e.responseCode == 416) {
                leng = 0;
                Log.i(TAG, CLASS + " End of file.");
            }
            else {
                Log.e(TAG, CLASS + " Bad Http Response Code:" +e.responseCode
                    + " " + e.responseMessage);
                throw e;
            }
        }
        mTotalLength = mDataSpec.position + leng;
        mCurrentPos = mDataSpec.position;
        if (!mPlaybackFragment.isBounded())
            leng = -1;
        return leng;
    }

    public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength <= 0)
            return 0;
        int leng = mHttpDataSource.read(buffer,offset,readLength);
        if (leng == -1) {
            leng = 0;
        }
        if (leng == 0) {
            DataSpec dataSpec2 = new DataSpec.Builder()
                    .setUri(mDataSpec.uri)
                    .setHttpMethod(mDataSpec.httpMethod)
                    .setHttpBody(mDataSpec.httpBody)
                    .setPosition(mCurrentPos + leng)
                    .setLength(mDataSpec.length)
                    .setKey(mDataSpec.key)
                    .setFlags(mDataSpec.flags)
                    .build();
            mHttpDataSource.close();

            long leng2 = 0;
            try {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // Ignore this exception.
                }
                leng2 = mHttpDataSource.open(dataSpec2);
            } catch (HttpDataSource.InvalidResponseCodeException e) {
                // Response code 416 = read past eof
                if (e.responseCode == 416) {
                    leng2 = 0;
                    Log.i(TAG, CLASS + " End of file. (http code 416)");
                }
                else {
                    Log.e(TAG, CLASS + " Bad Http Response Code:" +e.responseCode
                            + " " + e.responseMessage);
                    throw e;
                }
            }
            long totalLength2 = dataSpec2.position + leng2;
            Log.d(TAG, CLASS + " Incremental data length:" + leng2);
            if (totalLength2 > mTotalLength) {
                mTotalLength = totalLength2;
                leng = mHttpDataSource.read(buffer, offset, readLength);
                mCurrentPos = dataSpec2.position;
                mDataSpec = dataSpec2;
            }
        }
        if (leng > 0)
            mCurrentPos += leng;
        else
            leng = C.RESULT_END_OF_INPUT;
        return leng;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return mDataSpec.uri;
    }

    @Override
    public void close() throws IOException {
        mHttpDataSource.close();
    }

    public long getCurrentPos() {
        return mCurrentPos;
    }

    public static class Factory implements DataSource.Factory {
        private PlaybackFragment mPlaybackFragment;
        private String mUserAgent;

        public Factory(String userAgent, PlaybackFragment playbackFragment) {
            mUserAgent = userAgent;
            mPlaybackFragment = playbackFragment;
        }

        @NonNull
        @Override
        public DataSource createDataSource() {
            return new MythHttpDataSource(mUserAgent, mPlaybackFragment);
        }
    }

}
