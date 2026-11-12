package minidb.catalog;

import java.util.Objects;

public record Column(String name, DataType type, int maxLength) {
    public static final int INT_SIZE = 4;
    public static final int LENGTH_PREFIX = 2;
    public static final int MAX_VARCHAR = 255;

    public Column {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) throw new IllegalArgumentException("Column name cannot be blank");
        if (type == DataType.VARCHAR) {
            if (maxLength < 1 || maxLength > MAX_VARCHAR)
                throw new IllegalArgumentException("VARCHAR length must be 1.." + MAX_VARCHAR);
        } else {
            maxLength = 0;
        }
    }

    public static Column integer(String name) { return new Column(name, DataType.INT, 0); }
    public static Column varchar(String name, int maxLength) { return new Column(name, DataType.VARCHAR, maxLength); }

    public int storageSize() {
        return type == DataType.INT ? INT_SIZE : LENGTH_PREFIX + maxLength;
    }
}