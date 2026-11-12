package minidb.index;

import minidb.record.Value;
import minidb.table.Table.RowId;

import java.util.ArrayList;
import java.util.List;

/** One tree node as it looks after reading a page. Leaf: keys + rowIds. Internal: keys + rowIds (separators) + children. */
public class DiskNode {
    public static final int NO_PAGE = -1;

    public final boolean leaf;
    public final List<Value> keys = new ArrayList<>();
    public final List<RowId> rowIds = new ArrayList<>();      // leaf: the entries; internal: second half of each separator
    public final List<Integer> children = new ArrayList<>();  // internal only: always keys.size() + 1 page numbers
    public int next = NO_PAGE;                                // leaf only: next leaf in sorted order

    public DiskNode(boolean leaf) {
        this.leaf = leaf;
    }
}