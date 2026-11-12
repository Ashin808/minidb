package minidb.record;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.catalog.TableSchema;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class Row {
    private final TableSchema schema;
    private final List<Value> values;

    public Row(TableSchema schema, List<Value> values) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(values, "values");
        if (values.size() != schema.columnCount())
            throw new IllegalArgumentException("Expected " + schema.columnCount()
                    + " values but got " + values.size());
        for (int i = 0; i < values.size(); i++) {
            Column c = schema.getColumn(i);
            Value v = values.get(i);
            if (v == null)
                throw new IllegalArgumentException("Column " + c.name() + " cannot be null");
            if (v.type() != c.type())
                throw new IllegalArgumentException("Column " + c.name() + " expects "
                        + c.type() + " but got " + v.type());
            if (c.type() == DataType.VARCHAR) {
                int bytes = v.asString().getBytes(StandardCharsets.UTF_8).length;
                if (bytes > c.maxLength())
                    throw new IllegalArgumentException("Column " + c.name() + " allows at most "
                            + c.maxLength() + " bytes but got " + bytes);
            }
        }
        this.schema = schema;
        this.values = List.copyOf(values);
    }

    public TableSchema schema() { return schema; }
    public List<Value> values() { return values; }
    public Value get(int i) { return values.get(i); }

    public Value get(String columnName) {
        int i = schema.indexOf(columnName);
        if (i < 0) throw new IllegalArgumentException("No such column: " + columnName);
        return values.get(i);
    }

    @Override
    public String toString() { return values.toString(); }
}