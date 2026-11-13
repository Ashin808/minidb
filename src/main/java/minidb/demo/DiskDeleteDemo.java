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

public class DiskDeleteDemo {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("demo_data"));
        Column id = Column.integer("id");

        // 1. walk through deletes on a small tree (max 3 keys per node)
        String path = "demo_data/delete_demo.db";
        Files.deleteIfExists(Path.of(path));
        DiskBTree t = new DiskBTree(path, id, 3);
        for (int i = 1; i <= 10; i++) {
            t.insert(Value.ofInt(i * 10), new RowId(0, i - 1));
        }
        System.out.println(t.dump());
        int[] order = {70, 10, 20, 30};
        for (int k : order) {
            boolean ok = t.delete(Value.ofInt(k), new RowId(0, k / 10 - 1));
            t.check();
            System.out.println("delete " + k + " -> " + ok + " height=" + t.height() + " entries=" + t.size());
            System.out.println(t.dump());
        }
        System.out.println("delete 40 with wrong RowId -> " + t.delete(Value.ofInt(40), new RowId(9, 9)));
        System.out.println("delete 999 (missing)     -> " + t.delete(Value.ofInt(999), new RowId(0, 0)));

        // 2. close and reopen after deletes
        t.close();
        DiskBTree again = new DiskBTree(path, id, 3);
        again.check();
        System.out.println("reopened: height=" + again.height() + " entries=" + again.size()
                + " keys=" + again.keys().size());
        again.close();

        // 3. stress: 200 entries (40 keys x 5 RowIds), delete in random order, check() after EVERY delete
        String stress = "demo_data/delete_stress.db";
        int runs = 0;
        for (int maxKeys = 3; maxKeys <= 7; maxKeys++) {
            for (int seed = 1; seed <= 5; seed++) {
                Files.deleteIfExists(Path.of(stress));
                DiskBTree s = new DiskBTree(stress, id, maxKeys);
                List<Integer> nums = new ArrayList<>();
                for (int n = 1; n <= 200; n++) {
                    nums.add(n);
                }
                Collections.shuffle(nums, new Random(seed));
                for (int n : nums) {
                    s.insert(Value.ofInt(n % 40), rid(n));
                }
                s.check();

                Collections.shuffle(nums, new Random(seed + 100));
                for (int i = 0; i < nums.size(); i++) {
                    int n = nums.get(i);
                    if (!s.delete(Value.ofInt(n % 40), rid(n))) {
                        throw new IllegalStateException("delete failed for " + n);
                    }
                    s.check();
                    if (s.search(Value.ofInt(n % 40)).contains(rid(n))) {
                        throw new IllegalStateException(n + " still found after delete");
                    }
                    if (i == 100) {                           // halfway: close and reopen, must stay valid
                        s.close();
                        s = new DiskBTree(stress, id, maxKeys);
                        s.check();
                    }
                }
                if (s.size() != 0 || s.height() != 1 || !s.keys().isEmpty()) {
                    throw new IllegalStateException("tree not empty at the end");
                }
                s.close();
                runs++;
            }
        }
        System.out.println("stress: " + runs + " runs (maxKeys 3..7), all passed");

        // 4. real node size: 5000 keys, delete 4000 of them
        String bigPath = "demo_data/delete_big.db";
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
        System.out.println("5000 keys:       height=" + big.height() + " entries=" + big.size());
        for (int i = 0; i < 4000; i++) {
            big.delete(Value.ofInt(many.get(i)), rid(many.get(i)));
        }
        big.check();
        System.out.println("after 4000 deletes: height=" + big.height() + " entries=" + big.size());
        int kept = many.get(4500);
        System.out.println("search " + kept + " -> " + big.search(Value.ofInt(kept)));
        big.close();
    }

    private static RowId rid(int n) {
        return new RowId(n / 100, n % 100);
    }
}