package minidb.demo;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.record.Value;
import minidb.sql.Expression;
import minidb.sql.Operator;
import minidb.sql.Statement;
import minidb.sql.TokenType;

import java.util.List;
import java.util.stream.Collectors;

public class AstDemo {
    public static void main(String[] args) {
        Expression where = new Expression.And(
                new Expression.Comparison("age", Operator.GTE, Value.ofInt(18)),
                new Expression.Comparison("name", Operator.EQ, Value.ofString("Alice")));

        List<Statement> statements = List.of(
                new Statement.CreateTable("users", List.of(
                        Column.integer("id"), Column.varchar("name", 50), Column.integer("age"))),
                new Statement.CreateIndex("users", "id"),
                new Statement.Insert("users", List.of(
                        Value.ofInt(1), Value.ofString("Alice"), Value.ofInt(30))),
                new Statement.Select("users", List.of("name"), where),
                new Statement.Select("users", List.of(), null),
                new Statement.Delete("users",
                        new Expression.Comparison("id", Operator.EQ, Value.ofInt(1)))
        );
        for (Statement s : statements) {
            System.out.println(describe(s));
        }

        System.out.println(Operator.fromToken(TokenType.GTE));
        System.out.println(Operator.fromToken(TokenType.COMMA));
    }

    private static String describe(Statement s) {
        return switch (s) {
            case Statement.CreateTable c -> "CREATE TABLE " + c.tableName() + " ("
                    + c.columns().stream().map(AstDemo::column).collect(Collectors.joining(", ")) + ")";
            case Statement.CreateIndex c -> "CREATE INDEX on " + c.tableName() + "(" + c.columnName() + ")";
            case Statement.Insert i -> "INSERT INTO " + i.tableName() + " VALUES ("
                    + i.values().stream().map(Expression::format).collect(Collectors.joining(", ")) + ")";
            case Statement.Select q -> {
                String cols = q.columns().isEmpty() ? "*" : String.join(", ", q.columns());
                yield "SELECT " + cols + " FROM " + q.tableName() + whereText(q.where());
            }
            case Statement.Delete d -> "DELETE FROM " + d.tableName() + whereText(d.where());
        };
    }

    private static String whereText(Expression w) {
        return w == null ? "" : " WHERE " + w;
    }

    private static String column(Column c) {
        return c.name() + " " + c.type()
                + (c.type() == DataType.VARCHAR ? "(" + c.maxLength() + ")" : "");
    }
}