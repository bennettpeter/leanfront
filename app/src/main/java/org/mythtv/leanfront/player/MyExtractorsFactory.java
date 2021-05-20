/*
 * Copyright (c) 2020 Peter Bennett
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

package org.mythtv.leanfront.player;

import android.net.Uri;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TimestampAdjuster;

import org.mythtv.leanfront.model.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MyExtractorsFactory implements ExtractorsFactory {

    private DefaultExtractorsFactory defaultFactory;

    public MyExtractorsFactory() {
        this.defaultFactory = new DefaultExtractorsFactory();
    }

    @Override
    public Extractor[] createExtractors() {
        Extractor[] exts = defaultFactory.createExtractors();
        updateExtractors(exts);
        return exts;
    }

    private void updateExtractors(Extractor[] exts) {
        for (int ix = 0; ix < exts.length; ix++) {
            if (exts[ix] instanceof com.google.android.exoplayer2.extractor.ts.TsExtractor) {
                List<Format> closedCaptionFormats = new ArrayList<>();
                closedCaptionFormats.add(
                        new Format.Builder()
                                .setAccessibilityChannel(1)
                                .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                                .build());
                closedCaptionFormats.add(
                        new Format.Builder()
                                .setAccessibilityChannel(2)
                                .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                                .build());
                TsPayloadReader.Factory payloadReaderFactory
                        =  new DefaultTsPayloadReaderFactory(
                        0,
                        closedCaptionFormats);
                exts[ix] = new TsExtractor(
                        TsExtractor.MODE_SINGLE_PMT,
                        new TimestampAdjuster(0),
                        payloadReaderFactory,
                        Settings.getInt("pref_tweak_ts_search_pkts") * TsExtractor.TS_PACKET_SIZE);
            }
        }
    }
    @Override
    public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
        Extractor[] exts = defaultFactory.createExtractors(uri, responseHeaders);
        updateExtractors(exts);
        return exts;
    }
}
