package minidb.api;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.exec.Executor;
import minidb.index.TableIndex;
import minidb.table.Database;
import minidb.table.Table;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TablesController {

    /** maxLength is only set for VARCHAR columns (null for INT). */
    public record ColumnInfo(String name, String type, Integer maxLength) {}

    public record IndexInfo(String column, long entries, int height) {}

    public record TableInfo(String name, List<ColumnInfo> columns, int rowSize,
                            int pages, List<IndexInfo> indexes) {}

    private final Database db;
    private final Executor executor;

    public TablesController(Database db, Executor executor) {
        this.db = db;
        this.executor = executor;
    }

    @GetMapping("/tables")
    public List<TableInfo> tables() throws IOException {
        synchronized (executor) {          // same lock as /query: the engine is not thread-safe
            List<TableInfo> result = new ArrayList<>();
            for (String name : db.tableNames()) {
                Table table = db.getTable(name);

                List<ColumnInfo> columns = new ArrayList<>();
                for (Column c : table.schema().columns()) {
                    Integer max = c.type() == DataType.VARCHAR ? c.maxLength() : null;
                    columns.add(new ColumnInfo(c.name(), c.type().toString(), max));
                }

                List<IndexInfo> indexes = new ArrayList<>();
                for (TableIndex index : db.indexesOf(name)) {
                    indexes.add(new IndexInfo(index.columnName(), index.size(), index.height()));
                }

                result.add(new TableInfo(name, columns, table.schema().rowSize(),
                        table.pageCount(), indexes));
            }
            return result;
        }
    }
}