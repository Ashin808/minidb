package minidb.exec;

import minidb.catalog.Column;
import minidb.catalog.TableSchema;
import minidb.record.Row;
import minidb.sql.Expression;
import minidb.sql.SqlException;

import java.util.List;

/** Checks WHERE conditions against a schema (validate) and against rows (matches). */
public final class WhereEvaluator {

    private WhereEvaluator() {}

    /**
     * Walks the whole tree and fails on an unknown column or a type mismatch.
     * Needs only the schema, so it works even when the table has no rows.
     */
    public static void validate(Expression where, TableSchema schema) {
        if (where == null) {
            return;
        }
        switch (where) {
            case Expression.Comparison c -> {
                Column column = schema.columns().get(columnIndex(schema, c.column()));
                if (column.type() != c.literal().type()) {
                    throw new SqlException("Type mismatch: column " + column.name()
                            + " is " + column.type()
                            + " but value is " + Expression.format(c.literal()));
                }
            }
            case Expression.And a -> {
                validate(a.left(), schema);
                validate(a.right(), schema);
            }
            case Expression.Or o -> {
                validate(o.left(), schema);
                validate(o.right(), schema);
            }
        }
    }

    /** Does this row satisfy the condition? A null condition (no WHERE) matches every row. */
    public static boolean matches(Expression where, Row row) {
        if (where == null) {
            return true;
        }
        return switch (where) {
            case Expression.Comparison c -> {
                int index = columnIndex(row.schema(), c.column());
                int cmp = row.get(index).compareTo(c.literal());
                yield switch (c.op()) {
                    case EQ -> cmp == 0;
                    case NEQ -> cmp != 0;
                    case LT -> cmp < 0;
                    case LTE -> cmp <= 0;
                    case GT -> cmp > 0;
                    case GTE -> cmp >= 0;
                };
            }
            case Expression.And a -> matches(a.left(), row) && matches(a.right(), row);
            case Expression.Or o -> matches(o.left(), row) || matches(o.right(), row);
        };
    }

    static int columnIndex(TableSchema schema, String name) {
        List<Column> columns = schema.columns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new SqlException("No such column: " + name);
    }
}