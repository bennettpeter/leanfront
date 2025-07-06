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

import static org.mythtv.leanfront.ui.MainFragment.TYPE_EPISODE;
import static org.mythtv.leanfront.ui.MainFragment.TYPE_SERIES;
import static org.mythtv.leanfront.ui.MainFragment.TYPE_VIDEO;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import androidx.fragment.app.Fragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.Presenter;
import androidx.core.content.ContextCompat;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.BackendCache;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.ui.MainFragment;
import org.mythtv.leanfront.ui.VideoAction;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
    private int mSelectedBackgroundColor = -1;
    private int mDefaultBackgroundColor = -1;
    private int mType = 0;
    public static final int TYPE_PLAYBACK = 1;
    private VideoAction videoAction;
    private Fragment fragment;

    public CardPresenter(Fragment fragment) {
        this.fragment = fragment;
    }

    public void setType(int mType) {
        this.mType = mType;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mDefaultBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.default_background);
        mSelectedBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.selected_background);

        VideoCardView cardView = new VideoCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setInfoVisibility(BaseCardView.CARD_REGION_VISIBLE_ALWAYS);
        videoAction = new VideoAction(fragment,null);
        updateCardBackgroundColor(cardView, false);
        return new MyViewHolder(cardView);
    }

    public void showVideoMenu(VideoCardView cardView) {
        Video video = cardView.getVideo();
        if (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
            || video.rectype == VideoContract.VideoEntry.RECTYPE_VIDEO) {
            videoAction.setSelectedVideo(cardView.getVideo());
            if (mType == TYPE_PLAYBACK)
                // If we are in playback use a menu thah dow not include
                // play options.
                videoAction.onActionClicked(new Action(Video.ACTION_OTHER));
            else
                videoAction.onActionClicked(new Action(Video.ACTION_REFRESH_FULL));
        }
    }

    private void updateCardBackgroundColor(VideoCardView view, boolean selected) {
        int color = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;

        // Both background colors should be set because the view's
        // background is temporarily visible during animations.
        view.setBackgroundColor(color);
        view.findViewById(R.id.info_field).setBackgroundColor(color);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolderIn, Object item) {
        View rootView = viewHolderIn.view;
        rootView.setFocusable(true);
        MyViewHolder viewHolder = (MyViewHolder) viewHolderIn;
        Video video = (Video) item;
        viewHolder.mVideo = video;
        VideoCardView cardView = (VideoCardView) viewHolder.view;
        cardView.setVideo(video);
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
            case MainFragment.TYPE_GUIDE:
                defaultIcon = R.drawable.ic_guide;
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
            // damaged character - 💥
            if (video.isDamaged())
                subtitle.append("\uD83D\uDCA5");
            // Bookmark - 📖 or 🕮
            // Currently commented because videos do not have this filled in, only
            // recordings have it.
//            if (video.isBookmarked())
//                subtitle.append("\uD83D\uDCD6");
            // possible characters for watched - "👁" "⏿" "👀"
            if (video.isWatched())
                subtitle.append("\uD83D\uDC41");
            // symbols for deleted - "🗑" "🗶" "␡"
            if (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING
                    && "Deleted".equals(video.recGroup))
                subtitle.append("\uD83D\uDDD1");
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
        else {

            String auth =  BackendCache.getInstance().authorization;
            LazyHeaders.Builder lzhb =  new LazyHeaders.Builder();
            if (auth != null && auth.length() > 0)
                lzhb.addHeader("Authorization", auth);
            GlideUrl url = new GlideUrl(imageUrl, lzhb.build());

            Glide.with(cardView.getContext())
                    .load(url)
                    .apply(options)
                    .into(image);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolderIn) {
        MyViewHolder viewHolder = (MyViewHolder) viewHolderIn;
        VideoCardView cardView = (VideoCardView) viewHolder.view;

        // Remove references to images so that the garbage collector can free up memory.
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);
        viewHolder.mVideo = null;
    }
    class MyViewHolder extends Presenter.ViewHolder implements AsyncBackendCall.OnBackendCallListener {
        Video mVideo;

        public MyViewHolder(View viewIn) {
            super(viewIn);
            view.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN)
                    return false;
                int liType = -1;
                switch (keyCode) {
                    // Support play from the card unless already playing
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        if (mVideo == null || mType == TYPE_PLAYBACK)
                            return true;
                        liType = mVideo.getItemType();
                        switch (liType) {
                            case TYPE_EPISODE:
                            case TYPE_VIDEO:
                            case TYPE_SERIES:
                                AsyncBackendCall call = new AsyncBackendCall(null, this);
                                call.setView(viewIn);
                                call.setVideo(mVideo);
                                call.execute(Video.ACTION_REFRESH);
                                return true;
                        }
                        break;
                    // Menu while playing is passed through. In other cases is thrown away.
                    // To avoid clicking menu on an item in the related videos list
                    // and finding it actually operates on the main video.
                    case KeyEvent.KEYCODE_MENU:
                        showVideoMenu((VideoCardView) viewIn);
                        return true;
                }
                return false;
            });
            viewIn.setOnLongClickListener( (v) ->  {
                showVideoMenu((VideoCardView) viewIn);
                return true;
            });

        }
        public void onPostExecute(AsyncBackendCall taskRunner) {
            Context context = view.getContext();
            if (taskRunner == null)
                return;
            int[] tasks = taskRunner.getTasks();
            Intent intent;
            switch (tasks[0]) {
                case Video.ACTION_REFRESH:
                    if (context == null)
                        break;
                    long bookmark = taskRunner.getBookmark();
                    long posBookmark = taskRunner.getPosBookmark();
                    long lastPlay = taskRunner.getLastPlay();
                    long posLastPlay = taskRunner.getPosLastPlay();
                    if (lastPlay > 0 || posLastPlay > 0) {
                        bookmark = lastPlay;
                        posBookmark = posLastPlay;
                    }
                    intent = new Intent(context, PlaybackActivity.class);
                    intent.putExtra(PlaybackActivity.VIDEO, taskRunner.getVideo());
                    intent.putExtra(PlaybackActivity.BOOKMARK, bookmark);
                    intent.putExtra(PlaybackActivity.POSBOOKMARK, posBookmark);
                    context.startActivity(intent);
                    break;
            }
        }
    }
}
