package minidb.index;

import minidb.catalog.Column;
import minidb.record.Value;
import minidb.storage.Pager;
import minidb.table.Table.RowId;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** A B+ tree stored in a file of 4 KB pages. Page 0 = meta, every other page = one node. */
public class DiskBTree {
    private static final int MAGIC = 0x4D425449;              // "MBTI"
    private static final RowId LOWEST = new RowId(Integer.MIN_VALUE, Integer.MIN_VALUE);

    private final Pager pager;
    private final NodeCodec codec;
    private int rootPage;
    private int entryCount;
    private int pageReads;
    private int lastSearchPageReads;

    public DiskBTree(String path, Column keyColumn) throws IOException {
        this.pager = new Pager(path);
        this.codec = new NodeCodec(keyColumn);
        if (pager.pageCount() == 0) {                         // brand-new file
            pager.allocatePage();                             // page 0: meta
            int root = pager.allocatePage();                  // page 1: an empty leaf is the first root
            writeNode(root, new DiskNode(true));
            this.rootPage = root;
            this.entryCount = 0;
            writeMeta();
        } else {
            readMeta();
        }
    }

    // ---------- meta page ----------

    private void writeMeta() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(Pager.PAGE_SIZE);
        buf.putInt(MAGIC);
        buf.putInt(rootPage);
        buf.putInt(entryCount);
        pager.writePage(0, buf.array());
    }

    private void readMeta() throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(pager.readPage(0));
        if (buf.getInt() != MAGIC) {
            throw new IOException("not a MiniDB index file");
        }
        this.rootPage = buf.getInt();
        this.entryCount = buf.getInt();
    }

    // ---------- node access (5c-3 and 5c-4 build on these) ----------

    public DiskNode readNode(int page) throws IOException {
        pageReads++;
        return codec.decode(pager.readPage(page));
    }

    public void writeNode(int page, DiskNode node) throws IOException {
        pager.writePage(page, codec.encode(node));
    }

    /** Reserves a new page and returns its number. */
    public int newPage() throws IOException {
        return pager.allocatePage();
    }

    public int rootPage() {
        return rootPage;
    }

    public void setRoot(int page) throws IOException {
        this.rootPage = page;
        writeMeta();
    }

    public void setEntryCount(int count) throws IOException {
        this.entryCount = count;
        writeMeta();
    }

    // ---------- search ----------

    /** All RowIds stored under this key, in RowId order (empty list if none). */
    public List<RowId> search(Value key) throws IOException {
        int readsBefore = pageReads;

        DiskNode node = readNode(rootPage);
        while (!node.leaf) {
            node = readNode(node.children.get(childIndex(node, key, LOWEST)));
        }

        int i = 0;                                            // first entry >= (key, lowest possible RowId)
        while (i < node.keys.size() && compare(key, LOWEST, node.keys.get(i), node.rowIds.get(i)) > 0) {
            i++;
        }

        List<RowId> result = new ArrayList<>();
        while (true) {
            while (i < node.keys.size() && node.keys.get(i).compareTo(key) == 0) {
                result.add(node.rowIds.get(i));
                i++;
            }
            if (i < node.keys.size() || node.next == DiskNode.NO_PAGE) {
                break;                                        // hit a different key, or the last leaf
            }
            node = readNode(node.next);                       // matches may continue in the next leaf
            i = 0;
        }

        lastSearchPageReads = pageReads - readsBefore;
        return result;
    }

    // ---------- inspection ----------

    /** Number of levels, counting the leaves (a lone leaf = 1). */
    public int height() throws IOException {
        int h = 1;
        DiskNode node = readNode(rootPage);
        while (!node.leaf) {
            node = readNode(node.children.get(0));
            h++;
        }
        return h;
    }

    public int size() {
        return entryCount;
    }

    public int pageCount() throws IOException {
        return pager.pageCount();
    }

    public int lastSearchPageReads() {
        return lastSearchPageReads;
    }

    public void close() throws IOException {
        pager.close();
    }

    // ---------- helpers ----------

    /** Orders entries by key first, then by RowId (page, then slot). */
    static int compare(Value k1, RowId r1, Value k2, RowId r2) {
        int c = k1.compareTo(k2);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(r1.page(), r2.page());
        if (c != 0) {
            return c;
        }
        return Integer.compare(r1.slot(), r2.slot());
    }

    /** Which child to descend into: an entry equal to a separator goes to its RIGHT. */
    static int childIndex(DiskNode node, Value key, RowId id) {
        int i = 0;
        while (i < node.keys.size() && compare(key, id, node.keys.get(i), node.rowIds.get(i)) >= 0) {
            i++;
        }
        return i;
    }
}