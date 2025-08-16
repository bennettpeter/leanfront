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

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.ts.TsPayloadReader;

import org.mythtv.leanfront.model.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UnstableApi
public class MyExtractorsFactory implements ExtractorsFactory {

    private DefaultExtractorsFactory defaultFactory;

    public MyExtractorsFactory() {
        this.defaultFactory = new DefaultExtractorsFactory();
    }

    @NonNull
    @Override
    public Extractor[] createExtractors() {
        Extractor[] exts = defaultFactory.createExtractors();
        updateExtractors(exts);
        return exts;
    }

    @OptIn(markerClass = UnstableApi.class) private void updateExtractors(Extractor[] exts) {
        for (int ix = 0; ix < exts.length; ix++) {
            if (exts[ix] instanceof TsExtractor) {
                List<Format> closedCaptionFormats = new ArrayList<>();
                int num = Settings.getInt("pref_num_cc_chans");
                for (int ccnum = 1 ; ccnum <= num; ccnum++)
                    closedCaptionFormats.add(
                            new Format.Builder()
                                    .setAccessibilityChannel(ccnum)
                                    .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                                    .build());
                TsPayloadReader.Factory payloadReaderFactory
                        =  new DefaultTsPayloadReaderFactory(
                        0,
                        closedCaptionFormats);
                TsExtractor ts = new TsExtractor(
                        TsExtractor.MODE_SINGLE_PMT,
                        0,
                        new DefaultSubtitleParserFactory(),
                        new TimestampAdjuster(0),
                        payloadReaderFactory,
                        Settings.getInt("pref_tweak_ts_search_pkts") * TsExtractor.TS_PACKET_SIZE);
                exts[ix] = ts;
            }
        }
    }
    @NonNull
    @Override
    public Extractor[] createExtractors(@NonNull Uri uri, @NonNull Map<String, List<String>> responseHeaders) {
        Extractor[] exts = defaultFactory.createExtractors(uri, responseHeaders);
        updateExtractors(exts);
        return exts;
    }
}
