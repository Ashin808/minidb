package minidb.exec;

import minidb.record.Value;

import java.util.List;

/**
 * What running one statement gives back. Plain data only, so the REST layer
 * in Stage 6 can turn it straight into JSON.
 */
public record Result(String message, List<String> columns, List<List<Value>> rows, long elapsedNanos) {

    public static Result ofMessage(String message) {
        return new Result(message, List.of(), List.of(), 0);
    }

    public static Result ofRows(List<String> columns, List<List<Value>> rows) {
        return new Result(rowCount(rows.size()), columns, rows, 0);
    }

    public Result withElapsed(long nanos) {
        return new Result(message, columns, rows, nanos);
    }

    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }

    /** True for SELECT results (they carry columns), false for plain messages. */
    public boolean isQuery() {
        return !columns.isEmpty();
    }

    public static String rowCount(int n) {
        return n + (n == 1 ? " row" : " rows");
    }
}