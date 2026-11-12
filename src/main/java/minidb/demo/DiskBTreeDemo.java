package minidb.demo;

import minidb.catalog.Column;
import minidb.index.DiskBTree;
import minidb.index.DiskNode;
import minidb.record.Value;
import minidb.table.Table.RowId;

import java.nio.file.Files;
import java.nio.file.Path;

public class DiskBTreeDemo {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("demo_data"));
        String path = "demo_data/index_demo.db";
        Files.deleteIfExists(Path.of(path));
        Column id = Column.integer("id");

        // 1. a new file: meta page + one empty leaf
        DiskBTree tree = new DiskBTree(path, id);
        System.out.println("new file: pages=" + tree.pageCount() + " root=" + tree.rootPage()
                + " height=" + tree.height() + " size=" + tree.size());
        System.out.println("search 10 on empty tree -> " + tree.search(Value.ofInt(10)));

        // 2. build this tree by hand (insert comes in 5c-3). Key 50 appears twice, split across two leaves.
        //
        //          [ 40(0,3) | 50(1,0) ]
        //         /          |          \
        //   (10,20,30) -> (40,50) -> (50,60,70)
        int a = tree.rootPage();
        int b = tree.newPage();
        int c = tree.newPage();
        int r = tree.newPage();

        DiskNode leafA = leaf(c(10, 0, 0), c(20, 0, 1), c(30, 0, 2));
        leafA.next = b;
        DiskNode leafB = leaf(c(40, 0, 3), c(50, 0, 4));
        leafB.next = c;
        DiskNode leafC = leaf(c(50, 1, 0), c(60, 0, 5), c(70, 0, 6));

        DiskNode root = new DiskNode(false);
        root.keys.add(Value.ofInt(40));
        root.rowIds.add(new RowId(0, 3));
        root.keys.add(Value.ofInt(50));
        root.rowIds.add(new RowId(1, 0));
        root.children.add(a);
        root.children.add(b);
        root.children.add(c);

        tree.writeNode(a, leafA);
        tree.writeNode(b, leafB);
        tree.writeNode(c, leafC);
        tree.writeNode(r, root);
        tree.setRoot(r);
        tree.setEntryCount(8);

        System.out.println("hand-built: pages=" + tree.pageCount() + " root=" + tree.rootPage()
                + " height=" + tree.height() + " size=" + tree.size());

        // 3. search, with the number of pages each search touched
        show(tree, 10);
        show(tree, 50);   // duplicate that spans two leaves
        show(tree, 55);   // not present
        show(tree, 70);

        // 4. close and reopen: the data must still be there
        tree.close();
        DiskBTree again = new DiskBTree(path, id);
        System.out.println("reopened: root=" + again.rootPage() + " height=" + again.height() + " size=" + again.size());
        show(again, 50);
        again.close();
    }

    /** Builds a leaf entry list item: key with RowId(page, slot). Returns a one-entry node to merge. */
    private static Object[] c(int key, int page, int slot) {
        return new Object[]{key, page, slot};
    }

    private static DiskNode leaf(Object[]... entries) {
        DiskNode node = new DiskNode(true);
        for (Object[] e : entries) {
            node.keys.add(Value.ofInt((Integer) e[0]));
            node.rowIds.add(new RowId((Integer) e[1], (Integer) e[2]));
        }
        return node;
    }

    private static void show(DiskBTree tree, int key) throws Exception {
        System.out.println("search " + key + " -> " + tree.search(Value.ofInt(key))
                + "  (pages read: " + tree.lastSearchPageReads() + ")");
    }
}