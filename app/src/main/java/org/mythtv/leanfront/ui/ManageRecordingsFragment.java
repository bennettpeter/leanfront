package org.mythtv.leanfront.ui;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.Intent;
import android.os.Bundle;

import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Row;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.MyHeaderItem;

public class ManageRecordingsFragment extends BrowseSupportFragment {

    private static final int HEADER_ID_GUIDE = 1;
    private static final int HEADER_ID_RECRULES = 2;
    private static final int HEADER_ID_UPCOMING = 3;

    private ArrayObjectAdapter mRowsAdapter;
    private BackgroundManager mBackgroundManager;
    private boolean isGuide;
    private GuideFragment guideFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Intent intent = getActivity().getIntent();
        isGuide = "GUIDE".equals(intent.getCharSequenceExtra("TYPE"));
        super.onCreate(savedInstanceState);
        setupUi();
        loadData();
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        getMainFragmentRegistry().registerFragment(PageRow.class,
                new PageRowFragmentFactory(mBackgroundManager));

    }

    @Override
    public void onResume() {
        super.onResume();
        if (isGuide) {
            setHeadersState(HEADERS_DISABLED);
        }
    }

    private void setupUi() {
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setBrandColor(getResources().getColor(R.color.fastlane_background));
        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.search_opaque));
        if (isGuide)
            setTitle(getString(R.string.title_program_guide));
        else
            setTitle(getString(R.string.title_manage_recordings));
        setOnSearchClickedListener(view -> {
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
        });

        prepareEntranceTransition();
    }

    private void loadData() {
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mRowsAdapter);
        createRows();
        startEntranceTransition();
    }

    private void createRows() {
        if (!isGuide) {
            MyHeaderItem headerItem3 = new MyHeaderItem(HEADER_ID_UPCOMING, getString(R.string.title_upcoming));
            PageRow pageRow3 = new PageRow(headerItem3);
            mRowsAdapter.add(pageRow3);
            MyHeaderItem headerItem2 = new MyHeaderItem(HEADER_ID_RECRULES, getString(R.string.title_rec_rules));
            PageRow pageRow2 = new PageRow(headerItem2);
            mRowsAdapter.add(pageRow2);
        }
        MyHeaderItem headerItem1 = new MyHeaderItem(HEADER_ID_GUIDE, getString(R.string.title_program_guide));
        PageRow pageRow1 = new PageRow(headerItem1);
        mRowsAdapter.add(pageRow1);
        if (isGuide)
            setHeadersState(HEADERS_HIDDEN);
    }

    public void pageDown(int direction) {
//        RowsSupportFragment frag = getRowsSupportFragment();
        int selectedRowNum = getSelectedPosition();
        if (isGuide)
            selectedRowNum = 0;
        if (selectedRowNum < 0)
            return;
        if (isShowingHeaders())
            return;
        MyHeaderItem header = (MyHeaderItem) ((PageRow)mRowsAdapter.get(selectedRowNum)).getHeaderItem();
        long id = header.getId();
        if (id == HEADER_ID_GUIDE) {
            // This needs to go in guide creation and save the guide fragment in the magerecordings fragment
            // so i can access it here via a member.
            GuideFragment gfrag = (GuideFragment)header.getFragment();
            gfrag.pageDown(direction);
        }
//        RowsSupportFragment frag = getRowsSupportFragment();
//        int selectedRowNum = frag.getSelectedPosition();
//        ListRowPresenter.ViewHolder selectedViewHolder
//                = (ListRowPresenter.ViewHolder) getRowsSupportFragment()
//                .getRowViewHolder(selectedRowNum);
//        if (selectedViewHolder == null)
//            return;
//        int selectedItemNum = selectedViewHolder.getSelectedPosition();
//        int newPos = selectedItemNum + 5 * direction; // 5 = 1 page
//        if (newPos < 0)
//            newPos = 0;
//        ListRowPresenter.SelectItemViewHolderTask task
//                = new ListRowPresenter.SelectItemViewHolderTask(newPos);
//        task.setSmoothScroll(false);
//        frag.setSelectedPosition(selectedRowNum, false, task);
    }



    private static class PageRowFragmentFactory extends BrowseSupportFragment.FragmentFactory {
        private final BackgroundManager mBackgroundManager;

        PageRowFragmentFactory(BackgroundManager backgroundManager) {
            this.mBackgroundManager = backgroundManager;
        }

        @Override
        public Fragment createFragment(Object rowObj) {
            Row row = (Row)rowObj;
            MyHeaderItem header = (MyHeaderItem)row.getHeaderItem();
            mBackgroundManager.setDrawable(null);
            Fragment frag;
            switch ((int)header.getId()) {
                case HEADER_ID_GUIDE:
                    frag = new GuideFragment();
                    break;
                case HEADER_ID_RECRULES:
                    frag =  new RecRulesFragment();
                    break;
                case HEADER_ID_UPCOMING:
                    frag = new UpcomingFragment();
                    break;
                default:
                    return null;
            }
            header.setFragment(frag);
            return frag;
        }
    }

}
