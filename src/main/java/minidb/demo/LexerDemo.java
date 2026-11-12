package minidb.demo;

import minidb.sql.Lexer;
import minidb.sql.SqlException;

public class LexerDemo {
    public static void main(String[] args) {
        String[] good = {
                "SELECT * FROM users WHERE age >= 18 AND name = 'Alice';",
                "insert into users values (1, 'O''Brien', -5)",
                "a <> 1 OR b < 2"
        };
        for (String sql : good) {
            System.out.println(new Lexer(sql).tokenize());
        }

        String[] bad = {
                "SELECT # FROM t",
                "name = 'oops"
        };
        for (String sql : bad) {
            try {
                new Lexer(sql).tokenize();
            } catch (SqlException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}