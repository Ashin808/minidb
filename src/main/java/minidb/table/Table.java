package minidb.table;

import minidb.catalog.TableSchema;
import minidb.record.Row;
import minidb.record.RowSerializer;
import minidb.storage.Pager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** One table = one file. Rows live in fixed-size slots inside 4 KB pages. */
public class Table {

    /** Where a row lives: which page, which slot in that page. */
    public record RowId(int page, int slot) {}

    /** A row together with its location. */
    public record StoredRow(RowId id, Row row) {}

    private static final byte LIVE = 1;

    private final TableSchema schema;
    private final Pager pager;
    private final int rowSize;
    private final int rowsPerPage;

    public Table(TableSchema schema, String path) throws IOException {
        this.schema = schema;
        this.pager = new Pager(path);
        this.rowSize = schema.rowSize();
        this.rowsPerPage = schema.rowsPerPage(Pager.PAGE_SIZE);
    }

    public TableSchema schema() {
        return schema;
    }

    /** Stores the row in the first free slot (reusing deleted slots), adding a page if needed. */
    public RowId insert(Row row) throws IOException {
        byte[] bytes = RowSerializer.serialize(row);

        for (int p = 0; p < pager.pageCount(); p++) {
            byte[] page = pager.readPage(p);
            for (int s = 0; s < rowsPerPage; s++) {
                if (page[s * rowSize] != LIVE) {
                    return writeSlot(p, s, page, bytes);
                }
            }
        }

        pager.allocatePage();                       // all pages full: add a new, zero-filled one
        int p = pager.pageCount() - 1;
        return writeSlot(p, 0, pager.readPage(p), bytes);
    }

    /** Returns every live row with its RowId, in storage order. */
    public List<StoredRow> scan() throws IOException {
        List<StoredRow> result = new ArrayList<>();
        for (int p = 0; p < pager.pageCount(); p++) {
            byte[] page = pager.readPage(p);
            for (int s = 0; s < rowsPerPage; s++) {
                int offset = s * rowSize;
                if (page[offset] == LIVE) {
                    byte[] bytes = Arrays.copyOfRange(page, offset, offset + rowSize);
                    result.add(new StoredRow(new RowId(p, s), RowSerializer.deserialize(schema, bytes)));
                }
            }
        }
        return result;
    }

    /** Reads one row by its address (one page read). Returns null if the slot is empty or out of range. */
    public Row read(RowId id) throws IOException {
        if (id.page() < 0 || id.page() >= pager.pageCount() || id.slot() < 0 || id.slot() >= rowsPerPage) {
            return null;
        }
        byte[] page = pager.readPage(id.page());
        int offset = id.slot() * rowSize;
        if (page[offset] != LIVE) {
            return null;
        }
        return RowSerializer.deserialize(schema, Arrays.copyOfRange(page, offset, offset + rowSize));
    }

    /** Marks the slot as empty. Returns false if it was already empty. */
    public boolean delete(RowId id) throws IOException {
        byte[] page = pager.readPage(id.page());
        int offset = id.slot() * rowSize;
        if (page[offset] != LIVE) {
            return false;
        }
        page[offset] = 0;
        pager.writePage(id.page(), page);
        return true;
    }

    public int pageCount() throws IOException {
        return pager.pageCount();
    }

    public void close() throws IOException {
        pager.close();
    }

    private RowId writeSlot(int pageNo, int slot, byte[] page, byte[] rowBytes) throws IOException {
        System.arraycopy(rowBytes, 0, page, slot * rowSize, rowSize);
        pager.writePage(pageNo, page);
        return new RowId(pageNo, slot);
    }
}