/*
 * Copyright (c) 2014 The Android Open Source Project
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

package org.mythtv.leanfront.ui;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.app.SearchSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.mythtv.leanfront.BuildConfig;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.GuideSlot;
import org.mythtv.leanfront.model.Program;
import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.model.Video;
import org.mythtv.leanfront.model.VideoCursorMapper;
import org.mythtv.leanfront.presenter.CardPresenter;
import org.mythtv.leanfront.presenter.GuideCardPresenter;
import org.mythtv.leanfront.presenter.GuideCardView;
import org.mythtv.leanfront.ui.playback.PlaybackActivity;

import java.util.ArrayList;

/*
 * This class demonstrates how to do in-app search
 */
public class SearchFragment extends SearchSupportFragment
        implements SearchSupportFragment.SearchResultProvider,
        LoaderManager.LoaderCallbacks<Cursor>, AsyncBackendCall.OnBackendCallListener,
        BrowseSupportFragment.MainFragmentAdapterProvider,
        ManageRecordingsFragment.Paging {
    private static final String TAG = "lfe";
    private static final String CLASS = "SearchFragment";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mRowsAdapter;
    private String mQuery;
    private final CursorObjectAdapter mVideoCursorAdapter =
            new CursorObjectAdapter(new CardPresenter(this));

    private int mSearchLoaderId = 1;
    private boolean mResultsFound = false;
    private boolean mGuideInProgress = false;
    private BrowseSupportFragment.MainFragmentAdapter mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter(this);
    public int type;
    public static final int TYPE_SEARCH = 1;
    public static final int TYPE_NEWTITLES = 2;
    private int[] mSavedSelection = null;
    private String savedQuery;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null)
            mSavedSelection = null;
        else {
            mSavedSelection = savedInstanceState.getIntArray("selection");
            type = savedInstanceState.getInt("type");
            savedQuery = savedInstanceState.getString("query");
        }
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        mVideoCursorAdapter.setMapper(new VideoCursorMapper());

        setSearchResultProvider(this);
        setOnItemViewClickedListener(new ItemViewClickedListener());
        if (type == TYPE_NEWTITLES)
            setSearchQuery(getText(R.string.new_title_search).toString(), true);
        if (savedQuery != null && savedQuery.length() >= 3)
            onQueryTextSubmit(savedQuery);
    }

    @Override
    public void onPause() {
        mSavedSelection  = getSelection();
        mHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        mSavedSelection = getSelection();
        savedInstanceState.putIntArray("selection",mSavedSelection);
        savedInstanceState.putInt("type",type);
        savedInstanceState.putString("query",savedQuery);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mRowsAdapter;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (type == TYPE_NEWTITLES) {
            View view = getView().findViewById(R.id.lb_search_bar_items);
            if (view != null)
                view.setVisibility(View.GONE);
            view = getView().findViewById(R.id.lb_search_bar_speech_orb);
            if (view != null)
                view.setVisibility(View.GONE);
        }
    }

    /**
     * onQueryTextChange
     * Return false because we do not want to search after each keystroke
     * @param newQuery
     * @return
     */

    @Override
    public boolean onQueryTextChange(String newQuery) {
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mSavedSelection = null;
        if (DEBUG) Log.i(TAG, CLASS + String.format(" Search text submitted: %s", query));
        if (query.length() >= 3) {
            loadQuery(query);
            savedQuery = query;
        }
        return true;
    }

    public boolean hasResults()
    {
        return mRowsAdapter.size() > 0 && mResultsFound;
    }

    private void loadQuery(String query) {
        if (!TextUtils.isEmpty(query) && !"nil".equals(query)) {
            mQuery = query;
            mResultsFound = false;
            if (type == TYPE_SEARCH)
                LoaderManager.getInstance(this).initLoader(mSearchLoaderId++, null, this);
            else
                searchGuide();
        }
    }

    private void searchGuide() {
        // Search Program Guide
        if (!mGuideInProgress) {
            AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
            if (type == TYPE_NEWTITLES) {
                call.execute(Video.ACTION_SEARCHGUIDE_NEWTITLES);
            } else {
                call.setStringParameter(mQuery);
                call.execute(Video.ACTION_SEARCHGUIDE_TITLE, Video.ACTION_SEARCHGUIDE_KEYWORD);
            }
            mGuideInProgress = true;
        }
    }

    public void focusOnSearch() {
        getView().findViewById(R.id.lb_search_bar).requestFocus();
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String seq = Settings.getString("pref_seq");
        String ascdesc = Settings.getString("pref_seq_ascdesc");
        String query = mQuery;
        StringBuilder orderby = new StringBuilder();
        orderby.append(VideoContract.VideoEntry.COLUMN_TITLEMATCH).append(", ");
        if ("airdate".equals(seq)) {
            // +0 is used to convert the value to a number
            orderby.append(VideoContract.VideoEntry.COLUMN_SEASON).append("+0 ")
                    .append(ascdesc).append(", ");
            orderby.append(VideoContract.VideoEntry.COLUMN_EPISODE).append("+0 ")
                    .append(ascdesc).append(", ");
            orderby.append(VideoContract.VideoEntry.COLUMN_AIRDATE).append(" ")
                    .append(ascdesc).append(", ");
            orderby.append(VideoContract.VideoEntry.COLUMN_STARTTIME).append(" ")
                    .append(ascdesc);
        } else {
            orderby.append(VideoContract.VideoEntry.COLUMN_STARTTIME).append(" ")
                    .append(ascdesc).append(", ");
            orderby.append(VideoContract.VideoEntry.COLUMN_AIRDATE).append(" ")
                    .append(ascdesc);
        }
        // Add recordedid to sort for in case of duplicates or split recordings
        orderby.append(", ").append(VideoContract.VideoEntry.COLUMN_RECORDEDID).append(" ")
                .append(ascdesc);
        return new CursorLoader(
                getActivity(),
                VideoContract.VideoEntry.CONTENT_URI,
                null, // Return all fields.
                VideoContract.VideoEntry.COLUMN_TITLE + " LIKE ? OR " +
                VideoContract.VideoEntry.COLUMN_SUBTITLE + " LIKE ?",
                new String[]{"%" + query + "%", "%" + query + "%"},
               orderby.toString()
        );
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        int titleRes;
        if (cursor == null || cursor.isClosed())
            return;
        if (cursor.moveToFirst()) {
            mResultsFound = true;
            titleRes = R.string.search_result_videos;
        } else {
            titleRes = R.string.search_result_no_videos;
        }
        mVideoCursorAdapter.changeCursor(cursor);
        HeaderItem header = new HeaderItem(getContext().getString(titleRes,mQuery));
        ListRow row = new ListRow(header, mVideoCursorAdapter);
        if (mRowsAdapter.size() > 0)
            mRowsAdapter.replace(0, row);
        else
            mRowsAdapter.add(row);

        if (mSavedSelection != null) {
            SelectionSetter setter = new SelectionSetter(mSavedSelection[0], mSavedSelection[1]);
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(setter, 100);
        }
        searchGuide();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mVideoCursorAdapter.changeCursor(null);
    }

    @Override
    public void onPostExecute(AsyncBackendCall taskRunner) {
        int [] tasks = taskRunner.getTasks();
        switch (tasks[0]) {
            case Video.ACTION_SEARCHGUIDE_TITLE:
            case Video.ACTION_SEARCHGUIDE_NEWTITLES:
                mGuideInProgress = false;
                loadGuideData(taskRunner.getXmlResults());
                break;
        }
    }

    private static final int [] RESULT500 = {R.string.search_result_1_progs_500, R.string.search_result_2_progs_500};
    private static final int [] RESULTPROGS = {R.string.search_result_1_progs, R.string.search_result_2_progs};
    private static final int [] RESULTNOPROGS = {R.string.search_result_1_no_progs, R.string.search_result_2_no_progs};
    void loadGuideData(ArrayList <XmlNode> results) {
        if (results == null)
            return;
        for (int ix = 0; ix < results.size() ; ix++) {
            ArrayObjectAdapter guideAdapter = new ArrayObjectAdapter(new GuideCardPresenter(GuideCardView.TYPE_LARGE));
            XmlNode result = results.get(ix);
            XmlNode programNode = null;
            for (; ; ) {
                if (programNode == null)
                    programNode = result.getNode("Programs").getNode("Program");
                else
                    programNode = programNode.getNextSibling();
                if (programNode == null)
                    break;
                XmlNode chanNode = programNode.getNode("Channel");
                Program program = new Program(programNode, chanNode);
                String channum = chanNode.getString("ChanNum");
                String channelname = chanNode.getString("ChannelName");
                String callsign = chanNode.getString("CallSign");
                String chanDetails = channum + " " + channelname + " " + callsign;
                GuideSlot slot = new GuideSlot(program.chanId, -1, callsign, chanDetails);
                slot.cellType = GuideSlot.CELL_SEARCHRESULT;
                slot.timeSlot = program.startTime;
                slot.program = program;
                guideAdapter.add(slot);
            }
            int titleRes;
            if (guideAdapter.size() > 0) {
                mResultsFound = true;
                if (guideAdapter.size() == 500)
                    titleRes = RESULT500[ix];
                else
                    titleRes = RESULTPROGS[ix];
            } else
                titleRes = RESULTNOPROGS[ix];
            HeaderItem header = new HeaderItem(getContext().getString(titleRes, mQuery));
            Row row = new ListRow(header, guideAdapter);
            if (mRowsAdapter.size() > ix+1)
                mRowsAdapter.replace(ix+1, row);
            else
                mRowsAdapter.add(row);
        }
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    int [] getSelection() {
        int selectedRowNum = getRowsSupportFragment().getSelectedPosition();
        int selectedItemNum = -1;
        if (selectedRowNum >= 0) {
            ListRowPresenter.ViewHolder selectedViewHolder
                    = (ListRowPresenter.ViewHolder) getRowsSupportFragment()
                    .getRowViewHolder(selectedRowNum);
            if (selectedViewHolder != null)
                selectedItemNum = selectedViewHolder.getSelectedPosition();
        }
        return new int[]{selectedRowNum, selectedItemNum};
    }

    public void pageDown(int direction) {
        RowsSupportFragment frag = getRowsSupportFragment();
        int selectedRowNum = frag.getSelectedPosition();
        ListRowPresenter.ViewHolder selectedViewHolder
                = (ListRowPresenter.ViewHolder) getRowsSupportFragment()
                .getRowViewHolder(selectedRowNum);
        if (selectedViewHolder == null)
            return;
        int selectedItemNum = selectedViewHolder.getSelectedPosition();
        int newPos = selectedItemNum + 5 * direction; // 5 = 1 page
        if (newPos < 0)
            newPos = 0;
        ListRowPresenter.SelectItemViewHolderTask task
                = new ListRowPresenter.SelectItemViewHolderTask(newPos);
        task.setSmoothScroll(false);
        frag.setSelectedPosition(selectedRowNum, false, task);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {
            mSavedSelection = getSelection();
            if (item instanceof Video) {
                Video video = (Video) item;
                Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(PlaybackActivity.VIDEO, video);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        PlaybackActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            } else if (item instanceof GuideSlot) {
                GuideSlot card = (GuideSlot) item;
                Intent intent = new Intent(getContext(), EditScheduleActivity.class);
                intent.putExtra(EditScheduleActivity.CHANID, card.program.chanId);
                intent.putExtra(EditScheduleActivity.STARTTIME, card.program.startTime);
                startActivity(intent);
            } else {
                Toast.makeText(getActivity(), "Click", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class SelectionSetter implements Runnable {

        private int selectedRowNum;
        private int selectedItemNum;

        public SelectionSetter(int selectedRowNum, int selectedItemNum) {
            this.selectedRowNum = selectedRowNum;
            this.selectedItemNum = selectedItemNum;
        }
        public void run() {
            RowsSupportFragment frag = getRowsSupportFragment();
            if (frag != null && selectedRowNum >= 0 && selectedItemNum >= 0) {
                // Note we do not need to check selectedRowNum or
                // selectedItemNum, if either is more than the maximum
                // there is no exception - it just selects the last item.
                ListRowPresenter.SelectItemViewHolderTask task
                        = new ListRowPresenter.SelectItemViewHolderTask(selectedItemNum);
                task.setSmoothScroll(false);
                frag.setSelectedPosition(selectedRowNum, false, task);
            }
        }
    }

}
