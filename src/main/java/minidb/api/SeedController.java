
package minidb.api;

import minidb.catalog.Column;
import minidb.exec.Executor;
import minidb.record.Value;
import minidb.sql.SqlException;
import minidb.sql.Statement;
import minidb.table.Database;
import minidb.table.Table;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SeedController {

    private static final String TABLE = "demo";
    private static final int MAX_ROWS = 20000;

    public record SeedResponse(String table, int inserted, int totalRows, double elapsedMs) {}

    private final Database db;
    private final Executor executor;

    public SeedController(Database db, Executor executor) {
        this.db = db;
        this.executor = executor;
    }

    /** Creates demo(id INT, name VARCHAR(50), age INT) if needed and appends `rows` rows with fresh ids. */
    @PostMapping("/demo/seed")
    public SeedResponse seed(@RequestParam(defaultValue = "1000") int rows) throws IOException {
        if (rows < 1 || rows > MAX_ROWS) {
            throw new SqlException("rows must be between 1 and " + MAX_ROWS);
        }
        synchronized (executor) {          // same engine lock as every other endpoint
            long start = System.nanoTime();
            if (!db.hasTable(TABLE)) {
                db.createTable(TABLE, List.of(
                        Column.integer("id"), Column.varchar("name", 50), Column.integer("age")));
            }
            Table table = db.getTable(TABLE);

            int maxId = 0;
            for (Table.StoredRow stored : table.scan()) {
                maxId = Math.max(maxId, stored.row().get(0).asInt());
            }
            for (int i = 1; i <= rows; i++) {
                int id = maxId + i;
                // going through the Executor keeps every index of the table up to date
                executor.execute(new Statement.Insert(TABLE, List.of(
                        Value.ofInt(id), Value.ofString("user" + id), Value.ofInt(18 + id % 50))));
            }
            int total = table.scan().size();
            return new SeedResponse(TABLE, rows, total, (System.nanoTime() - start) / 1_000_000.0);
        }
    }
}