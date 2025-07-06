package org.mythtv.leanfront.presenter;

import android.content.Context;

import androidx.leanback.widget.ImageCardView;

import org.mythtv.leanfront.model.Video;

public class VideoCardView extends ImageCardView {
    Video video;
    public VideoCardView(Context context) {
        super(context);
        setFocusable(true);
    }

    public Video getVideo() {
        return video;
    }

    public void setVideo(Video video) {
        this.video = video;
    }
}
