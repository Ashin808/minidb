package minidb.api;

import minidb.catalog.DataType;
import minidb.exec.Executor;
import minidb.exec.Result;
import minidb.record.Value;
import minidb.sql.SqlException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class QueryController {

    /** useIndexes is optional: missing or null means true. */
    public record QueryRequest(String sql, Boolean useIndexes) {}

    /** The JSON reply. Rows hold plain numbers and strings so the React table can render them directly. */
    public record QueryResponse(String message, List<String> columns, List<List<Object>> rows,
                                String plan, int pagesRead, double elapsedMs) {}

    private final Executor executor;

    public QueryController(Executor executor) {
        this.executor = executor;
    }

    @PostMapping("/query")
    public QueryResponse query(@RequestBody QueryRequest request) throws IOException {
        if (request.sql() == null || request.sql().isBlank()) {
            throw new SqlException("SQL is empty");
        }

        Result result;
        synchronized (executor) {          // the engine is single-threaded, so one request at a time
            boolean useIndexes = request.useIndexes() == null || request.useIndexes();
            result = executor.execute(request.sql(), useIndexes);
        }
        return toResponse(result);
    }

    private static QueryResponse toResponse(Result r) {
        List<List<Object>> rows = new ArrayList<>();
        for (List<Value> row : r.rows()) {
            List<Object> out = new ArrayList<>();
            for (Value v : row) {
                out.add(v.type() == DataType.INT ? (Object) v.asInt() : v.asString());
            }
            rows.add(out);
        }
        return new QueryResponse(r.message(), r.columns(), rows, r.plan(), r.pagesRead(), r.elapsedMillis());
    }
}