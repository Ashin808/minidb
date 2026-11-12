package minidb.demo;

import minidb.sql.Parser;
import minidb.sql.SqlException;

public class ParserDemo {
    public static void main(String[] args) {
        String[] good = {
                "CREATE TABLE users (id INT, name VARCHAR(50), age INT);",
                "create index on users (id)",
                "INSERT INTO users VALUES (1, 'Alice', 30)",
                "SELECT * FROM users",
                "SELECT name, age FROM users WHERE age >= 18 AND name = 'Alice' OR id = 1",
                "SELECT * FROM users WHERE age < 10 OR (age > 60 AND id != 3)",
                "DELETE FROM users WHERE id = 1;",
                "DELETE FROM users"
        };
        for (String sql : good) {
            System.out.println(AstDemo.describe(Parser.parse(sql)));
        }

        String[] bad = {
                "SELECT * users",
                "SELECT * FROM users WHERE age >",
                "INSERT INTO users VALUES (1, 2",
                "CREATE TABLE t (id VARCHAR(300))",
                "SELECT * FROM users garbage",
                "DROP TABLE users"
        };
        for (String sql : bad) {
            try {
                Parser.parse(sql);
            } catch (SqlException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}