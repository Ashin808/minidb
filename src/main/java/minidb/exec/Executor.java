package minidb.exec;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.catalog.TableSchema;
import minidb.record.Row;
import minidb.record.Value;
import minidb.sql.Parser;
import minidb.sql.SqlException;
import minidb.sql.Statement;
import minidb.table.Database;
import minidb.table.Table;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Runs parsed statements against a Database. */
public class Executor {
    private final Database db;

    public Executor(Database db) {
        this.db = db;
    }

    /** SQL text in, Result out. The elapsed time covers parsing and execution. */
    public Result execute(String sql) throws IOException {
        long start = System.nanoTime();
        Result result = execute(Parser.parse(sql));
        return result.withElapsed(System.nanoTime() - start);
    }

    public Result execute(Statement statement) throws IOException {
        return switch (statement) {
            case Statement.CreateTable s -> createTable(s);
            case Statement.CreateIndex s ->
                    throw new SqlException("CREATE INDEX is not supported yet (coming in Stage 5)");
            case Statement.Insert s -> insert(s);
            case Statement.Select s -> select(s);
            case Statement.Delete s -> delete(s);
        };
    }

    private Result createTable(Statement.CreateTable s) throws IOException {
        db.createTable(s.tableName(), s.columns());
        return Result.ofMessage("Table " + s.tableName().toLowerCase(Locale.ROOT) + " created");
    }

    private Result insert(Statement.Insert s) throws IOException {
        Table table = db.getTable(s.tableName());
        TableSchema schema = table.schema();
        List<Value> values = s.values();

        if (values.size() != schema.columnCount()) {
            throw new SqlException("Expected " + schema.columnCount()
                    + " values but got " + values.size());
        }
        // check every value BEFORE writing anything, so a bad INSERT never stores a partial row
        for (int i = 0; i < values.size(); i++) {
            Column column = schema.getColumn(i);
            Value value = values.get(i);
            if (column.type() != value.type()) {
                throw new SqlException("Type mismatch: column " + column.name()
                        + " is " + column.type()
                        + " but value is " + describe(value));
            }
            if (column.type() == DataType.VARCHAR) {
                int bytes = value.asString().getBytes(StandardCharsets.UTF_8).length;
                if (bytes > column.maxLength()) {
                    throw new SqlException("Value too long for column " + column.name()
                            + ": " + bytes + " bytes, max is " + column.maxLength());
                }
            }
        }

        table.insert(new Row(schema, values));
        return Result.ofMessage("1 row inserted");
    }

    private Result select(Statement.Select s) throws IOException {
        Table table = db.getTable(s.tableName());
        TableSchema schema = table.schema();
        WhereEvaluator.validate(s.where(), schema);

        // work out which column positions to return (empty list = SELECT *)
        List<Integer> indexes = new ArrayList<>();
        if (s.columns().isEmpty()) {
            for (int i = 0; i < schema.columnCount(); i++) {
                indexes.add(i);
            }
        } else {
            for (String name : s.columns()) {
                indexes.add(WhereEvaluator.columnIndex(schema, name));
            }
        }
        List<String> names = new ArrayList<>();
        for (int i : indexes) {
            names.add(schema.getColumn(i).name());
        }

        List<List<Value>> rows = new ArrayList<>();
        for (Table.StoredRow stored : table.scan()) {
            if (WhereEvaluator.matches(s.where(), stored.row())) {
                List<Value> out = new ArrayList<>();
                for (int i : indexes) {
                    out.add(stored.row().get(i));
                }
                rows.add(out);
            }
        }
        return Result.ofRows(names, rows);
    }

    private Result delete(Statement.Delete s) throws IOException {
        Table table = db.getTable(s.tableName());
        WhereEvaluator.validate(s.where(), table.schema());

        int deleted = 0;
        for (Table.StoredRow stored : table.scan()) {      // scan() returns a finished list, so deleting while looping is safe
            if (WhereEvaluator.matches(s.where(), stored.row()) && table.delete(stored.id())) {
                deleted++;
            }
        }
        return Result.ofMessage(Result.rowCount(deleted) + " deleted");
    }

    private static String describe(Value v) {
        return v.type() == DataType.INT ? String.valueOf(v.asInt()) : "'" + v.asString() + "'";
    }
}