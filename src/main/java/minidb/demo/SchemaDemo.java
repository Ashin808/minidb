package minidb.demo;

import minidb.catalog.Column;
import minidb.catalog.TableSchema;
import minidb.storage.Pager;

import java.util.List;

public class SchemaDemo {
    public static void main(String[] args) {
        TableSchema users = new TableSchema("Users", List.of(
                Column.integer("id"),
                Column.varchar("name", 50),
                Column.integer("age")));

        System.out.println("Table: " + users.name());
        System.out.println("Columns: " + users.columnCount());
        System.out.println("Row size: " + users.rowSize());
        System.out.println("Rows per page: " + users.rowsPerPage(Pager.PAGE_SIZE));
        System.out.println("Index of NAME: " + users.indexOf("NAME"));
        System.out.println("Index of salary: " + users.indexOf("salary"));
    }
}