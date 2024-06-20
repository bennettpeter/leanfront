package org.mythtv.leanfront.presenter;

import android.content.Context;
import android.view.ViewGroup;

import androidx.leanback.widget.Presenter;

import org.mythtv.leanfront.model.RowSlot;

public class IconCardPresenter extends Presenter {
    private Context mContext;
    private int mType;

    public IconCardPresenter(Context context, int type) {
        super();
        mContext = context;
        mType = type;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(new IconCardView(mContext, mType));
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (item instanceof RowSlot)
            ((IconCardView)viewHolder.view).updateUi((RowSlot) item);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        ((IconCardView)viewHolder.view).updateUi(null);
    }
}
