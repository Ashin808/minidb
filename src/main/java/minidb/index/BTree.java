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
    private final int minKeys;      // a non-root node must keep at least this many keys
    private Node root = new Leaf();
    private int entryCount = 0;

    /** maxKeys = how many keys a node may hold before it splits. */
    public BTree(int maxKeys) {
        if (maxKeys < 3) {
            throw new IllegalArgumentException("maxKeys must be at least 3");
        }
        this.maxKeys = maxKeys;
        this.minKeys = maxKeys / 2;
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

    // ---------- delete ----------

    /** Removes one (key, RowId) entry. Returns false if that exact entry isn't in the tree. */
    public boolean delete(Value key, RowId id) {
        boolean removed = delete(root, key, id);
        if (removed) {
            entryCount--;
        }
        if (root instanceof Internal r && r.keys.isEmpty()) {   // root has one child left: tree shrinks a level
            root = r.children.get(0);
        }
        return removed;
    }

    private boolean delete(Node node, Value key, RowId id) {
        if (node instanceof Leaf leaf) {
            int i = lowerBound(leaf.keys, key);
            if (i >= leaf.keys.size() || leaf.keys.get(i).compareTo(key) != 0) {
                return false;
            }
            List<RowId> ids = leaf.rowIds.get(i);
            if (!ids.remove(id)) {
                return false;
            }
            if (ids.isEmpty()) {                              // last RowId for this key: remove the key itself
                leaf.keys.remove(i);
                leaf.rowIds.remove(i);
            }
            return true;
        }

        Internal internal = (Internal) node;
        int c = childIndex(internal, key);
        Node child = internal.children.get(c);
        boolean removed = delete(child, key, id);
        if (removed && child.keys.size() < minKeys) {
            rebalance(internal, c);                           // child got too empty: fix it from the parent
        }
        return removed;
    }

    /** Child c of parent has too few keys: borrow from a sibling if one can spare a key, else merge. */
    private void rebalance(Internal parent, int c) {
        Node child = parent.children.get(c);
        Node left = c > 0 ? parent.children.get(c - 1) : null;
        Node right = c < parent.children.size() - 1 ? parent.children.get(c + 1) : null;

        if (left != null && left.keys.size() > minKeys) {
            borrowFromLeft(parent, c, left, child);
        } else if (right != null && right.keys.size() > minKeys) {
            borrowFromRight(parent, c, child, right);
        } else if (left != null) {
            merge(parent, c - 1, left, child);                // child folds into its left sibling
        } else {
            merge(parent, c, child, right);                   // right sibling folds into child
        }
    }

    private void borrowFromLeft(Internal parent, int c, Node left, Node child) {
        int last = left.keys.size() - 1;
        if (child instanceof Leaf leafChild) {
            Leaf leftLeaf = (Leaf) left;
            leafChild.keys.add(0, leftLeaf.keys.remove(last));
            leafChild.rowIds.add(0, leftLeaf.rowIds.remove(last));
            parent.keys.set(c - 1, leafChild.keys.get(0));    // new separator = first key of the right node
        } else {
            Internal innerChild = (Internal) child;
            Internal leftInner = (Internal) left;
            innerChild.keys.add(0, parent.keys.get(c - 1));   // separator rotates down...
            innerChild.children.add(0, leftInner.children.remove(leftInner.children.size() - 1));
            parent.keys.set(c - 1, leftInner.keys.remove(last));   // ...and the sibling's last key rotates up
        }
    }

    private void borrowFromRight(Internal parent, int c, Node child, Node right) {
        if (child instanceof Leaf leafChild) {
            Leaf rightLeaf = (Leaf) right;
            leafChild.keys.add(rightLeaf.keys.remove(0));
            leafChild.rowIds.add(rightLeaf.rowIds.remove(0));
            parent.keys.set(c, rightLeaf.keys.get(0));
        } else {
            Internal innerChild = (Internal) child;
            Internal rightInner = (Internal) right;
            innerChild.keys.add(parent.keys.get(c));
            innerChild.children.add(rightInner.children.remove(0));
            parent.keys.set(c, rightInner.keys.remove(0));
        }
    }

    /** Folds `right` into `left` and removes the separator between them from the parent. */
    private void merge(Internal parent, int sep, Node left, Node right) {
        if (left instanceof Leaf leftLeaf) {
            Leaf rightLeaf = (Leaf) right;
            leftLeaf.keys.addAll(rightLeaf.keys);
            leftLeaf.rowIds.addAll(rightLeaf.rowIds);
            leftLeaf.next = rightLeaf.next;                   // keep the leaf chain intact
        } else {
            Internal leftInner = (Internal) left;
            Internal rightInner = (Internal) right;
            leftInner.keys.add(parent.keys.get(sep));         // internal merge pulls the separator DOWN
            leftInner.keys.addAll(rightInner.keys);
            leftInner.children.addAll(rightInner.children);
        }
        parent.keys.remove(sep);
        parent.children.remove(sep + 1);
    }

    // ---------- self-check (used by tests) ----------

    /** Verifies every rule of the tree; throws IllegalStateException describing the first one broken. */
    public void check() {
        int[] leafDepth = {0};
        int total = checkNode(root, null, null, 1, leafDepth);
        if (total != entryCount) {
            throw fail("tree holds " + total + " entries but size() says " + entryCount);
        }

        Node node = root;
        while (node instanceof Internal internal) {
            node = internal.children.get(0);
        }
        int chained = 0;
        Value previous = null;
        for (Leaf leaf = (Leaf) node; leaf != null; leaf = leaf.next) {
            for (int i = 0; i < leaf.keys.size(); i++) {
                Value key = leaf.keys.get(i);
                if (previous != null && previous.compareTo(key) >= 0) {
                    throw fail("leaf chain is not sorted");
                }
                previous = key;
                chained += leaf.rowIds.get(i).size();
            }
        }
        if (chained != entryCount) {
            throw fail("leaf chain misses entries");
        }
    }

    /** Checks one node; low is inclusive, high is exclusive. Returns the number of entries below it. */
    private int checkNode(Node node, Value low, Value high, int depth, int[] leafDepth) {
        boolean isRoot = node == root;
        int n = node.keys.size();
        if (n > maxKeys) throw fail("node has too many keys");
        if (!isRoot && n < minKeys) throw fail("node has too few keys");
        for (int i = 0; i < n; i++) {
            Value key = node.keys.get(i);
            if (i > 0 && node.keys.get(i - 1).compareTo(key) >= 0) throw fail("keys not in order");
            if (low != null && key.compareTo(low) < 0) throw fail("key below its range");
            if (high != null && key.compareTo(high) >= 0) throw fail("key above its range");
        }

        if (node instanceof Leaf leaf) {
            if (leafDepth[0] == 0) {
                leafDepth[0] = depth;
            } else if (leafDepth[0] != depth) {
                throw fail("leaves at different depths");
            }
            int count = 0;
            for (List<RowId> ids : leaf.rowIds) {
                if (ids.isEmpty()) throw fail("key with no RowIds");
                count += ids.size();
            }
            return count;
        }

        Internal internal = (Internal) node;
        if (isRoot && n < 1) throw fail("internal root without keys");
        if (internal.children.size() != n + 1) throw fail("wrong number of children");
        int total = 0;
        for (int i = 0; i <= n; i++) {
            Value lo = i == 0 ? low : internal.keys.get(i - 1);
            Value hi = i == n ? high : internal.keys.get(i);
            total += checkNode(internal.children.get(i), lo, hi, depth + 1, leafDepth);
        }
        return total;
    }

    private static IllegalStateException fail(String message) {
        return new IllegalStateException("B-tree broken: " + message);
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