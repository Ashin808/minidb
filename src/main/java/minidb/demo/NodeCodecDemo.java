package minidb.demo;

import minidb.catalog.Column;
import minidb.index.DiskNode;
import minidb.index.NodeCodec;
import minidb.record.Value;
import minidb.table.Table.RowId;

public class NodeCodecDemo {
    public static void main(String[] args) {
        NodeCodec ints = new NodeCodec(Column.integer("id"));
        NodeCodec names = new NodeCodec(Column.varchar("name", 50));

        System.out.println("INT key:         keySize=" + ints.keySize()
                + " leaf holds " + ints.maxLeafKeys() + ", internal holds " + ints.maxInternalKeys());
        System.out.println("VARCHAR(50) key: keySize=" + names.keySize()
                + " leaf holds " + names.maxLeafKeys() + ", internal holds " + names.maxInternalKeys());

        // 1. INT leaf round trip
        DiskNode leaf = new DiskNode(true);
        for (int i = 1; i <= 5; i++) {
            leaf.keys.add(Value.ofInt(i * 10));
            leaf.rowIds.add(new RowId(0, i - 1));
        }
        leaf.next = 7;
        byte[] page = ints.encode(leaf);
        System.out.println("page bytes=" + page.length);
        System.out.println("INT leaf round trip:      " + same(leaf, ints.decode(page)));

        // 2. INT internal round trip
        DiskNode inner = new DiskNode(false);
        inner.keys.add(Value.ofInt(30));
        inner.rowIds.add(new RowId(1, 2));
        inner.keys.add(Value.ofInt(60));
        inner.rowIds.add(new RowId(3, 4));
        inner.children.add(11);
        inner.children.add(12);
        inner.children.add(13);
        System.out.println("INT internal round trip:  " + same(inner, ints.decode(ints.encode(inner))));

        // 3. VARCHAR leaf, including a non-ASCII name (2 bytes in UTF-8)
        DiskNode nameLeaf = new DiskNode(true);
        String[] list = {"Alice", "Bob", "José"};
        for (int i = 0; i < list.length; i++) {
            nameLeaf.keys.add(Value.ofString(list[i]));
            nameLeaf.rowIds.add(new RowId(0, i));
        }
        System.out.println("VARCHAR leaf round trip:  " + same(nameLeaf, names.decode(names.encode(nameLeaf))));

        // 4. a full leaf fits, one more entry is refused
        DiskNode full = new DiskNode(true);
        for (int i = 0; i < ints.maxLeafKeys(); i++) {
            full.keys.add(Value.ofInt(i));
            full.rowIds.add(new RowId(0, i));
        }
        System.out.println("full leaf (" + full.keys.size() + " keys) round trip: " + same(full, ints.decode(ints.encode(full))));
        full.keys.add(Value.ofInt(9999));
        full.rowIds.add(new RowId(9, 9));
        try {
            ints.encode(full);
            System.out.println("overfull leaf: NOT refused (bug)");
        } catch (IllegalArgumentException e) {
            System.out.println("overfull leaf refused: " + e.getMessage());
        }

        // 5. an all-zero page is not a node
        try {
            ints.decode(new byte[4096]);
            System.out.println("zero page: NOT refused (bug)");
        } catch (IllegalArgumentException e) {
            System.out.println("zero page refused: " + e.getMessage());
        }
    }

    private static boolean same(DiskNode a, DiskNode b) {
        if (a.leaf != b.leaf || a.next != b.next || a.keys.size() != b.keys.size()) {
            return false;
        }
        for (int i = 0; i < a.keys.size(); i++) {
            if (a.keys.get(i).compareTo(b.keys.get(i)) != 0) {
                return false;
            }
        }
        return a.rowIds.equals(b.rowIds) && a.children.equals(b.children);
    }
}