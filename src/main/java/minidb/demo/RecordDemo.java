package minidb.demo;

import minidb.catalog.Column;
import minidb.catalog.TableSchema;
import minidb.record.Row;
import minidb.record.Value;

import java.util.List;

public class RecordDemo {
    public static void main(String[] args) {
        TableSchema users = new TableSchema("users", List.of(
                Column.integer("id"),
                Column.varchar("name", 50),
                Column.integer("age")));

        Row ok = new Row(users, List.of(Value.ofInt(1), Value.ofString("John"), Value.ofInt(22)));
        System.out.println("Row: " + ok);
        System.out.println("name = " + ok.get("NAME"));
        System.out.println("id < age? " + (ok.get("id").compareTo(ok.get("age")) < 0));

        try {
            new Row(users, List.of(Value.ofInt(2), Value.ofInt(5), Value.ofInt(30)));
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected (wrong type): " + e.getMessage());
        }
        try {
            new Row(users, List.of(Value.ofInt(3), Value.ofString("x".repeat(51)), Value.ofInt(30)));
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected (too long): " + e.getMessage());
        }
    }
}