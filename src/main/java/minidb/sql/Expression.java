package minidb.sql;

import minidb.catalog.DataType;
import minidb.record.Value;

/** A WHERE condition, stored as a tree. */
public sealed interface Expression {

    /** column <op> literal, e.g. age >= 18 */
    record Comparison(String column, Operator op, Value literal) implements Expression {
        @Override
        public String toString() {
            return column + " " + op.symbol() + " " + Expression.format(literal);
        }
    }

    record And(Expression left, Expression right) implements Expression {
        @Override
        public String toString() {
            return "(" + left + " AND " + right + ")";
        }
    }

    record Or(Expression left, Expression right) implements Expression {
        @Override
        public String toString() {
            return "(" + left + " OR " + right + ")";
        }
    }

    /** Formats a value the way it would appear in SQL text. */
    static String format(Value v) {
        return v.type() == DataType.INT ? String.valueOf(v.asInt()) : "'" + v.asString() + "'";
    }
}