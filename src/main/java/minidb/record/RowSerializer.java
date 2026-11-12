package minidb.record;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.catalog.TableSchema;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RowSerializer {
    private RowSerializer() {}

    /** Writes the row into a byte array of exactly schema.rowSize() bytes. */
    public static byte[] serialize(Row row) {
        TableSchema schema = row.schema();
        ByteBuffer buf = ByteBuffer.allocate(schema.rowSize()); // zero-filled, so padding is automatic
        buf.put((byte) 1); // live row
        for (int i = 0; i < schema.columnCount(); i++) {
            Column c = schema.getColumn(i);
            Value v = row.get(i);
            if (c.type() == DataType.INT) {
                buf.putInt(v.asInt());
            } else {
                byte[] text = v.asString().getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) text.length);
                buf.put(text);
                buf.position(buf.position() + (c.maxLength() - text.length)); // skip padding
            }
        }
        return buf.array();
    }

    /** Reads a row back. Returns null if the slot is empty or deleted. */
    public static Row deserialize(TableSchema schema, byte[] data) {
        if (data.length != schema.rowSize())
            throw new IllegalArgumentException("Expected " + schema.rowSize() + " bytes but got " + data.length);
        ByteBuffer buf = ByteBuffer.wrap(data);
        if (buf.get() == 0) return null;
        List<Value> values = new ArrayList<>();
        for (int i = 0; i < schema.columnCount(); i++) {
            Column c = schema.getColumn(i);
            if (c.type() == DataType.INT) {
                values.add(Value.ofInt(buf.getInt()));
            } else {
                int len = buf.getShort() & 0xFFFF;
                byte[] text = new byte[len];
                buf.get(text);
                buf.position(buf.position() + (c.maxLength() - len)); // skip padding
                values.add(Value.ofString(new String(text, StandardCharsets.UTF_8)));
            }
        }
        return new Row(schema, values);
    }
}