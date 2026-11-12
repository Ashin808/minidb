package minidb.demo;

import minidb.index.BTree;
import minidb.record.Value;
import minidb.table.Table.RowId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BTreeDemo {
    public static void main(String[] args) {
        // 1. small tree, so every split is easy to follow
        BTree tree = new BTree(3);
        for (int i = 1; i <= 10; i++) {
            tree.insert(Value.ofInt(i * 10), new RowId(0, i - 1));
        }
        System.out.println(tree.dump());
        System.out.println("height=" + tree.height() + " entries=" + tree.size());
        System.out.println("search 70 -> " + tree.search(Value.ofInt(70)));
        System.out.println("search 55 -> " + tree.search(Value.ofInt(55)));

        // 2. duplicate key: same tree shape, two RowIds
        tree.insert(Value.ofInt(70), new RowId(1, 0));
        System.out.println("search 70 -> " + tree.search(Value.ofInt(70)));
        System.out.println("height=" + tree.height() + " entries=" + tree.size());
        List<String> keyText = new ArrayList<>();
        for (Value k : tree.keys()) {
            keyText.add(String.valueOf(k.asInt()));
        }
        System.out.println("keys=" + keyText);

        // 3. string keys
        BTree names = new BTree(3);
        String[] list = {"Carol", "Alice", "Bob", "Dave"};
        for (int i = 0; i < list.length; i++) {
            names.insert(Value.ofString(list[i]), new RowId(0, i));
        }
        System.out.println(names.dump());
        System.out.println("search Bob -> " + names.search(Value.ofString("Bob")));

        // 4. stress test: 200 keys in random order
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            numbers.add(i);
        }
        Collections.shuffle(numbers, new Random(42));
        BTree big = new BTree(4);
        for (int n : numbers) {
            big.insert(Value.ofInt(n), new RowId(n / 100, n % 100));
        }

        boolean allFound = true;
        for (int n = 1; n <= 200; n++) {
            List<RowId> found = big.search(Value.ofInt(n));
            if (found.size() != 1 || !found.get(0).equals(new RowId(n / 100, n % 100))) {
                allFound = false;
            }
        }
        List<Value> keys = big.keys();
        boolean sorted = keys.size() == 200;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).asInt() != i + 1) {
                sorted = false;
            }
        }
        System.out.println("200 shuffled keys: allFound=" + allFound + " sorted=" + sorted);

        deleteDemo();
    }

    private static void deleteDemo() {
        System.out.println("\n--- 5b: delete ---");

        // 1. walk through deletes on a small tree
        BTree t = new BTree(3);
        for (int i = 1; i <= 10; i++) {
            t.insert(Value.ofInt(i * 10), new RowId(0, i - 1));
        }
        System.out.println(t.dump());
        int[] order = {70, 10, 20, 30};
        for (int k : order) {
            boolean ok = t.delete(Value.ofInt(k), new RowId(0, k / 10 - 1));
            t.check();                                   // throws if any tree rule is broken
            System.out.println("delete " + k + " -> " + ok
                    + " height=" + t.height() + " entries=" + t.size());
            System.out.println(t.dump());
        }
        System.out.println("delete 40 with wrong RowId -> " + t.delete(Value.ofInt(40), new RowId(9, 9)));
        System.out.println("delete 999 (missing)     -> " + t.delete(Value.ofInt(999), new RowId(0, 0)));

        // 2. duplicate keys: removing one RowId keeps the key, removing the last removes the key
        BTree d = new BTree(3);
        d.insert(Value.ofInt(70), new RowId(0, 6));
        d.insert(Value.ofInt(70), new RowId(1, 0));
        d.delete(Value.ofInt(70), new RowId(0, 6));
        System.out.println("after deleting one of two: " + d.search(Value.ofInt(70)));
        d.delete(Value.ofInt(70), new RowId(1, 0));
        System.out.println("after deleting both: " + d.search(Value.ofInt(70))
                + " entries=" + d.size());

        // 3. stress: many shapes, random insert and delete orders, check() after EVERY delete
        int runs = 0;
        for (int maxKeys = 3; maxKeys <= 7; maxKeys++) {
            for (int seed = 1; seed <= 20; seed++) {
                List<Integer> nums = new ArrayList<>();
                for (int i = 1; i <= 200; i++) {
                    nums.add(i);
                }
                Collections.shuffle(nums, new Random(seed));
                BTree big = new BTree(maxKeys);
                for (int n : nums) {
                    big.insert(Value.ofInt(n), new RowId(n / 100, n % 100));
                }
                big.check();

                Collections.shuffle(nums, new Random(seed + 100));
                for (int n : nums) {
                    if (!big.delete(Value.ofInt(n), new RowId(n / 100, n % 100))) {
                        throw new IllegalStateException("delete failed for " + n);
                    }
                    big.check();
                    if (!big.search(Value.ofInt(n)).isEmpty()) {
                        throw new IllegalStateException(n + " still found after delete");
                    }
                }
                if (big.size() != 0 || big.height() != 1 || !big.keys().isEmpty()) {
                    throw new IllegalStateException("tree not empty at the end");
                }
                runs++;
            }
        }
        System.out.println("stress: " + runs + " runs (maxKeys 3..7), all passed");
    }
}