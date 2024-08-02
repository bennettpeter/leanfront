/*
 * Copyright (c) 2015 The Android Open Source Project
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

package org.mythtv.leanfront.presenter;

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.Video;

import android.annotation.SuppressLint;
import android.content.Context;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

@SuppressLint("SimpleDateFormat")
public class DetailsDescriptionPresenter extends AbstractDetailsDescriptionPresenter {
    private ViewHolder mViewHolder;
    private Video mVideo;

    @Override
    protected void onBindDescription(ViewHolder viewHolder, Object item) {
        mVideo = (Video) item;
        mViewHolder = viewHolder;
        setupDescription();
    }
    
    @SuppressLint("SimpleDateFormat")
    public void setupDescription() {
        if (mVideo == null)
            return;
        Context context = mViewHolder.getBody().getContext();
        if (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
            || mVideo.rectype == VideoContract.VideoEntry.RECTYPE_VIDEO) {
            mViewHolder.getTitle().setText(mVideo.title);
            String subtitle = getSubtitle();
            mViewHolder.getSubtitle().setText(subtitle);
            String description = getDescription(null);
            mViewHolder.getBody().setText(description);
        }
        else if (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL) {
            mViewHolder.getTitle().setText(mVideo.channel);

            mViewHolder.getSubtitle().setText(
                    String.format(context.getString(R.string.channel_item_subtitle), mVideo.channum, mVideo.callsign));
            mViewHolder.getBody().setText("");

        }
    }
    public String getDescription(XmlNode xml) {
        if (mVideo == null)
            return null;
        boolean isRecording = (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);
        Context context = mViewHolder.getBody().getContext();
        if (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
                || mVideo.rectype == VideoContract.VideoEntry.RECTYPE_VIDEO) {
            StringBuilder description = new StringBuilder("\n");

            // 2018-05-23T00:00:00Z
            try {
                // Date Recorded
                SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
                DateFormat outFormat = android.text.format.DateFormat.getMediumDateFormat(context);
                String recDate = null;
                if (mVideo.starttime != null) {
                    Date date = dbFormat.parse(mVideo.starttime + "+0000");
                    recDate = outFormat.format(date);
                    description.append(recDate);
                }
                // Length of recording
                long duration = Long.parseLong(mVideo.duration, 10);
                duration = duration / 60000;
                if (duration > 0) {
                    if (description.length() > 0)
                        description.append(", ");
                    description.append(duration).append(" ").append(context.getString(R.string.video_minutes));
                }
                // Channel
                if (mVideo.channel != null && mVideo.channel.length()>0)
                    description.append("  ").append(mVideo.channel);
                // Original Air date
                dbFormat = new SimpleDateFormat("yyyy-MM-dd");
                if (mVideo.airdate != null) {
                    if ("01-01".equals(mVideo.airdate.substring(5)))
                        description.append("   [").append(mVideo.airdate.substring(0, 4)).append("]");
                    else {
                        Date date = dbFormat.parse(mVideo.airdate);
                        String origDate = outFormat.format(date);
                        if (!Objects.equals(origDate,recDate))
                            description.append("   [").append(outFormat.format(date)).append("]");
                    }
                }
                description.append('\n');
            } catch (Exception e) {
                e.printStackTrace();
            }
            description
                    .append(mVideo.description);
            if (xml != null) {
                NumberFormat fmt = NumberFormat.getInstance();
                fmt.setMaximumFractionDigits(1);

                description.append("\n\n")
                        .append(context.getString(R.string.video_url)).append("\n")
                        .append(mVideo.videoUrl);

                description
                        .append("\n\n")
                        .append(context.getString(R.string.video_filename)).append(":\t")
                        .append(mVideo.filename);
                if (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING)
                    description.append("\n")
                            .append(context.getString(R.string.video_filesize)).append(":\t")
                            .append(fmt.format(((double) mVideo.filesize) / 1000000.0))
                            .append(" MB");
                if (mVideo.videoPropNames != null && mVideo.videoPropNames.length() > 0
                    && !mVideo.videoPropNames.equals("UNKNOWN")) {
                    description.append("\n")
                            .append(context.getString(R.string.video_props)).append(":\t")
                            .append(mVideo.videoPropNames.replace("|",", "));
                }
                description.append("\n")
                        .append(context.getString(R.string.sched_storage_grp)).append(":\t")
                        .append(mVideo.storageGroup);
                if (mVideo.recGroup != null) {
                    description.append("\n")
                            .append(context.getString(R.string.sched_rec_group)).append(":\t")
                            .append(mVideo.recGroup);
                }
                if (mVideo.playGroup != null) {
                    description.append("\n")
                            .append(context.getString(R.string.sched_play_group)).append(":\t")
                            .append(mVideo.playGroup);
                }

                if (isRecording)
                    addRecDetails(context, xml, description);
                else
                    addVidDetails(context, xml, description);

            }
            return description.toString();
        }
        return null;
    }

    public String getSubtitle() {
        if (mVideo == null)
            return null;
        if (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
                || mVideo.rectype == VideoContract.VideoEntry.RECTYPE_VIDEO) {

            StringBuilder subtitle = new StringBuilder();
            // damaged character - 💥
            if (mVideo.isDamaged())
                subtitle.append("\uD83D\uDCA5");
            // Bookmark - 📖 or 🕮
            // Currently commented because videos do not have this filled in, only
            // recordings have it.
//            if (mVideo.isBookmarked())
//                subtitle.append("\uD83D\uDCD6");
            // possible characters for watched - "👁" "⏿" "👀"
            if (mVideo.isWatched())
                subtitle.append("\uD83D\uDC41");
            // symbols for deleted - "🗑" "🗶" "␡"
            if (mVideo.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
                    && "Deleted".equals(mVideo.recGroup))
                subtitle.append("\uD83D\uDDD1");
            if (mVideo.season != null && mVideo.season.compareTo("0") > 0) {
                subtitle.append('S').append(mVideo.season).append('E').append(mVideo.episode)
                        .append(' ');
            }
            subtitle.append(mVideo.subtitle);
            return subtitle.toString();
        }
        return null;
    }

    private void addRecDetails(Context context, XmlNode xml, StringBuilder description) {
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
        DateFormat dateFormat = android.text.format.DateFormat.getMediumDateFormat(context);
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
        XmlNode rec = xml.getNode("Recording");
        description.append("\n")
                .append(context.getString(R.string.details_category)).append(":\t")
                .append(xml.getString("Category"));
        description.append("\n")
                .append(context.getString(R.string.details_starttime)).append(":\t");
        try {
            Date date = dbFormat.parse(rec.getString("StartTs")+"+0000");
            description.append(dateFormat.format(date))
                    .append(' ').append(timeFormat.format(date));
        } catch(Exception e) {
            e.printStackTrace();
        }
        description.append("\n")
                .append(context.getString(R.string.details_endtime)).append(":\t");
        try {
            Date date = dbFormat.parse(rec.getString("EndTs")+"+0000");
            description.append(dateFormat.format(date))
                    .append(' ').append(timeFormat.format(date));
        } catch(Exception e) {
            e.printStackTrace();
        }
        description.append("\n")
                .append(context.getString(R.string.details_audioprops)).append(":\t")
                .append(xml.getString("AudioPropNames"));
        description.append("\n")
                .append(context.getString(R.string.details_hostname)).append(":\t")
                .append(xml.getString("HostName"));
        description.append("\n")
                .append(context.getString(R.string.details_statusname)).append(":\t")
                .append(rec.getString("StatusName"));
        description.append("\n")
                .append(context.getString(R.string.details_encodername)).append(":\t")
                .append(rec.getString("EncoderName"));

        XmlNode cast = xml.getNode("Cast").getNode("CastMembers").getNode("CastMember");
        boolean head = false;
        while (cast != null) {
            if (!head) {
                description.append("\n\n")
                        .append(context.getString(R.string.details_cast));
                head = true;
            }
            description.append("\n");
            String role = cast.getString("CharacterName");
            if (role == null || role.length() == 0)
                role = cast.getString("TranslatedRole");
            description.append(role).append(":\t")
                    .append(cast.getString("Name"));
            cast = cast.getNextSibling();
        }
    }
    private void addVidDetails(Context context, XmlNode xml, StringBuilder description) {
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");
        DateFormat dateFormat = android.text.format.DateFormat.getMediumDateFormat(context);
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
        description.append("\n")
                .append(context.getString(R.string.details_genre)).append(":\t");
        XmlNode genre = xml.getNode("Genres").getNode("GenreList").getNode("Genre");
        boolean head = false;
        while (genre != null) {
            if (head)
                description.append(", ");
            head = true;
            description.append(genre.getString("Name"));
            genre = genre.getNextSibling();
        }
        description.append("\n")
                .append(context.getString(R.string.details_adddate)).append(":\t");
        try {
            Date date = dbFormat.parse(xml.getString("AddDate") + "+0000");
            description.append(dateFormat.format(date));
        } catch (Exception e) {
            e.printStackTrace();
        }
        description.append("\n")
                .append(context.getString(R.string.details_director)).append(":\t")
                .append(xml.getString("Director"));
        description.append("\n")
                .append(context.getString(R.string.details_studio)).append(":\t")
                .append(xml.getString("Studio"));
        description.append("\n")
                .append(context.getString(R.string.details_certification)).append(":\t")
                .append(xml.getString("Certification"));
        description.append("\n")
                .append(context.getString(R.string.details_hostname)).append(":\t")
                .append(xml.getString("HostName"));

    }
}
