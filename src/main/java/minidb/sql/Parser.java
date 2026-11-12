package minidb.sql;

import minidb.catalog.Column;
import minidb.record.Value;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Convenience: SQL text in, Statement out. */
    public static Statement parse(String sql) {
        return new Parser(new Lexer(sql).tokenize()).parseStatement();
    }

    // ---------- statements ----------

    public Statement parseStatement() {
        Statement stmt = switch (peek().type()) {
            case CREATE -> parseCreate();
            case INSERT -> parseInsert();
            case SELECT -> parseSelect();
            case DELETE -> parseDelete();
            default -> throw error("Expected a statement (CREATE, INSERT, SELECT or DELETE)");
        };
        match(TokenType.SEMICOLON);                   // optional trailing ;
        expect(TokenType.EOF, "end of statement");    // nothing else may follow
        return stmt;
    }

    private Statement parseCreate() {
        advance(); // CREATE
        if (match(TokenType.TABLE)) return parseCreateTable();
        if (match(TokenType.INDEX)) return parseCreateIndex();
        throw error("Expected TABLE or INDEX after CREATE");
    }

    private Statement parseCreateTable() {
        String table = expect(TokenType.IDENTIFIER, "table name").text();
        expect(TokenType.LPAREN, "'('");
        List<Column> columns = new ArrayList<>();
        do {
            columns.add(parseColumnDef());
        } while (match(TokenType.COMMA));
        expect(TokenType.RPAREN, "')'");
        return new Statement.CreateTable(table, columns);
    }

    private Column parseColumnDef() {
        String name = expect(TokenType.IDENTIFIER, "column name").text();
        if (match(TokenType.INT)) {
            return Column.integer(name);
        }
        if (match(TokenType.VARCHAR)) {
            expect(TokenType.LPAREN, "'('");
            Token len = expect(TokenType.INT_LITERAL, "VARCHAR length");
            expect(TokenType.RPAREN, "')'");
            int n = Integer.parseInt(len.text());
            if (n < 1 || n > 255) {
                throw new SqlException("VARCHAR length must be between 1 and 255 at position " + len.position());
            }
            return Column.varchar(name, n);
        }
        throw error("Expected INT or VARCHAR");
    }

    private Statement parseCreateIndex() {
        expect(TokenType.ON, "ON");
        String table = expect(TokenType.IDENTIFIER, "table name").text();
        expect(TokenType.LPAREN, "'('");
        String column = expect(TokenType.IDENTIFIER, "column name").text();
        expect(TokenType.RPAREN, "')'");
        return new Statement.CreateIndex(table, column);
    }

    private Statement parseInsert() {
        advance(); // INSERT
        expect(TokenType.INTO, "INTO");
        String table = expect(TokenType.IDENTIFIER, "table name").text();
        expect(TokenType.VALUES, "VALUES");
        expect(TokenType.LPAREN, "'('");
        List<Value> values = new ArrayList<>();
        do {
            values.add(parseLiteral());
        } while (match(TokenType.COMMA));
        expect(TokenType.RPAREN, "')'");
        return new Statement.Insert(table, values);
    }

    private Statement parseSelect() {
        advance(); // SELECT
        List<String> columns = new ArrayList<>();   // stays empty for SELECT *
        if (!match(TokenType.STAR)) {
            do {
                columns.add(expect(TokenType.IDENTIFIER, "column name or '*'").text());
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.FROM, "FROM");
        String table = expect(TokenType.IDENTIFIER, "table name").text();
        return new Statement.Select(table, columns, parseOptionalWhere());
    }

    private Statement parseDelete() {
        advance(); // DELETE
        expect(TokenType.FROM, "FROM");
        String table = expect(TokenType.IDENTIFIER, "table name").text();
        return new Statement.Delete(table, parseOptionalWhere());
    }

    // ---------- WHERE expressions ----------
    // expression := and { OR and }
    // and        := primary { AND primary }
    // primary    := '(' expression ')' | comparison

    private Expression parseOptionalWhere() {
        return match(TokenType.WHERE) ? parseOr() : null;
    }

    private Expression parseOr() {
        Expression left = parseAnd();
        while (match(TokenType.OR)) {
            left = new Expression.Or(left, parseAnd());
        }
        return left;
    }

    private Expression parseAnd() {
        Expression left = parsePrimary();
        while (match(TokenType.AND)) {
            left = new Expression.And(left, parsePrimary());
        }
        return left;
    }

    private Expression parsePrimary() {
        if (match(TokenType.LPAREN)) {
            Expression inner = parseOr();
            expect(TokenType.RPAREN, "')'");
            return inner;
        }
        return parseComparison();
    }

    private Expression parseComparison() {
        String column = expect(TokenType.IDENTIFIER, "column name").text();
        Operator op = Operator.fromToken(peek().type());
        if (op == null) {
            throw error("Expected a comparison operator (=, !=, <, <=, >, >=)");
        }
        advance();
        return new Expression.Comparison(column, op, parseLiteral());
    }

    private Value parseLiteral() {
        Token t = peek();
        if (match(TokenType.INT_LITERAL)) return Value.ofInt(Integer.parseInt(t.text()));
        if (match(TokenType.STRING_LITERAL)) return Value.ofString(t.text());
        throw error("Expected a number or string literal");
    }

    // ---------- token helpers ----------

    private Token peek() {
        return tokens.get(current);
    }

    private Token advance() {
        Token t = tokens.get(current);
        if (t.type() != TokenType.EOF) current++;   // never walk past EOF
        return t;
    }

    private boolean match(TokenType type) {
        if (peek().type() == type) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(TokenType type, String what) {
        if (peek().type() == type) return advance();
        throw error("Expected " + what);
    }

    private SqlException error(String message) {
        Token t = peek();
        String found = t.type() == TokenType.EOF ? "end of input" : "'" + t.text() + "'";
        return new SqlException(message + " but found " + found + " at position " + t.position());
    }
}