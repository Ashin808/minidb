package minidb.demo;

import minidb.catalog.Column;
import minidb.record.Row;
import minidb.record.Value;
import minidb.sql.SqlException;
import minidb.table.Database;
import minidb.table.Table;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DatabaseDemo {
    private interface Action {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        deleteRecursively(Path.of("demo_data"));    // start fresh each run

        Database db = new Database("demo_data");
        Table users = db.createTable("Users", List.of(
                Column.integer("id"), Column.varchar("name", 50)));
        db.createTable("orders", List.of(
                Column.integer("id"), Column.integer("user_id")));
        System.out.println("Tables: " + db.tableNames());

        users.insert(new Row(users.schema(), List.of(Value.ofInt(1), Value.ofString("Alice"))));
        users.insert(new Row(users.schema(), List.of(Value.ofInt(2), Value.ofString("Bob"))));

        System.out.println("Lookup USERS -> " + db.getTable("USERS").schema().name());
        System.out.println("Has orders: " + db.hasTable("ORDERS"));
        System.out.println("Has items: " + db.hasTable("items"));

        expectError(() -> db.createTable("USERS", List.of(Column.integer("id"))));
        expectError(() -> db.getTable("missing"));
        expectError(() -> db.createTable("dup", List.of(Column.integer("a"), Column.integer("A"))));
        expectError(() -> {
            List<Column> wide = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                wide.add(Column.varchar("c" + i, 255));
            }
            db.createTable("wide", wide);
        });

        db.close();

        Database reopened = new Database("demo_data");
        System.out.println("After reopen: " + reopened.tableNames());
        Table again = reopened.getTable("users");
        System.out.println("users columns: " + again.schema().columnCount());
        System.out.println("users rows: " + again.scan().size());
        reopened.close();
    }

    private static void expectError(Action action) {
        try {
            action.run();
            System.out.println("No error (unexpected)");
        } catch (SqlException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Unexpected: " + e);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}