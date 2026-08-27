package org.mythtv.leanfront.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.mythtv.leanfront.model.Video;

@SuppressLint("AppCompatCustomView")
public class ImageOverlay extends ImageView {
    Video video;
    Paint progPaint = new Paint();
    Paint bgPaint = new Paint();
    float thickness;
    float space;
    public ImageOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        progPaint.setColor(Color.RED);
        bgPaint.setColor(Color.GRAY);
        thickness = 9f;
        space = 1f;
    }

    public void setVideo(Video video) {
        this.video = video;
    }
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (video != null && video.lastPlay > 0 && video.duration > 0) {
            float width = getWidth();
            float height = getHeight();
            float right = (width - space * 2) * video.lastPlay / video.duration + space;
            canvas.drawRect(space, height - thickness * 2,
                    right, height - thickness, progPaint);
            canvas.drawRect(right, height - thickness * 2,
                    width-space, height-thickness, bgPaint);

        }
    }
}
