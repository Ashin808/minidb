package minidb.table;

import minidb.catalog.Column;
import minidb.catalog.DataType;
import minidb.catalog.TableSchema;
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
 * (catalog.txt) remembers every table's name and columns.
 */
public class Database {
    private static final String CATALOG_FILE = "catalog.txt";

    private final Path dir;
    private final Map<String, Table> tables = new LinkedHashMap<>();   // key = lowercase name

    public Database(String dirPath) throws IOException {
        this.dir = Path.of(dirPath);
        Files.createDirectories(dir);
        loadCatalog();
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

    public void close() throws IOException {
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

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}