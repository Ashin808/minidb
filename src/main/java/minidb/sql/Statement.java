package minidb.sql;

import minidb.catalog.Column;
import minidb.record.Value;

import java.util.List;

/** One parsed SQL command. */
public sealed interface Statement {

    record CreateTable(String tableName, List<Column> columns) implements Statement {}

    /** CREATE INDEX ON table (column) */
    record CreateIndex(String tableName, String columnName) implements Statement {}

    record Insert(String tableName, List<Value> values) implements Statement {}

    /** columns is empty for SELECT *; where is null when there is no WHERE clause. */
    record Select(String tableName, List<String> columns, Expression where) implements Statement {}

    /** where is null for DELETE without a WHERE clause (deletes all rows). */
    record Delete(String tableName, Expression where) implements Statement {}
}