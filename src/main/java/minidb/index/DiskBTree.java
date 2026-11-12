package minidb.index;

import minidb.catalog.Column;
import minidb.catalog.DataType;
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

    /** Produced when a node overflows: the entry to push up and the page of the new right-hand node. */
    private record Split(Value key, RowId rowId, int rightPage) {}

    private final Pager pager;
    private final NodeCodec codec;
    private final int leafCap;                                // most entries a leaf may hold before it splits
    private final int internalCap;                            // most separators an internal node may hold
    private int rootPage;
    private int entryCount;
    private int pageReads;
    private int lastSearchPageReads;
    private boolean duplicateFound;

    public DiskBTree(String path, Column keyColumn) throws IOException {
        this(path, keyColumn, Integer.MAX_VALUE);
    }

    /** maxKeysLimit caps the node size (for demos with visible splits). Not stored in the file. */
    public DiskBTree(String path, Column keyColumn, int maxKeysLimit) throws IOException {
        if (maxKeysLimit < 3) {
            throw new IllegalArgumentException("maxKeysLimit must be at least 3");
        }
        this.pager = new Pager(path);
        this.codec = new NodeCodec(keyColumn);
        this.leafCap = Math.min(maxKeysLimit, codec.maxLeafKeys());
        this.internalCap = Math.min(maxKeysLimit, codec.maxInternalKeys());
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

    // ---------- node access ----------

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

    // ---------- insert ----------

    /** Adds one (key, RowId) entry. Returns false if that exact entry is already in the tree. */
    public boolean insert(Value key, RowId id) throws IOException {
        duplicateFound = false;
        Split split = insert(rootPage, key, id);
        if (duplicateFound) {
            return false;
        }
        if (split != null) {                                  // the root itself split: tree grows one level
            DiskNode newRoot = new DiskNode(false);
            newRoot.keys.add(split.key());
            newRoot.rowIds.add(split.rowId());
            newRoot.children.add(rootPage);
            newRoot.children.add(split.rightPage());
            int page = newPage();
            writeNode(page, newRoot);
            rootPage = page;
        }
        entryCount++;
        writeMeta();
        return true;
    }

    private Split insert(int page, Value key, RowId id) throws IOException {
        DiskNode node = readNode(page);

        if (node.leaf) {
            int i = 0;
            while (i < node.keys.size() && compare(key, id, node.keys.get(i), node.rowIds.get(i)) > 0) {
                i++;
            }
            if (i < node.keys.size() && compare(key, id, node.keys.get(i), node.rowIds.get(i)) == 0) {
                duplicateFound = true;                        // this exact entry already exists
                return null;
            }
            node.keys.add(i, key);
            node.rowIds.add(i, id);
            if (node.keys.size() <= leafCap) {
                writeNode(page, node);
                return null;
            }
            return splitLeaf(page, node);
        }

        int c = childIndex(node, key, id);
        Split childSplit = insert(node.children.get(c), key, id);
        if (childSplit == null) {
            return null;
        }
        node.keys.add(c, childSplit.key());                   // adopt the new separator and child
        node.rowIds.add(c, childSplit.rowId());
        node.children.add(c + 1, childSplit.rightPage());
        if (node.keys.size() <= internalCap) {
            writeNode(page, node);
            return null;
        }
        return splitInternal(page, node);
    }

    /** The right half moves to a new page. The first entry of the new leaf is COPIED up. */
    private Split splitLeaf(int page, DiskNode left) throws IOException {
        int mid = left.keys.size() / 2;
        DiskNode right = new DiskNode(true);
        right.keys.addAll(left.keys.subList(mid, left.keys.size()));
        right.rowIds.addAll(left.rowIds.subList(mid, left.rowIds.size()));
        left.keys.subList(mid, left.keys.size()).clear();
        left.rowIds.subList(mid, left.rowIds.size()).clear();

        int rightPage = newPage();
        right.next = left.next;                               // keep the leaf chain intact
        left.next = rightPage;
        writeNode(rightPage, right);
        writeNode(page, left);
        return new Split(right.keys.get(0), right.rowIds.get(0), rightPage);
    }

    /** The middle entry is MOVED up (it no longer stays in either half). */
    private Split splitInternal(int page, DiskNode left) throws IOException {
        int mid = left.keys.size() / 2;
        Value upKey = left.keys.get(mid);
        RowId upRow = left.rowIds.get(mid);

        DiskNode right = new DiskNode(false);
        right.keys.addAll(left.keys.subList(mid + 1, left.keys.size()));
        right.rowIds.addAll(left.rowIds.subList(mid + 1, left.rowIds.size()));
        right.children.addAll(left.children.subList(mid + 1, left.children.size()));
        left.keys.subList(mid, left.keys.size()).clear();
        left.rowIds.subList(mid, left.rowIds.size()).clear();
        left.children.subList(mid + 1, left.children.size()).clear();

        int rightPage = newPage();
        writeNode(rightPage, right);
        writeNode(page, left);
        return new Split(upKey, upRow, rightPage);
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

    /** Every key in sorted order (a key appears once per RowId), read by walking the leaf chain. */
    public List<Value> keys() throws IOException {
        List<Value> result = new ArrayList<>();
        for (DiskNode leaf = leftmostLeaf(); ; leaf = readNode(leaf.next)) {
            result.addAll(leaf.keys);
            if (leaf.next == DiskNode.NO_PAGE) {
                break;
            }
        }
        return result;
    }

    /** Text picture of the tree: [..] = internal node, (..) = leaf. */
    public String dump() throws IOException {
        StringBuilder sb = new StringBuilder();
        dump(rootPage, 0, sb);
        return sb.toString().stripTrailing();
    }

    private void dump(int page, int depth, StringBuilder sb) throws IOException {
        DiskNode node = readNode(page);
        sb.append("  ".repeat(depth));
        List<String> names = new ArrayList<>();
        for (Value key : node.keys) {
            names.add(show(key));
        }
        String body = String.join(", ", names);
        if (node.leaf) {
            sb.append('(').append(body).append(")\n");
        } else {
            sb.append('[').append(body).append("]\n");
            for (int child : node.children) {
                dump(child, depth + 1, sb);
            }
        }
    }

    private DiskNode leftmostLeaf() throws IOException {
        DiskNode node = readNode(rootPage);
        while (!node.leaf) {
            node = readNode(node.children.get(0));
        }
        return node;
    }

    // ---------- self-check (used by demos) ----------

    /** Verifies every rule of the tree; throws IllegalStateException describing the first one broken. */
    public void check() throws IOException {
        int[] leafDepth = {0};
        int total = checkNode(rootPage, null, null, null, null, 1, leafDepth);
        if (total != entryCount) {
            throw fail("tree holds " + total + " entries but size() says " + entryCount);
        }

        int chained = 0;
        Value prevKey = null;
        RowId prevRow = null;
        for (DiskNode leaf = leftmostLeaf(); ; leaf = readNode(leaf.next)) {
            for (int i = 0; i < leaf.keys.size(); i++) {
                if (prevKey != null && compare(prevKey, prevRow, leaf.keys.get(i), leaf.rowIds.get(i)) >= 0) {
                    throw fail("leaf chain is not sorted");
                }
                prevKey = leaf.keys.get(i);
                prevRow = leaf.rowIds.get(i);
                chained++;
            }
            if (leaf.next == DiskNode.NO_PAGE) {
                break;
            }
        }
        if (chained != entryCount) {
            throw fail("leaf chain has " + chained + " entries but size() says " + entryCount);
        }
    }

    /** Checks one node; low is inclusive, high is exclusive (null = no limit). Returns entries below it. */
    private int checkNode(int page, Value lowK, RowId lowR, Value highK, RowId highR,
                          int depth, int[] leafDepth) throws IOException {
        DiskNode node = readNode(page);
        boolean isRoot = page == rootPage;
        int n = node.keys.size();
        int cap = node.leaf ? leafCap : internalCap;
        if (n > cap) throw fail("node has too many keys");
        if (!isRoot && n < cap / 2) throw fail("node has too few keys");
        for (int i = 0; i < n; i++) {
            Value k = node.keys.get(i);
            RowId r = node.rowIds.get(i);
            if (i > 0 && compare(node.keys.get(i - 1), node.rowIds.get(i - 1), k, r) >= 0) {
                throw fail("entries not in order");
            }
            if (lowK != null && compare(k, r, lowK, lowR) < 0) throw fail("entry below its range");
            if (highK != null && compare(k, r, highK, highR) >= 0) throw fail("entry above its range");
        }

        if (node.leaf) {
            if (leafDepth[0] == 0) {
                leafDepth[0] = depth;
            } else if (leafDepth[0] != depth) {
                throw fail("leaves at different depths");
            }
            return n;
        }

        if (isRoot && n < 1) throw fail("internal root without keys");
        if (node.children.size() != n + 1) throw fail("wrong number of children");
        int total = 0;
        for (int i = 0; i <= n; i++) {
            Value loK = i == 0 ? lowK : node.keys.get(i - 1);
            RowId loR = i == 0 ? lowR : node.rowIds.get(i - 1);
            Value hiK = i == n ? highK : node.keys.get(i);
            RowId hiR = i == n ? highR : node.rowIds.get(i);
            total += checkNode(node.children.get(i), loK, loR, hiK, hiR, depth + 1, leafDepth);
        }
        return total;
    }

    private static IllegalStateException fail(String message) {
        return new IllegalStateException("Disk B-tree broken: " + message);
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

    private static String show(Value v) {
        return v.type() == DataType.INT ? String.valueOf(v.asInt()) : v.asString();
    }
}