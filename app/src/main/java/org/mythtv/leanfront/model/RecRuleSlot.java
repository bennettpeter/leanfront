package org.mythtv.leanfront.model;

import org.mythtv.leanfront.ui.UpcomingFragment;

//@SuppressLint("SimpleDateFormat")
public class RecRuleSlot extends RowSlot{
    public RecordRule rule;
    public Program program;
    public UpcomingFragment upcomingFragment;

    public RecRuleSlot(int cellType) {
        super(cellType);
    }

    public RecRuleSlot(int cellType, UpcomingFragment frag) {
        super(cellType);
        this.upcomingFragment = frag;
    }
    public RecRuleSlot(int cellType, RecordRule rule) {
        super(cellType);
        this.rule = rule;
    }
    public RecRuleSlot(int cellType, RecordRule rule, Program program) {
        super(cellType);
        this.rule = rule;
        this.program = program;
    }

}
