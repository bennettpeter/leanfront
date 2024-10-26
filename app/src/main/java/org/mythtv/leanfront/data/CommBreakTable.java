package org.mythtv.leanfront.data;

import android.util.Log;

import androidx.annotation.Nullable;

public class CommBreakTable {
    public Entry[] entries = new Entry[0];
    public int offSetType = 0;
    public static final int OFFSET_FRAME = 1;
    public static final int OFFSET_DURATION = 2;
    // default to 1 to prevent a zero division if not set.
    public long frameratex1000 = 1;

    private static final String TAG = "lfe";
    private static final String CLASS = "CommBreakTable";
    public static final int MARK_COMM_START = 4;
    public static final int MARK_COMM_END   = 5;
    public static final int MARK_CUT_START   = 1;
    public static final int MARK_CUT_END   = 0;
    public static final int MARK_VIDEO_RATE = 32;

    public synchronized void clear(int count) {
        entries = new Entry[count];
    }

    public synchronized void load(XmlNode data) {
        XmlNode node = data;
        int firstmark = -1;
        int lastmark = -1;
        if (node != null)
            node = node.getNode("Cuttings");
        if (node != null)
            node = node.getNode("Cutting");
        int nodeCount = 0;
        while (node != null) {
            XmlNode nextNode = node.getNextSibling();
            int mark = node.getInt("Mark", -99);
            if (mark == MARK_VIDEO_RATE) {
                frameratex1000 =  node.getInt("Offset", 1);
                node = nextNode;
                continue;
            }
            if (firstmark == -1)
                firstmark = mark;
            nodeCount++;
            if (nextNode == null)
                lastmark = mark;
            node = nextNode;
        }
        // Cater for "Cut to start" where there is no start entry
        if (firstmark == MARK_COMM_END || firstmark == MARK_CUT_END)
            nodeCount++;
        // Cater for "Cut to end" where there is no end entry
        if (lastmark == MARK_COMM_START || lastmark == MARK_CUT_START)
            nodeCount++;
        Log.i(TAG, CLASS + " CommBreakTable size:" + nodeCount );
        clear(nodeCount);
        node = data;
        if (node != null)
            node = node.getNode("Cuttings");
        if (node != null)
            node = node.getNode("Cutting");
        int ix = 0;
        int prior = 0;
        // Cater for "Cut to start" where there is no start entry
        // by creating a first entry
        if (firstmark == MARK_COMM_END || firstmark == MARK_CUT_END)
            entries[ix++] = new Entry(0,MARK_CUT_START);
        while (node != null) {
            int mark = node.getInt("Mark", 0);
            if (mark == MARK_VIDEO_RATE) {
//                frameratex1000 =  node.getInt("Offset", 1);
                node = node.getNextSibling();;
                continue;
            }
            int duration = node.getInt("Offset", 0);
            if (duration < prior) {
                Log.e(TAG, CLASS + " CommBreakTable out of sequence:" + prior + "," + duration);
                clear(0);
                return;
            }
            prior = duration;
            switch (mark) {
                case MARK_COMM_START:
                    mark = MARK_CUT_START;
                    break;
                case MARK_COMM_END:
                    mark = MARK_CUT_END;
                    break;
            }
            entries[ix++] = new Entry (duration, mark);
            node = node.getNextSibling();
        }
        if (ix == 0) {
            clear(0);
            return;
        }
        // Cater for "Cut to end" where there is no end entry
        // by creating a last entry
        if (lastmark == MARK_COMM_START || lastmark == MARK_CUT_START)
            entries[ix++] = new Entry(Integer.MAX_VALUE - 10000000, MARK_CUT_END);
        // Fill in any unused entries with the last entry
        while (ix < nodeCount)
            entries[ix++] = new Entry(Integer.MAX_VALUE - 10000000, MARK_CUT_START);
    }

    public long getOffsetMs(Entry entry) {
        if (offSetType == OFFSET_DURATION)
            return entry.offset;
        return entry.offset * 1000000 / frameratex1000;
    }

    public static class Entry implements Comparable<Entry> {

        private final long offset;
        public final int mark;

        public Entry(long offset, int mark) {
            this.offset = offset;
            this.mark = mark;
        }

        @Override
        public int compareTo(Entry o) {
            long diff =  offset - o.offset;
            if (diff < 0)
                return -1;
            if (diff > 0)
                return 1;
            return 0;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (o instanceof Entry)
                return (offset == ((Entry) o).offset);
            return super.equals(o);
        }
    }
}