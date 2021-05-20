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

import android.content.res.Resources;

import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;
import androidx.core.content.ContextCompat;

import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.ui.MainFragment;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
    private int mSelectedBackgroundColor = -1;
    private int mDefaultBackgroundColor = -1;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mDefaultBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.default_background);
        mSelectedBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.selected_background);

        ImageCardView cardView = new ImageCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setInfoVisibility(BaseCardView.CARD_REGION_VISIBLE_ALWAYS);
        updateCardBackgroundColor(cardView, false);
        return new ViewHolder(cardView);
    }

    private void updateCardBackgroundColor(ImageCardView view, boolean selected) {
        int color = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;

        // Both background colors should be set because the view's
        // background is temporarily visible during animations.
        view.setBackgroundColor(color);
        view.findViewById(R.id.info_field).setBackgroundColor(color);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        Video video = (Video) item;

        ImageCardView cardView = (ImageCardView) viewHolder.view;
        String imageUrl = null;
        if (video.rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL) {
            cardView.setTitleText(video.channel);
            try {
                if (video.chanid != null)
                    imageUrl = XmlNode.mythApiUrl(null,"/Guide/GetChannelIcon?ChanId=" + video.chanid);
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
        }
        else {
            cardView.setTitleText(video.title);
            imageUrl = video.cardImageUrl;
        }
        int defaultIcon;
        switch (video.type) {
            case MainFragment.TYPE_VIDEODIR:
                defaultIcon = R.drawable.im_folder;
                break;
            case MainFragment.TYPE_SETTINGS:
                defaultIcon = R.drawable.ic_settings;
                break;
            case MainFragment.TYPE_REFRESH:
                defaultIcon = R.drawable.im_refresh;
                break;
            case MainFragment.TYPE_CHANNEL:
                defaultIcon = R.drawable.im_live_tv;
                break;
            case MainFragment.TYPE_INFO:
                defaultIcon = R.drawable.im_info;
                break;
            case MainFragment.TYPE_MANAGE:
                defaultIcon = R.drawable.ic_voicemail;
                break;
            default:
                switch (video.rectype) {
                    case VideoContract.VideoEntry.RECTYPE_CHANNEL:
                        defaultIcon = R.drawable.im_live_tv;
                        break;
                    default:
                        defaultIcon = R.drawable.im_movie;
                }

        }
        StringBuilder subtitle = new StringBuilder();
        if (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
            || video.rectype == VideoContract.VideoEntry.RECTYPE_VIDEO) {
            // symbols for deleted - "🗑" "🗶" "␡"
            if (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
                    && "Deleted".equals(video.recGroup))
                subtitle.append("\uD83D\uDDD1");
            int progflags = 0;
            if (video.progflags != null)
                progflags = Integer.parseInt(video.progflags);
            // possible characters for watched - "👁" "⏿" "👀"
            if ((progflags & Video.FL_WATCHED) != 0)
                subtitle.append("\uD83D\uDC41");
            // damaged character - 💥
            if (video.isDamaged())
                subtitle.append("\uD83D\uDCA5");
            if (video.season != null && video.season.compareTo("0") > 0) {
                subtitle.append('S').append(video.season).append('E').append(video.episode)
                        .append(' ');
            }
            subtitle.append(video.subtitle);
        }
        else if (video.rectype == VideoContract.VideoEntry.RECTYPE_CHANNEL) {
            if (video.channum != null && video.callsign != null) {
                subtitle.append("Channel").append(" ");
                subtitle.append(video.channum).append(" ");
                subtitle.append(video.callsign);
            }
        }
        cardView.setContentText(subtitle);
        // Set card size from dimension resources.
        Resources res = cardView.getResources();
        int width = res.getDimensionPixelSize(R.dimen.card_width);
        int height = res.getDimensionPixelSize(R.dimen.card_height);
        cardView.setMainImageDimensions(width, height);
        ImageView image = cardView.getMainImageView();
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);

        RequestOptions options = new RequestOptions()
                .error(defaultIcon);

        if (imageUrl == null) {
            Glide.with(cardView.getContext())
                    .load(defaultIcon)
                    .apply(options)
                    .into(image);
        }
        else
            Glide.with(cardView.getContext())
                    .load(imageUrl)
                    .apply(options)
                    .into(image);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ImageCardView cardView = (ImageCardView) viewHolder.view;

        // Remove references to images so that the garbage collector can free up memory.
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);
    }
}
