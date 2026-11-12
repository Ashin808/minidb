package minidb.demo;

import minidb.exec.Executor;
import minidb.exec.Result;
import minidb.record.Value;
import minidb.sql.SqlException;
import minidb.table.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ExecutorDemo {
    public static void main(String[] args) throws Exception {
        deleteRecursively(Path.of("demo_data"));        // start fresh each run

        Database db = new Database("demo_data");
        Executor ex = new Executor(db);

        String[] script = {
                "CREATE TABLE users (id INT, name VARCHAR(10), age INT)",
                "INSERT INTO users VALUES (1, 'Alice', 30)",
                "INSERT INTO users VALUES (2, 'Bob', 25)",
                "INSERT INTO users VALUES (3, 'Carol', 41)",
                "SELECT * FROM users",
                "SELECT name, age FROM users WHERE age >= 30",
                "SELECT NAME FROM users WHERE id = 2",
                "DELETE FROM users WHERE age < 30",
                "SELECT * FROM users",
                "DELETE FROM users WHERE id = 99",
                // errors
                "INSERT INTO users VALUES (1, 'x')",
                "INSERT INTO users VALUES (1, 5, 3)",
                "INSERT INTO users VALUES (1, 'ThisNameIsTooLong', 3)",
                "SELECT nope FROM users",
                "SELECT * FROM missing",
                "SELECT * FROM users WHERE age = 'x'",
                "CREATE INDEX ON users (id)",
                "SELEC * FROM users"
        };
        for (String sql : script) {
            run(ex, sql);
        }

        System.out.println("--- close and reopen ---");
        db.close();
        Database reopened = new Database("demo_data");
        Executor ex2 = new Executor(reopened);
        run(ex2, "SELECT * FROM users");

        Result timed = ex2.execute("SELECT * FROM users");
        System.out.println("Timer works: " + (timed.elapsedNanos() > 0));
        reopened.close();
    }

    private static void run(Executor ex, String sql) throws IOException {
        System.out.println("> " + sql);
        try {
            Result r = ex.execute(sql);
            System.out.println(r.message());
            if (r.isQuery()) {
                System.out.println("  " + String.join(" | ", r.columns()));
                for (List<Value> row : r.rows()) {
                    List<String> cells = new ArrayList<>();
                    for (Value v : row) {
                        cells.add(v.type() == minidb.catalog.DataType.INT
                                ? String.valueOf(v.asInt()) : v.asString());
                    }
                    System.out.println("  " + String.join(" | ", cells));
                }
            }
        } catch (SqlException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}