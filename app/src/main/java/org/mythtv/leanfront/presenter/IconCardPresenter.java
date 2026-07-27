package org.mythtv.leanfront.presenter;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import org.mythtv.leanfront.model.RowSlot;

public class IconCardPresenter extends Presenter {
    private final Context mContext;
    private final int mType;

    public IconCardPresenter(Context context, int type) {
        super();
        mContext = context;
        mType = type;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(new IconCardView(mContext, mType));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object item) {
        if (item instanceof RowSlot)
            ((IconCardView)viewHolder.view).updateUi((RowSlot) item);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        ((IconCardView)viewHolder.view).updateUi(null);
    }
}
