package minidb.index;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.record.Value;
import minidb.storage.Pager;
import minidb.table.Table.RowId;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Converts a DiskNode to the bytes of one page and back. One codec per indexed column. */
public class NodeCodec {
    public static final int HEADER = 8;
    private static final byte LEAF = 1;
    private static final byte INTERNAL = 2;

    private final DataType type;
    private final int maxLength;
    private final int keySize;

    public NodeCodec(Column keyColumn) {
        this.type = keyColumn.type();
        this.maxLength = keyColumn.maxLength();
        this.keySize = keyColumn.storageSize();
    }

    public int keySize() {
        return keySize;
    }

    /** Most entries a leaf page can hold. */
    public int maxLeafKeys() {
        return (Pager.PAGE_SIZE - HEADER) / (keySize + 8);
    }

    /** Most separator keys an internal page can hold (it then has one more child). */
    public int maxInternalKeys() {
        return (Pager.PAGE_SIZE - HEADER - 4) / (keySize + 12);
    }

    public byte[] encode(DiskNode node) {
        int capacity = node.leaf ? maxLeafKeys() : maxInternalKeys();
        if (node.keys.size() > capacity) {
            throw new IllegalArgumentException("node has " + node.keys.size() + " keys, one page holds " + capacity);
        }
        if (node.rowIds.size() != node.keys.size()) {
            throw new IllegalArgumentException("every key needs a RowId");
        }
        if (!node.leaf && node.children.size() != node.keys.size() + 1) {
            throw new IllegalArgumentException("internal node needs keys + 1 children");
        }

        ByteBuffer buf = ByteBuffer.allocate(Pager.PAGE_SIZE);
        buf.put(node.leaf ? LEAF : INTERNAL);
        buf.putShort((short) node.keys.size());
        buf.putInt(node.next);
        buf.position(HEADER);

        if (!node.leaf) {
            buf.putInt(node.children.get(0));
        }
        for (int i = 0; i < node.keys.size(); i++) {
            putKey(buf, node.keys.get(i));
            buf.putInt(node.rowIds.get(i).page());
            buf.putInt(node.rowIds.get(i).slot());
            if (!node.leaf) {
                buf.putInt(node.children.get(i + 1));
            }
        }
        return buf.array();
    }

    public DiskNode decode(byte[] page) {
        ByteBuffer buf = ByteBuffer.wrap(page);
        byte kind = buf.get();
        if (kind != LEAF && kind != INTERNAL) {
            throw new IllegalArgumentException("page is not a tree node (type byte " + kind + ")");
        }
        DiskNode node = new DiskNode(kind == LEAF);
        int n = buf.getShort() & 0xFFFF;
        node.next = buf.getInt();
        if (n > (node.leaf ? maxLeafKeys() : maxInternalKeys())) {
            throw new IllegalArgumentException("page claims " + n + " keys: it is corrupt");
        }
        buf.position(HEADER);

        if (!node.leaf) {
            node.children.add(buf.getInt());
        }
        for (int i = 0; i < n; i++) {
            node.keys.add(getKey(buf));
            int rowPage = buf.getInt();
            int rowSlot = buf.getInt();
            node.rowIds.add(new RowId(rowPage, rowSlot));
            if (!node.leaf) {
                node.children.add(buf.getInt());
            }
        }
        return node;
    }

    private void putKey(ByteBuffer buf, Value key) {
        int start = buf.position();
        if (type == DataType.INT) {
            buf.putInt(key.asInt());
        } else {
            byte[] bytes = key.asString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxLength) {
                throw new IllegalArgumentException("key is longer than " + maxLength + " bytes");
            }
            buf.putShort((short) bytes.length);
            buf.put(bytes);
        }
        buf.position(start + keySize);                        // skip the unused reserved bytes
    }

    private Value getKey(ByteBuffer buf) {
        int start = buf.position();
        Value key;
        if (type == DataType.INT) {
            key = Value.ofInt(buf.getInt());
        } else {
            int length = buf.getShort() & 0xFFFF;
            byte[] bytes = new byte[length];
            buf.get(bytes);
            key = Value.ofString(new String(bytes, StandardCharsets.UTF_8));
        }
        buf.position(start + keySize);
        return key;
    }
}