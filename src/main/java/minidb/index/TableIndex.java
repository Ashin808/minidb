package minidb.index;

import minidb.catalog.Column;
import minidb.record.Row;
import minidb.record.Value;
import minidb.table.Table;
import minidb.table.Table.RowId;
import minidb.table.Table.StoredRow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** An index on one column of one table: column value -> RowIds, stored in a DiskBTree file. */
public class TableIndex {
    private final String columnName;
    private final int columnIndex;
    private final DiskBTree tree;

    private TableIndex(String columnName, int columnIndex, DiskBTree tree) {
        this.columnName = columnName;
        this.columnIndex = columnIndex;
        this.tree = tree;
    }

    /** Creates a fresh index file (replacing any old one) and fills it from every live row of the table. */
    public static TableIndex build(Table table, String columnName, String indexPath) throws IOException {
        int idx = table.schema().indexOf(columnName);
        if (idx < 0) {
            throw new IllegalArgumentException("no such column: " + columnName);
        }
        Column column = table.schema().getColumn(idx);
        Files.deleteIfExists(Path.of(indexPath));
        DiskBTree tree = new DiskBTree(indexPath, column);
        for (StoredRow stored : table.scan()) {
            tree.insert(stored.row().get(idx), stored.id());
        }
        return new TableIndex(columnName, idx, tree);
    }

    /** Opens an index file that already exists. */
    public static TableIndex open(Table table, String columnName, String indexPath) throws IOException {
        int idx = table.schema().indexOf(columnName);
        if (idx < 0) {
            throw new IllegalArgumentException("no such column: " + columnName);
        }
        Column column = table.schema().getColumn(idx);
        return new TableIndex(columnName, idx, new DiskBTree(indexPath, column));
    }

    /** RowIds of every row whose indexed column equals the value. */
    public List<RowId> lookup(Value value) throws IOException {
        return tree.search(value);
    }

    /** Call after a row is inserted into the table. */
    public void onInsert(Row row, RowId id) throws IOException {
        tree.insert(row.get(columnIndex), id);
    }

    /** Call after a row is deleted from the table. */
    public void onDelete(Row row, RowId id) throws IOException {
        tree.delete(row.get(columnIndex), id);
    }

    public String columnName() {
        return columnName;
    }

    public int size() {
        return tree.size();
    }

    public int height() throws IOException {
        return tree.height();
    }

    public int lastLookupPageReads() {
        return tree.lastSearchPageReads();
    }

    public void check() throws IOException {
        tree.check();
    }

    public void close() throws IOException {
        tree.close();
    }
}