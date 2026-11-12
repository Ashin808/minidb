package minidb.catalog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableSchema {
    /** 1 flag byte at the start of every row: 1 = live row, 0 = empty or deleted. */
    public static final int ROW_HEADER = 1;

    private final String name;
    private final List<Column> columns;
    private final Map<String, Integer> indexByName = new HashMap<>();
    private final int rowSize;

    public TableSchema(String name, List<Column> columns) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Table name cannot be blank");
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("A table needs at least one column");
        this.name = name.toLowerCase();
        this.columns = List.copyOf(columns);
        int size = ROW_HEADER;
        for (int i = 0; i < this.columns.size(); i++) {
            Column c = this.columns.get(i);
            if (indexByName.put(c.name().toLowerCase(), i) != null)
                throw new IllegalArgumentException("Duplicate column: " + c.name());
            size += c.storageSize();
        }
        this.rowSize = size;
    }

    public String name() { return name; }
    public List<Column> columns() { return columns; }
    public int columnCount() { return columns.size(); }
    public Column getColumn(int i) { return columns.get(i); }
    public int rowSize() { return rowSize; }
    public int indexOf(String columnName) { return indexByName.getOrDefault(columnName.toLowerCase(), -1); }
    public int rowsPerPage(int pageSize) { return pageSize / rowSize; }
}