package minidb.demo;

import minidb.exec.Executor;
import minidb.exec.Result;
import minidb.table.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class PlanDemo {
    public static void main(String[] args) throws Exception {
        String dir = "demo_data/plan_db";
        wipe(Path.of(dir));
        Database db = new Database(dir);
        Executor ex = new Executor(db);

        ex.execute("CREATE TABLE users (id INT, name VARCHAR(50), age INT)");
        for (int i = 1; i <= 5000; i++) {
            ex.execute("INSERT INTO users VALUES (" + i + ", 'user" + i + "', " + (18 + i % 50) + ")");
        }

        String query = "SELECT * FROM users WHERE id = 4000";
        System.out.println("WITHOUT index");
        show(ex, query);

        ex.execute("CREATE INDEX ON users (id)");
        System.out.println("WITH index");
        show(ex, query);

        System.out.println("non-SELECT statement: plan='" + ex.execute("DELETE FROM users WHERE id = 1").plan()
                + "' pagesRead=" + 0);
        db.close();
    }

    /** Runs the query 20 times so JVM warm-up doesn't hide the difference; prints the last run. */
    private static void show(Executor ex, String query) throws IOException {
        Result r = null;
        for (int i = 0; i < 20; i++) {
            r = ex.execute(query);
        }
        System.out.println("  " + query);
        System.out.println("  plan:       " + r.plan());
        System.out.println("  pages read: " + r.pagesRead());
        System.out.println("  rows:       " + r.rows().size());
        System.out.printf("  time:       %.3f ms%n", r.elapsedMillis());
    }

    private static void wipe(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                Files.delete(p);
            }
        }
    }
}