package org.mythtv.leanfront.model;

public class RowSlot {
    public int cellType = 0;
    public static final int CELL_TIMESLOT = 1;
    public static final int CELL_CHANNEL = 2;
    public static final int CELL_PROGRAM = 3;
    public static final int CELL_TIMESELECTOR = 4;
    public static final int CELL_LEFTARROW = 5;
    public static final int CELL_RIGHTARROW = 6;
    public static final int CELL_SEARCHRESULT = 7;
    // For use with UpcomingFragment
    public static final int CELL_RULE = 8;
    public static final int CELL_PENCIL = 9;
    public static final int CELL_PAPERCLIP = 10;
    public static final int CELL_CHECKBOX = 11;
    public static final int CELL_EMPTY = 12;

    public RowSlot(int cellType) {
        this.cellType = cellType;
    }
}
