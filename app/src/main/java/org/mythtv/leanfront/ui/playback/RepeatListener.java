package org.mythtv.leanfront.ui.playback;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

/**
 * A class, that can be used as a TouchListener on any view (e.g. a Button).
 * It cyclically runs a clickListener, emulating keyboard-like behavior. First
 * click is fired immediately, next one after the initialInterval, and subsequent
 * ones after the normalInterval.
 *
 * <p>Interval is scheduled after the onClick completes, so it has to run fast.
 * If it runs slow, it does not generate skipped onClicks. Can be rewritten to
 * achieve this.
 */
public class RepeatListener {


    private final int initialInterval;
    private final int normalInterval;
    private final PlaybackFragment playbackFragment;
    private final int direction;
    private boolean active;
    final Handler handler = new Handler(Looper.getMainLooper());


    private final Runnable handlerRunnable = new Runnable() {
        @Override
        public void run() {
            handler.postDelayed(this, normalInterval);
            playbackFragment.ffRew(direction);
        }
    };

    /**
     * @param initialInterval The interval after first click event
     * @param normalInterval The interval after second and subsequent click
     *       events
     * @param playbackFragment Playback Fragment
     * @param direction -1 for back, +1 for forward.
     */
    public RepeatListener(int initialInterval, int normalInterval,
            PlaybackFragment playbackFragment, int direction) {
        this.initialInterval = initialInterval;
        this.normalInterval = normalInterval;
        this.playbackFragment = playbackFragment;
        this.direction = direction;
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    public boolean onTouch(MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handler.removeCallbacks(handlerRunnable);
                handler.postDelayed(handlerRunnable, initialInterval);
                playbackFragment.ffRew(direction);
                active = true;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(handlerRunnable);
                active = false;
                return true;
        }

        return false;
    }

    public void cancel() {
        if (active) {
            handler.removeCallbacks(handlerRunnable);
            active = false;
        }
    }

}
