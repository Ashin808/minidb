package minidb.table;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.catalog.TableSchema;
import minidb.index.TableIndex;
import minidb.sql.SqlException;
import minidb.storage.Pager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A database = one folder. Each table is a file (<name>.db) and a text file
 * (catalog.txt) remembers every table's name and columns. Indexes are listed in
 * indexes.txt (one "table|column" line each) and stored in <table>_<column>.idx files.
 */
public class Database {
    private static final String CATALOG_FILE = "catalog.txt";
    private static final String INDEX_LIST_FILE = "indexes.txt";

    private final Path dir;
    private final Map<String, Table> tables = new LinkedHashMap<>();   // key = lowercase name
    private final Map<String, TableIndex> indexes = new LinkedHashMap<>();   // key = "table|column", lowercase

    public Database(String dirPath) throws IOException {
        this.dir = Path.of(dirPath);
        Files.createDirectories(dir);
        loadCatalog();
        loadIndexes();
    }

    public Table createTable(String name, List<Column> columns) throws IOException {
        String key = normalize(name);
        if (tables.containsKey(key)) {
            throw new SqlException("Table already exists: " + key);
        }

        Set<String> seen = new HashSet<>();
        for (Column c : columns) {
            if (!seen.add(normalize(c.name()))) {
                throw new SqlException("Duplicate column name: " + c.name());
            }
        }

        TableSchema schema = new TableSchema(key, columns);
        if (schema.rowsPerPage(Pager.PAGE_SIZE) < 1) {
            throw new SqlException("Row too large: " + schema.rowSize()
                    + " bytes, page size is " + Pager.PAGE_SIZE);
        }

        Table table = new Table(schema, tablePath(key));
        tables.put(key, table);
        saveCatalog();
        return table;
    }

    public Table getTable(String name) {
        Table table = tables.get(normalize(name));
        if (table == null) {
            throw new SqlException("No such table: " + normalize(name));
        }
        return table;
    }

    public boolean hasTable(String name) {
        return tables.containsKey(normalize(name));
    }

    public List<String> tableNames() {
        return new ArrayList<>(tables.keySet());
    }

    // ---------- indexes ----------

    /** Builds an index on table(column) from the rows already stored, and remembers it. */
    public TableIndex createIndex(String tableName, String columnName) throws IOException {
        Table table = getTable(tableName);
        String tableKey = normalize(tableName);
        String columnKey = normalize(columnName);
        if (table.schema().indexOf(columnKey) < 0) {
            throw new SqlException("No such column: " + columnKey);
        }
        String key = indexKey(tableKey, columnKey);
        if (indexes.containsKey(key)) {
            throw new SqlException("Index already exists on " + tableKey + "(" + columnKey + ")");
        }
        TableIndex index = TableIndex.build(table, columnKey, indexPath(tableKey, columnKey));
        indexes.put(key, index);
        saveIndexList();
        return index;
    }

    /** The index on table(column), or null if there isn't one. */
    public TableIndex getIndex(String tableName, String columnName) {
        return indexes.get(indexKey(normalize(tableName), normalize(columnName)));
    }

    /** Every index that belongs to this table. */
    public List<TableIndex> indexesOf(String tableName) {
        String prefix = normalize(tableName) + "|";
        List<TableIndex> result = new ArrayList<>();
        for (Map.Entry<String, TableIndex> entry : indexes.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    public void close() throws IOException {
        for (TableIndex index : indexes.values()) {
            index.close();
        }
        for (Table table : tables.values()) {
            table.close();
        }
    }

    // ---------- catalog file ----------
    // One line per table:  users|id:INT|name:VARCHAR:50|age:INT

    private void saveCatalog() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            StringBuilder line = new StringBuilder(entry.getKey());
            for (Column c : entry.getValue().schema().columns()) {
                line.append('|').append(c.name()).append(':').append(c.type());
                if (c.type() == DataType.VARCHAR) {
                    line.append(':').append(c.maxLength());
                }
            }
            lines.add(line.toString());
        }
        // write to a temp file first, then swap it in, so a crash can't leave a half-written catalog
        Path temp = dir.resolve(CATALOG_FILE + ".tmp");
        Files.write(temp, lines);
        Files.move(temp, dir.resolve(CATALOG_FILE), StandardCopyOption.REPLACE_EXISTING);
    }

    private void loadCatalog() throws IOException {
        Path catalog = dir.resolve(CATALOG_FILE);
        if (!Files.exists(catalog)) {
            return;   // brand-new database
        }
        for (String line : Files.readAllLines(catalog)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|");
            String name = parts[0];
            List<Column> columns = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                columns.add(parseColumn(parts[i], line));
            }
            tables.put(name, new Table(new TableSchema(name, columns), tablePath(name)));
        }
    }

    // ---------- index list file ----------
    // One line per index:  users|id

    private void saveIndexList() throws IOException {
        Path temp = dir.resolve(INDEX_LIST_FILE + ".tmp");
        Files.write(temp, new ArrayList<>(indexes.keySet()));
        Files.move(temp, dir.resolve(INDEX_LIST_FILE), StandardCopyOption.REPLACE_EXISTING);
    }

    private void loadIndexes() throws IOException {
        Path list = dir.resolve(INDEX_LIST_FILE);
        if (!Files.exists(list)) {
            return;   // no indexes yet
        }
        for (String line : Files.readAllLines(list)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|");
            if (parts.length != 2 || !tables.containsKey(parts[0])) {
                throw new IOException("Corrupt index list line: " + line);
            }
            Table table = tables.get(parts[0]);
            String path = indexPath(parts[0], parts[1]);
            TableIndex index;
            if (Files.exists(Path.of(path))) {
                index = TableIndex.open(table, parts[1], path);
            } else {
                index = TableIndex.build(table, parts[1], path);   // index file was lost: rebuild it from the table
            }
            indexes.put(line, index);
        }
    }

    private static Column parseColumn(String text, String line) throws IOException {
        String[] p = text.split(":");
        try {
            if (p.length == 2 && p[1].equals("INT")) {
                return Column.integer(p[0]);
            }
            if (p.length == 3 && p[1].equals("VARCHAR")) {
                return Column.varchar(p[0], Integer.parseInt(p[2]));
            }
        } catch (NumberFormatException e) {
            // fall through to the error below
        }
        throw new IOException("Corrupt catalog line: " + line);
    }

    private String tablePath(String key) {
        return dir.resolve(key + ".db").toString();
    }

    private String indexPath(String tableKey, String columnKey) {
        return dir.resolve(tableKey + "_" + columnKey + ".idx").toString();
    }

    private static String indexKey(String tableKey, String columnKey) {
        return tableKey + "|" + columnKey;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}