package minidb.index;

import minidb.catalog.DataType;
import minidb.record.Value;
import minidb.table.Table.RowId;

import java.util.ArrayList;
import java.util.List;

/**
 * An in-memory B+ tree: key -> list of RowIds (so duplicate keys are allowed).
 * Entries live in the leaves; internal nodes only hold separator keys.
 */
public class BTree {

    private abstract static class Node {
        final List<Value> keys = new ArrayList<>();
    }

    private static class Leaf extends Node {
        final List<List<RowId>> rowIds = new ArrayList<>();   // rowIds.get(i) belongs to keys.get(i)
        Leaf next;                                            // leaves form a sorted chain
    }

    private static class Internal extends Node {
        final List<Node> children = new ArrayList<>();        // always keys.size() + 1 children
    }

    /** Produced when a node overflows: the key to push up and the new right-hand node. */
    private record Split(Value key, Node right) {}

    private final int maxKeys;
    private Node root = new Leaf();
    private int entryCount = 0;

    /** maxKeys = how many keys a node may hold before it splits. */
    public BTree(int maxKeys) {
        if (maxKeys < 3) {
            throw new IllegalArgumentException("maxKeys must be at least 3");
        }
        this.maxKeys = maxKeys;
    }

    // ---------- insert ----------

    public void insert(Value key, RowId id) {
        Split split = insert(root, key, id);
        if (split != null) {                                  // the root itself split: tree grows one level
            Internal newRoot = new Internal();
            newRoot.keys.add(split.key());
            newRoot.children.add(root);
            newRoot.children.add(split.right());
            root = newRoot;
        }
        entryCount++;
    }

    private Split insert(Node node, Value key, RowId id) {
        if (node instanceof Leaf leaf) {
            int i = lowerBound(leaf.keys, key);
            if (i < leaf.keys.size() && leaf.keys.get(i).compareTo(key) == 0) {
                leaf.rowIds.get(i).add(id);                   // key already there: just add another RowId
                return null;
            }
            leaf.keys.add(i, key);
            List<RowId> ids = new ArrayList<>();
            ids.add(id);
            leaf.rowIds.add(i, ids);
            return leaf.keys.size() > maxKeys ? splitLeaf(leaf) : null;
        }

        Internal internal = (Internal) node;
        int c = childIndex(internal, key);
        Split childSplit = insert(internal.children.get(c), key, id);
        if (childSplit == null) {
            return null;
        }
        internal.keys.add(c, childSplit.key());               // adopt the new separator and child
        internal.children.add(c + 1, childSplit.right());
        return internal.keys.size() > maxKeys ? splitInternal(internal) : null;
    }

    /** The right half moves to a new leaf. The first key of the new leaf is COPIED up. */
    private Split splitLeaf(Leaf leaf) {
        int mid = leaf.keys.size() / 2;
        Leaf right = new Leaf();
        right.keys.addAll(leaf.keys.subList(mid, leaf.keys.size()));
        right.rowIds.addAll(leaf.rowIds.subList(mid, leaf.rowIds.size()));
        leaf.keys.subList(mid, leaf.keys.size()).clear();
        leaf.rowIds.subList(mid, leaf.rowIds.size()).clear();
        right.next = leaf.next;                               // keep the leaf chain intact
        leaf.next = right;
        return new Split(right.keys.get(0), right);
    }

    /** The middle key is MOVED up (it no longer stays in either half). */
    private Split splitInternal(Internal node) {
        int mid = node.keys.size() / 2;
        Value up = node.keys.get(mid);
        Internal right = new Internal();
        right.keys.addAll(node.keys.subList(mid + 1, node.keys.size()));
        right.children.addAll(node.children.subList(mid + 1, node.children.size()));
        node.keys.subList(mid, node.keys.size()).clear();
        node.children.subList(mid + 1, node.children.size()).clear();
        return new Split(up, right);
    }

    // ---------- search ----------

    /** All RowIds stored under this key (empty list if the key isn't in the tree). */
    public List<RowId> search(Value key) {
        Node node = root;
        while (node instanceof Internal internal) {
            node = internal.children.get(childIndex(internal, key));
        }
        Leaf leaf = (Leaf) node;
        int i = lowerBound(leaf.keys, key);
        if (i < leaf.keys.size() && leaf.keys.get(i).compareTo(key) == 0) {
            return List.copyOf(leaf.rowIds.get(i));
        }
        return List.of();
    }

    // ---------- inspection ----------

    /** Number of levels, counting the leaves (a lone leaf = 1). */
    public int height() {
        int h = 1;
        Node node = root;
        while (node instanceof Internal internal) {
            node = internal.children.get(0);
            h++;
        }
        return h;
    }

    /** Number of (key, RowId) entries inserted. */
    public int size() {
        return entryCount;
    }

    /** Every distinct key in sorted order, read by walking the leaf chain. */
    public List<Value> keys() {
        Node node = root;
        while (node instanceof Internal internal) {
            node = internal.children.get(0);
        }
        List<Value> result = new ArrayList<>();
        for (Leaf leaf = (Leaf) node; leaf != null; leaf = leaf.next) {
            result.addAll(leaf.keys);
        }
        return result;
    }

    /** Text picture of the tree: [..] = internal node, (..) = leaf. */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        dump(root, 0, sb);
        return sb.toString().stripTrailing();
    }

    private void dump(Node node, int depth, StringBuilder sb) {
        sb.append("  ".repeat(depth));
        List<String> names = new ArrayList<>();
        for (Value key : node.keys) {
            names.add(show(key));
        }
        String body = String.join(", ", names);
        if (node instanceof Internal internal) {
            sb.append('[').append(body).append("]\n");
            for (Node child : internal.children) {
                dump(child, depth + 1, sb);
            }
        } else {
            sb.append('(').append(body).append(")\n");
        }
    }

    // ---------- helpers ----------

    /** First position whose key is >= the given key. */
    private static int lowerBound(List<Value> keys, Value key) {
        int i = 0;
        while (i < keys.size() && keys.get(i).compareTo(key) < 0) {
            i++;
        }
        return i;
    }

    /** Which child to descend into: keys equal to a separator go to its RIGHT. */
    private static int childIndex(Internal node, Value key) {
        int i = 0;
        while (i < node.keys.size() && key.compareTo(node.keys.get(i)) >= 0) {
            i++;
        }
        return i;
    }

    private static String show(Value v) {
        return v.type() == DataType.INT ? String.valueOf(v.asInt()) : v.asString();
    }
}