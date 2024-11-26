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
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.mythtv.leanfront.BuildConfig;
import org.mythtv.leanfront.R;
import org.mythtv.leanfront.data.AsyncBackendCall;
import org.mythtv.leanfront.data.VideoContract;
import org.mythtv.leanfront.data.XmlNode;
import org.mythtv.leanfront.model.GuideSlot;
import org.mythtv.leanfront.model.Program;
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
        LoaderManager.LoaderCallbacks<Cursor>, AsyncBackendCall.OnBackendCallListener {
    private static final String TAG = "lfe";
    private static final String CLASS = "SearchFragment";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mRowsAdapter;
    private String mQuery;
    private final CursorObjectAdapter mVideoCursorAdapter =
            new CursorObjectAdapter(new CardPresenter());

    private int mSearchLoaderId = 1;
    private boolean mResultsFound = false;
    private boolean mGuideInProgress = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        mVideoCursorAdapter.setMapper(new VideoCursorMapper());

        setSearchResultProvider(this);
        setOnItemViewClickedListener(new ItemViewClickedListener());
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        return mRowsAdapter;
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
        if (DEBUG) Log.i(TAG, CLASS + String.format(" Search text submitted: %s", query));
        if (query.length() >= 3)
            loadQuery(query);
        return true;
    }

    public boolean hasResults()
    {
        return mRowsAdapter.size() > 0 && mResultsFound;
    }

    private void loadQuery(String query) {
        if (!TextUtils.isEmpty(query) && !"nil".equals(query)) {
            mRowsAdapter.clear();
            mQuery = query;
            mResultsFound = false;
            getLoaderManager().initLoader(mSearchLoaderId++, null, this);
            searchGuide();
        }
    }

    private void searchGuide() {
        // Search Program Guide
        if (!mGuideInProgress) {
            AsyncBackendCall call = new AsyncBackendCall(getActivity(), this);
            call.setStringParameter(mQuery);
            call.execute(Video.ACTION_SEARCHGUIDE_TITLE, Video.ACTION_SEARCHGUIDE_KEYWORD);
            mGuideInProgress = true;
        }
    }

    public void focusOnSearch() {
        getView().findViewById(R.id.lb_search_bar).requestFocus();
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String query = mQuery;
        return new CursorLoader(
                getActivity(),
                VideoContract.VideoEntry.CONTENT_URI,
                null, // Return all fields.
                VideoContract.VideoEntry.COLUMN_TITLE + " LIKE ? OR " +
                        VideoContract.VideoEntry.COLUMN_SUBTITLE + " LIKE ?",
                new String[]{"%" + query + "%", "%" + query + "%"},
                null // Default sort order
        );
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        // Do not refresh on a reload
        if (mRowsAdapter.size() > 0)
            return;
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
        mRowsAdapter.add(row);
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
            mRowsAdapter.add(row);
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {

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

}
