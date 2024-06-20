package org.mythtv.leanfront.presenter;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.ImageView;

import androidx.leanback.widget.BaseCardView;

import org.mythtv.leanfront.R;
import org.mythtv.leanfront.model.RecRuleSlot;
import org.mythtv.leanfront.model.RowSlot;

public class IconCardView extends BaseCardView {

    public static final int TYPE_SMALL = 1;
    public static final int TYPE_LARGE = 2;
    public static final int TYPE_WIDE = 3;

    public IconCardView(Context context, int type) {
        super(context);
        int layout = 0;
        switch (type) {
            case TYPE_LARGE:
                layout = R.layout.icon_card;
                break;
            case TYPE_SMALL:
                layout = R.layout.icon_card_small;
                break;
            case TYPE_WIDE:
                layout = R.layout.checkbox_card_wide;
                break;
        }
        LayoutInflater.from(getContext()).inflate(layout, this);
        setFocusable(true);
    }

//    public void updateUi(GuideSlot card) {
    public void updateUi(RowSlot slot) {
        if (slot ==  null)
            return;
        ImageView imageView = findViewById(R.id.card_image);
        switch (slot.cellType) {
            case RowSlot.CELL_LEFTARROW:
                imageView.setImageResource(R.drawable.im_arrow_left);
                break;
            case  RowSlot.CELL_RIGHTARROW:
                imageView.setImageResource(R.drawable.im_arrow_right);
                break;
            case  RowSlot.CELL_PENCIL:
                imageView.setImageResource(R.drawable.pencil);
                break;
            case  RowSlot.CELL_EMPTY:
                imageView.setImageResource(0);
                break;
            case  RowSlot.CELL_PAPERCLIP:
                imageView.setImageResource(R.drawable.paperclip);
                break;
            case  RowSlot.CELL_CHECKBOX:
                CheckBox cb = findViewById(R.id.checkBox);
                cb.setText(R.string.checkbox_all_status);
                boolean state = ((RecRuleSlot) slot).upcomingFragment.allStatusValues;
                cb.setChecked(state);
                break;
        }
    }

}
