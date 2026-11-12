package minidb.demo;

import minidb.catalog.Column;
import minidb.catalog.TableSchema;
import minidb.record.Row;
import minidb.record.RowSerializer;
import minidb.record.Value;

import java.util.List;

public class SerializerDemo {
    public static void main(String[] args) {
        TableSchema users = new TableSchema("users", List.of(
                Column.integer("id"),
                Column.varchar("name", 50),
                Column.integer("age")));

        System.out.println("Row size: " + users.rowSize());
        System.out.println("Rows per page: " + users.rowsPerPage(4096));

        Row original = new Row(users, List.of(Value.ofInt(1), Value.ofString("John"), Value.ofInt(22)));
        byte[] bytes = RowSerializer.serialize(original);
        System.out.println("Serialized length: " + bytes.length);

        Row restored = RowSerializer.deserialize(users, bytes);
        System.out.println("Restored: " + restored);
        System.out.println("Equal? " + original.values().equals(restored.values()));

        bytes[0] = 0; // simulate delete
        System.out.println("After delete flag: " + RowSerializer.deserialize(users, bytes));
    }
}