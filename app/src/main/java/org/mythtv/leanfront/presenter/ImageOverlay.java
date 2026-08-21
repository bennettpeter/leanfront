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
import org.mythtv.leanfront.ui.MainFragment;

@SuppressLint("AppCompatCustomView")
public class ImageOverlay extends ImageView {
    Video video;
    Paint progPaint = new Paint();
    Paint bgPaint = new Paint();
    public ImageOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        progPaint.setColor(Color.RED);
        bgPaint.setColor(Color.GRAY);
    }

    public void setVideo(Video video) {
        this.video = video;
    }
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (video != null && video.lastPlay > 0) {
            float width = getWidth();
            float height = getHeight();
            float thickness = height / 40;
            float right = (width-thickness) * video.lastPlay / video.duration;
            canvas.drawRect(thickness, height - thickness*2,
                    right, height - thickness, progPaint);
            canvas.drawRect(right, height-thickness*2,
                    width-thickness, height-thickness, bgPaint);

        }
    }
}
