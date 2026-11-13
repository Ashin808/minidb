package minidb.demo;

import minidb.exec.Executor;
import minidb.exec.Result;
import minidb.index.TableIndex;
import minidb.sql.SqlException;
import minidb.table.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class IndexSqlDemo {
    public static void main(String[] args) throws Exception {
        String dir = "demo_data/sql_index_db";
        wipe(Path.of(dir));

        Database db = new Database(dir);
        Executor ex = new Executor(db);

        ex.execute("CREATE TABLE users (id INT, name VARCHAR(50), age INT)");
        for (int i = 1; i <= 5000; i++) {
            ex.execute("INSERT INTO users VALUES (" + i + ", 'user" + i + "', " + (18 + i % 50) + ")");
        }
        System.out.println("5000 rows inserted");

        // 1. before the index: full scan
        String query = "SELECT * FROM users WHERE id = 4000";
        Result before = timed(ex, query);

        // 2. create the index, run the same query
        System.out.println(ex.execute("CREATE INDEX ON users (id)").message());
        Result after = timed(ex, query);
        System.out.println("same answer: " + (before.rows().size() == after.rows().size()
                && after.rows().get(0).get(0).asInt() == 4000));

        // 3. which queries use the index?
        timed(ex, "SELECT * FROM users WHERE id = 4000 AND age > 0");      // AND with an indexed equality: index
        timed(ex, "SELECT * FROM users WHERE id >= 4000 AND id <= 4000");  // no equality: full scan
        timed(ex, "SELECT * FROM users WHERE id = 4000 OR id = 10");       // OR: full scan
        timed(ex, "SELECT * FROM users WHERE age = 30");                   // no index on age: full scan

        // 4. the index follows INSERT and DELETE
        ex.execute("INSERT INTO users VALUES (9999, 'newuser', 20)");
        System.out.println("after insert, id=9999 -> " + timed(ex, "SELECT * FROM users WHERE id = 9999").rows().size() + " row");
        System.out.println(ex.execute("DELETE FROM users WHERE id = 4000").message());
        System.out.println("after delete, id=4000 -> " + timed(ex, "SELECT * FROM users WHERE id = 4000").rows().size() + " rows");
        TableIndex index = db.getIndex("users", "id");
        index.check();
        System.out.println("index check passed, entries=" + index.size() + " (5000 + 1 - 1 = 5000)");

        // 5. close and reopen the whole database: the index must come back
        db.close();
        Database again = new Database(dir);
        Executor ex2 = new Executor(again);
        System.out.println("reopened, id=9999 -> " + timed(ex2, "SELECT * FROM users WHERE id = 9999").rows().size() + " row");
        again.getIndex("users", "id").check();

        // 6. errors
        tryBad(ex2, "CREATE INDEX ON users (id)");
        tryBad(ex2, "CREATE INDEX ON users (nope)");
        tryBad(ex2, "CREATE INDEX ON nosuch (id)");
        again.close();
    }

    private static Result timed(Executor ex, String sql) throws IOException {
        long t0 = System.nanoTime();
        Result r = ex.execute(sql);
        long micros = (System.nanoTime() - t0) / 1000;
        System.out.println(sql + "\n   -> " + ex.lastPlan() + ", " + r.rows().size() + " rows, " + micros + " us");
        return r;
    }

    private static void tryBad(Executor ex, String sql) throws IOException {
        try {
            ex.execute(sql);
            System.out.println(sql + " -> NOT refused (bug)");
        } catch (SqlException e) {
            System.out.println(sql + " -> refused: " + e.getMessage());
        }
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