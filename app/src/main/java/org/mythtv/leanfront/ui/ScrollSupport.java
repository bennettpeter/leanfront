package org.mythtv.leanfront.ui;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowPresenter;
import androidx.recyclerview.widget.RecyclerView;

import org.mythtv.leanfront.R;

public class ScrollSupport {

    private final boolean isTV;
    private ScrollTask scrollTask;
    static int SCROLL_UPDATE_DELAY = 50;
    private ScrollListener scrollListener;
    private Handler mHandler;
    private RowsSupportFragment rowsSupportFragment;

    ScrollSupport(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        isTV = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        if (!isTV) {
            scrollTask = new ScrollTask();
            scrollListener = new ScrollListener();
            mHandler = new Handler();
        }
    }

    public void onItemSelected(Presenter.ViewHolder itemViewHolder,
        RowPresenter.ViewHolder rowViewHolder, RowsSupportFragment rowsSupportFragment) {
        if (!isTV && rowViewHolder != null && itemViewHolder != null) {
            this.rowsSupportFragment = rowsSupportFragment;
            HorizontalGridView view = rowViewHolder.view.findViewById(R.id.row_content);
            if (view != null && scrollListener.view != view) {
                if (scrollListener.view != null)
                    scrollListener.view.removeOnScrollListener(scrollListener);
                view.addOnScrollListener(scrollListener);
                scrollListener.rowViewHolder = (ListRowPresenter.ViewHolder)rowViewHolder;
                scrollListener.view = view;
                scrollTask.scrollSelected = -1;
            }
            scrollTask.view = itemViewHolder.view;
        }
    }

    private class ScrollListener extends RecyclerView.OnScrollListener {
        HorizontalGridView view;
        ListRowPresenter.ViewHolder rowViewHolder;
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState); // super does nothing
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                mHandler.removeCallbacks(scrollTask);
                scrollTask.scrollSelected = -1;
                scrollTask.direction = 0;
            } else {
                mHandler.postDelayed(scrollTask, SCROLL_UPDATE_DELAY);
            }
        }
    }

    /**
     * ScrollTask. Class attempts to smooth out scroll and fling when running on mobile.
     * These otherwise have the problem of jumping back to the start after a fling or
     * scroll. This code is far from perfect. The scroll and fling are still jittery
     * but at least are workable.
     */
    private class ScrollTask implements Runnable {
        // Currently selected item view
        View view;
        int scrollSelected = -1;
        int direction = 0;
        @Override
        public void run() {
            Rect rect = new Rect();
            int[] l = new int[2];
            view.getLocationOnScreen(l);
            int x = l[0];
            int w = view.getWidth();
            View mainView = rowsSupportFragment.getView();
            if (mainView == null)
                return;
            mainView.getGlobalVisibleRect(rect);
            int distance = 0;
            if (x < rect.left) {
                distance = Math.round((float)(rect.left - x) / w + 0.9f);
            }
            else if (x + w  > rect.right) {
                distance = - Math.round((float)(x + w - rect.right) / w + 0.9f);
            }
            boolean tryAgain = true;
            if (distance != 0) {
                if (direction == 0) {
                    if (distance > 0)
                        direction = 1;
                    else
                        direction = -1;
                }
                if (direction * distance < 0)
                    distance = direction;
                int selectedRowNum = rowsSupportFragment.getSelectedPosition();
                if (scrollSelected == -1 &&scrollListener.rowViewHolder != null)
                    scrollSelected = scrollListener.rowViewHolder.getSelectedPosition();
                if (scrollSelected >= 0)
                    scrollSelected += distance;
                if (scrollSelected >= 0) {
                    ListRowPresenter.SelectItemViewHolderTask task
                            = new ListRowPresenter.SelectItemViewHolderTask(scrollSelected);
                    task.setSmoothScroll(false);
                    rowsSupportFragment.setSelectedPosition(selectedRowNum, false,task);
                    tryAgain = false;
                }
            }
            if (tryAgain && scrollListener.view.getScrollState() != RecyclerView.SCROLL_STATE_IDLE)
                mHandler.postDelayed(scrollTask, SCROLL_UPDATE_DELAY);
        }
    }


}
