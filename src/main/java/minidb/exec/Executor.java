package minidb.exec;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.catalog.TableSchema;
import minidb.index.TableIndex;
import minidb.record.Row;
import minidb.record.Value;
import minidb.sql.Expression;
import minidb.sql.Operator;
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
    /** An index that can answer part of a WHERE clause, and the equality condition it answers. */
    private record IndexChoice(TableIndex index, Expression.Comparison comparison) {}

    private final Database db;
    private String lastPlan = "none yet";
    private boolean useIndexes = true;   // false = always full scan (used by the playground's Compare button)

    public Executor(Database db) {
        this.db = db;
    }

    /** How the last SELECT found its rows: "index lookup on ..." or "full scan of ...". */
    public String lastPlan() {
        return lastPlan;
    }

    /** SQL text in, Result out. The elapsed time covers parsing and execution. */
    public Result execute(String sql) throws IOException {
        long start = System.nanoTime();
        Result result = execute(Parser.parse(sql));
        return result.withElapsed(System.nanoTime() - start);
    }

    /** Same as execute(sql), but with indexes switched off or on for this one call. */
    public Result execute(String sql, boolean useIndexes) throws IOException {
        boolean before = this.useIndexes;
        this.useIndexes = useIndexes;
        try {
            return execute(sql);
        } finally {
            this.useIndexes = before;      // never leave the switch off for the next request
        }
    }

    public Result execute(Statement statement) throws IOException {
        return switch (statement) {
            case Statement.CreateTable s -> createTable(s);
            case Statement.CreateIndex s -> createIndex(s);
            case Statement.Insert s -> insert(s);
            case Statement.Select s -> select(s);
            case Statement.Delete s -> delete(s);
        };
    }

    private Result createTable(Statement.CreateTable s) throws IOException {
        db.createTable(s.tableName(), s.columns());
        return Result.ofMessage("Table " + s.tableName().toLowerCase(Locale.ROOT) + " created");
    }

    private Result createIndex(Statement.CreateIndex s) throws IOException {
        Table table = db.getTable(s.tableName());
        WhereEvaluator.columnIndex(table.schema(), s.columnName());   // rejects an unknown column
        TableIndex index = db.createIndex(s.tableName(), s.columnName());
        return Result.ofMessage("Index created on " + s.tableName().toLowerCase(Locale.ROOT)
                + "(" + s.columnName().toLowerCase(Locale.ROOT) + "): " + index.size() + " rows indexed");
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

        Row row = new Row(schema, values);
        Table.RowId id = table.insert(row);
        for (TableIndex index : db.indexesOf(s.tableName())) {     // keep every index of this table up to date
            index.onInsert(row, id);
        }
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

        // candidate rows: from an index if the WHERE allows it, otherwise every row
        List<Table.StoredRow> candidates;
        int pagesRead;
        IndexChoice choice = useIndexes ? chooseIndex(s.where(), s.tableName()) : null;
        if (choice != null) {
            candidates = new ArrayList<>();
            List<Table.RowId> ids = choice.index().lookup(choice.comparison().literal());
            pagesRead = choice.index().lastLookupPageReads();     // tree pages
            for (Table.RowId id : ids) {
                Row row = table.read(id);
                if (row != null) {
                    candidates.add(new Table.StoredRow(id, row));
                    pagesRead++;                                  // one table page per row fetched
                }
            }
            lastPlan = "index lookup on " + schema.name() + "(" + choice.index().columnName() + ")";
        } else {
            candidates = table.scan();
            pagesRead = table.pageCount();                        // a scan reads every table page
            lastPlan = "full scan of " + schema.name();
        }

        // the full WHERE is always re-checked, so an index can only make the query faster, never change its answer
        List<List<Value>> rows = new ArrayList<>();
        for (Table.StoredRow stored : candidates) {
            if (WhereEvaluator.matches(s.where(), stored.row())) {
                List<Value> out = new ArrayList<>();
                for (int i : indexes) {
                    out.add(stored.row().get(i));
                }
                rows.add(out);
            }
        }
        return Result.ofRows(names, rows).withPlan(lastPlan, pagesRead);
    }

    private Result delete(Statement.Delete s) throws IOException {
        Table table = db.getTable(s.tableName());
        WhereEvaluator.validate(s.where(), table.schema());
        List<TableIndex> tableIndexes = db.indexesOf(s.tableName());

        int deleted = 0;
        for (Table.StoredRow stored : table.scan()) {      // scan() returns a finished list, so deleting while looping is safe
            if (WhereEvaluator.matches(s.where(), stored.row()) && table.delete(stored.id())) {
                for (TableIndex index : tableIndexes) {    // remove the row from every index too
                    index.onDelete(stored.row(), stored.id());
                }
                deleted++;
            }
        }
        return Result.ofMessage(Result.rowCount(deleted) + " deleted");
    }

    /**
     * Finds an index that can narrow the search: an equality (col = x) on an indexed column,
     * either the whole WHERE or one of the conditions joined by AND. OR and other operators never qualify.
     */
    private IndexChoice chooseIndex(Expression where, String tableName) {
        if (where instanceof Expression.Comparison c) {
            if (c.op() == Operator.EQ) {
                TableIndex index = db.getIndex(tableName, c.column());
                if (index != null) {
                    return new IndexChoice(index, c);
                }
            }
            return null;
        }
        if (where instanceof Expression.And and) {
            IndexChoice left = chooseIndex(and.left(), tableName);
            return left != null ? left : chooseIndex(and.right(), tableName);
        }
        return null;
    }

    private static String describe(Value v) {
        return v.type() == DataType.INT ? String.valueOf(v.asInt()) : "'" + v.asString() + "'";
    }
}