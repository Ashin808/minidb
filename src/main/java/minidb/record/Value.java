package minidb.record;

import minidb.catalog.DataType;

import java.util.Objects;

public final class Value implements Comparable<Value> {
    private final DataType type;
    private final int intValue;
    private final String stringValue;

    private Value(DataType type, int intValue, String stringValue) {
        this.type = type;
        this.intValue = intValue;
        this.stringValue = stringValue;
    }

    public static Value ofInt(int v) { return new Value(DataType.INT, v, null); }

    public static Value ofString(String s) {
        Objects.requireNonNull(s, "NULL is not supported");
        return new Value(DataType.VARCHAR, 0, s);
    }

    public DataType type() { return type; }

    public int asInt() {
        if (type != DataType.INT) throw new IllegalStateException("Not an INT value");
        return intValue;
    }

    public String asString() {
        if (type != DataType.VARCHAR) throw new IllegalStateException("Not a VARCHAR value");
        return stringValue;
    }

    @Override
    public int compareTo(Value other) {
        if (type != other.type)
            throw new IllegalArgumentException("Cannot compare " + type + " with " + other.type);
        return type == DataType.INT ? Integer.compare(intValue, other.intValue)
                : stringValue.compareTo(other.stringValue);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Value v)) return false;
        return type == v.type && intValue == v.intValue && Objects.equals(stringValue, v.stringValue);
    }

    @Override
    public int hashCode() { return Objects.hash(type, intValue, stringValue); }

    @Override
    public String toString() { return type == DataType.INT ? Integer.toString(intValue) : "'" + stringValue + "'"; }
}