package org.mythtv.leanfront.ui.playback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import org.mythtv.leanfront.data.CommBreakTable;
import org.mythtv.leanfront.player.VideoPlayerGlue;

public class SeekbarOverlay extends View {
    CommBreakTable commBreakTable;
    Paint adPaint;
    VideoPlayerGlue playerGlue;

    public SeekbarOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        adPaint = new Paint();
        adPaint.setARGB(128,128,0,0);
    }

    public void setup(CommBreakTable commBreakTable, VideoPlayerGlue playerGlue) {
        this.commBreakTable = commBreakTable;
        this.playerGlue = playerGlue;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (commBreakTable != null && commBreakTable.entries.length > 0) {
            int width = getWidth();
            int height = getHeight();
            int left = height / 4;
            width = width - height/2;
            long duration = playerGlue.myGetDuration();
            if (duration <= 0)
                return;
            long breakStart = -1;
            long breakEnd = -1;
            for (int ix = 0 ; ix < commBreakTable.entries.length; ix++) {
                CommBreakTable.Entry entry = commBreakTable.entries[ix];
                switch (entry.mark) {
                    case CommBreakTable.MARK_COMM_START:
                    case CommBreakTable.MARK_CUT_START:
                        breakStart = commBreakTable.getOffsetMs(entry);
                        break;
                    case CommBreakTable.MARK_COMM_END:
                    case CommBreakTable.MARK_CUT_END:
                        breakEnd = commBreakTable.getOffsetMs(entry);
                        break;
                }
                if (breakStart >= 0f && breakEnd >= 0f) {
                    canvas.drawRect( (float)(breakStart * width / duration + left),  height / 3f,
                            (float)(breakEnd * width / duration + left),  (height * 2f) / 3f, adPaint);
                    breakStart = -1;
                    breakEnd = -1;
                }
            }
        }
        super.onDraw(canvas);
    }
}
