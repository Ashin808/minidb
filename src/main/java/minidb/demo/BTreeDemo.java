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
    }
}