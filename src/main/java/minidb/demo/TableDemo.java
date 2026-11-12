package minidb.demo;

import minidb.catalog.Column;
import minidb.catalog.TableSchema;
import minidb.record.Row;
import minidb.record.Value;
import minidb.table.Table;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TableDemo {
    public static void main(String[] args) throws Exception {
        String path = "table_demo.db";
        Files.deleteIfExists(Path.of(path));        // start fresh each run

        TableSchema schema = new TableSchema("users", List.of(
                Column.integer("id"), Column.varchar("name", 50), Column.integer("age")));
        System.out.println("rowSize=" + schema.rowSize()
                + " rowsPerPage=" + schema.rowsPerPage(4096));

        Table table = new Table(schema, path);

        System.out.println("Inserted Alice at " + table.insert(user(schema, 1, "Alice", 30)));
        System.out.println("Inserted Bob at " + table.insert(user(schema, 2, "Bob", 25)));
        System.out.println("Inserted Carol at " + table.insert(user(schema, 3, "Carol", 41)));
        printScan(table);

        Table.RowId bob = new Table.RowId(0, 1);
        System.out.println("Deleted " + bob + ": " + table.delete(bob));
        System.out.println("Deleted again: " + table.delete(bob));
        printScan(table);

        System.out.println("Inserted Dave at " + table.insert(user(schema, 4, "Dave", 19)));

        for (int i = 100; i < 170; i++) {
            table.insert(user(schema, i, "User" + i, 20));
        }
        System.out.println("pageCount=" + table.pageCount());
        System.out.println("Total rows: " + table.scan().size());

        table.close();
        Table reopened = new Table(schema, path);
        System.out.println("After reopen: " + reopened.scan().size() + " rows");
        reopened.close();
    }

    private static Row user(TableSchema schema, int id, String name, int age) {
        return new Row(schema, List.of(Value.ofInt(id), Value.ofString(name), Value.ofInt(age)));
    }

    private static void printScan(Table table) throws Exception {
        List<Table.StoredRow> rows = table.scan();
        System.out.println("Scan: " + rows.size() + " rows");
        for (Table.StoredRow r : rows) {
            System.out.println("  " + r.id() + " -> " + r.row().get("id").asInt()
                    + " " + r.row().get("name").asString() + " " + r.row().get("age").asInt());
        }
    }
}