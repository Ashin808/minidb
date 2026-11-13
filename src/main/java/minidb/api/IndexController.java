package minidb.api;

import minidb.exec.Executor;
import minidb.index.TableIndex;
import minidb.sql.SqlException;
import minidb.table.Database;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class IndexController {

    /** text is null unless ?text=true was asked for. */
    public record TreeResponse(String table, String column, int entries, int height,
                               TableIndex.NodeView root, String text) {}

    private final Database db;
    private final Executor executor;

    public IndexController(Database db, Executor executor) {
        this.db = db;
        this.executor = executor;
    }

    @GetMapping("/tables/{table}/indexes/{column}/dump")
    public TreeResponse dump(@PathVariable String table, @PathVariable String column,
                             @RequestParam(defaultValue = "20") int maxKeys,
                             @RequestParam(defaultValue = "false") boolean text) throws IOException {
        synchronized (executor) {          // same engine lock as /query and /tables
            TableIndex index = db.getIndex(table, column);
            if (index == null) {
                throw new SqlException("No index on " + table.toLowerCase() + "(" + column.toLowerCase() + ")");
            }
            return new TreeResponse(table.toLowerCase(), column.toLowerCase(),
                    index.size(), index.height(), index.structure(maxKeys),
                    text ? index.dump() : null);
        }
    }
}