package minidb.demo;

import minidb.catalog.Column;
import minidb.index.DiskBTree;
import minidb.record.Value;
import minidb.table.Table.RowId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DiskInsertDemo {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("demo_data"));
        Column id = Column.integer("id");

        // 1. small nodes (max 3 keys) so every split is visible
        String path = "demo_data/insert_demo.db";
        Files.deleteIfExists(Path.of(path));
        DiskBTree tree = new DiskBTree(path, id, 3);
        for (int i = 1; i <= 10; i++) {
            tree.insert(Value.ofInt(i * 10), new RowId(0, i - 1));
        }
        tree.check();
        System.out.println(tree.dump());
        System.out.println("height=" + tree.height() + " entries=" + tree.size() + " pages=" + tree.pageCount());

        // 2. same key with another RowId is allowed; the exact same entry is refused
        System.out.println("insert 70 with new RowId  -> " + tree.insert(Value.ofInt(70), new RowId(1, 0)));
        System.out.println("insert 70 with same RowId -> " + tree.insert(Value.ofInt(70), new RowId(0, 6)));
        System.out.println("search 70 -> " + tree.search(Value.ofInt(70)) + " entries=" + tree.size());
        tree.check();
        tree.close();

        // 3. close and reopen: the tree must still be there
        DiskBTree again = new DiskBTree(path, id, 3);
        again.check();
        System.out.println("reopened: height=" + again.height() + " entries=" + again.size()
                + " search 70 -> " + again.search(Value.ofInt(70)));
        again.close();

        // 4. stress: 200 entries in random order, 40 distinct keys with 5 RowIds each (duplicates span leaves),
        //    check() after EVERY insert, then verify every search, then reopen and check again
        String stress = "demo_data/insert_stress.db";
        int runs = 0;
        for (int maxKeys = 3; maxKeys <= 7; maxKeys++) {
            for (int seed = 1; seed <= 5; seed++) {
                Files.deleteIfExists(Path.of(stress));
                DiskBTree t = new DiskBTree(stress, id, maxKeys);
                List<Integer> nums = new ArrayList<>();
                for (int n = 1; n <= 200; n++) {
                    nums.add(n);
                }
                Collections.shuffle(nums, new Random(seed));
                for (int n : nums) {
                    if (!t.insert(Value.ofInt(n % 40), rid(n))) {
                        throw new IllegalStateException("insert refused for " + n);
                    }
                    t.check();
                }
                for (int k = 0; k < 40; k++) {
                    List<RowId> expected = new ArrayList<>();
                    for (int n = 1; n <= 200; n++) {
                        if (n % 40 == k) {
                            expected.add(rid(n));
                        }
                    }
                    if (!expected.equals(t.search(Value.ofInt(k)))) {
                        throw new IllegalStateException("wrong search result for key " + k);
                    }
                }
                List<Value> keys = t.keys();
                if (keys.size() != 200) {
                    throw new IllegalStateException("keys() has " + keys.size() + " entries");
                }
                for (int i = 1; i < keys.size(); i++) {
                    if (keys.get(i - 1).compareTo(keys.get(i)) > 0) {
                        throw new IllegalStateException("keys() not sorted");
                    }
                }
                t.close();

                DiskBTree reopened = new DiskBTree(stress, id, maxKeys);
                reopened.check();
                if (reopened.size() != 200) {
                    throw new IllegalStateException("size lost after reopen");
                }
                reopened.close();
                runs++;
            }
        }
        System.out.println("stress: " + runs + " runs (maxKeys 3..7), all passed");

        // 5. real node size (340 INT entries per leaf): 5000 shuffled keys
        String bigPath = "demo_data/insert_big.db";
        Files.deleteIfExists(Path.of(bigPath));
        DiskBTree big = new DiskBTree(bigPath, id);
        List<Integer> many = new ArrayList<>();
        for (int n = 1; n <= 5000; n++) {
            many.add(n);
        }
        Collections.shuffle(many, new Random(7));
        for (int n : many) {
            big.insert(Value.ofInt(n), rid(n));
        }
        big.check();
        System.out.println("5000 keys: height=" + big.height() + " pages=" + big.pageCount()
                + " entries=" + big.size());
        System.out.println("search 2500 -> " + big.search(Value.ofInt(2500))
                + " (pages read: " + big.lastSearchPageReads() + ")");
        big.close();
    }

    private static RowId rid(int n) {
        return new RowId(n / 100, n % 100);
    }
}