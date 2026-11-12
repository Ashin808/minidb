package minidb.demo;

import minidb.catalog.Column;
import minidb.catalog.TableSchema;
import minidb.exec.WhereEvaluator;
import minidb.record.Row;
import minidb.record.Value;
import minidb.sql.Expression;
import minidb.sql.Parser;
import minidb.sql.SqlException;
import minidb.sql.Statement;

import java.util.ArrayList;
import java.util.List;

public class WhereDemo {
    public static void main(String[] args) {
        TableSchema schema = new TableSchema("users", List.of(
                Column.integer("id"), Column.varchar("name", 50), Column.integer("age")));

        List<Row> rows = List.of(
                user(schema, 1, "Alice", 30),
                user(schema, 2, "Bob", 25),
                user(schema, 3, "Carol", 41),
                user(schema, 4, "Dave", 19));

        String[] conditions = {
                "",
                "WHERE age >= 25",
                "WHERE age >= 25 AND age < 40",
                "WHERE name = 'Bob' OR age < 20",
                "WHERE age < 20 OR (age > 40 AND id != 2)",
                "WHERE name > 'B'",
                "WHERE AGE = 30"
        };
        for (String where : conditions) {
            System.out.println("[" + (where.isEmpty() ? "(no WHERE)" : where) + "] -> "
                    + run(schema, rows, where));
        }

        String[] bad = {
                "WHERE nope = 1",
                "WHERE age = 'abc'",
                "WHERE name = 5",
                "WHERE age > 1 AND (oops = 2)"
        };
        for (String where : bad) {
            try {
                WhereEvaluator.validate(select(where).where(), schema);
                System.out.println("No error (unexpected)");
            } catch (SqlException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static List<String> run(TableSchema schema, List<Row> rows, String where) {
        Expression condition = select(where).where();
        WhereEvaluator.validate(condition, schema);
        List<String> names = new ArrayList<>();
        for (Row row : rows) {
            if (WhereEvaluator.matches(condition, row)) {
                names.add(row.get("name").asString());
            }
        }
        return names;
    }

    private static Statement.Select select(String where) {
        return (Statement.Select) Parser.parse("SELECT * FROM users " + where);
    }

    private static Row user(TableSchema schema, int id, String name, int age) {
        return new Row(schema, List.of(Value.ofInt(id), Value.ofString(name), Value.ofInt(age)));
    }
}