package minidb.demo;

import minidb.catalog.Column;
import minidb.catalog.TableSchema;
import minidb.index.TableIndex;
import minidb.record.Row;
import minidb.record.Value;
import minidb.table.Table;
import minidb.table.Table.RowId;
import minidb.table.Table.StoredRow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class IndexTimingDemo {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("demo_data"));
        String tablePath = "demo_data/timing_users.tbl";
        String indexPath = "demo_data/timing_users_id.idx";
        Files.deleteIfExists(Path.of(tablePath));

        TableSchema schema = new TableSchema("users", List.of(
                Column.integer("id"), Column.varchar("name", 50), Column.integer("age")));
        Table table = new Table(schema, tablePath);

        int rows = 20000;
        for (int i = 1; i <= rows; i++) {
            table.insert(new Row(schema, List.of(
                    Value.ofInt(i), Value.ofString("user" + i), Value.ofInt(18 + i % 50))));
        }
        System.out.println("table: " + rows + " rows, " + table.pageCount() + " pages");

        long t0 = System.nanoTime();
        TableIndex index = TableIndex.build(table, "id", indexPath);
        long buildMs = (System.nanoTime() - t0) / 1_000_000;
        index.check();
        System.out.println("index built in " + buildMs + " ms: entries=" + index.size()
                + " height=" + index.height());

        int target = 15000;

        // full scan: read every row, keep the ones with id = target
        int scanRuns = 5;
        RowId viaScan = null;
        long s0 = System.nanoTime();
        for (int r = 0; r < scanRuns; r++) {
            for (StoredRow stored : table.scan()) {
                if (stored.row().get("id").asInt() == target) {
                    viaScan = stored.id();
                }
            }
        }
        long scanMicros = (System.nanoTime() - s0) / 1000 / scanRuns;

        // index: walk the tree
        int indexRuns = 1000;
        List<RowId> viaIndex = null;
        long i0 = System.nanoTime();
        for (int r = 0; r < indexRuns; r++) {
            viaIndex = index.lookup(Value.ofInt(target));
        }
        long indexMicros = (System.nanoTime() - i0) / 1000 / indexRuns;

        System.out.println("WHERE id = " + target);
        System.out.println("  full scan: " + scanMicros + " us   (reads all " + table.pageCount() + " pages)");
        System.out.println("  index:     " + indexMicros + " us   (reads " + index.lastLookupPageReads() + " pages)");
        System.out.println("  same row found: " + (viaIndex.size() == 1 && viaIndex.get(0).equals(viaScan)));

        // the index must also answer "no such row"
        System.out.println("  id = 99999 -> " + index.lookup(Value.ofInt(99999)));

        index.close();

        // reopen the index from its file
        TableIndex again = TableIndex.open(table, "id", indexPath);
        System.out.println("reopened index: entries=" + again.size()
                + " lookup 15000 -> " + again.lookup(Value.ofInt(15000)));
        again.close();
        table.close();
    }
}